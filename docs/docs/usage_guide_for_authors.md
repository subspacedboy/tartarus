# Author Guide - So You Want To Hold Some Keys

## Step 1: Get A Keypair

Generate a keypair using the [key generator](https://tartarus.subjugated.club/keypair).
This keypair is how you log in and what the software will use to sign all your contracts
and commands.

**Do not share it**. Especially with your sub. It's functionally a master private key. Anyone with this image or key file can release
or unlock a contract.

**Protect it**. Treat it like a physical key your sub handed you. If you lose it you'll need an administrator with a designated `SafetyKey`
to abort the contract.

## Step 2: Login

Go to [Tartarus Login](https://tartarus.subjugated.club/login) and select **Author Login**.
Upload or drag and drop your saved keypair image/file. The browser verifies the key locally and establishes an authenticated `AuthorSession`.

## Step 3: Get A Token From Your Sub

Every sub has two links: the "share" link and the "total control" link.
The "share" token starts with `s-` and the total control token starts with `tc-`.

* An `s-` token lets you write a contract and send it to your sub for review and accept/reject.
* A `tc-` token lets you write a contract and issue it directly to hardware. If the lock is online, it will auto-accept it.

The website will clearly identify which token type you are viewing. When you load the link, it becomes a `KnownToken` saved to your account.
In the modernized Spartan UI dashboard, known tokens are displayed as cards under "Lock sessions". You can label them to keep track of who they belong to, or delete them when a session is finished.

## Step 4: Write A Contract

Using the Contract Creator:
* Set contract terms and optional author notes.
* Configure whether temporary unlocks via physical lock buttons are permitted.
* Configure whether on-device Emergency Unlock is permitted (enabled by default; disabling it displays a high-severity warning to the lock owner).
* Attach automated bots with granular permissions (`can_lock`, `can_unlock`, `can_release`, and `receive_events`).

Once submitted, the contract is signed with your author key and dispatched to the lock.

## Step 5: Managing the Lock & Serial Fallback

From the Author Contract View, you can issue commands (`Lock`, `Unlock`, and `Release`).

> [!TIP]
> **Serial Command Fallback**: If the lock is offline or experiencing network difficulties, command cards feature a **Send via Serial** button that dispatches the signed command directly over USB via Web Serial.

### Troubleshooting: Command Counter Sync

Every command carries a monotonic counter that must be greater than or equal to the lock's stored counter. If the lock and coordinator desynchronize (for example, after an NVS restore or dropped command) and commands fail with counter-too-low errors, open the **Troubleshooting: Command Counter Sync** panel in the Author Contract View and click **Bump command counter (+10)** to advance the coordinator's counter past the lock's state.

## Step 6: Release

When the agreed terms are fulfilled, click **Release Contract**. The author key signs a `ReleaseCommand` and, upon confirmation from the lock, the contract is ended and the hardware unlocks.

## Managing Bots (`/bots`)

Authors can register and manage their own automated bots at [My Bots](https://tartarus.subjugated.club/bots):

* **Deterministic Key Derivation**: Creating a bot derives a dedicated `secp256r1` keypair deterministically from your Author private key, an internal derivation name, and a key version (starting at `1`). You never need to manage separate master key files for your bots.
* **Lifecycle Controls**: Pause or resume bots (`ACTIVE` / `PAUSED`), rotate bot keys (incrementing `keyVersion`), and export the derived private/public PEMs for your bot runtime.
* **Attaching to Contracts**: Registered bots appear in the Contract Creator where you can grant them any combination of `receive_events`, `can_lock`, `can_unlock`, and `can_release`.