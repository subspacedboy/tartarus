# Overlord (Bluesky Bot)

**[Overlord-exe](https://bsky.app/profile/overlord-exe.subjugated.club)** is the Bluesky automation and social interaction layer for the Tartarus ecosystem. It bridges Bluesky DMs and social activity (likes, reposts, lists, and public posts) with the Tartarus Coordinator so lock wearers and keyholders can create, automate, and manage contracts without living in the web admin UI.

## How Overlord Fits Into the Ecosystem

The Tartarus ecosystem has four main layers:

1. **Physical Lock (ESP32-S3 Firmware)** — Enforces lock state, validates cryptographic `SignedMessage` commands, and emits `SignedEvent` telemetry.
2. **Tartarus Coordinator** — Central backend managing hardware attestation, contracts, command routing, and WebSocket event streams.
3. **Tartarus Web App** — Admin and device setup UI for flashing, BLE/Web Serial pairing, and generating shareable tokens (`s-...` / `tc-...`).
4. **Overlord (`overlord_exe`)** — Connects to the Coordinator as an Author over HTTPS + WebSockets (`/api/ws/events`) and connects to Bluesky for DMs, social graph queries, and public posts.

### Two-Bot Architecture

On the Coordinator, Overlord operates using **two** bot identities under one Author keypair:

* **`overlord` (Control Bot)** — The interactive control bot. It issues contracts, evaluates active rules every minute, listens for `AcceptContract`, `ReleaseContract`, `EmergencyUnlock`, and telemetry updates, and sends signed `Lock`, `Unlock`, and `Release` commands.
* **`announcer` (Public Announcer)** — A passive, read-only co-bot observer (`receive_events = true`, with `can_lock`, `can_unlock`, and `can_release` all disabled). When a contract is marked **public**, `announcer` is added as an observer so it can post acceptance and release announcements to Bluesky without having any control over the lock.

## Key Capabilities

### Composable State Machines

Rather than separate bots for each lock style, `overlord` attaches one or more composable rule machines to a single `ContractSession` and evaluates them according to a release policy (`ALL`, `ANY`, or `MANUAL`):

* **Timer** — Fixed or randomized duration (with random end times revealed only after the contract is accepted).
* **Keyholder Proxy Control** — Lets another Bluesky user act as keyholder and control the lock via DM (`lock`, `unlock`, `release`).
* **Bluesky Likes Goal** — Tracks likes on the wearer's Bluesky image posts until a target is reached.
* **Bluesky Crowd Time** — Posts a public notice on Bluesky where community likes and reposts during an open window extend the lock duration.

### Bluesky User Profiles & Token Learning

Because Overlord operates over Bluesky, it maintains a persistent profile keyed by each user's Bluesky DID:

* **Shareable Token Memory** — Automatically learns `s-...` and `tc-...` shareable tokens when users mention them in DMs or submit intake forms, and auto-injects them into future self-locks and proxy contracts (prompting for a choice if a user has multiple tokens on file).
* **Conversational Context** — Feeds the user's Bluesky handle, list memberships, active sessions, emergency-unlock status, and recent DM history into Amazon Bedrock so users can interact in plain English or via explicit `!commands`.

### Other Features

* **Emergency Relock** — Automatically notifies a wearer when an on-device `EmergencyUnlock` is detected and lets them relock via DM (`"Relock me"` or `!relock`).
* **Bluesky Community Lists** — Users can join or leave the `Doms`, `Subs`, and `Public Property` lists via DM.
* **Help** — Sending `"What can you do?"` or `!help` returns a link to Overlord's interactive `/help` page.
