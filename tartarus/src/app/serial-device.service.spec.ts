import { TestBed } from '@angular/core/testing';
import { SerialDeviceService } from './serial-device.service';
import * as flatbuffers from 'flatbuffers';
import { SignedMessage } from './club/subjugated/fb/message/signed-message';
import { MessagePayload } from './club/subjugated/fb/message/message-payload';
import { Acknowledgement } from './club/subjugated/fb/message/acknowledgement';
import { Error as FBError } from './club/subjugated/fb/message/error';

describe('SerialDeviceService', () => {
  let service: SerialDeviceService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(SerialDeviceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should correctly parse an Acknowledgement FlatBuffer result from Base64', () => {
    const builder = new flatbuffers.Builder(1024);

    const sessionOffset = builder.createString('QAAAW1');
    const pubKeyOffset = builder.createByteVector(new Uint8Array([1, 2, 3, 4]));

    Acknowledgement.startAcknowledgement(builder);
    Acknowledgement.addPublicKey(builder, pubKeyOffset);
    Acknowledgement.addSession(builder, sessionOffset);
    Acknowledgement.addSerialNumber(builder, 12345);
    Acknowledgement.addCounter(builder, 2);
    const ackOffset = Acknowledgement.endAcknowledgement(builder);

    const sigOffset = builder.createByteVector(new Uint8Array(64));
    SignedMessage.startSignedMessage(builder);
    SignedMessage.addSignature(builder, sigOffset);
    SignedMessage.addPayload(builder, ackOffset);
    SignedMessage.addPayloadType(builder, MessagePayload.Acknowledgement);
    const signedOffset = SignedMessage.endSignedMessage(builder);

    builder.finish(signedOffset);
    const bytes = builder.asUint8Array();
    const base64 = btoa(String.fromCharCode(...bytes));

    const result = service.parseResultPayload(base64);
    expect(result.type).toBe('ack');
    expect(result.serialNumber).toBe(12345);
    expect(result.counter).toBe(2);
    expect(result.resultBase64).toBe(base64);
  });

  it('should correctly parse an Error FlatBuffer result from Base64', () => {
    const builder = new flatbuffers.Builder(1024);

    const sessionOffset = builder.createString('QAAAW1');
    const msgOffset = builder.createString('Servo jammed');
    const pubKeyOffset = builder.createByteVector(new Uint8Array([1, 2, 3, 4]));

    FBError.startError(builder);
    FBError.addPublicKey(builder, pubKeyOffset);
    FBError.addSession(builder, sessionOffset);
    FBError.addSerialNumber(builder, 12345);
    FBError.addCounter(builder, 2);
    FBError.addMessage(builder, msgOffset);
    const errOffset = FBError.endError(builder);

    const sigOffset = builder.createByteVector(new Uint8Array(64));
    SignedMessage.startSignedMessage(builder);
    SignedMessage.addSignature(builder, sigOffset);
    SignedMessage.addPayload(builder, errOffset);
    SignedMessage.addPayloadType(builder, MessagePayload.Error);
    const signedOffset = SignedMessage.endSignedMessage(builder);

    builder.finish(signedOffset);
    const bytes = builder.asUint8Array();
    const base64 = btoa(String.fromCharCode(...bytes));

    const result = service.parseResultPayload(base64);
    expect(result.type).toBe('error');
    expect(result.errorMessage).toBe('Servo jammed');
    expect(result.serialNumber).toBe(12345);
    expect(result.counter).toBe(2);
  });
});
