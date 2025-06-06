// automatically generated by the FlatBuffers compiler, do not modify

/* eslint-disable @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any, @typescript-eslint/no-non-null-assertion */

import * as flatbuffers from 'flatbuffers';

export class Error {
  bb: flatbuffers.ByteBuffer|null = null;
  bb_pos = 0;
  __init(i:number, bb:flatbuffers.ByteBuffer):Error {
  this.bb_pos = i;
  this.bb = bb;
  return this;
}

static getRootAsError(bb:flatbuffers.ByteBuffer, obj?:Error):Error {
  return (obj || new Error()).__init(bb.readInt32(bb.position()) + bb.position(), bb);
}

static getSizePrefixedRootAsError(bb:flatbuffers.ByteBuffer, obj?:Error):Error {
  bb.setPosition(bb.position() + flatbuffers.SIZE_PREFIX_LENGTH);
  return (obj || new Error()).__init(bb.readInt32(bb.position()) + bb.position(), bb);
}

publicKey(index: number):number|null {
  const offset = this.bb!.__offset(this.bb_pos, 4);
  return offset ? this.bb!.readUint8(this.bb!.__vector(this.bb_pos + offset) + index) : 0;
}

publicKeyLength():number {
  const offset = this.bb!.__offset(this.bb_pos, 4);
  return offset ? this.bb!.__vector_len(this.bb_pos + offset) : 0;
}

publicKeyArray():Uint8Array|null {
  const offset = this.bb!.__offset(this.bb_pos, 4);
  return offset ? new Uint8Array(this.bb!.bytes().buffer, this.bb!.bytes().byteOffset + this.bb!.__vector(this.bb_pos + offset), this.bb!.__vector_len(this.bb_pos + offset)) : null;
}

session():string|null
session(optionalEncoding:flatbuffers.Encoding):string|Uint8Array|null
session(optionalEncoding?:any):string|Uint8Array|null {
  const offset = this.bb!.__offset(this.bb_pos, 6);
  return offset ? this.bb!.__string(this.bb_pos + offset, optionalEncoding) : null;
}

serialNumber():number {
  const offset = this.bb!.__offset(this.bb_pos, 8);
  return offset ? this.bb!.readUint16(this.bb_pos + offset) : 0;
}

counter():number {
  const offset = this.bb!.__offset(this.bb_pos, 10);
  return offset ? this.bb!.readUint16(this.bb_pos + offset) : 0;
}

message():string|null
message(optionalEncoding:flatbuffers.Encoding):string|Uint8Array|null
message(optionalEncoding?:any):string|Uint8Array|null {
  const offset = this.bb!.__offset(this.bb_pos, 12);
  return offset ? this.bb!.__string(this.bb_pos + offset, optionalEncoding) : null;
}

static startError(builder:flatbuffers.Builder) {
  builder.startObject(5);
}

static addPublicKey(builder:flatbuffers.Builder, publicKeyOffset:flatbuffers.Offset) {
  builder.addFieldOffset(0, publicKeyOffset, 0);
}

static createPublicKeyVector(builder:flatbuffers.Builder, data:number[]|Uint8Array):flatbuffers.Offset {
  builder.startVector(1, data.length, 1);
  for (let i = data.length - 1; i >= 0; i--) {
    builder.addInt8(data[i]!);
  }
  return builder.endVector();
}

static startPublicKeyVector(builder:flatbuffers.Builder, numElems:number) {
  builder.startVector(1, numElems, 1);
}

static addSession(builder:flatbuffers.Builder, sessionOffset:flatbuffers.Offset) {
  builder.addFieldOffset(1, sessionOffset, 0);
}

static addSerialNumber(builder:flatbuffers.Builder, serialNumber:number) {
  builder.addFieldInt16(2, serialNumber, 0);
}

static addCounter(builder:flatbuffers.Builder, counter:number) {
  builder.addFieldInt16(3, counter, 0);
}

static addMessage(builder:flatbuffers.Builder, messageOffset:flatbuffers.Offset) {
  builder.addFieldOffset(4, messageOffset, 0);
}

static endError(builder:flatbuffers.Builder):flatbuffers.Offset {
  const offset = builder.endObject();
  return offset;
}

static createError(builder:flatbuffers.Builder, publicKeyOffset:flatbuffers.Offset, sessionOffset:flatbuffers.Offset, serialNumber:number, counter:number, messageOffset:flatbuffers.Offset):flatbuffers.Offset {
  Error.startError(builder);
  Error.addPublicKey(builder, publicKeyOffset);
  Error.addSession(builder, sessionOffset);
  Error.addSerialNumber(builder, serialNumber);
  Error.addCounter(builder, counter);
  Error.addMessage(builder, messageOffset);
  return Error.endError(builder);
}
}
