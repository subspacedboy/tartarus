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
class GetContractRequest : Table() {

    fun __init(_i: Int, _bb: ByteBuffer)  {
        __reset(_i, _bb)
    }
    fun __assign(_i: Int, _bb: ByteBuffer) : GetContractRequest {
        __init(_i, _bb)
        return this
    }
    val lockSession : String?
        get() {
            val o = __offset(4)
            return if (o != 0) {
                __string(o + bb_pos)
            } else {
                null
            }
        }
    val lockSessionAsByteBuffer : ByteBuffer get() = __vector_as_bytebuffer(4, 1)
    fun lockSessionInByteBuffer(_bb: ByteBuffer) : ByteBuffer = __vector_in_bytebuffer(_bb, 4, 1)
    val contractSerialNumber : UShort
        get() {
            val o = __offset(6)
            return if(o != 0) bb.getShort(o + bb_pos).toUShort() else 0u
        }
    companion object {
        fun validateVersion() = Constants.FLATBUFFERS_24_3_25()
        fun getRootAsGetContractRequest(_bb: ByteBuffer): GetContractRequest = getRootAsGetContractRequest(_bb, GetContractRequest())
        fun getRootAsGetContractRequest(_bb: ByteBuffer, obj: GetContractRequest): GetContractRequest {
            _bb.order(ByteOrder.LITTLE_ENDIAN)
            return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb))
        }
        fun createGetContractRequest(builder: FlatBufferBuilder, lockSessionOffset: Int, contractSerialNumber: UShort) : Int {
            builder.startTable(2)
            addLockSession(builder, lockSessionOffset)
            addContractSerialNumber(builder, contractSerialNumber)
            return endGetContractRequest(builder)
        }
        fun startGetContractRequest(builder: FlatBufferBuilder) = builder.startTable(2)
        fun addLockSession(builder: FlatBufferBuilder, lockSession: Int) = builder.addOffset(0, lockSession, 0)
        fun addContractSerialNumber(builder: FlatBufferBuilder, contractSerialNumber: UShort) = builder.addShort(1, contractSerialNumber.toShort(), 0)
        fun endGetContractRequest(builder: FlatBufferBuilder) : Int {
            val o = builder.endTable()
            return o
        }
    }
}
