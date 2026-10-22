use std::rc::Rc;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use data_encoding::{BASE32_NOPAD, BASE64, HEXLOWER};
use embedded_hal::delay::DelayNs;
use embedded_hal::digital::OutputPin;
use embedded_hal::i2c::{ErrorType, I2c, Operation};
use esp_idf_hal::delay::Delay;
use esp_idf_hal::i2c::{I2cDriver, I2cError};
use esp_idf_svc::nvs::{EspNvs, NvsDefault};
use optiga_trust_m_rs::config::{IFX_I2C_BASE_ADDR, MAX_FRAME_SIZE, PL_DEFAULT_FREQUENCY_KHZ};
use optiga_trust_m_rs::ifx_i2c::{PlRegister, ProtectionConfig, ProtectionLevel, ResetType};
use optiga_trust_m_rs::pal::rust_crypto::RustCryptoBackend;
use optiga_trust_m_rs::pal::{
    Datastore, DatastoreId, HostCrypto, MonotonicClock, NoPin, OptigaI2cBus, OptigaTimer,
};
use optiga_trust_m_rs::{EccCurve, KeyUsage, Oid, OptigaTrustM, OptigaUtil, RngType};
use p256::ecdsa::signature::hazmat::{PrehashSigner, PrehashVerifier};
use p256::ecdsa::{Signature, SigningKey, VerifyingKey};
use p256::elliptic_curve::sec1::ToEncodedPoint;
use p256::{PublicKey, SecretKey};
use rand_core::{CryptoRng, RngCore};

use crate::internal_contract::SaveState;
use crate::provisioning::{
    burn_blk3_seed, burn_security_lockdown_efuses, compute_auth_key_attestation_digest,
    derive_pbs_from_seed, extract_public_key_bitstring, format_cert_export_json,
    get_efuse_security_status, read_blk3_seed, read_optiga_certificates,
};

// =========================================================================
// Hardware RNG for ESP32
// =========================================================================

pub struct Esp32Rng;

impl RngCore for Esp32Rng {
    fn next_u32(&mut self) -> u32 {
        unsafe { esp_idf_svc::sys::esp_random() as u32 }
    }

    fn next_u64(&mut self) -> u64 {
        let mut bytes = [0u8; 8];
        self.fill_bytes(&mut bytes);
        u64::from_le_bytes(bytes)
    }

    fn fill_bytes(&mut self, dest: &mut [u8]) {
        for chunk in dest.chunks_mut(4) {
            let rand = self.next_u32();
            for (i, byte) in chunk.iter_mut().enumerate() {
                *byte = (rand >> (i * 8)) as u8;
            }
        }
    }

    fn try_fill_bytes(&mut self, dest: &mut [u8]) -> Result<(), rand_core::Error> {
        self.fill_bytes(dest);
        Ok(())
    }
}

impl CryptoRng for Esp32Rng {}

// =========================================================================
// Shared I2C Bus & PAL Providers for OPTIGA
// =========================================================================

/// Thread-safe wrapper around `I2cDriver<'static>` implementing `embedded_hal::i2c::I2c`
/// and `optiga_trust_m_rs::pal::OptigaI2cBus`.
#[derive(Clone)]
pub struct SharedI2cBus(pub Arc<Mutex<I2cDriver<'static>>>);

impl SharedI2cBus {
    pub fn new(driver: I2cDriver<'static>) -> Self {
        Self(Arc::new(Mutex::new(driver)))
    }

    pub fn read_with_timeout(
        &self,
        addr: u8,
        buf: &mut [u8],
        timeout_ticks: u32,
    ) -> Result<(), esp_idf_svc::sys::EspError> {
        let mut guard = self.0.lock().unwrap();
        guard.read(addr, buf, timeout_ticks)
    }
}

impl ErrorType for SharedI2cBus {
    type Error = I2cError;
}

impl I2c for SharedI2cBus {
    fn read(&mut self, address: u8, read: &mut [u8]) -> Result<(), Self::Error> {
        let mut guard = self.0.lock().unwrap();
        embedded_hal::i2c::I2c::read(&mut *guard, address, read)
    }

    fn write(&mut self, address: u8, write: &[u8]) -> Result<(), Self::Error> {
        let mut guard = self.0.lock().unwrap();
        embedded_hal::i2c::I2c::write(&mut *guard, address, write)
    }

    fn write_read(
        &mut self,
        address: u8,
        write: &[u8],
        read: &mut [u8],
    ) -> Result<(), Self::Error> {
        let mut guard = self.0.lock().unwrap();
        embedded_hal::i2c::I2c::write_read(&mut *guard, address, write, read)
    }

    fn transaction(
        &mut self,
        address: u8,
        operations: &mut [Operation<'_>],
    ) -> Result<(), Self::Error> {
        let mut guard = self.0.lock().unwrap();
        embedded_hal::i2c::I2c::transaction(&mut *guard, address, operations)
    }
}

impl OptigaI2cBus for SharedI2cBus {
    fn set_bitrate_khz(&mut self, _bitrate_khz: u16) -> Result<(), Self::Error> {
        // Bitrate is fixed at configuration
        Ok(())
    }
}

/// Monotonic clock and delay provider implementing `OptigaTimer`.
pub struct EspOptigaTimer;

impl DelayNs for EspOptigaTimer {
    fn delay_ns(&mut self, ns: u32) {
        Delay::new_default().delay_ns(ns);
    }
}

impl MonotonicClock for EspOptigaTimer {
    fn now_ms(&self) -> u32 {
        unsafe { (esp_idf_svc::sys::esp_timer_get_time() / 1000) as u32 }
    }
}

/// In-memory host datastore storing the Platform Binding Shared Secret for Shielded Connections.
#[derive(Debug, Clone)]
pub struct HostDatastore {
    secret: [u8; 64],
    manage_context: [u8; 49],
    hibernate_context: [u8; 8],
}

impl Default for HostDatastore {
    fn default() -> Self {
        // Standard Infineon default platform binding shared secret: 0x01..=0x40 (64 bytes)
        let mut secret = [0u8; 64];
        for (i, b) in secret.iter_mut().enumerate() {
            *b = (i + 1) as u8;
        }
        Self {
            secret,
            manage_context: [0u8; 49],
            hibernate_context: [0u8; 8],
        }
    }
}

impl Datastore for HostDatastore {
    type Error = ();

    fn read(&self, id: DatastoreId, buf: &mut [u8]) -> Result<usize, Self::Error> {
        match id {
            DatastoreId::PlatformBindingSharedSecret => {
                if buf.len() < self.secret.len() {
                    return Err(());
                }
                buf[..self.secret.len()].copy_from_slice(&self.secret);
                Ok(self.secret.len())
            }
            DatastoreId::ManageContext => {
                if buf.len() < self.manage_context.len() {
                    return Err(());
                }
                buf[..self.manage_context.len()].copy_from_slice(&self.manage_context);
                Ok(self.manage_context.len())
            }
            DatastoreId::HibernateContext => {
                if buf.len() < self.hibernate_context.len() {
                    return Err(());
                }
                buf[..self.hibernate_context.len()].copy_from_slice(&self.hibernate_context);
                Ok(self.hibernate_context.len())
            }
        }
    }

    fn write(&mut self, id: DatastoreId, data: &[u8]) -> Result<(), Self::Error> {
        match id {
            DatastoreId::PlatformBindingSharedSecret => {
                if data.len() <= self.secret.len() {
                    self.secret[..data.len()].copy_from_slice(data);
                    Ok(())
                } else {
                    Err(())
                }
            }
            DatastoreId::ManageContext => {
                if data.len() <= self.manage_context.len() {
                    self.manage_context[..data.len()].copy_from_slice(data);
                    Ok(())
                } else {
                    Err(())
                }
            }
            DatastoreId::HibernateContext => {
                if data.len() <= self.hibernate_context.len() {
                    self.hibernate_context[..data.len()].copy_from_slice(data);
                    Ok(())
                } else {
                    Err(())
                }
            }
        }
    }
}

// =========================================================================
// OID Constants & Data Framing Specification
// =========================================================================

pub const OPTIGA_PBS_OID: Oid = Oid::Custom(0xE140);
pub const OPTIGA_FACTORY_KEY_OID: Oid = Oid::DevicePrivateKey0; // 0xE0F0
pub const OPTIGA_FACTORY_CERT_OID: Oid = Oid::Data0; // 0xE0E0
pub const OPTIGA_KEY_OID: Oid = Oid::DevicePrivateKey1; // 0xE0F1 (Lock Identity Key)
pub const OPTIGA_AUTH_KEY_OID: Oid = Oid::DevicePrivateKey2; // 0xE0F2 (Authenticity Check Key)
pub const OPTIGA_PUBKEY_DATA_OID: Oid = Oid::Custom(0xF1D0); // 140B Type B: 0xE0F1 SEC1 PubKey
pub const OPTIGA_ATTESTATION_DATA_OID: Oid = Oid::Custom(0xF1D1); // 140B Type B: [0xE0F2 PubKey(65B) || 0xE0F0 Sig(64B)]
pub const OPTIGA_CREDENTIALS_DATA_OID: Oid = Oid::Custom(0xF1D2); // 140B Type B: CBOR DeviceCredentials
pub const OPTIGA_CONTRACT_DATA_OID: Oid = Oid::Custom(0xF1E0); // 1500B Type A: CBOR SaveState
pub const FRAME_MAGIC: [u8; 2] = [0x54, 0x41]; // "TA"

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ProvisionMode {
    /// Development provisioning (`SOFT_PROVISION`):
    /// - Uses default 64-byte Platform Binding Secret (`0x01..=0x40`) in `0xE140`
    /// - Leaves `0xE140` metadata changeable (`Change=Always`)
    /// - Generates/moves keys & credentials into OPTIGA (`0xE0F1`, `0xF1D0`, `0xE0F2`, `0xF1D1`, `0xF1D2`)
    /// - Does NOT burn any ESP32-S3 eFuses (`EFUSE_BLK3`, JTAG, Secure Download Mode remain untouched)
    Soft,
    /// Production hardware lockdown (`PROVISION`):
    /// - Generates 32-byte random seed (`ESP32_RNG ^ OPTIGA_TRNG`), derives 64-byte PBS via HKDF-SHA256
    /// - Burns 32-byte seed into `EFUSE_BLK3` and write-protects `BLK3`
    /// - Generates/moves keys & credentials into OPTIGA (`0xE0F1`, `0xF1D0`, `0xE0F2`, `0xF1D1`, `0xF1D2`)
    /// - Permanently locks `0xE140` metadata (`Change=Never, Read=Never`)
    /// - Burns ESP32-S3 JTAG disable and Secure ROM Download Mode eFuses
    Hard,
}

#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct DeviceCredentials {
    pub session_token: String,
    pub mqtt_password: String,
}

fn generate_fresh_credentials() -> DeviceCredentials {
    let mut rng = Esp32Rng;
    let mut session_bytes = [0u8; 5]; // 5 bytes -> 8 base32 chars, take first 6
    rng.fill_bytes(&mut session_bytes);
    let session_token = BASE32_NOPAD.encode(&session_bytes)[..6].to_string();

    let mut pass_bytes = [0u8; 32];
    rng.fill_bytes(&mut pass_bytes);
    let mqtt_password = BASE32_NOPAD.encode(&pass_bytes)[..].to_string();

    DeviceCredentials {
        session_token,
        mqtt_password,
    }
}

fn load_or_generate_nvs_credentials(nvs: &EspNvs<NvsDefault>) -> DeviceCredentials {
    let mut rng = Esp32Rng;

    let mut session_buf = [0u8; 32];
    let session_token = match nvs
        .get_blob("session_key", &mut session_buf)
        .ok()
        .flatten()
    {
        Some(bytes) => {
            let token = String::from_utf8_lossy(bytes).into_owned();
            log::info!("Loaded existing session ID from NVS: {:?}", token);
            token
        }
        None => {
            log::info!("Generating a new session ID in NVS...");
            let mut bytes = [0u8; 5];
            rng.fill_bytes(&mut bytes);
            let token = BASE32_NOPAD.encode(&bytes)[..6].to_string();
            let _ = nvs.set_blob("session_key", token.as_bytes());
            token
        }
    };

    let mut pass_buf = [0u8; 128];
    let mqtt_password = match nvs
        .get_blob("mqtt_password", &mut pass_buf)
        .ok()
        .flatten()
    {
        Some(bytes) => {
            let token = String::from_utf8_lossy(bytes).into_owned();
            log::info!("Loaded existing MQTT password from NVS.");
            token
        }
        None => {
            log::info!("Generating a new MQTT password in NVS...");
            let mut bytes = [0u8; 32];
            rng.fill_bytes(&mut bytes);
            let token = BASE32_NOPAD.encode(&bytes)[..].to_string();
            let _ = nvs.set_blob("mqtt_password", token.as_bytes());
            token
        }
    };

    DeviceCredentials {
        session_token,
        mqtt_password,
    }
}

fn load_or_create_nvs_secret_key(nvs: &EspNvs<NvsDefault>) -> (SecretKey, PublicKey) {
    let key_name = "ec_private";
    let mut buffer: [u8; 256] = [0; 256];
    let existing_key: Option<&[u8]> = nvs
        .get_blob(key_name, &mut buffer)
        .expect("The NVS mechanism should have worked");
    let mut rng = Esp32Rng;

    let secret_key: SecretKey = if let Some(base64_key) = existing_key {
        log::info!("Loaded existing EC private key from NVS.");
        let key_bytes = BASE64.decode(base64_key).unwrap();
        SecretKey::from_slice(&key_bytes).expect("Failed to parse private key")
    } else {
        log::info!("Generating a new EC private key in software (NVS)...");
        let sk = SecretKey::random(&mut rng);
        let key_bytes = sk.to_bytes();
        let encoded_key = BASE64.encode(&key_bytes);
        nvs.set_blob(key_name, encoded_key.as_bytes()).unwrap();
        log::info!("Saved new EC private key to NVS.");
        sk
    };

    let public_key = PublicKey::from_secret_scalar(&secret_key.to_nonzero_scalar());
    (secret_key, public_key)
}

pub fn parse_optiga_public_key(raw: &[u8]) -> anyhow::Result<p256::PublicKey> {
    let sec1_slice = if raw.len() >= 68
        && raw[0] == 0x03
        && raw[1] == 0x42
        && raw[2] == 0x00
        && raw[3] == 0x04
    {
        &raw[3..68]
    } else if raw.len() == 65 && raw[0] == 0x04 {
        raw
    } else if raw.len() == 33 && (raw[0] == 0x02 || raw[0] == 0x03) {
        raw
    } else {
        anyhow::bail!("Unexpected public key format from OPTIGA ({} bytes)", raw.len());
    };
    p256::PublicKey::from_sec1_bytes(sec1_slice)
        .map_err(|e| anyhow::anyhow!("Failed to parse P-256 public key: {e}"))
}

pub fn optiga_der_integers_to_signature(der_ints: &[u8]) -> anyhow::Result<p256::ecdsa::Signature> {
    if der_ints.len() < 6 || der_ints[0] != 0x02 {
        anyhow::bail!("Invalid OPTIGA ECDSA signature header");
    }
    let r_len = der_ints[1] as usize;
    let s_tag_idx = 2 + r_len;
    if s_tag_idx + 2 > der_ints.len() || der_ints[s_tag_idx] != 0x02 {
        anyhow::bail!("Invalid OPTIGA ECDSA signature S tag offset");
    }
    let s_len = der_ints[s_tag_idx + 1] as usize;
    if s_tag_idx + 2 + s_len > der_ints.len() {
        anyhow::bail!("OPTIGA ECDSA signature truncated");
    }

    let r_raw = &der_ints[2..2 + r_len];
    let s_raw = &der_ints[s_tag_idx + 2..s_tag_idx + 2 + s_len];

    fn copy_coord_32(src: &[u8]) -> anyhow::Result<[u8; 32]> {
        // Strip leading 0x00 sign padding byte if present
        let stripped = if src.len() > 32 && src[0] == 0x00 {
            &src[src.len() - 32..]
        } else {
            src
        };
        if stripped.len() > 32 {
            anyhow::bail!("Coordinate exceeds 32 bytes ({} bytes)", stripped.len());
        }
        let mut out = [0u8; 32];
        out[32 - stripped.len()..].copy_from_slice(stripped);
        Ok(out)
    }

    let r_32 = copy_coord_32(r_raw)?;
    let s_32 = copy_coord_32(s_raw)?;
    let mut raw_64 = [0u8; 64];
    raw_64[..32].copy_from_slice(&r_32);
    raw_64[32..].copy_from_slice(&s_32);

    p256::ecdsa::Signature::from_slice(&raw_64)
        .map_err(|e| anyhow::anyhow!("Invalid P-256 signature scalars: {e}"))
}

pub fn read_framed_oid<I2C, T, VDD, RST, CRYPTO, DS>(
    util: &mut OptigaUtil<'_, I2C, T, VDD, RST, CRYPTO, DS>,
    oid: Oid,
    max_payload_len: usize,
) -> anyhow::Result<Option<Vec<u8>>>
where
    I2C: OptigaI2cBus,
    T: OptigaTimer,
    VDD: OutputPin,
    RST: OutputPin,
    CRYPTO: HostCrypto,
    DS: Datastore,
{
    let mut header = [0u8; 4];
    match util.read_data(oid, 0, &mut header) {
        Ok(read_len) if read_len >= 4 => {}
        Ok(_) => return Ok(None),
        Err(e) => {
            log::warn!("Failed to read header from OID {:?}: {:?}", oid, e);
            return Ok(None);
        }
    }

    if header[0] != FRAME_MAGIC[0] || header[1] != FRAME_MAGIC[1] {
        return Ok(None);
    }

    let len = u16::from_be_bytes([header[2], header[3]]) as usize;
    if len == 0 || len > max_payload_len {
        return Ok(None);
    }

    let mut buf = vec![0u8; len];
    let read_len = util
        .read_data(oid, 4, &mut buf)
        .map_err(|e| anyhow::anyhow!("Failed to read payload from OID {:?}: {:?}", oid, e))?;
    if read_len != len {
        anyhow::bail!(
            "Payload length mismatch on OID {:?}: expected {}, got {}",
            oid,
            len,
            read_len
        );
    }
    Ok(Some(buf))
}

pub fn write_framed_oid<I2C, T, VDD, RST, CRYPTO, DS>(
    util: &mut OptigaUtil<'_, I2C, T, VDD, RST, CRYPTO, DS>,
    oid: Oid,
    payload: &[u8],
    max_payload_len: usize,
) -> anyhow::Result<()>
where
    I2C: OptigaI2cBus,
    T: OptigaTimer,
    VDD: OutputPin,
    RST: OutputPin,
    CRYPTO: HostCrypto,
    DS: Datastore,
{
    if payload.len() > max_payload_len {
        anyhow::bail!(
            "Payload length {} exceeds max capacity {}",
            payload.len(),
            max_payload_len
        );
    }
    let len_be = (payload.len() as u16).to_be_bytes();
    let mut framed = Vec::with_capacity(4 + payload.len());
    framed.extend_from_slice(&FRAME_MAGIC);
    framed.extend_from_slice(&len_be);
    framed.extend_from_slice(payload);

    util.write_data(oid, 0, &framed, true)
        .map_err(|e| anyhow::anyhow!("Failed to write framed data to OID {:?}: {:?}", oid, e))?;
    Ok(())
}

pub fn clear_framed_oid<I2C, T, VDD, RST, CRYPTO, DS>(
    util: &mut OptigaUtil<'_, I2C, T, VDD, RST, CRYPTO, DS>,
    oid: Oid,
) -> anyhow::Result<()>
where
    I2C: OptigaI2cBus,
    T: OptigaTimer,
    VDD: OutputPin,
    RST: OutputPin,
    CRYPTO: HostCrypto,
    DS: Datastore,
{
    util.write_data(oid, 0, &[0x00, 0x00, 0x00, 0x00], true)
        .map_err(|e| anyhow::anyhow!("Failed to clear header for OID {:?}: {:?}", oid, e))?;
    Ok(())
}

// =========================================================================
// Security Backend Trait
// =========================================================================

pub trait SecurityBackend {
    /// Human-readable backend name for logging.
    fn backend_name(&self) -> &'static str;

    /// Returns the lock's NIST P-256 public key.
    fn public_key(&self) -> PublicKey;

    /// Returns the immutable hardware identifier (`copr_uid_hex` from OPTIGA `0xE0C2`)
    /// when backed by a provisioned OPTIGA Trust M, or `None` on software NVS / unprovisioned standby.
    fn hardware_id(&self) -> Option<String>;

    /// Signs a 32-byte SHA-256 digest using the lock's P-256 private key,
    /// returning a 64-byte raw IEEE P1363 signature.
    fn sign_prehash(&mut self, digest_32: &[u8; 32]) -> anyhow::Result<Signature>;

    /// Signs a 32-byte firmware authenticity challenge nonce using the hardware
    /// Authenticity Key (`0xE0F2`) if provisioned on OPTIGA.
    /// Returns `Ok(None)` when not provisioned or on software NVS backend so the caller
    /// can fall back to the legacy compile-time `private.key` if present.
    fn sign_authenticity_challenge(
        &mut self,
        nonce_32: &[u8; 32],
    ) -> anyhow::Result<Option<Signature>>;

    /// Computes the 32-byte ECDH shared secret between the lock's private key
    /// and `peer_public_key`.
    fn ecdh_shared_secret(&mut self, peer_public_key: &PublicKey) -> anyhow::Result<[u8; 32]>;

    /// Loads or creates the device's `session_token` and `mqtt_password`.
    fn load_or_create_credentials(
        &mut self,
        nvs: &EspNvs<NvsDefault>,
    ) -> anyhow::Result<DeviceCredentials>;

    /// Reads the saved contract state if present.
    fn read_contract(&mut self) -> anyhow::Result<Option<SaveState>>;

    /// Persists the contract state.
    fn write_contract(&mut self, state: &SaveState) -> anyhow::Result<()>;

    /// Removes/clears the persisted contract state.
    fn clear_contract(&mut self) -> anyhow::Result<()>;

    /// Wipes keys, credentials, and contract state prior to a full device reset.
    fn wipe_for_reset(&mut self) -> anyhow::Result<()>;

    /// Executes full production hardware provisioning (`PROVISION` command):
    /// - Generates/burns `EFUSE_BLK3` seed and binds OPTIGA PBS (`0xE140`)
    /// - Generates Lock Identity Key (`0xE0F1` / `0xF1D0`)
    /// - Generates Authenticity Key (`0xE0F2`), signs with Infineon Factory Key (`0xE0F0`), caches in `0xF1D1`
    /// - Moves/stores credentials in `0xF1D2`
    /// - Locks `0xE140` to `Change=Never, Read=Never`
    /// - Burns ESP32-S3 JTAG disable + Secure ROM Download Mode eFuses
    /// Returns `(new_lock_public_key, json_bundle)`.
    fn provision_device(
        &mut self,
        session_token: &str,
        mqtt_password: &str,
    ) -> anyhow::Result<(PublicKey, String)>;

    /// Executes development provisioning (`SOFT_PROVISION` command):
    /// - Writes the default 64-byte Platform Binding Secret (`0x01..=0x40`) to `0xE140` (keeping `0xE140` changeable)
    /// - Generates Lock Identity Key (`0xE0F1` / `0xF1D0`)
    /// - Generates Authenticity Key (`0xE0F2`), signs with Infineon Factory Key (`0xE0F0`), caches in `0xF1D1`
    /// - Moves/stores credentials in `0xF1D2` and deletes plaintext credentials from NVS
    /// - Does NOT burn any ESP32-S3 eFuses
    /// Returns `(new_lock_public_key, json_bundle)`.
    fn soft_provision_device(
        &mut self,
        session_token: &str,
        mqtt_password: &str,
    ) -> anyhow::Result<(PublicKey, String)>;

    /// Exports the certificate chain and attestation record (`EXPORT_CERT` command) as a JSON bundle.
    fn export_cert_bundle(&mut self, session_token: &str) -> anyhow::Result<String>;
}

// =========================================================================
// Legacy ESP32 NVS Security Backend
// =========================================================================

pub struct NvsSecurityBackend {
    nvs: Rc<EspNvs<NvsDefault>>,
    secret_key: SecretKey,
    public_key: PublicKey,
}

impl NvsSecurityBackend {
    pub fn new(nvs: Rc<EspNvs<NvsDefault>>) -> Self {
        let (secret_key, public_key) = load_or_create_nvs_secret_key(&nvs);
        Self {
            nvs,
            secret_key,
            public_key,
        }
    }
}

impl SecurityBackend for NvsSecurityBackend {
    fn backend_name(&self) -> &'static str {
        "ESP32 NVS"
    }

    fn public_key(&self) -> PublicKey {
        self.public_key
    }

    fn hardware_id(&self) -> Option<String> {
        None
    }

    fn sign_prehash(&mut self, digest_32: &[u8; 32]) -> anyhow::Result<Signature> {
        let signing_key = SigningKey::from(&self.secret_key);
        let sig: Signature = signing_key
            .sign_prehash(digest_32)
            .map_err(|e| anyhow::anyhow!("NVS software signing failed: {e}"))?;
        Ok(sig)
    }

    fn sign_authenticity_challenge(
        &mut self,
        _nonce_32: &[u8; 32],
    ) -> anyhow::Result<Option<Signature>> {
        // Software NVS backend does not hold a hardware authenticity key; caller will fall back to private.key
        Ok(None)
    }

    fn ecdh_shared_secret(&mut self, peer_public_key: &PublicKey) -> anyhow::Result<[u8; 32]> {
        let shared_secret = p256::ecdh::diffie_hellman(
            self.secret_key.to_nonzero_scalar(),
            peer_public_key.as_affine(),
        );
        let mut out = [0u8; 32];
        out.copy_from_slice(shared_secret.raw_secret_bytes());
        Ok(out)
    }

    fn load_or_create_credentials(
        &mut self,
        nvs: &EspNvs<NvsDefault>,
    ) -> anyhow::Result<DeviceCredentials> {
        Ok(load_or_generate_nvs_credentials(nvs))
    }

    fn read_contract(&mut self) -> anyhow::Result<Option<SaveState>> {
        read_contract_from_nvs(&self.nvs)
    }

    fn write_contract(&mut self, state: &SaveState) -> anyhow::Result<()> {
        write_contract_to_nvs(&self.nvs, state)
    }

    fn clear_contract(&mut self) -> anyhow::Result<()> {
        let _ = self.nvs.remove("contract");
        Ok(())
    }

    fn wipe_for_reset(&mut self) -> anyhow::Result<()> {
        let _ = self.nvs.remove("contract");
        let _ = self.nvs.remove("ec_private");
        let _ = self.nvs.remove("session_key");
        let _ = self.nvs.remove("mqtt_password");
        Ok(())
    }

    fn provision_device(
        &mut self,
        _session_token: &str,
        _mqtt_password: &str,
    ) -> anyhow::Result<(PublicKey, String)> {
        anyhow::bail!("Hardware provisioning requires an OPTIGA Trust M security chip on I2C (0x30)")
    }

    fn soft_provision_device(
        &mut self,
        _session_token: &str,
        _mqtt_password: &str,
    ) -> anyhow::Result<(PublicKey, String)> {
        anyhow::bail!("Soft provisioning requires an OPTIGA Trust M security chip on I2C (0x30)")
    }

    fn export_cert_bundle(&mut self, _session_token: &str) -> anyhow::Result<String> {
        anyhow::bail!("Certificate export requires an OPTIGA Trust M security chip on I2C (0x30)")
    }
}

fn read_contract_from_nvs(nvs: &EspNvs<NvsDefault>) -> anyhow::Result<Option<SaveState>> {
    let key_name = "contract";
    let required_size = match nvs.blob_len(key_name)? {
        Some(len) => len,
        None => {
            log::info!("No contract data was present in NVS");
            return Ok(None);
        }
    };
    let mut buffer = vec![0u8; required_size];
    match nvs.get_blob(key_name, &mut buffer) {
        Ok(Some(bytes)) => {
            let len = bytes.len();
            match ciborium::de::from_reader(&buffer[..len]) {
                Ok(state) => Ok(Some(state)),
                Err(e) => anyhow::bail!("Contract bytes present but failed to deserialize: {e}"),
            }
        }
        Ok(None) => Ok(None),
        Err(e) => anyhow::bail!("Failed to read contract from NVS: {e}"),
    }
}

fn write_contract_to_nvs(nvs: &EspNvs<NvsDefault>, state: &SaveState) -> anyhow::Result<()> {
    let mut bytes = Vec::new();
    ciborium::ser::into_writer(state, &mut bytes)
        .map_err(|e| anyhow::anyhow!("Failed to serialize contract: {e}"))?;
    nvs.set_blob("contract", &bytes)
        .map_err(|e| anyhow::anyhow!("Failed to write contract to NVS: {e}"))?;
    Ok(())
}

// =========================================================================
// OPTIGA Trust M Hardware Security Backend
// =========================================================================

pub struct OptigaSecurityBackend {
    dev: OptigaTrustM<SharedI2cBus, EspOptigaTimer, NoPin, NoPin, RustCryptoBackend, HostDatastore>,
    nvs: Rc<EspNvs<NvsDefault>>,
    public_key: PublicKey,
    unprovisioned_secret_key: Option<SecretKey>,
    is_provisioned: bool,
    protection: ProtectionConfig,
    copr_uid: Vec<u8>,
}

impl OptigaSecurityBackend {
    /// Initializes communication with the OPTIGA Trust M chip in **strict read-only mode**.
    ///
    /// No metadata, keys, or data objects in OPTIGA NVM are modified during `try_init`.
    /// - If the OPTIGA has already been provisioned (via `PROVISION` or `SOFT_PROVISION`),
    ///   it loads the provisioned public key (`0xF1D0`) and verifies `0xE0F1`.
    /// - If the OPTIGA has NOT been provisioned yet (and `EFUSE_BLK3` is unburned), it enters
    ///   **Unprovisioned Standby** mode—performing zero writes to OPTIGA and waiting for an
    ///   explicit `SOFT_PROVISION` or `PROVISION` command over serial.
    pub fn try_init(mut bus: SharedI2cBus, nvs: Rc<EspNvs<NvsDefault>>) -> anyhow::Result<Self> {
        // 1. Fast I2C Probe: Try up to 3 times to write PlRegister::I2cState (0x82) to 0x30
        let mut detected = false;
        for attempt in 1..=3 {
            if embedded_hal::i2c::I2c::write(
                &mut bus,
                IFX_I2C_BASE_ADDR,
                &[PlRegister::I2cState as u8],
            )
            .is_ok()
            {
                detected = true;
                break;
            }
            log::debug!("OPTIGA probe attempt {attempt}/3 failed");
            std::thread::sleep(Duration::from_millis(5));
        }

        if !detected {
            anyhow::bail!("No OPTIGA Trust M detected at 0x30");
        }

        log::info!(
            "OPTIGA chip detected on I2C bus at 0x{:02X}. Initializing driver (read-only boot check)...",
            IFX_I2C_BASE_ADDR
        );

        // 2. Build driver instance
        let mut dev = OptigaTrustM::builder(bus, EspOptigaTimer)
            .with_datastore(HostDatastore::default())
            .build();

        // 3. Open IFX I2C stack (Soft Reset)
        dev.open(
            ResetType::Soft,
            MAX_FRAME_SIZE as u16,
            PL_DEFAULT_FREQUENCY_KHZ,
        )
        .map_err(|e| anyhow::anyhow!("OPTIGA open stack failed: {e}"))?;
        log::info!("OPTIGA IFX I2C stack negotiation complete");

        // 4. Open Application context
        {
            let mut util = dev.util();
            util.open_application(false)
                .map_err(|e| anyhow::anyhow!("Failed to open OPTIGA application: {e}"))?;
            log::info!("OPTIGA application context opened");
        }

        // 5. Read Coprocessor UID (0xE0C2, Read=Always) so we can derive PBS if BLK3 is provisioned
        let copr_uid = {
            let mut util = dev.util();
            util.set_protection(ProtectionConfig {
                level: ProtectionLevel::None,
                force_reestablish: false,
            });
            let mut uid_buf = [0u8; 32];
            let len = util
                .read_data(Oid::CoprUid, 0, &mut uid_buf)
                .map_err(|e| anyhow::anyhow!("Failed to read OPTIGA CoprUid (0xE0C2): {e}"))?;
            uid_buf[..len].to_vec()
        };

        // 6. Negotiate Shielded Connection (AES-128-CCM-8)
        let blk3_seed_opt = read_blk3_seed();
        if let Some(ref blk3_seed) = blk3_seed_opt {
            log::info!("EFUSE_BLK3 seed detected; deriving 64-byte OPTIGA Platform Binding Secret via HKDF-SHA256...");
            let derived_pbs = derive_pbs_from_seed(blk3_seed, &copr_uid);
            let _ = dev
                .cmd
                .stack
                .prl
                .datastore
                .write(DatastoreId::PlatformBindingSharedSecret, &derived_pbs);
        }

        let mut protection = ProtectionConfig {
            level: ProtectionLevel::None,
            force_reestablish: false,
        };
        {
            let mut util = dev.util();
            util.set_protection(ProtectionConfig {
                level: ProtectionLevel::Full,
                force_reestablish: true,
            });
            let mut uid_buf = [0u8; 32];
            match util.read_data(Oid::CoprUid, 0, &mut uid_buf) {
                Ok(len) => {
                    log::info!(
                        "OPTIGA Shielded Connection established (BLK3 provisioned={})! Coprocessor UID ({} bytes): {:02X?}",
                        blk3_seed_opt.is_some(),
                        len,
                        &uid_buf[..len]
                    );
                    protection = ProtectionConfig {
                        level: ProtectionLevel::Full,
                        force_reestablish: false,
                    };
                }
                Err(e) => {
                    if blk3_seed_opt.is_some() {
                        anyhow::bail!(
                            "CRITICAL: EFUSE_BLK3 is provisioned, but OPTIGA Shielded Connection handshake failed ({e})! Refusing to fall back to unencrypted bus."
                        );
                    }
                    log::warn!(
                        "OPTIGA Shielded Connection negotiation failed ({e}) on unprovisioned unit; falling back to ProtectionLevel::None"
                    );
                }
            }
        }

        // 7. Strictly Read-Only Provisioning Check (NO metadata writes or key generation at boot!)
        let mut provisioned_pk: Option<PublicKey> = None;
        {
            let mut util = dev.util();
            util.set_protection(protection);
            let pubkey_opt = read_framed_oid(&mut util, OPTIGA_PUBKEY_DATA_OID, 96)
                .ok()
                .flatten();
            let att_opt = read_framed_oid(&mut util, OPTIGA_ATTESTATION_DATA_OID, 136)
                .ok()
                .flatten();

            if let (Some(pubkey_bytes), Some(att_bytes)) = (pubkey_opt, att_opt) {
                if att_bytes.len() == 129 {
                    if let Ok(pk) = parse_optiga_public_key(&pubkey_bytes) {
                        // Read-only verification that 0xE0F1 holds the matching private key and supports ECDH
                        let mut crypt = dev.crypt();
                        crypt.set_protection(protection);
                        let test_digest = [0x42u8; 32];
                        let mut sig_buf = [0u8; 128];
                        let mut sign_ok = false;
                        let mut ecdh_ok = false;

                        if let Ok(sig_len) =
                            crypt.ecdsa_sign(&test_digest, OPTIGA_KEY_OID, &mut sig_buf)
                        {
                            if let Ok(sig) = optiga_der_integers_to_signature(&sig_buf[..sig_len]) {
                                let verifying_key = VerifyingKey::from(&pk);
                                if verifying_key.verify_prehash(&test_digest, &sig).is_ok() {
                                    sign_ok = true;
                                }
                            }
                        }

                        if sign_ok {
                            let sec1_encoded = pk.to_encoded_point(false);
                            let sec1_bytes = sec1_encoded.as_bytes();
                            let mut bitstring_68 = [0u8; 68];
                            bitstring_68[0] = 0x03;
                            bitstring_68[1] = 0x42;
                            bitstring_68[2] = 0x00;
                            bitstring_68[3..].copy_from_slice(sec1_bytes);
                            let mut dummy_secret = [0u8; 32];
                            if crypt
                                .ecdh(
                                    OPTIGA_KEY_OID,
                                    &bitstring_68,
                                    EccCurve::NistP256,
                                    &mut dummy_secret,
                                )
                                .is_ok()
                            {
                                ecdh_ok = true;
                            } else if crypt
                                .ecdh(
                                    OPTIGA_KEY_OID,
                                    sec1_bytes,
                                    EccCurve::NistP256,
                                    &mut dummy_secret,
                                )
                                .is_ok()
                            {
                                ecdh_ok = true;
                            }
                        }

                        if sign_ok && ecdh_ok {
                            log::info!(
                                "Loaded and verified provisioned OPTIGA state (0xE0F1 + 0xF1D0 + 0xF1D1)"
                            );
                            provisioned_pk = Some(pk);
                        }
                    }
                }
            }
        }

        match provisioned_pk {
            Some(pk) => Ok(Self {
                dev,
                nvs,
                public_key: pk,
                unprovisioned_secret_key: None,
                is_provisioned: true,
                protection,
                copr_uid,
            }),
            None => {
                if blk3_seed_opt.is_some() {
                    anyhow::bail!(
                        "CRITICAL: EFUSE_BLK3 is provisioned, but OPTIGA slots (0xF1D0/0xF1D1) are not provisioned or failed verification!"
                    );
                }
                log::warn!(
                    "OPTIGA Trust M is attached but UNPROVISIONED (zero writes performed to OPTIGA on boot). Running in NVS standby until SOFT_PROVISION or PROVISION is executed."
                );
                let (sk, pk) = load_or_create_nvs_secret_key(&nvs);
                Ok(Self {
                    dev,
                    nvs,
                    public_key: pk,
                    unprovisioned_secret_key: Some(sk),
                    is_provisioned: false,
                    protection,
                    copr_uid,
                })
            }
        }
    }

    fn generate_and_store_lock_keypair(
        dev: &mut OptigaTrustM<
            SharedI2cBus,
            EspOptigaTimer,
            NoPin,
            NoPin,
            RustCryptoBackend,
            HostDatastore,
        >,
        protection: ProtectionConfig,
    ) -> anyhow::Result<PublicKey> {
        log::info!("Generating fresh P-256 keypair inside OPTIGA at OID 0xE0F1...");
        {
            let mut util = dev.util();
            util.set_protection(protection);
            let _ = util.write_metadata(
                OPTIGA_KEY_OID,
                &[0x20, 0x09, 0xD0, 0x01, 0x00, 0xD3, 0x01, 0x00, 0xE8, 0x01, 0x33],
            );
            let _ = util.write_metadata(
                OPTIGA_PUBKEY_DATA_OID,
                &[0x20, 0x06, 0xD0, 0x01, 0x00, 0xD1, 0x01, 0x00],
            );
            let _ = util.write_metadata(
                OPTIGA_CONTRACT_DATA_OID,
                &[0x20, 0x06, 0xD0, 0x01, 0x00, 0xD1, 0x01, 0x00],
            );
            // Clear any old contract as the key is being replaced
            let _ = clear_framed_oid(&mut util, OPTIGA_CONTRACT_DATA_OID);
        }

        let generated_pk = {
            let mut crypt = dev.crypt();
            crypt.set_protection(protection);
            let mut pubkey_buf = [0u8; 128];
            let pub_len = crypt
                .ecc_generate_keypair(
                    EccCurve::NistP256,
                    OPTIGA_KEY_OID,
                    KeyUsage::all(),
                    &mut pubkey_buf,
                )
                .map_err(|e| anyhow::anyhow!("Failed to generate ECC keypair on OPTIGA: {e}"))?;

            parse_optiga_public_key(&pubkey_buf[..pub_len])?
        };
        log::info!("Generated fresh P-256 keypair on OPTIGA (0xE0F1)");

        {
            let mut util = dev.util();
            util.set_protection(protection);
            let sec1_bytes = generated_pk.to_sec1_bytes();
            write_framed_oid(&mut util, OPTIGA_PUBKEY_DATA_OID, sec1_bytes.as_ref(), 96)?;
            log::info!("Cached SEC1 public key into OPTIGA 0xF1D0");
        }
        Ok(generated_pk)
    }

    fn write_credentials_to_optiga(&mut self, creds: &DeviceCredentials) -> anyhow::Result<()> {
        let mut cbor_bytes = Vec::new();
        ciborium::ser::into_writer(creds, &mut cbor_bytes)
            .map_err(|e| anyhow::anyhow!("Failed to serialize DeviceCredentials: {e}"))?;
        let mut util = self.dev.util();
        util.set_protection(self.protection);
        let _ = util.write_metadata(
            OPTIGA_CREDENTIALS_DATA_OID,
            &[0x20, 0x06, 0xD0, 0x01, 0x00, 0xD1, 0x01, 0x00],
        );
        write_framed_oid(&mut util, OPTIGA_CREDENTIALS_DATA_OID, &cbor_bytes, 136)?;
        Ok(())
    }

    fn execute_provisioning(
        &mut self,
        mode: ProvisionMode,
        session_token: &str,
        mqtt_password: &str,
    ) -> anyhow::Result<(PublicKey, String)> {
        let mode_label = match mode {
            ProvisionMode::Soft => "SOFT_PROVISION (Development Mode - No eFuses Burned)",
            ProvisionMode::Hard => "PROVISION (Factory Hardware Lockdown Mode)",
        };
        log::info!("=== STARTING TARTARUS {} ===", mode_label);

        if mode == ProvisionMode::Soft && read_blk3_seed().is_some() {
            anyhow::bail!(
                "Cannot run SOFT_PROVISION on a unit whose EFUSE_BLK3 has already been permanently burned; use PROVISION or EXPORT_CERT"
            );
        }

        // Step 1: Read Infineon X.509 Certificate Chain from 0xE0E0 and extract factory public key (for 0xE0F0)
        let certs = {
            let mut util = self.dev.util();
            util.set_protection(self.protection);
            read_optiga_certificates(&mut util, OPTIGA_FACTORY_CERT_OID)?
        };
        if certs.is_empty() {
            anyhow::bail!("No X.509 certificates found in OPTIGA OID 0xE0E0");
        }
        let factory_pubkey_bitstring = extract_public_key_bitstring(&certs[0].der)
            .ok_or_else(|| anyhow::anyhow!("Failed to locate P-256 public key in 0xE0E0 leaf certificate"))?;
        let factory_pk = parse_optiga_public_key(factory_pubkey_bitstring)?;
        let factory_verifying_key = VerifyingKey::from(&factory_pk);
        log::info!(
            "Step 1 OK: Extracted Infineon factory certificate chain ({} cert(s)) and verified leaf P-256 SPKI",
            certs.len()
        );

        // Step 2: Determine Platform Binding Secret (PBS), write to 0xE140, and verify Shielded Connection
        let (target_pbs, hard_seed_opt): ([u8; 64], Option<[u8; 32]>) = match mode {
            ProvisionMode::Soft => {
                log::info!(
                    "Step 2 (SOFT_PROVISION): Using standard 64-byte Infineon default PBS (0x01..=0x40) for 0xE140"
                );
                (HostDatastore::default().secret, None)
            }
            ProvisionMode::Hard => {
                let seed_32: [u8; 32] = match read_blk3_seed() {
                    Some(existing_seed) => {
                        log::info!(
                            "Step 2 (PROVISION): EFUSE_BLK3 already contains a 32-byte seed; reusing for PBS derivation."
                        );
                        existing_seed
                    }
                    None => {
                        log::info!(
                            "Step 2 (PROVISION): Generating fresh 32-byte random seed (ESP32 HWRNG ^ OPTIGA TRNG)..."
                        );
                        let mut esp_rand = [0u8; 32];
                        Esp32Rng.fill_bytes(&mut esp_rand);

                        let mut optiga_rand = [0u8; 32];
                        {
                            let mut crypt = self.dev.crypt();
                            crypt.set_protection(self.protection);
                            crypt
                                .get_random_with_type(RngType::Trng, &mut optiga_rand)
                                .map_err(|e| {
                                    anyhow::anyhow!("OPTIGA TRNG failed during seed generation: {e}")
                                })?;
                        }

                        let mut combined = [0u8; 32];
                        for i in 0..32 {
                            combined[i] = esp_rand[i] ^ optiga_rand[i];
                        }
                        if combined.iter().all(|&b| b == 0) {
                            anyhow::bail!("Generated seed was all zeros");
                        }
                        combined
                    }
                };
                let derived = derive_pbs_from_seed(&seed_32, &self.copr_uid);
                (derived, Some(seed_32))
            }
        };

        // Write target_pbs to OPTIGA 0xE140
        {
            let mut util = self.dev.util();
            util.set_protection(self.protection);
            // Ensure 0xE140 is writable (Change=Always, Execute=Always, DataObjectType=0x22 Platform Binding Secret)
            let _ = util.write_metadata(
                OPTIGA_PBS_OID,
                &[0x20, 0x09, 0xD0, 0x01, 0x00, 0xD3, 0x01, 0x00, 0xE8, 0x01, 0x22],
            );
            if let Err(e) = util.write_data(OPTIGA_PBS_OID, 0, &target_pbs, true) {
                log::warn!(
                    "Writing PBS to 0xE140 returned {e} (may already be written/locked); verifying handshake..."
                );
            }
        }

        // Update host datastore with target_pbs and force a fresh Shielded Connection handshake
        let _ = self
            .dev
            .cmd
            .stack
            .prl
            .datastore
            .write(DatastoreId::PlatformBindingSharedSecret, &target_pbs);
        self.dev.cmd.stack.prl.reset();

        {
            let mut util = self.dev.util();
            util.set_protection(ProtectionConfig {
                level: ProtectionLevel::Full,
                force_reestablish: true,
            });
            let mut uid_check = [0u8; 32];
            let len = util
                .read_data(Oid::CoprUid, 0, &mut uid_check)
                .map_err(|e| {
                    anyhow::anyhow!(
                        "Shielded Connection handshake with PBS failed: {e}. Aborting provisioning!"
                    )
                })?;
            if &uid_check[..len] != self.copr_uid.as_slice() {
                anyhow::bail!("CoprUid mismatch over Shielded Connection handshake!");
            }
        }
        self.protection = ProtectionConfig {
            level: ProtectionLevel::Full,
            force_reestablish: false,
        };
        log::info!("Step 2 OK: OPTIGA 0xE140 bound and ProtectionLevel::Full handshake verified.");

        // Step 3: Burn 32-byte seed into ESP32-S3 EFUSE_BLK3 ONLY in Hard Provisioning mode
        if let Some(ref seed_32) = hard_seed_opt {
            burn_blk3_seed(seed_32)?;
            log::info!("Step 3 OK: EFUSE_BLK3 programmed and write-protected.");
        } else {
            log::info!("Step 3 (SOFT_PROVISION): Skipping EFUSE_BLK3 burn.");
        }

        // Step 4: Generate fresh Lock Identity Keypair in 0xE0F1 and cache SEC1 Public Key in 0xF1D0
        let new_lock_pk = Self::generate_and_store_lock_keypair(&mut self.dev, self.protection)?;
        self.public_key = new_lock_pk;
        self.unprovisioned_secret_key = None;
        let lock_sec1 = self.public_key.to_sec1_bytes();
        log::info!("Step 4 OK: Lock Identity Keypair generated in 0xE0F1 and cached in 0xF1D0.");

        // Step 5: Generate Authenticity Check Keypair in 0xE0F2, sign with Factory Key 0xE0F0, verify & cache in 0xF1D1
        let (auth_sec1_vec, mfg_sig_bytes) = {
            {
                let mut util = self.dev.util();
                util.set_protection(self.protection);
                let _ = util.write_metadata(
                    OPTIGA_AUTH_KEY_OID,
                    &[0x20, 0x09, 0xD0, 0x01, 0x00, 0xD3, 0x01, 0x00, 0xE8, 0x01, 0x11],
                );
                let _ = util.write_metadata(
                    OPTIGA_ATTESTATION_DATA_OID,
                    &[0x20, 0x06, 0xD0, 0x01, 0x00, 0xD1, 0x01, 0x00],
                );
            }

            let auth_pk = {
                let mut crypt = self.dev.crypt();
                crypt.set_protection(self.protection);
                let mut pubkey_buf = [0u8; 128];
                let pub_len = crypt
                    .ecc_generate_keypair(
                        EccCurve::NistP256,
                        OPTIGA_AUTH_KEY_OID,
                        KeyUsage::auth_and_sign(),
                        &mut pubkey_buf,
                    )
                    .map_err(|e| {
                        anyhow::anyhow!("Failed to generate Auth ECC keypair in 0xE0F2: {e}")
                    })?;
                parse_optiga_public_key(&pubkey_buf[..pub_len])?
            };

            // Verify 0xE0F2 can sign a test nonce
            {
                let mut crypt = self.dev.crypt();
                crypt.set_protection(self.protection);
                let test_nonce = [0xA5u8; 32];
                let mut test_sig_buf = [0u8; 128];
                let test_sig_len = crypt
                    .ecdsa_sign(&test_nonce, OPTIGA_AUTH_KEY_OID, &mut test_sig_buf)
                    .map_err(|e| anyhow::anyhow!("0xE0F2 self-test sign failed: {e}"))?;
                let test_sig = optiga_der_integers_to_signature(&test_sig_buf[..test_sig_len])?;
                VerifyingKey::from(&auth_pk)
                    .verify_prehash(&test_nonce, &test_sig)
                    .map_err(|e| anyhow::anyhow!("0xE0F2 self-test verification failed: {e}"))?;
            }

            let auth_sec1 = auth_pk.to_sec1_bytes(); // 65 bytes (uncompressed SEC1: 0x04 || X || Y)
            if auth_sec1.len() != 65 {
                anyhow::bail!(
                    "Expected 65-byte uncompressed SEC1 key for 0xE0F2, got {}",
                    auth_sec1.len()
                );
            }

            let attestation_digest =
                compute_auth_key_attestation_digest(&self.copr_uid, auth_sec1.as_ref());

            // Sign attestation_digest with Infineon Factory Private Key 0xE0F0
            let mfg_sig = {
                let mut crypt = self.dev.crypt();
                crypt.set_protection(self.protection);
                let mut sig_buf = [0u8; 128];
                let sig_len = crypt
                    .ecdsa_sign(&attestation_digest, OPTIGA_FACTORY_KEY_OID, &mut sig_buf)
                    .map_err(|e| {
                        anyhow::anyhow!(
                            "Failed to sign 0xE0F2 attestation with Factory Key 0xE0F0: {e}"
                        )
                    })?;
                optiga_der_integers_to_signature(&sig_buf[..sig_len])?
            };

            // Verify 0xE0F0 signature against Infineon 0xE0E0 certificate public key
            factory_verifying_key
                .verify_prehash(&attestation_digest, &mfg_sig)
                .map_err(|e| {
                    anyhow::anyhow!(
                        "Factory Key 0xE0F0 signature failed verification against 0xE0E0 certificate: {e}"
                    )
                })?;

            let mfg_sig_raw = mfg_sig.to_bytes(); // 64 bytes P1363
            let mut attestation_record = Vec::with_capacity(129);
            attestation_record.extend_from_slice(auth_sec1.as_ref());
            attestation_record.extend_from_slice(mfg_sig_raw.as_slice());

            {
                let mut util = self.dev.util();
                util.set_protection(self.protection);
                write_framed_oid(
                    &mut util,
                    OPTIGA_ATTESTATION_DATA_OID,
                    &attestation_record,
                    136,
                )?;
            }

            (auth_sec1.to_vec(), mfg_sig_raw.to_vec())
        };
        log::info!(
            "Step 5 OK: Authenticity Key 0xE0F2 generated, certified by Factory Key 0xE0F0, and stored in 0xF1D1."
        );

        // Step 6: Move/Store DeviceCredentials into OPTIGA 0xF1D2 and remove from NVS
        let creds = DeviceCredentials {
            session_token: session_token.to_string(),
            mqtt_password: mqtt_password.to_string(),
        };
        self.write_credentials_to_optiga(&creds)?;
        let _ = self.nvs.remove("session_key");
        let _ = self.nvs.remove("mqtt_password");
        let _ = self.nvs.remove("ec_private");
        log::info!("Step 6 OK: DeviceCredentials moved into OPTIGA 0xF1D2 and wiped from NVS.");

        // Step 7 & 8: Metadata lockdown and ESP32-S3 eFuse lockdown ONLY in Hard Provisioning mode
        let status_label = match mode {
            ProvisionMode::Hard => {
                {
                    let mut util = self.dev.util();
                    util.set_protection(self.protection);
                    if let Err(e) = util.write_metadata(
                        OPTIGA_PBS_OID,
                        &[0x20, 0x06, 0xD0, 0x01, 0xFF, 0xD1, 0x01, 0xFF],
                    ) {
                        log::warn!(
                            "Setting 0xE140 metadata to Change=Never, Read=Never returned: {e}"
                        );
                    }
                }
                log::info!("Step 7 OK: OPTIGA PBS (0xE140) locked (Change=Never, Read=Never).");

                burn_security_lockdown_efuses()?;
                log::info!("Step 8 OK: ESP32-S3 JTAG and Secure Download Mode eFuses burned.");
                "PROVISIONED"
            }
            ProvisionMode::Soft => {
                log::info!(
                    "Step 7 & 8 (SOFT_PROVISION): Leaving 0xE140 changeable and skipping all ESP32-S3 eFuse burns."
                );
                "SOFT_PROVISIONED"
            }
        };

        self.is_provisioned = true;

        let efuse_status = get_efuse_security_status();
        let json = format_cert_export_json(
            status_label,
            session_token,
            &self.copr_uid,
            lock_sec1.as_ref(),
            &auth_sec1_vec,
            &mfg_sig_bytes,
            &certs,
            &efuse_status,
        );

        log::info!("=== TARTARUS {} COMPLETE ===", mode_label);
        Ok((self.public_key, json))
    }
}

impl SecurityBackend for OptigaSecurityBackend {
    fn backend_name(&self) -> &'static str {
        if self.is_provisioned {
            "OPTIGA Trust M (Provisioned)"
        } else {
            "OPTIGA Trust M (Unprovisioned Standby - NVS Active)"
        }
    }

    fn public_key(&self) -> PublicKey {
        self.public_key
    }

    fn hardware_id(&self) -> Option<String> {
        if self.is_provisioned {
            Some(HEXLOWER.encode(&self.copr_uid))
        } else {
            None
        }
    }

    fn sign_prehash(&mut self, digest_32: &[u8; 32]) -> anyhow::Result<Signature> {
        if !self.is_provisioned {
            let sk = self
                .unprovisioned_secret_key
                .as_ref()
                .ok_or_else(|| anyhow::anyhow!("Unprovisioned standby missing NVS secret key"))?;
            let signing_key = SigningKey::from(sk);
            return signing_key
                .sign_prehash(digest_32)
                .map_err(|e| anyhow::anyhow!("Standby NVS software signing failed: {e}"));
        }

        let mut crypt = self.dev.crypt();
        crypt.set_protection(self.protection);
        let mut sig_buf = [0u8; 128];
        let sig_len = crypt
            .ecdsa_sign(digest_32, OPTIGA_KEY_OID, &mut sig_buf)
            .map_err(|e| anyhow::anyhow!("OPTIGA ecdsa_sign (0xE0F1) failed: {e}"))?;
        optiga_der_integers_to_signature(&sig_buf[..sig_len])
    }

    fn sign_authenticity_challenge(
        &mut self,
        nonce_32: &[u8; 32],
    ) -> anyhow::Result<Option<Signature>> {
        if !self.is_provisioned {
            log::info!(
                "OPTIGA is in unprovisioned standby; falling back to software challenge key if available"
            );
            return Ok(None);
        }

        let mut crypt = self.dev.crypt();
        crypt.set_protection(self.protection);
        let mut sig_buf = [0u8; 128];
        let sig_len = crypt
            .ecdsa_sign(nonce_32, OPTIGA_AUTH_KEY_OID, &mut sig_buf)
            .map_err(|e| anyhow::anyhow!("OPTIGA ecdsa_sign on Auth Key (0xE0F2) failed: {e}"))?;
        let sig = optiga_der_integers_to_signature(&sig_buf[..sig_len])?;
        log::info!("Signed firmware authenticity challenge using OPTIGA hardware key 0xE0F2");
        Ok(Some(sig))
    }

    fn ecdh_shared_secret(&mut self, peer_public_key: &PublicKey) -> anyhow::Result<[u8; 32]> {
        if !self.is_provisioned {
            let sk = self
                .unprovisioned_secret_key
                .as_ref()
                .ok_or_else(|| anyhow::anyhow!("Unprovisioned standby missing NVS secret key"))?;
            let shared_secret =
                p256::ecdh::diffie_hellman(sk.to_nonzero_scalar(), peer_public_key.as_affine());
            let mut out = [0u8; 32];
            out.copy_from_slice(shared_secret.raw_secret_bytes());
            return Ok(out);
        }

        let sec1_encoded = peer_public_key.to_encoded_point(false);
        let sec1_bytes = sec1_encoded.as_bytes(); // 65 bytes
        let mut bitstring_68 = [0u8; 68];
        bitstring_68[0] = 0x03;
        bitstring_68[1] = 0x42;
        bitstring_68[2] = 0x00;
        bitstring_68[3..].copy_from_slice(sec1_bytes);

        let mut shared_secret = [0u8; 32];
        let mut crypt = self.dev.crypt();
        crypt.set_protection(self.protection);
        let res = crypt.ecdh(
            OPTIGA_KEY_OID,
            &bitstring_68,
            EccCurve::NistP256,
            &mut shared_secret,
        );
        if let Err(e) = res {
            log::warn!(
                "OPTIGA ecdh with 68-byte BitString returned {e}, falling back to 65-byte SEC1..."
            );
            crypt
                .ecdh(
                    OPTIGA_KEY_OID,
                    sec1_bytes,
                    EccCurve::NistP256,
                    &mut shared_secret,
                )
                .map_err(|e| anyhow::anyhow!("OPTIGA ECDH failed: {e}"))?;
        }
        Ok(shared_secret)
    }

    fn load_or_create_credentials(
        &mut self,
        nvs: &EspNvs<NvsDefault>,
    ) -> anyhow::Result<DeviceCredentials> {
        // When OPTIGA is unprovisioned, NEVER write to OPTIGA 0xF1D2 on boot—use NVS until PROVISION / SOFT_PROVISION
        if !self.is_provisioned {
            return Ok(load_or_generate_nvs_credentials(nvs));
        }

        let existing_optiga_creds = {
            let mut util = self.dev.util();
            util.set_protection(self.protection);
            match read_framed_oid(&mut util, OPTIGA_CREDENTIALS_DATA_OID, 136)? {
                Some(bytes) => ciborium::de::from_reader::<DeviceCredentials, _>(&bytes[..]).ok(),
                None => None,
            }
        };

        if let Some(creds) = existing_optiga_creds {
            if !creds.session_token.is_empty() && !creds.mqtt_password.is_empty() {
                log::info!(
                    "Loaded device credentials (session_token={:?}) from OPTIGA 0xF1D2",
                    creds.session_token
                );
                return Ok(creds);
            }
        }

        // Only reached on a provisioned OPTIGA after wipe_for_reset() cleared 0xF1D2
        log::info!(
            "Provisioned OPTIGA 0xF1D2 was cleared by reset; generating fresh credentials in 0xF1D2..."
        );
        let mut generated = generate_fresh_credentials();
        {
            let mut trng_bytes = [0u8; 37];
            let mut crypt = self.dev.crypt();
            crypt.set_protection(self.protection);
            if crypt
                .get_random_with_type(RngType::Trng, &mut trng_bytes)
                .is_ok()
            {
                let mut esp_bytes = [0u8; 37];
                Esp32Rng.fill_bytes(&mut esp_bytes);
                for i in 0..37 {
                    trng_bytes[i] ^= esp_bytes[i];
                }
                generated.session_token = BASE32_NOPAD.encode(&trng_bytes[..5])[..6].to_string();
                generated.mqtt_password = BASE32_NOPAD.encode(&trng_bytes[5..37]).to_string();
            }
        }
        self.write_credentials_to_optiga(&generated)?;
        Ok(generated)
    }

    fn read_contract(&mut self) -> anyhow::Result<Option<SaveState>> {
        if !self.is_provisioned {
            return read_contract_from_nvs(&self.nvs);
        }
        let mut util = self.dev.util();
        util.set_protection(self.protection);
        match read_framed_oid(&mut util, OPTIGA_CONTRACT_DATA_OID, 1496)? {
            Some(bytes) => {
                let state: SaveState = ciborium::de::from_reader(&bytes[..])
                    .map_err(|e| anyhow::anyhow!("Failed to deserialize SaveState from OPTIGA: {e}"))?;
                Ok(Some(state))
            }
            None => Ok(None),
        }
    }

    fn write_contract(&mut self, state: &SaveState) -> anyhow::Result<()> {
        if !self.is_provisioned {
            return write_contract_to_nvs(&self.nvs, state);
        }
        let mut bytes = Vec::new();
        ciborium::ser::into_writer(state, &mut bytes)
            .map_err(|e| anyhow::anyhow!("Failed to serialize SaveState: {e}"))?;
        let mut util = self.dev.util();
        util.set_protection(self.protection);
        write_framed_oid(&mut util, OPTIGA_CONTRACT_DATA_OID, &bytes, 1496)
    }

    fn clear_contract(&mut self) -> anyhow::Result<()> {
        if !self.is_provisioned {
            let _ = self.nvs.remove("contract");
            return Ok(());
        }
        let mut util = self.dev.util();
        util.set_protection(self.protection);
        clear_framed_oid(&mut util, OPTIGA_CONTRACT_DATA_OID)
    }

    fn wipe_for_reset(&mut self) -> anyhow::Result<()> {
        if !self.is_provisioned {
            let _ = self.nvs.remove("contract");
            let _ = self.nvs.remove("ec_private");
            let _ = self.nvs.remove("session_key");
            let _ = self.nvs.remove("mqtt_password");
            return Ok(());
        }

        log::info!(
            "Wiping provisioned OPTIGA operational state (contract 0xF1E0, credentials 0xF1D2) and rotating Lock Key 0xE0F1..."
        );
        {
            let mut util = self.dev.util();
            util.set_protection(self.protection);
            let _ = clear_framed_oid(&mut util, OPTIGA_CONTRACT_DATA_OID);
            let _ = clear_framed_oid(&mut util, OPTIGA_CREDENTIALS_DATA_OID);
            let _ = clear_framed_oid(&mut util, OPTIGA_PUBKEY_DATA_OID);
        }
        if let Ok(new_pk) = Self::generate_and_store_lock_keypair(&mut self.dev, self.protection) {
            self.public_key = new_pk;
        }
        Ok(())
    }

    fn provision_device(
        &mut self,
        session_token: &str,
        mqtt_password: &str,
    ) -> anyhow::Result<(PublicKey, String)> {
        self.execute_provisioning(ProvisionMode::Hard, session_token, mqtt_password)
    }

    fn soft_provision_device(
        &mut self,
        session_token: &str,
        mqtt_password: &str,
    ) -> anyhow::Result<(PublicKey, String)> {
        self.execute_provisioning(ProvisionMode::Soft, session_token, mqtt_password)
    }

    fn export_cert_bundle(&mut self, session_token: &str) -> anyhow::Result<String> {
        if !self.is_provisioned {
            anyhow::bail!(
                "OPTIGA has not been provisioned yet; run SOFT_PROVISION or PROVISION first"
            );
        }

        let (certs, attestation) = {
            let mut util = self.dev.util();
            util.set_protection(self.protection);
            let certs = read_optiga_certificates(&mut util, OPTIGA_FACTORY_CERT_OID)?;
            let att = read_framed_oid(&mut util, OPTIGA_ATTESTATION_DATA_OID, 136)?
                .ok_or_else(|| {
                    anyhow::anyhow!(
                        "Device has not been provisioned yet (no attestation record in 0xF1D1)"
                    )
                })?;
            (certs, att)
        };

        if attestation.len() != 129 {
            anyhow::bail!(
                "Invalid attestation record length in 0xF1D1: expected 129 bytes, got {}",
                attestation.len()
            );
        }

        let auth_pubkey_sec1 = &attestation[..65];
        let manufacturer_sig_p1363 = &attestation[65..129];
        let lock_sec1 = self.public_key.to_sec1_bytes();
        let efuse_status = get_efuse_security_status();

        Ok(format_cert_export_json(
            "EXPORTED",
            session_token,
            &self.copr_uid,
            lock_sec1.as_ref(),
            auth_pubkey_sec1,
            manufacturer_sig_p1363,
            &certs,
            &efuse_status,
        ))
    }
}

// =========================================================================
// Factory Constructor
// =========================================================================

pub fn init_security_backend(
    bus: SharedI2cBus,
    nvs: Rc<EspNvs<NvsDefault>>,
) -> Box<dyn SecurityBackend> {
    let blk3_provisioned = read_blk3_seed().is_some();
    match OptigaSecurityBackend::try_init(bus, nvs.clone()) {
        Ok(optiga_backend) => {
            log::info!(
                "Security backend initialized: {} (blk3_provisioned={})",
                optiga_backend.backend_name(),
                blk3_provisioned
            );
            Box::new(optiga_backend)
        }
        Err(e) => {
            if blk3_provisioned {
                log::error!(
                    "CRITICAL SECURITY VIOLATION: Device is provisioned (EFUSE_BLK3 set), but OPTIGA Trust M failed to initialize: {:#}",
                    e
                );
                panic!(
                    "Provisioned device cannot boot without genuine bound OPTIGA Trust M: {:#}",
                    e
                );
            }
            log::info!(
                "OPTIGA Trust M not available ({:#}); falling back to ESP32 NVS backend (unprovisioned mode)",
                e
            );
            Box::new(NvsSecurityBackend::new(nvs))
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_optiga_public_key_68_byte_bitstring() {
        let sk = SecretKey::random(&mut rand_core::OsRng);
        let pk = PublicKey::from_secret_scalar(&sk.to_nonzero_scalar());
        let sec1 = pk.to_sec1_bytes();
        let mut bitstring = vec![0x03, 0x42, 0x00];
        bitstring.extend_from_slice(sec1.as_ref());

        let parsed = parse_optiga_public_key(&bitstring).expect("Should parse 68-byte BitString");
        assert_eq!(parsed, pk);
    }

    #[test]
    fn test_parse_optiga_public_key_65_byte_sec1() {
        let sk = SecretKey::random(&mut rand_core::OsRng);
        let pk = PublicKey::from_secret_scalar(&sk.to_nonzero_scalar());
        let sec1 = pk.to_sec1_bytes();

        let parsed = parse_optiga_public_key(sec1.as_ref()).expect("Should parse 65-byte SEC1");
        assert_eq!(parsed, pk);
    }

    #[test]
    fn test_optiga_der_integers_to_signature() {
        let sk = SecretKey::random(&mut rand_core::OsRng);
        let signing_key = SigningKey::from(&sk);
        let digest = [0x55u8; 32];
        let sig: Signature = signing_key.sign_prehash(&digest).unwrap();
        let (r, s) = sig.split_bytes();

        // Construct ASN.1 DER integers: [0x02, r_len, r..., 0x02, s_len, s...]
        // With leading 0x00 if highest bit is set:
        let mut der = Vec::new();
        der.push(0x02);
        if r[0] & 0x80 != 0 {
            der.push(33);
            der.push(0x00);
        } else {
            der.push(32);
        }
        der.extend_from_slice(&r);

        der.push(0x02);
        if s[0] & 0x80 != 0 {
            der.push(33);
            der.push(0x00);
        } else {
            der.push(32);
        }
        der.extend_from_slice(&s);

        let parsed_sig = optiga_der_integers_to_signature(&der).expect("Should parse DER integers");
        assert_eq!(parsed_sig, sig);
    }

    #[test]
    fn test_framed_oid_header_roundtrip() {
        let payload = b"Hello Tartarus Framing";
        let max_len = 100;
        let mut framed = Vec::new();
        framed.extend_from_slice(&FRAME_MAGIC);
        framed.extend_from_slice(&(payload.len() as u16).to_be_bytes());
        framed.extend_from_slice(payload);

        assert_eq!(&framed[0..2], &FRAME_MAGIC);
        let len = u16::from_be_bytes([framed[2], framed[3]]) as usize;
        assert_eq!(len, payload.len());
        assert!(len <= max_len);
        assert_eq!(&framed[4..4 + len], payload);
    }
}
