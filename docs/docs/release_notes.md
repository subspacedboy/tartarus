# Release Notes

## V 1.1.1

Release `1.1.1` represents a major architectural modernization and hardening of the Tartarus firmware since commit `d82e1d6d`. Highlights include a transition to Slint for the on-device user interface, on-board BLE GATT server integration, offline event buffering, Secure Boot support, and numerous protocol security enhancements.

### User Interface & Display
* **Migration to Slint UI**: Replaced the legacy `embedded-graphics` screen engine with a modern declarative [Slint](https://slint.dev/) UI framework (`ui/app.slint`), retiring the old static screen modules (`qr_screen`, `lock_state_screen`, `wifi_info_screen`, overlays).
* **Unlock Visual Feedback**: Added an animated flashing screen indicator on unlock execution.
* **QR Display Optimization**: Cached QR code rendering to eliminate display redraw lag and prevent screen tearing on large payload transfers.

### Bluetooth Low Energy (BLE)
* **On-Board BLE GATT Server**: Implemented a full BLE GATT server (`ble_server.rs`) enabling direct communication with the lock without requiring Wi-Fi.
* **GATT Authentication & Controls**: Added challenge-response authentication over BLE and GATT characteristics for querying device status, issuing commands, and inspecting/draining the event queue.

### Event System & Offline Storage
* **Dedicated Per-Lock Event Queues**: Refactored event publishing so locks stream state updates and notifications directly to their own dedicated topics for coordinator fanout.
* **Offline Event Ring Buffer**: Added a 20-event in-memory ring buffer (`event_buffer.rs`) that captures and preserves audit events when the lock is offline, flushing them automatically upon reconnection or via BLE.

### System, Partitions & Security
* **ESP-IDF 5.5.4 Upgrade**: Upgraded core framework and hal dependencies to ESP-IDF 5.5.4.
* **Secure Boot Support**: Integrated Secure Boot v2 configuration (`sdkconfig.defaults.secureboot`) with ECDSA signature verification for bootloader and firmware images.
* **Partition Table Rework**: Deprecated and removed legacy OTA partitions to maximize app binary space; standardized NVS storage at 24 KB.
* **Web Serial Flashing**: Added distribution builds (`build_firmware_dist.sh`) and manifest generation (`firmware.json`) enabling in-browser flashing over Web Serial.
* **NVS Backup Utility**: Added `backup_nvs.sh` to safely backup and restore lock credentials, identity, and contracts across firmware flashes.
* **USB Serial Command Input**: Added STDIN command reading over USB with Base64 armoring for local recovery and diagnostics.

### Protocol & Security Hardening
* **Emergency Unlock**: Added support for protocol-level Emergency Unlock commands across FlatBuffers schemas and internal contract handlers.
* **Safety Key Enforcement**: Enforced cryptographic validation ensuring only designated `SafetyKeys` can sign `AbortCommand` and `ResetCommand` payloads.
* **Bot Permission Checks**: Enforced strict authorization checks (`can_lock`, `can_unlock`, `can_release`) on incoming bot commands and restricted outbound event dispatching to authorized bots.
* **Key Parsing**: Hardened SEC1 elliptic curve public key parsing and challenge-response handling.

### Networking & MQTT
* **Aggressive Asynchronous Wi-Fi**: Overhauled `WifiManager` with an asynchronous connection strategy, better scan handling, DHCP delay accommodation, and aggressive reconnection logic.
* **Standardized MQTT Topics**: Standardized per-lock topic paths for commands, updates, and event streams.
* **Multi-Chunk Message Reassembly**: Replaced firmware-specific assemblers with a generalized `MessageAssembler` isolated per MQTT topic to prevent interleaved packet corruption.
* **Low-Latency Publisher**: Reduced MQTT publisher worker polling latency from 2000 ms to 50 ms for responsive command acknowledgments.

### Fixes & Performance
* **Contract Command Counter Corruption**: Fixed a bug where loading a secondary contract could corrupt the command counter in memory or cause a panic.
* **Large Contract Serialization**: Resolved issues where large contracts could be stored in NVS but failed to re-load or caused boot panics.
* **Boot State Sync**: Fixed boot order in `LockCtx` to ensure NVS contract state is restored prior to the initial `StartedUpdate` announcement.
* **LLVM Xtensa LTO Crash**: Fixed a release-mode compiler crash (`XtensaISD::PCREL_WRAPPER`) under `opt-level = "z"` by refactoring MQTT worker closures into non-inlined routines.
* **Heap Allocation Reduction**: Reworked memory layouts across `LockCtx`, verifiers, and network services to significantly reduce dynamic heap allocations.
