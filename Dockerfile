# Build container for rv2-space-linux.
#
# You do NOT need this file for a normal build: scripts/build-*.sh pull the
# stock kas image (ghcr.io/siemens/kas/kas) automatically via
# scripts/kas-container. Build this image instead when you want to
#   - bake extra host tools into the builder,
#   - add corporate CA certificates or proxy configuration,
#   - mirror a fixed builder image into your own registry for CI.
#
# Build it:
#     docker build -t rv2-space-linux-builder .
#     # or: podman build -t rv2-space-linux-builder .
#
# Use it (scripts/kas-container honours KAS_CONTAINER_IMAGE):
#     KAS_CONTAINER_IMAGE=rv2-space-linux-builder ./scripts/build-dev.sh
#
# ---------------------------------------------------------------------------
# ADJUST HERE on a kas upgrade: keep this tag in lockstep with the vendored
# wrapper (scripts/kas-container, KAS_CONTAINER_SCRIPT_VERSION -- currently
# 5.4). The kas image ships the complete Yocto build-host toolchain plus a
# matching kas, so the config-header-version requirements (v14 for this
# repo's kas files, v16 for meta-sgl's diskmon.yml fragment) stay covered.
#
# For reproducible release builds, pin the digest instead of the tag:
#     FROM ghcr.io/siemens/kas/kas@sha256:<digest>
# (resolve it with: docker manifest inspect ghcr.io/siemens/kas/kas:5.4)
# ---------------------------------------------------------------------------
FROM ghcr.io/siemens/kas/kas:5.4

# kas-container runs the container with --user=root and the kas entrypoint
# drops to the build user (remapped to the host UID/GID) itself, so staying
# root here is correct -- do not add a USER directive, and do not override
# ENTRYPOINT/CMD, or the wrapper's user mapping breaks.
USER root

# Everything a Yocto wrynose build needs is already in the base image; add
# only repo-specific extras here. bmap-tools lets you inspect/verify the
# generated .wic.gz/.bmap artifacts from inside the container (actual SD-card
# flashing still happens on the host -- see scripts/flash-sd.sh).
RUN apt-get update \
    && apt-get install --no-install-recommends -y \
        bmap-tools \
    && rm -rf /var/lib/apt/lists/*
