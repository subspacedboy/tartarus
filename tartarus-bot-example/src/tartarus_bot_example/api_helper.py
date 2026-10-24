import hashlib
import random
import flatbuffers

from tartarus_bot_example.signer import Signer

from club.subjugated.fb.message.Contract import (
    ContractStart,
    ContractAddSerialNumber,
    ContractAddPublicKey,
    ContractAddTerms,
    ContractAddIsTemporaryUnlockAllowed,
    ContractAddBots,
    ContractStartBotsVector,
    ContractEnd,
)
from club.subjugated.fb.message.Bot import (
    BotStart,
    BotAddName,
    BotAddPublicKey,
    BotAddPermissions,
    BotEnd,
)
from club.subjugated.fb.message.Permission import (
    PermissionStart,
    PermissionAddReceiveEvents,
    PermissionAddCanLock,
    PermissionAddCanRelease,
    PermissionEnd,
)
from club.subjugated.fb.message.SignedMessage import (
    SignedMessageStart,
    SignedMessageAddAuthorityIdentifier,
    SignedMessageAddSignature,
    SignedMessageAddPayload,
    SignedMessageAddPayloadType,
    SignedMessageEnd,
)
from club.subjugated.fb.message.MessagePayload import MessagePayload as SignedMessagePayload
from club.subjugated.fb.message.ReleaseCommand import (
    ReleaseCommandStart,
    ReleaseCommandAddContractSerialNumber,
    ReleaseCommandAddCounter,
    ReleaseCommandAddSerialNumber,
    ReleaseCommandEnd,
)


def make_signed_contract_bytes(
    serial_number: int,
    bot_name: str,
    signer: Signer,
    terms: str = "Locked until I say so",
) -> bytes:
    """Builds a Contract FlatBuffer and wraps it in a SignedMessage signed by the bot."""
    builder = flatbuffers.Builder(1024)

    pub_key = signer.compressed_public_key_bytes()
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

    terms_offset = builder.CreateString(terms)
    contract_pub_key_offset = builder.CreateByteVector(pub_key)

    ContractStartBotsVector(builder, 1)
    builder.PrependUOffsetTRelative(bot_offset)
    bots_vector = builder.EndVector(1)

    ContractStart(builder)
    ContractAddSerialNumber(builder, serial_number)
    ContractAddPublicKey(builder, contract_pub_key_offset)
    ContractAddTerms(builder, terms_offset)
    ContractAddIsTemporaryUnlockAllowed(builder, False)
    ContractAddBots(builder, bots_vector)
    contract_offset = ContractEnd(builder)

    builder.Finish(contract_offset)
    contract_bytes = builder.Output()

    start = int.from_bytes(contract_bytes[0:4], byteorder="little")
    contract_start = int.from_bytes(contract_bytes[start : start + 4], byteorder="little", signed=True)
    vtable_start = start - contract_start
    digest = hashlib.sha256(contract_bytes[vtable_start:]).digest()
    signature = signer.sign(digest)

    authority_offset = builder.CreateString(bot_name)
    signature_offset = builder.CreateByteVector(signature)

    SignedMessageStart(builder)
    SignedMessageAddAuthorityIdentifier(builder, authority_offset)
    SignedMessageAddSignature(builder, signature_offset)
    SignedMessageAddPayload(builder, contract_offset)
    SignedMessageAddPayloadType(builder, SignedMessagePayload.Contract)
    signed_message_offset = SignedMessageEnd(builder)

    builder.Finish(signed_message_offset)
    return bytes(builder.Output())


def make_signed_release_command_bytes(
    bot_name: str,
    contract_serial_number: int,
    counter: int,
    signer: Signer,
) -> bytes:
    """Builds a ReleaseCommand FlatBuffer and wraps it in a SignedMessage signed by the bot."""
    builder = flatbuffers.Builder(1024)

    ReleaseCommandStart(builder)
    ReleaseCommandAddContractSerialNumber(builder, contract_serial_number)
    ReleaseCommandAddCounter(builder, counter)
    ReleaseCommandAddSerialNumber(builder, int(random.getrandbits(16)))
    release_offset = ReleaseCommandEnd(builder)

    builder.Finish(release_offset)
    release_bytes = builder.Output()

    start = int.from_bytes(release_bytes[0:4], byteorder="little")
    cmd_start = int.from_bytes(release_bytes[start : start + 4], byteorder="little", signed=True)
    vtable_start = start - cmd_start
    digest = hashlib.sha256(release_bytes[vtable_start:]).digest()
    signature = signer.sign(digest)

    authority_offset = builder.CreateString(bot_name)
    signature_offset = builder.CreateByteVector(signature)

    SignedMessageStart(builder)
    SignedMessageAddAuthorityIdentifier(builder, authority_offset)
    SignedMessageAddSignature(builder, signature_offset)
    SignedMessageAddPayload(builder, release_offset)
    SignedMessageAddPayloadType(builder, SignedMessagePayload.ReleaseCommand)
    signed_message_offset = SignedMessageEnd(builder)

    builder.Finish(signed_message_offset)
    return bytes(builder.Output())


def make_signed_message_bytes(
    bot_name: str,
    message_text: str,
    signer: Signer,
) -> bytes:
    """Builds a SignedMessage containing the double-SHA256 signature of message_text."""
    builder = flatbuffers.Builder(512)
    digest = hashlib.sha256(message_text.encode("utf-8")).digest()
    signature = signer.sign(digest)

    authority_offset = builder.CreateString(bot_name)
    signature_offset = builder.CreateByteVector(signature)

    SignedMessageStart(builder)
    SignedMessageAddAuthorityIdentifier(builder, authority_offset)
    SignedMessageAddSignature(builder, signature_offset)
    signed_message_offset = SignedMessageEnd(builder)

    builder.Finish(signed_message_offset)
    return bytes(builder.Output())
