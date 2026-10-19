# SortNStock — Configuration and Hotkey Reference

All options are registered with **MaLiLib** (`ConfigManager` + `ConfigScreenRegistry`) and are
edited in MaLiLib's generated config GUI. There is **no** Cloth Config, no ModMenu-specific
config screen and no separate hand-rolled JSON system — MaLiLib owns loading, saving,
keybind conflict detection and the UI.

Config file (written by MaLiLib): `config/sortnstock.json`

The GUI has one tab per section below, plus an **All** tab that lists every option and hotkey.
The config screen can be opened three ways: the `openConfigGuiHotkey` hotkey (default `T` then `K`),
the config button in **Mod Menu**, or MaLiLib's own mod list / config switcher.

## Why on/off options are option lists, not booleans

Every on/off option here is stored as a two-entry option list (`ON` / `OFF`) rather than a boolean,
and the button reads **开启 / 关闭** instead of yes/no. (The sort direction used to be an `ASCENDING` / `DESCENDING` option as well; it was removed and every sort is ascending.)

That is not a stylistic choice. MaLiLib renders *every* boolean as a button reading
`malilib.gui.button.true` / `.false` — a global string shared by all MaLiLib mods — and its
`WidgetConfigOption` picks the widget from the closed `ConfigType` enum, with no `IConfigWidget`
style hook a mod could use to supply its own. An option list is the only form whose displayed text
the mod controls. Clicking still switches the value in one click
(`ConfigButtonOptionList` cycles on click; right-click cycles backwards).

A consequence worth knowing: **the JSON values changed type.** `"sortEnabled": true` became
`"sortEnabled": "on"`. A pre-existing config file will have its old boolean values ignored and those
options fall back to their defaults once; they are then written back in the new form.

## This document is the long-form reference, not the tooltips

The in-game tooltips are deliberately kept to one or two short lines. **MaLiLib cannot wrap them:**
its `RenderUtils.drawHoverText` only splits on explicit `\n` and otherwise measures the line against
the *whole screen width*, so anything long is drawn as a single line running off the edge. There is
no wrap width to configure.

The comments are therefore written short, and a `\n` is used where a second line genuinely helps.
The full explanation of each option lives below in this file instead. If you are reading a tooltip
and want the detail, look the option up here.

---

## 1. Sorting — `Sorting`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `sortEnabled` | Option list | `ON` | `ON`, `OFF` | Master switch for the sorting module. |
| `sortKey` | Option list | `REGISTRY_NAME` | see below | Primary sort key. |

**Fixed behaviour that is not configurable.** Sorting always merges partial stacks into full ones
first and then packs the results forwards towards the start of the container. These used to be
`sortMergeStacks` and `sortCompactItems`; both were removed on request and are now always on, since
a "tidy up" is expected to do both. Their old keys are ignored and dropped on the next save, and the
"reorder in place, keep the gaps" mode no longer exists.

### What sorting covers in the player's own inventory (not configurable)

Hovering anywhere in the player's own inventory sorts **only the 27 main inventory slots**. The
hotbar, the armor slots and the offhand slot are never touched.

This is deliberately fixed behaviour with no option behind it. The hotbar is a hand-arranged
quick-access bar, and having a sort silently rearrange it — or shuffle the tool you are holding out
of reach — is not a preference worth exposing. It also removes the question of what hovering the
hotbar should mean: every part of the player's inventory targets the same 27 slots.

> **Removed option.** This used to be `sortHotbarSeparately`, which chose between "the whole player
> inventory" and "only the hotbar / only the main inventory". It has been deleted, so a stale entry
> for it in an existing `config/sortnstock.json` is simply ignored and dropped on the next save.

> **Renamed key.** This option used to be `sortKeepEmptySlots`, an inverted name that defaulted to
> *keeping* the gaps. Because a persisted config value always overrides a code default, simply
> flipping that default did nothing for anyone with an existing `config/sortnstock.json`. The key
> was therefore renamed: an existing file does not contain `sortCompactItems`, so it picks up the
> new default, and the stale `sortKeepEmptySlots` entry is dropped the next time the config is
> saved. MaLiLib's `ConfigUtils.readConfigBase` only applies keys that are present, so an absent
> key is safe.

### `sortKey` values

| Value | Meaning |
|---|---|
| `REGISTRY_NAME` | Item registry id (e.g. `minecraft:stone`) |
| `DISPLAY_NAME` | Localised display name |
| `COUNT` | Stack size |
| `DURABILITY` | Remaining durability |
| `NBT` | Serialised data components / NBT string |
| `TAG` | Item tag list |

### Ordering rules that are not configurable

**Shulker boxes are placed in the last slots of the container**, whatever the sort key and whichever
direction is chosen. This is not "sorted last among the items" — the boxes are reserved into a tail
block covering the container's final slots, the ordinary items are packed at the front, and any
slots in between stay empty. With 27 slots, 12 ordinary stacks and 9 boxes, the boxes go to slots
18-26 and slots 12-17 are the gap. When the container is too full for a gap the two groups simply
become contiguous and the boxes still end up at the end. Detection goes through the vanilla
`minecraft:shulker_boxes` item tag or a `BlockItem` over a `ShulkerBoxBlock`, so all sixteen dyed
variants and modded shulker boxes are covered.

Compaction is always on, so the reservation always applies: the boxes take the tail block and the
slots before it, if any, stay empty.

**Within that tail block the boxes follow their own priority chain, `1 > 2 > 5 > 3 = 4`:**

1. **Fill state** — empty boxes, then partly filled, then full. "Full" means no room is left: every
   one of the 27 slots is occupied *and* every stack in it is at its own maximum. A box holding 27
   single items is therefore counted as partly filled, not full.
2. **Colour** — the creative-inventory colour order, white, orange, magenta, light blue, yellow,
   lime, pink, gray, light gray, cyan, purple, blue, brown, green, red, black. This is
   `DyeColor`'s declaration order, which is the order the creative inventory lists coloured blocks
   in. The undyed box sorts before all of them.
5. **Mixed before pure** — a box holding more than one kind of item comes before a box holding a
   single kind.
3. **Boxes that are not full** — the one holding least comes first. "Least" is the total item count
   (confirmed with the user, rather than the number of occupied slots).
4. **Full boxes** — ordered by their first contents, compared exactly as ordinary items are, i.e.
   with the configured sort key. "First contents" is the item in the box's first occupied contents
   slot. Levels 3 and 4 never compete, since a box is either full or not.

"Mixed" means the box holds more than one kind of item, by item identity. An empty box has no
contents and is always treated as pure; that has no practical effect, since every empty box is pure
and they are separated by colour first.

All of this is ascending; there is no descending mode.

**In creative mode the middle mouse button belongs to vanilla.** Where the sort hotkey is bound to
the middle mouse, sorting steps aside so clone / pick-block works: it does nothing and leaves the
click untouched. That applies only when all three hold — creative mode, the sort key actually bound
to the middle mouse, and the hovered slot holding something. On an empty slot vanilla's clone does
nothing anyway, so sorting still runs, which keeps sorting reachable in creative by hovering a gap.
Rebinding the sort key to anything other than the middle mouse removes the restriction entirely.

A vanilla GUI button click sound is played when a sort starts, so the hotkey gives audible
feedback rather than appearing to do nothing.

---

## 2. Restocking — `Restock`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `restockEnabled` | Option list | `OFF` | `ON`, `OFF` | Master switch for automatic restocking. |
| `restockMinCount` | Integer | `0` | **0–64** | Global minimum count. A restock fills straight to one full stack (see below). `0` means "only top up once a slot has run dry". |
| `restockTargetSlots` | Option list | `HOTBAR` | `HOTBAR`, `OFFHAND`, `BOTH` | Which slots may receive restocked items. |
| `restockCooldownTicks` | Integer | `10` | 0–200 | Cooldown after any restock action. |
| `restockMaxOpsPerTick` | Integer | `1` | 1–9 | Hard cap on container operations issued per tick. |

**Deliberately absent from this section:**

- no **trigger-selection** option — there is exactly one trigger (see below);
- no per-source **enable flags** for the player inventory or the open container. Both are simply
  always available, tried in a fixed order — the player's own inventory first, then the open
  container. The order used to be configurable; it is not any more;
- no **per-item thresholds**. With a single threshold and a target that is always one full stack, a
  per-item override had only two possible readings — "restock this item earlier" or "keep more of
  it" — and could not express the second one anyway.

### Trigger: held-item count decrease

There is **no trigger-selection option**, deliberately. Restocking has exactly one trigger — a
check runs when the stack in the main hand gets smaller, which happens when an item is used or
consumed (eating, throwing, drinking) and when a block is placed — so a boolean "enable this
trigger" switch would have nothing to select between. `restockEnabled` is the master switch.

The triggers that used to exist were removed on request:

| Removed trigger | Former key |
|---|---|
| Periodic tick | `restockTriggerTick` |
| After pickup | `restockTriggerAfterPickup` |
| After opening a container | `restockTriggerAfterContainerOpen` |
| After item use | `restockTriggerAfterUse` |
| Held-count toggle (redundant) | `restockTriggerHeldDecrease` |
| Tick interval (only fed the tick trigger) | `restockTickInterval` |

An intermediate version renamed the surviving trigger to `restockTriggerHeldDecrease` and kept it
as a boolean. That key has since been removed as well, because with only one trigger left the
option had no alternative to choose and was pure noise in the config screen. `restockTickInterval`
went with it, since its only remaining consumer was the Litematica "On Tick" mode, itself removed
(see section 4).

Restocking is therefore driven purely by actually consuming something from the hand, instead of
polling on a timer.

### Threshold and target semantics

```
threshold = restockMinCount

target(item) = one full stack of that item
             = item.getDefaultMaxStackSize()      (64 for most, 16 for eggs/snowballs, …)
```

A restock for an item is considered once the amount available in the target slots drops to **or
below** `restockMinCount`, and items are then moved until the slot holds **one full stack**.

There is **no hysteresis option**. The item itself defines how large a stack is, so "top it back up
to a full stack" needs no second number — and removing it also removes the question of how the two
numbers should interact. A former `restockHysteresis` key is ignored and dropped from existing
config files.

With `restockMinCount` at **0** the rule collapses to "top up only once the slot is empty", which
is a useful setting: you place blocks until the stack runs out, and the next placement's trigger
refills it to a full stack. Note that reaching an empty slot still requires the *identity* of what
was consumed, since an empty slot no longer says what used to be in it — the trigger carries that
through from the previous tick.

> Items with a stack size of 1 (tools, and anything else that does not stack) are deliberately
> excluded from count-based restocking.

> **Removed: `restockPerItemThresholds`.** It used to override the minimum count per item. It only
> ever controlled **when** to act — the amount added was always one full stack — so it could not
> express "keep more of this item", which is what people reached for it expecting. The key is
> ignored and dropped from existing config files.

Sources are tried in a fixed order: the player's own inventory first, then the open container. The\norder used to be configurable; with the duplicate shulker tier gone only two sources remain and the\nuseful order is "things I am carrying" before "things in the chest I happen to have open".

---

## 3. QuickShulker integration — `QuickShulker`

The shulker-box behaviour lives in its own section and GUI tab, so it can be configured — or
switched off — independently of the restock core.

| Key | Type | Default | Description |
|---|---|---|---|
| `quickshulkerRestockFromShulker` | Option list | `ON` | `ON`, `OFF` | Restock out of a carried shulker box that holds the wanted item. Requires QuickShulker; has no effect without it. |

This option only fires once nothing reachable already holds the wanted item. SortNStock then scans the
whole inventory for a carried box containing it and asks QuickShulker to open that box; the refill is
remembered and retried on the following ticks, because the box's menu only exists once the server
has answered.

> **The box's screen will open visibly.** Taking items out of a shulker box is server-authoritative
> and QuickShulker's only mechanism is  + [char]0x0060 + 'sendOpenPacket + [char]0x0060 + ', so there is no silent path. This is a
> platform limit, not a missing implementation.
There is deliberately no separate "use shulker boxes as a source" switch — it duplicated the
open-container tier, see above.

> **Note.** Reading a shulker's contents needs no QuickShulker — the contents live in the vanilla
> `DataComponents.CONTAINER` component and are already client-synced. QuickShulker is only needed
> to *open* a carried box so items can be moved out, which is a server round-trip. That is why
> the flow is two-phase: request the open, then restock on a later pass.

---

## 4. Litematica integration — `Litematica`

| Key | Type | Default | Range | Description |
|---|---|---|---|---|
| `litematicaAutoRestock` | Option list | `OFF` | `ON`, `OFF` | Master switch for the schematic integration. |
| `litematicaRightClickRestock` | Option list | `ON` | `ON`, `OFF` | Use a carried shulker box when the required block is not in the inventory at all. |
| `litematicaLowCountRestock` | Option list | `ON` | `ON`, `OFF` | Restock when the held item is correct but the count is low. |
| `litematicaCooldownTicks` | Integer | `5` | 0–200 | Cooldown between schematic-triggered restocks. |

### Single evaluation point

There is **no** trigger-timing option. The integration has exactly one evaluation point: the head
of Litematica's `InventoryUtils.schematicWorldPickBlock`, reached from a right-click on a
schematic position. That is the only moment at which a restock can land before the hotbar slot is
switched, so the same click can use the restocked stack.

A former `litematicaTriggerTiming` option offered three modes; it was removed.

| Removed value | Why it was removed |
|---|---|
| `ON_TICK` | Polling on the client tick could never help the click that is in progress — it is exactly the timer-based behaviour that was removed globally. |
| `ON_HELD_COUNT_CHANGE` | Also one tick late for the current click, so it could never substitute for the pick-block hook. |
| `ON_RIGHT_CLICK` | Not removed as behaviour — it became the fixed, only behaviour, so it no longer needs to be a selectable value. |

Restocking on a held-item count decrease (section 2) is the automatic module's trigger; the
right-click hook above is the schematic integration's evaluation point. Both are now fixed rather
than configurable.

### Deliberate non-options

Per the design brief, SortNStock intentionally does **not** expose:

- a target-slot list — the allowed hotbar slots come from Litematica's own
  `pickBlockableSlots` config (see `docs/PHASE-0-COMPATIBILITY-REPORT.md` §D1);
- a slot blacklist, or any fallback slot strategy;
- schematic-specific thresholds — the restock module's `restockMinCount` is reused verbatim;
- a trigger timing — see above.

---

## 5. Debug — `Debug`

| Key | Type | Default | Description |
|---|---|---|---|
| `debugLogging` | Option list | `OFF` | `ON`, `OFF` | Verbose logging of decisions and mixin entry. |

---

## 6. Default hotkey summary

| Hotkey | Default | Action |
|---|---|---|
| `sortHotkey` | **`BUTTON_3`** (middle mouse) | Sort the container under the cursor |
| `openConfigGuiHotkey` | **`T`,`K`** | Open the SortNStock config screen |

**Notes on the defaults.**

- These are the only two hotkeys. The restock module is switched on and off from the config screen,
  and a key that is unbound by default is just a row the user has to make a decision about. Former
  keys `restockNowHotkey`, `toggleRestockHotkey` and `litematicaHotkey` were removed on request;
  they were all unbound, so nothing changes for anyone who never set them. They are ignored and
  dropped from existing config files.
- `sortHotkey` uses the middle mouse button, which is **also vanilla's "pick block" button**.
  Both actions will fire on the same click; see the user test checklist.
- `T`,`K` means "hold `T`, then press `K`" in MaLiLib's notation (modifier first), matching how
  Litematica writes combos such as `M,C`.
- Both bound hotkeys use `Context.ANY`, so they work while a container screen is open. This
  matters for sorting specifically: MaLiLib's default context is `INGAME`, which is inactive
  whenever any screen is open, so a default-context sort hotkey would never fire in the one
  situation it is needed.
- MaLiLib performs its own conflict detection and shows any collision in its hotkey list; every
  binding can be changed or cleared in the MaLiLib config GUI.

> **待用户验证 / pending user verification.** Default-key conflict freedom, and whether the
> middle-mouse sort hotkey interacts acceptably with vanilla pick block, have not been confirmed
> in game. Please see `docs/USER-TEST-CHECKLIST.md`.
