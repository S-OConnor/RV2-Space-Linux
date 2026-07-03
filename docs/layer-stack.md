# Layer Stack

How the layers in this build fit together, who owns what, and how to move
the whole stack to a newer Yocto series.

## Stack, bottom-up

```
┌─────────────────────────────────────────────────────────────┐
│ layers/meta-company           product layer (this repo)     │
│   distro rv2-sgl, rv2-sgl-image, packagegroup-rv2-sgl-base,  │
│   hello-rv2                                                  │
├─────────────────────────────────────────────────────────────┤
│ meta-sgl-core (from meta-sgl) policy layer — PLACEHOLDER     │
│   today: conf/layer.conf only, no recipes                    │
├─────────────────────────────────────────────────────────────┤
│ meta-riscv                    BSP layer                      │
│   orangepi-rv2-mainline machine, linux-mainline-k1 kernel,   │
│   U-Boot config                                               │
├─────────────────────────────────────────────────────────────┤
│ meta-oe / meta-python /       (from meta-openembedded)       │
│ meta-networking                                               │
├─────────────────────────────────────────────────────────────┤
│ meta-poky (from meta-yocto)   provides poky.conf              │
├─────────────────────────────────────────────────────────────┤
│ meta (from openembedded-core) base OE layer                  │
├─────────────────────────────────────────────────────────────┤
│ bitbake                       build tool, not a layer        │
└─────────────────────────────────────────────────────────────┘
```

## Who owns what

| Owns | Layer |
|---|---|
| `MACHINE` (`orangepi-rv2-mainline`), kernel, U-Boot config | `meta-riscv` |
| `DISTRO` (`rv2-sgl`), `rv2-sgl-image`, `packagegroup-rv2-sgl-base`, `hello-rv2` | `layers/meta-company` |
| Space-grade policy/features (hardening, safety supervision, updates, observability) | `meta-sgl` — **future**; today `meta-sgl-core` ships configuration only |

## meta-riscv is the BSP; meta-sgl is not

It's worth being explicit about this because the two layers can look
similar (both are "board/platform adjacent"), but they play different
roles:

- **`meta-riscv` is the BSP.** It owns the `orangepi-rv2-mainline` machine
  configuration, the kernel (`linux-mainline-k1`), and the U-Boot
  configuration (currently the `bananapi-f3_defconfig` workaround — see
  [`docs/boot-flow.md`](boot-flow.md)). Nothing else in this stack knows how
  to boot the board; that knowledge lives here.
- **`meta-sgl` is not a BSP.** It's a policy/feature layer aimed at giving
  space-grade Linux distros a common set of hardening, safety-supervision,
  update, and observability building blocks — analogous to what Automotive
  Grade Linux (AGL) does for automotive. It has no opinion on which board
  you're running; it layers on top of whatever BSP you bring. As of July
  2026 its `meta-sgl-core` sublayer contains only `conf/layer.conf` — see
  [`docs/sgl-integration-notes.md`](sgl-integration-notes.md) for the full,
  current inventory of what it does and doesn't provide.

## Branch matrix

| Repo | Branch/tag | Layer(s) |
|---|---|---|
| bitbake | `2.18` | — (build tool) |
| openembedded-core | `wrynose` | `meta` |
| meta-yocto | `wrynose` | `meta-poky` |
| meta-openembedded | `wrynose` | `meta-oe`, `meta-python`, `meta-networking` |
| meta-riscv | `wrynose` | BSP (machine, kernel, U-Boot config) |
| meta-sgl | `main` | `meta-sgl-core` (policy, placeholder today) |
| layers/meta-company | local (this repo) | product layer |

### Why there's no single "poky" pin

Historically, `poky` was a combo repository bundling bitbake + oe-core +
meta-yocto, and people pinned one branch for all three. **There is no
`poky` combo repository for Yocto 5.3 and later** — its last branch is
`walnascar`. From wrynose (6.0) onward, the three pieces are tracked
separately, as in the table above: bitbake as a build tool on its own
branch (`2.18`), openembedded-core (layer `meta`) on `wrynose`, and
meta-yocto (layer `meta-poky`, which provides `poky.conf` — required by
`rv2-sgl.conf`'s `require conf/distro/poky.conf`) also on `wrynose`. This
repo's `kas` configuration pins them individually, mirroring the pattern
`meta-riscv` itself uses in its own `kas` files.

### Yocto series notes

- Series in use: **wrynose (6.0)**, the current LTS, supported until April
  2030.
- `whinlatter` (5.3) is already end-of-life.
- `scarthgap` (5.0 LTS) does **not** contain the RV2 machine at all.
- `orangepi-rv2-mainline.conf` exists on `meta-riscv` branches `wrynose`,
  `whinlatter`, and `master`.
- `meta-sgl`'s SGL CI currently targets `scarthgap` and the BeagleV-Fire
  reference board, not `wrynose` or the RV2 — one reason this repo carries
  a temporary compatibility shim (see
  [`docs/sgl-integration-notes.md`](sgl-integration-notes.md)).

## Layer priority

`layers/meta-company/conf/layer.conf` sets
`BBFILE_PRIORITY_meta-company = "10"`. This is comfortably above the
default-priority upstream layers, so recipes and `.bbappend`s in this repo's
product layer win in the usual BitBake priority-ordering sense without
needing further tuning.

## How to bump the Yocto series

There is a single anchor for the series pin, plus two places that must be
re-checked whenever it moves:

1. **`kas/include/rv2-base.yml`** — update the branch/tag for
   openembedded-core, meta-yocto, meta-openembedded, and meta-riscv to the
   new series (and bitbake to the matching tool release).
2. **`layers/meta-company/conf/layer.conf`** — update
   `LAYERSERIES_COMPAT_meta-company` to include the new series.
3. **Re-check the `meta-sgl-core` compatibility shim** — `meta-sgl-core`'s
   published `LAYERSERIES_COMPAT` may or may not include the new series by
   the time you bump. If it does, drop the
   `LAYERSERIES_COMPAT_meta-sgl-core:append` shim in
   `kas/include/rv2-base.yml`. If it doesn't yet, keep (or update) the shim
   and re-verify meta-sgl's `kas/` fragments (in particular `diskmon.yml`'s
   `kas` header-version requirement) still work against the newer tooling.

See [`docs/sgl-integration-notes.md`](sgl-integration-notes.md) for full
detail on the shim itself.
