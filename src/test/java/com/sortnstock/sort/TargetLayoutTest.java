package com.sortnstock.sort;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for where the sorted stacks are written.
 *
 * <p>The rule that matters: shulker boxes go into the <b>last slots of the container</b>, not
 * merely behind the other items. The first implementation reserved no tail at all and simply sorted
 * the boxes last within a front-packed run, which looked correct in the unit tests but did not do
 * what the user asked for — the boxes ended up directly behind the other items with empty slots
 * after them.</p>
 */
class TargetLayoutTest
{
    @Test
    void shulkerBoxesGetTheLastSlotsOfTheContainer()
    {
        // The reported case: 27 slots, 12 ordinary stacks, 9 shulker boxes.
        int tailStart = TargetLayout.shulkerTailStart(27, 12, 9);

        assertEquals(18, tailStart, "9 boxes in a 27-slot container start at slot 18");
        assertArrayEquals(new int[] { 18, 19, 20, 21, 22, 23, 24, 25, 26 },
                TargetLayout.shulkerDestinations(tailStart, 9));
        assertArrayEquals(new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 },
                TargetLayout.otherDestinations(12));
    }

    /** A full container has no room for a gap, so the boxes sit directly behind the other items. */
    @Test
    void aFullRegionPutsTheBoxesDirectlyAtTheEnd()
    {
        int tailStart = TargetLayout.shulkerTailStart(27, 18, 9);

        assertEquals(18, tailStart);
        assertArrayEquals(new int[] { 18, 19, 20, 21, 22, 23, 24, 25, 26 },
                TargetLayout.shulkerDestinations(tailStart, 9));
    }

    /** The tail never overflows, however full the region is. */
    @Test
    void theTailNeverLeavesTheRegionEvenWhenEverySlotIsUsed()
    {
        for (int shulkers = 0; shulkers <= 27; shulkers++)
        {
            for (int others = 0; others + shulkers <= 27; others++)
            {
                int tailStart = TargetLayout.shulkerTailStart(27, others, shulkers);
                int[] tail = TargetLayout.shulkerDestinations(tailStart, shulkers);

                if (shulkers == 0)
                {
                    assertEquals(0, tail.length);
                    continue;
                }

                assertEquals(27 - 1, tail[tail.length - 1],
                        "the last box must land in the last slot (" + others + " + " + shulkers + ")");
                assertEquals(shulkers, tail.length);

                // The tail must start at or after the end of the other group, and never overlap it.
                assertEquals(true, tailStart >= others,
                        "the tail must not overlap the ordinary stacks (" + others + " + " + shulkers + ")");
            }
        }
    }

    /** No shulker boxes means no reservation at all, so compaction still fills the whole region. */
    @Test
    void nothingIsReservedWithoutShulkerBoxes()
    {
        assertEquals(27, TargetLayout.shulkerTailStart(27, 21, 0));
        assertEquals(0, TargetLayout.shulkerDestinations(27, 0).length);
    }

    /** One box alone still goes to the very last slot. */
    @Test
    void aSingleBoxGoesToTheLastSlot()
    {
        int tailStart = TargetLayout.shulkerTailStart(27, 3, 1);

        assertEquals(26, tailStart);
        assertArrayEquals(new int[] { 26 }, TargetLayout.shulkerDestinations(tailStart, 1));
    }
}
