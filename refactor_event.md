# Plan: Refactor `StartedUpdate` and `PeriodicUpdate` into `event.fbs`

## 1. Overview & Goals

Currently, `StartedUpdate` and `PeriodicUpdate` live in `flatbuffers/contract.fbs` (`union MessagePayload` inside `SignedMessage`), while lock audit/lifecycle events live in `flatbuffers/event.fbs` (`Event` inside `SignedEvent`). Because of this split:
- Every physical lock state transition (`local_lock`, `lock`, `local_unlock`, `emergency_unlock`, `unlock`) in firmware (`LockCtx`) signs and publishes **two** separate messages: a `PeriodicUpdate` (to update `LockSession` state on the coordinator) and an `Event` (for contract audit logging and bot fan-out).
- `StartedUpdate` and `PeriodicUpdate` duplicate fields (`session`, `current_contract_serial`, `local_lock`, `local_unlock`) already represented by `CommonMetadata` and `EventType`.

### Key Objectives
1. **Unify `StartedUpdate` and `PeriodicUpdate` into `event.fbs`**: Make all event variants full-fledged tables inside `union EventType` on `Event`.
2. **Add `device_uptime` to `PeriodicUpdate`**: Report the device's monotonic uptime in seconds (`ulong` / `u64`).
3. **Eliminate Double-Publishing on Lock/Unlock Transitions**: Physical state changes (`local_lock`, `lock`, `local_unlock`, `emergency_unlock`, `unlock`) only emit their corresponding durable `SignedEvent`; `PeriodicUpdate` is emitted strictly on the 60-second heartbeat timer.
4. **Ephemeral Handling for `StartedUpdate` & `PeriodicUpdate`**:
   - **Firmware**: `StartedUpdate` and `PeriodicUpdate` use `counter = 0` and bypass the persistent NVS `PersistentEventRingBuffer` (not stored in flash and not replayed).
   - **Coordinator**: `StartedUpdate` and `PeriodicUpdate` are **not** stored in the database (`EventRepository` / `EventDeliveryRepository`), but **are** opportunistically forwarded over WebSocket (`EventWebSocketHandler`) to active bots on the contract that have `receiveEvents == true`.

---

## 2. FlatBuffers Schema Changes

### 2.1 `flatbuffers/event.fbs`

All event types become first-class tables in `union EventType`. `StartedUpdate` and `PeriodicUpdate` drop their duplicate `session` and `current_contract_serial` fields (which live on `CommonMetadata`), `PeriodicUpdate` drops the legacy `local_lock` / `local_unlock` booleans, and `PeriodicUpdate` gains `device_uptime: ulong`:

```fbs
namespace club.subjugated.fb.event;

table CommonMetadata {
    lock_session: string;
    contract_serial_number: ushort;
    serial_number: ushort;
    counter: ushort;
    event_id: string;
    correlation_id: string;
}

table AcceptContract {}
table LocalLock {}
table LocalUnlock {}
table Lock {}
table Unlock {}
table ReleaseContract {}
table EmergencyUnlock {}

table StartedUpdate {
    public_key: [ubyte];
    started_with_local_contract: bool;
    is_locked: bool;
}

table PeriodicUpdate {
    is_locked: bool;
    emergency_unlocked: bool = false;
    device_uptime: ulong;
}

union EventType {
    AcceptContract,
    LocalLock,
    LocalUnlock,
    Lock,
    Unlock,
    ReleaseContract,
    EmergencyUnlock,
    StartedUpdate,
    PeriodicUpdate
}

table Event {
    metadata: CommonMetadata;
    event: EventType;
}

table SignedEvent {
    signature: [ubyte];
    payload: Event;
}

root_type SignedEvent;
```

### 2.2 `flatbuffers/contract.fbs`

Remove `StartedUpdate` and `PeriodicUpdate` table definitions and remove them from `union MessagePayload`:

```fbs
union MessagePayload {
  Contract,
  LockCommand,
  UnlockCommand,
  ReleaseCommand,
  Acknowledgement,
  Error,
  AbortCommand,
  ResetCommand
}
```

### 2.3 Code Generation (`make_flatbuffers.sh`)

Run `./make_flatbuffers.sh` to regenerate FlatBuffers bindings across:
- Rust firmware (`src/contract_generated.rs`, `src/event_generated.rs`)
- Kotlin coordinator & CLI (`tartarus-coordinator/src/main/kotlin/club/subjugated/fb/...`, `overlord_exe/src/main/kotlin/club/subjugated/fb/...`)
- Python bot example (`tartarus-bot-example/src/club/...`)
- TypeScript web frontend (`tartarus/src/app/club/...`)

---

## 3. Cryptographic Serialization Order (Protocol Adherence)

Because signature verification (`calculate_payload_hash` in `src/fb_helper.rs` and `getBytesOfTableWithVTable` in `fb_helpers.kt`) hashes from `Event`'s `vtableStart` to the end of the buffer, FlatBuffers' bottom-up buffer construction requires a strict creation order whenever building a `SignedEvent`:

1. Create all strings/byte vectors belonging to `CommonMetadata` and the inner event table (e.g., `lock_session`, `event_id`, `correlation_id`, `StartedUpdate.public_key`).
2. Create the `CommonMetadata` table and the inner event union table (`AcceptContract`, `LocalLock`, ..., `StartedUpdate`, or `PeriodicUpdate`).
3. Create the `Event` table referencing `metadata`, `event_type: EventType::...`, and `event: Some(inner_table.as_union_value())`.
4. **Pass 1 (Signing)**: Call `builder.finish(event, None)` and compute the ECDSA signature over `[actual_start..]` via `calculate_signature_with_backend`.
5. **Pass 2 (Wrapping)**: Repeat steps 1–3 identically in a fresh `FlatBufferBuilder`, then create the `signature` byte vector and `SignedEvent` table *after* `Event::create(...)`, and finish with `builder.finish(signed_event, None)`.

---

## 4. Rust Firmware Changes

### 4.1 `src/lock_ctx.rs`

1. **Eliminate Double-Publishing on State Transitions**:
   - Remove `self.enqueue_periodic_update_message(...)` from:
     - `local_lock()`
     - `lock()`
     - `local_unlock()`
     - `emergency_unlock()`
     - `unlock()`
   - Each of these methods now only emits its corresponding durable event (`EventType::LocalLock`, `EventType::Lock`, `EventType::LocalUnlock`, `EventType::EmergencyUnlock`, `EventType::Unlock`).

2. **Durable Event Construction (`enqueue_bot_event`)**:
   - Accept `EventType` (or an internal enum for durable events) and construct the matching empty union table (`AcceptContract::create`, `LocalLock::create`, etc.) before `Event::create`.
   - Allocate monotonic `counter` from `self.event_ring.current_counter()`.
   - Persist the serialized `SignedEvent` bytes to `self.event_ring.append(&self.nvs, &data)`.
   - Enqueue via `SignedMessageTransport::new_for_event(data, current_contract_serial, counter)` to publish on `locks/{session_token}/events`.

3. **Ephemeral `StartedUpdate` Event (`enqueue_announce_message`)**:
   - Build `CommonMetadata` with `counter = 0`, `event_id = "{session_token}:{current_contract_serial}:0"`, `correlation_id = None`.
   - Build `StartedUpdate` (`public_key`, `started_with_local_contract`, `is_locked`).
   - Build `Event` (`event_type: EventType::StartedUpdate`, `event: Some(started_update.as_union_value())`) and sign into `SignedEvent`.
   - **Do not** append to `self.event_ring`.
   - Enqueue to `TopicType::LockEvents` (`locks/{session_token}/events`) with `event_info: None` (so MQTT QoS 1 publish completion does not trigger NVS ring-buffer acknowledgment).

4. **Ephemeral `PeriodicUpdate` Event (`enqueue_periodic_update_message`)**:
   - Called only from the 60-second timer in `LockCtx::tick()`.
   - Compute monotonic device uptime in seconds from ESP-IDF's hardware monotonic timer:
     ```rust
     let device_uptime = (unsafe { esp_idf_svc::sys::esp_timer_get_time() }.max(0) as u64) / 1_000_000;
     ```
   - Build `CommonMetadata` with `counter = 0`, `event_id = "{session_token}:{current_contract_serial}:0"`, `correlation_id = None`.
   - Build `PeriodicUpdate` (`is_locked: self.is_locked`, `emergency_unlocked: self.emergency_unlocked`, `device_uptime`).
   - Build `Event` (`event_type: EventType::PeriodicUpdate`, `event: Some(periodic_update.as_union_value())`) and sign into `SignedEvent`.
   - **Do not** append to `self.event_ring`.
   - Enqueue to `TopicType::LockEvents` (`locks/{session_token}/events`) with `event_info: None`.

### 4.2 `src/event_buffer.rs`

- Update `inspect_events_json` and unit tests in `src/event_buffer.rs` to construct/read the union-based `EventArgs { metadata, event_type, event }`.

---

## 5. Kotlin Coordinator Changes

### 5.1 `util/fb_helpers.kt`

1. Remove `StartedUpdatePayload` and `PeriodicUpdatePayload` from `ValidatedPayload`, `findVerificationKeyRequirement`, `signedMessageBytesValidator`, and `signedMessageBytesValidatorWithExternalKey`.
2. Add support for self-signed `StartedUpdate` verification on `SignedEvent`:
   - `verifySignedEventWithEmbeddedKey(buf: ByteBuffer): SignedEvent?` — inspects `signedEvent.payload`, verifies `eventType == EventType.StartedUpdate`, extracts `StartedUpdate` via `payload.event(StartedUpdate())`, reads `startedUpdate.publicKey`, and verifies the signature over `payload` (`Event` table + vtable bytes).
   - Keep `verifySignedEvent(buf: ByteBuffer, key: ByteArray): SignedEvent?` for all events verified against the stored `LockSession` (or `ProvisionedHardwareUnit`) public key.

### 5.2 `services/MqttListenerService.kt`

1. Remove `StartedUpdatePayload` and `PeriodicUpdatePayload` branches from the `locks/+/updates` listener (`lockUpdateTopic` now only handles `AcknowledgementPayload` and `ErrorPayload`).
2. Expose or delegate the post-announce actions (updating `liveClients`, updating `ProvisionedHardwareUnit`, replaying `PENDING` commands to `locks/$sessionToken`, sending `CoordinatorConfiguration` to `configuration/$sessionToken`, and sending `FirmwareChallengeRequest` to `firmware/$sessionToken`) so they execute when `EventType.StartedUpdate` arrives on `locks/+/events`.

### 5.3 `services/EventService.kt` & `services/LockSessionService.kt`

When `processIncomingLockEvent(sessionToken, payloadBytes)` receives a `SignedEvent` on `locks/{session}/events`:

1. **Peek `eventType` & Verify Signature**:
   - Parse `SignedEvent.getRootAsSignedEvent(ByteBuffer.wrap(payloadBytes))`.
   - **If `eventType == EventType.StartedUpdate`**:
     - Verify signature using the embedded `StartedUpdate.publicKey` (`verifySignedEventWithEmbeddedKey`).
     - Run the `StartedUpdate` bootstrap flow: create/update `LockSession` with `publicKey` and `isLocked = startedUpdate.isLocked`, link `ProvisionedHardwareUnit`, replay pending commands, and send configuration + firmware challenge.
     - Opportunistically forward over WebSocket to active contract bots (if `contractSerial > 0` and contract exists) **without** saving to `EventRepository` or `EventDeliveryRepository`.
     - Return `null` (no durable `Event` row created).
   - **For all other `EventType`s**:
     - Look up `LockSession` (including fallback to `ProvisionedHardwareUnit` key rotation if the stored `LockSession` key fails verification).
     - Verify signature via `verifySignedEvent`.

2. **Handle `EventType.PeriodicUpdate` (Ephemeral)**:
   - Extract `PeriodicUpdate` via `eventPayload.event(PeriodicUpdate())`.
   - Call `lockSessionService.handlePeriodicUpdate(sessionToken, contractSerial, periodicUpdate)` to update `lockSession.isLocked`, `lockSession.emergencyUnlocked`, log `periodicUpdate.deviceUptime`, and publish `PeriodicUpdateEvent`.
   - Opportunistically forward over WebSocket to active contract bots **without** saving to `EventRepository` or `EventDeliveryRepository`.
   - Return `null`.

3. **Handle Durable Events (`AcceptContract`, `LocalLock`, `LocalUnlock`, `Lock`, `Unlock`, `ReleaseContract`, `EmergencyUnlock`)**:
   - **Update `LockSession` state immediately**:
     - `AcceptContract`, `LocalLock`, `Lock` $\rightarrow$ `lockSession.isLocked = true`
     - `LocalUnlock`, `Unlock` $\rightarrow$ `lockSession.isLocked = false`
     - `EmergencyUnlock` $\rightarrow$ `lockSession.isLocked = false`, `lockSession.emergencyUnlocked = true`
     - `ReleaseContract` $\rightarrow$ `lockSession.isLocked = false`, `lockSession.emergencyUnlocked = false`
     - Save updated `LockSession`.
   - **Deduplicate & Persist**:
     - Check `EventRepository` for existing `(contractId, counter)` or `(sessionToken, contractSerial, counter)`.
     - Save `Event` entity in `EventRepository`.
   - **Durable Bot Fan-out**:
     - Create `EventDelivery` records in `EventDeliveryRepository` and send over `EventWebSocketHandler` (marking `delivered = true` on success).

4. **Opportunistic Bot Fan-out Helper (`fanOutEphemeralEventToContractBots`)**:
   - Used by `StartedUpdate` and `PeriodicUpdate`.
   - Finds the contract matching `(lockSession.id, contractSerial)` (or if `contractSerial == 0`, checks if there is an active `CONFIRMED` contract on `lockSession`).
   - Filters embedded bots where `permissions?.receiveEvents == true`, looks up `Bot` in `BotRepository` with `state == BotState.ACTIVE`, and sends the `"bot_event"` WebSocket payload (`eventId = metadata.eventId`, `eventType = "StartedUpdate"` / `"PeriodicUpdate"`, `counter = 0`, `rawSignedEventBase64`, plus `deviceUptime` for `PeriodicUpdate`) directly via `eventWebSocketHandler.sendBotMessage(...)` without creating `Event` or `EventDelivery` DB rows.

---

## 6. Testing & Verification Plan

1. **FlatBuffers Generation**:
   - Run `./make_flatbuffers.sh` and verify clean generation across Rust, Kotlin, Python, and TypeScript.
2. **Coordinator Unit Tests (`EventServiceTest.kt`)**:
   - Update existing test `processIncomingLockEventUsesStandardNameScheme` to construct the `Unlock` union table on `Event`.
   - Add test for `EventType.StartedUpdate`: verifies embedded-key signature validation, `LockSession` creation/update, opportunistic WebSocket delivery to active bots, and zero writes to `EventRepository` / `EventDeliveryRepository`.
   - Add test for `EventType.PeriodicUpdate`: verifies `device_uptime` round-trip, `LockSession` `isLocked`/`emergencyUnlocked` update, opportunistic WebSocket delivery to active bots, and zero writes to `EventRepository` / `EventDeliveryRepository`.
   - Add test verifying that durable state-transition events (`LocalLock`, `EmergencyUnlock`, `Unlock`, etc.) update `LockSession.isLocked` and `LockSession.emergencyUnlocked` in addition to persisting the `Event`.
3. **Rust Unit Tests**:
   - Update and run unit tests in `src/event_buffer.rs` and `src/fb_helper.rs`.
