from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.utils import decode_dss_signature

class Signer:
    def __init__(self, private_key_path="ec_private_key.pem", public_key_path="ec_public_key.pem"):
        # Load the private key
        with open(private_key_path, "rb") as f:
            self.private_key = serialization.load_pem_private_key(f.read(), password=None)

        # Load the public key
        with open(public_key_path, "rb") as f:
            self.public_key = serialization.load_pem_public_key(f.read())

        if not isinstance(self.private_key, ec.EllipticCurvePrivateKey):
            raise ValueError("Loaded private key is not an EC private key")

        if not isinstance(self.public_key, ec.EllipticCurvePublicKey):
            raise ValueError("Loaded public key is not an EC public key")

    def sign(self, data: bytes) -> bytes:
        """Signs the given data using the EC private key and returns the signature."""
        der_signature = self.private_key.sign(data, ec.ECDSA(hashes.SHA256()))
        r, s = decode_dss_signature(der_signature)

        r_bytes = r.to_bytes(32, byteorder='big')
        s_bytes = s.to_bytes(32, byteorder='big')
        raw_signature = r_bytes + s_bytes

        return raw_signature