#!/usr/bin/env bash
# Build the cross SDK installer for rv2-sgl-image (release configuration).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"   # kas resolves the work dir from CWD: sources land in layers/, build output in build/

# All build tooling runs inside the official kas container image
# (ghcr.io/siemens/kas/kas) via the vendored scripts/kas-container wrapper --
# nothing besides Podman or Docker needs to be installed on the host.
# This project defaults to podman; export KAS_CONTAINER_ENGINE=docker to use
# docker instead. Set KAS_NATIVE=1 to use a host-installed `kas`.
export KAS_CONTAINER_ENGINE="${KAS_CONTAINER_ENGINE:-podman}"
if [[ "${KAS_NATIVE:-0}" == "1" ]]; then
    if ! command -v kas >/dev/null 2>&1; then
        echo "error: KAS_NATIVE=1 but 'kas' was not found on the host." >&2
        exit 1
    fi
    KAS_CMD=(kas)
else
    if ! command -v "${KAS_CONTAINER_ENGINE}" >/dev/null 2>&1; then
        echo "error: container engine '${KAS_CONTAINER_ENGINE}' not found (required by scripts/kas-container)." >&2
        echo "       Install it, set KAS_CONTAINER_ENGINE to an available engine, or set KAS_NATIVE=1 to use a host-installed kas." >&2
        exit 1
    fi
    KAS_CMD=("${REPO_ROOT}/scripts/kas-container")
fi

# kas/rv2-sdk.yml sets `task: populate_sdk`, so plain `kas build` runs
# bitbake rv2-sgl-image -c populate_sdk
echo "note: the SDK installer will land in build/tmp/deploy/sdk/*.sh"
exec "${KAS_CMD[@]}" build kas/rv2-sdk.yml "$@"   # extra args pass through (e.g. --update)
