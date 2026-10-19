package com.sortnstock.restock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression tests for the deposit-undo bug.
 *
 * <p>Reported as: after a shulker-box refill had happened once, putting a stack into a shulker box
 * made restock pull it straight back out. The trigger only compared the main-hand count, so
 * <i>storing</i> a stack looked exactly like <i>consuming</i> one.</p>
 *
 * <p>The fix relies on a fact about the game: a container screen being open rules out consumption.
 * While one is open the tick is fed through {@link RestockTrigger#update}, which follows the count
 * without reporting anything.</p>
 */
class RestockTriggerDepositTest
{
    private static final String LOG = "minecraft:stripped_pale_oak_log";

    @Test
    void depositingThroughAContainerScreenIsNotAConsumption()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        // Holding a full stack in slot 3.
        assertNull(trigger.observe(3, LOG, 64));

        // The player opens a shulker box and puts the whole stack in: the count drops to 0 while the
        // screen is open, so the tick is fed as an update rather than an observation.
        trigger.update(3, null, 0);

        // Closing the screen: the hand is still empty, and nothing may be reported.
        assertNull(trigger.observe(3, null, 0),
                "storing a stack must not be mistaken for consuming it");
    }

    @Test
    void depositingPartOfAStackIsNotAConsumption()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        assertNull(trigger.observe(2, LOG, 64));
        trigger.update(2, LOG, 16);

        assertNull(trigger.observe(2, LOG, 16),
                "the remaining stack must not be topped back up");
        assertEquals(2, trigger.originSlot());
    }

    /** The real consumption path must still work after an update. */
    @Test
    void consumptionAfterAContainerScreenStillTriggers()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        trigger.update(1, LOG, 32);
        assertNull(trigger.observe(1, LOG, 32));

        // One placed block, no screen open.
        assertEquals(LOG, trigger.observe(1, LOG, 31),
                "placing a block outside a GUI is still a consumption");
        assertEquals(1, trigger.originSlot());
    }

    /** A slot switch is still ignored, so switching stacks does not look like consumption. */
    @Test
    void slotSwitchIsStillNotAConsumption()
    {
        RestockTrigger<String> trigger = new RestockTrigger<>();

        assertNull(trigger.observe(0, LOG, 64));
        assertNull(trigger.observe(4, "minecraft:stone", 1));
    }
}
