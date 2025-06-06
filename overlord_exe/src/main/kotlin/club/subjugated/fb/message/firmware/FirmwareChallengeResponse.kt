// automatically generated by the FlatBuffers compiler, do not modify

package club.subjugated.fb.message.firmware

import com.google.flatbuffers.BaseVector
import com.google.flatbuffers.BooleanVector
import com.google.flatbuffers.ByteVector
import com.google.flatbuffers.Constants
import com.google.flatbuffers.DoubleVector
import com.google.flatbuffers.FlatBufferBuilder
import com.google.flatbuffers.FloatVector
import com.google.flatbuffers.LongVector
import com.google.flatbuffers.StringVector
import com.google.flatbuffers.Struct
import com.google.flatbuffers.Table
import com.google.flatbuffers.UnionVector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sign

@Suppress("unused")
class FirmwareChallengeResponse : Table() {

    fun __init(_i: Int, _bb: ByteBuffer)  {
        __reset(_i, _bb)
    }
    fun __assign(_i: Int, _bb: ByteBuffer) : FirmwareChallengeResponse {
        __init(_i, _bb)
        return this
    }
    fun signature(j: Int) : UByte {
        val o = __offset(4)
        return if (o != 0) {
            bb.get(__vector(o) + j * 1).toUByte()
        } else {
            0u
        }
    }
    val signatureLength : Int
        get() {
            val o = __offset(4); return if (o != 0) __vector_len(o) else 0
        }
    val signatureAsByteBuffer : ByteBuffer get() = __vector_as_bytebuffer(4, 1)
    fun signatureInByteBuffer(_bb: ByteBuffer) : ByteBuffer = __vector_in_bytebuffer(_bb, 4, 1)
    val version : club.subjugated.fb.message.firmware.Version? get() = version(club.subjugated.fb.message.firmware.Version())
    fun version(obj: club.subjugated.fb.message.firmware.Version) : club.subjugated.fb.message.firmware.Version? {
        val o = __offset(6)
        return if (o != 0) {
            obj.__assign(__indirect(o + bb_pos), bb)
        } else {
            null
        }
    }
    companion object {
        fun validateVersion() = Constants.FLATBUFFERS_24_3_25()
        fun getRootAsFirmwareChallengeResponse(_bb: ByteBuffer): FirmwareChallengeResponse = getRootAsFirmwareChallengeResponse(_bb, FirmwareChallengeResponse())
        fun getRootAsFirmwareChallengeResponse(_bb: ByteBuffer, obj: FirmwareChallengeResponse): FirmwareChallengeResponse {
            _bb.order(ByteOrder.LITTLE_ENDIAN)
            return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb))
        }
        fun createFirmwareChallengeResponse(builder: FlatBufferBuilder, signatureOffset: Int, versionOffset: Int) : Int {
            builder.startTable(2)
            addVersion(builder, versionOffset)
            addSignature(builder, signatureOffset)
            return endFirmwareChallengeResponse(builder)
        }
        fun startFirmwareChallengeResponse(builder: FlatBufferBuilder) = builder.startTable(2)
        fun addSignature(builder: FlatBufferBuilder, signature: Int) = builder.addOffset(0, signature, 0)
        @kotlin.ExperimentalUnsignedTypes
        fun createSignatureVector(builder: FlatBufferBuilder, data: UByteArray) : Int {
            builder.startVector(1, data.size, 1)
            for (i in data.size - 1 downTo 0) {
                builder.addByte(data[i].toByte())
            }
            return builder.endVector()
        }
        fun startSignatureVector(builder: FlatBufferBuilder, numElems: Int) = builder.startVector(1, numElems, 1)
        fun addVersion(builder: FlatBufferBuilder, version: Int) = builder.addOffset(1, version, 0)
        fun endFirmwareChallengeResponse(builder: FlatBufferBuilder) : Int {
            val o = builder.endTable()
            return o
        }
    }
}
