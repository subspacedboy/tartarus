# Release Notes

## V 1.2.0

Release `1.2.0` is a comprehensive release spanning the entire Tartarus ecosystem since commit `d82e1d6d`. This release introduces hardware-backed security via Infineon Optiga Trust M, a streamlined custom embedded-graphics display engine, an overhaul of the event and protocol architecture, a modernized WebSocket-based bot platform, Spartan UI frontend redesign, and end-to-end hardware attestation and provisioning.

> [!WARNING]
> **Breaking Change — Firmware Upgrade Required**: Because this release fundamentally changes the underlying FlatBuffers protocol (migrating lifecycle and status telemetry such as `StartedUpdate` and `PeriodicUpdate` directly into the unified `Event` schema) and overhauls how bots communicate and interact (transitioning from REST polling to a WebSocket-based author ownership architecture), **this is a breaking change across the entire ecosystem**. Operating on the Tartarus network going forward requires upgrading physical locks to firmware `1.2.0` or higher; legacy firmware will not be able to process bot commands or dispatch telemetry to modern coordinators.

### Hardware Security & Secure Element
* **SecurityBackend Architecture**: Introduced a pluggable `SecurityBackend` abstraction trait in the firmware, decoupling core contract and cryptographic routines from underlying storage and crypto implementations.
* **Infineon Optiga Trust M Integration**: Integrated native hardware security module support for the Infineon Optiga Trust M secure element, providing hardware-isolated private keys, ECDSA signing, and tamper-resistant cryptographic storage.
* **Software Fallback Backend**: Retained a software-based `SecurityBackend` implementation for local development and hardware units lacking dedicated secure cryptoprocessors.
* **Hardware Factory Provisioning**: Added a dedicated firmware factory provisioning command interface and soft provisioning modes for staging device identities, root certificates, and cryptographic keys.
* **Coordinator Hardware Verification**: Implemented certificate chain validation in the coordinator to cryptographically verify hardware authenticity and attestation reports against official Infineon Root CAs.
* **Admin Provisioning Console**: Added a web-based provisioning ingestion modal allowing administrators to inspect and verify hardware units over Web Serial.

### Firmware Architecture & Display Engine
* **Embedded-Graphics Migration**: Removed the Slint UI framework dependency in favor of a lean, custom display stack built on `embedded-graphics` (`src/ui/mod.rs`, `components.rs`, `screens.rs`, `theme.rs`, `font.rs`). This dramatically reduces flash footprint, eliminates runtime overhead, and prevents heap thrashing.
* **Typography & UI Components**: Added raster font rendering with bundled DejaVuSans typography, standardized color themes, and dedicated screen layouts for status, lock state, Wi-Fi info, and QR presentation.
* **Display Optimizations**: Cached QR code rendering to eliminate redraw latency, and added animated visual feedback indicators on unlock execution.
* **Main Thread Stack Expansion**: Increased ESP32 main task stack size to 32 KB to safely handle deep cryptographic verification chains and large protocol payloads.
* **Firmware Version Reporting**: Updated the firmware to self-report its semantic version during initialization and periodic updates, ingested directly into coordinator lock sessions.

### Protocol & Unified Event Model
* **Unified Event Telemetry**: Migrated `StartedUpdate` and `PeriodicUpdate` from message payloads into first-class FlatBuffers `Event` definitions (`dc50673`), creating a single unified pipeline for device status, lock state transitions, and audit logs.
* **Emergency Unlock**: Added end-to-end protocol support for emergency unlock operations across FlatBuffers schemas, firmware state machines, and coordinator event services.
* **Safety Key Signature Validation**: Cryptographically enforced that `AbortCommand` and `ResetCommand` payloads can only be authorized by designated `SafetyKeys`.
* **Bot Permission Enforcement**: Implemented strict capability authorization checks (`can_lock`, `can_unlock`, `can_release`) for incoming bot commands and restricted outbound event dispatching exclusively to authorized bots.
* **Isolated Per-Lock Topics**: Standardized MQTT topic hierarchies to per-lock paths (`tartarus/{lock_id}/...`) for commands, updates, and event streams, preventing cross-lock topic interference.
* **Multi-Chunk Message Reassembly**: Replaced legacy message assemblers with a robust, per-topic `MessageAssembler` capable of safely reassembling interleaved multi-part payloads.

### Overlord & Bot Ecosystem
* **WebSocket Bot Architecture**: Transitioned the Overlord bot platform from REST polling to a high-throughput, duplex WebSocket architecture with explicit author ownership.
* **Simplified Bot Core**: Streamlined `BotComponent` and bot implementations (`SimpleProxyBot`, `SuperBot`, `AnnouncerBot`), making the codebase significantly easier to extend, test, and maintain.
* **Conversational Intent Engine**: Refactored the natural conversation intent parsing pipeline into modular, maintainable intent handlers (`ProxyLockIntent`, `ProxyUnlockIntent`, `ProxyReleaseIntent`, `ProxyContractIntent`).
* **Dependency & Protocol Updates**: Updated Bluesky social integration dependencies and modernized bot integration test suites.

### Coordinator Backend
* **Hardware Attestation Services**: Added services to validate device certificates, challenge-response nonces, and hardware identity tokens.
* **Lock Session Enhancements**: Added tracking of active firmware versions across lock sessions and direct navigation links from contract management views to corresponding lock sessions.
* **Admin Event Subscriptions**: Introduced dedicated WebSocket admin sessions with token authentication and live administrative event broadcasting.
* **Serial Acknowledgments**: Added support for processing manual command acknowledgments received out-of-band via USB serial.
* **Persistence & Security Hardening**: Corrected JPA `@OneToMany` entity mappings, enforced MariaDB dialect compatibility in test runners, safeguarded JWT filter chains against duplicate executions, and expanded nonce cache sizes.

### Web Frontend & Modernization
* **Spartan UI Migration**: Modernized the Angular application by transitioning components to Spartan UI (`spartan.ng`) and Tailwind CSS primitives (`hlm` dialogs, sheets, cards, tables, badges, buttons, tooltips, sonner toasts, and spinners).
* **View Redesigns**: Completely overhauled key views including "My Lock", Contract Creator, Lock User Contract Details, Author Contract View, and Lock Sessions.
* **In-Browser Web Serial Flashing**: Built an integrated Web Serial firmware updater (`updater.component.ts`) capable of flashing devices directly from the browser with partition awareness and baud-rate selection.
* **Web Serial Command Terminal**: Added direct "Send via serial" capabilities onto command cards with STDIN Base64 encoding.
* **BLE Device Management**: Added interactive BLE controls for querying device status, inspecting the on-device event queue, and draining buffered offline events.
* **Angular Upgrades**: Upgraded frontend core dependencies through Angular 20, 21, and 22, along with Angular CDK 21.

### Bluetooth Low Energy (BLE) & Offline Storage
* **On-Board BLE GATT Server**: Implemented an embedded BLE GATT server (`ble_server.rs`) enabling direct local interaction without active Wi-Fi.
* **GATT Challenge-Response Auth**: Secured BLE management characteristics with challenge-response cryptographic authentication.
* **Offline Ring Buffer**: Implemented an in-memory 20-event ring buffer (`event_buffer.rs`) that preserves audit logs when Wi-Fi is disconnected and automatically flushes on reconnection or BLE drain.

### System, Security & Flashing Tooling
* **Secure Boot v2**: Integrated Secure Boot v2 configuration with ECDSA image signatures for bootloader and application verification.
* **Partition Layout Optimization**: Standardized non-volatile storage (NVS) at 24 KB and retired legacy OTA partition schemes to maximize app flash space.
* **Framework Upgrade**: Upgraded underlying firmware framework to ESP-IDF 5.5.4.
* **Backup & Diagnostic Utilities**: Added `backup_nvs.sh` for preserving cryptographic identity across flashes, automated manifest generation (`firmware.json`), and comprehensive protocol documentation (`PROTOCOL.md`).

### Reliability & Bug Fixes
* **Contract Memory Corruption**: Fixed an issue where loading a secondary contract corrupted in-memory command counters and triggered system panics.
* **Large Contract NVS Deserialization**: Fixed contract buffer limits in NVS reads (expanded from 1 KB to 4 KB) to ensure large contracts load reliably without boot failure.
* **LLVM Xtensa LTO Instruction Selection**: Resolved compiler crash (`XtensaISD::PCREL_WRAPPER`) under link-time optimization (`opt-level = "z"`) by extracting MQTT closures into non-inlined worker functions.
* **Heap Allocation Reductions**: Reworked struct layouts across `LockCtx` and network workers to eliminate unnecessary dynamic memory allocations.
* **Asynchronous Wi-Fi Reconnection**: Reworked `WifiManager` connection routines with exponential backoff and asynchronous DHCP handling.

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
