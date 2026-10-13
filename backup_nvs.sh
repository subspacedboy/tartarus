#!/usr/bin/env bash
# ==============================================================================
# Tartarus Lock - NVS Partition Backup Script
# ==============================================================================
# Reads the 16 KiB NVS partition (contains cryptographic keys, WiFi credentials,
# and device state) from the attached ESP32-S3 Tartarus lock and saves it to a
# binary file with a timestamp.
#
# Partition layout reference (from partitions.csv):
#   - Name:   nvs
#   - Offset: 0x9000
#   - Size:   0x4000 (16 KiB / 16,384 bytes)
#   - Chip:   ESP32-S3
#
# Usage:
#   ./backup_nvs.sh                     # Auto-detect port and backup with timestamp
#   ./backup_nvs.sh -p /dev/cu.usb...   # Specify serial port
#   ./backup_nvs.sh -o custom_name.bin  # Specify custom output filename
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

NVS_OFFSET="0x9000"
NVS_SIZE="0x4000"
EXPECTED_BYTES=16384
DEFAULT_CHIP="esp32s3"

CHIP="${CHIP:-$DEFAULT_CHIP}"
PORT="${PORT:-${ESPFLASH_PORT:-}}"
BAUD="${BAUD:-}"
TIMESTAMP="$(date +"%Y%m%d_%H%M%S")"
OUTPUT_FILE=""

show_help() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS] [PORT_OR_OUTPUT]

Backs up the Tartarus lock NVS partition (0x9000 - 0xD000, 16 KiB) with a timestamp.

Options:
  -p, --port <PORT>        Serial port connected to the lock (auto-detected if omitted)
  -o, --output <FILE>      Output filename (default: nvs_backup_<TIMESTAMP>.bin)
  -c, --chip <CHIP>        Target chip (default: esp32s3)
  -b, --baud <BAUD>        Serial baud rate
  -h, --help               Show this help message

Examples:
  ./backup_nvs.sh
  ./backup_nvs.sh -p /dev/cu.usbmodem1101
  ./backup_nvs.sh /dev/cu.usbmodem1101
  ./backup_nvs.sh -o my_lock_nvs.bin
EOF
}

# Parse options
while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--port)
            PORT="$2"
            shift 2
            ;;
        -o|--output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        -c|--chip)
            CHIP="$2"
            shift 2
            ;;
        -b|--baud)
            BAUD="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            # Positional argument convenience:
            # If it starts with /dev/ or looks like a port, set PORT.
            # Otherwise treat as output filename.
            if [[ "$1" == /dev/* || "$1" == COM* ]]; then
                PORT="$1"
            elif [[ -z "$OUTPUT_FILE" ]]; then
                OUTPUT_FILE="$1"
            else
                echo "Error: Unknown argument '$1'" >&2
                show_help >&2
                exit 1
            fi
            shift
            ;;
    esac
done

if [[ -z "$OUTPUT_FILE" ]]; then
    OUTPUT_FILE="nvs_backup_${TIMESTAMP}.bin"
fi

echo "=============================================="
echo " Tartarus Lock NVS Backup"
echo "=============================================="
echo "  Chip:        $CHIP"
echo "  NVS Offset:  $NVS_OFFSET"
echo "  NVS Size:    $NVS_SIZE ($EXPECTED_BYTES bytes)"
echo "  Timestamp:   $TIMESTAMP"
echo "  Output file: $OUTPUT_FILE"
if [[ -n "$PORT" ]]; then
    echo "  Port:        $PORT"
else
    echo "  Port:        (auto-detect)"
fi
echo "=============================================="

# Find available tool (prefer espflash, fallback to esptool)
TOOL=""
ESPFLASH_CMD=""
ESPTOOL_CMD=""

if command -v espflash >/dev/null 2>&1; then
    ESPFLASH_CMD="espflash"
elif [[ -x "$HOME/.cargo/bin/espflash" ]]; then
    ESPFLASH_CMD="$HOME/.cargo/bin/espflash"
fi

if [[ -n "$ESPFLASH_CMD" ]]; then
    TOOL="espflash"
elif command -v esptool.py >/dev/null 2>&1; then
    TOOL="esptool"
    ESPTOOL_CMD="esptool.py"
elif command -v esptool >/dev/null 2>&1; then
    TOOL="esptool"
    ESPTOOL_CMD="esptool"
elif python3 -c "import esptool" >/dev/null 2>&1; then
    TOOL="esptool"
    ESPTOOL_CMD="python3 -m esptool"
else
    echo "Error: Neither 'espflash' nor 'esptool' could be found." >&2
    echo "Please ensure espflash is installed (cargo install espflash) or esptool is in PATH." >&2
    exit 1
fi

echo "Reading NVS partition using $TOOL..."

if [[ "$TOOL" == "espflash" ]]; then
    CMD=("$ESPFLASH_CMD" "read-flash")
    if [[ -n "$CHIP" ]]; then
        CMD+=("--chip" "$CHIP")
    fi
    if [[ -n "$PORT" ]]; then
        CMD+=("--port" "$PORT")
    fi
    if [[ -n "$BAUD" ]]; then
        CMD+=("--baud" "$BAUD")
    fi
    CMD+=("$NVS_OFFSET" "$NVS_SIZE" "$OUTPUT_FILE")

    "${CMD[@]}"
else
    CMD=($ESPTOOL_CMD)
    if [[ -n "$CHIP" ]]; then
        CMD+=("--chip" "$CHIP")
    fi
    if [[ -n "$PORT" ]]; then
        CMD+=("-p" "$PORT")
    fi
    if [[ -n "$BAUD" ]]; then
        CMD+=("-b" "$BAUD")
    fi
    CMD+=("read_flash" "$NVS_OFFSET" "$NVS_SIZE" "$OUTPUT_FILE")

    "${CMD[@]}"
fi

# Verify output
if [[ ! -f "$OUTPUT_FILE" ]]; then
    echo "Error: Output file '$OUTPUT_FILE' was not created." >&2
    exit 1
fi

ACTUAL_SIZE=$(wc -c < "$OUTPUT_FILE" | tr -d ' ')

if [[ "$ACTUAL_SIZE" -ne "$EXPECTED_BYTES" ]]; then
    echo "Warning: Expected $EXPECTED_BYTES bytes but got $ACTUAL_SIZE bytes." >&2
fi

# Update symlink to latest backup
ln -sf "$OUTPUT_FILE" "nvs_backup.bin"

echo ""
echo " Backup completed successfully!"
echo "  File:    $SCRIPT_DIR/$OUTPUT_FILE"
echo "  Size:    $ACTUAL_SIZE bytes"
echo "  Symlink: $SCRIPT_DIR/nvs_backup.bin -> $OUTPUT_FILE"
echo ""
echo "To restore this backup in the future:"
if [[ "$TOOL" == "espflash" ]]; then
    echo "  $ESPFLASH_CMD write-bin $NVS_OFFSET $OUTPUT_FILE"
else
    echo "  $ESPTOOL_CMD write_flash $NVS_OFFSET $OUTPUT_FILE"
fi
