SUMMARY = "Polaris multi-target tracker (example framework) apps for the RV2 image"
DESCRIPTION = "Angle-only / dual-pool multi-target tracker. Ships the worked \
track_from_files application and the ZeroMQ socket services (frame_server, \
frame_processor, frame_recorder, sensor_csv_driver) built from the C++20 kernel \
library, plus the bundled step configs under /etc/polaris/configs."
HOMEPAGE = "https://github.com/S-OConnor/embedded-polaris"

# Upstream ships no license file and its RPM spec declares the payload
# "Proprietary"; track it as CLOSED (no license auditing / no LIC_FILES_CHKSUM)
# until a LICENSE lands in the repo.
LICENSE = "CLOSED"

# Pulled from git at build time -- never from a local checkout. `cplusplus_migration`
# is the C++ port branch; SRCREV pins the exact commit for reproducibility. Bump the
# two together to take a newer drop.
#
# NOTE: if the repository is private, the build host needs read access to it
# (an https token or an ssh deploy key + `protocol=ssh`); a fetch failure here means
# credentials/mirror are not configured, not a recipe error.
SRC_URI = "git://github.com/S-OConnor/embedded-polaris.git;protocol=https;branch=cplusplus_migration \
           file://0001-cross-build-zmq-socket-apps.patch"
SRCREV = "ddb367cb78dd025aa17a15c5adb1340b67fcb5fe"

# Like the cppzmq recipe, S is left unset: this Yocto sets BB_GIT_DEFAULT_DESTSUFFIX
# to ${BP}, so git unpacks to ${UNPACKDIR}/${BP}, which is exactly the default S.

inherit cmake

# Build-time dependencies (all already provided by the layer stack):
#   boost      (oe-core) -- header-only Boost.PropertyTree, the runtime XML config loader
#   zeromq     (meta-oe) -- libzmq, linked by the frame_* socket apps
#   cppzmq     (meta-oe) -- header-only C++ bindings over libzmq
#   googletest (meta-oe) -- upstream gates the app targets behind GTest being found
#                           (find_package(GTest) in CMakeLists); build-time only, the
#                           test binaries it builds are not installed.
#   highway    (meta-oe) -- Google Highway, for POLARIS_ENABLE_HIGHWAY (below). REQUIRED
#                           whenever Highway is on: the CMake does find_package(hwy CONFIG)
#                           and, if it is NOT found, falls back to a FetchContent git clone
#                           at configure time -- which fails in Yocto's network-isolated
#                           do_configure. Depending on it here keeps find_package(hwy)
#                           satisfied from the sysroot so that fallback is never taken.
DEPENDS = "boost zeromq cppzmq googletest highway"

# The frame_* apps dynamically link libzmq; Boost/cppzmq/gtest are header-only or
# test-only and add no runtime dependency. (Shared-library tracking would infer
# zeromq from the ELF as well; it is declared here for clarity.) Highway (hwy::hwy)
# is linked statically by the meta-oe recipe, so it too adds no runtime dependency;
# were it ever shared, the automatic shlib scan would add it here on its own.
RDEPENDS:${PN} = "zeromq"

# -DPOLARIS_TARGET_ARCH=spacemit-x60: the Orange Pi RV2's SoC (SpacemiT K1/X60,
# RISC-V RVV 1.0, VLEN=256). This adds -march=rv64gcv_zvl256b -mrvv-vector-bits=zvl,
# overriding the machine tune's -march to emit fixed-width RVV for the board (the -mabi
# stays lp64d, so the objects remain ABI-compatible with the rv64gc sysroot libraries --
# the standard "one RVV component over a baseline system" arrangement). Matches the
# upstream build:rv2 CI profile. Needs GCC 14+ for the zvl256b / -mrvv-vector-bits flags;
# wrynose ships GCC 15. (The -mcpu=spacemit-x60 scheduling model is clang-only upstream,
# so this GCC build correctly gets just the ISA + VLEN, which is what codegen depends on.)
# -DPOLARIS_ENABLE_HIGHWAY=ON: build the Google Highway across-instance SIMD NIS kernels
# (bit-identical to the scalar path); resolved from the sysroot via the highway DEPENDS.
# -DPOLARIS_BUILD_ZMQ_APP=ON: build the socket services (the SRC_URI patch is what lets
# them cross-compile -- upstream keeps them host-only otherwise).
EXTRA_OECMAKE = "-DPOLARIS_TARGET_ARCH=spacemit-x60 -DPOLARIS_ENABLE_HIGHWAY=ON -DPOLARIS_BUILD_ZMQ_APP=ON"

# The upstream CMake defines no install() rules (release staging is done by the shell
# script packaging/stage_release.sh); install the built binaries and configs by hand,
# mirroring that script's on-target layout.
do_install() {
    install -d ${D}${bindir}
    for app in track_from_files frame_server frame_processor frame_recorder sensor_csv_driver; do
        install -m 0755 ${B}/$app ${D}${bindir}/$app
    done

    # Bundled step configs -> /etc/polaris/configs (matches the upstream RPM layout).
    # Each app bakes a build-tree default config path at compile time; override it at
    # runtime with `--config /etc/polaris/configs/<name>.xml`.
    install -d ${D}${sysconfdir}/polaris/configs
    install -m 0644 ${S}/configs/*.xml ${D}${sysconfdir}/polaris/configs/
}

# Each app embeds its default --config path (a build-tree location) as a compile
# definition, which trips the buildpaths QA check. It is harmless here: the path is
# only a fallback and every app accepts --config at runtime, so allow it.
INSANE_SKIP:${PN} += "buildpaths"
