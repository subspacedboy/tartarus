from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization

def save_key(filename, data):
    with open(filename, "wb") as f:
        f.write(data)

def generate_challenge_keypair():
    # Generate a new EC private key for secp256r1
    private_key = ec.generate_private_key(ec.SECP256R1())

    # Get the private key in raw compressed form (32 bytes)
    private_bytes = private_key.private_numbers().private_value.to_bytes(32, byteorder='big')

    # Get the public key in compressed form (33 bytes)
    public_key = private_key.public_key()
    public_numbers = public_key.public_numbers()
    prefix = b'\x02' if public_numbers.y % 2 == 0 else b'\x03'
    public_bytes = prefix + public_numbers.x.to_bytes(32, byteorder='big')

    # Save private and public keys
    save_key("private.key", private_bytes)
    save_key("public.key", public_bytes)

if __name__ == "__main__":
    generate_challenge_keypair()

