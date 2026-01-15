#!/bin/bash

echo "Building"

cargo build --release

# Make a merged binary that contains the bootloader, partition table, and app
espflash save-image --chip esp32s3 target/xtensa-esp32s3-espidf/release/tartarus-firmware /tmp/image.bin --flash-size=4mb --merge

dd if=/tmp/image.bin of=/tmp/partitions.bin bs=1K skip=32 count=4
dd if=/tmp/image.bin of=/tmp/bootloader_4mb.bin bs=1K count=32

# Make the main app
espflash save-image --chip esp32s3 target/xtensa-esp32s3-espidf/release/tartarus-firmware /tmp/app.bin --flash-size=4mb
