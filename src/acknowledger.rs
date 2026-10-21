use crate::contract_generated::club::subjugated::fb::message::{
    Acknowledgement, AcknowledgementArgs, Error, ErrorArgs, MessagePayload, SignedMessage,
    SignedMessageArgs,
};
use crate::fb_helper::calculate_signature_with_backend;
use crate::storage_backend::SecurityBackend;
use crate::verifier::{VerificationError, VerifiedType};
use flatbuffers::FlatBufferBuilder;

pub(crate) struct Acknowledger {}

impl Acknowledger {
    pub fn new() -> Self {
        Self {}
    }

    pub fn build_acknowledgement(
        &self,
        command: VerifiedType,
        session_token: &str,
        backend: &mut dyn SecurityBackend,
    ) -> Vec<u8> {
        let public_key = backend.public_key();
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let (serial_number, counter) = match command {
            VerifiedType::Contract(c) => (c.serial_number, 0u16),
            VerifiedType::UnlockCommand(unlock) => (unlock.serial_number, unlock.counter),
            VerifiedType::LockCommand(lock) => (lock.serial_number, lock.counter),
            VerifiedType::ReleaseCommand(release) => (release.serial_number, release.counter),
            VerifiedType::AbortCommand(abort) => (abort.serial_number, abort.counter),
            // An "acknowledged" reset means that it wasn't enabled...
            VerifiedType::ResetCommand(reset) => (reset.serial_number(), 0),
        };

        let pub_key_holder = builder.create_vector(public_key.to_sec1_bytes().as_ref());
        let session = builder.create_string(session_token);

        let ack = Acknowledgement::create(
            &mut builder,
            &AcknowledgementArgs {
                public_key: Some(pub_key_holder),
                session: Some(session),
                serial_number,
                counter,
            },
        );

        builder.finish(ack, None);
        let buffer = builder.finished_data();

        let signature = calculate_signature_with_backend(buffer, backend)
            .expect("Failed to sign acknowledgement message");

        // UGH. We have to build the whole message over again because of the way
        // Rust implements flatbuffers.
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let pub_key_holder = builder.create_vector(public_key.to_sec1_bytes().as_ref());
        let session = builder.create_string(session_token);

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

        let sig_bytes = signature.to_bytes();
        let signature_offset = builder.create_vector(sig_bytes.as_slice());
        let signed_message = SignedMessage::create(
            &mut builder,
            &SignedMessageArgs {
                signature: Some(signature_offset),
                payload: Some(payload_value),
                payload_type,
                authority_identifier: None,
            },
        );

        builder.finish(signed_message, None);
        builder.finished_data().to_vec()
    }

    pub fn build_error_for_command(
        &self,
        command: VerifiedType,
        session_token: &str,
        backend: &mut dyn SecurityBackend,
        message: &str,
    ) -> Vec<u8> {
        let public_key = backend.public_key();
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        log::info!("Building error for command: {command:?}");

        let (serial_number, counter) = match command {
            VerifiedType::Contract(c) => (c.serial_number, 0u16),
            VerifiedType::UnlockCommand(unlock) => (unlock.serial_number, unlock.counter),
            VerifiedType::LockCommand(lock) => (lock.serial_number, lock.counter),
            VerifiedType::ReleaseCommand(release) => (release.serial_number, release.counter),
            VerifiedType::AbortCommand(abort) => (abort.serial_number, abort.counter),
            VerifiedType::ResetCommand(reset) => (reset.serial_number(), 0),
        };

        let pub_key_holder = builder.create_vector(public_key.to_sec1_bytes().as_ref());
        let session = builder.create_string(session_token);
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

        builder.finish(error_message, None);
        let buffer = builder.finished_data();

        let signature = calculate_signature_with_backend(buffer, backend)
            .expect("Failed to sign error message");

        // UGH. We have to build the whole message over again because of the way
        // Rust implements flatbuffers.
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let pub_key_holder = builder.create_vector(public_key.to_sec1_bytes().as_ref());
        let session = builder.create_string(session_token);
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

        let sig_bytes = signature.to_bytes();
        let signature_offset = builder.create_vector(sig_bytes.as_slice());
        let signed_message = SignedMessage::create(
            &mut builder,
            &SignedMessageArgs {
                signature: Some(signature_offset),
                payload: Some(payload_value),
                payload_type,
                authority_identifier: None,
            },
        );

        builder.finish(signed_message, None);
        builder.finished_data().to_vec()
    }

    pub fn build_error(
        &self,
        err: VerificationError,
        session_token: &str,
        backend: &mut dyn SecurityBackend,
    ) -> Vec<u8> {
        let public_key = backend.public_key();
        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let pub_key_holder = builder.create_vector(public_key.to_sec1_bytes().as_ref());
        let session = builder.create_string(session_token);
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

        builder.finish(error_message, None);
        let buffer = builder.finished_data();

        let signature = calculate_signature_with_backend(buffer, backend)
            .expect("Failed to sign error message");

        let mut builder = FlatBufferBuilder::with_capacity(1024);

        let pub_key_holder = builder.create_vector(public_key.to_sec1_bytes().as_ref());
        let session = builder.create_string(session_token);
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

        let sig_bytes = signature.to_bytes();
        let signature_offset = builder.create_vector(sig_bytes.as_slice());
        let signed_message = SignedMessage::create(
            &mut builder,
            &SignedMessageArgs {
                signature: Some(signature_offset),
                payload: Some(payload_value),
                payload_type,
                authority_identifier: None,
            },
        );

        builder.finish(signed_message, None);
        builder.finished_data().to_vec()
    }
}
