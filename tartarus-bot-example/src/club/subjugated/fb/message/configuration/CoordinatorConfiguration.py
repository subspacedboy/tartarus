# automatically generated by the FlatBuffers compiler, do not modify

# namespace: configuration

import flatbuffers
from flatbuffers.compat import import_numpy
np = import_numpy()

class CoordinatorConfiguration(object):
    __slots__ = ['_tab']

    @classmethod
    def GetRootAs(cls, buf, offset=0):
        n = flatbuffers.encode.Get(flatbuffers.packer.uoffset, buf, offset)
        x = CoordinatorConfiguration()
        x.Init(buf, n + offset)
        return x

    @classmethod
    def GetRootAsCoordinatorConfiguration(cls, buf, offset=0):
        """This method is deprecated. Please switch to GetRootAs."""
        return cls.GetRootAs(buf, offset)
    # CoordinatorConfiguration
    def Init(self, buf, pos):
        self._tab = flatbuffers.table.Table(buf, pos)

    # CoordinatorConfiguration
    def WebUri(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(4))
        if o != 0:
            return self._tab.String(o + self._tab.Pos)
        return None

    # CoordinatorConfiguration
    def WsUri(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(6))
        if o != 0:
            return self._tab.String(o + self._tab.Pos)
        return None

    # CoordinatorConfiguration
    def MqttUri(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(8))
        if o != 0:
            return self._tab.String(o + self._tab.Pos)
        return None

    # CoordinatorConfiguration
    def ApiUri(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(10))
        if o != 0:
            return self._tab.String(o + self._tab.Pos)
        return None

    # CoordinatorConfiguration
    def SafetyKeys(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(12))
        if o != 0:
            x = self._tab.Vector(o)
            x += flatbuffers.number_types.UOffsetTFlags.py_type(j) * 4
            x = self._tab.Indirect(x)
            from club.subjugated.fb.message.configuration.Key import Key
            obj = Key()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None

    # CoordinatorConfiguration
    def SafetyKeysLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(12))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

    # CoordinatorConfiguration
    def SafetyKeysIsNone(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(12))
        return o == 0

    # CoordinatorConfiguration
    def EnableResetCommand(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(14))
        if o != 0:
            return bool(self._tab.Get(flatbuffers.number_types.BoolFlags, o + self._tab.Pos))
        return False

    # CoordinatorConfiguration
    def DisableSafetyKeys(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(16))
        if o != 0:
            return bool(self._tab.Get(flatbuffers.number_types.BoolFlags, o + self._tab.Pos))
        return False

    # CoordinatorConfiguration
    def EnableAuxiliarySafetyKeys(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(18))
        if o != 0:
            return bool(self._tab.Get(flatbuffers.number_types.BoolFlags, o + self._tab.Pos))
        return False

    # CoordinatorConfiguration
    def AuxiliarySafetyKeys(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(20))
        if o != 0:
            x = self._tab.Vector(o)
            x += flatbuffers.number_types.UOffsetTFlags.py_type(j) * 4
            x = self._tab.Indirect(x)
            from club.subjugated.fb.message.configuration.Key import Key
            obj = Key()
            obj.Init(self._tab.Bytes, x)
            return obj
        return None

    # CoordinatorConfiguration
    def AuxiliarySafetyKeysLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(20))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

    # CoordinatorConfiguration
    def AuxiliarySafetyKeysIsNone(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(20))
        return o == 0

    # CoordinatorConfiguration
    def LoginTokenPublicKey(self, j):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(22))
        if o != 0:
            a = self._tab.Vector(o)
            return self._tab.Get(flatbuffers.number_types.Int8Flags, a + flatbuffers.number_types.UOffsetTFlags.py_type(j * 1))
        return 0

    # CoordinatorConfiguration
    def LoginTokenPublicKeyAsNumpy(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(22))
        if o != 0:
            return self._tab.GetVectorAsNumpy(flatbuffers.number_types.Int8Flags, o)
        return 0

    # CoordinatorConfiguration
    def LoginTokenPublicKeyLength(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(22))
        if o != 0:
            return self._tab.VectorLen(o)
        return 0

    # CoordinatorConfiguration
    def LoginTokenPublicKeyIsNone(self):
        o = flatbuffers.number_types.UOffsetTFlags.py_type(self._tab.Offset(22))
        return o == 0

def CoordinatorConfigurationStart(builder):
    builder.StartObject(10)

def Start(builder):
    CoordinatorConfigurationStart(builder)

def CoordinatorConfigurationAddWebUri(builder, webUri):
    builder.PrependUOffsetTRelativeSlot(0, flatbuffers.number_types.UOffsetTFlags.py_type(webUri), 0)

def AddWebUri(builder, webUri):
    CoordinatorConfigurationAddWebUri(builder, webUri)

def CoordinatorConfigurationAddWsUri(builder, wsUri):
    builder.PrependUOffsetTRelativeSlot(1, flatbuffers.number_types.UOffsetTFlags.py_type(wsUri), 0)

def AddWsUri(builder, wsUri):
    CoordinatorConfigurationAddWsUri(builder, wsUri)

def CoordinatorConfigurationAddMqttUri(builder, mqttUri):
    builder.PrependUOffsetTRelativeSlot(2, flatbuffers.number_types.UOffsetTFlags.py_type(mqttUri), 0)

def AddMqttUri(builder, mqttUri):
    CoordinatorConfigurationAddMqttUri(builder, mqttUri)

def CoordinatorConfigurationAddApiUri(builder, apiUri):
    builder.PrependUOffsetTRelativeSlot(3, flatbuffers.number_types.UOffsetTFlags.py_type(apiUri), 0)

def AddApiUri(builder, apiUri):
    CoordinatorConfigurationAddApiUri(builder, apiUri)

def CoordinatorConfigurationAddSafetyKeys(builder, safetyKeys):
    builder.PrependUOffsetTRelativeSlot(4, flatbuffers.number_types.UOffsetTFlags.py_type(safetyKeys), 0)

def AddSafetyKeys(builder, safetyKeys):
    CoordinatorConfigurationAddSafetyKeys(builder, safetyKeys)

def CoordinatorConfigurationStartSafetyKeysVector(builder, numElems):
    return builder.StartVector(4, numElems, 4)

def StartSafetyKeysVector(builder, numElems):
    return CoordinatorConfigurationStartSafetyKeysVector(builder, numElems)

def CoordinatorConfigurationAddEnableResetCommand(builder, enableResetCommand):
    builder.PrependBoolSlot(5, enableResetCommand, 0)

def AddEnableResetCommand(builder, enableResetCommand):
    CoordinatorConfigurationAddEnableResetCommand(builder, enableResetCommand)

def CoordinatorConfigurationAddDisableSafetyKeys(builder, disableSafetyKeys):
    builder.PrependBoolSlot(6, disableSafetyKeys, 0)

def AddDisableSafetyKeys(builder, disableSafetyKeys):
    CoordinatorConfigurationAddDisableSafetyKeys(builder, disableSafetyKeys)

def CoordinatorConfigurationAddEnableAuxiliarySafetyKeys(builder, enableAuxiliarySafetyKeys):
    builder.PrependBoolSlot(7, enableAuxiliarySafetyKeys, 0)

def AddEnableAuxiliarySafetyKeys(builder, enableAuxiliarySafetyKeys):
    CoordinatorConfigurationAddEnableAuxiliarySafetyKeys(builder, enableAuxiliarySafetyKeys)

def CoordinatorConfigurationAddAuxiliarySafetyKeys(builder, auxiliarySafetyKeys):
    builder.PrependUOffsetTRelativeSlot(8, flatbuffers.number_types.UOffsetTFlags.py_type(auxiliarySafetyKeys), 0)

def AddAuxiliarySafetyKeys(builder, auxiliarySafetyKeys):
    CoordinatorConfigurationAddAuxiliarySafetyKeys(builder, auxiliarySafetyKeys)

def CoordinatorConfigurationStartAuxiliarySafetyKeysVector(builder, numElems):
    return builder.StartVector(4, numElems, 4)

def StartAuxiliarySafetyKeysVector(builder, numElems):
    return CoordinatorConfigurationStartAuxiliarySafetyKeysVector(builder, numElems)

def CoordinatorConfigurationAddLoginTokenPublicKey(builder, loginTokenPublicKey):
    builder.PrependUOffsetTRelativeSlot(9, flatbuffers.number_types.UOffsetTFlags.py_type(loginTokenPublicKey), 0)

def AddLoginTokenPublicKey(builder, loginTokenPublicKey):
    CoordinatorConfigurationAddLoginTokenPublicKey(builder, loginTokenPublicKey)

def CoordinatorConfigurationStartLoginTokenPublicKeyVector(builder, numElems):
    return builder.StartVector(1, numElems, 1)

def StartLoginTokenPublicKeyVector(builder, numElems):
    return CoordinatorConfigurationStartLoginTokenPublicKeyVector(builder, numElems)

def CoordinatorConfigurationEnd(builder):
    return builder.EndObject()

def End(builder):
    return CoordinatorConfigurationEnd(builder)
