# automatically generated by the FlatBuffers compiler, do not modify

# namespace: club

import flatbuffers
from flatbuffers.compat import import_numpy
np = import_numpy()

class Contract(object):
    __slots__ = ['_tab']

    @classmethod
    def GetRootAs(cls, buf, offset=0):
        n = flatbuffers.encode.Get(flatbuffers.packer.uoffset, buf, offset)
        x = Contract()
        x.Init(buf, n + offset)
        return x

    @classmethod
    def GetRootAsContract(cls, buf, offset=0):
        """This method is deprecated. Please switch to GetRootAs."""
        return cls.GetRootAs(buf, offset)
    # Contract
    def Init(self, buf, pos):
        self._tab = flatbuffers.table.Table(buf, pos)

    # Contract
    def PublicKey(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(4))
        if o != 0:
            a = self._tab.Vector(o)
            return self._tab.Get(flatbuffers.number_types.Uint8Flags, a + flatbuffers.number_types.UOffsetTFlags.py_type(j * 1))
        return 0

    # Contract
    def PublicKeyAsNumpy(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(4))
        if o != 0:
            return self._tab.GetVectorAsNumpy(flatbuffers.number_types.Uint8Flags, o)
        return 0

    # Contract
    def PublicKeyLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(4))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

    # Contract
    def PublicKeyIsNone(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(4))
        return o == 0

    # Contract
    def Nonce(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            a = self._tab.Vector(o)
            return self._tab.Get(flatbuffers.number_types.Uint8Flags, a + flatbuffers.number_types.UOffsetTFlags.py_type(j * 1))
        return 0

    # Contract
    def NonceAsNumpy(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            return self._tab.GetVectorAsNumpy(flatbuffers.number_types.Uint8Flags, o)
        return 0

    # Contract
    def NonceLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

    # Contract
    def NonceIsNone(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        return o == 0

    # Contract
    def ConfirmCode(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            a = self._tab.Vector(o)
            return self._tab.Get(flatbuffers.number_types.Uint8Flags, a + flatbuffers.number_types.UOffsetTFlags.py_type(j * 1))
        return 0

    # Contract
    def ConfirmCodeAsNumpy(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            return self._tab.GetVectorAsNumpy(flatbuffers.number_types.Uint8Flags, o)
        return 0

    # Contract
    def ConfirmCodeLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

    # Contract
    def ConfirmCodeIsNone(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        return o == 0

    # Contract
    def Session(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(10))
        if o != 0:
            return self._tab.String(o + self._tab.Pos)
        return None

    # Contract
    def Notes(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(12))
        if o != 0:
            return self._tab.String(o + self._tab.Pos)
        return None

    # Contract
    def IsUnremovable(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(14))
        if o != 0:
            return bool(self._tab.Get(flatbuffers.number_types.BoolFlags, o + self._tab.Pos))
        return False

    # Contract
    def EndConditionType(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(16))
        if o != 0:
            return self._tab.Get(flatbuffers.number_types.Uint8Flags, o + self._tab.Pos)
        return 0

    # Contract
    def EndCondition(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(18))
        if o != 0:
            from flatbuffers.table import Table
            obj = Table(bytearray(), 0)
            self._tab.Union(obj, o)
            return obj
        return None

    # Contract
    def Webhooks(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(20))
        if o != 0:
            x = self._tab.Vector(o)
            x += flatbuffers.number_types.UOffsetTFlags.py_type(j) * 4
            x = self._tab.Indirect(x)
            from subjugated.club.WebHook import WebHook
            obj = WebHook()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None

    # Contract
    def WebhooksLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(20))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

    # Contract
    def WebhooksIsNone(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(20))
        return o == 0

    # Contract
    def IsTemporaryUnlockAllowed(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(22))
        if o != 0:
            return bool(self._tab.Get(flatbuffers.number_types.BoolFlags, o + self._tab.Pos))
        return False

    # Contract
    def UnlockRules(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(24))
        if o != 0:
            x = self._tab.Indirect(o + self._tab.Pos)
            from subjugated.club.TemporaryUnlockRules import TemporaryUnlockRules
            obj = TemporaryUnlockRules()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None

def ContractStart(builder):
    builder.StartObject(11)

def Start(builder):
    ContractStart(builder)

def ContractAddPublicKey(builder, publicKey):
    builder.PrependUOffsetTRelativeSlot(0, flatbuffers.number_types.UOffsetTFlags.py_type(publicKey), 0)

def AddPublicKey(builder, publicKey):
    ContractAddPublicKey(builder, publicKey)

def ContractStartPublicKeyVector(builder, numElems):
    return builder.StartVector(1, numElems, 1)

def StartPublicKeyVector(builder, numElems):
    return ContractStartPublicKeyVector(builder, numElems)

def ContractAddNonce(builder, nonce):
    builder.PrependUOffsetTRelativeSlot(1, flatbuffers.number_types.UOffsetTFlags.py_type(nonce), 0)

def AddNonce(builder, nonce):
    ContractAddNonce(builder, nonce)

def ContractStartNonceVector(builder, numElems):
    return builder.StartVector(1, numElems, 1)

def StartNonceVector(builder, numElems):
    return ContractStartNonceVector(builder, numElems)

def ContractAddConfirmCode(builder, confirmCode):
    builder.PrependUOffsetTRelativeSlot(2, flatbuffers.number_types.UOffsetTFlags.py_type(confirmCode), 0)

def AddConfirmCode(builder, confirmCode):
    ContractAddConfirmCode(builder, confirmCode)

def ContractStartConfirmCodeVector(builder, numElems):
    return builder.StartVector(1, numElems, 1)

def StartConfirmCodeVector(builder, numElems):
    return ContractStartConfirmCodeVector(builder, numElems)

def ContractAddSession(builder, session):
    builder.PrependUOffsetTRelativeSlot(3, flatbuffers.number_types.UOffsetTFlags.py_type(session), 0)

def AddSession(builder, session):
    ContractAddSession(builder, session)

def ContractAddNotes(builder, notes):
    builder.PrependUOffsetTRelativeSlot(4, flatbuffers.number_types.UOffsetTFlags.py_type(notes), 0)

def AddNotes(builder, notes):
    ContractAddNotes(builder, notes)

def ContractAddIsUnremovable(builder, isUnremovable):
    builder.PrependBoolSlot(5, isUnremovable, 0)

def AddIsUnremovable(builder, isUnremovable):
    ContractAddIsUnremovable(builder, isUnremovable)

def ContractAddEndConditionType(builder, endConditionType):
    builder.PrependUint8Slot(6, endConditionType, 0)

def AddEndConditionType(builder, endConditionType):
    ContractAddEndConditionType(builder, endConditionType)

def ContractAddEndCondition(builder, endCondition):
    builder.PrependUOffsetTRelativeSlot(7, flatbuffers.number_types.UOffsetTFlags.py_type(endCondition), 0)

def AddEndCondition(builder, endCondition):
    ContractAddEndCondition(builder, endCondition)

def ContractAddWebhooks(builder, webhooks):
    builder.PrependUOffsetTRelativeSlot(8, flatbuffers.number_types.UOffsetTFlags.py_type(webhooks), 0)

def AddWebhooks(builder, webhooks):
    ContractAddWebhooks(builder, webhooks)

def ContractStartWebhooksVector(builder, numElems):
    return builder.StartVector(4, numElems, 4)

def StartWebhooksVector(builder, numElems):
    return ContractStartWebhooksVector(builder, numElems)

def ContractAddIsTemporaryUnlockAllowed(builder, isTemporaryUnlockAllowed):
    builder.PrependBoolSlot(9, isTemporaryUnlockAllowed, 0)

def AddIsTemporaryUnlockAllowed(builder, isTemporaryUnlockAllowed):
    ContractAddIsTemporaryUnlockAllowed(builder, isTemporaryUnlockAllowed)

def ContractAddUnlockRules(builder, unlockRules):
    builder.PrependUOffsetTRelativeSlot(10, flatbuffers.number_types.UOffsetTFlags.py_type(unlockRules), 0)

def AddUnlockRules(builder, unlockRules):
    ContractAddUnlockRules(builder, unlockRules)

def ContractEnd(builder):
    return builder.EndObject()

def End(builder):
    return ContractEnd(builder)
