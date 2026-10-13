use p256::ecdsa::SigningKey;

#[allow(dead_code)]
pub fn get_challenge_key() -> Option<SigningKey> {
    crate::firmware_updater::get_challenge_key()
}


