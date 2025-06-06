// automatically generated by the FlatBuffers compiler, do not modify

/* eslint-disable @typescript-eslint/no-unused-vars, @typescript-eslint/no-explicit-any, @typescript-eslint/no-non-null-assertion */

import { AbortCommand } from '../../../../club/subjugated/fb/message/abort-command.js';
import { Acknowledgement } from '../../../../club/subjugated/fb/message/acknowledgement.js';
import { Contract } from '../../../../club/subjugated/fb/message/contract.js';
import { Error } from '../../../../club/subjugated/fb/message/error.js';
import { LockCommand } from '../../../../club/subjugated/fb/message/lock-command.js';
import { PeriodicUpdate } from '../../../../club/subjugated/fb/message/periodic-update.js';
import { ReleaseCommand } from '../../../../club/subjugated/fb/message/release-command.js';
import { ResetCommand } from '../../../../club/subjugated/fb/message/reset-command.js';
import { StartedUpdate } from '../../../../club/subjugated/fb/message/started-update.js';
import { UnlockCommand } from '../../../../club/subjugated/fb/message/unlock-command.js';


export enum MessagePayload {
  NONE = 0,
  Contract = 1,
  LockCommand = 2,
  UnlockCommand = 3,
  ReleaseCommand = 4,
  StartedUpdate = 5,
  PeriodicUpdate = 6,
  Acknowledgement = 7,
  Error = 8,
  AbortCommand = 9,
  ResetCommand = 10
}

export function unionToMessagePayload(
  type: MessagePayload,
  accessor: (obj:AbortCommand|Acknowledgement|Contract|Error|LockCommand|PeriodicUpdate|ReleaseCommand|ResetCommand|StartedUpdate|UnlockCommand) => AbortCommand|Acknowledgement|Contract|Error|LockCommand|PeriodicUpdate|ReleaseCommand|ResetCommand|StartedUpdate|UnlockCommand|null
): AbortCommand|Acknowledgement|Contract|Error|LockCommand|PeriodicUpdate|ReleaseCommand|ResetCommand|StartedUpdate|UnlockCommand|null {
  switch(MessagePayload[type]) {
    case 'NONE': return null; 
    case 'Contract': return accessor(new Contract())! as Contract;
    case 'LockCommand': return accessor(new LockCommand())! as LockCommand;
    case 'UnlockCommand': return accessor(new UnlockCommand())! as UnlockCommand;
    case 'ReleaseCommand': return accessor(new ReleaseCommand())! as ReleaseCommand;
    case 'StartedUpdate': return accessor(new StartedUpdate())! as StartedUpdate;
    case 'PeriodicUpdate': return accessor(new PeriodicUpdate())! as PeriodicUpdate;
    case 'Acknowledgement': return accessor(new Acknowledgement())! as Acknowledgement;
    case 'Error': return accessor(new Error())! as Error;
    case 'AbortCommand': return accessor(new AbortCommand())! as AbortCommand;
    case 'ResetCommand': return accessor(new ResetCommand())! as ResetCommand;
    default: return null;
  }
}

export function unionListToMessagePayload(
  type: MessagePayload, 
  accessor: (index: number, obj:AbortCommand|Acknowledgement|Contract|Error|LockCommand|PeriodicUpdate|ReleaseCommand|ResetCommand|StartedUpdate|UnlockCommand) => AbortCommand|Acknowledgement|Contract|Error|LockCommand|PeriodicUpdate|ReleaseCommand|ResetCommand|StartedUpdate|UnlockCommand|null, 
  index: number
): AbortCommand|Acknowledgement|Contract|Error|LockCommand|PeriodicUpdate|ReleaseCommand|ResetCommand|StartedUpdate|UnlockCommand|null {
  switch(MessagePayload[type]) {
    case 'NONE': return null; 
    case 'Contract': return accessor(index, new Contract())! as Contract;
    case 'LockCommand': return accessor(index, new LockCommand())! as LockCommand;
    case 'UnlockCommand': return accessor(index, new UnlockCommand())! as UnlockCommand;
    case 'ReleaseCommand': return accessor(index, new ReleaseCommand())! as ReleaseCommand;
    case 'StartedUpdate': return accessor(index, new StartedUpdate())! as StartedUpdate;
    case 'PeriodicUpdate': return accessor(index, new PeriodicUpdate())! as PeriodicUpdate;
    case 'Acknowledgement': return accessor(index, new Acknowledgement())! as Acknowledgement;
    case 'Error': return accessor(index, new Error())! as Error;
    case 'AbortCommand': return accessor(index, new AbortCommand())! as AbortCommand;
    case 'ResetCommand': return accessor(index, new ResetCommand())! as ResetCommand;
    default: return null;
  }
}
