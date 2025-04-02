from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization

# Generate a new EC key pair
private_key = ec.generate_private_key(ec.SECP256R1())  # Using the P-256 curve

# Save the private key to a file (without encryption)
with open("ec_private_key.pem", "wb") as f:
    f.write(private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.TraditionalOpenSSL,  # Writes as "BEGIN EC PRIVATE KEY"
        encryption_algorithm=serialization.NoEncryption()  # No password protection
    ))

# Extract the public key
public_key = private_key.public_key()

# Save the public key to a file
with open("ec_public_key.pem", "wb") as f:
    f.write(public_key.public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo  # Standard public key format
    ))

print("EC key pair generated and saved as 'ec_private_key.pem' and 'ec_public_key.pem'")
