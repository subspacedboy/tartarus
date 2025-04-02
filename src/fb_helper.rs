use p256::ecdsa::signature::Signer;
use p256::ecdsa::{Signature, SigningKey};
use sha2::{Digest, Sha256};

pub fn calculate_signature(buffer: &[u8], signing_key: &SigningKey) -> Signature {
    let table_offset = buffer[0] as usize;
    let vtable_offset = buffer[table_offset] as usize;
    let actual_start = table_offset - vtable_offset;

    let hash = Sha256::digest(buffer[actual_start..]);

    signing_key.sign(&hash.as_slice())
}
