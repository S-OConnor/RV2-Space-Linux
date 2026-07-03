SUMMARY = "Static management network configuration for rv2-sgl (10.0.2.101)"
DESCRIPTION = "Ships a systemd-networkd .network file that pins the primary \
wired interface to a fixed IPv4 address, overriding the DHCP default that \
systemd-conf installs as 80-wired.network."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://10-rv2-static.network"

S = "${UNPACKDIR}"

# Config only -- no compiled content, so it is machine-independent.
inherit allarch

do_install() {
    install -d ${D}${sysconfdir}/systemd/network
    install -m 0644 ${S}/10-rv2-static.network ${D}${sysconfdir}/systemd/network/10-rv2-static.network
}

FILES:${PN} = "${sysconfdir}/systemd/network/10-rv2-static.network"

# The file is only meaningful under systemd-networkd, which this distro always
# has (INIT_MANAGER = "systemd"); systemd is not RDEPEND'd here to keep the
# package allarch-clean, since it is guaranteed present on every rv2-sgl image.
