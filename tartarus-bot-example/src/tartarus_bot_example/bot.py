import asyncio
import json
import uuid


import asyncio
import json

from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import serialization

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
        return self.private_key.sign(data, ec.ECDSA(hashes.SHA256()))

signer = Signer()

import asyncio
import paho.mqtt.client as mqtt

from club.subjugated.fb.event.SignedEvent import SignedEvent
from club.subjugated.fb.event.EventType import EventType

class AsyncMQTTClient:
    def __init__(self, bot_name, broker, port, topic, websocket_path="/mqtt"):
        self.broker = broker
        self.port = port
        self.bot_name = bot_name
        # self.topic = topic
        self.client = mqtt.Client(
            client_id="b-something_my_client_id",
            transport="websockets")
        self.client.username = self.bot_name

        self.client.ws_set_options(path=websocket_path)

        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message

    def on_connect(self, client, userdata, flags, rc):
        print(f"Connected with result code {rc}")
        client.subscribe(f"bots/inbox_{self.bot_name}")

    def on_message(self, client, userdata, msg):
        print(f"Received message: {msg.topic}")

        se = SignedEvent.GetRootAsSignedEvent(msg.payload)
        e = se.Payload()

        common_metadata = e.Metadata()
        print(common_metadata.LockSession())
        print(common_metadata.ContractSerialNumber())
        
        match e.EventType():
            case EventType.AcceptContract:
                print("Contract accepted")
            case EventType.LocalLock:
                print("Local lock")
            case EventType.LocalUnlock:
                print("Local Unlock")
            case EventType.ReleaseContract:
                print("Contract released")


    async def start(self):
        self.client.connect(self.broker, self.port, 60)
        self.client.loop_start()

        try:
            while True:
                await asyncio.sleep(1)
        except asyncio.CancelledError:
            self.client.loop_stop()
            self.client.disconnect()

async def main():
    client = AsyncMQTTClient(bot_name="b-42R6AGO", broker="localhost", port=4447, topic="test/topic")
    await client.start()

asyncio.run(main())

