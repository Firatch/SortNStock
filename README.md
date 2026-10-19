# SortNStock

**English** | [简体中文](README.zh_cn.md)

A client-side Minecraft Fabric mod for **inventory sorting** and **automatic restocking**, with
optional [Litematica](https://github.com/sakura-ryoko/litematica) and
[QuickShulker](https://github.com/KyrptonaughtMC/QuickShulker) integration.

SortNStock is deliberately conservative about what it does:

- **Client-only.** Nothing is required on the server.
- **It never creates items.** No spawning, duplicating or "refilling". Every movement is an ordinary
  container click (`PICKUP` / `SWAP`), so the server validates it exactly as if you had clicked it
  yourself.
- **It never places blocks or touches a schematic.** Placing is Litematica's job; SortNStock only
  puts the required item into a hotbar slot.

---

## Requirements

| | |
|---|---|
| Minecraft | 26.1.x |
| Fabric Loader | 0.19.3+ |
| Java | **25** (26.1 requires it — not 21) |
| [MaLiLib](https://github.com/sakura-ryoko/malilib) | 0.28.11 – 0.28.x (required: config and hotkey system) |
| Litematica | 0.27.11 – 0.27.x (optional: schematic integration) |
| QuickShulker | any 26.1 build (optional: restocking out of shulker boxes) |
| Mod Menu | optional: adds the config button to the mod list |

## Building

```bash
./gradlew build
```

Gradle must run on **Java 25**. If your Java 25 is not the default `JAVA_HOME`, set
`org.gradle.java.home` in `gradle.properties` (or the `JAVA_HOME` environment variable).

## Hotkeys

| Action | Default |
|---|---|
| Sort the container under the cursor | **Middle mouse** |
| Open the config screen | **`T` then `K`** |

These four options also carry an optional toggle key, unbound by default: *Enable Automatic
Restocking*, *Enable Litematica Integration*, *Enable Restock From Shulker Boxes*, *Debug Logging*.

Middle mouse is also vanilla's pick block, so both actions would fire on one click. When a sort
starts, SortNStock suppresses the vanilla action for that click; in creative mode, however, the
middle mouse button is left to vanilla on a non-empty slot so clone / pick block wins.

## Sorting

Hover a container slot and press the hotkey. The mod sorts the container that slot belongs to;
hovering your own inventory sorts its **27 main slots** — never the hotbar, armour or offhand.

- **Sort key**: registry name, display name, count, durability, NBT/components, or tags. Always
  ascending.
- **Always merges** partial stacks into full ones, then **packs the results forwards**.
- **Shulker boxes are reserved into the last slots of the container**, with a gap before them. Within
  that tail block the boxes are ordered by fill state (empty → partly filled → full), then colour
  (creative-inventory order), then mixed-before-pure, then their contents.
- Only items you already own are moved. A pair that cannot be moved safely is skipped rather than
  risking the contents of the container.

## Restocking

Restocking keeps your hotbar and/or offhand stocked with items you already carry.

- **One trigger**: the held stack shrank, i.e. you used, placed, ate, threw or drank something.
  Storing items in a container does not count — items cannot be used while a GUI is open, so that is
  never treated as consumption.
- **Threshold**: `restockMinCount` (0–64). At `0` the rule becomes "top up once the stack runs out".
- **Target**: one full stack of that item — 64 for most, 16 for eggs and snowballs, and so on.
- **Sources**: your inventory first, then the container you currently have open.
- **Optional shulker-box source**: with QuickShulker installed, a needed item that is only inside a
  carried shulker box can be fetched from it. The box is opened, the items are moved out, and the box
  is closed — with its screen hidden and its sounds muted, so it does not interrupt play.

## Litematica integration

With Litematica installed and the integration enabled, SortNStock hooks the head of Litematica's
`schematicWorldPickBlock`, i.e. the moment before the hotbar slot is switched, so the same right-click
can use the restocked stack.

- Hooks in at exactly that one point; it does not poll on a timer.
- **Does nothing if the required block is already anywhere in your inventory** — Litematica's pick
  block can select any hotbar slot holding it, and can pull it out of the main inventory. Restocking
  only fires when pick block has nothing to select, which is also the only case a shulker box can
  help with.
- The hotbar slots it may fill come from Litematica's own `pickBlockableSlots` config.

## Configuration

Everything lives in the MaLiLib-generated config screen: the hotkey, the Mod Menu button, or
MaLiLib's own mod list. 15 options across six tabs — **All / Sorting / Restocking / QuickShulker /
Litematica / Debug / Hotkeys**. Every option is documented in
[`docs/CONFIG-REFERENCE.md`](docs/CONFIG-REFERENCE.md).

## Known limitations

- Sorting and restocking are ordinary container clicks; a server that rejects or rate-limits them may
  disagree with the client.
- The Litematica mixin targets one exact method signature of Litematica 0.27.14. A future release may
  move it. The mixin config is gated on Litematica being present, so a missing Litematica cannot
  crash the mod.
- Restocking out of a shulker box needs QuickShulker **on the server side too**, because opening a box
  is a custom payload. Without it nothing is sent into the void — the feature simply stays inactive.
- Fetching from a shulker box takes a couple of ticks (open → sync → move → close), so the click that
  triggered a Litematica restock may land before the refill completes.

## Compatibility

| Component | Tested reference |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.155.3+26.1.2 |
| MaLiLib | 0.28.12 |
| Litematica | 0.27.14 |
| QuickShulker | 3.1.0-26.1 |

Deeper material lives in [`docs/`](docs/): the compatibility reconnaissance, the configuration
reference, and the manual test checklist.

## Licence

MIT — see [`LICENSE`](LICENSE). SortNStock links against, but does not copy code from, MaLiLib and
Litematica (LGPLv3) and QuickShulker (MIT); see [`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md).
