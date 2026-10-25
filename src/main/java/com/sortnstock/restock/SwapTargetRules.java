package com.sortnstock.restock;

/**
 * Chooses which slot to empty into a shulker box when the inventory is full.
 *
 * <p>Split out of {@link RestockManager} so the rule can be tested without a container menu, the same
 * way {@link DestinationRules} was. It has to be right in an awkward situation — nothing can take the
 * stack, so something must be displaced — and getting it wrong costs the player items for as long as
 * the box stays shut.</p>
 */
final class SwapTargetRules
{
    private SwapTargetRules()
    {
    }

    /**
     * Picks the slot whose contents are displaced into the box.
     *
     * <p>The slot holding the <b>smallest stack</b> is chosen: the displaced stack waits in the box
     * until the player takes it back, so the smallest one is the one they miss least. A tie is broken
     * in favour of the slot the player is holding, then by list order.</p>
     *
     * <p>Empty slots are skipped — an empty slot is a destination, not something to displace — and so
     * are shulker boxes, because a box put inside another box needs two boxes opened to reach again.
     * Those two exclusions mean the result, when not -1, always refers to a slot that actually holds
     * something.</p>
     *
     * @param slots       candidate menu slot ids, in the order they should be considered
     * @param counts      the item count in each candidate slot
     * @param empty       whether each candidate slot is empty
     * @param shulkerBox  whether each candidate slot holds a shulker box
     * @param sourceSlot  the box slot the wanted stack is taken from; never a candidate
     * @param selectedSlot the slot the player is holding, or -1
     * @return the chosen menu slot id, or -1 when there is nothing suitable
     */
    static int choose(int[] slots, int[] counts, boolean[] empty, boolean[] shulkerBox,
                      int sourceSlot, int selectedSlot)
    {
        int bestSlot = -1;
        int bestCount = Integer.MAX_VALUE;

        for (int i = 0; i < slots.length; i++)
        {
            if (slots[i] == sourceSlot || empty[i] || shulkerBox[i])
            {
                continue;
            }

            if (counts[i] < bestCount)
            {
                bestCount = counts[i];
                bestSlot = slots[i];
            }
        }

        if (bestSlot < 0 || selectedSlot == bestSlot)
        {
            return bestSlot;
        }

        // Prefer the held slot when it ties for the smallest stack.
        for (int i = 0; i < slots.length; i++)
        {
            if (slots[i] == selectedSlot && empty[i] == false && shulkerBox[i] == false &&
                counts[i] == bestCount)
            {
                return selectedSlot;
            }
        }

        return bestSlot;
    }
}
