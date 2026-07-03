SUMMARY = "SGL-oriented image for the Orange Pi RV2 (SpacemiT Ky X1)"
DESCRIPTION = "Minimal-but-usable image: systemd, SSH, NTP, watchdog, \
networking tools and the hello-rv2 sample application."
LICENSE = "MIT"

inherit core-image

# IMPORTANT: all IMAGE_* appends must come AFTER the inherit line. core-image
# sets IMAGE_INSTALL with ?=, so appending before the inherit silently drops
# packagegroup-core-boot.

IMAGE_FEATURES += "ssh-server-openssh"
IMAGE_INSTALL += "packagegroup-rv2-sgl-base"
IMAGE_LINGUAS = "en-us"

# IMAGE_FSTYPES intentionally not set here -- the orangepi-rv2-mainline machine
# conf already produces wic.gz + wic.bmap + ext4 + tar.zst.

# ---------------------------------------------------------------------------
# SGL placeholders
# ---------------------------------------------------------------------------
# Hardening candidate; requires volatile-binds review before enabling:
# IMAGE_FEATURES += "read-only-rootfs"
#
# Once meta-sgl-core ships packagegroups (it currently ships none):
# IMAGE_INSTALL += "packagegroup-sgl-core"
