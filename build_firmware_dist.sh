#!/bin/bash
set -e

# ==============================================================================
# Firmware Build & Packaging Script
# ==============================================================================
#
# Partition layout (4 MB Flash):
#   - 0x0000  - 0x8000  : Bootloader (32 KiB)
#   - 0x8000  - 0x9000  : Partition Table (4 KiB)
#   - 0x9000  - 0xF000  : NVS (24 KiB) [PRESERVED - contains device state]
#   - 0xF000  - 0x10000 : PHY init (4 KiB)
#   - 0x10000 - 0x400000: Factory App (4032 KiB)
#
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION_FILE="${SCRIPT_DIR}/VERSION"

if [ -n "$FIRMWARE_VERSION" ]; then
    VERSION="$FIRMWARE_VERSION"
elif [ -f "$VERSION_FILE" ]; then
    VERSION=$(tr -d '[:space:]' < "$VERSION_FILE")
else
    echo "Error: VERSION file not found at $VERSION_FILE!" >&2
    exit 1
fi

SIGNED=false

for arg in "$@"; do
    case "$arg" in
        --signed)
            SIGNED=true
            ;;
        -h|--help)
            echo "Usage: $(basename "$0") [--signed]"
            echo ""
            echo "Options:"
            echo "  --signed    Sign application binary and export signed bootloader for Secure Boot v2"
            echo "  -h, --help  Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg"
            echo "Usage: $(basename "$0") [--signed]"
            exit 1
            ;;
    esac
done

echo "Building Tartarus Firmware v${VERSION}"

KEY_FILE="secure_boot_signing_key.pem"

if [ "$SIGNED" = true ]; then
    echo "Mode: SIGNED (Secure Boot v2)"
    if [ ! -f "$KEY_FILE" ]; then
        echo "Error: Signing key ($KEY_FILE) not found! Cannot build signed firmware."
        exit 1
    fi
    export ESP_IDF_SDKCONFIG_DEFAULTS="sdkconfig.defaults;sdkconfig.defaults.secureboot"
else
    echo "Mode: UNSIGNED (Default)"
    export ESP_IDF_SDKCONFIG_DEFAULTS="sdkconfig.defaults"
fi

# Detect whether the cached esp-idf-sys build matches the requested mode/flashsize.
# If mode or flash size changed, clear cached esp-idf-sys build directory to ensure CMake reconfigures.
CACHED_SDKCONFIG=$(find target/xtensa-esp32s3-espidf/release/build -name "sdkconfig" 2>/dev/null | head -n 1)
if [ -n "$CACHED_SDKCONFIG" ] && [ -f "$CACHED_SDKCONFIG" ]; then
    HAS_SECURE_BOOT=$(grep -c "^CONFIG_SECURE_BOOT=y" "$CACHED_SDKCONFIG" || true)
    HAS_4MB_FLASH=$(grep -c "^CONFIG_ESPTOOLPY_FLASHSIZE_4MB=y" "$CACHED_SDKCONFIG" || true)
    if [ "$SIGNED" = true ] && [ "$HAS_SECURE_BOOT" -eq 0 ]; then
        echo "Detected previous build without secure boot. Cleaning esp-idf-sys cache to enable secure boot..."
        rm -rf target/xtensa-esp32s3-espidf/release/build/esp-idf-sys-*
    elif [ "$SIGNED" = false ] && [ "$HAS_SECURE_BOOT" -gt 0 ]; then
        echo "Detected previous build with secure boot. Cleaning esp-idf-sys cache to disable secure boot..."
        rm -rf target/xtensa-esp32s3-espidf/release/build/esp-idf-sys-*
    elif [ "$HAS_4MB_FLASH" -eq 0 ]; then
        echo "Detected cached build with 2MB flash size. Cleaning esp-idf-sys cache to apply 4MB flash size..."
        rm -rf target/xtensa-esp32s3-espidf/release/build/esp-idf-sys-*
    fi
fi

export ESP_IDF_GLOB_COREROOT_BASE="."
export ESP_IDF_GLOB_COREROOT_PARTITIONS="partitions.csv"
if [ -f "$KEY_FILE" ]; then
    export ESP_IDF_GLOB_COREROOT_KEY="$KEY_FILE"
fi

# Ensure partitions.csv is available in any existing esp-idf-sys build directories
for d in $(find target/xtensa-esp32s3-espidf -type d -name "esp-idf-sys-*" 2>/dev/null); do
    if [ -d "$d/out" ]; then
        cp -f partitions.csv "$d/out/" 2>/dev/null || true
        if [ -f "$KEY_FILE" ]; then
            cp -f "$KEY_FILE" "$d/out/" 2>/dev/null || true
        fi
    fi
done

echo "Building release binary..."
cargo build --release

# S3-compatible versioned file names
PARTITIONS_IMAGE="partitions-${VERSION}.bin"

if [ "$SIGNED" = true ]; then
    BOOTLOADER_IMAGE="bootloader-${VERSION}-signed.bin"
    UPDATE_IMAGE="app-${VERSION}-signed.bin"
    FULL_IMAGE="image-${VERSION}-signed.bin"
    UPDATE_DESC="Tartarus Lock Firmware Update v${VERSION} (Signed, preserves NVS)"
    FULL_DESC="Tartarus Lock Full Provisioning v${VERSION} (Signed Bootloader + Partitions + App)"
else
    BOOTLOADER_IMAGE="bootloader-${VERSION}.bin"
    UPDATE_IMAGE="app-${VERSION}.bin"
    FULL_IMAGE="image-${VERSION}.bin"
    UPDATE_DESC="Tartarus Lock Firmware Update v${VERSION} (Unsigned, preserves NVS)"
    FULL_DESC="Tartarus Lock Full Provisioning v${VERSION} (Bootloader + Partitions + App)"
fi

echo "Packaging merged 4MB image (/tmp/${FULL_IMAGE})..."
# Make a merged binary that contains the bootloader, partition table, and app
espflash save-image --chip esp32s3 target/xtensa-esp32s3-espidf/release/tartarus-firmware "/tmp/${FULL_IMAGE}" --flash-size=4mb --merge --partition-table partitions.csv

# Extract individual binaries for selective flashing
dd if="/tmp/${FULL_IMAGE}" of="/tmp/${PARTITIONS_IMAGE}" bs=1K skip=32 count=4
dd if="/tmp/${FULL_IMAGE}" of=/tmp/bootloader_raw.bin bs=1K count=32

echo "Packaging standalone factory app binary..."
# Make the standalone main app (target offset 0x10000)
espflash save-image --chip esp32s3 target/xtensa-esp32s3-espidf/release/tartarus-firmware /tmp/app_raw.bin --flash-size=4mb --partition-table partitions.csv

if [ "$SIGNED" = true ]; then
    # Copy signed bootloader generated by the esp-idf-sys build
    BOOTLOADER_SRC=$(find target/xtensa-esp32s3-espidf/release/build -name "bootloader.bin" 2>/dev/null | head -n 1)
    if [ -n "$BOOTLOADER_SRC" ] && [ -f "$BOOTLOADER_SRC" ]; then
        echo "Copying signed bootloader from ${BOOTLOADER_SRC}..."
        cp "$BOOTLOADER_SRC" "/tmp/${BOOTLOADER_IMAGE}"
    else
        echo "Warning: Signed bootloader not found in target build directory."
        cp /tmp/bootloader_raw.bin "/tmp/${BOOTLOADER_IMAGE}"
    fi

    ESP_SECURE_PY=$(which espsecure.py 2>/dev/null || find .embuild/espressif/python_env -name espsecure.py 2>/dev/null | head -n 1)

    if [ -f "$KEY_FILE" ] && [ -n "$ESP_SECURE_PY" ]; then
        echo "Signing standalone app with Secure Boot v2 key..."
        "$ESP_SECURE_PY" sign_data --version 2 --keyfile "$KEY_FILE" --output "/tmp/${UPDATE_IMAGE}" /tmp/app_raw.bin
    else
        echo "Error: Signing key ($KEY_FILE) or espsecure.py not found! Cannot sign."
        exit 1
    fi

    echo "Re-splicing merged signed 4MB image..."
    dd if="/tmp/${BOOTLOADER_IMAGE}" of="/tmp/${FULL_IMAGE}" bs=1K seek=0 conv=notrunc 2>/dev/null
    dd if="/tmp/${UPDATE_IMAGE}" of="/tmp/${FULL_IMAGE}" bs=1K seek=64 conv=notrunc 2>/dev/null
else
    # In unsigned mode, use standard bootloader extracted from merged image
    cp /tmp/bootloader_raw.bin "/tmp/${BOOTLOADER_IMAGE}"
    cp /tmp/app_raw.bin "/tmp/${UPDATE_IMAGE}"
fi

# Create convenience backward-compatible symlinks in /tmp
ln -sf "/tmp/${BOOTLOADER_IMAGE}" /tmp/bootloader.bin
ln -sf "/tmp/${PARTITIONS_IMAGE}" /tmp/partitions.bin
ln -sf "/tmp/${UPDATE_IMAGE}" /tmp/app.bin
ln -sf "/tmp/${FULL_IMAGE}" /tmp/image.bin
ln -sf "/tmp/${FULL_IMAGE}" /tmp/image.bin

# Generate or update firmware.json for Tartarus Web Updater
TODAY=$(date +%Y-%m-%d)
echo "Updating firmware.json manifest..."
RELEASE_VERSION="${VERSION}" \
RELEASE_DATE="${TODAY}" \
RELEASE_SIGNED="${SIGNED}" \
RELEASE_DESC="${UPDATE_DESC}" \
RELEASE_APP="${UPDATE_IMAGE}" \
RELEASE_BOOTLOADER="${BOOTLOADER_IMAGE}" \
RELEASE_PARTITIONS="${PARTITIONS_IMAGE}" \
python3 - << 'EOF'
import os, json

version = os.environ['RELEASE_VERSION']
today = os.environ['RELEASE_DATE']
signed = os.environ['RELEASE_SIGNED'].lower() == 'true'
desc = os.environ['RELEASE_DESC']
app = os.environ['RELEASE_APP']
bootloader = os.environ['RELEASE_BOOTLOADER']
partitions = os.environ['RELEASE_PARTITIONS']

manifest_file = 'firmware.json'
data = {'name': 'Tartarus Firmware Releases', 'chipset': 'ESP32-S3', 'releases': []}
if os.path.exists(manifest_file):
    try:
        with open(manifest_file, 'r') as f:
            loaded = json.load(f)
            if isinstance(loaded, dict) and 'releases' in loaded:
                data = loaded
    except Exception as e:
        print(f"Note: starting fresh firmware.json: {e}")

new_release = {
    'version': version,
    'releaseDate': today,
    'signed': signed,
    'description': desc,
    'app': app,
    'bootloader': bootloader,
    'partitions': partitions,
}

# Update in place if same version and signed status exist, otherwise prepend
existing = [r for r in data.get('releases', []) if not (r.get('version') == version and r.get('signed') == signed)]
existing.insert(0, new_release)
data['releases'] = existing

with open('/tmp/firmware.json', 'w') as f:
    json.dump(data, f, indent=2)
    f.write('\n')
EOF

cp /tmp/firmware.json ./firmware.json

echo ""
echo "Firmware packaging complete:"
echo "  - Full merged image:   /tmp/${FULL_IMAGE} (flash at 0x0)"
echo "  - Bootloader:          /tmp/${BOOTLOADER_IMAGE} (flash at 0x0)"
echo "  - Partition table:     /tmp/${PARTITIONS_IMAGE} (flash at 0x8000)"
echo "  - Standalone app:      /tmp/${UPDATE_IMAGE} (flash at 0x10000)"
echo "  - Web Updater manifest:/tmp/firmware.json and ./firmware.json"
echo ""
echo "Ready for upload to S3 (${FIRMWARE_BASE_URL})."
