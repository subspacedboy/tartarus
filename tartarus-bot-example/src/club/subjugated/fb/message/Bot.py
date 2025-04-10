# automatically generated by the FlatBuffers compiler, do not modify

# namespace: message

import flatbuffers
from flatbuffers.compat import import_numpy
np = import_numpy()

class Bot(object):
    __slots__ = ['_tab']

    @classmethod
    def GetRootAs(cls, buf, offset=0):
        n = flatbuffers.encode.Get(flatbuffers.packer.uoffset, buf, offset)
        x = Bot()
        x.Init(buf, n + offset)
        return x

    @classmethod
    def GetRootAsBot(cls, buf, offset=0):
        """This method is deprecated. Please switch to GetRootAs."""
        return cls.GetRootAs(buf, offset)
    # Bot
    def Init(self, buf, pos):
        self._tab = flatbuffers.table.Table(buf, pos)

    # Bot
    def Name(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(4))
        if o != 0:
            return self._tab.String(o + self._tab.Pos)
        return None

    # Bot
    def PublicKey(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            a = self._tab.Vector(o)
            return self._tab.Get(flatbuffers.number_types.Uint8Flags, a + flatbuffers.number_types.UOffsetTFlags.py_type(j * 1))
        return 0

    # Bot
    def PublicKeyAsNumpy(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            return self._tab.GetVectorAsNumpy(flatbuffers.number_types.Uint8Flags, o)
        return 0

    # Bot
    def PublicKeyLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

    # Bot
    def PublicKeyIsNone(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        return o == 0

    # Bot
    def Permissions(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            x = self._tab.Indirect(o + self._tab.Pos)
            from club.subjugated.fb.message.Permission import Permission
            obj = Permission()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None

def BotStart(builder):
    builder.StartObject(3)

def Start(builder):
    BotStart(builder)

def BotAddName(builder, name):
    builder.PrependUOffsetTRelativeSlot(0, flatbuffers.number_types.UOffsetTFlags.py_type(name), 0)

def AddName(builder, name):
    BotAddName(builder, name)

def BotAddPublicKey(builder, publicKey):
    builder.PrependUOffsetTRelativeSlot(1, flatbuffers.number_types.UOffsetTFlags.py_type(publicKey), 0)

def AddPublicKey(builder, publicKey):
    BotAddPublicKey(builder, publicKey)

def BotStartPublicKeyVector(builder, numElems):
    return builder.StartVector(1, numElems, 1)

def StartPublicKeyVector(builder, numElems):
    return BotStartPublicKeyVector(builder, numElems)

def BotAddPermissions(builder, permissions):
    builder.PrependUOffsetTRelativeSlot(2, flatbuffers.number_types.UOffsetTFlags.py_type(permissions), 0)

def AddPermissions(builder, permissions):
    BotAddPermissions(builder, permissions)

def BotEnd(builder):
    return builder.EndObject()

def End(builder):
    return BotEnd(builder)
