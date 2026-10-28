# Lock User Guide - So You Want Someone To Hold Your Keys

## Step 1: Complete Build + Setup

Make sure your lock is assembled, powered, and connected to the network (or accessible via Web Serial / BLE).

## Step 2: Login

Scan the QR code displayed on the screen of the lock (or use the credentials generated during initial setup).
This opens an authenticated `LockUserSession` on [Tartarus](https://tartarus.subjugated.club), bringing you to the **My Lock** dashboard.

## Step 3: Share Links & Tokens

In the "My Lock" dashboard, you have two invitation links:

* **Share Link (`s-...`)**: When your author writes a contract with this link, you receive an invitation in your dashboard with full details. You have the choice to accept or reject it.
* **Total Control Link (`tc-...`)**: If you give someone your Total Control link, any contract they submit will bypass manual confirmation and be sent directly to the hardware for automatic acceptance.

## Step 4: Review and Accept Contracts

When a contract proposal arrives under a Share token, review:
* The contract terms and duration.
* Whether temporary unlocking via the physical lock buttons is permitted.
* Which automated bots are attached, and what permissions they have (`can_lock`, `can_unlock`, `can_release`, `receive_events`).

If you agree, click **Accept Contract**. The lock physically engages and seals.

## Step 5: Operating the Lock & Offline Buffering

* **Temporary Unlocks**: If allowed by your contract, you can navigate to the lock screen on the physical device (press D2 to cycle screens) and press D1 (the middle button) to toggle between locked and unlocked.
* **Offline Event Ring Buffer**: If your Wi-Fi is temporarily offline, you can still cycle temporary unlocks. The firmware records these actions in an on-board 20-event ring buffer, which automatically drains and syncs to the coordinator and author as soon as Wi-Fi reconnects or via BLE.
* **Emergency Safeguards**: If an unexpected emergency occurs or connectivity is permanently lost, contracts can be released using the **Emergency Unlock** protocol or aborted by an administrator using an offline `SafetyKey`.