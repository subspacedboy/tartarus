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
class GetLatestFirmwareResponse : Table() {

    fun __init(_i: Int, _bb: ByteBuffer)  {
        __reset(_i, _bb)
    }
    fun __assign(_i: Int, _bb: ByteBuffer) : GetLatestFirmwareResponse {
        __init(_i, _bb)
        return this
    }
    fun digest(j: Int) : UByte {
        val o = __offset(4)
        return if (o != 0) {
            bb.get(__vector(o) + j * 1).toUByte()
        } else {
            0u
        }
    }
    val digestLength : Int
        get() {
            val o = __offset(4); return if (o != 0) __vector_len(o) else 0
        }
    val digestAsByteBuffer : ByteBuffer get() = __vector_as_bytebuffer(4, 1)
    fun digestInByteBuffer(_bb: ByteBuffer) : ByteBuffer = __vector_in_bytebuffer(_bb, 4, 1)
    val firmwareName : String?
        get() {
            val o = __offset(6)
            return if (o != 0) {
                __string(o + bb_pos)
            } else {
                null
            }
        }
    val firmwareNameAsByteBuffer : ByteBuffer get() = __vector_as_bytebuffer(6, 1)
    fun firmwareNameInByteBuffer(_bb: ByteBuffer) : ByteBuffer = __vector_in_bytebuffer(_bb, 6, 1)
    val versionName : String?
        get() {
            val o = __offset(8)
            return if (o != 0) {
                __string(o + bb_pos)
            } else {
                null
            }
        }
    val versionNameAsByteBuffer : ByteBuffer get() = __vector_as_bytebuffer(8, 1)
    fun versionNameInByteBuffer(_bb: ByteBuffer) : ByteBuffer = __vector_in_bytebuffer(_bb, 8, 1)
    val size : Int
        get() {
            val o = __offset(10)
            return if(o != 0) bb.getInt(o + bb_pos) else 0
        }
    companion object {
        fun validateVersion() = Constants.FLATBUFFERS_24_3_25()
        fun getRootAsGetLatestFirmwareResponse(_bb: ByteBuffer): GetLatestFirmwareResponse = getRootAsGetLatestFirmwareResponse(_bb, GetLatestFirmwareResponse())
        fun getRootAsGetLatestFirmwareResponse(_bb: ByteBuffer, obj: GetLatestFirmwareResponse): GetLatestFirmwareResponse {
            _bb.order(ByteOrder.LITTLE_ENDIAN)
            return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb))
        }
        fun createGetLatestFirmwareResponse(builder: FlatBufferBuilder, digestOffset: Int, firmwareNameOffset: Int, versionNameOffset: Int, size: Int) : Int {
            builder.startTable(4)
            addSize(builder, size)
            addVersionName(builder, versionNameOffset)
            addFirmwareName(builder, firmwareNameOffset)
            addDigest(builder, digestOffset)
            return endGetLatestFirmwareResponse(builder)
        }
        fun startGetLatestFirmwareResponse(builder: FlatBufferBuilder) = builder.startTable(4)
        fun addDigest(builder: FlatBufferBuilder, digest: Int) = builder.addOffset(0, digest, 0)
        @kotlin.ExperimentalUnsignedTypes
        fun createDigestVector(builder: FlatBufferBuilder, data: UByteArray) : Int {
            builder.startVector(1, data.size, 1)
            for (i in data.size - 1 downTo 0) {
                builder.addByte(data[i].toByte())
            }
            return builder.endVector()
        }
        fun startDigestVector(builder: FlatBufferBuilder, numElems: Int) = builder.startVector(1, numElems, 1)
        fun addFirmwareName(builder: FlatBufferBuilder, firmwareName: Int) = builder.addOffset(1, firmwareName, 0)
        fun addVersionName(builder: FlatBufferBuilder, versionName: Int) = builder.addOffset(2, versionName, 0)
        fun addSize(builder: FlatBufferBuilder, size: Int) = builder.addInt(3, size, 0)
        fun endGetLatestFirmwareResponse(builder: FlatBufferBuilder) : Int {
            val o = builder.endTable()
            return o
        }
    }
}
