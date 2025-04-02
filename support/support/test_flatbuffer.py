
import base64
import io
from cryptography.hazmat.primitives import serialization
import hashlib

import flatbuffers
from subjugated.club import Contract, SignedMessage, Capabilities, WebHook

import qrcode
# from subjugated.club.Capabilities import Online

with open("ec_public_key.pem", "rb") as pub_file:
    public_key = serialization.load_pem_public_key(pub_file.read())

compressed_public_key = public_key.public_bytes(
    encoding=serialization.Encoding.X962,
    format=serialization.PublicFormat.CompressedPoint
)

# Serialize Contract
builder = flatbuffers.Builder(1024)
public_key_offset = builder.CreateString(compressed_public_key)

# webhook_address = "https://subjugated.club/humilibot"
# webhook_addres_offset = builder.CreateString(webhook_address)
# WebHook.WebHookStart(builder)
# WebHook.WebHookAddAddress(builder, webhook_addres_offset)
# webhook = WebHook.WebHookEnd(builder)

# webhooks = [
#     webhook
# ]

capabilities = [
    Capabilities.Capabilities().Online
]

Contract.StartCapabilitiesVector(builder, len(capabilities))
for capability in reversed(capabilities):  # Reverse because FlatBuffers uses stack-based serialization
    builder.PrependByte(capability)
capabilities_offset = builder.EndVector()

# Contract.StartParticipantsVector(builder, len(webhooks))
# for w in reversed(webhooks):
#     builder.PrependUOffsetTRelative(w)
# webhook_offset = builder.EndVector()

Contract.ContractStart(builder)
Contract.ContractAddPublicKey(builder, public_key_offset)
Contract.ContractAddIsBlind(builder, True)
Contract.AddCapabilities(builder, capabilities_offset)
contract = Contract.ContractEnd(builder)
builder.Finish(contract)

payload_buffer = builder.Output()[contract:]
payload_hash = hashlib.sha256(payload_buffer).digest()
payload_hash_vector = builder.CreateByteVector(payload_hash)

SignedMessage.SignedMessageStart(builder)
SignedMessage.SignedMessageAddPayload(builder, contract)
SignedMessage.SignedMessageAddSignature(builder, payload_hash_vector)
signed_message = SignedMessage.SignedMessageEnd(builder)
builder.Finish(signed_message)

buf = builder.Output()

with open("contract.fbsbin", "wb") as f:
    f.write(buf)

qr = qrcode.QRCode(box_size=10, border=5)
qr.add_data(bytes(buf))
qr.make(fit=True)

img = qr.make_image(fill="black", back_color="white")

img_io = io.BytesIO()
img.save(img_io, "PNG")
img_io.seek(0)

with open("contract.png", "wb") as f:
    f.write(img_io.read())
# Get the serialized buffer
# buf = builder.Output()