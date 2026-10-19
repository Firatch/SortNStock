package com.sortnstock.restock;

import java.util.function.IntPredicate;

/**
 * Where a restocked stack should be put, as a pure rule with no Minecraft types.
 *
 * <p>Split out of {@link RestockManager} so it can be tested directly, because it has already been
 * wrong once: the refill used to land in whichever empty hotbar slot came first, rather than in the
 * slot the player had just been placing from — rearranging the hotbar behind their back.</p>
 */
final class DestinationRules
{
    private DestinationRules()
    {
    }

    /**
     * Chooses the destination slot.
     *
     * @param targetSlots candidate slots as menu slot ids, in the order they should be considered
     * @param isEmpty     whether a given slot is empty
     * @param holdsWanted whether a given slot holds the item being restocked
     * @param preferred   the slot the player was working from, or -1
     * @return the chosen menu slot id, or -1 when nothing can take the stack
     */
    static int choose(int[] targetSlots, IntPredicate isEmpty, IntPredicate holdsWanted, int preferred)
    {
        // The slot the item was consumed from wins whenever it can take the stack — whether it is
        // now empty, or still holds the item being topped up. Preferring it also avoids selecting a
        // different slot that already holds a full stack of the same item: the free space there
        // would be zero, the move would be impossible, and the source stack would be skipped
        // silently.
        if (preferred >= 0 && contains(targetSlots, preferred) &&
            (isEmpty.test(preferred) || holdsWanted.test(preferred)))
        {
            return preferred;
        }

        // Otherwise top up an existing partial stack of the same item...
        for (int slot : targetSlots)
        {
            if (holdsWanted.test(slot))
            {
                return slot;
            }
        }

        // ...or fall back to the first slot that is simply empty.
        for (int slot : targetSlots)
        {
            if (isEmpty.test(slot))
            {
                return slot;
            }
        }

        return -1;
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
}
