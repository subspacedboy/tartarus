import asyncio

import asyncio
import json
import hashlib

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

import flatbuffers
import argparse

import random
import base64
from club.subjugated.fb.message.LockCommand import LockCommand

from club.subjugated.fb.bots.BotApiMessage import *
from club.subjugated.fb.bots.GetContractRequest import *
from club.subjugated.fb.bots.GetContractResponse import *
from club.subjugated.fb.bots.CreateContractRequest import *
from club.subjugated.fb.bots.MessagePayload import *


from club.subjugated.fb.message.Contract import *
from club.subjugated.fb.message.Bot import *
from club.subjugated.fb.message.Permission import *
from club.subjugated.fb.message.SignedMessage import *
from club.subjugated.fb.message.MessagePayload import MessagePayload as SignedMessagePayload

def make_create_contract_request(shareableToken: str, bot_name: str):
    # First make contract
    builder = flatbuffers.Builder(1024)

    pub_key = signer.public_key.public_bytes(
        encoding=serialization.Encoding.X962,
        format=serialization.PublicFormat.CompressedPoint
    )
    print(" ".join(str(b) for b in pub_key))
    # pub_key = base64.b64encode(pub_key)
    pub_key_offset = builder.CreateByteVector(pub_key)

    PermissionStart(builder)
    PermissionAddReceiveEvents(builder, True)
    PermissionAddCanLock(builder, True)
    PermissionAddCanRelease(builder, True)
    permission_offset = PermissionEnd(builder)

    bot_name_offset = builder.CreateString(bot_name)
    BotStart(builder)
    BotAddName(builder, bot_name_offset)
    BotAddPublicKey(builder, pub_key_offset)
    BotAddPermissions(builder, permission_offset)
    bot_offset = BotEnd(builder)

    terms_offset = builder.CreateString("Locked until I say so")
    pub_key_offset = builder.CreateByteVector(pub_key)
    random_16_bit = random.getrandbits(16)

    ContractStart(builder)
    ContractAddSerialNumber(builder, random_16_bit)
    ContractAddPublicKey(builder, pub_key_offset)
    ContractAddTerms(builder, terms_offset)
    ContractAddIsTemporaryUnlockAllowed(builder, False)
    ContractAddBots(builder, bot_offset)
    contract_offset = ContractEnd(builder)

    builder.Finish(contract_offset)
    bytes = builder.Output()

    # c = Contract()
    # c.Init(bytes, 0)
    # print(c._tab.Bytes)
    # print(" ".join(str(b) for b in c._tab.Bytes))
    start = bytes[0]
    contract_start = bytes[start]
    vtable_start = start - contract_start
    # print(vtable_start)
    hash = hashlib.sha256(bytes[vtable_start:]).digest()
    # print(hash)
    signature = signer.sign(hash)
    print(" ".join(str(b) for b in signature))

    signature_offset = builder.CreateByteVector(signature)

    SignedMessageStart(builder)
    SignedMessageAddSignature(builder, signature_offset)
    SignedMessageAddPayload(builder, contract_offset)
    SignedMessageAddPayloadType(builder, SignedMessagePayload.Contract)
    signed_message_offset = SignedMessageEnd(builder)

    builder.Finish(signed_message_offset)
    signed_messaged_bytes = builder.Output()

    builder = flatbuffers.Builder(1024)
    contract_offset = builder.CreateByteVector(signed_messaged_bytes)
    shareable_offset = builder.CreateString(shareableToken)

    CreateContractRequestStart(builder)
    CreateContractRequestAddContract(builder, contract_offset)
    CreateContractRequestAddShareableToken(builder, shareable_offset)
    create_request = CreateContractRequestEnd(builder)

    bot_name_offset = builder.CreateString(bot_name)
    BotApiMessageStart(builder)
    BotApiMessageAddName(builder, bot_name_offset)
    BotApiMessageAddPayloadType(builder, MessagePayload.CreateContractRequest)
    BotApiMessageAddPayload(builder, create_request)
    random_64_bit = int(random.getrandbits(63))
    BotApiMessageAddRequestId(builder, random_64_bit)
    
    bot_api_message_offset = BotApiMessageEnd(builder)

    builder.Finish(bot_api_message_offset)
    buf = builder.Output()

    # # Get the serialized bytes
    message = BotApiMessage.GetRootAsBotApiMessage(buf, 0)
    return message


def make_contract_request(bot_name, lock_session, contract_serial_number) -> BotApiMessage:
    builder = flatbuffers.Builder(1024)

    # Create the lock_session string
    lock_session_offset = builder.CreateString(lock_session)

    # Build GetContractRequest
    GetContractRequestStart(builder)
    GetContractRequestAddLockSession(builder, lock_session_offset)
    GetContractRequestAddContractSerialNumber(builder, contract_serial_number)
    get_contract_request_offset = GetContractRequestEnd(builder)

    name_offset = builder.CreateString(bot_name)
    BotApiMessageStart(builder)
    
    BotApiMessageAddName(builder, name_offset)
    BotApiMessageAddPayloadType(builder, MessagePayload.GetContractRequest)
    BotApiMessageAddPayload(builder, get_contract_request_offset)
    random_64_bit = int(random.getrandbits(63))
    BotApiMessageAddRequestId(builder, random_64_bit)
    
    bot_api_message_offset = BotApiMessageEnd(builder)

    # Finalize the message
    builder.Finish(bot_api_message_offset)

    # BotApiMessage.Init()
    buf = builder.Output()

    # Get the serialized bytes
    message = BotApiMessage.GetRootAsBotApiMessage(buf, 0)
    return message

import asyncio
import paho.mqtt.client as mqtt

from club.subjugated.fb.event.SignedEvent import SignedEvent
from club.subjugated.fb.event.EventType import EventType


def has_callable(obj, method_name):
    return hasattr(obj, method_name) and callable(getattr(obj, method_name))

class AsyncTartarusClient:
    def __init__(self, bot_name, broker, port, topic, websocket_path="/mqtt", callback_obj=None):
        self.broker = broker
        self.port = port
        self.bot_name = bot_name
        # self.topic = topic
        self.client = mqtt.Client(
            client_id=f"bot-{bot_name}",
            transport="websockets",)
        self.client.username = self.bot_name
        self.client.keepalive = 30

        self.client.ws_set_options(path=websocket_path)

        self.client.on_connect = self.on_connect
        self.client.on_message = self.on_message

        self.callback_obj = callback_obj
        self.loop = asyncio.get_event_loop()
        # self.response_queue = asyncio.Queue()

        self.responses = {}

    def on_connect(self, client, userdata, flags, rc):
        print(f"Connected!")
        client.subscribe(f"bots/inbox_events_{self.bot_name}")
        client.subscribe(f"bots/inbox_api_{self.bot_name}")

    def on_message(self, client, userdata, msg):
        print(f"Received message on: {msg.topic}")

        if "events" in msg.topic:
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
                    if has_callable(self.callback_obj, 'on_lock'):
                        asyncio.run_coroutine_threadsafe(self.callback_obj.on_lock(self, se), self.loop)
                case EventType.LocalUnlock:
                    print("Local Unlock")
                    if has_callable(self.callback_obj, 'on_unlock'):
                        asyncio.run_coroutine_threadsafe(self.callback_obj.on_unlock(self, se), self.loop)
                case EventType.ReleaseContract:
                    print("Contract released")
                case EventType.Lock:
                    print("Locked via command")
                    if has_callable(self.callback_obj, 'on_lock'):
                        self.callback_obj.on_lock()
                case EventType.Unlock:
                    print("Unlocked via command")
                    if has_callable(self.callback_obj, 'on_unlock'):
                        self.callback_obj.on_unlock()
        if "api" in msg.topic:
            api_message = BotApiMessage.GetRootAsBotApiMessage(msg.payload)

            if api_message.RequestId() in self.responses:
                print("Had a correlation")
                queue = self.responses[api_message.RequestId()]
                del self.responses[api_message.RequestId()]
                self.loop.call_soon_threadsafe(queue.put_nowait, api_message)

    def make_api_request(self, message: BotApiMessage, queue):
        self.client.publish("coordinator/inbox", message._tab.Bytes)
        self.responses[message.RequestId()] = queue

    async def start(self):
        self.client.connect(self.broker, self.port, 60)
        self.client.loop_start()

        try:
            while True:
                await asyncio.sleep(1)
        except asyncio.CancelledError:
            self.client.loop_stop()
            self.client.disconnect()


class TimerBot():
    def __init__(self, bot_name):
        self.response_queue = asyncio.Queue()
        self.bot_name = bot_name
        self.tartarus = None

    async def issue_contract(self, shareableToken):
        message = make_create_contract_request(shareableToken, self.bot_name)
        print("Making create contract request")
        self.tartarus.make_api_request(message, self.response_queue)


    async def on_lock(self, tartarus, event):
        print("TimerBot on_lock")
        e = event.Payload()
        common_metadata = e.Metadata()
        print(common_metadata.LockSession())
        print(common_metadata.ContractSerialNumber())

        print("Make API request from bot")
        message = make_contract_request(self.bot_name, common_metadata.LockSession(),common_metadata.ContractSerialNumber())
        print(f"message {message}")
        tartarus.make_api_request(message, self.response_queue)

        try:
            response = await asyncio.wait_for(self.response_queue.get(), timeout=5.0)
            print(f"Received response: {response}")
        except asyncio.TimeoutError:
            print("Response timeout!")


    async def on_unlock(self, tartarus, event):
        print("TimerBot un_lock")


def parse_args():
    parser = argparse.ArgumentParser(description="Process a shareable token.")
    parser.add_argument("shareableToken", type=str, help="A shareable token string")
    return parser.parse_args()

async def call_timer_method(timer, shareable):
    # while True:
    await asyncio.sleep(1)  # Adjust the interval as needed
    print("Calling timer method...")
    await timer.issue_contract(shareable)  # Replace with your actual timer method

async def main():
    args = parse_args()

    make_create_contract_request(args.shareableToken, "b-42R6AGO")
    # exit()

    timer = TimerBot(bot_name="b-42R6AGO")

    try:
        client = AsyncTartarusClient(bot_name="b-42R6AGO", broker="localhost", port=4447, topic="test/topic", callback_obj=timer)
        timer.tartarus = client
        asyncio.create_task(call_timer_method(timer, args.shareableToken))

        await client.start()
    except Exception as e:
        print(f"Unhandled exception in main: {e}")
        raise e



asyncio.run(main())

