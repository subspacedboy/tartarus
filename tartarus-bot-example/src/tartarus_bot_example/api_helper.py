import random
import hashlib

from tartarus_bot_example.signer import Signer

from club.subjugated.fb.message.LockCommand import LockCommand

from club.subjugated.fb.bots.BotApiMessage import *
from club.subjugated.fb.bots.GetContractRequest import *
from club.subjugated.fb.bots.GetContractResponse import *
from club.subjugated.fb.bots.CreateContractRequest import *
from club.subjugated.fb.bots.MessagePayload import *
from club.subjugated.fb.bots.CreateCommandRequest import *
from club.subjugated.fb.bots.CreateMessageRequest import *

from club.subjugated.fb.message.Contract import *
from club.subjugated.fb.message.Bot import *
from club.subjugated.fb.message.Permission import *
from club.subjugated.fb.message.SignedMessage import *
from club.subjugated.fb.message.MessagePayload import MessagePayload as SignedMessagePayload
from club.subjugated.fb.message.ReleaseCommand import *

from cryptography.hazmat.primitives import serialization


def make_create_contract_request(shareableToken: str, serial_number: int, bot_name: str, signer: Signer):
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

    ContractStartBotsVector(builder, 1) # num bots
    builder.PrependUOffsetTRelative(bot_offset)
    bots_vector = builder.EndVector(1)

    ContractStart(builder)
    ContractAddSerialNumber(builder, serial_number)
    ContractAddPublicKey(builder, pub_key_offset)
    ContractAddTerms(builder, terms_offset)
    ContractAddIsTemporaryUnlockAllowed(builder, False)
    ContractAddBots(builder, bots_vector)
    contract_offset = ContractEnd(builder)

    builder.Finish(contract_offset)
    bytes = builder.Output()

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

def make_release_command_request(bot_name: str, contract_name: str, shareable_token: str, contract_serial_number:int, counter: int, signer: Signer):
    # First make contract
    builder = flatbuffers.Builder(1024)

    ReleaseCommandStart(builder)
    ReleaseCommandAddContractSerialNumber(builder, contract_serial_number)
    ReleaseCommandAddCounter(builder, counter)
    ReleaseCommandAddSerialNumber(builder, int(random.getrandbits(16)))
    release_offset = ReleaseCommandEnd(builder)

    builder.Finish(release_offset)
    bytes = builder.Output()

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
    SignedMessageAddPayload(builder, release_offset)
    SignedMessageAddPayloadType(builder, SignedMessagePayload.ReleaseCommand)
    signed_message_offset = SignedMessageEnd(builder)

    builder.Finish(signed_message_offset)
    signed_messaged_bytes = builder.Output()

    builder = flatbuffers.Builder(1024)
    command_offset = builder.CreateByteVector(signed_messaged_bytes)
    contract_name_offset = builder.CreateString(contract_name)
    shareable_token_offset = builder.CreateString(shareable_token)
    # shareable_offset = builder.CreateString(shareableToken)

    CreateCommandRequestStart(builder)
    CreateCommandRequestAddCommandBody(builder, command_offset)
    CreateCommandRequestAddContractName(builder, contract_name_offset)
    CreateCommandRequestAddShareableToken(builder, shareable_token_offset)

    create_request = CreateCommandRequestEnd(builder)

    bot_name_offset = builder.CreateString(bot_name)
    BotApiMessageStart(builder)
    BotApiMessageAddName(builder, bot_name_offset)
    BotApiMessageAddPayloadType(builder, MessagePayload.CreateCommandRequest)
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

from club.subjugated.fb.bots.GetLockSessionRequest import *

def make_get_lock_session_request(bot_name: str, shareable_token: str) -> BotApiMessage:
    builder = flatbuffers.Builder(1024)

    # Create the lock_session string
    shareable_token_offset = builder.CreateString(shareable_token)

    # Build GetContractRequest
    GetLockSessionRequestStart(builder)
    GetLockSessionRequestAddShareableToken(builder, shareable_token_offset)
    get_lock_session_request_offset = GetLockSessionRequestEnd(builder)

    name_offset = builder.CreateString(bot_name)
    BotApiMessageStart(builder)
    
    BotApiMessageAddName(builder, name_offset)
    BotApiMessageAddPayloadType(builder, MessagePayload.GetLockSessionRequest)
    BotApiMessageAddPayload(builder, get_lock_session_request_offset)
    random_64_bit = int(random.getrandbits(63))
    BotApiMessageAddRequestId(builder, random_64_bit)
    
    bot_api_message_offset = BotApiMessageEnd(builder)

    builder.Finish(bot_api_message_offset)
    buf = builder.Output()

    # Get the serialized bytes
    message = BotApiMessage.GetRootAsBotApiMessage(buf, 0)
    return message

def make_new_message_request(bot_name: str, message: str, contract_name: str) -> BotApiMessage:
    builder = flatbuffers.Builder(1024)

    # Create the lock_session string
    contract_name_offset = builder.CreateString(contract_name)
    message_offset = builder.CreateString(message)

    CreateMessageRequestStart(builder)
    CreateMessageRequestAddContractName(builder, contract_name_offset)
    CreateMessageRequestAddMessage(builder, message_offset)
    create_message_request_offset = CreateCommandRequestEnd(builder)

    name_offset = builder.CreateString(bot_name)
    BotApiMessageStart(builder)
    
    BotApiMessageAddName(builder, name_offset)
    BotApiMessageAddPayloadType(builder, MessagePayload.CreateMessageRequest)
    BotApiMessageAddPayload(builder, create_message_request_offset)
    random_64_bit = int(random.getrandbits(63))
    BotApiMessageAddRequestId(builder, random_64_bit)
    
    bot_api_message_offset = BotApiMessageEnd(builder)

    builder.Finish(bot_api_message_offset)
    buf = builder.Output()

    # Get the serialized bytes
    message = BotApiMessage.GetRootAsBotApiMessage(buf, 0)
    return message

def response_as_get_lock_session_response(response : BotApiMessage) -> dict :
    from club.subjugated.fb.bots import MessagePayload
    from club.subjugated.fb.bots.GetLockSessionResponse import GetLockSessionResponse

    if response.PayloadType() == MessagePayload.MessagePayload.GetLockSessionResponse:
        actual_response = GetLockSessionResponse()
        actual_response.Init(response.Payload().Bytes, response.Payload().Pos)

        data = {}
        data['pub_key'] = [actual_response.PublicKey(i) for i in range(actual_response.PublicKeyLength())]
        data['name'] = actual_response.Name().decode("utf-8")
        data['available_for_contract'] = actual_response.AvailableForContract()
        return data
    raise "Wrong payload type for response"

def response_as_get_contract_response(response: BotApiMessage) -> dict :
    from club.subjugated.fb.bots import MessagePayload
    from club.subjugated.fb.bots.GetContractResponse import GetContractResponse

    if response.PayloadType() == MessagePayload.MessagePayload.GetContractResponse:
        actual_response = GetContractResponse()
        print(f"Response {response._tab.Bytes}")
        actual_response.Init(response.Payload().Bytes, response.Payload().Pos)

        data = {}
        data['name'] = actual_response.Name().decode("utf-8")
        data['state'] = actual_response.State().decode("utf-8")
        data['next_counter'] = actual_response.NextCounter()
        return data
    raise "Wrong payload type for response"

def response_as_create_contract_response(response: BotApiMessage) -> dict:
    from club.subjugated.fb.bots import MessagePayload
    from club.subjugated.fb.bots.CreateContractResponse import CreateContractResponse

    if response.PayloadType() == MessagePayload.MessagePayload.CreateContractResponse:
        actual_response = CreateContractResponse()
        actual_response.Init(response.Payload().Bytes, response.Payload().Pos)

        data = {}
        data['contract_name'] = actual_response.ContractName().decode('utf-8')
        return data
    raise "Wrong payload type for response"