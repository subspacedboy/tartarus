#!/bin/bash
set -e

# ==============================================================================
# Firmware Build & Packaging Script
# ==============================================================================
#
# Partition layout (4 MB Flash):
#   - 0x0000  - 0x8000  : Bootloader (32 KiB)
#   - 0x8000  - 0x9000  : Partition Table (4 KiB)
#   - 0x9000  - 0xD000  : NVS (16 KiB) [PRESERVED - contains device state]
#   - 0xF000  - 0x10000 : PHY init (4 KiB)
#   - 0x10000 - 0x400000: Factory App (4032 KiB)
#
# ==============================================================================
# FLASHING INSTRUCTIONS:
#
# 1. UPDATING EXISTING UNITS IN THE FIELD (PRESERVING NVS DATA):
#    CRITICAL: Never run chip erase (`erase_flash` or `espflash ... --erase-parts`)
#    because that will wipe existing user data in the NVS partition (0x9000-0xD000).
#
#    Using espflash:
#      espflash write-bin 0x8000 /tmp/partitions.bin
#      espflash write-bin 0x10000 /tmp/app.bin
#
#    Using esptool.py:
#      esptool.py --chip esp32s3 write_flash 0x8000 /tmp/partitions.bin 0x10000 /tmp/app.bin
#
# 2. PROVISIONING BRAND NEW / BLANK UNITS:
#    The merged binary contains bootloader, partition table, and the factory app.
#
#    Using espflash:
#      espflash write-bin 0x0 /tmp/image.bin
#
#    Using esptool.py:
#      esptool.py --chip esp32s3 write_flash 0x0 /tmp/image.bin
# ==============================================================================

echo "Building release binary..."
cargo build --release

echo "Packaging merged 4MB image..."
# Make a merged binary that contains the bootloader, partition table, and app
espflash save-image --chip esp32s3 target/xtensa-esp32s3-espidf/release/tartarus-firmware /tmp/image.bin --flash-size=4mb --merge

# Extract individual binaries for selective flashing
dd if=/tmp/image.bin of=/tmp/partitions.bin bs=1K skip=32 count=4
dd if=/tmp/image.bin of=/tmp/bootloader_4mb.bin bs=1K count=32

echo "Packaging standalone factory app binary..."
# Make the standalone main app (target offset 0x10000)
espflash save-image --chip esp32s3 target/xtensa-esp32s3-espidf/release/tartarus-firmware /tmp/app.bin --flash-size=4mb

echo "Firmware packaging complete:"
echo "  - Full merged image: /tmp/image.bin (flash at 0x0)"
echo "  - Partition table:   /tmp/partitions.bin (flash at 0x8000)"
echo "  - Standalone app:    /tmp/app.bin (flash at 0x10000)"
