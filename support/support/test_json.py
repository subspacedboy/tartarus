
import base64
import io
from cryptography.hazmat.primitives import serialization
import hashlib

import json
from subjugated.club import Contract, SignedMessage, Capabilities, WebHook

import qrcode
import zstd

# from subjugated.club.Capabilities import Online

with open("ec_public_key.pem", "rb") as pub_file:
    public_key = serialization.load_pem_public_key(pub_file.read())

compressed_public_key = public_key.public_bytes(
    encoding=serialization.Encoding.X962,
    format=serialization.PublicFormat.CompressedPoint
)
# compressed_public_key = public_key.public_bytes(
#     encoding=serialization.Encoding.PEM,
#     format=serialization.PublicFormat.SubjectPublicKeyInfo
# )

contract = {
    "public_key" : base64.b64encode(compressed_public_key).decode(),
    "is_partial" : True,
    "complete_contract_address" : "https://subjugated.club/contracts/v2/hello"
}

contract_as_json = json.dumps(contract)
print(contract_as_json)
sha = hashlib.sha256()
sha.update(contract_as_json.encode('utf-8'))
payload_hash = sha.hexdigest()

message = {
    "signature" : payload_hash,
    "contract" : contract
}

print(message)

cdata_mt = zstd.compress(json.dumps(message).encode("utf-8"), 1, 4)
with open("contract.zstd", "wb") as f:
    f.write(cdata_mt)

with open("contract.json", "wb") as f:
    f.write(json.dumps(message).encode("utf-8"))

# qr = qrcode.QRCode(box_size=10, border=5)
# qr.add_data(bytes(buf))
# qr.make(fit=True)

# img = qr.make_image(fill="black", back_color="white")

# img_io = io.BytesIO()
# img.save(img_io, "PNG")
# img_io.seek(0)

# with open("contract.png", "wb") as f:
#     f.write(img_io.read())
