from datetime import datetime
import decimal
import hashlib
import io
import json
import logging
import logging.config
from flask import Flask, Response, jsonify, request, send_file
from flask_cors import CORS

import base64
from cryptography import x509
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from .subjugated.club.SignedMessage import SignedMessage
from .subjugated.club.MessagePayload import MessagePayload
from .subjugated.club.LockUpdateEvent import LockUpdateEvent
from .subjugated.club.UpdateType import UpdateType

from .base import Base
import os

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from .key_record import KeyRecord
from .lock_session import LockSession
from .author_session import AuthorSession
from .contract import Contract

from cryptography.hazmat.primitives.asymmetric.utils import encode_dss_signature

logging.config.fileConfig(
            f"config/logging.conf", disable_existing_loggers=False
        )

logger = logging.getLogger(__name__)

engine = create_engine('sqlite:////tmp/tartarus.db')

Base.metadata.create_all(engine)
Session = sessionmaker(bind=engine)

def raw_to_der_signature(raw_sig: bytearray) -> bytes:
    if len(raw_sig) % 2 != 0:
        raise ValueError("Invalid raw signature length")

    half_len = len(raw_sig) // 2
    r = int.from_bytes(raw_sig[:half_len], "big")
    s = int.from_bytes(raw_sig[half_len:], "big")
    return encode_dss_signature(r, s)

def create_app():
    app = Flask(__name__)

    @app.route("/event", methods=["POST"])
    def handle_event():
        if request.content_type != "application/octet-stream":
            return Response("Invalid content type", status=400)

        buf = request.data  # Get raw bytes from the request
        print(f"Received {len(buf)} bytes")

        signed_message = SignedMessage.GetRootAsSignedMessage(buf, 0)
        c = signed_message.Payload()

        vtableOffsetStart = c.Pos - int.from_bytes(buf[c.Pos:c.Pos+1])
        hash = hashlib.sha256(buf[vtableOffsetStart:]).digest()

        data = buf[vtableOffsetStart:]
        # logger.info(f"Data first_byte_val({int(buf[c.Pos])}) vtable_offset({vtable_offset}) actual_start({actual_start}) -> {' '.join(map(str, data))}")
        logger.info(f"Hash -> {' '.join(map(str, hash))}")

        signature = [signed_message.Signature(i) for i in range(signed_message.SignatureLength())]
        logger.info(f"Signature -> {' '.join(map(str, signature))}")


        if signed_message.PayloadType() == MessagePayload.LockUpdateEvent:
            lock_update = LockUpdateEvent()
            lock_update.Init(signed_message.Payload().Bytes, signed_message.Payload().Pos)

            key = [lock_update.PublicKey(i) for i in range(lock_update.PublicKeyLength())]
            logger.debug(f"key {key}")
            working_key = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), bytes(key))
            def raw_to_der_signature(raw_sig: bytearray) -> bytes:
                    if len(raw_sig) % 2 != 0:
                        raise ValueError("Invalid raw signature length")

                    half_len = len(raw_sig) // 2
                    r = int.from_bytes(raw_sig[:half_len], "big")
                    s = int.from_bytes(raw_sig[half_len:], "big")
                    return encode_dss_signature(r, s)

            der_signature = raw_to_der_signature(bytes(signature))
            working_key.verify(der_signature, hash, ec.ECDSA(hashes.SHA256()))
            logger.info("Signature is verified!")
            
            update_type = lock_update.ThisUpdateType()
            if update_type == UpdateType.Started:
                print("A lockbox started and let us know.")


        return Response("Received", status=200)

    @app.route("/author_sessions", methods=['POST'])
    def save_author_key():
        data = request.get_json()
        if not data or 'public_key' not in data:
            return jsonify({'error': 'Missing public_key'}), 400
        if not data or 'session' not in data:
            return jsonify({'error': 'Missing session'}), 400
        if not data or 'signature' not in data:
            return jsonify({'error': 'Missing signature'}), 400

        raw_key = data['public_key']
        key_bytes = base64.urlsafe_b64decode(raw_key)
        session_token = data['session']
        try:
            valid_key = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), bytes(key_bytes))
        except ValueError as e:
            return jsonify({'error': 'Public key doesn\'t parse as SECP256R1'}), 400

        if not valid_key:
            return jsonify({'error': 'Public key doesn\'t parse as SECP256R1'}), 400
        
        key_bytes = base64.urlsafe_b64decode(raw_key)
        
        hash = hashlib.sha256(session_token.encode('utf-8')).digest()
        logger.info(f"Hash -> {' '.join(map(str, hash))}")

        signature_bytes = base64.urlsafe_b64decode(data['signature'])
        der_signature = raw_to_der_signature(signature_bytes)
        try:
            valid_key.verify(der_signature, hash, ec.ECDSA(hashes.SHA256()))
        except:
            return jsonify({'error': 'Invalid signature'}), 400
        
        session = Session()

        maybe_session = session.query(AuthorSession).filter(AuthorSession.session == session_token).filter(AuthorSession.public_key == raw_key).first()
        if maybe_session:
            # We already have this one.
            return jsonify({'message': 'Key saved'}), 200
        
        keypair = AuthorSession(public_key=data['public_key'], session=data['session'])
        session.add(keypair)
        session.commit()

        return jsonify({'message': 'Key saved'}), 200

    @app.route("/lock_sessions", methods=['POST'])
    def save_pub_key():
        data = request.get_json()
        if not data or 'public_key' not in data:
            return jsonify({'error': 'Missing public_key'}), 400
        if not data or 'session' not in data:
            return jsonify({'error': 'Missing session'}), 400

        raw_key = data['public_key']        
        key_bytes = base64.urlsafe_b64decode(raw_key)
        session_token = data['session']
        try:
            valid_key = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), bytes(key_bytes))
        except ValueError as e:
            return jsonify({'error': 'Public key doesn\'t parse as SECP256R1'}), 400

        if not valid_key:
            return jsonify({'error': 'Public key doesn\'t parse as SECP256R1'}), 400
        
        session = Session()

        maybe_session = session.query(LockSession).filter(LockSession.session == session_token).filter(LockSession.public_key == raw_key).first()
        if maybe_session:
            # We already have this one.
            return jsonify({'message': 'Key saved'}), 200
        
        keypair = LockSession(public_key=data['public_key'], session=data['session'])
        session.add(keypair)
        session.commit()

        return jsonify({'message': 'Key saved'}), 200
    
    @app.route("/lock_sessions/<string:token>")
    def get_lock_session(token):
        session = Session()
        maybe_session = session.query(LockSession).filter(LockSession.session == token).first()
        if not maybe_session:
            return jsonify({'error': 'No such session'}), 404
        
        return jsonify(maybe_session.to_dict()), 200


    @app.route("/keys", methods=['POST'])
    def save_key():
        data = request.get_json()
        if not data or 'public_key' not in data:
            return jsonify({'error': 'Missing public_key'}), 400

        session = Session()
        keypair = KeyRecord(public_key=data['public_key'])
        session.add(keypair)
        session.commit()

        return jsonify({'message': 'Key saved'}), 200
    
    @app.route("/contracts/<string:contract_name>", methods=['POST'])
    def save_contract(contract_name):
        data = request.get_json()
        if not data or 'signed_message' not in data:
            return jsonify({'error': 'Missing signed_message'}), 400
        
        session = Session()
        maybe_contract = session.query(Contract).filter(Contract.name == contract_name).first()

        if maybe_contract is not None:
            return jsonify({'error': 'Contract already exists.'}), 400

        contract = Contract(name=contract_name, body=data['signed_message'])
        if contract.validate_body():
            session.add(contract)
            session.commit()
        else:
            return jsonify({'error': 'Invalid contract body'}), 400
        
        return jsonify({'message': 'Contract saved'}), 200
    
    @app.route("/announce", methods=['POST'])
    def announce():

        return "OK"
    
    return app
    
if __name__ == "__main__":
    app = create_app()
    # app.json = CustomJSONProvider(app)
    CORS(app)
    app.run(debug=True, host="0.0.0.0", port=5002, use_reloader=False)
