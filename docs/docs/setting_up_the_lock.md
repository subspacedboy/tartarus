# Initial Setup

> [!IMPORTANT]
> **Before You Flash**: If you plan to install the official production firmware, please read the [Signed Firmware & Secure Boot v2](#signed-firmware--secure-boot-v2) section **before** flashing your hardware. Enabling Secure Boot v2 permanently burns key hashes into physical eFuses inside the microcontroller and **cannot be undone**.

## Getting Connected

The primary step to completing setup is configuring network connectivity on the lock.

There are several ways to configure and manage your lock:

### 1. Web Serial Console (Recommended for Modern Builds)

If your lock does not have a QR camera module, you can configure Wi-Fi and manage setup directly over USB using the **[Web Serial Console](https://tartarus.subjugated.club/console)**:

* Plug your lock into your computer via USB-C.
* Open the console in Chrome or Edge and click **Connect** (115200 baud).
* You can view real-time boot logs, monitor Wi-Fi association and DHCP status, or enter Base64-armored configuration commands directly into STDIN.

### 2. Wi-Fi QR Code (Legacy Builds with Camera)

If your lock is built with the Useful Sensors Tiny Code Reader module:

* Use the [Wi-Fi helper](https://tartarus.subjugated.club/wifi-helper) in the web app to generate a credentials QR code. (All processing occurs locally in your browser).
* Alternatively, generate a WPA3 Wi-Fi QR code using iOS/macOS Passwords app or Windows 11.
* Hold the code in front of the camera module until the lock reads it and connects.

### 3. Bluetooth Low Energy (BLE)

The lock runs an on-board BLE GATT server (`ble_server.rs`). Using a compatible BLE browser or client:

* Pair with the lock over BLE using challenge-response authentication.
* Query device health, inspect buffered offline events, and issue management commands locally without active Wi-Fi.

## Verifying it worked

Once the lock is online it will try and connect to the default message broker at `wss://tartarus-mqtt.subjugated.club:4447/mqtt`.
If it succeeds it will download a configuration blob containing everything it needs to create login credentials for you.

Give it a reset and a scannable QR code should be on the display after it boots.

## Signed Firmware & Secure Boot v2

Tartarus offers official production firmware releases cryptographically signed with **ESP32-S3 Secure Boot v2**. You can flash these signed builds directly in your browser using the **[Web Serial Updater](https://tartarus.subjugated.club/updater)**.

### What is Secure Boot v2?

Secure Boot v2 creates a cryptographic chain of trust anchored directly in the physical silicon of the ESP32-S3 microcontroller. On power-up, the read-only hardware ROM verifies the cryptographic signature of the bootloader before executing it; the bootloader then verifies the signature of the application firmware image before executing the lock operating system.

### What are the Benefits?

* **Absolute Tamper Resistance**: Nobody—including the lock owner or anyone with physical access to the device—can flash modified, patched, or backdoored firmware to force open the servo, bypass contract counters, or dump encryption keys.
* **Hardware Anti-Rollback**: Hardware eFuse counters enforce version monotonicity, preventing downgrades to older firmware versions that may have had bugs.
* **Locked Hardware Debugging**: Secure Boot permanently disables hardware JTAG debugging pins and enables Secure ROM Download Mode. This prevents attackers from dumping flash or SRAM contents over USB serial.
* **Coordinator Attestation**: Allows the lock to cryptographically prove its authenticity to the coordinator, granting verified status and hardware badges.
* **Psychological & Contract Integrity**: For software engineers and tech-savvy lock owners who know how microcontrollers work, knowing that the lock cannot be hacked or reflashed with custom scripts provides true psychological peace of mind and an uncompromising, binding lock experience.

### ⚠️ Permanent 1-Way Process (Cannot Be Undone)

> [!CAUTION]
> **Enabling Secure Boot v2 is 100% PERMANENT and IRREVERSIBLE.**
>
> When the ESP32-S3 boots a Secure Boot v2 bootloader for the first time, it **permanently burns** the public key digest into one-time programmable (OTP) physical **eFuses** inside the silicon chip.
>
> * **eFuses cannot be cleared, erased, or reset.** Once burned, there is no factory reset command, no JTAG unbricking method, and no recovery utility on earth that can reverse it.
> * From that moment on, the ESP32-S3 hardware ROM will **ONLY** boot bootloaders and firmware images cryptographically signed by the official private release key.
> * If you flash unsigned code, custom homebrew firmware, or other hobbyist platforms (such as CircuitPython or Arduino), the hardware ROM will reject the signature and **refuse to boot**, entering an immediate boot loop.
> * **You can still install official updates!** The bootloader is built in reflashable mode, meaning future official releases from subjugated.club (e.g., v1.2.0, v1.2.1) can be flashed seamlessly over the in-browser Web Serial updater or command line.
>
> If you plan to repurpose your ESP32-S3 board for other general electronics projects in the future, **do not flash the signed release or enable Secure Boot v2**. If you want a dedicated, hardened lock that cannot be defeated by serial reflashing, Secure Boot v2 is strongly recommended.