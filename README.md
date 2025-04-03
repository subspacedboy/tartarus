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

Angular 19 and the source for `https://tartarus.subjugated.club`. Found
in `tartarus`.

### Example bot

The example bot is a Python example for how to make a simple TimerBot.
Check out `tartarus-bot-example`.

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