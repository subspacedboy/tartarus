// automatically generated by the FlatBuffers compiler, do not modify

/* eslint-disable @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any, @typescript-eslint/no-non-null-assertion */

import * as flatbuffers from 'flatbuffers';

export class CreateCommandRequest {
  bb: flatbuffers.ByteBuffer|null = null;
  bb_pos = 0;
  __init(i:number, bb:flatbuffers.ByteBuffer):CreateCommandRequest {
  this.bb_pos = i;
  this.bb = bb;
  return this;
}

static getRootAsCreateCommandRequest(bb:flatbuffers.ByteBuffer, obj?:CreateCommandRequest):CreateCommandRequest {
  return (obj || new CreateCommandRequest()).__init(bb.readInt32(bb.position()) + bb.position(), bb);
}

static getSizePrefixedRootAsCreateCommandRequest(bb:flatbuffers.ByteBuffer, obj?:CreateCommandRequest):CreateCommandRequest {
  bb.setPosition(bb.position() + flatbuffers.SIZE_PREFIX_LENGTH);
  return (obj || new CreateCommandRequest()).__init(bb.readInt32(bb.position()) + bb.position(), bb);
}

commandBody(index: number):number|null {
  const offset = this.bb!.__offset(this.bb_pos, 4);
  return offset ? this.bb!.readUint8(this.bb!.__vector(this.bb_pos + offset) + index) : 0;
}

commandBodyLength():number {
  const offset = this.bb!.__offset(this.bb_pos, 4);
  return offset ? this.bb!.__vector_len(this.bb_pos + offset) : 0;
}

commandBodyArray():Uint8Array|null {
  const offset = this.bb!.__offset(this.bb_pos, 4);
  return offset ? new Uint8Array(this.bb!.bytes().buffer, this.bb!.bytes().byteOffset + this.bb!.__vector(this.bb_pos + offset), this.bb!.__vector_len(this.bb_pos + offset)) : null;
}

shareableToken():string|null
shareableToken(optionalEncoding:flatbuffers.Encoding):string|Uint8Array|null
shareableToken(optionalEncoding?:any):string|Uint8Array|null {
  const offset = this.bb!.__offset(this.bb_pos, 6);
  return offset ? this.bb!.__string(this.bb_pos + offset, optionalEncoding) : null;
}

contractName():string|null
contractName(optionalEncoding:flatbuffers.Encoding):string|Uint8Array|null
contractName(optionalEncoding?:any):string|Uint8Array|null {
  const offset = this.bb!.__offset(this.bb_pos, 8);
  return offset ? this.bb!.__string(this.bb_pos + offset, optionalEncoding) : null;
}

static startCreateCommandRequest(builder:flatbuffers.Builder) {
  builder.startObject(3);
}

static addCommandBody(builder:flatbuffers.Builder, commandBodyOffset:flatbuffers.Offset) {
  builder.addFieldOffset(0, commandBodyOffset, 0);
}

static createCommandBodyVector(builder:flatbuffers.Builder, data:number[]|Uint8Array):flatbuffers.Offset {
  builder.startVector(1, data.length, 1);
  for (let i = data.length - 1; i >= 0; i--) {
    builder.addInt8(data[i]!);
  }
  return builder.endVector();
}

static startCommandBodyVector(builder:flatbuffers.Builder, numElems:number) {
  builder.startVector(1, numElems, 1);
}

static addShareableToken(builder:flatbuffers.Builder, shareableTokenOffset:flatbuffers.Offset) {
  builder.addFieldOffset(1, shareableTokenOffset, 0);
}

static addContractName(builder:flatbuffers.Builder, contractNameOffset:flatbuffers.Offset) {
  builder.addFieldOffset(2, contractNameOffset, 0);
}

static endCreateCommandRequest(builder:flatbuffers.Builder):flatbuffers.Offset {
  const offset = builder.endObject();
  return offset;
}

static createCreateCommandRequest(builder:flatbuffers.Builder, commandBodyOffset:flatbuffers.Offset, shareableTokenOffset:flatbuffers.Offset, contractNameOffset:flatbuffers.Offset):flatbuffers.Offset {
  CreateCommandRequest.startCreateCommandRequest(builder);
  CreateCommandRequest.addCommandBody(builder, commandBodyOffset);
  CreateCommandRequest.addShareableToken(builder, shareableTokenOffset);
  CreateCommandRequest.addContractName(builder, contractNameOffset);
  return CreateCommandRequest.endCreateCommandRequest(builder);
}
}
