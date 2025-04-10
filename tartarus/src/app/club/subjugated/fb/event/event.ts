// automatically generated by the FlatBuffers compiler, do not modify

/* eslint-disable @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any, @typescript-eslint/no-non-null-assertion */

import * as flatbuffers from 'flatbuffers';

import { CommonMetadata } from '../../../../club/subjugated/fb/event/common-metadata.js';
import { EventType } from '../../../../club/subjugated/fb/event/event-type.js';


export class Event {
  bb: flatbuffers.ByteBuffer|null = null;
  bb_pos = 0;
  __init(i:number, bb:flatbuffers.ByteBuffer):Event {
  this.bb_pos = i;
  this.bb = bb;
  return this;
}

static getRootAsEvent(bb:flatbuffers.ByteBuffer, obj?:Event):Event {
  return (obj || new Event()).__init(bb.readInt32(bb.position()) + bb.position(), bb);
}

static getSizePrefixedRootAsEvent(bb:flatbuffers.ByteBuffer, obj?:Event):Event {
  bb.setPosition(bb.position() + flatbuffers.SIZE_PREFIX_LENGTH);
  return (obj || new Event()).__init(bb.readInt32(bb.position()) + bb.position(), bb);
}

metadata(obj?:CommonMetadata):CommonMetadata|null {
  const offset = this.bb!.__offset(this.bb_pos, 4);
  return offset ? (obj || new CommonMetadata()).__init(this.bb!.__indirect(this.bb_pos + offset), this.bb!) : null;
}

eventType():EventType {
  const offset = this.bb!.__offset(this.bb_pos, 6);
  return offset ? this.bb!.readInt8(this.bb_pos + offset) : EventType.Undefined;
}

static startEvent(builder:flatbuffers.Builder) {
  builder.startObject(2);
}

static addMetadata(builder:flatbuffers.Builder, metadataOffset:flatbuffers.Offset) {
  builder.addFieldOffset(0, metadataOffset, 0);
}

static addEventType(builder:flatbuffers.Builder, eventType:EventType) {
  builder.addFieldInt8(1, eventType, EventType.Undefined);
}

static endEvent(builder:flatbuffers.Builder):flatbuffers.Offset {
  const offset = builder.endObject();
  return offset;
}

static createEvent(builder:flatbuffers.Builder, metadataOffset:flatbuffers.Offset, eventType:EventType):flatbuffers.Offset {
  Event.startEvent(builder);
  Event.addMetadata(builder, metadataOffset);
  Event.addEventType(builder, eventType);
  return Event.endEvent(builder);
}
}
