package com.sortnstock.restock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the restock destination rule.
 *
 * <p>Guards the defect where a refill landed in the first empty hotbar slot instead of the slot the
 * player had been placing from — which rearranged the hotbar and, in the case where the first
 * matching slot already held a full stack, silently skipped the move altogether.</p>
 */
class DestinationRulesTest
{
    /** Slots 10..14 act as the configured hotbar targets. */
    private static final int[] TARGETS = { 10, 11, 12, 13, 14 };

    /** Convenience: build the two predicates from a small readable description of each slot. */
    private static int choose(int preferred, int[] emptySlots, int[] wantedSlots)
    {
        return DestinationRules.choose(
                TARGETS,
                slot -> contains(emptySlots, slot),
                slot -> contains(wantedSlots, slot),
                preferred);
    }

    private static boolean contains(int[] slots, int wanted)
    {
        for (int slot : slots)
        {
            if (slot == wanted)
            {
                return true;
            }
        }

        return false;
    }

    @Test
    void refillsTheSlotTheItemWasUsedFrom()
    {
        // Slot 13 was used up and is now empty; several other hotbar slots are empty too.
        int chosen = choose(13, new int[] { 10, 11, 13, 14 }, new int[] {});

        assertEquals(13, chosen,
                "the refill must return to the slot the player was placing from, not the first gap");
    }

    @Test
    void prefersTheOriginSlotEvenWhenAnotherSlotIsEmptyAndEarlier()
    {
        int chosen = choose(14, new int[] { 10, 14 }, new int[] {});

        assertEquals(14, chosen);
    }

    @Test
    void topsUpTheOriginSlotWhenItStillHoldsTheItem()
    {
        int chosen = choose(12, new int[] { 10 }, new int[] { 12 });

        assertEquals(12, chosen);
    }

    @Test
    void fallsBackToAnExistingPartialStackWhenTheOriginIsUsableByAnotherItem()
    {
        // The origin slot is neither empty nor holding the wanted item, so it cannot take it.
        int chosen = choose(13, new int[] { 11 }, new int[] { 11 });

        assertEquals(11, chosen);
    }

    @Test
    void fallsBackToTheFirstEmptySlotWhenThereIsNoOrigin()
    {
        int chosen = choose(-1, new int[] { 11, 12 }, new int[] {});

        assertEquals(11, chosen);
    }

    @Test
    void honoursTheTargetSlotOrdering()
    {
        int[] reversed = { 14, 13, 12, 11, 10 };

        int chosen = DestinationRules.choose(
                reversed,
                slot -> contains(new int[] { 10, 14 }, slot),
                slot -> false,
                -1);

        assertEquals(14, chosen, "the fallback must respect the given slot order");
    }

    @Test
    void ignoresAnOriginSlotThatIsNotATarget()
    {
        // Slot 3 is a hotbar slot the player used, but restocking targets the backpack here.
        int chosen = DestinationRules.choose(
                TARGETS,
                slot -> contains(new int[] { 10 }, slot),
                slot -> false,
                3);

        assertEquals(10, chosen, "an out-of-target origin must not be used");
    }

    @Test
    void reportsNoDestinationWhenNothingCanTakeTheStack()
    {
        int chosen = choose(-1, new int[] {}, new int[] {});

        assertEquals(-1, chosen);
    }

    /**
     * A full stack of the same item elsewhere must not win over the empty origin slot. Picking it
     * would leave zero free space, making the move impossible and skipping the source stack.
     */
    @Test
    void doesNotPickAFullStackOfTheSameItemOverTheEmptyOrigin()
    {
        // Slot 10 holds a full stack of the item; slot 13 is the empty origin.
        int chosen = choose(13, new int[] { 13 }, new int[] { 10 });

        assertEquals(13, chosen);
    }
}
