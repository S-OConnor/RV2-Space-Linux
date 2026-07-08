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
    rv2-network \
    zeromq \
    cppzmq \
    hello-rv2 \
    embedded-polaris \
    rs422-tools \
"

# Package notes:
#   chrony / chronyc     - from meta-networking (NTP client + control tool)
#   nano                 - from meta-oe (swap for `vim` from oe-core if preferred)
#   python3-modules      - the full stdlib metapackage; drop it to save
#                          significant image size (python3-core alone is much smaller)
#   watchdog / watchdog-config - from oe-core (userspace watchdog daemon + config)
#   rv2-network          - from meta-company; pins the primary wired port to the
#                          static address 10.0.2.101/24 (systemd-networkd),
#                          overriding systemd-conf's DHCP default. Edit the
#                          .network file in recipes-core/network to change it.
#   zeromq               - from meta-oe; ships the runtime libzmq.so in the image.
#   cppzmq               - from meta-oe; header-only C++ bindings, so its main
#                          package is empty (ALLOW_EMPTY) and adds ~nothing to the
#                          rootfs. It is listed here on purpose: the SDK is built
#                          from this image with SDKIMAGE_FEATURES's dev-pkgs glob,
#                          which pulls the "-dev" of every installed package into
#                          the toolchain sysroot. So zeromq-dev (zmq.h, libzmq.so
#                          symlink, libzmq.pc) and cppzmq-dev (zmq.hpp) reach the
#                          SDK only because zeromq/cppzmq are installed here.
#                          They are also the runtime deps of embedded-polaris below.
#   embedded-polaris     - from meta-company; the Polaris tracker apps
#                          (track_from_files + the frame_* ZeroMQ socket services),
#                          fetched and built from git (github S-OConnor/embedded-polaris,
#                          branch cplusplus_migration). It links libzmq at runtime, which
#                          is why zeromq is required in the image. See its recipe in
#                          recipes-apps/embedded-polaris.
#   rs422-tools          - from meta-company; the RS-422 serial_link apps rs422-monitor
#                          (attitude-stream live display) and rs422-c2 (register command
#                          & control), fetched and built from git (github
#                          S-OConnor/RS-422-Tools, branch master). Plain CMake, no extra
#                          runtime deps beyond glibc. See recipes-apps/rs422-tools.

# SGL placeholder: future SGL packagegroups (e.g. packagegroup-sgl-core:
# hardening, safety supervision, update client, observability agents) should be
# appended here or in the image once meta-sgl publishes them.
