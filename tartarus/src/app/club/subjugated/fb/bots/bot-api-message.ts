// automatically generated by the FlatBuffers compiler, do not modify

/* eslint-disable @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any, @typescript-eslint/no-non-null-assertion */

import * as flatbuffers from 'flatbuffers';

import { MessagePayload, unionToMessagePayload, unionListToMessagePayload } from '../../../../club/subjugated/fb/bots/message-payload.js';


export class BotApiMessage {
  bb: flatbuffers.ByteBuffer|null = null;
  bb_pos = 0;
  __init(i:number, bb:flatbuffers.ByteBuffer):BotApiMessage {
  this.bb_pos = i;
  this.bb = bb;
  return this;
}

static getRootAsBotApiMessage(bb:flatbuffers.ByteBuffer, obj?:BotApiMessage):BotApiMessage {
  return (obj || new BotApiMessage()).__init(bb.readInt32(bb.position()) + bb.position(), bb);
}

static getSizePrefixedRootAsBotApiMessage(bb:flatbuffers.ByteBuffer, obj?:BotApiMessage):BotApiMessage {
  bb.setPosition(bb.position() + flatbuffers.SIZE_PREFIX_LENGTH);
  return (obj || new BotApiMessage()).__init(bb.readInt32(bb.position()) + bb.position(), bb);
}

name():string|null
name(optionalEncoding:flatbuffers.Encoding):string|Uint8Array|null
name(optionalEncoding?:any):string|Uint8Array|null {
  const offset = this.bb!.__offset(this.bb_pos, 4);
  return offset ? this.bb!.__string(this.bb_pos + offset, optionalEncoding) : null;
}

payloadType():MessagePayload {
  const offset = this.bb!.__offset(this.bb_pos, 6);
  return offset ? this.bb!.readUint8(this.bb_pos + offset) : MessagePayload.NONE;
}

payload<T extends flatbuffers.Table>(obj:any):any|null {
  const offset = this.bb!.__offset(this.bb_pos, 8);
  return offset ? this.bb!.__union(obj, this.bb_pos + offset) : null;
}

requestId():bigint {
  const offset = this.bb!.__offset(this.bb_pos, 10);
  return offset ? this.bb!.readInt64(this.bb_pos + offset) : BigInt('0');
}

static startBotApiMessage(builder:flatbuffers.Builder) {
  builder.startObject(4);
}

static addName(builder:flatbuffers.Builder, nameOffset:flatbuffers.Offset) {
  builder.addFieldOffset(0, nameOffset, 0);
}

static addPayloadType(builder:flatbuffers.Builder, payloadType:MessagePayload) {
  builder.addFieldInt8(1, payloadType, MessagePayload.NONE);
}

static addPayload(builder:flatbuffers.Builder, payloadOffset:flatbuffers.Offset) {
  builder.addFieldOffset(2, payloadOffset, 0);
}

static addRequestId(builder:flatbuffers.Builder, requestId:bigint) {
  builder.addFieldInt64(3, requestId, BigInt('0'));
}

static endBotApiMessage(builder:flatbuffers.Builder):flatbuffers.Offset {
  const offset = builder.endObject();
  return offset;
}

static finishBotApiMessageBuffer(builder:flatbuffers.Builder, offset:flatbuffers.Offset) {
  builder.finish(offset);
}

static finishSizePrefixedBotApiMessageBuffer(builder:flatbuffers.Builder, offset:flatbuffers.Offset) {
  builder.finish(offset, undefined, true);
}

static createBotApiMessage(builder:flatbuffers.Builder, nameOffset:flatbuffers.Offset, payloadType:MessagePayload, payloadOffset:flatbuffers.Offset, requestId:bigint):flatbuffers.Offset {
  BotApiMessage.startBotApiMessage(builder);
  BotApiMessage.addName(builder, nameOffset);
  BotApiMessage.addPayloadType(builder, payloadType);
  BotApiMessage.addPayload(builder, payloadOffset);
  BotApiMessage.addRequestId(builder, requestId);
  return BotApiMessage.endBotApiMessage(builder);
}
}
