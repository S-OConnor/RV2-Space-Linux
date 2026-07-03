SUMMARY = "Single-file C++ sanity application for the RV2 image"
DESCRIPTION = "Tiny C++ program used to prove the toolchain, packaging and \
image assembly work end to end."
HOMEPAGE = ""
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://hello-rv2.cpp"

# Modern pattern for file://-only recipes (S = "${WORKDIR}" is deprecated).
S = "${UNPACKDIR}"

# ${LDFLAGS} is mandatory: omitting it fails the GNU_HASH QA check.
do_compile() {
    ${CXX} ${CXXFLAGS} ${LDFLAGS} ${S}/hello-rv2.cpp -o hello-rv2
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 hello-rv2 ${D}${bindir}/hello-rv2
}
