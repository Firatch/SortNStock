# SortNStock

English | [简体中文](README.zh_cn.md)

A client-side Fabric mod that sorts containers and keeps your hotbar stocked. It integrates with
[Litematica](https://github.com/sakura-ryoko/litematica) and
[QuickShulker](https://github.com/KyrptonaughtMC/QuickShulker) when they are installed.

Every movement is an ordinary container click, so the server validates it the same way it validates
a click you make yourself. The mod does not create items and does not place blocks. Nothing is
needed on the server.

## Requirements

| | |
|---|---|
| Minecraft | 26.1.x |
| Fabric Loader | 0.19.3+ |
| Java | 25 (26.1 needs 25, not 21) |
| [MaLiLib](https://github.com/sakura-ryoko/malilib) | 0.28.11 – 0.28.x, required |
| Litematica | 0.27.11 – 0.27.x, optional |
| QuickShulker | any 26.1 build, optional |
| Mod Menu | optional, adds a config button to the mod list |

## Install

Put the jar and MaLiLib in `.minecraft/mods`. Litematica, QuickShulker and Mod Menu are only needed
for their respective features.

To build it yourself, run `./gradlew build` with Gradle on Java 25. If Java 25 is not your default
`JAVA_HOME`, set `org.gradle.java.home` in `gradle.properties` or point `JAVA_HOME` at it.

## Hotkeys

| Action | Default |
|---|---|
| Sort the container under the cursor | middle mouse |
| Open the config screen | `T` then `K` |

Four options can also be given a toggle key, unbound by default: Enable Automatic Restocking, Enable
Litematica Integration, Enable Restock From Shulker Boxes and Debug Logging.

Middle mouse is also vanilla's pick block, so one click would trigger both. When a sort is actually
started, the mod suppresses the vanilla action for that click. In creative mode the button is left to
vanilla on a non-empty slot, so clone and pick block keep working.

## Sorting

Hover a slot in a container and press the hotkey. The mod sorts the container that slot belongs to.
Hovering your own inventory sorts the 27 main slots and leaves the hotbar, armour and offhand alone.

- Sort key: registry name, display name, count, durability, components or tags. Always ascending.
- Partial stacks are merged first, then the result is packed towards the front.
- Shulker boxes are kept in the last slots of the container, with a gap in front of them. Inside that
  block they are ordered by fill state (empty, partly filled, full), then colour in creative-inventory
  order, then mixed boxes before pure ones, then by contents.
- Only items you already have are moved. A pair that cannot be moved safely is skipped instead of
  risking the whole container.

## Restocking

The mod tops up your hotbar and/or offhand from items you already carry.

- It triggers when the stack you are holding shrinks, which covers using, placing, eating, throwing
  and drinking. Storing items in a container does not trigger it: you cannot use an item while a GUI
  is open, so that never counts as consumption.
- `restockMinCount` sets the threshold, 0 to 64. At 0 it only refills once the stack is gone.
- The refill goes up to one full stack: 64 for most items, 16 for eggs, snowballs and similar.
- Sources are your inventory first, then the container you have open.
- With QuickShulker installed, an item that is only inside a carried shulker box can be fetched from
  it. The box opens, the items move, the box closes. Its screen stays hidden and its sounds are muted
  so it does not interrupt what you are doing.

## Shulker boxes and pick block

Vanilla pick block only works on a block you already carry. When you press middle mouse on a block you
have none of, the mod opens a carried shulker box that holds it and takes the stack out. Press middle
mouse again to select it.

This uses the same Enable Restock From Shulker Boxes option as restocking. It is skipped in creative
mode, where pick block clones the item instead. Nothing changes when you already carry the block.

## Litematica

With Litematica installed and the integration enabled, the mod hooks Litematica's
`schematicWorldPickBlock`, which runs just before the hotbar slot is switched, so the same right-click
can use the refilled stack.

- It works at that one point only and does not poll on a timer.
- If the required block is already anywhere in your inventory, the mod does nothing. Litematica's pick
  block can select a matching hotbar slot, or pull the item out of your main inventory, so there is
  nothing to add.
- The hotbar slots it may fill come from Litematica's own `pickBlockableSlots` setting.
- When every one of those slots is occupied, restocking swaps instead: the item comes out of the box
  and the stack from the slot with the fewest items goes in.
- While a box is opening, Litematica's easy place is held off for that click, so nothing is placed
  before the item is in your hand.

Fetching from a box takes a few ticks, so the click that asked for it may finish before the refill
does. Press again once the item appears.

## Configuration

All settings live in the MaLiLib config screen, opened with the hotkey, the Mod Menu button or
MaLiLib's own mod list. They are grouped into tabs for sorting, restocking, shulker boxes, Litematica,
debug and hotkeys, plus an overview tab that lists everything.

Every option is described in [`docs/CONFIG-REFERENCE.md`](docs/CONFIG-REFERENCE.md). The mod does not
print to the action bar; feedback is audio only, and diagnostics go to the log behind Debug Logging.

## Known issues

- Sorting and restocking are container clicks. A server that rejects or rate-limits them may disagree
  with what your client shows.
- The Litematica mixins target method signatures from Litematica 0.27.x, so a later release may move
  them. The mixin config is only applied when Litematica is present, so its absence cannot crash the
  game.
- Opening a shulker box needs QuickShulker on the server as well, since it uses a custom payload.
  Without it the feature stays inactive and sends nothing.
- A Minecraft 26.3 port is in progress on the `mc26.3` branch.

## Tested with

| Component | Version |
|---|---|
| Minecraft | 26.1.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.155.3+26.1.2 |
| MaLiLib | 0.28.12 |
| Litematica | 0.27.14 |
| QuickShulker | 3.1.0-26.1 |

More detail is in [`docs/`](docs/): the compatibility reconnaissance, the config reference and a manual
test checklist.

## Licence

MIT, see [`LICENSE`](LICENSE). SortNStock links against MaLiLib and Litematica (LGPLv3) and
QuickShulker (MIT) without copying their code. See [`THIRD-PARTY-LICENSES.md`](THIRD-PARTY-LICENSES.md).
