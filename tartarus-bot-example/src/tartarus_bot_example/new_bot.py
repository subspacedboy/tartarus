from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
import base64

# Load the EC public key from a PEM file
with open("ec_public_key.pem", "rb") as key_file:
    public_key = serialization.load_pem_public_key(key_file.read())

# Convert to compressed point format
if isinstance(public_key, ec.EllipticCurvePublicKey):
    compressed_bytes = public_key.public_bytes(
        encoding=serialization.Encoding.X962,
        format=serialization.PublicFormat.CompressedPoint
    )

    # Base64 encode the compressed bytes
    encoded_key = base64.b64encode(compressed_bytes).decode("utf-8")
    print(encoded_key)

    import requests

    # url = "http://localhost:5002/bots/"
    url = "https://tartarus-api.subjugated.club:4446/bots/"

    data = {
        "publicKey": encoded_key,
        "description": "Timing bot"
    }

    response = requests.post(url, json=data)

    print(response.status_code)
    print(response.text)

    