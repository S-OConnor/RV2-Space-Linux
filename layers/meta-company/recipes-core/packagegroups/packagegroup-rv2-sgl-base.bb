SUMMARY = "Runtime baseline for RV2 Space Linux images"
DESCRIPTION = "Base set of runtime packages shared by all rv2-sgl images."
LICENSE = "MIT"

inherit packagegroup

# NOTE: the SSH server is intentionally NOT listed here -- it is owned by
# IMAGE_FEATURES (ssh-server-openssh) in rv2-sgl-image to avoid double ownership.
RDEPENDS:${PN} = " \
    chrony \
    chronyc \
    iproute2 \
    iputils \
    ethtool \
    curl \
    rsync \
    git \
    nano \
    python3-core \
    python3-modules \
    watchdog \
    watchdog-config \
    zeromq \
    cppzmq \
    hello-rv2 \
"

# Package notes:
#   chrony / chronyc     - from meta-networking (NTP client + control tool)
#   nano                 - from meta-oe (swap for `vim` from oe-core if preferred)
#   python3-modules      - the full stdlib metapackage; drop it to save
#                          significant image size (python3-core alone is much smaller)
#   watchdog / watchdog-config - from oe-core (userspace watchdog daemon + config)
#   zeromq               - from meta-oe; ships the runtime libzmq.so in the image.
#   cppzmq               - from meta-oe; header-only C++ bindings, so its main
#                          package is empty (ALLOW_EMPTY) and adds ~nothing to the
#                          rootfs. It is listed here on purpose: the SDK is built
#                          from this image with SDKIMAGE_FEATURES's dev-pkgs glob,
#                          which pulls the "-dev" of every installed package into
#                          the toolchain sysroot. So zeromq-dev (zmq.h, libzmq.so
#                          symlink, libzmq.pc) and cppzmq-dev (zmq.hpp) reach the
#                          SDK only because zeromq/cppzmq are installed here.

# SGL placeholder: future SGL packagegroups (e.g. packagegroup-sgl-core:
# hardening, safety supervision, update client, observability agents) should be
# appended here or in the image once meta-sgl publishes them.
