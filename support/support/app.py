from flask import Flask, request
import base64
from cryptography import x509
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

import os

PRIVATE_KEY_FILE = "ec_private_key.pem"
PUBLIC_KEY_FILE = "ec_public_key.pem"
PASSWORD = b"mypassword"  # Change this to a secure password

# Check if keys already exist
if os.path.exists(PRIVATE_KEY_FILE) and os.path.exists(PUBLIC_KEY_FILE):
    print("Keys already exist, loading from files...")

    # Load private key
    with open(PRIVATE_KEY_FILE, "rb") as priv_file:
        private_key = serialization.load_pem_private_key(
            priv_file.read(),
            password=PASSWORD
        )

    # Load public key
    with open(PUBLIC_KEY_FILE, "rb") as pub_file:
        public_key = serialization.load_pem_public_key(pub_file.read())

else:
    print("Keys not found, generating new key pair...")

    # Generate a new EC private key
    private_key = ec.generate_private_key(ec.SECP256R1())

    # Serialize the private key with encryption
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.BestAvailableEncryption(PASSWORD)
    )

    # Serialize the public key
    public_pem = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )

    # Save keys to files
    with open(PRIVATE_KEY_FILE, "wb") as priv_file:
        priv_file.write(private_pem)

    with open(PUBLIC_KEY_FILE, "wb") as pub_file:
        pub_file.write(public_pem)

    print("New keys generated and saved!")

# Display the loaded public key
print("Loaded Public Key (PEM):\n", public_key.public_bytes(
    encoding=serialization.Encoding.PEM,
    format=serialization.PublicFormat.SubjectPublicKeyInfo
).decode())

def create_app():
    app = Flask(__name__)


    # # engine = create_engine('sqlite:///:memory:')
    # file_path = "/tmp/my_database.db"

    # # Create an SQLite engine
    # engine = create_engine(f"sqlite:///{file_path}")

    # Base.metadata.create_all(engine)
    # Session = sessionmaker(bind=engine)
    # session = Session()

    # app.template_folder = "../templates"
    # app.static_folder = "../static"

    @app.route("/", methods=["GET"])
    def landing():
        lock_public_key = request.args.get('public')
        pem_key = base64.b64decode(lock_public_key)
        print(pem_key)
        lock_public_key = serialization.load_pem_public_key(pem_key)
        
        shared_secret = private_key.exchange(ec.ECDH(), lock_public_key)
        print("Shared Secret:", shared_secret.hex())
        
        # Make the B64 encoded version of this side's public key
        public_pem = private_key.public_key().public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )
        
        b64_my_pub_key = base64.b64encode(public_pem)
        print(f"B64 key: {b64_my_pub_key}")

        aes_key = HKDF(
            algorithm=hashes.SHA256(),
            length=32,  # 32 bytes = 256 bits (AES-256)
            salt=None,  # Optional salt (can be added for extra security)
            info=b"AES-GCM key derivation",  # Contextual information
        ).derive(shared_secret)

        print(" ".join(format(byte, "02X") for byte in aes_key))

        aesgcm = AESGCM(aes_key)
        nonce = os.urandom(12)

        plaintext = b"LOCK"
        ciphertext = aesgcm.encrypt(nonce, plaintext, associated_data=None)

        b64_nonce = base64.b64encode(nonce)
        b64_ciphertext = base64.b64encode(ciphertext)

        print(f"Nonce {b64_nonce}")
        print(f"Cipher {b64_ciphertext}")

        # my_public_key_pem = private_key.public_key().public_bytes(
        #     encoding=serialization.Encoding.PEM,
        #     format=serialization.PublicFormat.SubjectPublicKeyInfo
        # )
        # encoded_pub = base64.b64encode(my_public_key_pem)
        # print(f"My public pem: {encoded_pub}")

        return "OK"
    
    return app
    
if __name__ == "__main__":
    app = create_app()
    app.run(debug=True, host="0.0.0.0", port=5002)
