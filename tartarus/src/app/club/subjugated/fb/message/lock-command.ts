// automatically generated by the FlatBuffers compiler, do not modify

/* eslint-disable @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any, @typescript-eslint/no-non-null-assertion */

import * as flatbuffers from 'flatbuffers';

export class LockCommand {
  bb: flatbuffers.ByteBuffer|null = null;
  bb_pos = 0;
  __init(i:number, bb:flatbuffers.ByteBuffer):LockCommand {
  this.bb_pos = i;
  this.bb = bb;
  return this;
}

static getRootAsLockCommand(bb:flatbuffers.ByteBuffer, obj?:LockCommand):LockCommand {
  return (obj || new LockCommand()).__init(bb.readInt32(bb.position()) + bb.position(), bb);
}

static getSizePrefixedRootAsLockCommand(bb:flatbuffers.ByteBuffer, obj?:LockCommand):LockCommand {
  bb.setPosition(bb.position() + flatbuffers.SIZE_PREFIX_LENGTH);
  return (obj || new LockCommand()).__init(bb.readInt32(bb.position()) + bb.position(), bb);
}

contractSerialNumber():number {
  const offset = this.bb!.__offset(this.bb_pos, 4);
  return offset ? this.bb!.readUint16(this.bb_pos + offset) : 0;
}

serialNumber():number {
  const offset = this.bb!.__offset(this.bb_pos, 6);
  return offset ? this.bb!.readUint16(this.bb_pos + offset) : 0;
}

counter():number {
  const offset = this.bb!.__offset(this.bb_pos, 8);
  return offset ? this.bb!.readUint16(this.bb_pos + offset) : 0;
}

static startLockCommand(builder:flatbuffers.Builder) {
  builder.startObject(3);
}

static addContractSerialNumber(builder:flatbuffers.Builder, contractSerialNumber:number) {
  builder.addFieldInt16(0, contractSerialNumber, 0);
}

static addSerialNumber(builder:flatbuffers.Builder, serialNumber:number) {
  builder.addFieldInt16(1, serialNumber, 0);
}

static addCounter(builder:flatbuffers.Builder, counter:number) {
  builder.addFieldInt16(2, counter, 0);
}

static endLockCommand(builder:flatbuffers.Builder):flatbuffers.Offset {
  const offset = builder.endObject();
  return offset;
}

static createLockCommand(builder:flatbuffers.Builder, contractSerialNumber:number, serialNumber:number, counter:number):flatbuffers.Offset {
  LockCommand.startLockCommand(builder);
  LockCommand.addContractSerialNumber(builder, contractSerialNumber);
  LockCommand.addSerialNumber(builder, serialNumber);
  LockCommand.addCounter(builder, counter);
  return LockCommand.endLockCommand(builder);
}
}
