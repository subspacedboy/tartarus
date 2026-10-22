use data_encoding::{BASE64, HEXLOWER};
use embedded_hal::digital::OutputPin;
use hkdf::Hkdf;
use optiga_trust_m_rs::pal::{Datastore, HostCrypto, OptigaI2cBus, OptigaTimer};
use optiga_trust_m_rs::{Oid, OptigaUtil};
use sha2::{Digest, Sha256};

use esp_idf_svc::sys::{
    esp_efuse_batch_write_begin, esp_efuse_batch_write_cancel, esp_efuse_batch_write_commit,
    esp_efuse_block_is_empty, esp_efuse_block_t_EFUSE_BLK3,
    esp_efuse_enable_rom_secure_download_mode, esp_efuse_read_block, esp_efuse_read_field_bit,
    esp_efuse_read_field_cnt, esp_efuse_read_secure_version, esp_efuse_set_write_protect,
    esp_efuse_write_block, esp_efuse_write_field_bit, esp_efuse_write_field_cnt, ESP_OK,
    ESP_EFUSE_DIS_PAD_JTAG, ESP_EFUSE_DIS_USB_JTAG, ESP_EFUSE_DIS_USB_SERIAL_JTAG_DOWNLOAD_MODE,
    ESP_EFUSE_ENABLE_SECURITY_DOWNLOAD, ESP_EFUSE_SECURE_BOOT_EN, ESP_EFUSE_SOFT_DIS_JTAG,
    ESP_EFUSE_WR_DIS_BLOCK_USR_DATA,
};

pub const PBS_HKDF_INFO: &[u8] = b"tartarus-optiga-pbs-v1";
pub const AUTH_ATTESTATION_DOMAIN: &[u8] = b"TARTARUS-AUTH-KEY-V1:";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EfuseSecurityStatus {
    pub blk3_provisioned: bool,
    pub blk3_write_protected: bool,
    pub secure_boot_enabled: bool,
    pub pad_jtag_disabled: bool,
    pub usb_jtag_disabled: bool,
    pub soft_jtag_disabled_cnt: usize,
    pub security_download_enabled: bool,
    pub usb_download_disabled: bool,
    pub secure_version: u32,
}

impl EfuseSecurityStatus {
    pub fn soft_jtag_fully_disabled(&self) -> bool {
        self.soft_jtag_disabled_cnt >= 3
    }
}

pub fn get_efuse_security_status() -> EfuseSecurityStatus {
    unsafe {
        let blk3_empty = esp_efuse_block_is_empty(esp_efuse_block_t_EFUSE_BLK3);
        let blk3_write_protected = esp_efuse_read_field_bit(
            core::ptr::addr_of_mut!(ESP_EFUSE_WR_DIS_BLOCK_USR_DATA) as *mut _,
        );
        let secure_boot_enabled =
            esp_efuse_read_field_bit(core::ptr::addr_of_mut!(ESP_EFUSE_SECURE_BOOT_EN) as *mut _);
        let pad_jtag_disabled =
            esp_efuse_read_field_bit(core::ptr::addr_of_mut!(ESP_EFUSE_DIS_PAD_JTAG) as *mut _);
        let usb_jtag_disabled =
            esp_efuse_read_field_bit(core::ptr::addr_of_mut!(ESP_EFUSE_DIS_USB_JTAG) as *mut _);

        let mut soft_jtag_cnt: usize = 0;
        let _ = esp_efuse_read_field_cnt(
            core::ptr::addr_of_mut!(ESP_EFUSE_SOFT_DIS_JTAG) as *mut _,
            &mut soft_jtag_cnt as *mut usize,
        );

        let security_download_enabled = esp_efuse_read_field_bit(
            core::ptr::addr_of_mut!(ESP_EFUSE_ENABLE_SECURITY_DOWNLOAD) as *mut _,
        );
        let usb_download_disabled = esp_efuse_read_field_bit(
            core::ptr::addr_of_mut!(ESP_EFUSE_DIS_USB_SERIAL_JTAG_DOWNLOAD_MODE) as *mut _,
        );
        let secure_version = esp_efuse_read_secure_version();

        EfuseSecurityStatus {
            blk3_provisioned: !blk3_empty,
            blk3_write_protected,
            secure_boot_enabled,
            pad_jtag_disabled,
            usb_jtag_disabled,
            soft_jtag_disabled_cnt: soft_jtag_cnt,
            security_download_enabled,
            usb_download_disabled,
            secure_version,
        }
    }
}

/// Reads the 32-byte (256-bit) Platform Binding Secret seed from ESP32-S3 `EFUSE_BLK3`
/// (`ESP_EFUSE_USER_DATA`) if `BLK3` has been programmed.
/// Returns `None` if `BLK3` is all-zero (unprovisioned).
pub fn read_blk3_seed() -> Option<[u8; 32]> {
    unsafe {
        if esp_efuse_block_is_empty(esp_efuse_block_t_EFUSE_BLK3) {
            return None;
        }
        let mut seed = [0u8; 32];
        let err = esp_efuse_read_block(
            esp_efuse_block_t_EFUSE_BLK3,
            seed.as_mut_ptr() as *mut core::ffi::c_void,
            0,
            256,
        );
        if err != ESP_OK {
            log::error!("Failed to read EFUSE_BLK3: esp_err_t = 0x{:x}", err);
            return None;
        }
        if seed.iter().all(|&b| b == 0) {
            return None;
        }
        Some(seed)
    }
}

/// Burns the 32-byte Platform Binding Secret seed into `EFUSE_BLK3` (`ESP_EFUSE_USER_DATA`)
/// and permanently write-protects `BLK3` (`WR_DIS.BLK3`), leaving `RD_DIS.BLK3 = 0` so the
/// MCU can read it at boot to derive the OPTIGA Shielded Connection secret.
pub fn burn_blk3_seed(seed: &[u8; 32]) -> anyhow::Result<()> {
    if seed.iter().all(|&b| b == 0) {
        anyhow::bail!("Refusing to burn an all-zero seed into EFUSE_BLK3");
    }

    if let Some(existing) = read_blk3_seed() {
        if &existing == seed {
            log::info!("EFUSE_BLK3 already contains the expected seed; ensuring write protection.");
            unsafe {
                let status = get_efuse_security_status();
                if !status.blk3_write_protected {
                    let err = esp_efuse_set_write_protect(esp_efuse_block_t_EFUSE_BLK3);
                    if err != ESP_OK {
                        anyhow::bail!(
                            "Failed to set write protection on EFUSE_BLK3: 0x{:x}",
                            err
                        );
                    }
                }
            }
            return Ok(());
        } else {
            anyhow::bail!(
                "EFUSE_BLK3 is already programmed with a different seed; cannot overwrite!"
            );
        }
    }

    unsafe {
        let err = esp_efuse_batch_write_begin();
        if err != ESP_OK {
            anyhow::bail!("esp_efuse_batch_write_begin failed: 0x{:x}", err);
        }

        let write_err = esp_efuse_write_block(
            esp_efuse_block_t_EFUSE_BLK3,
            seed.as_ptr() as *const core::ffi::c_void,
            0,
            256,
        );
        if write_err != ESP_OK {
            let _ = esp_efuse_batch_write_cancel();
            anyhow::bail!("esp_efuse_write_block(EFUSE_BLK3) failed: 0x{:x}", write_err);
        }

        let wp_err = esp_efuse_set_write_protect(esp_efuse_block_t_EFUSE_BLK3);
        if wp_err != ESP_OK {
            let _ = esp_efuse_batch_write_cancel();
            anyhow::bail!(
                "esp_efuse_set_write_protect(EFUSE_BLK3) failed: 0x{:x}",
                wp_err
            );
        }

        let commit_err = esp_efuse_batch_write_commit();
        if commit_err != ESP_OK {
            let _ = esp_efuse_batch_write_cancel();
            anyhow::bail!(
                "esp_efuse_batch_write_commit(EFUSE_BLK3) failed: 0x{:x}",
                commit_err
            );
        }
    }

    // Verify readback
    match read_blk3_seed() {
        Some(readback) if &readback == seed => {
            log::info!("Verified EFUSE_BLK3 seed burn and write-protection.");
            Ok(())
        }
        Some(_) => anyhow::bail!("EFUSE_BLK3 readback mismatch after burn!"),
        None => anyhow::bail!("EFUSE_BLK3 readback returned empty after burn!"),
    }
}

/// Burns the hardware lockdown eFuses that protect `BLK3` from readout while keeping the
/// unit serial-upgradeable:
/// - `ESP_EFUSE_DIS_PAD_JTAG` (`HARD_DIS_JTAG`) = 1
/// - `ESP_EFUSE_DIS_USB_JTAG` = 1 (disables JTAG inside USB-Serial-JTAG while preserving USB CDC & download)
/// - `ESP_EFUSE_SOFT_DIS_JTAG` = 0b111 (all 3 bits set)
/// - `ESP_EFUSE_ENABLE_SECURITY_DOWNLOAD` = 1 (via `esp_efuse_enable_rom_secure_download_mode()`)
pub fn burn_security_lockdown_efuses() -> anyhow::Result<()> {
    let before = get_efuse_security_status();

    unsafe {
        let err = esp_efuse_batch_write_begin();
        if err != ESP_OK {
            anyhow::bail!("esp_efuse_batch_write_begin failed: 0x{:x}", err);
        }

        if !before.pad_jtag_disabled {
            let r = esp_efuse_write_field_bit(
                core::ptr::addr_of_mut!(ESP_EFUSE_DIS_PAD_JTAG) as *mut _,
            );
            if r != ESP_OK {
                let _ = esp_efuse_batch_write_cancel();
                anyhow::bail!("Failed to stage ESP_EFUSE_DIS_PAD_JTAG: 0x{:x}", r);
            }
        }

        if !before.usb_jtag_disabled {
            let r = esp_efuse_write_field_bit(
                core::ptr::addr_of_mut!(ESP_EFUSE_DIS_USB_JTAG) as *mut _,
            );
            if r != ESP_OK {
                let _ = esp_efuse_batch_write_cancel();
                anyhow::bail!("Failed to stage ESP_EFUSE_DIS_USB_JTAG: 0x{:x}", r);
            }
        }

        if before.soft_jtag_disabled_cnt < 3 {
            let remaining = 3 - before.soft_jtag_disabled_cnt;
            let r = esp_efuse_write_field_cnt(
                core::ptr::addr_of_mut!(ESP_EFUSE_SOFT_DIS_JTAG) as *mut _,
                remaining,
            );
            if r != ESP_OK {
                let _ = esp_efuse_batch_write_cancel();
                anyhow::bail!("Failed to stage ESP_EFUSE_SOFT_DIS_JTAG: 0x{:x}", r);
            }
        }

        let commit_err = esp_efuse_batch_write_commit();
        if commit_err != ESP_OK {
            let _ = esp_efuse_batch_write_cancel();
            anyhow::bail!(
                "esp_efuse_batch_write_commit(JTAG lockdown) failed: 0x{:x}",
                commit_err
            );
        }

        if !before.security_download_enabled {
            let dl_err = esp_efuse_enable_rom_secure_download_mode();
            if dl_err != ESP_OK {
                anyhow::bail!(
                    "esp_efuse_enable_rom_secure_download_mode failed: 0x{:x}",
                    dl_err
                );
            }
        }
    }

    let after = get_efuse_security_status();
    log::info!("Security lockdown eFuses verified: {:?}", after);
    Ok(())
}

/// Derives the 64-byte OPTIGA Platform Binding Secret (PBS) from the 32-byte `BLK3` seed
/// and the 27-byte OPTIGA Coprocessor UID (`0xE0C2`) using HKDF-SHA256.
pub fn derive_pbs_from_seed(seed: &[u8; 32], copr_uid: &[u8]) -> [u8; 64] {
    let hkdf = Hkdf::<Sha256>::new(Some(copr_uid), seed);
    let mut pbs = [0u8; 64];
    hkdf.expand(PBS_HKDF_INFO, &mut pbs)
        .expect("64 bytes is a valid HKDF-SHA256 output length");
    pbs
}

/// Computes the 32-byte SHA-256 digest signed by the Infineon factory key (`0xE0F0`)
/// to certify the device's Authenticity Check public key (`0xE0F2`):
/// `SHA-256("TARTARUS-AUTH-KEY-V1:" || copr_uid || auth_pubkey_sec1)`
pub fn compute_auth_key_attestation_digest(
    copr_uid: &[u8],
    auth_pubkey_sec1: &[u8],
) -> [u8; 32] {
    let mut hasher = Sha256::new();
    hasher.update(AUTH_ATTESTATION_DOMAIN);
    hasher.update(copr_uid);
    hasher.update(auth_pubkey_sec1);
    let digest = hasher.finalize();
    let mut out = [0u8; 32];
    out.copy_from_slice(&digest);
    out
}

/// Parses OPTIGA object metadata to extract used byte size (Tag 0xC5).
pub fn get_object_used_size(meta: &[u8]) -> Option<usize> {
    if meta.is_empty() || meta[0] != 0x20 {
        return None;
    }
    let total_len = if meta.len() > 1 { meta[1] as usize } else { 0 };
    let bound = core::cmp::min(meta.len(), 2 + total_len);
    let mut idx = 2;
    while idx < bound {
        let tag = meta[idx];
        if idx + 1 >= bound {
            break;
        }
        let len = meta[idx + 1] as usize;
        idx += 2;
        if idx + len > bound {
            break;
        }
        let val = &meta[idx..idx + len];
        if tag == 0xC5 {
            if len == 2 {
                return Some(u16::from_be_bytes([val[0], val[1]]) as usize);
            } else if len == 1 {
                return Some(val[0] as usize);
            }
        }
        idx += len;
    }
    None
}

#[derive(Debug, Clone)]
pub struct ExtractedCert {
    pub der: Vec<u8>,
    pub offset: usize,
}

/// Extracts all valid X.509 DER certificates embedded in a raw OPTIGA certificate data object
/// (`0xE0E0`), supporting bare DER, TLS Identity Containers (`0xC0`), and multi-cert chains.
pub fn extract_x509_certificates(raw: &[u8]) -> Vec<ExtractedCert> {
    let mut certs = Vec::new();
    let mut cursor = 0;

    while cursor + 4 <= raw.len() {
        if raw[cursor] == 0x30 {
            let (header_len, payload_len) = if raw[cursor + 1] == 0x82 && cursor + 4 <= raw.len() {
                let len = ((raw[cursor + 2] as usize) << 8) | (raw[cursor + 3] as usize);
                (4, len)
            } else if raw[cursor + 1] == 0x81 && cursor + 3 <= raw.len() {
                let len = raw[cursor + 2] as usize;
                (3, len)
            } else {
                cursor += 1;
                continue;
            };

            let total_len = header_len + payload_len;
            if cursor + total_len <= raw.len() && payload_len >= 64 {
                let body = &raw[cursor + header_len..];
                if !body.is_empty() && body[0] == 0x30 {
                    certs.push(ExtractedCert {
                        der: raw[cursor..cursor + total_len].to_vec(),
                        offset: cursor,
                    });
                    cursor += total_len;
                    continue;
                }
            }
        }
        cursor += 1;
    }

    certs
}

/// Reads certificate data object from an OPTIGA OID (`Oid::Data0` / `0xE0E0`).
pub fn read_optiga_certificates<I2C, T, VDD, RST, CRYPTO, DS>(
    util: &mut OptigaUtil<'_, I2C, T, VDD, RST, CRYPTO, DS>,
    oid: Oid,
) -> anyhow::Result<Vec<ExtractedCert>>
where
    I2C: OptigaI2cBus,
    T: OptigaTimer,
    VDD: OutputPin,
    RST: OutputPin,
    CRYPTO: HostCrypto,
    DS: Datastore,
{
    let mut meta_buf = [0u8; 64];
    let cert_len = match util.read_metadata(oid, &mut meta_buf) {
        Ok(len) => get_object_used_size(&meta_buf[..len]),
        Err(e) => {
            log::warn!("Could not read metadata for OID {:?}: {e}", oid);
            None
        }
    };

    let total_size = match cert_len {
        Some(size) if size > 0 => size,
        _ => {
            let mut header = [0u8; 4];
            util.read_data(oid, 0, &mut header)
                .map_err(|e| anyhow::anyhow!("Failed reading OID header: {e}"))?;
            if header[0] == 0xC0 {
                (u16::from_be_bytes([header[1], header[2]]) as usize) + 3
            } else if header[0] == 0x30 && header[1] == 0x82 {
                (u16::from_be_bytes([header[2], header[3]]) as usize) + 4
            } else if header[0] == 0x30 && header[1] == 0x81 {
                (header[2] as usize) + 3
            } else {
                1024
            }
        }
    };

    let mut raw_data = vec![0u8; total_size];
    let read_len = util
        .read_data(oid, 0, &mut raw_data)
        .map_err(|e| anyhow::anyhow!("Failed reading certificate body from {:?}: {e}", oid))?;
    raw_data.truncate(read_len);

    Ok(extract_x509_certificates(&raw_data))
}

/// Locates the NIST P-256 public key (ASN.1 BitString: `[0x03, 0x42, 0x00, 0x04, X(32B), Y(32B)]`)
/// within an X.509 DER certificate.
pub fn extract_public_key_bitstring(cert_der: &[u8]) -> Option<&[u8]> {
    if cert_der.len() < 68 {
        return None;
    }
    for i in 0..=cert_der.len() - 68 {
        if cert_der[i] == 0x03
            && cert_der[i + 1] == 0x42
            && cert_der[i + 2] == 0x00
            && cert_der[i + 3] == 0x04
        {
            return Some(&cert_der[i..i + 68]);
        }
    }
    None
}

/// Formats the provisioning / certificate export JSON bundle for serial output.
pub fn format_cert_export_json(
    status_label: &str,
    session_token: &str,
    copr_uid: &[u8],
    lock_pubkey_sec1: &[u8],
    auth_pubkey_sec1: &[u8],
    manufacturer_sig_p1363: &[u8],
    certs: &[ExtractedCert],
    efuse_status: &EfuseSecurityStatus,
) -> String {
    let attestation_digest = compute_auth_key_attestation_digest(copr_uid, auth_pubkey_sec1);
    let certs_b64_list: Vec<String> = certs
        .iter()
        .map(|c| format!("\"{}\"", BASE64.encode(&c.der)))
        .collect();

    format!(
        "{{\"status\":\"{}\",\"session_token\":\"{}\",\"copr_uid_hex\":\"{}\",\"lock_pubkey_b64\":\"{}\",\"auth_pubkey_b64\":\"{}\",\"manufacturer_sig_b64\":\"{}\",\"attestation_digest_hex\":\"{}\",\"infineon_certs_der_b64\":[{}],\"efuse_status\":{{\"blk3_provisioned\":{},\"blk3_write_protected\":{},\"secure_boot_enabled\":{},\"pad_jtag_disabled\":{},\"usb_jtag_disabled\":{},\"soft_jtag_disabled\":{},\"security_download_enabled\":{},\"usb_download_disabled\":{},\"secure_version\":{}}}}}",
        status_label,
        session_token,
        HEXLOWER.encode(copr_uid),
        BASE64.encode(lock_pubkey_sec1),
        BASE64.encode(auth_pubkey_sec1),
        BASE64.encode(manufacturer_sig_p1363),
        HEXLOWER.encode(&attestation_digest),
        certs_b64_list.join(","),
        efuse_status.blk3_provisioned,
        efuse_status.blk3_write_protected,
        efuse_status.secure_boot_enabled,
        efuse_status.pad_jtag_disabled,
        efuse_status.usb_jtag_disabled,
        efuse_status.soft_jtag_fully_disabled(),
        efuse_status.security_download_enabled,
        efuse_status.usb_download_disabled,
        efuse_status.secure_version,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_pbs_derivation_deterministic_and_distinct() {
        let seed = [0xAAu8; 32];
        let uid1 = [0x01u8; 27];
        let uid2 = [0x02u8; 27];

        let pbs1 = derive_pbs_from_seed(&seed, &uid1);
        let pbs1_again = derive_pbs_from_seed(&seed, &uid1);
        let pbs2 = derive_pbs_from_seed(&seed, &uid2);

        assert_eq!(pbs1, pbs1_again);
        assert_ne!(pbs1, pbs2);
        assert_eq!(pbs1.len(), 64);
    }

    #[test]
    fn test_compute_auth_key_attestation_digest() {
        let uid = [0x11u8; 27];
        let pubkey = [0x04u8; 65];
        let d1 = compute_auth_key_attestation_digest(&uid, &pubkey);
        let d2 = compute_auth_key_attestation_digest(&uid, &pubkey);
        assert_eq!(d1, d2);
    }
}
