SUMMARY = "RS-422 serial-link monitor and command-&-control apps for the RV2 image"
DESCRIPTION = "The rs422-monitor (RX: receive and live-display the RFC 1662 HDLC \
attitude stream, with an optional CSV log) and rs422-c2 (register read/write \
command-&-control host) applications from the serial_link C++20 port, built \
against the project's in-tree libserial_link static library."
HOMEPAGE = "https://github.com/S-OConnor/RS-422-Tools"

# Upstream ships no LICENSE file (the CMake/CPack metadata declares a vendor and
# contact but no license); track it as CLOSED -- no license auditing, no
# LIC_FILES_CHKSUM -- until a LICENSE lands in the repo. Mirrors the
# embedded-polaris recipe in this layer.
LICENSE = "CLOSED"

# Pulled from git at build time -- never from a local checkout. SRCREV pins the
# exact commit for reproducibility; bump SRCREV (and branch, if it moves) together
# to take a newer drop.
#
# NOTE: if the repository is private, the build host needs read access to it
# (an https token or an ssh deploy key + protocol=ssh); a fetch failure here means
# credentials/mirror are not configured, not a recipe error.
SRC_URI = "git://github.com/S-OConnor/RS-422-Tools.git;protocol=https;branch=master"
SRCREV = "87a27165c2f1ea202c5ab9b2d7e4f54f46a61a17"

# Like embedded-polaris / cppzmq, S is left unset: this Yocto sets
# BB_GIT_DEFAULT_DESTSUFFIX to ${BP}, so git unpacks to ${UNPACKDIR}/${BP}, which
# is exactly the default S.

inherit cmake

# No build-time DEPENDS. The only libraries the code uses are pthreads
# (Threads::Threads, resolved from the toolchain by find_package(Threads)) and
# libutil (-lutil, for openpty; part of glibc in the sysroot). GoogleTest is NOT
# pulled because the tests are disabled below: with SERIAL_LINK_BUILD_TESTS=OFF
# the CMake never adds the tests/ subdirectory, so its find_package(GTest) +
# FetchContent fallback -- which would clone GoogleTest at configure time and fail
# Yocto's network-isolated do_configure -- is never reached.

# -DSERIAL_LINK_BUILD_TESTS=OFF: skip the GoogleTest suite in image builds (see
#   the DEPENDS note above). Enable it only in a separate ptest recipe that
#   DEPENDS on googletest.
# SERIAL_LINK_BUILD_APPS defaults to ON upstream; the install(TARGETS ...) rules
#   in CMakeLists.txt place the app binaries in ${bindir} (and libserial_link.a /
#   the public headers in ${libdir} / ${includedir}, which Yocto auto-splits into
#   the -staticdev / -dev packages), so the inherited cmake do_install needs no
#   override beyond the trim below.
EXTRA_OECMAKE = "-DSERIAL_LINK_BUILD_TESTS=OFF"

# The app binaries link only libc / libstdc++ / libpthread / libutil, all from
# glibc; the automatic shared-library scan adds those runtime deps on its own, so
# no explicit RDEPENDS is needed.

# CMake builds and installs all three rs422-* apps under a single
# SERIAL_LINK_BUILD_APPS switch (there is no per-app CMake option). The image only
# needs the monitor (RX) and c2 (register control) hosts, so drop the TX-side
# attitude publisher after the install. To also ship it, delete the rm line.
do_install:append() {
    rm -f ${D}${bindir}/rs422-attitude-publisher
}
