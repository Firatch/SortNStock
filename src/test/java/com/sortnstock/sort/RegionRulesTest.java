package com.sortnstock.sort;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the region membership rule.
 *
 * <p>Guards the defect where sorting the player's backpack also swept in the armour and offhand
 * slots — they share the player's {@code Inventory} instance, so grouping by container identity
 * alone caught them — and the later request that the hotbar be left alone entirely.</p>
 */
class RegionRulesTest
{
    /** Mirrors Inventory.getSelectionSize(). */
    private static final int HOTBAR_SIZE = 9;
    /** Mirrors Inventory.INVENTORY_SIZE. */
    private static final int INVENTORY_SIZE = 36;

    private static boolean included(int containerSlot)
    {
        return RegionRules.belongsToPlayerRegion(containerSlot, HOTBAR_SIZE, INVENTORY_SIZE);
    }

    @Test
    void hotbarSlotsAreExcluded()
    {
        for (int slot = 0; slot < HOTBAR_SIZE; slot++)
        {
            assertFalse(included(slot),
                    "hotbar slot " + slot + " must never be part of a sort region");
        }
    }

    @Test
    void armourAndOffhandSlotsAreExcluded()
    {
        // 36-39 are the armour slots and 40 is the offhand; both share the player's Inventory.
        for (int slot = INVENTORY_SIZE; slot <= 40; slot++)
        {
            assertFalse(included(slot),
                    "restricted slot " + slot + " must never be part of a sort region");
        }
    }

    @Test
    void exactlyTheMainInventoryIsIncluded()
    {
        int count = 0;

        for (int slot = 0; slot <= 45; slot++)
        {
            if (included(slot))
            {
                count++;
            }
        }

        // The backpack has 27 slots: 9..35.
        org.junit.jupiter.api.Assertions.assertEquals(27, count);
    }

    @Test
    void boundarySlotsAreDecidedCorrectly()
    {
        assertFalse(included(HOTBAR_SIZE - 1), "slot 8 is the last hotbar slot");
        assertTrue(included(HOTBAR_SIZE), "slot 9 is the first backpack slot");
        assertTrue(included(INVENTORY_SIZE - 1), "slot 35 is the last backpack slot");
        assertFalse(included(INVENTORY_SIZE), "slot 36 is the first armour slot");
    }
}
