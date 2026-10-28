# Tartarus Monorepo

## Overview

### Firmware

The firmware for the Tartarus smart lock is written in Rust
and that's what's here in the root repo.

### Coordinator

This is the API and backend that powers the system. Combination
of REST API and MQTT broker/subscriber. This is SpringBoot and
Kotlin found in `tartarus-coordinator`.

### Web Frontend

Angular 22 application built with Spartan UI and Tailwind primitives at `https://tartarus.subjugated.club`. Found in `tartarus`. Includes in-browser Web Serial firmware flashing, serial command dispatch, BLE management, and contract administration.

### Example bot

The example bot is a Python example for how to make a simple TimerBot.
Check out `tartarus-bot-example`.

### Overlord_exe

The official bot framework and runtime written in Kotlin, communicating over duplex WebSockets with author-owned sessions. Includes Bluesky social integration. Found in `overlord_exe`.
See [@subjugatedclub.bsky.social](https://bsky.app/profile/subjugatedclub.bsky.social).


### Flatbuffers

All the communication between the various parts are implemented as
Flatbuffers. The schema for the flatbuffers is in `flatbuffers`. You
can rebuild them with `make_flatbuffers.sh`.

### Archive

I left an old prototype of the coordinator written in Python in case
you want more examples of making + signing messages.

## Rust Firmware

### Building

```
cargo build --release
```