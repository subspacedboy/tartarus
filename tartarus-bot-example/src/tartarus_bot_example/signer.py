import base64
import json
import time
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

SECP256R1_ORDER = 0xFFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551
BOT_KDF_SALT = b"tartarus-bot-kdf-v1"


def _b64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


class Signer:
    def __init__(
        self,
        private_key_path: str | None = "ec_private_key.pem",
        public_key_path: str | None = "ec_public_key.pem",
        private_key: ec.EllipticCurvePrivateKey | None = None,
    ):
        if private_key is not None:
            self.private_key = private_key
            self.public_key = private_key.public_key()
        else:
            with open(private_key_path, "rb") as f:
                self.private_key = serialization.load_pem_private_key(f.read(), password=None)

            if public_key_path is not None:
                with open(public_key_path, "rb") as f:
                    self.public_key = serialization.load_pem_public_key(f.read())
            else:
                self.public_key = self.private_key.public_key()

        if not isinstance(self.private_key, ec.EllipticCurvePrivateKey):
            raise ValueError("Loaded private key is not an EC private key")

        if not isinstance(self.public_key, ec.EllipticCurvePublicKey):
            raise ValueError("Loaded public key is not an EC public key")

    @classmethod
    def from_author_key(
        cls,
        author_private_key_path: str,
        internal_name: str,
        key_version: int = 1,
    ) -> "Signer":
        """Deterministically derives a bot secp256r1 keypair from the Author's private key using HKDF-SHA256."""
        with open(author_private_key_path, "rb") as f:
            author_priv = serialization.load_pem_private_key(f.read(), password=None)
        if not isinstance(author_priv, ec.EllipticCurvePrivateKey):
            raise ValueError("Author private key is not an EC private key")

        d_author_bytes = author_priv.private_numbers().private_value.to_bytes(32, byteorder="big")
        hkdf = HKDF(
            algorithm=hashes.SHA256(),
            length=48,
            salt=BOT_KDF_SALT,
            info=f"{internal_name}:{key_version}".encode("utf-8"),
        )
        okm = hkdf.derive(d_author_bytes)
        d_bot = (int.from_bytes(okm, byteorder="big") % (SECP256R1_ORDER - 1)) + 1
        bot_priv = ec.derive_private_key(d_bot, ec.SECP256R1())
        return cls(private_key_path=None, public_key_path=None, private_key=bot_priv)

    def compressed_public_key_bytes(self) -> bytes:
        return self.public_key.public_bytes(
            encoding=serialization.Encoding.X962,
            format=serialization.PublicFormat.CompressedPoint,
        )

    def compressed_public_key_b64(self) -> str:
        return base64.b64encode(self.compressed_public_key_bytes()).decode("ascii")

    def sign(self, data: bytes) -> bytes:
        """Signs the given data using the EC private key (SHA-256) and returns the 64-byte raw (R || S) signature."""
        der_signature = self.private_key.sign(data, ec.ECDSA(hashes.SHA256()))
        r, s = decode_dss_signature(der_signature)

        r_bytes = r.to_bytes(32, byteorder="big")
        s_bytes = s.to_bytes(32, byteorder="big")
        return r_bytes + s_bytes

    def create_jwt(self, subject: str, ttl_seconds: int = 3600) -> str:
        """Creates an ES256 JWT signed by this key for authenticating REST and WebSocket requests."""
        now = int(time.time())
        header = {"alg": "ES256", "typ": "JWT"}
        payload = {"sub": subject, "iat": now, "exp": now + ttl_seconds}
        signing_input = (
            f"{_b64url_encode(json.dumps(header, separators=(',', ':')).encode('utf-8'))}."
            f"{_b64url_encode(json.dumps(payload, separators=(',', ':')).encode('utf-8'))}"
        )
        raw_sig = self.sign(signing_input.encode("ascii"))
        return f"{signing_input}.{_b64url_encode(raw_sig)}"
