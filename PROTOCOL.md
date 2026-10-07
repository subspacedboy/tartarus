# Tartarus Protocol: Message Signing and Verification Specification

This document specifies the cryptographic signing, serialization, and verification protocol used across the Tartarus system components:
- **Lock Firmware** (Rust / ESP32, e.g. [src/verifier.rs](file:///Users/chris/Development/qrdisplay/src/verifier.rs), [src/acknowledger.rs](file:///Users/chris/Development/qrdisplay/src/acknowledger.rs), [src/fb_helper.rs](file:///Users/chris/Development/qrdisplay/src/fb_helper.rs))
- **Coordinator Backend** (Kotlin / JVM, e.g. [fb_helpers.kt](file:///Users/chris/Development/qrdisplay/tartarus-coordinator/src/main/kotlin/club/subjugated/tartarus_coordinator/util/fb_helpers.kt))
- **Clients & Frontends** (TypeScript / Angular, e.g. [crypto.service.ts](file:///Users/chris/Development/qrdisplay/tartarus/src/app/crypto.service.ts))
- **Third-Party Bots and Integrations**

It provides all necessary specifications and algorithms so an independent developer can construct or verify compliant messages in any programming language.

---

## 1. Cryptographic Primitives

All cryptographic operations in the protocol rely on standard NIST curves and SHA-2:

| Parameter | Specification | Details |
|---|---|---|
| **Curve** | `NIST P-256` (`secp256r1` / `prime256v1`) | 256-bit elliptic curve |
| **Signature Scheme** | `ECDSA` | Deterministic or randomized signing |
| **Digest Algorithm** | `SHA-256` | 32-byte message digest |
| **Public Key Format** | `SEC1 Compressed` | **33 bytes**: `0x02` or `0x03` prefix byte + 32-byte X coordinate |
| **Wire Signature Format** | `IEEE P1363 (Raw)` | **64 bytes**: 32-byte `r` + 32-byte `s` (big-endian, concatenated) |

### Signature Encoding Differences
- **Firmware & Wire Format:** Always uses 64-byte raw IEEE P1363 signatures in FlatBuffers (`signature: [ubyte]`).
- **WebCrypto API (Browser):** `crypto.subtle.sign()` natively produces 64-byte raw IEEE P1363 signatures for ECDSA.
- **Java / BouncyCastle (Coordinator):** Standard JCA (`Signature.getInstance("SHA256withECDSA")`) generates and expects ASN.1/DER encoded signatures (variable length ~70–72 bytes: `0x30 [len] 0x02 [r_len] [r] 0x02 [s_len] [s]`).
- **Implementor Requirement:** Any JVM implementation must convert incoming 64-byte raw signatures to DER before calling JCA `verify()`, and convert DER to 64-byte raw signatures before emitting wire messages.

---

## 2. Schema Architecture

The core data structures are defined in [flatbuffers/contract.fbs](file:///Users/chris/Development/qrdisplay/flatbuffers/contract.fbs) and [flatbuffers/event.fbs](file:///Users/chris/Development/qrdisplay/flatbuffers/event.fbs).

### Top-Level Envelope: `SignedMessage`

```flatbuffers
namespace club.subjugated.fb.message;

table SignedMessage {
  signature: [ubyte];
  payload: MessagePayload;
  authority_identifier: string; // Optional: used for bot authorities or safety keys
}

root_type SignedMessage;
```

`MessagePayload` is a FlatBuffers **union**:
```flatbuffers
union MessagePayload {
  Contract,
  LockCommand,
  UnlockCommand,
  ReleaseCommand,
  StartedUpdate,
  PeriodicUpdate,
  Acknowledgement,
  Error,
  AbortCommand,
  ResetCommand
}
```

Because `payload` is defined as a union of tables, the payload table and the enclosing `SignedMessage` table **reside within the same serialized FlatBuffer binary**.

---

## 3. The FlatBuffers Binary Signing Scheme

### The Problem
FlatBuffers builds buffers **backwards** (from the end of the buffer down to offset 0), and child tables/vectors must be constructed *before* their parent table. 

To create `SignedMessage`, both `payload` (table offset) and `signature` (vector offset) must be passed to `startSignedMessage()`. But to sign the payload, the payload must already exist. 

### The Solution: Tail-Slice Hashing
Because FlatBuffers builds memory in reverse order:
1. When the payload table (e.g. `UnlockCommand`) and all its child strings/vectors are serialized into a fresh builder, they occupy the **tail** portion of the buffer.
2. The payload table begins at an offset `table_start`. The first two bytes at `table_start` store a signed 16-bit little-endian relative offset to its `vtable`.
3. The vtable begins at `vtable_start = table_start - vtable_offset`.
4. **All data belonging to the payload** (vtable, fields, strings, vectors) sits in the slice:
   $$\text{Slice} = \text{buffer}[\text{vtable\_start} \,..\, \text{buffer.len}()]$$
5. The SHA-256 hash of this slice is signed with ECDSA.
6. When the signature vector and outer `SignedMessage` table are serialized into the same builder, FlatBuffers allocates them at lower addresses (towards byte 0).
7. On the receiver, `&buffer[vtable_start .. buffer.len()]` remains completely unchanged, allowing zero-copy in-place hash and verification.

```
Buffer Memory Layout (Addresses 0 to End):
[ 0x00 ... ]  <-- Front: Root offset & padding
[ SignedMessage Table & vtable ]
[ Signature byte vector ]
============================== <-- vtable_start of Payload
[ Payload vtable ]
[ Payload Table fields ]
[ Strings / child vectors ]
============================== <-- buffer.len()
(Everything between vtable_start and buffer.len() is the signed slice)
```

---

## 4. Message Creation (Sender Specification)

There are two valid ways to construct and sign messages:

### Method A: Single-Builder Append (TypeScript / Kotlin)
Used when the language builder allows continuing allocations after calculating the payload:

1. **Allocate Builder:** Create a new `FlatBufferBuilder(1024)`.
2. **Build Payload:** Serialize all child fields/strings, then start and finish the payload table (e.g., `UnlockCommand.startUnlockCommand(builder)` ... `UnlockCommand.endUnlockCommand(builder)`).
3. **Finish Payload Root:** Call `builder.finish(payloadOffset)`.
4. **Determine VTable Start:**
   - Read the 16-bit table offset at the buffer root:
     $$\text{offset\_to\_table} = \text{data}[0] \mid (\text{data}[1] \ll 8)$$
   - Read the 16-bit vtable offset:
     $$\text{offset\_to\_vtable} = \text{data}[\text{offset\_to\_table}] \mid (\text{data}[\text{offset\_to\_table} + 1] \ll 8)$$
   - Compute:
     $$\text{vtable\_start} = \text{offset\_to\_table} - \text{offset\_to\_vtable}$$
5. **Hash & Sign:**
   - Slice: $\text{target\_bytes} = \text{data}[\text{vtable\_start} \,..\, \text{data.len}()]$
   - Compute $H = \text{SHA-256}(\text{target\_bytes})$.
   - Sign $H$ with the author/bot private key using ECDSA P-256 to get 64-byte raw signature.
6. **Append Signature and Outer Envelope:**
   - Without clearing the builder, create the signature byte vector (`createSignatureVector`).
   - Start `SignedMessage`:
     - Set `payload` = `payloadOffset`.
     - Set `payload_type` = Union type tag (e.g., `MessagePayload.UnlockCommand`).
     - Set `signature` = `signatureOffset`.
     - (Optional) Set `authority_identifier` if signed by a bot or safety key.
   - End `SignedMessage` and call `builder.finish(signedMessageOffset)`.
7. **Transmit:** The final `builder.asUint8Array()` / `builder.sizedByteArray()` contains the complete signed message.

### Method B: Double-Builder Rebuild (Rust Firmware)
Used in Rust because `FlatBufferBuilder` transfers ownership or locks the buffer on `finish()`:

1. **Builder 1:** Build the payload table, call `builder1.finish(payload, None)`, and extract `buffer = builder1.finished_data()`.
2. **Compute Signature:**
   ```rust
   let table_offset = buffer[0] as usize;
   let vtable_offset = buffer[table_offset] as usize;
   let actual_start = table_offset - vtable_offset;
   let hash = Sha256::digest(&buffer[actual_start..]);
   let signature = signing_key.sign(&hash.as_slice());
   ```
3. **Builder 2:** Allocate a fresh `FlatBufferBuilder`. Re-serialize the exact same payload fields in the exact same order.
4. Add the signature vector to `builder2`.
5. Create and finish `SignedMessage` containing both the payload and signature.
6. Return `builder2.finished_data()`.

---

## 5. Message Verification (Receiver Specification)

When a component (such as the ESP32 firmware in [src/verifier.rs](file:///Users/chris/Development/qrdisplay/src/verifier.rs)) receives raw FlatBuffer bytes:

### Step 1: Parse the Envelope
Verify that the bytes parse as a valid FlatBuffer root of type `SignedMessage`:
```rust
let signed_msg = root_as_signed_message(incoming_data.as_slice())?;
```

### Step 2: Key Resolution
Determine which public key must be used to verify the signature:
1. **Explicit Authority:** If `signed_msg.authority_identifier()` is present:
   - Look up the public key matching that identifier (e.g., a registered Bot public key or the device's Safety Key).
   - If not found $\rightarrow$ Reject with error.
2. **Contract Establishment:** If `signed_msg.payload_type() == MessagePayload::Contract`:
   - If the lock is already bound to an active contract $\rightarrow$ Reject (`"Lock already under contract"`).
   - Extract `Contract.public_key()` (33-byte compressed SEC1) from the message payload itself and decode into a Verifying Key.
3. **Active Contract Authority:** For commands (`LockCommand`, `UnlockCommand`, `ReleaseCommand`, etc.):
   - Use the public key stored from the currently active contract.
   - If no active contract exists $\rightarrow$ Reject.

### Step 3: Extract & Hash the Payload Slice
Find the payload table within the buffer:
1. Get the payload table's byte offset `message_table_start` (in Rust: `table._tab.loc()`; in Kotlin: reflection `bb_pos`).
2. Read the 16-bit little-endian relative offset to its vtable:
   $$\text{vtable\_offset} = \text{u16::from\_le\_bytes}(\text{incoming\_data}[\text{table\_start} \,..\, \text{table\_start} + 2])$$
3. Compute the start index:
   $$\text{vtable\_start} = \text{message\_table\_start} - \text{vtable\_offset}$$
4. Slice the payload buffer:
   $$\text{message\_buffer} = \text{incoming\_data}[\text{vtable\_start} \,..\, \text{incoming\_data.len}()]$$
5. Compute the digest:
   $$H = \text{SHA-256}(\text{message\_buffer})$$

### Step 4: Verify the Signature
1. Parse the 64-byte raw signature from `signed_msg.signature()`.
2. Verify:
   $$\text{ECDSA\_Verify}(\text{VerifyingKey}, H, \text{Signature}) == \text{Valid}$$
   - If invalid $\rightarrow$ Reject (`"Invalid signature"`).

### Step 5: Validate Protocol Replay & State
For commands (`LockCommand`, `UnlockCommand`, `ReleaseCommand`, `AbortCommand`):
1. **Contract Binding:** Verify `command.contract_serial_number() == state.current_contract_serial_number`.
2. **Replay Protection (Monotonic Counter):**
   - Check `command.counter() > state.minimum_counter`.
   - If valid, update `state.minimum_counter = command.counter()`.
   - If `counter <= minimum_counter` $\rightarrow$ Reject (`"Counter on message was too low"`).

---

## 6. Implementation Reference

### Rust (Receiver / Verifier)
From [src/verifier.rs](file:///Users/chris/Development/qrdisplay/src/verifier.rs#L86-L108):
```rust
pub trait HashTableBytes<T> {
    fn calculate_hash(&self, incoming_data: &[u8]) -> Output<Sha256>;
}

impl<T: HasTable> HashTableBytes<T> for T {
    fn calculate_hash(&self, incoming_data: &[u8]) -> Output<Sha256> {
        let message_table_start = self.loc();
        let vtable_offset = u16::from_le_bytes(
            incoming_data[message_table_start..message_table_start + 2]
                .try_into()
                .expect("wrong size"),
        ) as usize;
        let contract_end = incoming_data.len();
        let vtable_start = message_table_start - vtable_offset;
        let message_buffer = &incoming_data[vtable_start..contract_end];

        Sha256::digest(message_buffer)
    }
}
```

### TypeScript (Web / Sender)
From [tartarus/src/app/crypto.service.ts](file:///Users/chris/Development/qrdisplay/tartarus/src/app/crypto.service.ts#L59-L78):
```typescript
async hashAndSignFb(privateKey: CryptoKey, data: Uint8Array): Promise<ArrayBuffer> {
  const offsetToTable = data[0] | (data[1] << 8);
  const offsetToVTable = data[offsetToTable] | (data[offsetToTable + 1] << 8);
  const vTableStart = offsetToTable - offsetToVTable;
  const vtableAndContract = data.slice(vTableStart);

  return await this.hashAndSignData(privateKey, vtableAndContract);
}

async hashAndSignData(privateKey: CryptoKey, data: Uint8Array): Promise<ArrayBuffer> {
  const hash = await crypto.subtle.digest("SHA-256", data);
  return await crypto.subtle.sign(
    { name: "ECDSA", hash: { name: "SHA-256" } },
    privateKey,
    hash
  );
}
```

### Kotlin (Coordinator / Verifier)
From [tartarus-coordinator/src/main/kotlin/.../fb_helpers.kt](file:///Users/chris/Development/qrdisplay/tartarus-coordinator/src/main/kotlin/club/subjugated/tartarus_coordinator/util/fb_helpers.kt#L204-L225):
```kotlin
fun <T : Table> getBytesOfTableWithVTable(table: T): ByteArray {
    val buffer = table.byteBuffer.duplicate()
    val vtableStart = getVTableStart(table)
    buffer.position(vtableStart)
    val remaining = buffer.remaining()
    return ByteArray(remaining).apply { buffer.get(this) }
}

fun <T : Table> getVTableStart(table: T): Int {
    val byteBuffer = table.byteBuffer
    val tableStart = getBbPos(table) // Reflection on private bb_pos
    val vtableOffset = byteBuffer.getShort(tableStart).toInt()
    return tableStart - vtableOffset
}
```

---

## 7. Guidelines and Pitfalls for Implementors

1. **No Preceding Allocations in Builder:** When constructing the payload, ensure no other strings, vectors, or tables are serialized into the builder before starting the payload. Any data preceding the payload will reside at the buffer's tail and corrupt the signature slice.
2. **Buffer Truncation:** Transport protocols (MQTT, HTTP, QR codes) must transmit the exact byte length returned by `builder.finished_data()` / `builder.sizedByteArray()`. Appending trailing null bytes or padding will alter `incoming_data.len()` and break the signature check.
3. **VTable Deduplication:** Do not build other tables in the same builder prior to the payload if they share the payload's shape; otherwise, the builder might reuse an earlier vtable outside the payload's slice.
4. **Signature Encoding on JVM / Android:** If using Java/Kotlin, always ensure signatures sent to the lock are converted from DER to 64-byte raw format via `derToRawSignature()`, and received signatures are converted to DER via `rawToDerSignature()`.
5. **Compressed Public Keys:** Public keys stored in `Contract.public_key` must be 33 bytes in SEC1 compressed form (`0x02` for even Y, `0x03` for odd Y). If your crypto library exports uncompressed 65-byte keys (`0x04 || X || Y`), compress them before storing in the FlatBuffer.
