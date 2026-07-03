#!/usr/bin/env bash
#
# flash-sd.sh — Flash a Yocto image for the Orange Pi RV2 (SpacemiT Ky X1/K1)
#               onto an SD card.
#
# ############################################################################
# # WARNING: THIS SCRIPT IS DESTRUCTIVE.                                      #
# #                                                                           #
# # It writes raw data directly to a block device. ALL DATA on the target    #
# # device WILL BE DESTROYED — partition tables, filesystems, everything.    #
# #                                                                           #
# # * It must be run as root (raw block-device access).                      #
# # * DOUBLE-CHECK the target device before confirming. Picking the wrong    #
# #   device (e.g. your system disk) will irrecoverably wipe it.             #
# # * Use `lsblk` beforehand to positively identify the SD card.             #
# ############################################################################
#
# Usage:
#   sudo scripts/flash-sd.sh /dev/sdX [deploy-dir]
#
#   /dev/sdX     Whole-disk block device of the SD card (NOT a partition).
#   deploy-dir   Optional. Directory containing the build artifacts.
#                Defaults to the mainline deploy images dir under build/.
#
# Flashing this board is a TWO-step process (see meta-riscv
# docs/orangepi-rv2-mainline.md):
#   1. Write the rootfs .wic.gz image to the device.
#   2. Write bootinfo_sd.bin to the very start of the device — the SpacemiT
#      boot ROM reads this header from the card start; without it the card
#      does NOT boot. This MUST happen AFTER step 1, because writing the wic
#      image overwrites the start of the disk.
#

set -euo pipefail

# --- helpers ----------------------------------------------------------------

# die: print a message to stderr and exit non-zero.
die() {
	echo "Error: $*" >&2
	exit 1
}

# usage: print the usage synopsis (to stderr).
usage() {
	cat >&2 <<'EOF'
Usage: flash-sd.sh /dev/sdX [deploy-dir]

  /dev/sdX     Whole-disk block device of the target SD card (NOT a partition).
  deploy-dir   Optional. Directory with the build artifacts. Defaults to
               <repo-root>/build/tmp/deploy/images/orangepi-rv2-mainline

WARNING: This is destructive. All data on the target device is erased.
         Must be run as root. Double-check the target device.
EOF
}

# --- resolve repo root & arguments ------------------------------------------

# Derive the repo root from this script's location (scripts/ is one level down).
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# First positional argument is the target device; refuse with usage if absent.
if [[ $# -lt 1 ]]; then
	usage
	die "no target device specified."
fi

DEV="$1"
# Second (optional) argument overrides the deploy directory.
DEPLOY_DIR="${2:-${REPO_ROOT}/build/tmp/deploy/images/orangepi-rv2-mainline}"

# --- safety guards ----------------------------------------------------------
# Each guard below refuses to proceed on a condition that could destroy the
# wrong device or fail mid-flash. Fail fast and loud.

# Guard: must be root — raw writes to a block device require it.
if [[ "${EUID}" -ne 0 ]]; then
	die "must be run as root (try: sudo $0 $*)."
fi

# Guard: the target must actually be a block device.
if [[ ! -b "${DEV}" ]]; then
	die "'${DEV}' is not a block device."
fi

# Guard: refuse partitions — we must flash the WHOLE disk, not a partition
# such as /dev/sdb1, /dev/mmcblk0p1 or /dev/nvme0n1p1. lsblk reports the
# device TYPE; "part" means the user pointed us at a partition.
if [[ "$(lsblk -ndo TYPE "${DEV}")" == "part" ]]; then
	die "'${DEV}' looks like a partition, not a whole disk. Pass the whole-disk device (e.g. /dev/sdb, not /dev/sdb1)."
fi

# Guard: refuse if anything on the device (or any of its partitions) is
# mounted. This is the primary guard against accidentally flashing the running
# system disk — a mounted filesystem almost always means "this is in use".
if [[ -n "$(lsblk -no MOUNTPOINTS "${DEV}" 2>/dev/null | tr -d '[:space:]')" ]]; then
	die "'${DEV}' has a mounted filesystem. Refusing to flash a device that is in use (guard against wiping the system disk). Unmount it first if you are certain."
fi

# --- artifact discovery -----------------------------------------------------

# Ensure the deploy directory exists before globbing inside it.
if [[ ! -d "${DEPLOY_DIR}" ]]; then
	die "deploy directory not found: ${DEPLOY_DIR} (build the image first: scripts/build-dev.sh)."
fi

# Pick the NEWEST (by mtime) rootfs image. The glob is intentionally loose:
# image names may carry a ".rootfs." infix and a build timestamp, and both
# can change if meta-riscv renames its outputs — adjust the glob below if so.
#
# NOTE: artifact names may change if meta-riscv renames outputs — adjust
#       this glob if discovery ever fails.
IMAGE=""
IMAGE_GLOB="${DEPLOY_DIR}/rv2-sgl-image-orangepi-rv2-mainline*.wic.gz"
# Find newest match by mtime; null-delimited to be safe with odd names.
IMAGE="$(find "${DEPLOY_DIR}" -maxdepth 1 -type f \
	-name 'rv2-sgl-image-orangepi-rv2-mainline*.wic.gz' \
	-printf '%T@\t%p\n' 2>/dev/null | sort -rn | head -n1 | cut -f2-)"

if [[ -z "${IMAGE}" || ! -f "${IMAGE}" ]]; then
	die "no rootfs image found matching '${IMAGE_GLOB}'. Build it first: scripts/build-dev.sh."
fi

# Derive the bmap file from the image name by swapping the extension.
# bmaptool can use it to write (and verify) only the used blocks.
BMAP="${IMAGE%.wic.gz}.wic.bmap"

# bootinfo_sd.bin lives alongside the image in the deploy dir. Without it the
# board's boot ROM has no header to read and the card will NOT boot.
BOOTINFO="${DEPLOY_DIR}/bootinfo_sd.bin"
if [[ ! -f "${BOOTINFO}" ]]; then
	die "bootinfo_sd.bin not found in ${DEPLOY_DIR}. The board will NOT boot without it (the SpacemiT boot ROM reads this header from the start of the card)."
fi

# --- pre-flight summary & confirmation --------------------------------------

echo "About to flash the following:"
echo "  Image:    ${IMAGE}"
if [[ -f "${BMAP}" ]]; then
	echo "  Bmap:     ${BMAP}"
else
	echo "  Bmap:     not found, will use dd"
fi
echo "  Bootinfo: ${BOOTINFO}"
echo
echo "Target device:"
lsblk -d -o NAME,SIZE,MODEL,TRAN "${DEV}"
echo
echo "!!! ALL DATA ON ${DEV} WILL BE DESTROYED !!!"
echo

# Interactive confirmation: require the literal string "yes". Anything else
# (including an empty line or EOF) aborts.
read -r -p "Type 'yes' to flash ${DEV}: " answer
if [[ "${answer}" != "yes" ]]; then
	die "aborted."
fi

# --- step 1: write the rootfs image -----------------------------------------

echo
echo "==> Step 1/2: writing rootfs image to ${DEV} ..."
if [[ -f "${BMAP}" ]] && command -v bmaptool >/dev/null 2>&1; then
	# Prefer bmaptool: it writes only the used blocks and verifies checksums,
	# so it is faster and safer than a raw dd. NOTE: the tool was renamed from
	# "bmap-tools" (package) to the "bmaptool" command in modern distros — we
	# probe for the COMMAND, not the package name.
	echo "    using bmaptool (bmap present, faster + checksum-verified)"
	bmaptool copy "${IMAGE}" "${DEV}"
else
	# Fallback: decompress and dd. We pipe through zcat because the image is
	# gzip-compressed (.wic.gz); dd cannot decompress on its own.
	# conv=fsync forces the data out to the device before dd exits.
	echo "    using dd fallback (no bmap and/or bmaptool unavailable)"
	zcat "${IMAGE}" | dd of="${DEV}" bs=4M status=progress conv=fsync
fi

# --- step 2: write the boot header (MUST be after step 1) -------------------

# ORDERING IS CRITICAL: bootinfo_sd.bin is written to offset 0 (the very start
# of the device — note: NO seek). Step 1 (the wic image write) overwrites the
# start of the disk, so this header MUST be written AFTERWARDS or it would be
# clobbered. The SpacemiT boot ROM reads this header from the card start; the
# card will not boot without it.
echo
echo "==> Step 2/2: writing bootinfo_sd.bin to start of ${DEV} ..."
dd if="${BOOTINFO}" of="${DEV}" conv=fsync status=none

# --- finalize ---------------------------------------------------------------

# Flush all buffers to the physical device before we declare success.
sync

echo
echo "Success: ${DEV} flashed."
echo "You can now remove the card safely."
echo "Hint: attach a serial console to the board to watch it boot."
