# Architecture Notes: How It All Works

This doc is a continuous work in progress.

This serves as the rough architecture guide for how the whole
thing works if you're curious.

There are four core components in the Tartarus ecosystem:

1. **The Lock (Firmware)**: ESP32-S3 firmware written in Rust managing physical locking state, hardware security (Infineon Optiga Trust M or software fallback), display rendering via `embedded-graphics`, and local Bluetooth LE / USB serial interfaces.
2. **The Coordinator**: Spring Boot + Kotlin backend providing REST APIs, MQTT messaging, hardware attestation verification, and WebSocket session management.
3. **The Web Application**: Modern Angular application built with Spartan UI and Tailwind primitives at [Tartarus](https://tartarus.subjugated.club), supporting in-browser Web Serial flashing, serial command dispatch, BLE management, and contract administration.
4. **Overlord & Bot Platform**: Automated agent and bot services communicating over duplex WebSockets with author ownership for reactive lock automation and social interactions (e.g. Bluesky).

The "center" of the system is the Coordinator. The locks need somewhere to send and receive updates, process commands, etc.
When they startup they check their local configuration and go through these steps:

## Cryptography

Almost every entity in the system has a public and private keypair.

Going into the details of a asymmetric cryptography is way beyond the scope
of this doc. That being said:

* All keys use curve `secp256r1`.
* All public keys are represented in compressed form.
* All hashes are calculated with `sha256`.
* All signatures are to be in `raw` format, not `DER`.
* All symmetric cryptography is `aes-256-gcm`.
* All password that need to be verified are stored with `Scrypt` and params: 16384, 8, 1, 32.
* Where `HKDF` is used, we populate the info field with the task at hand.
* All `ByteArrays` are encoded with Base64 unless they are meant to be in a URL in which case we use URL-safe Base64.
* Online keys (`SafetyKey`) are stored as PKCS8 since they aren't transmitted.

## `SignedMessage`

All the commands and contracts are implemented as `SignedMessage`.
A `SignedMessage` is a flatbuffer with a signed payload. So when you make
a contract you generate the entire `Contract` flatbuffer, calculate a hash of the
whole table, sign that, and then attach the signature to the outer-wrapper of the
`SignedMessage`.

Every entity that receives or forwards a `SignedMessage` will check the signature
and reject the message if it fails to pass.

### Commands, Counters & Serial Numbers

All the commands after a `Contract` is accepted are to include a counter value. In order for a `SignedMessage` to be valid
the counter value in the message has to be greater than or equal to the current counter in the lock.
This is designed to prevent message replay (Someone trying to use an unlock code twice).

All commands also have to include both their own unique serial number and the serial number
of the contract they are issued under. If the contract serial number in a message doesn't match
the current contract it will be rejected. This prevents one person's messages from being used on
a different lock.

### Hashing

The hash of a flatbuffer has to also include the vtable of the message.
Because the format essentially uses "pointers" in the format, all of those
pointers also have to be covered- and those are the vtable. You'll see code like this almost anywhere a signature is needed:

```
builder.Finish(contract_offset)
bytes = builder.Output()

start = bytes[0]
contract_start = bytes[start]
vtable_start = start - contract_start
hash = hashlib.sha256(bytes[vtable_start:]).digest()
signature = signer.sign(hash)
```

### Signature Format

Signatures need to be raw r + s values, not `DER` encoded.
This was an early design decision to minimize the size of `SignedMessage` tables
so they would help fit inside scannable QR code blocks. The downside is most
verification libraries like BouncyCastle want signatures in `DER` format so you'll need to convert them
around.

## `SignedEvent` & Unified Telemetry

The lock generates cryptographically signed events for the coordinator, web admin sessions, and any authorized bots listed on a contract. They are similar in structure to `SignedMessage`, carrying a signed payload with common metadata and timestamps.

The events in the contract lifecycle and telemetry system are:

* `AcceptContract` - This event is emitted after the contract has been confirmed in the hardware.
* `Lock` - The lock was locked via command from the coordinator or a bot.
* `Unlock` - The lock was unlocked via command.
* `LocalLock` - The lock button on the face of the lock was used. Only emits if temporary unlocking is allowed.
* `LocalUnlock` - The unlock button on the face of the lock was used. Only emits if temporary unlocking is allowed.
* `ReleaseContract` - The contract was released and confirmed in hardware.
* `EmergencyUnlock` - The lock was unlocked physically by the subject via the on-device Emergency Unlock screen.
* `StartedUpdate` - Telemetry emitted on lock boot announcing the lock's public key, lock state, and whether it started with a local contract.
* `PeriodicUpdate` - Heartbeat telemetry published periodically while online. Bots with `receive_events` permissions will receive these periodic updates too.

## Contract structure

Contracts are pretty straight forward:

```
table Contract {
  serial_number: ushort;
  public_key: [ubyte];
  bots: [Bot];
  terms: string;
  // If true, subject can freely cycle the lock.
  is_temporary_unlock_allowed: bool;
  // If true, subject can emergency unlock the lock. Defaults to true.
  is_emergency_unlock_allowed: bool = true;
}
```

Notes:

* The serial number is randomly generated by the author.
* The `public_key` is the author's public key. All future commands will need to be signed
by this key.
* The `bots` vector identifies which bots are allowed to participate in this contract.
* The `terms` string is just an unstructured string. It can include whatever you want. None of the core
bits of the system will try and interpret this field.
* `is_temporary_unlock_allowed` controls whether the physical lock/unlock button on the lock face works.
* `is_emergency_unlock_allowed` (defaults to `true`) controls whether the on-device Emergency Unlock screen is available.

### Bots & Permissions

Only bots listed on the contract body will be able to receive events. Events are published by the lock to isolated per-lock MQTT topics (`tartarus/{lock_id}/events`), and the coordinator filters and fans them out to authorized bots over duplex WebSockets. Bots will also receive periodic updates if they have event permissions.

Additionally, bots have four possible permission settings:

* `receive_events` - Whether or not `SignedEvent` messages will be generated at their appropriate point in the lifecycle.
If this is false no events will be sent to the bot.
* `can_unlock` - If this is true the bot can sign an `UnlockCommand` and it will be accepted.
* `can_lock` - Same as unlock but for lock.
* `can_release` - If this is true the bot can end the contract.

### Bot Transport & Key Derivation

The legacy MQTT FlatBuffers bot inbox (`bots.fbs`) has been retired. Modern bots are owned by an `AuthorSession` and interact with the coordinator via:

* **Deterministic Key Derivation**: Bot `secp256r1` keypairs can be derived deterministically via HKDF from the owning Author's private key, an internal bot name, and a `keyVersion` (managed in the `/bots` UI).
* **Authenticated REST & WebSockets**: Bots submit signed commands over authenticated REST (`/bots/...`) and receive filtered `SignedEvent` streams in real time over duplex WebSockets (`/ws/events`). See `overlord_exe` and `tartarus-bot-example` for reference implementations.

## Lock Startup

What happens on lock startup:

* Send `StartedUpdate` from the unified `event.fbs` flatbuffers. If the coordinator has never heard from this lock before, it creates a new `LockSession`. The update includes the lock's public key material, whether it started with an active contract, and the current lock state.
* Receive a `CoordinatorConfiguration` message potentially updating the lock's local configuration or toggling experimental firmware features.
* In the pre-1.2.0 world, we had scaffolding for OTA updates (`GetLatestFirmwareRequest`). The problem is that the Adafruit Reverse TFT Feather only has 4MB of flash, which became unworkable as firmware features grew. However, modern in-browser Web Serial flashing (`/updater`) obviated the need for OTA anyway, allowing the partition table to be standardized with a larger application partition and 24 KB NVS.
* Authenticate hardware security: The firmware uses a pluggable `SecurityBackend` architecture. For "official" hardware builds, an Infineon Optiga Trust M secure element module provides hardware-isolated keys and ECDSA signing; the coordinator cryptographically verifies the hardware certificate chain against Infineon Root CAs. The "official" build is an optional additional layer of security for those that want a slightly more oppressive experience—especially good for software people who know how things work! Homebrew builds run smoothly using the software security backend.

From there, it will publish a `PeriodicUpdate` event once a minute indefinitely (which is also forwarded to authorized bots).

### Hardware Provisioning & Attestation

For official builds equipped with an Infineon Optiga Trust M secure element (`src/provisioning.rs`):

* **Platform Binding Secret (`EFUSE_BLK3`)**: During factory provisioning, a 256-bit seed is burned into `EFUSE_BLK3` (write-protected) and combined with HKDF (`tartarus-optiga-pbs-v1`) to bind the Optiga chip to the host ESP32-S3.
* **Serial Provisioning Commands**: Sending `PROVISION` (hardware Optiga + eFuses) or `SOFT_PROVISION` (software backend) over USB serial when no contract is active generates/exports the device's public key and certificate payload (`PROVISION_RESULT:{...}`).
* **Coordinator Verification**: Administrators ingest the provisioning payload via `/admin/hardware`, allowing the coordinator to validate the Infineon certificate chain against root CAs and mark the `LockSession` as hardware-verified.

## Bluetooth Low Energy (BLE) & Offline Storage

To support scenarios where Wi-Fi is intermittent or unavailable, the firmware incorporates local transport and buffering:

* **On-Board BLE GATT Server**: An embedded BLE GATT server (`ble_server.rs`) allows direct interaction with the lock without requiring Wi-Fi. It authenticates clients using a 6-digit PIN displayed on the lock's `BLE PIN` screen (with a 5-attempt lockout resettable via `D1`) for issuing commands, configuring Wi-Fi, reading device status, and draining audit events.
* **Offline Ring Buffer**: A 20-event circular buffer (`event_buffer.rs`) captures state changes (such as local lock/unlock toggles) when the lock is offline. Once Wi-Fi reconnects or a client connects over BLE, buffered events are automatically flushed and synced.

## Terms

### `LockSession`

A `LockSession` uniquely defines a particular Tartarus lock.

### `LockUserSession`

This is an owner of a Tartarus lock logged into the website.
You can simultaneously have a `LockUserSession` and an `AuthorSession`.

### `AuthorSession`

This is someone logged into the website with a keypair image they generated and is authoring/managing contracts.
You can simultaneously have a `LockUserSession` and an `AuthorSession`.

### `AdminSession`

Admin users that have special, magical powers.

### `SafetyKey`

A set of reserved online keys that can sign `Abort` messages.
Only `AdminSession` users can use `SafetyKey`s. 