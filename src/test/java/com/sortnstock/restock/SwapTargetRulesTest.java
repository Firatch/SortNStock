package com.sortnstock.restock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the rule that chooses which slot is emptied into a shulker box when the inventory is full.
 *
 * <p>The important property is that the rule never returns a slot it should not use: the box slot it
 * is taking the stack from, an empty slot (that is a destination, not a displacement), or a shulker
 * box.</p>
 */
class SwapTargetRulesTest
{
    @Test
    void picksTheSmallestStack()
    {
        int[] slots = { 54, 55, 56, 57 };
        int[] counts = { 64, 7, 32, 12 };

        assertEquals(55, SwapTargetRules.choose(slots, counts, none(4), none(4), 0, -1));
    }

    @Test
    void tieGoesToTheHeldSlot()
    {
        int[] slots = { 54, 55, 56 };
        int[] counts = { 32, 5, 5 };

        assertEquals(55, SwapTargetRules.choose(slots, counts, none(3), none(3), 0, 55));
    }

    @Test
    void tieWithoutTheHeldSlotUsesListOrder()
    {
        int[] slots = { 54, 55, 56 };
        int[] counts = { 9, 5, 5 };

        // The held slot (30) is not a candidate, so the first smallest stack in list order wins.
        assertEquals(55, SwapTargetRules.choose(slots, counts, none(3), none(3), 0, 30));
    }

    @Test
    void tiePrefersTheHeldSlotEvenWhenItComesLater()
    {
        int[] slots = { 54, 55, 56 };
        int[] counts = { 9, 5, 5 };

        assertEquals(56, SwapTargetRules.choose(slots, counts, none(3), none(3), 0, 56));
    }

    @Test
    void neverChoosesTheSourceSlot()
    {
        int[] slots = { 0, 54 };
        int[] counts = { 1, 20 };

        // Slot 0 holds the smallest stack but is where the stack is coming from.
        assertEquals(54, SwapTargetRules.choose(slots, counts, none(2), none(2), 0, -1));
    }

    @Test
    void skipsEmptySlots()
    {
        int[] slots = { 54, 55 };
        int[] counts = { 0, 40 };
        boolean[] empty = { true, false };

        assertEquals(55, SwapTargetRules.choose(slots, counts, empty, none(2), 0, 54));
    }

    @Test
    void skipsShulkerBoxes()
    {
        int[] slots = { 54, 55 };
        int[] counts = { 1, 30 };
        boolean[] isBox = { true, false };

        // Slot 54 has the smallest stack but holds a box, which must not be nested into another box.
        assertEquals(55, SwapTargetRules.choose(slots, counts, none(2), isBox, 0, -1));
    }

    @Test
    void returnsMinusOneWhenNothingQualifies()
    {
        int[] slots = { 0, 54 };
        int[] counts = { 5, 0 };
        boolean[] empty = { false, true };

        assertEquals(-1, SwapTargetRules.choose(slots, counts, empty, none(2), 0, -1));
    }

    private static boolean[] none(int length)
    {
        return new boolean[length];
    }
}
