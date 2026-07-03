# SGL Integration Notes

An honest accounting of what Space Grade Linux (SGL) integration in this
repo actually consists of today, versus what's a marked placeholder for
later. Written to be re-read and updated as `meta-sgl` matures.

## 1. What SGL is

Space Grade Linux is a Special Interest Group (SIG) under
[ELISA](https://elisa.tech/) (Enabling Linux in Safety Applications),
modeled on Automotive Grade Linux (AGL): a shared set of policy, packages,
and features for building Linux distributions suitable for space
applications, rather than a single fixed distro. It is currently working
toward becoming a standalone Linux Foundation project. Its reference
hardware target and CI target today is the **BeagleV-Fire** board, with CI
running against the **scarthgap** Yocto series. Project site:
[sgl.elisa.tech](https://sgl.elisa.tech); layer:
[github.com/elisa-tech/meta-sgl](https://github.com/elisa-tech/meta-sgl).

## 2. What `meta-sgl` provides today (verified July 2026)

`meta-sgl`'s `meta-sgl-core` sublayer — the piece this repo depends on —
currently contains **only `conf/layer.conf`**. There are no recipes, no
packagegroups, and no `.bbappend`s in it yet.

`meta-sgl`'s real substance right now is a set of composable `kas`
fragments in its `kas/` directory, which downstream projects can pull in
selectively. As inventoried:

| Fragment | Status / caveat |
|---|---|
| `kas/common.yml` | Contains dev-only, **insecure** `IMAGE_FEATURES` — do not adopt as-is for a release build |
| `kas/systemd.yml` | Uses the legacy `VIRTUAL-RUNTIME_init_manager` init-selection pattern, superseded by the modern `INIT_MANAGER` variable (this repo's distro conf uses `INIT_MANAGER` directly — see §3) |
| `kas/diskmon.yml` | Uses `kas` config header version 16 — requires a newer `kas` than this repo's other configs (header v14) |
| `kas/limit-pressure.yml` | Present; not yet evaluated/adopted here |
| `kas/sgl/sgl.yml` | Present; not yet evaluated/adopted here |
| `kas/sgl/clang.yml` | Present; not yet evaluated/adopted here |
| `kas/sgl-scarthgap-*.yml` | Top-level configs targeting the `scarthgap` series |
| Space-ROS variants | Present in the repo; not evaluated here |

`meta-sgl`'s `LAYERSERIES_COMPAT` for `meta-sgl-core` is currently:

```
LAYERSERIES_COMPAT = "kirkstone scarthgap walnascar whinlatter"
```

Note `wrynose` is **not** in that list yet.

## 3. What this repo does about it

- **Wires in the integration point.** `meta-sgl-core` is included in
  `BBLAYERS` (via `kas/include/rv2-base.yml`) even though it currently
  contributes nothing but its `layer.conf`. This means the moment
  `meta-sgl-core` starts shipping recipes/packagegroups/policy includes,
  they're one `kas --update` away from being usable here — see the
  placeholder comments already left in `layers/meta-company` (§4).

- **A temporary `LAYERSERIES_COMPAT` shim.** Because `meta-sgl-core`
  doesn't declare `wrynose` support yet, this repo forces the issue in
  `kas/include/rv2-base.yml`'s `bblayers_conf_header`:

  ```
  LAYERSERIES_COMPAT_meta-sgl-core:append = " wrynose"
  ```

  The shim must live in `bblayers.conf`, not `local.conf`: BitBake checks
  layer compatibility while parsing the `layer.conf` files, which happens
  *before* `local.conf` is read — an append placed in `local.conf` is
  verified not to work. `bblayers.conf` is parsed first, so the append is
  already in effect when the check runs. (The shim also sets
  `BBFILE_PATTERN_IGNORE_EMPTY_meta-sgl-core = "1"` to silence the harmless
  "no bb files matched" warning from a layer that ships no recipes yet.)

  This is a **stopgap, not an endorsement** that `meta-sgl-core` has been
  validated against `wrynose` — it just stops BitBake from refusing to
  parse the layer over a compatibility-string mismatch, which matters here
  only because `meta-sgl-core` has no recipes to actually break yet.
  **Remove this shim once upstream `meta-sgl` declares `wrynose` in its own
  `LAYERSERIES_COMPAT`**, and re-verify layer compatibility from scratch at
  that point rather than assuming the shim is still harmless.

- **Commit-pinning guidance.** `meta-sgl` is tracked at branch `main`
  (there is no tagged release yet). A moving branch is fine for
  experimentation but not for a build you need to reproduce — pin `meta-sgl`
  to a specific commit (via `kas lock` or an explicit `refspec` in
  `kas/include/rv2-base.yml`) before relying on this repo for anything
  beyond exploration, and bump that pin deliberately rather than tracking
  `main` unattended.

## 4. Placeholder map

Where SGL themes currently show up as explicit, commented placeholders in
this repo (search these files for `SGL placeholder` / `SGL integration
placeholders` comments):

| SGL theme | Placeholder location(s) |
|---|---|
| Hardening / security | `layers/meta-company/conf/distro/rv2-sgl.conf` (commented `DISTRO_FEATURES:append = " seccomp pam"`, and the `sgl-policy.inc` require line); `kas/rv2-release.yml` (commented `read-only-rootfs`, `cve-check` hardening candidates) |
| Safety & watchdog supervision | `layers/meta-company/conf/distro/rv2-sgl.conf` (comment noting watchdog coverage today comes from the `watchdog` package / `systemd`'s `RuntimeWatchdogSec=`, and that SGL safety-monitor/supervision hooks will land here); `watchdog` + `watchdog-config` packages already present in `packagegroup-rv2-sgl-base` as the current, non-placeholder baseline |
| Updates (OTA / A-B) | `layers/meta-company/conf/distro/rv2-sgl.conf` (comment reserving space for a future OTA strategy, e.g. RAUC/SWUpdate A/B, to be selected with SGL guidance) |
| Observability | `layers/meta-company/conf/distro/rv2-sgl.conf` (comment reserving space for a logging/telemetry stack); `meta-sgl`'s `kas/diskmon.yml` and `kas/limit-pressure.yml` fragments as candidate optional future `kas` includes |
| SGL packagegroups | `layers/meta-company/recipes-core/images/rv2-sgl-image.bb` (commented `IMAGE_INSTALL += "packagegroup-sgl-core"`, noting `meta-sgl-core` ships no packagegroups today); `layers/meta-company/recipes-core/packagegroups/packagegroup-rv2-sgl-base.bb` (trailing comment reserving a spot for future SGL packagegroups — hardening, safety supervision, update client, observability agents) |

## 5. How to adopt real SGL pieces later

1. **Watch `meta-sgl`** (github.com/elisa-tech/meta-sgl) for `meta-sgl-core`
   gaining recipes/packagegroups, and for `LAYERSERIES_COMPAT` picking up
   `wrynose`.
2. **Bump compatibility** — once upstream declares `wrynose`, remove the
   `LAYERSERIES_COMPAT_meta-sgl-core:append` shim in
   `kas/include/rv2-base.yml` (§3) and re-test the build clean.
3. **Replace placeholders one theme at a time** using the map in §4 —
   e.g. when `meta-sgl-core` ships a hardening policy include, swap the
   commented `sgl-policy.inc` `require` line in `rv2-sgl.conf` for a real
   one, rather than layering ad hoc equivalents on top.
4. **Evaluate the `kas` fragments deliberately, not wholesale.** `common.yml`
   in particular ships dev-only insecure features that must not leak into
   `rv2-release.yml`; `systemd.yml`'s legacy init pattern should stay
   superseded by this repo's `INIT_MANAGER` approach unless upstream
   changes it; `diskmon.yml` needs a `kas` header-v16-capable toolchain
   before it can be included at all.
5. **Consider the Space-ROS variants** in `meta-sgl` if/when this repo's
   scope grows to include robotics/ROS workloads — out of scope for the
   current headless starter image.
