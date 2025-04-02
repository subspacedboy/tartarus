import base64
from dataclasses import dataclass
from datetime import datetime, timezone
import logging
import uuid
import hashlib

from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric.utils import encode_dss_signature, decode_dss_signature
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.bindings.openssl.binding import Binding

from .subjugated.club.SignedMessage import SignedMessage
from .subjugated.club.Contract import Contract as ContractMessage
from .subjugated.club.MessagePayload import MessagePayload

from sqlalchemy import Column, DateTime, String
from .base import Base

logger = logging.getLogger(__name__)

binding = Binding()
binding.init_static_locks()

def consume_openssl_errors():
    errors = []
    while True:
        code = binding.lib.ERR_get_error()
        if code == 0:
            break

        lib_name = binding.lib.ERR_lib_error_string(code)
        func_name = binding.lib.ERR_func_error_string(code)
        reason = binding.lib.ERR_reason_error_string(code)

        print(f"OpenSSL Error: {hex(code)} - {lib_name.decode() if lib_name else 'Unknown'} - "
              f"{func_name.decode() if func_name else 'Unknown'} - {reason.decode() if reason else 'Unknown'}")

        errors.append((code, lib_name, func_name, reason))

    return errors

@dataclass
class Contract(Base):
    __tablename__ = "contracts"

    id: str = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    name: str = Column(String, nullable=False)
    body: str = Column(String, nullable=False)
    created_at = Column(DateTime, default=datetime.now(timezone.utc))

    def validate_body(self):
        try:
            decoded_data = base64.b64decode(self.body)
            buf = bytearray(decoded_data)
            signed_message = SignedMessage.GetRootAsSignedMessage(buf, 0)
            c = signed_message.Payload()

            vtableOffsetStart = c.Pos - int.from_bytes(buf[c.Pos:c.Pos+1])
            hash = hashlib.sha256(buf[vtableOffsetStart:]).digest()

            data = buf[vtableOffsetStart:]
            # logger.info(f"Data first_byte_val({int(buf[c.Pos])}) vtable_offset({vtable_offset}) actual_start({actual_start}) -> {' '.join(map(str, data))}")
            logger.info(f"Hash -> {' '.join(map(str, hash))}")

            signature = [signed_message.Signature(i) for i in range(signed_message.SignatureLength())]
            logger.info(f"Signature -> {' '.join(map(str, signature))}")
            
            if signed_message.PayloadType() == MessagePayload.Contract:
                logger.info("Verifying as contract")
                contract = ContractMessage()
                contract.Init(signed_message.Payload().Bytes, signed_message.Payload().Pos)

                key = [contract.PublicKey(i) for i in range(contract.PublicKeyLength())]
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
                try:
                    working_key.verify(der_signature, hash, ec.ECDSA(hashes.SHA256()))
                    logger.info("Signature is verified!")
                    return True
                except InvalidSignature:
                    logger.warning(f"Signature is wrong.")
                    return False
                except Exception as e:
                    logger.warning(f"Verification didn't work {e}")
                    return False
            

            return signed_message is not None  # Modify this based on additional validation needs
        except Exception as e:

            logger.warning(f"Failed to validate contract -> {e}")
            return False