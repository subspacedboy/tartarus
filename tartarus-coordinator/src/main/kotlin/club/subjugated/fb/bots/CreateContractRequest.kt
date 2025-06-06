// automatically generated by the FlatBuffers compiler, do not modify

package club.subjugated.fb.bots

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
class CreateContractRequest : Table() {

    fun __init(_i: Int, _bb: ByteBuffer)  {
        __reset(_i, _bb)
    }
    fun __assign(_i: Int, _bb: ByteBuffer) : CreateContractRequest {
        __init(_i, _bb)
        return this
    }
    val shareableToken : String?
        get() {
            val o = __offset(4)
            return if (o != 0) {
                __string(o + bb_pos)
            } else {
                null
            }
        }
    val shareableTokenAsByteBuffer : ByteBuffer get() = __vector_as_bytebuffer(4, 1)
    fun shareableTokenInByteBuffer(_bb: ByteBuffer) : ByteBuffer = __vector_in_bytebuffer(_bb, 4, 1)
    fun contract(j: Int) : UByte {
        val o = __offset(6)
        return if (o != 0) {
            bb.get(__vector(o) + j * 1).toUByte()
        } else {
            0u
        }
    }
    val contractLength : Int
        get() {
            val o = __offset(6); return if (o != 0) __vector_len(o) else 0
        }
    val contractAsByteBuffer : ByteBuffer get() = __vector_as_bytebuffer(6, 1)
    fun contractInByteBuffer(_bb: ByteBuffer) : ByteBuffer = __vector_in_bytebuffer(_bb, 6, 1)
    companion object {
        fun validateVersion() = Constants.FLATBUFFERS_24_3_25()
        fun getRootAsCreateContractRequest(_bb: ByteBuffer): CreateContractRequest = getRootAsCreateContractRequest(_bb, CreateContractRequest())
        fun getRootAsCreateContractRequest(_bb: ByteBuffer, obj: CreateContractRequest): CreateContractRequest {
            _bb.order(ByteOrder.LITTLE_ENDIAN)
            return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb))
        }
        fun createCreateContractRequest(builder: FlatBufferBuilder, shareableTokenOffset: Int, contractOffset: Int) : Int {
            builder.startTable(2)
            addContract(builder, contractOffset)
            addShareableToken(builder, shareableTokenOffset)
            return endCreateContractRequest(builder)
        }
        fun startCreateContractRequest(builder: FlatBufferBuilder) = builder.startTable(2)
        fun addShareableToken(builder: FlatBufferBuilder, shareableToken: Int) = builder.addOffset(0, shareableToken, 0)
        fun addContract(builder: FlatBufferBuilder, contract: Int) = builder.addOffset(1, contract, 0)
        @kotlin.ExperimentalUnsignedTypes
        fun createContractVector(builder: FlatBufferBuilder, data: UByteArray) : Int {
            builder.startVector(1, data.size, 1)
            for (i in data.size - 1 downTo 0) {
                builder.addByte(data[i].toByte())
            }
            return builder.endVector()
        }
        fun startContractVector(builder: FlatBufferBuilder, numElems: Int) = builder.startVector(1, numElems, 1)
        fun endCreateContractRequest(builder: FlatBufferBuilder) : Int {
            val o = builder.endTable()
            return o
        }
    }
}
