package com.sortnstock.sort;

import java.time.Duration;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Tests for the sort planner's arithmetic, running entirely on {@link SlotModel} — plain integers,
 * no Minecraft. These cover the two defects that reached users from this code:
 *
 * <ul>
 *   <li>the merge phase could <b>loop forever</b>, freezing the client before a single click was
 *       sent ("nothing sorted and the game hung"), and</li>
 *   <li>the merge stopped as soon as the number of stacks matched, leaving stacks <b>half full</b>
 *       (50 + 60 stayed 50 + 60 instead of becoming 64 + 46).</li>
 * </ul>
 */
class SortPlannerTest
{
    /** Item handle used for "the item under test"; handle 0 means empty. */
    private static final int ITEM_A = 1;
    private static final int ITEM_B = 2;

    private static SlotModel model(int[] items, int[] counts, int cap)
    {
        int[] caps = new int[items.length];
        Arrays.fill(caps, cap);

        return new SlotModel(items.clone(), counts.clone(), caps);
    }

    private static SlotModel modelWithCaps(int[] items, int[] counts, int[] caps)
    {
        return new SlotModel(items.clone(), counts.clone(), caps.clone());
    }

    private static int total(SlotModel model)
    {
        int total = 0;

        for (int i = 0; i < model.size(); i++)
        {
            total += model.count(i);
        }

        return total;
    }

    private static int[] counts(SlotModel model)
    {
        int[] result = new int[model.size()];

        for (int i = 0; i < model.size(); i++)
        {
            result[i] = model.count(i);
        }

        return result;
    }

    /**
     * The freeze regression. 30 + 40 + 10 = 80 does not fit in one stack, so merge has to fill one
     * stack and leave the remainder — the exact shape that used to spin forever because the refill
     * became a no-op once the target stack was already full.
     */
    @Test
    void mergeTerminatesWhenStacksOverflowAFullStack()
    {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
        {
            SlotModel live = model(
                    new int[] { ITEM_A, ITEM_A, ITEM_A },
                    new int[] { 30, 40, 10 },
                    64);

            SortPlanner.Result result =
                    SortPlanner.plan(live, model(new int[3], new int[3], 64), true, -1);

            // A merge can reduce the stack count at most once per merge, so a well-behaved plan
            // needs at most n-1 groups. Anything near the safety bound means the loop was spinning.
            assertTrue(result.groups().size() <= live.size(),
                    "merge emitted " + result.groups().size() + " groups for " + live.size()
                            + " slots — that is a runaway loop, not a merge");
            assertTrue(live.isFullyMerged());
        });
    }

    /** Same shape, but with the stacks ordered so the first one is already full. */
    @Test
    void mergeTerminatesWhenTheFirstStackIsAlreadyFull()
    {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
        {
            SlotModel live = model(
                    new int[] { ITEM_A, ITEM_A, ITEM_A },
                    new int[] { 64, 6, 10 },
                    64);

            SortPlanner.Result result =
                    SortPlanner.plan(live, model(new int[3], new int[3], 64), true, -1);

            assertTrue(result.groups().size() <= live.size(),
                    "merge emitted " + result.groups().size() + " groups for " + live.size()
                            + " slots — that is a runaway loop, not a merge");
            assertTrue(live.isFullyMerged());
        });
    }

    /**
     * The half-full regression: two stacks of 50 and 60 already have the same *stack count* as the
     * ideal 64 + 46, so the old count-based guard merged nothing.
     */
    @Test
    void mergeFillsStacksEvenWhenTheStackCountAlreadyMatches()
    {
        SlotModel live = model(
                new int[] { ITEM_A, ITEM_A },
                new int[] { 50, 60 },
                64);

        SortPlanner.plan(live, model(new int[2], new int[2], 64), true, -1);

        assertEquals(64, live.count(0), "the first stack should end up full");
        assertEquals(46, live.count(1), "the remainder should be left in the other stack");
    }

    /** 40 + 40 overflows exactly once: one full stack plus a 16 remainder. */
    @Test
    void mergeSplitsAnExactOverflowIntoFullStackPlusRemainder()
    {
        SlotModel live = model(
                new int[] { ITEM_A, ITEM_A },
                new int[] { 40, 40 },
                64);

        SortPlanner.plan(live, model(new int[2], new int[2], 64), true, -1);

        assertEquals(64, live.count(0));
        assertEquals(16, live.count(1));
    }

    /** 32 + 32 fits exactly: one full stack and an emptied slot. */
    @Test
    void mergeEmptiesTheSourceWhenEverythingFits()
    {
        SlotModel live = model(
                new int[] { ITEM_A, ITEM_A },
                new int[] { 32, 32 },
                64);

        SortPlanner.plan(live, model(new int[2], new int[2], 64), true, -1);

        assertEquals(64, live.count(0));
        assertTrue(live.isEmpty(1), "the source slot should end up empty");
        assertTrue(live.isFullyMerged());
    }

    /** Items that stack to 16 must fill to 16, not to 64. */
    @Test
    void mergeRespectsTheItemsOwnStackLimit()
    {
        SlotModel live = model(
                new int[] { ITEM_A, ITEM_A },
                new int[] { 10, 10 },
                16);

        SortPlanner.plan(live, model(new int[2], new int[2], 16), true, -1);

        assertEquals(16, live.count(0));
        assertEquals(4, live.count(1));
    }

    /** Merging is opt-in: with it off, same-item stacks must be left exactly where they are. */
    @Test
    void mergeDoesNothingWhenDisabled()
    {
        SlotModel live = model(
                new int[] { ITEM_A, ITEM_A },
                new int[] { 50, 60 },
                64);

        SlotModel target = model(
                new int[] { ITEM_A, ITEM_A },
                new int[] { 50, 60 },
                64);

        SortPlanner.Result result = SortPlanner.plan(live, target, false, -1);

        assertEquals(50, live.count(0));
        assertEquals(60, live.count(1));
        assertTrue(result.groups().isEmpty(), "nothing needed to move");
        assertEquals(SortPlanner.Reason.ALREADY_SORTED, result.reason());
    }

    /** A stack is pulled down into an empty slot nearer the front — the compacting case. */
    @Test
    void movesAStackDownIntoAnEmptySlot()
    {
        SlotModel live = model(
                new int[] { 0, ITEM_A },
                new int[] { 0, 5 },
                64);

        SlotModel target = model(
                new int[] { ITEM_A, 0 },
                new int[] { 5, 0 },
                64);

        SortPlanner.Result result = SortPlanner.plan(live, target, false, -1);

        assertEquals(SortPlanner.Reason.PLANNED, result.reason());
        assertEquals(1, result.groups().size());
        assertEquals(5, live.count(0));
        assertEquals(0, live.count(1));
    }

    /**
     * Stacks may also move towards <i>higher</i> indices. This used to be refused outright, because
     * the planner only ever pulled stacks towards index 0. Shulker boxes are now reserved into the
     * tail of the container, so they routinely have to travel the other way.
     */
    @Test
    void movesAStackUpIntoAnEmptySlot()
    {
        SlotModel live = model(
                new int[] { ITEM_A, 0 },
                new int[] { 5, 0 },
                64);

        SlotModel target = model(
                new int[] { 0, ITEM_A },
                new int[] { 0, 5 },
                64);

        SortPlanner.Result result = SortPlanner.plan(live, target, false, -1);

        assertEquals(SortPlanner.Reason.PLANNED, result.reason());
        assertEquals(1, result.groups().size());
        assertEquals(0, live.count(0));
        assertEquals(5, live.count(1), "the stack must end up in the later slot");
    }

    /**
     * The shape the shulker-box rule produces: the boxes belong at the very end of the container,
     * behind a gap, so they must be pushed backwards past the ordinary items.
     */
    @Test
    void pushesStacksPastAGapToTheEndOfTheRegion()
    {
        // 6 slots: A at 0, the "shulker" B at 1, the rest empty.
        SlotModel live = model(
                new int[] { ITEM_A, ITEM_B, 0, 0, 0, 0 },
                new int[] { 64, 1, 0, 0, 0, 0 },
                64);

        // Target: A at 0, a gap at 1-4, B alone in the last slot.
        SlotModel target = model(
                new int[] { ITEM_A, 0, 0, 0, 0, ITEM_B },
                new int[] { 64, 0, 0, 0, 0, 1 },
                64);

        SortPlanner.Result result = SortPlanner.plan(live, target, false, -1);

        assertEquals(SortPlanner.Reason.PLANNED, result.reason());
        assertEquals(0, result.skipped(), "every pair must be placeable");

        assertTrue(live.matchesExactly(0, target, 0), "the ordinary stack stays at the front");
        assertTrue(live.matchesExactly(5, target, 5), "the box is pushed to the last slot");

        for (int i = 1; i < 5; i++)
        {
            assertTrue(live.isEmpty(i), "slot " + i + " should be part of the gap");
        }
    }

    /** Two groups crossing each other must still resolve, given enough passes. */
    @Test
    void resolvesTwoGroupsThatSwapSides()
    {
        SlotModel live = model(
                new int[] { ITEM_B, ITEM_A },
                new int[] { 1, 64 },
                64);

        SlotModel target = model(
                new int[] { ITEM_A, ITEM_B },
                new int[] { 64, 1 },
                64);

        SortPlanner.Result result = SortPlanner.plan(live, target, false, -1);

        assertEquals(SortPlanner.Reason.PLANNED, result.reason());
        assertEquals(0, result.skipped());
        assertTrue(live.matchesExactly(0, target, 0));
        assertTrue(live.matchesExactly(1, target, 1));
    }

    /**
     * The post-condition that matters most: once a merge plan has run, no two slots may hold the
     * same item with a merge between them. Swept over many random shapes, because the freeze only
     * appeared for particular count splits.
     */
    @Test
    void mergeLeavesNoMergeablePairBehind()
    {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () ->
        {
            Random random = new Random(20261003L);

            for (int round = 0; round < 2000; round++)
            {
                final int size = 2 + random.nextInt(7);
                int[] items = new int[size];
                int[] counts = new int[size];

                for (int i = 0; i < size; i++)
                {
                    // Half the slots empty, the rest a random mix of two items at random counts.
                    if (random.nextInt(2) == 0)
                    {
                        continue;
                    }

                    items[i] = random.nextBoolean() ? ITEM_A : ITEM_B;
                    counts[i] = 1 + random.nextInt(64);
                }

                SlotModel live = model(items, counts, 64);
                final int before = total(live);

                SortPlanner.plan(live, model(new int[size], new int[size], 64), true, -1);

                assertEquals(before, total(live),
                        "merging must never change how many items exist (round " + round + ")");
                assertTrue(live.isFullyMerged(),
                        "no mergeable pair may remain (round " + round + ", "
                                + Arrays.toString(counts) + ")");
            }
        });
    }

    /** Different items are never merged together, however similar the counts look. */
    @Test
    void mergeNeverMixesDifferentItems()
    {
        SlotModel live = model(
                new int[] { ITEM_A, ITEM_B },
                new int[] { 10, 10 },
                64);

        SortPlanner.plan(live, model(new int[2], new int[2], 64), true, -1);

        assertEquals(10, live.count(0));
        assertEquals(10, live.count(1));
        assertFalse(live.sameItem(0, 1));
    }

    /**
     * The receiving slot is filled only up to <i>its own</i> limit. This is the shape of the old
     * bug where the stack limit was read from slot 0 for the whole region — here that would give
     * 20 in slot 0 instead of stopping at 16.
     */
    @Test
    void mergeFillsEachSlotOnlyToItsOwnLimit()
    {
        SlotModel live = modelWithCaps(
                new int[] { ITEM_A, ITEM_A },
                new int[] { 10, 10 },
                new int[] { 16, 64 });

        SortPlanner.plan(live, model(new int[2], new int[2], 64), true, -1);

        assertEquals(16, live.count(0), "slot 0 must stop at its own limit of 16");
        assertEquals(4, live.count(1), "the rest stays in slot 1");
    }
}
