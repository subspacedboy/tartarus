use std::rc::Rc;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use data_encoding::BASE64;
use embedded_hal::delay::DelayNs;
use embedded_hal::digital::OutputPin;
use embedded_hal::i2c::{ErrorType, I2c, Operation};
use esp_idf_hal::delay::Delay;
use esp_idf_hal::i2c::{I2cDriver, I2cError};
use esp_idf_svc::nvs::{EspNvs, NvsDefault};
use optiga_trust_m_rs::config::{IFX_I2C_BASE_ADDR, MAX_FRAME_SIZE, PL_DEFAULT_FREQUENCY_KHZ};
use optiga_trust_m_rs::ifx_i2c::{PlRegister, ProtectionConfig, ProtectionLevel, ResetType};
use optiga_trust_m_rs::pal::rust_crypto::RustCryptoBackend;
use optiga_trust_m_rs::pal::{Datastore, DatastoreId, HostCrypto, MonotonicClock, NoPin, OptigaI2cBus, OptigaTimer};
use optiga_trust_m_rs::{EccCurve, KeyUsage, Oid, OptigaTrustM, OptigaUtil};
use p256::ecdsa::signature::hazmat::{PrehashSigner, PrehashVerifier};
use p256::ecdsa::{Signature, SigningKey, VerifyingKey};
use p256::elliptic_curve::sec1::ToEncodedPoint;
use p256::{PublicKey, SecretKey};
use rand_core::{CryptoRng, RngCore};

use crate::internal_contract::SaveState;

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

    pub fn read_with_timeout(&self, addr: u8, buf: &mut [u8], timeout_ticks: u32) -> Result<(), esp_idf_svc::sys::EspError> {
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

pub const OPTIGA_KEY_OID: Oid = Oid::DevicePrivateKey1;
pub const OPTIGA_PUBKEY_DATA_OID: Oid = Oid::Custom(0xF1D0);
pub const OPTIGA_CONTRACT_DATA_OID: Oid = Oid::Custom(0xF1E0);
pub const FRAME_MAGIC: [u8; 2] = [0x54, 0x41]; // "TA"

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
    /// Human-readable backend name for logging ("OPTIGA Trust M" or "ESP32 NVS").
    fn backend_name(&self) -> &'static str;

    /// Returns the lock's NIST P-256 public key.
    fn public_key(&self) -> PublicKey;

    /// Signs a 32-byte SHA-256 digest using the lock's P-256 private key,
    /// returning a 64-byte raw IEEE P1363 signature.
    fn sign_prehash(&mut self, digest_32: &[u8; 32]) -> anyhow::Result<Signature>;

    /// Computes the 32-byte ECDH shared secret between the lock's private key
    /// and `peer_public_key`.
    fn ecdh_shared_secret(&mut self, peer_public_key: &PublicKey) -> anyhow::Result<[u8; 32]>;

    /// Reads the saved contract state if present.
    fn read_contract(&mut self) -> anyhow::Result<Option<SaveState>>;

    /// Persists the contract state.
    fn write_contract(&mut self, state: &SaveState) -> anyhow::Result<()>;

    /// Removes/clears the persisted contract state.
    fn clear_contract(&mut self) -> anyhow::Result<()>;

    /// Wipes keys and contract state prior to a full device reset.
    fn wipe_for_reset(&mut self) -> anyhow::Result<()>;
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
            log::info!("Generating a new EC private key in software...");
            let sk = SecretKey::random(&mut rng);
            let key_bytes = sk.to_bytes();
            let encoded_key = BASE64.encode(&key_bytes);
            nvs.set_blob(key_name, encoded_key.as_bytes()).unwrap();
            log::info!("Saved new EC private key to NVS.");
            sk
        };

        let public_key = PublicKey::from_secret_scalar(&secret_key.to_nonzero_scalar());
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

    fn sign_prehash(&mut self, digest_32: &[u8; 32]) -> anyhow::Result<Signature> {
        let signing_key = SigningKey::from(&self.secret_key);
        let sig: Signature = signing_key
            .sign_prehash(digest_32)
            .map_err(|e| anyhow::anyhow!("NVS software signing failed: {e}"))?;
        Ok(sig)
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

    fn read_contract(&mut self) -> anyhow::Result<Option<SaveState>> {
        let key_name = "contract";
        let required_size = match self.nvs.blob_len(key_name)? {
            Some(len) => len,
            None => {
                log::info!("No contract data was present in NVS");
                return Ok(None);
            }
        };
        let mut buffer = vec![0u8; required_size];
        match self.nvs.get_blob(key_name, &mut buffer) {
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

    fn write_contract(&mut self, state: &SaveState) -> anyhow::Result<()> {
        let mut bytes = Vec::new();
        ciborium::ser::into_writer(state, &mut bytes)
            .map_err(|e| anyhow::anyhow!("Failed to serialize contract: {e}"))?;
        self.nvs
            .set_blob("contract", &bytes)
            .map_err(|e| anyhow::anyhow!("Failed to write contract to NVS: {e}"))?;
        Ok(())
    }

    fn clear_contract(&mut self) -> anyhow::Result<()> {
        let _ = self.nvs.remove("contract");
        Ok(())
    }

    fn wipe_for_reset(&mut self) -> anyhow::Result<()> {
        let _ = self.nvs.remove("contract");
        let _ = self.nvs.remove("ec_private");
        Ok(())
    }
}

// =========================================================================
// OPTIGA Trust M Hardware Security Backend
// =========================================================================

pub struct OptigaSecurityBackend {
    dev: OptigaTrustM<SharedI2cBus, EspOptigaTimer, NoPin, NoPin, RustCryptoBackend, HostDatastore>,
    public_key: PublicKey,
    protection: ProtectionConfig,
}

impl OptigaSecurityBackend {
    pub fn try_init(mut bus: SharedI2cBus) -> anyhow::Result<Self> {
        // 1. Fast I2C Probe: Try up to 3 times to write PlRegister::I2cState (0x82) to 0x30
        let mut detected = false;
        for attempt in 1..=3 {
            if embedded_hal::i2c::I2c::write(&mut bus, IFX_I2C_BASE_ADDR, &[PlRegister::I2cState as u8]).is_ok() {
                detected = true;
                break;
            }
            log::debug!("OPTIGA probe attempt {attempt}/3 failed");
            std::thread::sleep(Duration::from_millis(5));
        }

        if !detected {
            anyhow::bail!("No OPTIGA Trust M detected at 0x30");
        }

        log::info!("OPTIGA chip detected on I2C bus at 0x{:02X}. Initializing driver...", IFX_I2C_BASE_ADDR);

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

        // 5. Negotiate Shielded Connection (AES-128-CCM-8)
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
                        "OPTIGA Shielded Connection established! Coprocessor UID ({} bytes): {:02X?}",
                        len,
                        &uid_buf[..len]
                    );
                    protection = ProtectionConfig {
                        level: ProtectionLevel::Full,
                        force_reestablish: false,
                    };
                }
                Err(e) => {
                    log::warn!(
                        "OPTIGA Shielded Connection negotiation failed ({e}), falling back to ProtectionLevel::None"
                    );
                }
            }
        }

        // 6. Ensure Metadata & Load or Generate ECC Keypair
        // Ensure permissive metadata on key slot 0xE0F1 and data objects 0xF1D0, 0xF1E0
        {
            let mut util = dev.util();
            util.set_protection(protection);
            // 0xE0F1: Change=Always, Execute=Always, KeyUsage=0x33 (AUTH | ENCRYPT | SIGN | KEY_AGREEMENT)
            let _ = util.write_metadata(
                OPTIGA_KEY_OID,
                &[0x20, 0x09, 0xD0, 0x01, 0x00, 0xD3, 0x01, 0x00, 0xE8, 0x01, 0x33],
            );
            // 0xF1D0: Change=Always, Read=Always
            let _ = util.write_metadata(
                OPTIGA_PUBKEY_DATA_OID,
                &[0x20, 0x06, 0xD0, 0x01, 0x00, 0xD1, 0x01, 0x00],
            );
            // 0xF1E0: Change=Always, Read=Always
            let _ = util.write_metadata(
                OPTIGA_CONTRACT_DATA_OID,
                &[0x20, 0x06, 0xD0, 0x01, 0x00, 0xD1, 0x01, 0x00],
            );
        }

        let mut public_key: Option<PublicKey> = None;
        {
            let mut util = dev.util();
            util.set_protection(protection);
            if let Ok(Some(pubkey_bytes)) = read_framed_oid(&mut util, OPTIGA_PUBKEY_DATA_OID, 96) {
                if let Ok(pk) = parse_optiga_public_key(&pubkey_bytes) {
                    // Verify that 0xE0F1 actually holds the matching private key AND supports ECDH
                    let mut crypt = dev.crypt();
                    crypt.set_protection(protection);
                    let test_digest = [0x42u8; 32];
                    let mut sig_buf = [0u8; 128];
                    let mut sign_ok = false;
                    let mut ecdh_ok = false;

                    if let Ok(sig_len) = crypt.ecdsa_sign(&test_digest, OPTIGA_KEY_OID, &mut sig_buf) {
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
                        if crypt.ecdh(OPTIGA_KEY_OID, &bitstring_68, EccCurve::NistP256, &mut dummy_secret).is_ok() {
                            ecdh_ok = true;
                        } else if crypt.ecdh(OPTIGA_KEY_OID, sec1_bytes, EccCurve::NistP256, &mut dummy_secret).is_ok() {
                            ecdh_ok = true;
                        }
                    }

                    if sign_ok && ecdh_ok {
                        log::info!("Loaded and cryptographically verified OPTIGA P-256 public key (ECDSA + ECDH) from 0xF1D0");
                        public_key = Some(pk);
                    } else if sign_ok {
                        log::warn!("Stored key in 0xE0F1 supports signing but failed ECDH (missing KEY_AGREEMENT usage); regenerating keypair...");
                    } else {
                        log::warn!("Stored public key does not match private key in 0xE0F1; regenerating keypair...");
                    }
                }
            }
        }

        let pk = match public_key {
            Some(k) => k,
            None => {
                log::info!("Generating fresh P-256 keypair inside OPTIGA at OID 0xE0F1...");
                let mut util = dev.util();
                util.set_protection(protection);
                let _ = util.write_metadata(
                    OPTIGA_KEY_OID,
                    &[0x20, 0x09, 0xD0, 0x01, 0x00, 0xD3, 0x01, 0x00, 0xE8, 0x01, 0x33],
                );
                // Clear any old contract as the key is being replaced
                let _ = clear_framed_oid(&mut util, OPTIGA_CONTRACT_DATA_OID);

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

                let generated_pk = parse_optiga_public_key(&pubkey_buf[..pub_len])?;
                log::info!("Generated fresh P-256 keypair on OPTIGA");

                let mut util = dev.util();
                util.set_protection(protection);
                let sec1_bytes = generated_pk.to_sec1_bytes();
                write_framed_oid(&mut util, OPTIGA_PUBKEY_DATA_OID, sec1_bytes.as_ref(), 96)?;
                log::info!("Cached SEC1 public key into OPTIGA 0xF1D0");
                generated_pk
            }
        };

        Ok(Self {
            dev,
            public_key: pk,
            protection,
        })
    }
}

impl SecurityBackend for OptigaSecurityBackend {
    fn backend_name(&self) -> &'static str {
        "OPTIGA Trust M"
    }

    fn public_key(&self) -> PublicKey {
        self.public_key
    }

    fn sign_prehash(&mut self, digest_32: &[u8; 32]) -> anyhow::Result<Signature> {
        let mut crypt = self.dev.crypt();
        crypt.set_protection(self.protection);
        let mut sig_buf = [0u8; 128];
        let sig_len = crypt
            .ecdsa_sign(digest_32, OPTIGA_KEY_OID, &mut sig_buf)
            .map_err(|e| anyhow::anyhow!("OPTIGA ecdsa_sign failed: {e}"))?;
        optiga_der_integers_to_signature(&sig_buf[..sig_len])
    }

    fn ecdh_shared_secret(&mut self, peer_public_key: &PublicKey) -> anyhow::Result<[u8; 32]> {
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
        let res = crypt.ecdh(OPTIGA_KEY_OID, &bitstring_68, EccCurve::NistP256, &mut shared_secret);
        if let Err(e) = res {
            log::warn!("OPTIGA ecdh with 68-byte BitString returned {e}, falling back to 65-byte SEC1...");
            crypt
                .ecdh(OPTIGA_KEY_OID, sec1_bytes, EccCurve::NistP256, &mut shared_secret)
                .map_err(|e| anyhow::anyhow!("OPTIGA ECDH failed: {e}"))?;
        }
        Ok(shared_secret)
    }

    fn read_contract(&mut self) -> anyhow::Result<Option<SaveState>> {
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
        let mut bytes = Vec::new();
        ciborium::ser::into_writer(state, &mut bytes)
            .map_err(|e| anyhow::anyhow!("Failed to serialize SaveState: {e}"))?;
        let mut util = self.dev.util();
        util.set_protection(self.protection);
        write_framed_oid(&mut util, OPTIGA_CONTRACT_DATA_OID, &bytes, 1496)
    }

    fn clear_contract(&mut self) -> anyhow::Result<()> {
        let mut util = self.dev.util();
        util.set_protection(self.protection);
        clear_framed_oid(&mut util, OPTIGA_CONTRACT_DATA_OID)
    }

    fn wipe_for_reset(&mut self) -> anyhow::Result<()> {
        let mut util = self.dev.util();
        util.set_protection(self.protection);
        let _ = clear_framed_oid(&mut util, OPTIGA_CONTRACT_DATA_OID);
        let _ = clear_framed_oid(&mut util, OPTIGA_PUBKEY_DATA_OID);
        Ok(())
    }
}

// =========================================================================
// Factory Constructor
// =========================================================================

pub fn init_security_backend(
    bus: SharedI2cBus,
    nvs: Rc<EspNvs<NvsDefault>>,
) -> Box<dyn SecurityBackend> {
    match OptigaSecurityBackend::try_init(bus) {
        Ok(optiga_backend) => {
            log::info!("Security backend initialized: OPTIGA Trust M (hardware-backed)");
            Box::new(optiga_backend)
        }
        Err(e) => {
            log::info!(
                "OPTIGA Trust M not available ({:#}); falling back to ESP32 NVS backend",
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
        let mut raw_68 = vec![0x03, 0x42, 0x00, 0x04];
        raw_68.extend_from_slice(&[0x11; 32]); // X
        raw_68.extend_from_slice(&[0x22; 32]); // Y
        assert_eq!(raw_68.len(), 68);

        // May fail SEC1 validation if point not on curve, so generate a valid point first:
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
