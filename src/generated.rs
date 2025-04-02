pub mod generated {
    use p256::ecdsa::SigningKey;
    use p256::SecretKey;

    pub const CHALLENGE_KEY: Option<&'static [u8]> =
        include!(concat!(env!("OUT_DIR"), "/key_parts.rs"));

    pub fn get_challenge_key() -> Option<SigningKey> {
        if let Some(key_bytes) = CHALLENGE_KEY {
            log::info!("We have an authenticity key");

            let secret_key = SecretKey::from_slice(key_bytes).unwrap();

            let cloned_secret = secret_key.clone();
            let bytes = cloned_secret.to_bytes();
            let key_bytes = bytes.as_slice();
            let signing_key = SigningKey::from_slice(key_bytes).unwrap();

            Some(signing_key)
        } else {
            log::info!("We DO NOT have an authenticity key");
            None
        }
    }
}
