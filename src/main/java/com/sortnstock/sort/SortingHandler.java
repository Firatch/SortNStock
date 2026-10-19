package com.sortnstock.sort;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.lwjgl.glfw.GLFW;

import com.sortnstock.SortNStock;
import com.sortnstock.config.ConfigToggles;
import com.sortnstock.config.Configs;
import com.sortnstock.config.Hotkeys;
import com.sortnstock.config.options.SortKey;

/**
 * Entry point for the inventory sorting module.
 */
public final class SortingHandler
{
    /**
     * How MaLiLib encodes a mouse button in a keybind: {@code InputConstants} value minus 100 (see
     * {@code KeybindMulti}). The middle mouse button is GLFW button 2, so this is -98.
     */
    private static final int MIDDLE_MOUSE_KEY_CODE = GLFW.GLFW_MOUSE_BUTTON_MIDDLE - 100;

    private SortingHandler()
    {
    }

    /**
     * Sorts the container the mouse cursor is currently over.
     *
     * @return true if a sort program was successfully queued
     */
    public static boolean sortHoveredContainer()
    {
        if (ConfigToggles.isOn(Configs.Sorting.SORT_ENABLED) == false)
        {
            // Silent: an action-bar message for "the module is switched off" was reported as noise.
            // The reason is still traceable in the log, which is where a diagnostic belongs.
            SortNStock.debugLog("sort: sorting is disabled in the config, ignoring the hotkey");
            return false;
        }

        final Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.screen == null)
        {
            return false;
        }

        if ((mc.screen instanceof AbstractContainerScreen<?>) == false)
        {
            return false;
        }

        final AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.screen;
        final Slot hovered = getHoveredSlot(screen);

        if (hovered == null)
        {
            // Silent: pressing the sort key while not pointing at a slot is not a user error worth
            // an on-screen message. The previous action-bar message here was reported as noise.
            SortNStock.debugLog("sort: no slot under the cursor");
            return false;
        }

        if (SortQueue.isActive())
        {
            // A previous sort is still running; do not stack programs on top of each other.
            SortNStock.debugLog("sort: a sort is already in progress, ignoring the hotkey");
            return false;
        }

        // In creative mode the middle mouse button is vanilla's clone / pick-block. When sorting is
        // bound to it, sorting has to step aside — otherwise the click is cancelled and pick-block
        // never happens. Returning false here leaves the click untouched, so vanilla handles it.
        if (yieldsToCreativePickBlock(mc, hovered))
        {
            SortNStock.debugLog("sort: creative pick-block takes priority, not sorting");
            return false;
        }

        final InventoryRegion region = InventoryRegion.of(screen.getMenu(), hovered);

        if (region == null)
        {
            SortNStock.LOGGER.warn("sort: could not determine the inventory region for the hovered slot");
            return false;
        }

        final SortKey key = (SortKey) Configs.Sorting.SORT_KEY.getOptionListValue();

        // Logged on every sort. The region size is the quickest way to spot a region that has swept
        // in slots it should not have (27 = player backpack, otherwise the container's slot count).
        // The shulker count is here so that "shulker boxes were not moved to the end" can be told
        // apart from "no shulker boxes were detected": if this reads 0 for a container that clearly
        // holds some, detection is what broke, not the ordering.
        // Merging partial stacks and compacting towards the front are always on: they used to be
        // options, but a "tidy up" is expected to do both.
        SortNStock.debugLog("sort: region has {} slot(s), key={}", region.size(), key);

        if (Configs.Debug.DEBUG_LOGGING.getBooleanValue())
        {
            // Which container is actually being sorted. "region has 27 slot(s)" is ambiguous
            // between a single chest and the player's own 27-slot backpack, and sorting the wrong
            // one would look exactly like "nothing happened" while staring at the chest.
            SortNStock.debugLog("sort: region owner = {} (player inventory: {})",
                    region.slot(0).container.getClass().getSimpleName(),
                    region.slot(0).container instanceof net.minecraft.world.entity.player.Inventory);

            SortNStock.debugLog("sort: live shulker slot(s) = {}",
                    InventorySorter.liveShulkerPositions(region));
        }

        final List<ItemStack> target = InventorySorter.computeTarget(region, key);

        // Where the target puts the shulker boxes. Combined with the detection count above, this
        // says in one line whether the rule failed at detection or at placement.
        if (Configs.Debug.DEBUG_LOGGING.getBooleanValue())
        {
            SortNStock.debugLog("sort: target shulker slot(s) = {}",
                    InventorySorter.shulkerPositions(target));
        }

        final SortPlanner.Result result = SortPlanner.plan(region, target, true);

        if (result.groups().isEmpty())
        {
            // Either nothing needed moving, or the arrangement could not be reached. Both are
            // non-destructive outcomes, so give audible feedback and change nothing. No action-bar
            // message: sorting is a hotkey action and the sound already says "nothing to do".
            SortNStock.debugLog("sort: nothing to do ({})", result.reason());
            SortFeedback.playNothingToDo();

            return false;
        }

        SortNStock.debugLog("sort: starting {} click group(s), {} skipped pair(s)",
                result.groups().size(), result.skipped());

        SortQueue.start(region, target, result.groups());
        SortFeedback.playSortStarted();

        return true;
    }

    /**
     * Whether this click should be left to vanilla's creative clone / pick-block instead of sorting.
     *
     * <p>Three conditions all have to hold:</p>
     *
     * <ul>
     *   <li>the player is in creative, where the middle mouse button clones the hovered stack;</li>
     *   <li>the sort hotkey is actually bound to the middle mouse — if it has been rebound to some
     *       other key, that key cannot clone anything and sorting should still work in creative;</li>
     *   <li>the hovered slot holds something. On an empty slot vanilla's clone does nothing, so
     *       there is no conflict and sorting proceeds — which also keeps sorting reachable in
     *       creative by hovering a gap.</li>
     * </ul>
     */
    private static boolean yieldsToCreativePickBlock(Minecraft mc, Slot hovered)
    {
        return mc.player != null &&
               mc.player.isCreative() &&
               hovered.hasItem() &&
               sortsOnMiddleMouse();
    }

    /** True when the sort hotkey's current binding includes the middle mouse button. */
    private static boolean sortsOnMiddleMouse()
    {
        for (int key : Hotkeys.SORT_INVENTORY.getKeybind().getKeys())
        {
            if (key == MIDDLE_MOUSE_KEY_CODE)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Reads the hovered slot through SortNStock's own accessor mixin
     * ({@link com.sortnstock.mixin.IMixinAbstractContainerScreen}), so sorting does not depend on
     * another mod's internal mixin interface being present.
     */
    private static Slot getHoveredSlot(AbstractContainerScreen<?> screen)
    {
        try
        {
            return ((com.sortnstock.mixin.IMixinAbstractContainerScreen) screen).sortnstock_getHoveredSlot();
        }
        catch (Throwable t)
        {
            SortNStock.LOGGER.error("sort: could not read the hovered slot accessor", t);
            return null;
        }
    }
}
