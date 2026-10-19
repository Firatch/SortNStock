package com.sortnstock.restock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the restock trigger.
 *
 * <p>Guards the defect where, with the minimum count set to 0, using a stack up completely never
 * refilled it: the slot was empty by the time the check ran, so the item that had been consumed
 * could no longer be identified and nothing was restocked. It also covers the second half of that
 * fix — switching hotbar slots must not look like consuming items.</p>
 *
 * <p>Instantiated with {@code String} as the item type, which is the whole point of making the
 * trigger generic.</p>
 */
class RestockTriggerTest
{
    private static final String STONE = "minecraft:stone";
    private static final String DIRT = "minecraft:dirt";

    @Test
    void doesNotFireOnTheFirstObservation()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        assertNull(trigger.observe(0, STONE, 64),
                "there is no previous tick to compare against, so nothing can have been consumed");
    }

    @Test
    void firesWhenTheHeldStackShrinks()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        trigger.observe(3, STONE, 64);

        assertEquals(STONE, trigger.observe(3, STONE, 63));
    }

    /** The zero-threshold regression: the last item is used and the hand is now empty. */
    @Test
    void stillIdentifiesTheItemWhenTheStackIsUsedUpCompletely()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        trigger.observe(3, STONE, 1);

        assertEquals(STONE, trigger.observe(3, null, 0),
                "the emptied slot must still report what used to be in it");
    }

    @Test
    void doesNotFireAgainOnceTheHandIsAlreadyEmpty()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        trigger.observe(3, STONE, 1);
        trigger.observe(3, null, 0);

        assertNull(trigger.observe(3, null, 0), "nothing more was consumed");
    }

    /**
     * Switching from a stack of 64 to a stack of 1 in another slot drops the count by 63 without
     * anything being consumed. Treating that as a trigger would refill at random.
     */
    @Test
    void doesNotFireWhenTheSelectedSlotChanges()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        trigger.observe(0, STONE, 64);

        assertNull(trigger.observe(1, DIRT, 1), "a slot change is not a consumption");
    }

    /** ...but a real consumption in the new slot still fires. */
    @Test
    void firesAfterASlotChangeOnceTheNewStackIsActuallyUsed()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        trigger.observe(0, STONE, 64);
        assertNull(trigger.observe(1, DIRT, 1));

        assertEquals(DIRT, trigger.observe(1, DIRT, 0));
    }

    @Test
    void doesNotFireWhenTheStackGrows()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        trigger.observe(3, STONE, 10);

        assertNull(trigger.observe(3, STONE, 42), "picking items up is not a consumption");
    }

    @Test
    void doesNotFireWhenTheCountIsUnchanged()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        trigger.observe(3, STONE, 10);

        assertNull(trigger.observe(3, STONE, 10));
    }

    @Test
    void reportsTheSlotTheItemWasConsumedFrom()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        trigger.observe(7, STONE, 5);
        trigger.observe(7, STONE, 4);

        assertEquals(7, trigger.originSlot(),
                "the refill must go back to the slot the player was placing from");
    }

    /**
     * Picking a different item into the hand is not a consumption either, even though the previous
     * stack effectively disappears from the main hand.
     */
    @Test
    void doesNotFireWhenTheHeldItemTypeChanges()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        trigger.observe(3, STONE, 64);

        assertNull(trigger.observe(3, DIRT, 64),
                "the count did not shrink, so nothing was consumed");
    }
}
