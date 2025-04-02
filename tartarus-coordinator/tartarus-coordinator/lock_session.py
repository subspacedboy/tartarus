import base64
from dataclasses import dataclass
from datetime import datetime, timezone
import uuid

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

from sqlalchemy import Column, DateTime, String
from .base import Base

@dataclass
class LockSession(Base):
    __tablename__ = "lock_sessions"

    id: str = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    public_key: str = Column(String, nullable=False)
    session: str = Column(String, nullable=False)
    last_heard_from = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    def to_dict(self):
        # To make this easier to work on the web we need to reexport this key
        # from secp1 compressed to good ol' PEM.
        key_bytes = base64.urlsafe_b64decode(self.public_key)
        public_key = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), key_bytes)

        public_pem = public_key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        ).decode('utf-8')
        
        return {
            "id": self.id,
            # "name": self.name,
            "public_key" : self.public_key,
            "public_pem" : public_pem,
            "session" : self.session,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "last_heard_from": self.last_heard_from.isoformat() if self.last_heard_from else None,
        }