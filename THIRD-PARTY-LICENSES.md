# Third-Party Notices

SortNStock is licensed under the **MIT Licence** (see [`LICENSE`](LICENSE)).

SortNStock **links against** the mods below at runtime and compiles against their published
artifacts. It does **not** copy, port or embed any of their source code. All API usage was
written from the published artifacts and their public signatures; no source files from these
projects were copied into this repository.

---

## MaLiLib — required dependency

- Project: MaLiLib (sakura-ryoko fork of masa's MaLiLib)
- Source: https://github.com/sakura-ryoko/malilib
- Reference: branch `LTS/26.1`, commit `e06d6b08d8ef03dc0e12673e62a7a92720d658d4`
- Artifact: `fi.dy.masa.malilib:malilib-fabric-26.1.2:0.28.12`
- Licence: **GNU Lesser General Public License v3.0 (LGPLv3)**

SortNStock uses MaLiLib's config system, config GUI, hotkey system and inventory utilities through
their public API. MaLiLib is **not** bundled or redistributed by SortNStock; users install it
separately. Because MaLiLib is LGPLv3 and is used as a separate, dynamically linked library, the
LGPL's copyleft requirements do not extend to SortNStock's own source.

## Litematica — optional dependency

- Project: Litematica (sakura-ryoko fork of masa's Litematica)
- Source: https://github.com/sakura-ryoko/litematica
- Reference: branch `LTS/26.1`, commit `edf9ced2af2244d56fc20086883a014bf051b1b8`
- Artifact: `fi.dy.masa.litematica:litematica-fabric-26.1.2:0.27.14`
- Licence: **GNU Lesser General Public License v3.0 (LGPLv3)**

SortNStock reads Litematica's config values and injects a single Mixin into one of its methods at
runtime. Litematica is **not** bundled or redistributed by SortNStock. It is declared as a
`recommends`, every Litematica type reference is isolated behind a runtime mod-loaded check, and
the Mixin is skipped entirely when Litematica is absent.

## QuickShulker — optional dependency

- Project: QuickShulker (MoRanpcy's fork of kyrptonaught's QuickShulker)
- Source: https://github.com/MoRanpcy/quickshulker
- Reference: branch `26.1`, commit `085cec74393640e7bf10730c1f3b3a0152cbc24f`
- Artifact: `net.kyrptonaught:quickshulker:3.1.0-26.1`
- Licence: **MIT Licence**

SortNStock calls QuickShulker's public `OpenShulkerPacket.sendOpenPacket(int)` to open a carried
shulker box. QuickShulker is **not** bundled or redistributed by SortNStock. It is declared as a
`recommends` and is runtime-guarded.

## Fabric API / Fabric Loader / Minecraft

- Fabric API: https://github.com/FabricMC/fabric-api — Apache License 2.0
- Fabric Loader: https://github.com/FabricMC/fabric-loader — Apache License 2.0
- Minecraft: property of Mojang Studios. Not redistributed by this project.

## Build-time only

`com.google.code.findbugs:jsr305` (Apache License 2.0) is used as a `compileOnly` annotation
dependency and is not packaged.

---

## Note on the reconnaissance clones

During development the three prerequisite repositories above were cloned locally (into a
git-ignored `_recon/` directory) **solely to read their public API signatures**. That directory is
not part of the build, is not packaged, and none of its code was copied into SortNStock.
