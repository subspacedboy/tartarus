use crate::storage_backend::SecurityBackend;
use p256::ecdsa::signature::Signer;
use p256::ecdsa::{Signature, SigningKey};
use sha2::{Digest, Sha256};

pub fn calculate_payload_hash(buffer: &[u8]) -> [u8; 32] {
    let table_offset = buffer[0] as usize;
    let vtable_offset = buffer[table_offset] as usize;
    let actual_start = table_offset - vtable_offset;

    // The Tartarus wire protocol applies SHA256withECDSA over the SHA-256 table digest
    // (i.e. double SHA-256). PrehashSigner::sign_prehash and OPTIGA's ecdsa_sign expect the
    // final 32-byte digest to sign directly without additional hashing.
    let hash1 = Sha256::digest(&buffer[actual_start..]);
    let hash2 = Sha256::digest(hash1.as_slice());
    let mut out = [0u8; 32];
    out.copy_from_slice(hash2.as_slice());
    out
}

#[allow(dead_code)]
pub fn calculate_signature(buffer: &[u8], signing_key: &SigningKey) -> Signature {
    let table_offset = buffer[0] as usize;
    let vtable_offset = buffer[table_offset] as usize;
    let actual_start = table_offset - vtable_offset;

    let hash = Sha256::digest(&buffer[actual_start..]);

    signing_key.sign(hash.as_slice())
}

pub fn calculate_signature_with_backend(
    buffer: &[u8],
    backend: &mut dyn SecurityBackend,
) -> anyhow::Result<Signature> {
    let digest = calculate_payload_hash(buffer);
    backend.sign_prehash(&digest)
}

#[cfg(test)]
mod tests {
    use super::*;
    use p256::ecdsa::signature::hazmat::PrehashVerifier;
    use p256::ecdsa::VerifyingKey;
    use rand_core::OsRng;

    #[test]
    fn test_signature_equivalence() {
        let sk = p256::SecretKey::random(&mut OsRng);
        let signing_key = SigningKey::from(&sk);
        let verifying_key = VerifyingKey::from(&signing_key);

        // Dummy FlatBuffers buffer: byte 0 is offset (2), byte 2 is vtable offset (2) => start is 0
        let fake_fb_buffer = [2u8, 0, 2, 0, 10, 20, 30, 40];

        // 1. Old method: calculate_signature
        let sig1 = calculate_signature(&fake_fb_buffer, &signing_key);

        // 2. New method: calculate_payload_hash + sign_prehash
        let digest = calculate_payload_hash(&fake_fb_buffer);
        let sig2: Signature = p256::ecdsa::signature::hazmat::PrehashSigner::sign_prehash(&signing_key, &digest).unwrap();

        // 3. Verify coordinator's verification flow on sig2:
        // Coordinator does:
        // val hash = digest.digest(justContractBytes) // hash1
        // Signature.getInstance("SHA256withECDSA").apply { update(hash); verify(sig) } // SHA-256(hash1)
        let hash1 = Sha256::digest(&fake_fb_buffer[0..]);
        let expected_prehash = Sha256::digest(hash1.as_slice());
        assert_eq!(digest, expected_prehash.as_slice());

        // VerifyingKey::verify_prehash with digest verifies both signatures!
        verifying_key.verify_prehash(&digest, &sig1).expect("sig1 must verify with digest");
        verifying_key.verify_prehash(&digest, &sig2).expect("sig2 must verify with digest");
    }
}
