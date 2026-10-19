package com.sortnstock.sort;

/**
 * Which slots make up a region, as a pure rule with no Minecraft types.
 *
 * <p>Split out of {@link InventoryRegion} so the decision can be tested directly. It is worth
 * isolating because it has already been wrong once: the player's armour slots (container slots
 * 36-39) and the offhand slot (40) are backed by the <i>same</i> {@code Inventory} instance as the
 * main inventory, so grouping by container identity alone swept them into the region. That is wrong
 * twice over — armour slots accept a single item, which drags the whole region's stack limit down
 * to 1, and a sort could try to move a helmet into the backpack.</p>
 */
final class RegionRules
{
    private RegionRules()
    {
    }

    /**
     * Whether a slot belonging to the player's own {@code Inventory} takes part in a region.
     *
     * <p>Only the main inventory qualifies. The hotbar is excluded because it is a hand-arranged
     * quick-access bar that a sort should never rearrange, and the armour and offhand slots are
     * excluded because they are restricted slots rather than ordinary storage. This is fixed
     * behaviour, not a preference, so there is no configuration behind it.</p>
     *
     * @param containerSlot the slot's index within the player's inventory
     * @param hotbarSize    number of leading slots that are the hotbar (9)
     * @param inventorySize one past the last main inventory slot (36); slots at or beyond this are
     *                      armour and offhand
     */
    static boolean belongsToPlayerRegion(int containerSlot, int hotbarSize, int inventorySize)
    {
        return containerSlot >= hotbarSize && containerSlot < inventorySize;
    }
}
