# Boot Flow

High-level description of how the Orange Pi RV2 (SpacemiT Ky X1 / K1
family) goes from power-on to a running mainline Linux, and how the
artifacts produced by this repo's build map onto that chain. This is a
summary derived from `meta-riscv`'s own `docs/orangepi-rv2-mainline.md`;
consult that file in `meta-riscv` for the authoritative, up-to-date detail.

## The chain

```
 SpacemiT     FSBL.bin      boot-bundle.itb        mainline       OpenSBI      mainline
 boot ROM  →  (vendor    →  (FIT image:         →  U-Boot      →  (RISC-V   →  Linux
 (on-chip)    U-Boot         u-boot-nodtb.bin,      (bananapi-     SBI,          (kernel
              tree)          fw_dynamic.bin,        f3_defconfig   supervisor    Image +
                              u-boot.dtb,            workaround)    mode)         DTB)
                              kernel Image,
                              k1-orangepi-rv2.dtb)
```

## Stage by stage

1. **SpacemiT boot ROM.** Fixed, on-chip first code to run. It reads a boot
   header (`bootinfo_sd.bin`, see below) from the start of the boot medium
   and uses it to locate and load the first-stage bootloader.

2. **FSBL.bin — first-stage bootloader.** Built from SpacemiT's vendor
   U-Boot tree (not mainline U-Boot). Loaded directly by the boot ROM. Its
   job is to bring up enough of the SoC (clocks, DRAM, etc.) to load and
   jump into the next stage.

3. **`boot-bundle.itb`.** A U-Boot FIT (Flattened Image Tree) image loaded
   into RAM by the FSBL. It bundles everything mainline needs to take over:
   - `u-boot-nodtb.bin` — mainline U-Boot proper
   - `fw_dynamic.bin` — OpenSBI's dynamic firmware
   - `u-boot.dtb` — device tree for mainline U-Boot
   - the kernel `Image`
   - `k1-orangepi-rv2.dtb` — device tree for the kernel

4. **Mainline U-Boot.** Runs from the FIT image. Currently built with the
   **`bananapi-f3_defconfig`** as a workaround, because the RV2 has no
   mainline U-Boot defconfig of its own yet. It hands off to OpenSBI, which
   in turn starts the kernel.

5. **OpenSBI.** The RISC-V Supervisor Binary Interface implementation — the
   RISC-V analogue of what TF-A provides on Arm platforms. Runs in M-mode
   and provides the SBI that supervisor-mode software (the kernel) relies
   on.

6. **Mainline Linux.** RV2 support landed in mainline around **kernel
   6.18**. This is where userspace (systemd, the packages installed by this
   repo's image) starts.

### Where each artifact comes from

| Stage | Source |
|---|---|
| `FSBL.bin` | SpacemiT's vendor U-Boot tree (not mainline) |
| `boot-bundle.itb` contents (U-Boot, OpenSBI, kernel, DTBs) | Mainline sources, built by this repo's Yocto build via `meta-riscv` |
| Mainline U-Boot | Mainline U-Boot, `bananapi-f3_defconfig` (workaround) |
| OpenSBI | Mainline OpenSBI |
| Kernel | Mainline Linux (~6.18+) |

## Why `bootinfo_sd.bin` is mandatory, and why it's written last

The SpacemiT boot ROM does not scan a filesystem for a bootloader — it reads
a fixed boot-info header from the very start of the boot medium (the SD
card) to find `FSBL.bin`. Without that header at offset 0, the boot ROM has
nothing to load, and **the board will not boot**, regardless of how correct
the rest of the image is.

This is also why flashing is a strict two-step, ordered process:

1. Write the root filesystem image (`*.rootfs.wic.gz`) to the card.
2. Write `bootinfo_sd.bin` to the start of the card (offset 0, no seek).

Step 2 must happen **after** step 1, because writing the `wic` image
overwrites the beginning of the disk — including wherever a
previously-written boot header was. If you flash `bootinfo_sd.bin` first and
the root filesystem image second, you'll end up with a card that has a
correct filesystem but no way for the boot ROM to start anything.
`scripts/flash-sd.sh` enforces this ordering automatically; see the
[README](../README.md#flashing) for the two-step summary.

## Artifact inventory

All artifacts land in
`build/tmp/deploy/images/orangepi-rv2-mainline/`, produced by the
`orangepi-rv2-mainline` machine configuration's `IMAGE_FSTYPES` plus the
board's boot-firmware recipes.

| Artifact | Purpose |
|---|---|
| `rv2-sgl-image-orangepi-rv2-mainline*.rootfs.wic.gz` | Compressed, partitioned root filesystem/boot image (`wic.gz`) — written to the card first |
| `*.wic.bmap` | Block map for the `wic.gz` image, used by `bmaptool` for fast, checksum-verified writes |
| `bootinfo_sd.bin` | Boot-ROM header; written to the start of the card **after** the `wic.gz` image |
| `boot-bundle.itb` | FIT image containing mainline U-Boot, OpenSBI, kernel `Image`, and the kernel DTB; embedded in / loaded via the boot chain above |
| `*.ext4` | Raw root filesystem image (alternate to `wic.gz`, e.g. for other deployment methods) |
| `*.tar.zst` | Root filesystem as a compressed tarball (e.g. for extraction into an existing partition layout) |

## Known limitations of the mainline bring-up

- Mainline kernel support for the RV2 is recent (~6.18). Headless/server
  workloads are expected to work; GPU/NPU and some peripherals are not
  expected to work yet.
- There is no upstream mainline U-Boot defconfig for the RV2 yet, so builds
  substitute `bananapi-f3_defconfig`. This is a workaround, not a
  board-specific configuration — revisit it once an RV2 defconfig lands
  upstream.
