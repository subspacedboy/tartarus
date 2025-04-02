use crate::contract_generated::club::subjugated::fb::message::{
    Acknowledgement, AcknowledgementArgs, Error, ErrorArgs, MessagePayload, SignedMessage,
    SignedMessageArgs,
};
use crate::verifier::{VerificationError, VerifiedType};
use flatbuffers::FlatBufferBuilder;
use p256::ecdsa::signature::Signer;
use p256::ecdsa::{Signature, SigningKey};
use p256::PublicKey;
use sha2::{Digest, Sha256};

pub(crate) struct Acknowledger {}

impl Acknowledger {
    pub fn new() -> Self {
        Self {}
    }

    pub fn build_acknowledgement(
        &self,
        command: VerifiedType,
        session_token: &String,
        public_key: &PublicKey,
        signing_key: &SigningKey,
    ) -> Vec<u8> {
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let (serial_number, counter) = match command {
            VerifiedType::Contract(c) => (c.serial_number, 0u16),
            VerifiedType::UnlockCommand(unlock) => (unlock.serial_number, unlock.counter),
            VerifiedType::LockCommand(lock) => (lock.serial_number, lock.counter),
            VerifiedType::ReleaseCommand(release) => (release.serial_number, release.counter),
        };

        let public_key_bytes: Vec<u8> = public_key.to_sec1_bytes().to_vec();
        let pub_key_holder = builder.create_vector(&public_key_bytes);
        let session = builder.create_string(&session_token);

        let ack = Acknowledgement::create(
            &mut builder,
            &AcknowledgementArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                serial_number,
                counter,
            },
        );

        // let _payload_type = MessagePayload::Acknowledgement; // Union type
        // let _payload_value = ack.as_union_value();

        builder.finish(ack, None);
        let buffer = builder.finished_data();

        let table_offset = buffer[0] as usize;
        let vtable_offset = buffer[table_offset] as usize;
        let actual_start = table_offset - vtable_offset;

        // log::info!(
        //     "Update buffer w/ vtable ({},{}): {:?}",
        //     vtable_offset,
        //     table_offset,
        //     buffer
        // );
        let hash = Sha256::digest(&buffer[actual_start..]);
        // log::info!("Hash {:?}", hash);

        // // UGH. We have to build the whole message over again because of the way
        // // Rust implements flatbuffers.
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let public_key_bytes: Vec<u8> = public_key.to_sec1_bytes().to_vec();
        let pub_key_holder = builder.create_vector(&public_key_bytes);
        let session = builder.create_string(&session_token);

        let ack = Acknowledgement::create(
            &mut builder,
            &AcknowledgementArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                serial_number,
                counter,
            },
        );

        let payload_type = MessagePayload::Acknowledgement; // Union type
        let payload_value = ack.as_union_value();

        let signature: Signature = signing_key.sign(&hash.as_slice());
        let sig_bytes = signature.to_bytes();

        let signature_offset = builder.create_vector(sig_bytes.as_slice());
        let signed_message = SignedMessage::create(
            &mut builder,
            &SignedMessageArgs {
                signature: Some(signature_offset),
                payload: Some(payload_value),
                payload_type,
            },
        );

        builder.finish(signed_message, None);
        builder.finished_data().to_vec()
    }

    pub fn build_error_for_command(
        &self,
        command: VerifiedType,
        session_token: &String,
        public_key: &PublicKey,
        signing_key: &SigningKey,
        message: &String,
    ) -> Vec<u8> {
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        log::info!("Building error for command: {command:?}");

        let (serial_number, counter) = match command {
            VerifiedType::Contract(c) => (c.serial_number, 0u16),
            VerifiedType::UnlockCommand(unlock) => (unlock.serial_number, unlock.counter),
            VerifiedType::LockCommand(lock) => (lock.serial_number, lock.counter),
            VerifiedType::ReleaseCommand(release) => (release.serial_number, release.counter),
        };

        let public_key_bytes: Vec<u8> = public_key.to_sec1_bytes().to_vec();
        let pub_key_holder = builder.create_vector(&public_key_bytes);
        let session = builder.create_string(&session_token);
        let message_offset = builder.create_string(message);
        let error_message = Error::create(
            &mut builder,
            &ErrorArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                serial_number,
                counter,
                message: Some(message_offset),
            },
        );

        // let _payload_type = MessagePayload::Acknowledgement; // Union type
        // let _payload_value = ack.as_union_value();

        builder.finish(error_message, None);
        let buffer = builder.finished_data();

        let table_offset = buffer[0] as usize;
        let vtable_offset = buffer[table_offset] as usize;
        let actual_start = table_offset - vtable_offset;

        // log::info!(
        //     "Update buffer w/ vtable ({},{}): {:?}",
        //     vtable_offset,
        //     table_offset,
        //     buffer
        // );
        let hash = Sha256::digest(&buffer[actual_start..]);
        // log::info!("Hash {:?}", hash);

        // // UGH. We have to build the whole message over again because of the way
        // // Rust implements flatbuffers.
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let public_key_bytes: Vec<u8> = public_key.to_sec1_bytes().to_vec();
        let pub_key_holder = builder.create_vector(&public_key_bytes);
        let session = builder.create_string(&session_token);
        let message_offset = builder.create_string(message);
        let error_message = Error::create(
            &mut builder,
            &ErrorArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                serial_number,
                counter,
                message: Some(message_offset),
            },
        );

        let payload_type = MessagePayload::Error; // Union type
        let payload_value = error_message.as_union_value();

        let signature: Signature = signing_key.sign(&hash.as_slice());
        let sig_bytes = signature.to_bytes();

        let signature_offset = builder.create_vector(sig_bytes.as_slice());
        let signed_message = SignedMessage::create(
            &mut builder,
            &SignedMessageArgs {
                signature: Some(signature_offset),
                payload: Some(payload_value),
                payload_type,
            },
        );

        builder.finish(signed_message, None);
        builder.finished_data().to_vec()
    }

    pub fn build_error(
        &self,
        err: VerificationError,
        session_token: &String,
        public_key: &PublicKey,
        signing_key: &SigningKey,
    ) -> Vec<u8> {
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let public_key_bytes: Vec<u8> = public_key.to_sec1_bytes().to_vec();
        let pub_key_holder = builder.create_vector(&public_key_bytes);
        let session = builder.create_string(&session_token);
        let message_offset = builder.create_string(&err.message);
        let error_message = Error::create(
            &mut builder,
            &ErrorArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                serial_number: err.serial_number,
                counter: err.counter,
                message: Some(message_offset),
            },
        );

        // let _payload_type = MessagePayload::Acknowledgement; // Union type
        // let _payload_value = ack.as_union_value();

        builder.finish(error_message, None);
        let buffer = builder.finished_data();

        let table_offset = buffer[0] as usize;
        let vtable_offset = buffer[table_offset] as usize;
        let actual_start = table_offset - vtable_offset;

        // log::info!(
        //     "Update buffer w/ vtable ({},{}): {:?}",
        //     vtable_offset,
        //     table_offset,
        //     buffer
        // );
        let hash = Sha256::digest(&buffer[actual_start..]);
        // log::info!("Hash {:?}", hash);

        // // UGH. We have to build the whole message over again because of the way
        // // Rust implements flatbuffers.
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let public_key_bytes: Vec<u8> = public_key.to_sec1_bytes().to_vec();
        let pub_key_holder = builder.create_vector(&public_key_bytes);
        let session = builder.create_string(&session_token);
        let message_offset = builder.create_string(&err.message);
        let error_message = Error::create(
            &mut builder,
            &ErrorArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                serial_number: err.serial_number,
                counter: err.counter,
                message: Some(message_offset),
            },
        );

        let payload_type = MessagePayload::Error; // Union type
        let payload_value = error_message.as_union_value();

        let signature: Signature = signing_key.sign(&hash.as_slice());
        let sig_bytes = signature.to_bytes();

        let signature_offset = builder.create_vector(sig_bytes.as_slice());
        let signed_message = SignedMessage::create(
            &mut builder,
            &SignedMessageArgs {
                signature: Some(signature_offset),
                payload: Some(payload_value),
                payload_type,
            },
        );

        builder.finish(signed_message, None);
        builder.finished_data().to_vec()
    }
}
