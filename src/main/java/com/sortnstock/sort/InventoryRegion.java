package com.sortnstock.sort;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * A contiguous group of menu slots that belong to the same backing {@link Container}.
 *
 * <p>Grouping by {@link Slot#container} identity is how SortNStock decides what "the
 * container under the cursor" means: hovering any slot of the open chest yields the chest's
 * slots, hovering the player's own inventory yields the player's inventory slots.</p>
 *
 * <h2>The player inventory is the backpack only</h2>
 *
 * <p>Sorting the player's own inventory deliberately covers <b>only the 27 main inventory
 * slots</b>. The hotbar is never sorted, and neither are the armor or offhand slots. This is
 * fixed behaviour rather than a configuration option: the hotbar is a hand-arranged quick-access
 * bar, and having a sort silently rearrange it (or shuffle a held tool out of reach) is not
 * something the player can be expected to want. It is also the reason the region needs no
 * "which part did I hover" logic — hovering anywhere in the player's inventory targets the same
 * 27 slots.</p>
 *
 * <p>Three groups of slots share the player's {@link Inventory} instance and have to be excluded
 * explicitly:</p>
 *
 * <ul>
 *   <li>the hotbar (container slots 0-8),</li>
 *   <li>the armor slots (36-39),</li>
 *   <li>the offhand slot (40).</li>
 * </ul>
 *
 * <p>Armor matters twice over: those slots accept a single item, so leaving them in would also
 * drag the region's stack limit down to 1 and make every item look unstackable.</p>
 */
public final class InventoryRegion
{
    /** First container slot of the main inventory; 0-8 are the hotbar. */
    private static final int MAIN_INVENTORY_START = Inventory.getSelectionSize();
    /** One past the last main inventory slot; 36-39 are armor and 40 is the offhand. */
    private static final int MAIN_INVENTORY_END = Inventory.INVENTORY_SIZE;

    private final AbstractContainerMenu menu;
    /** Menu slot ids, in menu order. */
    private final List<Integer> slotIds;

    private InventoryRegion(AbstractContainerMenu menu, List<Integer> slotIds)
    {
        this.menu = menu;
        this.slotIds = slotIds;
    }

    /**
     * Builds the region for the given hovered slot.
     *
     * @return the region, or null when fewer than two slots qualify
     */
    public static InventoryRegion of(AbstractContainerMenu menu, Slot hoveredSlot)
    {
        if (menu == null || hoveredSlot == null)
        {
            return null;
        }

        final Container target = hoveredSlot.container;
        final boolean isPlayerInv = target instanceof Inventory;

        List<Integer> ids = new ArrayList<>();

        for (int menuSlotId = 0; menuSlotId < menu.slots.size(); menuSlotId++)
        {
            Slot slot = menu.slots.get(menuSlotId);

            if (slot.container != target)
            {
                continue;
            }

            // For the player's own inventory, keep only the main 27 slots. Everything else that
            // shares this Container instance — hotbar, armor, offhand — stays out of the region.
            // The decision itself lives in RegionRules so it can be unit tested.
            if (isPlayerInv &&
                RegionRules.belongsToPlayerRegion(slot.getContainerSlot(),
                        MAIN_INVENTORY_START, MAIN_INVENTORY_END) == false)
            {
                continue;
            }

            ids.add(menuSlotId);
        }

        if (ids.size() < 2)
        {
            return null;
        }

        return new InventoryRegion(menu, ids);
    }

    public AbstractContainerMenu getMenu()
    {
        return this.menu;
    }

    public int size()
    {
        return this.slotIds.size();
    }

    /** Menu slot id for a region-relative index. */
    public int menuSlotId(int regionIndex)
    {
        return this.slotIds.get(regionIndex);
    }

    public Slot slot(int regionIndex)
    {
        return this.menu.slots.get(this.menuSlotId(regionIndex));
    }
}
