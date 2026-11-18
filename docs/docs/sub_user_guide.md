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
* The contract terms.
* Whether temporary unlocking via the physical lock buttons is permitted.
* Whether on-device Emergency Unlock is allowed (a high-severity warning is shown if disabled).
* Which automated bots are attached, and what permissions they have (`can_lock`, `can_unlock`, `can_release`, `receive_events`).

If you agree, click **Accept Contract**. The lock physically engages and seals.

## Step 5: Operating the Lock & Offline Buffering

* **Temporary Unlocks**: If allowed by your contract, you can navigate to the **LOCK STATUS** screen on the physical device (press `D2` to cycle screens) and press `D1` (the middle button) to toggle between locked and unlocked.
* **Offline Event Ring Buffer**: If your Wi-Fi is temporarily offline, you can still cycle temporary unlocks. The firmware records these actions in an on-board 20-event ring buffer, which automatically drains and syncs to the coordinator and author as soon as Wi-Fi reconnects or via BLE.
* **Emergency Safeguards**: If Emergency Unlock is allowed by the contract, you can cycle to the **EMERGENCY UNLOCK** screen (`D2`) and press `D1` 5 times to physically unlock the servo. This places the lock in an `emergency_unlocked` state (blocking local lock/unlock until the author sends an explicit `Lock` command) without ending the contract. If an author key is permanently lost, an administrator can end the contract using an offline `SafetyKey` `Abort` command.

## On-Device Screens & Physical Controls

The lock's display is organized into 6 screens. Press **`D2`** to cycle forward through the screens, and press **`D1`** (the middle button) to perform the active screen's action:

| Screen | Title | `D1` Action | Notes |
| --- | --- | --- | --- |
| 0 | `PAIRING / QR` | — | Displays the scannable pairing QR code (or "Configuration Needed" before initial setup). |
| 1 | `LOCK STATUS` | Toggle lock/unlock | Only works when no contract is active or `is_temporary_unlock_allowed` is `true`. Blocked while `emergency_unlocked` is active. |
| 2 | `EMERGENCY UNLOCK` | Press 5 times to unlock | Only shown when `is_emergency_unlock_allowed` is `true` (default). |
| 3 | `BLE PIN` | Regenerate 6-digit PIN | Displays the current BLE pairing PIN and clears any 5-attempt BLE lockout. |
| 4 | `NETWORK` | — | Shows Wi-Fi connection status and SSID. |
| 5 | `SYSTEM` | Start firmware update | Shows firmware version; pressing `D1` initiates an update if one is available. |

### Emergency Unlock Lifecycle & Recovery

1. **Contract Creation & Review**: Contracts have `is_emergency_unlock_allowed` enabled by default. If an author disables it, you will see a high-severity warning on the contract review screen before accepting.
2. **Triggering**: Cycle to `EMERGENCY UNLOCK` with `D2` and press `D1` **5 times**. The servo immediately opens, the device emits an `EmergencyUnlock` event, and `emergency_unlocked` is set to `true`.
3. **Recovery**: While `emergency_unlocked` is `true`, local `D1` lock/unlock toggles are disabled and both dashboards display an emergency-unlocked alert. The contract remains active until the author sends an explicit **`Lock`** command (which clears `emergency_unlocked` and re-engages the mechanism) or **`Release`**es the contract.