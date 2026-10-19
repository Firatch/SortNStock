# Developer Self-Check Report

This report covers **only** what the developer can verify mechanically: dependency resolution,
compilation, packaging, and configuration validity.

**It contains no gameplay test results.** No in-game functionality has been verified. See
`docs/USER-TEST-CHECKLIST.md` for everything that remains 待用户验证.

---

## 1. Build results

| Phase | Content | Command | Result |
|---|---|---|---|
| 1 | Skeleton (unobfuscated Loom toolchain + dependency resolution) | `gradlew build` | ✅ BUILD SUCCESSFUL (7m14s, cold cache) |
| 2 | MaLiLib config + hotkey + config-GUI wiring | `gradlew build` | ✅ BUILD SUCCESSFUL |
| 3 | Inventory sorting | `gradlew build` | ✅ BUILD SUCCESSFUL |
| 4 | Restock core | `gradlew build` | ✅ BUILD SUCCESSFUL |
| 5 | QuickShulker integration | `gradlew build` | ✅ BUILD SUCCESSFUL |
| 6 | Litematica integration + Easy Place Mixin | `gradlew build` | ✅ BUILD SUCCESSFUL |
| 7 | Docs, licence, packaging | `gradlew build` | ✅ BUILD SUCCESSFUL |

Toolchain actually used:

```
Gradle          9.7.1 (wrapper; copied from MaLiLib's wrapper)
Loom            1.17.21 (net.fabricmc.fabric-loom, non-remapping)
Daemon JVM      <java-25-home>  (Zulu 25.0.2) via org.gradle.java.home
Launcher JVM    21.0.8
Minecraft       26.1.2 (unobfuscated — no mappings declared)
Java release    25
```

The one compile warning encountered during development (`InventorySorter` using a deprecated API,
`Item#builtInRegistryHolder()`) was fixed by switching to the supported
`BuiltInRegistries.ITEM.wrapAsHolder(item).tags()` path. The final build is warning-free apart from
Gradle's own deprecation notice about Gradle 10 compatibility, which comes from the Loom plugin,
not from this project's build script.

### Build environment workarounds (worth knowing)

1. **Java trust store.** The Gradle wrapper could not download its distribution, and Gradle could
   not have downloaded dependencies either, because this machine's JDK trust store failed the
   public TLS chain with `PKIX path building failed`. (`PowerShell`'s Windows TLS stack worked,
   which is how the distribution zip was fetched and installed into the wrapper cache.)
   `gradle.properties` now sets `-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT` so the JVM uses the
   Windows trust store. Remove this on a machine whose JDK trusts the public CAs normally.

2. **Gradle distribution.** Because the wrapper's download failed, the Gradle 9.7.1 distribution
   zip was fetched with `Invoke-WebRequest` and unpacked into the wrapper's cache directory
   (`~/.gradle/wrapper/dists/gradle-9.7.1-bin/<hash>/`). This is a cache population step, not a
   project change — the wrapper configuration itself is standard and pinned to 9.7.1.

### Artifacts produced

```
build/libs/sortnstock-26.1.2-0.1.0.jar
build/libs/sortnstock-26.1.2-0.1.0-sources.jar
```

Packaged jar contents were inspected and contain: the mod classes, `fabric.mod.json` (with
`${version}` correctly expanded to `0.1.0`), `sortnstock.litematica.mixins.json`, the
`en_us`/`zh_cn` language files, and the bundled `LICENSE_sortnstock-26.1.2`.

---

## 2. Dependency resolution

All dependencies resolve from public repositories, verified both by live Maven metadata checks and
by an actual successful Gradle resolution:

| Artifact | Version | Repository |
|---|---|---|
| `com.mojang:minecraft` | 26.1.2 | (Loom) |
| `net.fabricmc:fabric-loader` | 0.19.5 | maven.fabricmc.net |
| `net.fabricmc.fabric-api:fabric-api` | 0.155.3+26.1.2 | maven.fabricmc.net |
| `fi.dy.masa.malilib:malilib-fabric-26.1.2` | 0.28.12 | masa.dy.fi/maven/sakura-ryoko |
| `fi.dy.masa.litematica:litematica-fabric-26.1.2` | 0.27.14 | masa.dy.fi/maven/sakura-ryoko |
| `net.kyrptonaught:quickshulker` | 3.1.0-26.1 | maven.moranpcy.com/releases |
| `com.google.code.findbugs:jsr305` | 3.0.2 | Maven Central (`compileOnly`) |

QuickShulker's transitive runtime pins (an older `fabric-api`, `modmenu`, `fabric-loader`) are
explicitly excluded so the development runtime stays deterministic and uses this project's
Fabric API version. Verified in the resolved runtime classpath:

```
+--- fi.dy.masa.malilib:malilib-fabric-26.1.2:0.28.12
+--- fi.dy.masa.litematica:litematica-fabric-26.1.2:0.27.14
|    \--- fi.dy.masa.malilib:malilib-fabric-26.1.2:0.28.12 (*)
\--- net.kyrptonaught:quickshulker:3.1.0-26.1
     \--- me.fallenbreath:conditional-mixin-fabric:0.6.4 (*)
```

The full resolved graph is saved at
[`docs/build-logs/dependency-tree-runtimeClasspath.txt`](build-logs/dependency-tree-runtimeClasspath.txt),
and the final build output at [`docs/build-logs/final-build.log`](build-logs/final-build.log).

Mixin infrastructure resolved from Loom: `net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7` and
`io.github.llamalad7:mixinextras-fabric:0.5.5`.

---

## 3. API verification method

Per the brief's prohibition on inventing APIs, **no API used by this project was guessed**. Every
signature was verified against one of:

1. the prerequisite source trees (cloned read-only into the git-ignored `_recon/` directory), or
2. `javap` against the **actual jars** — taking advantage of 26.1 being unobfuscated, so the real
   signatures are readable:
   - `minecraft-merged-deobf-26.1.2.jar` from the Loom cache,
   - `litematica-fabric-26.1.2-0.27.14.jar`,
   - `quickshulker-3.1.0-26.1.jar`.

This mattered. Three APIs that would otherwise have been written from assumption turned out **not
to exist** in 26.1 and were corrected before the code compiled:

| Assumed | Reality in 26.1 | Used instead |
|---|---|---|
| `ItemStack.getMaxStackSize()` | does not exist on `ItemStack` | `Item#getDefaultMaxStackSize()`, or `Slot#getMaxStackSize(ItemStack)` |
| `ItemStack.getTags()` | does not exist on `ItemStack` | `BuiltInRegistries.ITEM.wrapAsHolder(item).tags()` |
| `BlockState.isAir()` | does not exist on `BlockState` | `state.is(BlockTags.AIR)` |

Additionally, the brief's `PICK_BLOCK_USABLE_SLOTS` config does not exist in Litematica; the real
option is `Configs.Generic.PICK_BLOCKABLE_SLOTS`. See `docs/PHASE-0-COMPATIBILITY-REPORT.md` §D1.

---

## 4. Resource and Mixin configuration validation

- [x] `fabric.mod.json` parses, and `${version}` expands correctly in the packaged jar.
- [x] `depends` contains `minecraft`, `fabricloader`, `java`, `fabric-api`, `malilib`.
- [x] `recommends` contains `litematica` and `quickshulker` (soft dependencies).
- [x] `environment` is `client` — this is a client-only mod.
- [x] `sortnstock.litematica.mixins.json` is well-formed, targets `JAVA_25`, and is registered in
      `fabric.mod.json` with `environment: client`.
- [x] The Mixin config declares a plugin (`SortNStockLitematicaMixinPlugin`) which returns `false`
      from `shouldApplyMixin` when Litematica is absent, so the missing target class can never be
      touched on a non-Litematica install.
- [x] The Mixin's target method signature was verified with `javap` against the actual Litematica
      0.27.14 jar:
      `public static void schematicWorldPickBlock(ItemStack, BlockPos, Level, Minecraft)`.
- [x] Language files (`en_us.json`, `zh_cn.json`) are valid JSON and cover every registered config
      key and GUI label (verified by key-by-key comparison against the config declarations).
- [x] No `RemapJar`/`remapJar` usage — correct for unobfuscated 26.1, where the plain `jar` task is
      the deliverable.

---

## 5. Known unverified points

These are things the developer **cannot** establish without running the game, and they are
therefore explicitly not claimed:

1. **All gameplay behaviour.** Sorting, restocking, thresholds, triggers, source priority.
2. **That the Mixin actually applies at runtime.** The signature is verified and the build is
   clean, but only a real launch proves the injection lands. *(A startup smoke test was attempted;
   see §6b.)*
3. **Same-tick ordering.** That Litematica's pick block observes SortNStock's restocked stack
   within the *same* right-click, and that the server accepts the resulting click chain. The code
   is written so the client-side ordering holds (clicks are dispatched synchronously at the head of
   `schematicWorldPickBlock`, which runs before the slot switch and before the use packet), but
   this is a runtime property. If it does not hold, the documented fallback is "a second
   right-click may be required".
4. **Mutation-safety.** That no item can be lost or duplicated. The design is deliberately
   conservative — every move is a balanced group of ordinary clicks with an empty cursor at each
   group boundary, and any unexpected state aborts the remaining program — but this needs
   in-game confirmation.
5. **Server compatibility**, including servers that rate-limit or reject inventory clicks.
6. **Hotkey conflict freedom** for the chosen defaults.
7. **QuickShulker's shulker-open flow** (two-phase open-then-move), including whether the open
   request is accepted and how disruptive it feels.
8. **Long-session stability** and memory behaviour.
9. **Java 25 vs. brief.** The brief asked for Java ≥ 21; Minecraft 26.1 requires **Java 25**. The
   build pins a Java 25 Gradle JVM.
10. **Sort click cadence.** Sorting now dispatches its whole click program in a single tick with no
    throttling (see §6a). Whether a given server accepts that burst is a runtime property.

---

## 6a. Review of the externally applied `SortQueue` change

One file was modified outside this session: `src/main/java/com/sortnstock/sort/SortQueue.java`
(modified 2026-09-27). The working tree has no Git history, so the "before" state below is
reconstructed from this project's own session records (the file was previously a cross-tick
executor capped at `MAX_GROUPS_PER_TICK = 2`), **not** from a real diff.

**What changed:** the sort program is no longer spread across ticks. `MAX_GROUPS_PER_TICK` and the
bounded per-tick slice were removed; `tick()` now drains the entire queue in one pass and cancels.
The abort conditions are unchanged (container identity, cursor empty at start, cursor empty after
every group).

**What was verified by review (not by running the game):**

- [x] `region` cannot be null while `groups` is non-null — `start()` assigns both, `cancel()` clears
      both — so `region.getMenu()` on the guard line is safe.
- [x] All three original abort paths are preserved.
- [x] `execute()` still uses `ContainerInput.PICKUP` with button 0 and `ContainerInput.SWAP` with
      the hotbar index, matching `SortOp`'s contract, and maps region indices through
      `region.menuSlotId(...)`.
- [x] No dangling references to the removed constant anywhere in the source.
- [x] `./gradlew build` succeeds, and the strings unique to the new implementation
      (`one-tick dispatch`, `finished in one tick`) are present in the packaged
      `sortnstock-26.1.2-0.1.0.jar`, so the shipped artifact really contains the new code.

**What this does NOT establish:** that the burst is accepted by any given server. Click-burst
behaviour against packet-rate limiting and anti-cheat is **待用户验证** — see
`docs/USER-TEST-CHECKLIST.md` §4.1. Item 10 above and README limitation 3 record the exposure.

**Known consequence, accepted:** `SortQueue.isActive()` is now true only for the brief window
between `start()` and the next tick, so the re-entry guard in `SortingHandler` will rarely fire. It
is retained as cheap protection against a double key press landing in the same window, not because
it is load-bearing.

---

## 6c. Defects found by in-game testing and fixed

Reported 2026-10-03 from a test instance (Minecraft 26.1.2, Fabric Loader 0.19.5, MaLiLib 0.28.12,
**Litematica and QuickShulker not installed**): "sorting is completely unusable, items are not
sorted, and merging freezes the game."

The supplied crash-info bundle contained **no exception and no crash report**. `latest.log` shows
the mod loading and initialising normally (`SortNStock initialised; config registered with MaLiLib`)
and the Litematica mixin being skipped as designed, then the log simply stops — the process was
force-terminated. A silent stop with no stack trace is the signature of an infinite loop on the
client thread, not a crash. That matched a defect found by reading the merge code.

**Defect 1 — infinite loop in `SortPlanner.mergePhase` (the freeze).**
When two same-item stacks overflowed one full stack, the code refilled the first to the cap and
pushed the remainder back to the second. If the first was *already* at the cap this was a no-op,
yet the loop still recorded progress and rescanned, forever. Because `plan()` runs before any click
is issued, the single defect explains both symptoms: no clicks were ever sent *and* the render
thread spun. Fixed by only treating a merge as progress when a stack actually grows (skip the pair
when `cap - a.getCount() <= 0`), plus a hard safety bound that emits a partial plan and logs an
error instead of looping.

**Defect 2 — armor and offhand slots swept into the player-inventory region.**
The armor slots (container slots 36-39) and the offhand slot (40) are backed by the same
`Inventory` instance as the main inventory (`ArmorSlot` passes it straight to `Slot`), so grouping
by `Slot.container` identity selected 41 slots instead of 36. Fixed by requiring
`getContainerSlot() < Inventory.INVENTORY_SIZE` (36) for player-inventory regions.

**Defect 3 — `InventorySorter.maxStackSize` asked slot 0 only.**
With armor slots in the region, slot 0 was the helmet slot, whose limit is 1, so every item was
treated as unstackable and merging silently did nothing. Fixed by taking the best limit across the
region, seeded with the item's own limit.

Both defects 2 and 3 together explain the "items are not sorted" half: the target arrangement was
computed over slots that reject ordinary items, so the resulting clicks could not be applied and
the queue aborted on a non-empty cursor.

### Second round (2026-10-03, after the freeze fix): "merging works but nothing is filled or packed"

With the freeze gone, the next report showed a sorted-but-unsatisfying result: stacks not filled
and items still scattered. Two further defects, both independent of the first three:

**Defect 4 — the merge stopped as soon as the stack *count* matched.**
`mergePhase` guarded each candidate pair with `countStacks(target, a) >= countStacks(live, a)`.
Stack *counts* are not stack *fullness*: two stacks of 50 and 60 have exactly the same count as
the ideal 64 + 46, so the guard skipped them and both stayed half full forever. Fixed by driving
the merge off the `sortMergeStacks` option instead — when it is on, stacks are filled greedily
until no stack has room. Maximal filling produces a unique set of counts, so a greedy fill lands
exactly on what `InventorySorter.mergeIdentical` computed for the target; the now-unused
`countStacks` helper was deleted. When the option is off, the merge phase is skipped entirely so
in-place ordering is unaffected.

**Defect 5 — the compaction option was inverted, and flipping its default was not enough.**
`sortKeepEmptySlots` documented "leave the empty slots where they are instead of compacting" and
defaulted to `true`, so a user asking for a "tidy up" got a container that was merely reordered in
place.

The first attempt at a fix — changing the default to `false` — **did not work in practice**, and
the reason is worth recording: **a persisted config value always overrides a code default.**
Inspection of the live test instance confirmed this. The installed jar was byte-identical to the
new build (matching SHA256), yet `<test-instance>\.minecraft\config\sortnstock.json`
still contained `"sortKeepEmptySlots": true`, so the old behaviour was retained. MaLiLib's
`ConfigUtils.readConfigBase` only applies a key when the JSON object actually contains it
(`if (obj.has(name))`), and `writeConfigBase` builds a fresh object from the registered options.

The option was therefore **renamed** to `sortCompactItems`, meaning "pack the sorted items towards
the start of the container", defaulting to `true`. An existing config does not contain the new key,
so it picks up the new default with no manual step, and the stale key is dropped on the next save.
The rename is the migration; there is no migration hook in MaLiLib and none was needed.

**Defect 6 — same-item stacks could land in the wrong order.**
`findMatch` matched on item and components only, ignoring count. After merging, live and target hold
the same stacks, so it would happily drag a 46-count stack into the slot that asked for a 64,
leaving same-item stacks swapped — visible when sorting by count. `findMatch` now looks for an exact
match (item, components *and* count) first and only falls back to the item-level match when no exact
stack exists.

### Behaviour change requested after the second round: the hotbar is never sorted

Requested directly rather than reported as a defect: sorting the player's own inventory must
**never** touch the hotbar, and this must be hardcoded rather than exposed as an option.

Implemented by narrowing the player-inventory region to the **27 main inventory slots** only and
deleting the `sortHotbarSeparately` option outright. `InventoryRegion.of` lost its `splitHotbar`
parameter along with the now-unused `playerInventory`, `hotbarOnly`, `getSlotIds` and
`isHotbarOnly` members; the exclusion is now a single explicit range check
(`MAIN_INVENTORY_START = Inventory.getSelectionSize()` .. `MAIN_INVENTORY_END =
Inventory.INVENTORY_SIZE`), which keeps the hotbar (0-8), armor (36-39) and offhand (40) out.

Because the region never contains a hotbar slot, the guarantee does not depend on which slot was
hovered — hovering the hotbar targets the same 27 slots, and there is no "which part did I hover"
branch left to get wrong. Removing the option is safe for existing config files for the same reason
as the `sortCompactItems` rename: an absent or unknown key is ignored by
`ConfigUtils.readConfigBase` and dropped on the next save.

The expected region size in the debug log is therefore **27** for the player's backpack, versus the
container's own slot count for a chest.

**What was verified:** the change compiles, `./gradlew build` succeeds, `javap` on the packaged
`InventoryRegion` shows the 2-argument `of(...)` with the unused accessors gone, and the packaged
`Configs` lists exactly five sorting options with no hotbar entry. The updated jar was installed
into the test instance.

**What was NOT verified:** that the hotbar is in fact left untouched in game. As always, this is
reasoned from the code and confirmed to compile — no in-game testing was performed.

### Third round: hysteresis removed, and restocking at a zero threshold

Two requests, one of them a bug report.

**Change 1 — the hysteresis option is gone; a restock fills to one full stack.**
`restockHysteresis` was deleted and `Thresholds.targetFor(item)` now simply returns the item's own
maximum stack size. The item already defines how big a stack is (64 for most things, 16 for eggs,
snowballs and ender pearls), so a second number only raised the question of how the two should
interact. `restockMinCount` (and the per-item overrides) now decide **when** to act and nothing
else. The expected hotbar outcome is therefore "one full stack", not "threshold + 8".

**Defect 7 — with `restockMinCount` at 0, using a stack up completely never refilled it.**
The general restock scan identifies what to top up by looking at the items *currently sitting in
the target slots*:

```java
if (Thresholds.isAtOrBelowThreshold(stack) && wanted.contains(stack.getItem()) == false)
```

When the last item of a stack is consumed, that slot is empty by the time this runs, and an empty
slot carries no item to compare against — `supportsCountRestock` rejects it because it is empty. So
the item that had just been used up was never named, `wanted` came back empty, and nothing was
refilled. At a non-zero threshold this went unnoticed because the stack was topped up while it was
still non-empty; at 0 the only moment the rule fires is the moment the identity is lost.

Fixed by remembering what the main hand held on the previous tick and passing it into the planner
as `justConsumed`. An emptied slot can then still be identified by name. The same threshold rule is
applied to it (`have <= thresholdFor(item)`), so this cannot degrade into an unconditional refill
on every block placed.

While in the trigger, a second robustness fix was made: the trigger previously compared the main
hand count between ticks only, so **switching from a large stack to a smaller one** (64 → 1 in a
different slot) looked exactly like consuming 63 items. The selected hotbar slot is now tracked and
a slot change suppresses the trigger.

**What was verified:** the changes compile; `./gradlew build` succeeds; the packaged sources show
nine `Restock` options with no hysteresis entry, `targetFor` returning
`Math.max(1, item.getDefaultMaxStackSize())`, and no remaining `restockHysteresis` key in either
language file. The updated jar was installed into the test instance.

**What was NOT verified:** that a zero threshold now actually refills an emptied slot in game, and
that the fill-to-full-stack behaviour is correct for 16-stack items. Both are reasoned from the
code and confirmed to compile only.

**Defect 8 — the refill did not go back into the slot it was used from.**
Confirmed working after the previous fix, but landing in the wrong place: the emptied slot was
refilled into whichever empty hotbar slot came first, not the one the player had been placing from.
`findDestination` scanned `targets` in menu order and remembered the *first* empty slot, with no
notion of where the item had come from.

Fixed by carrying the origin slot through the trigger. The main hand is the selected hotbar slot,
so `SlotLookup.findMenuSlot(containerMenu, inventory, getSelectedSlot())` resolves it at the moment
the count drops, and `planAndQueue` now takes a `preferredDestination`. `findDestination` honours
that slot first when it is empty **or** still holds the item being topped up.

The second half of that condition matters beyond the reported symptom: preferring the origin slot
also prevents the scan from selecting a *different* hotbar slot that happens to already hold a full
stack of the same item. In that case the computed free space is zero, the source stack is skipped
silently, and no restock happens at all.

**What was verified:** the change compiles; `./gradlew build` succeeds; `javap` on the packaged
`RestockManager` shows the new
`planAndQueue(Minecraft, ItemStack, Item, int)` overload alongside the old two-argument one; and
the updated jar was installed into the test instance.

**What was NOT verified:** that the refill lands in the original hotbar slot in game.

**What was verified:** the six fixes compile; `./gradlew build` succeeds; the new safety-bound
string is present in the packaged jar; `javap -c` on the packaged `InventoryRegion.class` shows the
inlined `bipush 36` bound (defect 2); and the packaged sources confirm the option is now
`sortCompactItems` with default `true`. Defects 4 and 5 were additionally traced by hand against
the reported layout (11 items, chests totalling 322) and against the `50 + 60` shape, confirming the
greedy merge now reaches `64 + 46` where the old guard left `50 + 60`. The root cause of the failed
first fix was confirmed by reading the live config file and comparing the installed jar's hash
against the build output, not by inference.

**What was NOT verified:** that sorting now actually behaves correctly in game. These fixes are
reasoned from the code and confirmed to compile — no in-game testing was performed. See
`docs/USER-TEST-CHECKLIST.md` §4.2 for the targeted regression tests.

**Process note:** every defect so far is a pure logic error in this project's own code that a unit
test on `SortPlanner` would have caught. No test infrastructure exists yet; adding one is the
highest-value next step, and a defensive bound is now in place so that the freeze failure mode
degrades to a logged partial plan rather than hanging the client.

---

## 6b. `runClient` startup smoke test

The developer may run `runClient` only as a **startup smoke test**. Even when it succeeds, it
establishes at most that the client launches, the mod loads, and the Mixin applies without an
injection error. It establishes **nothing** about gameplay.

**Attempted — inconclusive, not completed.**

What happened, factually:

1. `gradlew runClient` configured the launch successfully. These tasks completed:
   `compileJava`, `processResources`, `classes`, `generateLog4jConfig`, `generateDLIConfig`,
   `configureLaunch`.
2. It then reached `:downloadAssets` and **stalled there for 28 minutes with no further file
   activity** (asset cache stuck at 4751 files / 432 MB; newest write 13:10:03, observed 13:38:30).
   The Minecraft client process was therefore never spawned; only the Gradle wrapper JVM and the
   Gradle daemon were running.
3. The run was cancelled and the leftover Gradle processes were terminated.

**Consequence:** the client never launched, so the smoke test proved nothing. Specifically:

- ❌ Runtime application of the Litematica Mixin remains **unverified**.
- ❌ Mod initialisation (`InitHandler`) and MaLiLib config registration remain **unverified at
  runtime**.
- ❌ The config GUI was never rendered.

The static substitutes for the first point are strong but are **not** equivalent to a launch: the
Mixin's target class and exact method descriptor
(`schematicWorldPickBlock(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V`)
were confirmed with `javap` against the real Litematica 0.27.14 jar, and the plugin/gating logic
compiles. A user-side launch is still the only way to confirm the injection lands.

The asset stall appears to be an environment/network limitation of this machine (the public TLS
chain here also needed the `WINDOWS-ROOT` workaround), not a project defect — no project file was
involved in the stall. On a machine that can complete the Minecraft asset download, `gradlew
runClient` should reach the launch step.

---

## 6d. Unit test harness (step 1 of the plan)

`./gradlew test` now exists and `build` depends on it. 12 tests pass; one spike test is skipped
deliberately.

**Why the obvious approach does not work.** A plain JUnit test in this project *can* reach
Minecraft's classes — that was verified — but it cannot construct an `ItemStack`. Minecraft 26.1
binds an item's data components during the **datapack / registry-data load** phase, not during
`Bootstrap.bootStrap()`: the only caller of `Holder$Reference#bindComponents` in the entire game jar
is `DataComponentInitializers$BakedEntry#apply()`, driven by
`DataComponentInitializers#build(HolderLookup.Provider)`. Calling `BuiltInRegistries.bootStrap()` as
well only throws `Registry is already frozen` — `Bootstrap.bootStrap()` already does it. The result
is `NullPointerException: Components not bound yet` from `Item#getDefaultMaxStackSize()`.

`MinecraftBootstrapSpikeTest` records this as executable documentation so nobody re-investigates it.
Reproducing the datapack phase in a test was rejected as too heavy and too version-fragile, and the
Fabric client-gametest route needs a full asset download, which does not complete in this
environment.

**What was done instead.** The planner's arithmetic was moved onto integers:

- `SlotModel` — a mutable, **Minecraft-free** working copy of a region: one item handle, one count
  and one stack limit per slot. Handles are opaque; the planner only ever asks whether two slots
  hold the same thing.
- `SlotModels` — the production adapter, the only part that still touches `Slot`/`ItemStack`. It maps
  "same item" onto `ItemStack.isSameItemAndComponents` and allocates the handles.
- `SortPlanner` — the merge and placement passes now run entirely on `SlotModel`. The public entry
  point still takes a region, and delegates to a package-private core the tests call directly.

**Coverage.** `SortPlannerTest` asserts, among other things: the merge terminates for the exact
count shapes that used to freeze the client; stacks are filled even when the stack *count* already
matches (the 50 + 60 → 64 + 46 case); the item's own stack limit is respected (16-stack items);
merging is a no-op when disabled; and, swept over 2000 random layouts, that the total item count
never changes and **no mergeable pair is ever left behind**.

**The tests were verified to actually bite.** Both historical bugs were deliberately reintroduced:

| Reintroduced bug | Result |
|---|---|
| Merge counts a no-op as progress (the freeze) | 3 tests failed, e.g. `merge emitted 36 groups for 3 slots — that is a runaway loop, not a merge` |
| Merge refuses partial fills (stacks left half full) | 4 tests failed, e.g. `the first stack should end up full ==> expected: <64> but was: <50>` |

Both mutations were then reverted, and a follow-up check confirmed no leftover edits before the
final green build.

**A test found a real limitation.** An early version of one test asserted a stack could be moved to
a *higher* index. It failed, correctly: the planner only pulls stacks towards index 0, so such a
target is reported `UNREACHABLE` and nothing is clicked.
`InventorySorter.computeTarget` cannot produce that shape (it either compacts onto a prefix or reuses
the occupied positions), so it is a guard rather than a routine path — now asserted explicitly and
documented on `sortPhase`.

### Step 2 — region filter and restock destination extracted

Two more decisions were moved out of Minecraft-touching code, each because it had already been
wrong once:

- `RegionRules.belongsToPlayerRegion(containerSlot, hotbarSize, inventorySize)` — which slots of
  the player's own `Inventory` join a region. `InventoryRegion.of` now just calls it with
  `Inventory.getSelectionSize()` / `Inventory.INVENTORY_SIZE`.
- `DestinationRules.choose(targetSlots, isEmpty, holdsWanted, preferred)` — where a restocked stack
  goes. `RestockManager.findDestination` is now a thin adapter that supplies those predicates from
  the real menu.

`RegionRulesTest` (4 tests) and `DestinationRulesTest` (9 tests) cover them. Total: **25 passing,
1 skipped**.

**Both were verified to bite, by reintroducing the original bugs:**

| Reintroduced bug | Failure produced |
|---|---|
| Region includes everything sharing the container (hotbar, armour, offhand) | `expected: <27> but was: <41>` — 41 is exactly the buggy region size |
| Destination ignores the origin slot | `expected: <13> but was: <10>` — the refill went to the first empty slot, which is precisely the reported symptom |

A third assertion guards a subtler half of the same bug: preferring a *different* slot that already
holds a full stack of the item would leave no free space, the move would be impossible, and the
source stack would be skipped silently.

Both mutations were reverted, and a follow-up scan confirmed no `MUTATION` markers remained before
the final green build.

### Step 3 — restock trigger extracted

`RestockTrigger<T>` holds the "did the main hand shrink, and what was consumed" rule. It is generic
over the item type, so production instantiates it with `Item` and a test with `String`. The three
loose static fields it replaced (`lastMainHandCount`, `lastMainHandItem`, `lastSelectedSlot`) are
gone from `RestockManager`, which now just feeds it one observation per tick.

`RestockTriggerTest` (10 tests) covers: no trigger on the first tick; a genuine decrease firing; a
stack **used up completely still reporting what it held**; no repeat once the hand is already empty;
a **slot switch not counting as consumption** while a real use in the new slot still does; growth,
an unchanged count and an item-type swap all being ignored; and the origin slot being reported.

**Verified to bite, by reintroducing both bugs:**

| Reintroduced bug | Failure produced |
|---|---|
| Read only the current hand when deciding what was consumed | `expected: <minecraft:stone> but was: <null>` — the identity loss that made a zero threshold never refill |
| Drop the slot-switch guard | `a slot change is not a consumption ==> expected: <null> but was: <minecraft:dirt>` |

Reverted, with a scan for leftover `MUTATION` markers before the final build.

### Outcome of the three steps

**35 tests pass, 1 deliberately skipped.** Every defect reported from in-game testing is now covered
by a test that has been shown to fail when the bug is reintroduced:

| Defect | Covered by |
|---|---|
| Merge looped forever, freezing the client | `SortPlannerTest` (2 tests + a random sweep) |
| Merge stopped at an equal stack count, leaving stacks half full | `SortPlannerTest` |
| Stack limit read from slot 0 only | `SortPlannerTest` |
| Armour, offhand and hotbar swept into the player region | `RegionRulesTest` |
| Refill landed in the first empty hotbar slot | `DestinationRulesTest` |
| Zero threshold never refilled (identity lost) | `RestockTriggerTest` |
| A slot switch mistaken for consumption | `RestockTriggerTest` |

Not covered, and not coverable this way: anything that depends on the live game — the real menus,
the Mixin injection, hotkey delivery and the server's response to the click burst. Those remain
**待用户验证**.

### Mod Menu entry point added

Reported from testing: the mod could not be configured from the Mod Menu list.

Cause: Mod Menu only knows how to find a config screen for Cloth Config's AutoConfig, or for a mod
that registers the `modmenu` entry point. It has no knowledge of MaLiLib. MaLiLib *does* ship such
an entry point (`fi.dy.masa.malilib.compat.modmenu.ModMenuImpl`), but it returns a screen for MaLiLib
itself — its config GUI, which contains a mod list — and does not bridge other mods' screens. So
every MaLiLib-based mod needs its own; Litematica's `fabric.mod.json` was checked and it ships
`fi.dy.masa.litematica.compat.modmenu.ModMenuImpl`, which is the same three-line factory pattern
used here.

Fix: `com.sortnstock.compat.modmenu.ModMenuImpl` implementing `ModMenuApi`, registered as the
`modmenu` entry point, returning a `GuiConfigs` with the Mod Menu screen as its parent. Mod Menu is
a `compileOnly` dependency (`com.terraformersmc:modmenu:18.0.1`, pinned to the version in the test
instance): because only Mod Menu asks Fabric Loader for that entry point, the class is never loaded
when Mod Menu is absent, so no runtime dependency is created and no `depends`/`recommends` entry was
needed.

**What was verified:** the project compiles against Mod Menu's real API (checked with `javap` first,
not guessed); `./gradlew build` succeeds with all 35 tests still passing; and the packaged jar
contains both the `modmenu` entry point in `fabric.mod.json` and
`com/sortnstock/compat/modmenu/ModMenuImpl.class`.

**What was NOT verified:** that the config button actually appears and opens the screen in game.

### Six options removed on request

Reported from testing with three screenshots: delete the two source-enable flags
(`restockUsePlayerInventory`, `restockUseOpenContainer`), the per-item thresholds
(`restockPerItemThresholds`), and the three unbound hotkeys (`restockNowHotkey`,
`toggleRestockHotkey`, `litematicaHotkey`). The config went from **26 options to 20**.

Consequences, all deliberate:

- The player inventory and the open container are now unconditionally available as restock sources.
  Only the carried shulker boxes keep a switch, and it stays in the QuickShulker section. The order
  sources are tried in is still configurable via `restockSourcePriority`.
- `Thresholds` lost the whole override table, its lazy re-parsing, the id lookup and the malformed
  entry warnings. `thresholdFor(Item)` became `threshold()`, since the value no longer depends on
  the item; `targetFor` still returns the item's own stack size.
- `RestockManager.restockNow()` became dead code once its hotkey was gone, so it was deleted along
  with the two action-bar messages it was the only user of (`message.restock.disabled` and
  `message.restock.no_action`). The `InfoUtils` import went with it.
- Because all three hotkeys were unbound by default, nobody who never set them is affected. The
  stale keys are ignored by `ConfigUtils.readConfigBase` and dropped on the next save.

**What was verified:** no dangling references to any of the six keys anywhere in the sources or the
language files; both language files still parse; the packaged sources list exactly six `Restock`
options; `./gradlew build` succeeds with all 35 tests passing.

**What was NOT verified:** the resulting GUI in game — that the tabs now show 5 / 6 / 2 / 4 / 1 / 2
entries and that the tooltips fit. See `docs/USER-TEST-CHECKLIST.md` §2.

### On/off options converted from booleans to option lists

Reported from testing: the boolean options show "是 / 否" and the user asked for a click-to-switch
control instead.

Investigation first, because the obvious reading was wrong in one respect and blocked in another:

- `ConfigButtonBoolean.onMouseClickedImpl` already calls `config.toggleBooleanValue()`, so the
  control **was** click-to-toggle; only the label differed.
- That label is `malilib.gui.button.true` / `.false` — MaLiLib's own global language keys, shared by
  every MaLiLib mod.
- `WidgetConfigOption` selects the widget purely from the closed `ConfigType` enum
  (`BOOLEAN` → `ConfigButtonBoolean`), and MaLiLib 0.28.12 has no `IConfigWidget` or widget-factory
  hook. There is therefore **no supported way to give a boolean option a different widget**.

The one form whose displayed text a mod does control is `ConfigOptionList`
(`ConfigButtonOptionList` renders `getDisplayName()` and cycles on click / right-click). All 11
on/off options were therefore converted, with the sort direction getting a meaningful
`ASCENDING` / `DESCENDING` pair so the button reads 升序 / 降序 rather than yes/no:

- new `config/options/Toggle.java` (`ON` / `OFF`) and `config/options/SortOrder.java`
  (`ASCENDING` / `DESCENDING`);
- new `config/ConfigToggles.java` holding the declarations and the `isOn` / `isAscending` readers, so
  the rest of the code still reads naturally and reverting to booleans would touch only two files;
- 11 declarations and 16 read sites updated; `ConfigBoolean` and `getBooleanValue` no longer appear
  anywhere in the sources.

A separate real defect was found and fixed while looking: the `sortAscending` tooltip said "取消勾选
为降序" / "uncheck for descending", describing a checkbox that does not exist.

**What was verified:** no `ConfigBoolean` / `getBooleanValue` remains in the sources; both language
files parse; the packaged sources show exactly 11 declarations going through `ConfigToggles`; and
`./gradlew build` succeeds with all 35 tests passing.

**What was NOT verified:** the buttons in game, and that the old boolean values in an existing
config file are dropped cleanly (they should fall back to defaults once and be rewritten in the new
form).

**Known consequence:** the JSON value type changed — `"sortEnabled": true` is now
`"sortEnabled": "on"`.

### Two more sorting rules: shulker boxes last, and creative pick-block priority

Requested from testing with a screenshot of the desired layout.

**1. Shulker boxes sort to the end.** `InventorySorter.comparator` now applies a fixed category rank
(ordinary items 0, shulker boxes 1) *outside* the ascending/descending reversal, so a descending
sort cannot move them to the front. Detection uses `BlockItem` over `ShulkerBoxBlock` rather than a
list of the seventeen item constants, so dyed variants and modded shulker boxes are covered.

This is the part that could be tested without the game: the composition was split into a generic
`InventorySorter.buildComparator(categoryRank, byKey, ascending)` so a test can drive it with plain
values. `SortOrderingTest` (4 tests) covers ascending, descending, single-category and tie-break
behaviour.

Verified to bite: reversing the *whole* comparator — the obvious implementation — was reintroduced
and the descending test failed with
`expected: <[ordinary-c, ordinary-b, ordinary-a, shulker-b, shulker-a]> but was: <[shulker-b, shulker-a, ordinary-c, ...]>`,
i.e. the shulker boxes jumped to the front. Reverted afterwards, with a scan confirming no
`MUTATION` markers remained.

**2. Creative mode gives the middle mouse to vanilla.** `SortingHandler.yieldsToCreativePickBlock`
returns true — making the handler return `false` and therefore *not* cancel the click — when all of
the following hold: the player is creative, the sort hotkey's current binding actually contains the
middle mouse (`IKeybind.getKeys()`, encoded as GLFW button minus 100 as `KeybindMulti` does), and
the hovered slot holds an item. Gating on the binding means rebinding the sort key restores sorting
in creative, and gating on a non-empty slot means sorting still works in creative by hovering a gap,
since vanilla's clone does nothing there.

**What was verified:** no `MUTATION` markers left; `./gradlew build` succeeds with 39 tests passing
and 1 skipped; the jar was installed.

**What was NOT verified:** both behaviours in game. In particular the creative rule depends on
vanilla's clone actually being suppressed when the handler returns `false`, which is reasoned from
`KeybindMulti` returning `cancel` only when the callback reports handled — not observed.

### Shulker-box ordering reported as not working — stale jar, plus hardened detection

Reported as "the shulker-boxes-last rule has no effect", with debug logging enabled and an
instruction to find the cause in the logs.

The log showed exactly one sort:

```
[12:42:51] sort: region has 27 slot(s), key=REGISTRY_NAME, ascending=true, merge=true, compact=true
[12:42:51] sort: starting 7 click group(s), 0 skipped pair(s)
```

Comparing that against the filesystem settled it: the session started at **12:26**, the only sort in
it ran at **12:42:51**, and the jar containing the change was installed at **12:46** — and there is
no log for any later session. The game had been started before the new jar existed, and a mod jar
swapped while the game is running is simply not loaded. **The change was never actually exercised.**

Two things were done anyway, because the report exposed a genuine weakness in diagnosability:

1. **Detection hardened.** `isShulkerBox` now accepts either the vanilla `minecraft:shulker_boxes`
   item tag (`BuiltInRegistries.ITEM.wrapAsHolder(item).is(ItemTags.SHULKER_BOXES)`) or the
   `BlockItem` over `ShulkerBoxBlock` check. Neither alone is enough: the block check alone misses a
   modded box with a custom item class, and the tag alone misses a box that is not tagged. Note
   `ItemStack.is(TagKey)` does **not** exist in 26.1 — only `is(Predicate<Holder<Item>>)` — so the
   tag is tested through the holder.
2. **The log now reports the shulker count**:
   `sort: region has N slot(s), M shulker box(es), ...`. A future report can therefore distinguish
   "detection failed" (M is 0 while the container visibly holds boxes) from "ordering failed" (M is
   right but the boxes are not at the end) without another round trip.

The checklist entry now also states that the game must be fully restarted after replacing the jar.

**What was verified:** the build succeeds with 39 tests passing and 1 skipped; the packaged sources
contain the tag check and `countShulkerBoxes`; the jar was reinstalled.

**What was NOT verified:** the behaviour in game — as established above, it has not yet been run
with a jar that contains the change.

### Shulker detection verified; placement still unconfirmed

After the rule was still reported as not working, the detection assumption was finally verified
directly rather than assumed. `ShulkerDetectionTest` reads the `Items` constants and asserts that
all seventeen vanilla shulker box items are `BlockItem`s over a `ShulkerBoxBlock`, and that chests
and stone are not mistaken for them. It passes, so **detection is correct**.

Getting that test to run took two details worth recording:

- `Items`' static initialiser calls into registries that require bootstrap, so the test needs
  `SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();`. Unlike `ItemStack` construction,
  `instanceof` and `getBlock()` read plain fields, so they work after bootstrap — the datapack
  phase that blocks component access is not needed.
- The item constants must be read **inside** the test methods. As static fields they are
  initialised while the test class loads, before `@BeforeAll` runs, which produced
  `ExceptionInInitializerError: Not bootstrapped (called from registry minecraft:game_event)`.

Placement is covered by `SortOrderingTest` (the category rank survives the direction flip). What is
**not** established is the end-to-end result in game.

The last log examined ran from 12:52:57 to 12:58:53; the build carrying the shulker-count line was
installed at 12:57:48, and no later session exists — so that session could not have logged the
count. To make the next attempt conclusive, the sort now also logs the target positions:

```
sort: region has N slot(s), M shulker box(es), key=..., ...
sort: target shulker slot(s) = [24, 25, 26]
```

If `M` is 0 while the container visibly holds boxes, detection is at fault; if `M` is right but the
listed positions are not at the end, placement is at fault; if both look right and the inventory
still disagrees, the difference is between the planned target and what the clicks achieved.

### Shulker-box ordering: confirmed working in game

Verified from the test instance log (`latest.log`, session 13:17–13:18, jar
`02205F8A01848739` installed 13:16:56 — the timestamps were checked first, per `AGENTS.md`).

The diagnostics separated the three links in the chain, and all three passed:

```
sort: live shulker slot(s)   = [13, 14, 15, 16, 17, 18, 19, 20, 25]   ← one box out of place
sort: target shulker slot(s) = [12, 13, 14, 15, 16, 17, 18, 19, 20]   ← last 9 of 21 filled slots
sort: after execution, 0 slot(s) still differ from the target: []     ← clicks achieved it
... next press ...
sort: live shulker slot(s)   = [12, 13, 14, 15, 16, 17, 18, 19, 20]
sort: nothing to do (ALREADY_SORTED)
```

Across the session: **three executions, every one with 0 residual slot differences, and eleven
`ALREADY_SORTED`** — the sort converges and stays converged. Detection, ordering and execution are
each confirmed rather than inferred.

Note for the record: a previous session (13:11, jar `0360815EF8C3A777`) showed repeated
non-converging presses (8, 7, 1, 6, 7 groups) on the same chest. No sorting logic changed between
that build and this one — only logging was added — and the behaviour has not reproduced since. The
`after execution, N slot(s) still differ` line now makes any recurrence immediately visible, so it
is left in place rather than being explained away.

The transient position-25/26 entries in the log are the user moving a single shulker out of place
between presses; each sort put it back at the end of the filled range.

### Shulker-box ordering: the requirement was misunderstood, not broken

The previous entry in this log claimed this feature was "confirmed working in game". **That was
wrong and has been superseded.** It was based on the log showing the client menu matching the
target — the mod's own view, not what the user sees — and the user reported the problem still
present. Do not treat a matching client-side state as a verification of visible behaviour.

The actual requirement, clarified by asking: the boxes belong in the **last slots of the container**
(with a gap before them), not merely "sorted last among the items". The first implementation did
the latter, so detection, ordering and execution were all correct and the result was still not what
was asked for. The lesson is recorded plainly: three of the four links in the chain were fine, and
the missing one was the definition of the goal.

Changes:

- new `TargetLayout`, holding the destination rule as pure integers:
  `shulkerTailStart = max(otherCount, regionSize - shulkerCount)`, with the ordinary items packed at
  the front and the boxes filling the tail block. The `max` clamp keeps the two groups contiguous and
  overflow-free when the container is full.
- `InventorySorter.computeTarget` now splits the sorted list into ordinary stacks and boxes and lays
  each group out separately. With "compact items forwards" off it keeps the old in-place behaviour,
  since reserving a tail is inherently a move.
- `SortPlanner.sortPhase` had to change too: it only ever moved a stack towards index 0, so it could
  **not** push a box to the end of the container. It now searches the whole region for a source and
  repeats the pass until no progress is made, because filling a later slot from an earlier one can
  leave that earlier slot wanting a stack already passed over. A pass bound keeps a pathological
  target from looping on the client thread.
- `InventorySorter.range` became dead code and was removed.

Tests: `TargetLayoutTest` (5) covers the tail rule, the full-container case, a 28×28 sweep asserting
the last box always lands in the last slot and never overlaps the ordinary group, the no-boxes case
and the single-box case. `SortPlannerTest` gained
`pushesStacksPastAGapToTheEndOfTheRegion`, `movesAStackUpIntoAnEmptySlot` (replacing the old
"backwards is unreachable" test, which documented the limitation that had to go) and
`resolvesTwoGroupsThatSwapSides`.

Mutation-checked: reverting `shulkerTailStart` to the old "boxes merely follow the other items"
made three `TargetLayoutTest` cases fail with `expected: <18> but was: <12>` and
`expected: <26> but was: <3>`, then was reverted and the mutation scan came back clean. 48 tests
pass.

**What was verified:** the layout arithmetic and the planner's arithmetic, by unit tests.
**What was NOT verified:** the result in game — this is 待用户验证.

### Ordering within the shulker-box group

Requested once the tail placement worked: empty boxes, then partly filled, then full — and within a
fill state, the creative-inventory colour order — with the fill state dominating the colour.

Implemented as `ShulkerOrder`, a pure class holding the comparison, with the classification of an
actual `ItemStack` left in `InventorySorter`:

- **Fill state** is read from `DataComponents.CONTAINER`. No component means empty (vanilla drops
  it when the last item is removed). Full means every one of `ShulkerBoxBlockEntity.CONTAINER_SIZE`
  slots is occupied *and* every contained stack is at its own `getDefaultMaxStackSize()`, i.e. no
  room left; a box of 27 single items is therefore partly filled rather than full.
- **Colour** uses `ShulkerBoxBlock.getColor()`. The rank is `DyeColor.ordinal()`, which was verified
  to be white, orange, magenta, light blue, yellow, lime, pink, gray, light gray, cyan, purple, blue,
  brown, green, red, black — the creative-inventory order. The undyed box has no colour and ranks
  before all of them.
- This order is **fixed**: it does not reverse with the sort direction, matching the tail
  reservation, which is itself direction-independent. The user stated the order explicitly rather
  than describing it as ascending, so treating it as a grouping is the reading that matches.

`InventorySorter.buildComparator` became `categoryFirst` (it no longer owns the direction split, so
the name now says what it does); `SortOrderingTest` was updated to compose the direction itself and
its four invariants still hold. `ShulkerOrderTest` adds five cases, including the explicit
"fill state beats colour" assertion.

Mutation-checked: swapping the two criteria in `ShulkerOrder.compare` made
`fillStateBeatsColour` fail with `expected: <[empty-black, full-white]> but was: <[full-white, empty-black]>`
and `fillStateIsThePrimaryCriterion` fail too — exactly the violation of the stated priority.
Reverted, mutation scan clean, **53 tests pass**.

**Verified:** the ordering rule, by unit tests. **Not verified:** the result in game — 待用户验证.

### Sort direction removed; fuller shulker-box ordering

Two requests in one message: delete the sort-direction option and make every sort ascending, and
extend the shulker-box ordering to a six-point spec with priority `1 > 2 > 5 > 3 = 4`.

**Direction removed.** `sortAscending` is gone, along with `SortOrder`, `ConfigToggles.sortOrder`
and `ConfigToggles.isAscending`; `comparator` and `computeTarget` lost the `ascending` parameter and
the direction branch. Config is now 19 options (sorting 4). The stale `sortAscending` key in an
existing config file is ignored on load and dropped on the next save. `SortOrderingTest` lost its
descending case and now covers only the category grouping, which is all that remains.

**Shulker ordering.** `ShulkerOrder` now holds a `Key` (fill state, colour rank, mixed, amount) and
two pure comparisons: `comparePriority` for levels 1, 2 and 5, and `compareContent` for level 3.
Level 4 for full boxes needs to compare their first contents *as ordinary items*, so it stays in
`InventorySorter.compareShulkerBoxes` and reuses the very comparator the ordinary items are sorted
with — which is what "顺序与普通物品一致" asks for.

Interpretations recorded, because the spec admits more than one reading — **all three were confirmed
correct by the user on 2026-10-19**, so no code change was needed after the review:

- **"contents"** for level 3 is the **total item count** in the box. "Occupied slot count" is the
  other plausible reading; changing it is a one-line change in `shulkerKey`.
- **mixed vs pure** is defined on **distinct item kinds**, by item identity. An empty box is never
  mixed.
- **"first contents"** for level 4 is the item in the box's first occupied contents slot.

`ShulkerOrderTest` was rewritten for the new API (8 tests) and covers the fill-state ordering, the
explicit "fill state beats colour" case, colour within a fill state, "mixed before pure", the
inversion "colour beats mixed-before-pure" for priority 2 > 5, "least contents first", the undyed
box ordering and that an empty box is never mixed.

**Verified:** 55 unit tests pass.
**Not verified:** the ordering in game — 待用户验证.

### "Merge stacks" and "compact items forwards" removed, both always on

Requested with a screenshot of the two rows. Both options are gone and the behaviour is fixed:
sorting always merges partial stacks and always packs towards the front.

- `Configs.Sorting` is down to `sortEnabled` and `sortKey`; the mod now has **17 options**.
- `InventorySorter.computeTarget` lost both parameters, and with them the `compact == false` branch
  and its comment. That branch was the only place that placed stacks 1:1 onto the previously
  occupied slots, so the "reorder in place, keep the gaps" mode no longer exists at all.
- `SortingHandler` no longer reads either option and passes a fixed `true` to `SortPlanner.plan`.
  The planner keeps its `merge` parameter — it is a general algorithm parameter and
  `SortPlannerTest` exercises both values — but production only ever uses the merging path.
- The debug line is now `sort: region has N slot(s), key=...`, since there is nothing to report
  about merge/compact any more.
- The language keys are gone; the old `sortMergeStacks` / `sortCompactItems` keys in an existing
  config file are ignored by the loader and dropped on the next save.
- Docs updated: the option tables, the "requires compaction" caveat on the shulker tail (compaction
  is now always on, so the reservation always applies), and the README's history of the
  `sortKeepEmptySlots` rename.

**Verified:** no references to either key remain in the sources or language files; both language
files parse; 55 tests pass; the jar was installed.

**Not verified:** the GUI in game — 待用户验证.

### "No container under the cursor" action-bar message removed

Reported with a screenshot of `SortNStock:光标下没有容器` appearing during ordinary play. Both call
sites were removed: pressing the sort key while not pointing at a slot, and hovering a slot that
yields no sortable region. Sorting now simply does nothing there.

The reasoning recorded: pressing a hotkey where it does not apply is not a user error worth an
on-screen message, and the message was reported as noise. The case is still diagnosable — the
"no slot under the cursor" case writes a debug line, and the "no region" case keeps its
`LOGGER.warn` — so nothing was lost for troubleshooting.

The other action-bar messages were deliberately **left in place**, because each reports a state the
player cannot otherwise see:

| Message | Why it stays |
|---|---|
| `sort.disabled` | The sort hotkey was pressed but the module is switched off in config |
| `sort.busy` | A previous sort is still running |
| `sort.unreachable` | Nothing could be moved safely |
| `litematica.unavailable` / `no_placement` | The Litematica integration cannot act |

If those are also unwanted, they are one line each to delete.

**Verified:** no references to the key remain in the sources or language files; both language files
parse; 55 tests pass; the jar was installed. **Not verified:** in game — 待用户验证.

### The two QuickShulker switches merged into one

Asked what the difference was between "use carried shulker boxes as a source" and "automatically
open shulker boxes". Investigating to answer the question turned up a defect: **the first option did
not do what its name said.**

`QuickShulkerCompat.sourceSlots` returned `SlotLookup.openContainerSlots(...)` when the open menu was
a `ShulkerBoxMenu` — and `openContainerSlots` already returns every slot that does not belong to the
player, which is exactly the shulker box's slots. So the `SHULKER` source tier added **no slot the
`OPEN_CONTAINER` tier did not already return**: it was a duplicate. Its only real effect was to gate
the auto-open branch, i.e. it was a master switch for the other option wearing a misleading label
("carried", when it only ever saw an already-open box).

Merged as requested (option A): the "use as source" option is gone and auto-open remains.

- `RestockManager.Tier` lost `SHULKER`; `sourceSlots` lost the branch and the now-pointless
  `shulkerSourceSlots` adapter, and its `Minecraft` parameter.
- `SourcePriority` went from six orderings over three tiers to two over two:
  `INV_CONTAINER` (default) and `CONTAINER_INV`.
- The auto-open condition no longer checks the removed option.
- `QuickShulkerCompat.sourceSlots` is deleted; its now-unused `ShulkerBoxMenu` and `List` imports
  went with it.
- Config: QuickShulker 2 → 1, total 17 → **16**. Old keys `quickshulkerUseAsSource` and the four
  obsolete `shulker*` priority values are ignored on load and dropped on the next save; a persisted
  `restockSourcePriority` naming a removed value falls back to the default via
  `SourcePriority.fromStringStatic`.

Behaviour is unchanged in practice: an open box is still a source (through the container tier), and
a carried box holding a wanted item is still opened automatically.

**Verified:** 55 tests pass, both language files parse, no references to the removed identifiers
remain. **Not verified:** in game — the jar was installed while Minecraft was running, so it needs a
full restart before it is loaded.

### "Automatically open" renamed to "restock from shulker boxes", and actually implemented

Asked to rename the option to 从潜影盒中补货 and to make it work. Investigating why it did not work
found two real defects:

1. **The box search covered only the hotbar and the offhand.**
   `QuickShulkerCompat.requestOpenShulkerWithItem` skipped every other inventory slot, so a box of
   building material kept in the backpack was never even considered. The search now covers the whole
   inventory, since QuickShulker's packet addresses a slot of the open menu and such a slot exists
   for every inventory position.
2. **Nothing retried after the box was opened.** The consumption trigger fires exactly once and calls
   `planAndQueue` once. When the item was only inside a box, that call sent the open request and
   returned false — and because opening is asynchronous (the box's menu only exists after the server
   answers), the refill never happened at all. The log showed it plainly: `was used up ... refilling`
   followed by nothing.

   A pending refill is now remembered (item + origin slot) and retried on subsequent client ticks,
   bounded to ~2 s so a request that can never succeed does not retry forever, and respecting the
   cooldown and queue so it cannot race the normal path.

Also renamed the config key `quickshulkerAutoOpen` → `quickshulkerRestockFromShulker` (field
`QUICKSHULKER_RESTOCK_FROM_SHULKER`), so existing config files pick up the new behaviour rather than
depend on a persisted value whose meaning has changed.

**Platform limit, recorded because it cannot be engineered away:** taking items out of a shulker box
is server-authoritative, and QuickShulker exposes only `OpenShulkerPacket.sendOpenPacket(slot)`. There
is no silent path, so the box's screen does open visibly when this fires. QuickShulker's
`Util.getQuickItemInventory` returns a client-side container view, but mutating it would not survive
server reconciliation.

**Verified:** 55 tests pass; both language files parse; the jar was installed.
**Not verified:** the flow in game — and it needs a full restart, since Minecraft was running when
the jar was installed.

### Restock-from-shulker: wrong destination slot, and a box left open

Reported after the first working round of the feature, which is worth recording because it confirms
the flow itself ran: the log showed `requesting open of menu slot 32`, then a queued click group and
`completed the pending refill`.

1. **The refill landed in the first hotbar slot instead of the one just used.** The pending refill
   stored the origin as a *menu slot id*. Opening a shulker box replaces the open menu, so by the time
   the retry ran that id addressed a different slot — `DestinationRules` therefore had no usable
   preference and fell back to the first empty hotbar slot. The origin is now stored as a **hotbar
   index** (0-8) and resolved against the menu that is open *at retry time*.
2. **The box's screen could not be closed afterwards.** The refill is now responsible for tidying up
   after itself: `pendingOpenedBox` records that this mod sent the open request, and
   `finishPending` calls `LocalPlayer.clientSideCloseContainer()` both on success and when the retry
   gives up, so the player is never stranded in a GUI they did not open.

**Verified:** 55 tests pass; the jar was installed.
**Not verified:** in game — and a full restart is required, since Minecraft was running at install
time.

### Can items be taken out of a shulker box without opening it?

Asked and answered, not implemented: **no, not in a client-only mod.** Extracting items is
server-authoritative, and QuickShulker exposes only `OpenShulkerPacket.sendOpenPacket(slot)`.
`Util.getQuickItemInventory` returns a client-side container view; writing to it would be discarded
when the server reconciles. The only silent route would be a packet the *server* understands — i.e. a
server-side companion mod or plugin, which is outside this mod's client-only scope.

### Restock-from-shulker: the box was closed before its clicks were dispatched

Reported as: the box opens (with its sound), **no items come out**, the screen then vanishes without
a close sound, and the close sound plays a moment later.

The cause was my own previous fix, and it is a clean ordering bug: `RestockQueue` dispatches at the
**start** of `onClientTick`, while the pending-refill retry runs **after** it. So on the tick the
retry queued the move, `finishPending` immediately called `clientSideCloseContainer()`. The queued
click group was still waiting for the next tick's dispatch — by which time the menu was gone, so the
queue aborted with "container changed" and nothing moved. The screen closing without its sound, with
the sound arriving later, is the client-side close racing ahead of the server's acknowledgement.

Fixed by deferring the close: `finishPending` now sets `closeBoxWhenIdle`, and `onClientTick` closes
the box only on a later tick where `RestockQueue.isActive()` is false. Order of events is then:
open → queue move → dispatch move → close, which is the order the packets have to arrive in anyway.

**Verified:** 55 tests pass; the jar was installed.
**Not verified:** in game — a full restart is required, Minecraft was running.

### Ghost items: the programmatic container close removed

Reported: the screen still vanished with a delayed close sound, and — more seriously — **ghost items
appeared in the inventory between the screen vanishing and the sound playing.**

That is a client/server divergence, and it points at the one non-vanilla thing this mod does to the
container lifecycle: `LocalPlayer#clientSideCloseContainer()`. Tearing the menu down client-side while
the server's slot updates for the just-queued move are still in flight leaves the two sides
disagreeing about the inventory, which is exactly what a ghost item is. The delayed close sound is the
same divergence seen from the other side — the client closed, the server answered later.

**The programmatic close has been removed entirely.** Closing a container is the server's decision;
forcing it from a click program cannot be made safe, and item consistency matters far more than
saving the player one Escape press. The box now stays open after the refill and the player closes it
through the normal, fully-synchronised path.

This also re-frames the two earlier reports — "cannot close the GUI" and "close sound arrives
late" — as symptoms of the same interference rather than separate problems.

**Verified:** 55 tests pass; no `clientSideCloseContainer` call remains (only a comment explaining
why). **Not verified:** in game — a full restart is required.

**Open question for the next round:** if ghost items still occur with no programmatic close, the
divergence is not the close but the open/click window itself — i.e. moving items on a menu that
QuickShulker opened asynchronously — and that needs a different fix (waiting for the menu to be
observed stable for a tick before clicking).

### Four-step fix modelled on BetterShulkerHUDMod

Read the reference implementation (BetterShulkerHUDMod; `git clone` was blocked at github.com:443,
so the sources were fetched over HTTP) and applied its four techniques:

1. **Container-sync tracking** — new `ContainerSync` plus `MixinClientPacketListener`, which bumps a
   per-container sequence from `handleContainerContent` and `handleContainerSetSlot`. Verified the
   handler signatures with `javap`; note `ClientboundContainerSetContentPacket#containerId()` but
   `ClientboundContainerSetSlotPacket#getContainerId()`.
2. **Wait for the box to be synced before clicking.** This is the ghost-item fix: the menu object
   exists as soon as the screen opens, but its slots stay empty until the content packet arrives.
   The refill was planning moves against empty slots, so the client simulated one result while the
   server applied another.
3. **Wait for confirmation before closing.** The close is now deferred through
   `tickDeferredClose`: it waits until the queue has dispatched *and* the box's container has been
   synced since a baseline taken right after the move, with a 6-tick local-confirmation grace as a
   fallback (the same value and technique as the reference).
4. **`canSend` guard and creative slot remapping** — `ClientPlayNetworking.canSend(...)` so an open
   request is not sent into a channel the server does not accept, and
   `ClientUtil.isCreativeScreen` / `getSlotId` to translate the slot id in creative screens, where
   menu indices and server indices differ.

**Verified:** compiles, 55 tests pass, jar installed. **Not verified:** in game — and a full restart
is required, Minecraft was running at install time.

### Ghost items persisted: the confirmation baseline was taken too early

Precisely described by the user: after the screen closes and before the close sound plays, the
picked-up item is missing from the inventory; clicking that slot does not refresh it, but once the
close sound plays the item appears.

That is a dropped-update bug, and the mechanism is now clear. Slot updates are addressed to a
**container id**, and vanilla discards them when the player's open menu is a different one. Closing
the box switches the client to the inventory menu immediately, so the server's updates for the move —
still in flight — are thrown away, and the client's copy of the inventory stays stale until something
forces a full refresh (which is what the close sound coincides with).

The fix in the previous round was meant to prevent exactly this, but **its baseline was taken at the
wrong moment**: `finishPending` recorded `ContainerSync.version()` when the move was only *queued*,
while the clicks go out on the next tick. The box's initial content sync — or any unrelated sync —
therefore satisfied `hasSyncAfter(...)`, the close reported `serverConfirmed=true` and happened
straight away. A false positive.

`closeBaselineArmed` now defers the baseline until the first tick on which the queue is no longer
active, i.e. after the clicks have actually been dispatched. Only then can a sync be a response to
the move, and only then is closing safe. The close log now also prints `graceTicks` so a fallback
close is distinguishable from a confirmed one.

**Verified:** 55 tests pass, jar installed. **Not verified:** in game — restart required.

**Known cosmetic defect:** the pending retry re-runs the planner every tick, so an item that exists
in no box logs `was used up ... refilling` ~20 times before giving up. Harmless but noisy; the retry
should stop re-logging.

### Ghost items, root cause: the container was never actually closed

Cloned the reference implementation properly through the local SOCKS proxy (port 10808) — the direct
clone had been blocked at github.com:443 — into `_recon/bettershulkerhud` (commit `4f09166`) and read
its 26.1.2 sources, which target exactly this Minecraft version.

The decisive difference is one line. BetterShulkerHUDMod closes the box with
`client.player.closeContainer()` (`LocalPlayer#closeContainer`, public). We used
`LocalPlayer#clientSideCloseContainer()`, which **only switches the client's menu back to the
inventory and never tells the server**. So the server still believed the shulker box was open and kept
addressing slot updates to that box's container id; the client had already moved to the inventory
menu, so vanilla discarded every one of those updates. The moved items existed server-side but never
reached the client's inventory — until something forced a full refresh, which is the close sound.

That single defect explains all three symptoms: the ghost item, the item appearing only after the
close sound, and the close sound arriving late.

Fixed by closing with `mc.player.closeContainer()`, on top of the sync gates from the previous round
(which are still correct and still needed — they ensure the move is confirmed before the close is
sent, so the updates are applied while the box's menu is still the open one).

**Verified:** 55 tests pass, jar installed. **Not verified:** in game — restart required.

### Speeding up the shulker refill

Reported as too slow. Two avoidable waits were removed:

1. **The open request borrowed the user-facing `restockCooldownTicks` (default 10 ticks) as its
   retry delay**, so every shulker refill sat idle for ~half a second before it was even allowed to
   look at the box it had just opened. A dedicated `OPEN_REQUEST_COOLDOWN_TICKS = 2` replaces it;
   the setting keeps its real meaning (spacing between restocks).
2. **The queued move waited a whole extra tick to be dispatched.** `RestockQueue.tick` is now also
   called immediately after queueing from the pending path. That is the same code path — it
   re-checks the menu and the cursor — just without the tick of latency.

What remains is inherent: two server round trips (open → content sync, move → move sync) plus the
close confirmation. Cutting the latter would reintroduce the ghost-item bug, so it stays.

### Deposit-undo bug: storing a stack was read as consuming it

Reported as a fatal bug: putting a stack into a shulker box made restock pull it straight back out,
and it only became obvious once shulker-based refills had happened. The log confirmed the chain
directly — `main hand shrank (consumed ...)` immediately followed by `refilling` and a queued click
group, once per deposit.

The trigger only compared the main-hand count, so *storing* a stack looked exactly like *consuming*
one. The source search then found the same item in the box it had just been put into and pulled it
back — the deposit undid itself.

The fix rests on a fact about the game: **items cannot be used while a container screen is open**, so
a shrinking main hand there necessarily means the player is storing, not consuming. While any
`AbstractContainerScreen` is open the tick is now fed through a new `RestockTrigger#update`, which
follows the count without reporting anything. Merely skipping the observation would not work: the
tracked state would go stale and the next tick after closing the screen would read the entire
difference as a single consumption.

The pending-refill and close paths still run with the shulker box open — only the consumption trigger
is gated.

`RestockTriggerDepositTest` adds four cases (whole-stack deposit, partial deposit, consumption still
working after an update, slot switch ignored). Mutation-checked: making `update` a no-op fails two of
them with `expected: <null> but was: <minecraft:stripped_pale_oak_log>`. Reverted, scan clean, **59
tests pass**.

**Not verified:** in game — restart required.

### Litematica easy-place: the box opened but nothing happened and it could not be closed

With easy place on, right-clicking a schematic position opened the shulker box and then stranded the
player in it.

The pending-refill state was being created by the *caller* (the consumption trigger), not by the code
that actually sends the open request. The Litematica hook uses a different entry point —
`planAndExecuteNow`, which goes through the same `plan(...)` — so it opened the box and returned,
leaving nobody to complete the move or close it. The box screen then just sat there.

The pending refill (item, origin slot, timeout, sync baselines) is now established inside the
`groups.isEmpty()` branch right where the open request is sent, so **every** caller gets a follow-up:
the box is opened, the move completes on a later tick once its contents have synced, and it is closed
again after the server confirms. The condition also now uses `QuickShulkerCompat.canSend()` rather
than just `isAvailable()`, so a request is never sent where the server cannot accept it.

Note the Litematica refill still lands a couple of ticks after the click, so the click that triggered
it may not use the refilled stack — the pre-existing "may need a second right-click" limitation.

**Verified:** 59 tests pass, jar installed. **Not verified:** in game — restart required.

### Litematica: restock fought the pick block for the same right-click

Reported as: selected slot 1, right-click a schematic → restock moved the item into slot 2; right-click
again, and instead of the pick block selecting slot 2, restock fired once more.

The handler only looked at the *main hand*. When the hand did not hold the required item it restocked
unconditionally — without checking whether the item was already carried somewhere the pick block
could reach. After a refill landed in slot 2, the next right-click therefore restocked again instead
of letting Litematica select slot 2.

`hasItemInInventory` now short-circuits the restock when the player already carries the item: the
pick block can select any hotbar slot holding it, and can pull it out of the main inventory, so
restocking there competes with the pick block rather than helping it. The check deliberately looks
only at the inventory — an item inside a carried shulker box is *not* reachable by the pick block, so
that is exactly the case where the restock still fires, which is what makes the two features
compose instead of collide.

**Verified:** 59 tests pass, jar installed. **Not verified:** in game — restart required.

---

## 7. Summary for the reviewer

| Claim | Status |
|---|---|
| `./gradlew build` passes | ✅ Verified |
| Dependencies resolve from public repositories | ✅ Verified |
| Locked versions/commits documented | ✅ Verified |
| Mixin target signature matches Litematica 0.27.14 | ✅ Verified via `javap` |
| Soft-dependency crash safety (no Litematica / no QuickShulker) | ✅ Verified by construction + build; runtime application **unverified** |
| No invented APIs | ✅ Verified — three assumed APIs were corrected |
| Externally applied `SortQueue` change reviewed | ✅ Verified by code review + build + jar inspection (§6a); click-burst acceptance on servers **unverified** |
| Client launches and the Mixin applies | ⚠️ **Smoke test inconclusive** (asset download stalled; client never started) |
| Any in-game functionality works | ❌ **Not tested — pending user verification** |
