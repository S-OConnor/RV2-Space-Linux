# RV2 Space Linux

A Yocto/[kas](https://kas.readthedocs.io/) starter repository for building
Space-Grade-Linux-style images for the **Orange Pi RV2** (RISC-V 64,
SpacemiT Ky X1 / K1 family SoC). It wires together the upstream RISC-V BSP
layer, the [ELISA](https://elisa.tech/) [meta-sgl](https://github.com/elisa-tech/meta-sgl)
policy layer, and a small local product layer (distro, image, packagegroup,
and a sample app) into a build you can reproduce with a couple of commands.

This repo is a **starter/reference project**, not an official Space Grade
Linux (SGL) product or an ELISA deliverable. SGL itself is still young (see
[`docs/sgl-integration-notes.md`](docs/sgl-integration-notes.md) for exactly
what it provides today); this repo tracks it honestly, including a temporary
compatibility shim, rather than pretending the integration is further along
than it is.

## Hardware target

| | |
|---|---|
| Board | Orange Pi RV2 |
| SoC | SpacemiT Ky X1 / K1 family (RISC-V 64) |
| BitBake `MACHINE` | `orangepi-rv2-mainline` (from `meta-riscv`) |
| Custom `DISTRO` | `rv2-sgl` (from `layers/meta-company`) |
| Image target | `rv2-sgl-image` (from `layers/meta-company`) |

## Repository layout

```
rv2-space-linux/
├── README.md
├── docs/
│   ├── boot-flow.md              # RV2 boot chain, artifacts, flashing rationale
│   ├── layer-stack.md            # layer ownership, branch matrix, priorities
│   └── sgl-integration-notes.md  # what meta-sgl provides today vs. placeholders
├── kas/
│   ├── include/
│   │   └── rv2-base.yml          # shared base: repos, machine, distro, target
│   ├── rv2-dev.yml               # + dev conveniences (root login, tools-debug)
│   ├── rv2-release.yml           # hardening candidates commented, reproducibility notes
│   └── rv2-sdk.yml               # includes rv2-release.yml, task: populate_sdk
├── layers/
│   └── meta-company/             # product layer (distro, image, packagegroup, app)
│       ├── conf/
│       │   ├── layer.conf
│       │   └── distro/rv2-sgl.conf
│       ├── recipes-apps/hello-rv2/
│       └── recipes-core/
│           ├── images/rv2-sgl-image.bb
│           └── packagegroups/packagegroup-rv2-sgl-base.bb
└── scripts/
    ├── build-dev.sh
    ├── build-release.sh
    ├── build-sdk.sh
    ├── flash-sd.sh
    └── kas-container             # vendored from kas 5.4 (runs kas in a container)
```

`meta-riscv` and `meta-sgl` are not vendored in this repo — kas fetches them
into `layers/` per the pins in `kas/include/rv2-base.yml`.

## Prerequisites

The build is fully containerized — **nothing Yocto-related is installed on
the host**. Builds run inside the official kas container image
([`ghcr.io/siemens/kas/kas`](https://kas.readthedocs.io/en/latest/userguide/kas-container.html)),
driven by the vendored [`scripts/kas-container`](scripts/kas-container)
wrapper (from kas release 5.4, MIT-licensed). The image ships a complete
Yocto build host including a current `kas`, so the config-header-version
requirements (v14 for this repo's files, v16 for `meta-sgl`'s `diskmon.yml`
fragment) are covered.

You need:

- **Docker or Podman.** `kas-container` prefers Docker; set
  `KAS_CONTAINER_ENGINE=podman` to force Podman.
- Roughly 100 GB of free disk and a many-core machine — first builds compile
  a full toolchain and root filesystem from source.
- `bmaptool` on the host (recommended) for fast, checksum-verified flashing —
  see [Flashing](#flashing). Flashing runs on the host, not in the
  container, because it needs raw access to the SD-card block device.

If you prefer a host-installed `kas` instead of the container, install it
with `pip install kas` and run the build scripts with `KAS_NATIVE=1`.

## Quickstart

```sh
git clone <this-repo-url> rv2-space-linux
cd rv2-space-linux
./scripts/build-dev.sh
```

This repository must remain a git repository (it is one) — `kas` resolves
the local `meta-company` layer from it. The first build fetches many
gigabytes of source (oe-core, meta-openembedded, meta-riscv, meta-sgl, plus
the kernel and toolchain sources bitbake pulls in) and can take hours; the
resulting `build/` directory runs to tens of gigabytes.

Artifacts land in `build/tmp/deploy/images/orangepi-rv2-mainline/`. Once the
build finishes:

```sh
sudo ./scripts/flash-sd.sh /dev/sdX
```

replacing `/dev/sdX` with the SD card's whole-disk device (not a partition —
use `lsblk` to confirm). See [Flashing](#flashing) below.

## Build variants

| Variant | kas file | What differs | Output |
|---|---|---|---|
| Development | `kas/rv2-dev.yml` | Adds `EXTRA_IMAGE_FEATURES` for root login without a password (`allow-empty-password allow-root-login empty-root-password post-install-logging`) plus `tools-debug`. (Yocto 6.0 removed the old `debug-tweaks` meta-feature — the split features are listed explicitly.) Dev only, never for anything network-reachable or flight-representative. | Root filesystem image in `deploy/images/` |
| Release | `kas/rv2-release.yml` | Debug features off. Carries commented-out hardening candidates (`read-only-rootfs`, `cve-check`) and reproducibility notes (`kas lock`, commit pinning) for when those are exercised. | Root filesystem image in `deploy/images/` |
| SDK | `kas/rv2-sdk.yml` | Includes `rv2-release.yml` and sets `task: populate_sdk`. | Cross-development SDK installer in `deploy/sdk/` |

Run them with `scripts/build-dev.sh`, `scripts/build-release.sh`, or
`scripts/build-sdk.sh`. Each is a thin wrapper that runs `kas build` inside
the official kas container via `scripts/kas-container`; extra arguments pass
straight through to `kas` (e.g. `./scripts/build-dev.sh --update`). Set
`KAS_NATIVE=1` to use a host-installed `kas` instead.

## Where the output lands

- Root filesystem images (dev/release): `build/tmp/deploy/images/orangepi-rv2-mainline/`
- SDK installer: `build/tmp/deploy/sdk/*.sh`

## Flashing

Flashing this board is a **two-step process** — see
[`docs/boot-flow.md`](docs/boot-flow.md) for the full rationale:

1. Write the compressed root filesystem image
   (`rv2-sgl-image-orangepi-rv2-mainline*.rootfs.wic.gz`) to the card, using
   `bmaptool` if available (fast, checksum-verified) or `dd` as a fallback.
2. Write `bootinfo_sd.bin` to the very start of the card — the SpacemiT boot
   ROM reads this header from the card start, and **the card will not boot
   without it**. This must happen *after* step 1, because step 1 overwrites
   the start of the disk.

`scripts/flash-sd.sh` automates both steps, with guards against flashing a
partition, a mounted device, or a missing `bootinfo_sd.bin`, and an explicit
`yes` confirmation prompt before writing anything:

```sh
sudo ./scripts/flash-sd.sh /dev/sdX
```

## Branch/version pinning

There is **no `poky` combo repository for Yocto 5.3 and later** (poky's last
branch is `walnascar`). This repo assembles the equivalent set of layers
directly, mirroring how `meta-riscv` pins its own `kas` configs:

| Repo | Branch/tag | Role |
|---|---|---|
| bitbake | `2.18` | Build tool (not a layer) |
| openembedded-core | `wrynose` | Layer `meta` |
| meta-yocto | `wrynose` | Layer `meta-poky` (provides `poky.conf`, required by `rv2-sgl.conf`) |
| meta-openembedded | `wrynose` | Layers `meta-oe`, `meta-python`, `meta-networking` |
| meta-riscv | `wrynose` | BSP: `orangepi-rv2-mainline` machine, kernel, U-Boot config |
| meta-sgl | `main` (sublayer `meta-sgl-core`) | Space-grade policy/features layer; see the compatibility shim note below |
| layers/meta-company | (local, this repo) | Product layer: `rv2-sgl` distro, `rv2-sgl-image`, packagegroup, `hello-rv2` |

Yocto series: **wrynose (6.0)**, the current LTS, supported until April
2030. `whinlatter` (5.3) is already end-of-life. `scarthgap` (5.0 LTS) does
not contain the RV2 machine at all — `orangepi-rv2-mainline.conf` only
exists on `meta-riscv`'s `wrynose`, `whinlatter`, and `master` branches.

`meta-sgl-core`'s published `LAYERSERIES_COMPAT` does not yet list
`wrynose`, so this repo applies a temporary compatibility shim in
`kas/include/rv2-base.yml`. See
[`docs/sgl-integration-notes.md`](docs/sgl-integration-notes.md) for what it
does and when to remove it.

## Known limitations

- Mainline kernel bring-up for the RV2 landed around **Linux 6.18**.
  Headless/server use works; GPU/NPU and some peripherals are not expected
  to work yet.
- Mainline U-Boot has no RV2 defconfig upstream yet, so the build uses the
  `bananapi-f3_defconfig` as a workaround until one lands.
- `meta-sgl-core` currently ships configuration only (no recipes, no
  packagegroups) — see `docs/sgl-integration-notes.md` for the current
  inventory and what's still a placeholder in this repo.

## Further reading

- [`docs/boot-flow.md`](docs/boot-flow.md) — the RV2 boot chain and flashing details
- [`docs/layer-stack.md`](docs/layer-stack.md) — layer ownership and the branch matrix
- [`docs/sgl-integration-notes.md`](docs/sgl-integration-notes.md) — SGL integration status, real vs. placeholder
- `meta-riscv`'s `docs/orangepi-rv2-mainline.md` — upstream BSP boot-flow documentation
- [github.com/elisa-tech/meta-sgl](https://github.com/elisa-tech/meta-sgl) — the SGL layer
- [sgl.elisa.tech](https://sgl.elisa.tech) — the Space Grade Linux project
