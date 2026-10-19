# SortNStock — Phase 0 Compatibility Reconnaissance Report

**Target:** Minecraft 26.1.2 · Fabric Loader ≥ 0.19.3 · Fabric API (exact match) · client-only
**Reconnaissance method:** the three prerequisite repositories were cloned locally and read
directly; all version/coordinate claims below were verified against those working trees and
against the live Maven metadata of the publishing repositories. No API in this report is
inferred or invented.

---

## 1. Version and compatibility report

### 1.1 Verdict

**Compatible.** All three prerequisites have branches that target Minecraft 26.1.2 (or the
26.1 line), and all required artifacts resolve from public Maven repositories. No blocking
incompatibility was found.

Two specification discrepancies were found and are resolved as described in §1.4. They do
not block development, but they change how two requirements are implemented.

### 1.2 Verified dependency table

| Component | Branch / ref | Commit locked | Artifact | Version | MC target |
|---|---|---|---|---|---|
| MaLiLib | `LTS/26.1` | `e06d6b08d8ef03dc0e12673e62a7a92720d658d4` | `fi.dy.masa.malilib:malilib-fabric-26.1.2` | `0.28.12` | 26.1.2 |
| Litematica | `LTS/26.1` | `edf9ced2af2244d56fc20086883a014bf051b1b8` | `fi.dy.masa.litematica:litematica-fabric-26.1.2` | `0.27.14` | 26.1.2 |
| QuickShulker | `26.1` | `085cec74393640e7bf10730c1f3b3a0152cbc24f` | `net.kyrptonaught:quickshulker` | `3.1.0-26.1` | 26.1 line (no MC constraint) |

Repositories (verified live):

- MaLiLib + Litematica → `https://masa.dy.fi/maven/sakura-ryoko`
  (metadata reports `latest`/`release` = `0.28.12` and `0.27.14` respectively for the
  `…-26.1.2` artifacts)
- QuickShulker → `https://maven.moranpcy.com/releases`
  (metadata lists `3.1.0-26.1`; `…-26.1` is the highest 26.1-line version)

### 1.3 Toolchain (differs from the brief — read this)

| Item | Brief said | Reality for MC 26.1.2 | Action |
|---|---|---|---|
| Java | ≥ 21 | **Java 25 required** for the Gradle JVM | Locked to Java 25 |
| Gradle | (unspecified) | 9.7.1 (matches MaLiLib/Litematica wrappers) | Wrapper copied from MaLiLib |
| Loom | Fabric Loom | **`net.fabricmc.fabric-loom`** (non-remapping), `1.17.21` | Pinned |
| Mappings | (unspecified) | **None** — 26.1 is unobfuscated | No `mappings` line |
| Compile scope | `modImplementation` | **`implementation` / `compileOnly`** | Used throughout |

**26.1 is the first unobfuscated Minecraft release.** Per the
[Fabric 26.1 announcement](https://fabricmc.net/2026/03/14/261.html), Yarn is no longer
officially supported, the remapping Loom plugin is replaced by `net.fabricmc.fabric-loom`,
`modImplementation`/`modCompileOnly` are replaced by plain `implementation`/`compileOnly`,
and `remapJar` is replaced by `jar`. All mods targeting 26.1+ use Mojang's official names.
This is reflected in `build.gradle`.

Java 25 runtimes present on this machine: `<java-25-home>` (25.0.2) and
`<alternate-java-25-home>` (25.0.2). `gradle.properties` pins
`org.gradle.java.home` to the Zulu build; the default `JAVA_HOME` on this machine is
Java 21 and **will not build this project**.

### 1.4 Specification discrepancies found (and how they are handled)

**D1 — `PICK_BLOCK_USABLE_SLOTS` does not exist in Litematica.**
The brief repeatedly refers to a Litematica config named `PICK_BLOCK_USABLE_SLOTS`. No such
config exists in the 26.1.2 codebase. The real option is:

```
Configs.Generic.PICK_BLOCKABLE_SLOTS   // field type: ConfigString
new ConfigString("pickBlockableSlots", "1,2,3,4,5")
```

It is a **comma-separated list of 1-based hotbar slot numbers**, not a bitmask or a
min/max range. Litematica consumes it by pushing the parsed list into
`fi.dy.masa.litematica.util.InventoryUtils.setPickBlockableSlots(String)`, which converts
each entry to a 0-based index, keeps only entries accepted by `Inventory.isHotbarSlot(int)`,
and de-duplicates. The parsed result is stored in a **private** static list, so SortNStock
must read the config string itself and re-derive the slot set rather than reading Litematica's
internal list.

*Resolution:* SortNStock reuses the real `pickBlockableSlots` config as the single source of
truth for allowed target slots, exactly as the brief's intent requires. No separate slot
config, no blacklist, no fallback is created. This is called out in the README and the
in-game config comments so the naming difference is not mistaken for a bug.

**D2 — Shulker contents can be read without QuickShulker.**
The brief asks for a QuickShulker compatibility layer to read carried shulker contents. It
turns out shulker contents live in the ordinary vanilla component
`DataComponents.CONTAINER` → `ItemContainerContents`, which is part of the `ItemStack` and is
therefore already synchronised to the client. MaLiLib — already a hard dependency — exposes
ready-made readers:

```
malilib.util.InventoryUtils.getStoredItems(ItemStack)                  // non-empty entries
malilib.util.InventoryUtils.getStoredItems(ItemStack, int slotCount)   // preserves empty slots
malilib.util.InventoryUtils.getStoredItemCounts(ItemStack)             // per-item counts
malilib.util.InventoryUtils.shulkerBoxHasItems(ItemStack)
```

*Resolution:* reading is done through these vanilla/MaLiLib paths and works with or without
QuickShulker installed. **QuickShulker is required only for the "take items *out* of a
shulker" step**, because mutating a shulker's contents is a server-authoritative operation.
No reflection and no obfuscated names are used on the QuickShulker path; it is a genuine
public API call.

---

## 2. Dependency coordinates and API inventory

### 2.1 Gradle coordinates

```groovy
// Hard dependency — config/hotkey system and inventory primitives
implementation "fi.dy.masa.malilib:malilib-fabric-26.1.2:0.28.12"

// Soft dependency — schematic "demand source" (compileOnly + runtimeOnly)
compileOnly  "fi.dy.masa.litematica:litematica-fabric-26.1.2:0.27.14"
runtimeOnly  "fi.dy.masa.litematica:litematica-fabric-26.1.2:0.27.14"

// Soft dependency — opening carried shulkers
compileOnly  "net.kyrptonaught:quickshulker:3.1.0-26.1"
runtimeOnly  "net.kyrptonaught:quickshulker:3.1.0-26.1"
```

Transitive pins published by MaLiLib 0.28.12 (for reference): `fabric-api-base 2.0.3`,
`fabric-networking-api-v1 6.3.1`, `fabric-resource-loader-v1 2.0.10`, `fabric-loader 0.19.5`,
`jsr305 3.0.2`, `conditional-mixin-fabric 0.6.4`.

### 2.2 MaLiLib API (verified by reading source)

**Mod initialisation**

| Symbol | Signature / usage |
|---|---|
| `fi.dy.masa.malilib.interfaces.IInitializationHandler` | `void registerModHandlers()` |
| `fi.dy.masa.malilib.event.InitializationHandler` | `getInstance().registerInitializationHandler(IInitializationHandler)` |

**Config registration**

| Symbol | Signature / usage |
|---|---|
| `fi.dy.masa.malilib.config.ConfigManager` | `getInstance().registerConfigHandler(String modId, IConfigHandler handler)` |
| `fi.dy.masa.malilib.config.IConfigHandler` | `void load()`, `void save()`, `default void onConfigsChanged()`, `default void onLanguageChanged(String)` |
| `fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN` | `registerConfigScreenFactory(ModInfo)` |
| `fi.dy.masa.malilib.util.data.ModInfo` | `record ModInfo(String modId, String modName, Supplier<GuiBase> configScreenSupplier)` |

**Config options** (`fi.dy.masa.malilib.config.options.*`, all support fluent `.apply(String translationKeyPrefix)`)

| Class | Constructor used |
|---|---|
| `ConfigBoolean` | `(String name, boolean defaultValue)` |
| `ConfigInteger` | `(String name, int defaultValue, int min, int max)` |
| `ConfigString` | `(String name, String defaultValue)` |
| `ConfigFloat` | `(String name, float def, float min, float max)` |
| `ConfigOptionList` | `(String name, IConfigOptionListEntry defaultValue)` |
| `ConfigHotkey` | `(String name, String defaultHotkey)` / `(name, defaultHotkey, KeybindSettings)` |
| `ConfigBooleanHotkeyed` | `(String name, boolean def, String defaultHotkey)` |

`ConfigOptionList` values must implement `fi.dy.masa.malilib.config.IConfigOptionListEntry`:
`getStringValue()`, `getDisplayName()`, `cycle(boolean)`, `fromString(String)`.

**Hotkeys**

| Symbol | Signature / usage |
|---|---|
| `fi.dy.masa.malilib.hotkeys.IHotkeyCallback` | `boolean onKeyAction(KeyAction action, IKeybind key)` |
| `fi.dy.masa.malilib.hotkeys.IKeybind` | `setCallback(IHotkeyCallback)`, `isKeybindHeld()` |
| `fi.dy.masa.malilib.hotkeys.KeyCallbackToggleBooleanConfigWithMessage` | `(IConfigBoolean)` — built-in "toggle a boolean config + print message" callback |
| `fi.dy.masa.malilib.hotkeys.KeyAction` | `PRESS`, `RELEASE`, `BOTH` |
| `fi.dy.masa.malilib.hotkeys.KeybindSettings` | `PRESS_ALLOWEXTRA`, `RELEASE_EXCLUSIVE`, `MODIFIER_INGAME` |
| `fi.dy.masa.malilib.hotkeys.IKeybindProvider` | `void addKeysToMap(IKeybindManager)`, `void addHotkeys(IKeybindManager)` |
| `fi.dy.masa.malilib.hotkeys.IKeybindManager` | `registerKeybindProvider(IKeybindProvider)`, `addKeybindToMap(IKeybind)`, `addHotkeysForCategory(String modName, String keyCategory, List<? extends IHotkey>)` |
| `fi.dy.masa.malilib.event.InputEventHandler` | `getKeybindManager()` |

**Ticking**

| Symbol | Signature / usage |
|---|---|
| `fi.dy.masa.malilib.interfaces.IClientTickHandler` | `void onClientTick(Minecraft mc)` |
| `fi.dy.masa.malilib.event.TickHandler` | `getInstance().registerClientTickHandler(IClientTickHandler)` |

**Config GUI**

| Symbol | Signature / usage |
|---|---|
| `fi.dy.masa.malilib.gui.GuiConfigsBase` | subclass; `super(int listX, int listY, String modId, String titleKey, String version)` |
| `fi.dy.masa.malilib.gui.interfaces.IConfigGuiAllTab` | provides the combined "All" tab |
| `getConfigs()` | returns `List<ConfigOptionWrapper>`; `ConfigOptionWrapper.createFor(List<? extends IConfigBase>)` |

**Persistence**

| Symbol | Signature / usage |
|---|---|
| `fi.dy.masa.malilib.util.FileUtils` | `getConfigDirectory()` |
| `fi.dy.masa.malilib.config.ConfigUtils` | `readConfigBase(JsonObject, String category, List<? extends IConfigBase>)`, `writeConfigBase(…)`, `readHotkeys(…)`, `writeHotkeys(…)` |
| `fi.dy.masa.malilib.util.data.json.JsonUtils` | `writeJsonToFile(JsonObject, Path)` |

MaLiLib handles load-on-launch and save-on-change itself; the mod's `IConfigHandler` only
has to implement `load()`/`save()` against its own JSON file.

**Inventory primitives** (`fi.dy.masa.malilib.util.InventoryUtils`) — the core of restocking.
Every mutating call funnels into an ordinary container click, so the server stays authoritative:

| Method | Purpose |
|---|---|
| `swapSlots(AbstractContainerMenu container, int slotNum, int hotbarSlot)` | swap a slot with a hotbar slot (`ContainerInput.SWAP`) |
| `swapItemToMainHand(ItemStack stackReference, Minecraft mc)` | survival-safe swap of an item into the main hand |
| `findSlotWithItem(AbstractContainerMenu container, ItemStack stackReference, boolean reverse)` | locate a matching item (`-1` if absent) |
| `findEmptySlotInPlayerInventory(AbstractContainerMenu containerPlayer, boolean allowOffhand, boolean reverse)` | find a free inventory slot |
| `getMenuAsContainer(AbstractContainerMenu menu)` | view an open menu as a `Container` |
| `getStoredItems(ItemStack)` / `getStoredItems(ItemStack, int)` | read shulker/container-item contents |
| `getStoredItemCounts(ItemStack)` | per-item counts stored inside a container item |
| `shulkerBoxHasItems(ItemStack)` | quick shulker-content test |
| `areStacksEqual` / `areStacksAndNbtEqual` / `areStacksEqualIgnoreNbt` / `areStacksEqualIgnoreDurability` | comparison predicates for sort keys and stack merging |

### 2.3 Litematica API (verified by reading source)

| Need | Symbol |
|---|---|
| Allowed hotbar slots | `fi.dy.masa.litematica.config.Configs.Generic.PICK_BLOCKABLE_SLOTS` (`ConfigString`, default `"1,2,3,4,5"`) |
| BlockState → required item | `fi.dy.masa.litematica.materials.MaterialCache.getInstance().getRequiredBuildItemForState(BlockState, Level, BlockPos)` |
| Easy place flow | `fi.dy.masa.litematica.util.EasyPlaceUtils` |
| Pick block | `fi.dy.masa.litematica.util.PickBlockUtils`, `fi.dy.masa.litematica.util.WorldUtils.doSchematicWorldPickBlock(boolean, Minecraft)` |
| Mixin config plugin pattern | `fi.dy.masa.litematica.compat.mixin.LitematicaMixinConfigPlugin extends RestrictiveMixinConfigPlugin` |
| Access to Litematica config classes | `Configs.Generic`, `Configs.Visuals`, `Configs.InfoOverlays`, `Configs.Colors` (all in `…litematica.config`) |
| Placement / schematic world | to be finalised from the dedicated easy-place mapping pass (task in progress at time of writing) |

### 2.4 QuickShulker API

| Need | Symbol |
|---|---|
| Mod id | `quickshulker` |
| Entry points for other mods | `"quickshulker"` → `net.kyrptonaught.quickshulker.api.RegisterQuickShulker` (`registerProviders()`); `"quickshulker_client"` → `api.RegisterQuickShulkerClient` (`registerClient()`) |
| Open a carried shulker (client→server) | `net.kyrptonaught.quickshulker.network.OpenShulkerPacket.sendOpenPacket(int invSlot)` — `invSlot` indexes the player's **current** `containerMenu.slots` (36–44 = hotbar, 45 = offhand) |
| Shulker contents reader | `net.kyrptonaught.shulkerutils.ShulkerUtils.getInventoryFromShulker(ItemStack)` → `ItemStackInventory extends SimpleContainer` |
| Is item openable | `net.kyrptonaught.quickshulker.api.Util.isOpenableItem(ItemStack)`, `Util.canOpenInHand(ItemStack)` |

Note: opening a shulker is **client-initiated but requires a server round-trip**; the opened
menu is a real server-side `ShulkerBoxMenu`. QuickShulker exposes **no** public event for
open/close/contents-change, so SortNStock polls the player's `containerMenu` instead of
subscribing to events. No reflection is needed.

---

## 3. Module design and event flow

### 3.1 Layering

```
com.sortnstock
├── SortNStock              ModInitializer — hands off to MaLiLib
├── Reference               mod ids / metadata constants
├── InitHandler             IInitializationHandler — the single wiring point
├── config/
│   ├── Configs             IConfigHandler — all options + JSON load/save
│   ├── Hotkeys             all ConfigHotkey declarations
│   └── options/            enums implementing IConfigOptionListEntry
├── event/
│   ├── InputHandler        IKeybindProvider — registers keybinds with MaLiLib
│   ├── KeyCallbacks        binds each hotkey to its action
│   └── ClientTickHandler   IClientTickHandler — drives throttled/periodic work
├── gui/GuiConfigs          GuiConfigsBase subclass (MaLiLib renders it)
├── sort/                   inventory sorting engine (pure, unit-testable)
├── restock/
│   ├── RestockManager      decision + cooldown/throttle logic
│   ├── ItemSource tiers    player inventory → shulkers → open container
│   └── Thresholds          global minCount + per-item overrides + hysteresis
└── compat/
    ├── litematica/         guarded Litematica access + the Easy Place mixin
    └── quickshulker/       guarded QuickShulker access
```

### 3.2 Event flow — restocking triggered by Easy Place

```
player right-clicks a schematic position
        │
        ▼
[Litematica mixin]  injected at the head of the easy-place pick-block step
        │
        ├─ hard guards: mod enabled? schematic active? crosshair on a schematic block?
        │                required item resolvable? allowed-slot set non-empty?
        ├─ cooldown check (litematicaCooldownTicks)
        ▼
SortNStock RestockManager
        ├─ resolve required ItemStack for the targeted schematic BlockState
        ├─ compute target count = minCount(+per-item override) + hysteresis
        ├─ scan tiers:  1) player inventory   2) carried shulkers   3) open container
        ├─ issue ordinary container clicks (SWAP) to move items to allowed hotbar slots
        ▼
returns to Litematica
        │
        ▼
Litematica Pick Block selects the item / switches slot
        │
        ▼
Litematica Easy Place places the block   (SortNStock never cancels or intercepts the click)
```

### 3.3 Design principles carried over from the brief

- SortNStock **never** places blocks, never builds, never modifies a schematic.
- Slot switching stays with Litematica Pick Block; block placement stays with Easy Place.
- SortNStock's only job is moving already-owned items into the allowed hotbar slots.
- Target slots come solely from Litematica's `pickBlockableSlots`.
- Thresholds come solely from the restock module (global `minCount` + per-item overrides) —
  no separate Litematica thresholds.
- If the allowed-slot set is empty, the tick is skipped entirely.

---

## 4. Risks

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | Java 25 required, brief said ≥ 21 | High (build fails on Java 21) | Pinned `org.gradle.java.home`; documented in README |
| R2 | `PICK_BLOCK_USABLE_SLOTS` misnamed in brief | Medium | Uses the real `PICK_BLOCKABLE_SLOTS`; documented |
| R3 | Mixin↔Litematica version coupling | Medium | Mixin target pinned to Litematica `0.27.14`; exact commit recorded in README |
| R4 | Same-tick ordering: restock must complete before pick block | **High** | Dedicated mapping pass to pick the injection point; if ordering cannot be guaranteed, behaviour is documented as "may need a second right-click" and added to the test checklist |
| R5 | Litematica mixin must not load when Litematica is absent | High | Separate mixin config with an `IMixinConfigPlugin` gated on `FabricLoader.isModLoaded("litematica")`; all Litematica types isolated in `compat/litematica` |
| R6 | Moving items out of a shulker needs a server round-trip | Medium | Shulker tier only active when QuickShulker is present; degrades to read-only otherwise |
| R7 | QuickShulker 26.1 artifact has no MC version constraint | Low | Declared `recommends` and runtime-guarded |
| R8 | Throttling must not spam container clicks | Medium | Cooldown + per-tick operation cap, both configurable |

---

## 5. Phased plan

| Phase | Content | Exit criterion |
|---|---|---|
| 0 | This report | Compatibility + API inventory agreed |
| 1 | Skeleton, `runClient` capable | `gradlew build` passes |
| 2 | MaLiLib config + hotkey registration | `gradlew build` passes |
| 3 | Inventory sorting | `gradlew build` passes |
| 4 | Restock core | `gradlew build` passes |
| 5 | QuickShulker integration | `gradlew build` passes |
| 6 | Litematica integration incl. Easy Place mixin | `gradlew build` passes |
| 7 | Build, docs, licence, test checklist, release build | `gradlew build` passes |

---

## 6. Verification status

**Verified by the developer (mechanical, not gameplay):** dependency resolution, artifact
existence on Maven, library API signatures read from source, compile success, resource and
mixin-configuration validity.

**Explicitly NOT verified:** any in-game behaviour. No gameplay testing has been performed.
Every functional item in the user test checklist is **待用户验证 / pending user verification**.
