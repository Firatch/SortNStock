package com.sortnstock.config;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import com.sortnstock.Reference;
import com.sortnstock.SortNStock;
import com.sortnstock.config.options.RestockTargetSlots;
import com.sortnstock.config.options.SortKey;

/**
 * All SortNStock configuration options.
 *
 * <p>Persistence, the config GUI, and hotkey conflict detection are all owned by MaLiLib —
 * this class only declares the options and reads/writes its own JSON file in the shape
 * MaLiLib expects.</p>
 */
public class Configs implements IConfigHandler
{
    private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";

    public static class Sorting
    {
        private static final String KEY = Reference.MOD_ID + ".config.sorting";

        public static final ConfigOptionList SORT_ENABLED           = ConfigToggles.toggle("sortEnabled", true, KEY);
        public static final ConfigOptionList SORT_KEY               = new ConfigOptionList("sortKey", SortKey.REGISTRY_NAME).apply(KEY);

        // Note: "merge stacks" and "compact items forwards" used to be options here. Both are now
        // fixed behaviour that is always on — a tidy-up is expected to merge partial stacks and pack
        // the items towards the start of the container, so they were not decisions worth asking the
        // player to make. Their old keys are ignored by the config loader and dropped on the next
        // save.

        // Note: there is deliberately no option for the hotbar. Sorting the player's own inventory
        // never touches the hotbar, the armor slots or the offhand — see InventoryRegion. The
        // hotbar is a hand-arranged quick-access bar, so rearranging it is not a preference worth
        // offering.

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                SORT_ENABLED,
                SORT_KEY
        );
    }

    public static class Restock
    {
        private static final String KEY = Reference.MOD_ID + ".config.restock";

        public static final ConfigBooleanHotkeyed RESTOCK_ENABLED = new ConfigBooleanHotkeyed("restockEnabled", false, "").apply(KEY);
        public static final ConfigInteger    RESTOCK_MIN_COUNT            = new ConfigInteger("restockMinCount", 0, 0, 64).apply(KEY);

        // There is deliberately no hysteresis option. A restock always refills the slot to one
        // full stack for that item — 64 for most items, 16 for things like eggs and snowballs —
        // so the gap between "below the threshold" and "full" is whatever the item allows. The
        // minimum count alone decides when to act.

        public static final ConfigOptionList RESTOCK_TARGET_SLOTS         = new ConfigOptionList("restockTargetSlots", RestockTargetSlots.HOTBAR).apply(KEY);
        public static final ConfigInteger    RESTOCK_COOLDOWN_TICKS       = new ConfigInteger("restockCooldownTicks", 10, 0, 200).apply(KEY);
        public static final ConfigInteger    RESTOCK_MAX_OPS_PER_TICK     = new ConfigInteger("restockMaxOpsPerTick", 1, 1, 9).apply(KEY);

        // Notes on what is deliberately absent here:
        //
        // - No trigger-selection option. Restocking has exactly one trigger (the main hand stack
        //   getting smaller), so a switch for it would have nothing to select between.
        // - No per-source enable flags for the player inventory or the open container. Both are
        //   simply always available; only the carried shulker boxes can be switched off, and that
        //   lives in the QuickShulker section. The order they are tried in is still configurable
        //   through a fixed order (inventory first).
        // - No per-item thresholds. restockMinCount is the only threshold, and a restock always
        //   fills to one full stack, so a per-item override only had two ways to be confusing.

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                RESTOCK_ENABLED,
                RESTOCK_MIN_COUNT,
                RESTOCK_TARGET_SLOTS,
                RESTOCK_COOLDOWN_TICKS,
                RESTOCK_MAX_OPS_PER_TICK
        );
    }

    /**
     * QuickShulker integration, kept in its own section and GUI tab so the shulker-specific
     * behaviour can be configured (or switched off) independently of the restock core.
     */
    public static class QuickShulker
    {
        private static final String KEY = Reference.MOD_ID + ".config.quickshulker";

        // Note: there used to be a second switch here, "use carried shulker boxes as a source".
        // It was removed because it contributed no source slots — an open shulker box is already
        // covered by the "open container" tier — and because the one thing that did matter, opening
        // a carried box so its contents become reachable, is what the option below does.
        //
        // The key was renamed from "quickshulkerAutoOpen": the option is not merely about opening a
        // box, it is about restocking out of it, and the rename makes existing files pick the new
        // behaviour up instead of depending on a persisted old value.
        public static final ConfigBooleanHotkeyed QUICKSHULKER_RESTOCK_FROM_SHULKER =
                new ConfigBooleanHotkeyed("quickshulkerRestockFromShulker", false, "").apply(KEY);

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                QUICKSHULKER_RESTOCK_FROM_SHULKER
        );
    }

    public static class Litematica
    {
        private static final String KEY = Reference.MOD_ID + ".config.litematica";

        public static final ConfigBooleanHotkeyed LITEMATICA_AUTO_RESTOCK = new ConfigBooleanHotkeyed("litematicaAutoRestock", false, "").apply(KEY);
        public static final ConfigOptionList LITEMATICA_FROM_SHULKER_BOX      = ConfigToggles.toggle("litematicaRightClickRestock", true, KEY);
        public static final ConfigOptionList LITEMATICA_LOW_COUNT        = ConfigToggles.toggle("litematicaLowCountRestock", true, KEY);

        // There is deliberately no trigger-timing option. The integration has exactly one
        // evaluation point: the head of Litematica's pick block, reached from a right-click on a
        // schematic position. That single point is what makes the restock land before the slot
        // switch, so the same click can use the restocked stack. Offering "on tick" as an
        // alternative only invited a mode that could never help the current click.
        public static final ConfigInteger    LITEMATICA_COOLDOWN_TICKS   = new ConfigInteger("litematicaCooldownTicks", 5, 0, 200).apply(KEY);

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                LITEMATICA_AUTO_RESTOCK,
                LITEMATICA_FROM_SHULKER_BOX,
                LITEMATICA_LOW_COUNT,
                LITEMATICA_COOLDOWN_TICKS
        );
    }

    public static class Debug
    {
        private static final String KEY = Reference.MOD_ID + ".config.debug";

        public static final ConfigBooleanHotkeyed DEBUG_LOGGING = new ConfigBooleanHotkeyed("debugLogging", false, "").apply(KEY);

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                DEBUG_LOGGING
        );
    }

    @Override
    public void load()
    {
        loadFromFile();
    }

    @Override
    public void save()
    {
        saveToFile();
    }

    public static void loadFromFile()
    {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);

        if (Files.exists(configFile) && Files.isReadable(configFile))
        {
            JsonElement element = JsonUtils.parseJsonFile(configFile);

            if (element != null && element.isJsonObject())
            {
                JsonObject root = element.getAsJsonObject();

                ConfigUtils.readConfigBase(root, "Sorting", Sorting.OPTIONS);
                ConfigUtils.readConfigBase(root, "Restock", Restock.OPTIONS);
                ConfigUtils.readConfigBase(root, "QuickShulker", QuickShulker.OPTIONS);
                ConfigUtils.readConfigBase(root, "Litematica", Litematica.OPTIONS);
                ConfigUtils.readConfigBase(root, "Debug", Debug.OPTIONS);
                ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);

                SortNStock.debugLog("loadFromFile(): loaded '{}'", configFile.toAbsolutePath());
            }
            else
            {
                SortNStock.LOGGER.error("loadFromFile(): failed to parse config file '{}'", configFile.toAbsolutePath());
            }
        }
    }

    public static void saveToFile()
    {
        Path dir = FileUtils.getConfigDirectory();

        if (Files.exists(dir) == false)
        {
            FileUtils.createDirectoriesIfMissing(dir);
        }

        if (Files.isDirectory(dir))
        {
            JsonObject root = new JsonObject();

            ConfigUtils.writeConfigBase(root, "Sorting", Sorting.OPTIONS);
            ConfigUtils.writeConfigBase(root, "Restock", Restock.OPTIONS);
            ConfigUtils.writeConfigBase(root, "QuickShulker", QuickShulker.OPTIONS);
            ConfigUtils.writeConfigBase(root, "Litematica", Litematica.OPTIONS);
            ConfigUtils.writeConfigBase(root, "Debug", Debug.OPTIONS);
            ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);

            JsonUtils.writeJsonToFile(root, dir.resolve(CONFIG_FILE_NAME));
        }
        else
        {
            SortNStock.LOGGER.error("saveToFile(): config directory '{}' does not exist", dir.toAbsolutePath());
        }
    }
}
