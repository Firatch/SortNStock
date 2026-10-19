package com.sortnstock.sort;

/**
 * Decides which slots the sorted stacks are written to.
 *
 * <p>Two groups are laid out separately:</p>
 *
 * <ul>
 *   <li>the <b>non-shulker</b> stacks, packed towards the front of the region;</li>
 *   <li>the <b>shulker boxes</b>, reserved into the <i>last slots of the container</i> — not merely
 *       "after the other items". That distinction is what the user asked for: with 27 slots, 12
 *       ordinary stacks and 9 shulker boxes, the boxes belong in slots 18-26, leaving slots 12-17
 *       empty as a gap, rather than sitting at 12-20 directly behind the other items.</li>
 * </ul>
 *
 * <p>When the region is too full for a gap the two groups simply become contiguous and the boxes
 * still end up at the end, because the tail start is clamped to the end of the other group
 * ({@code max(otherCount, regionSize - shulkerCount)}). The total never overflows: the two counts
 * together are the number of non-empty stacks, which fits in the region by construction.</p>
 *
 * <p>Pure integers, so the layout rule can be unit tested without Minecraft.</p>
 */
final class TargetLayout
{
    private TargetLayout()
    {
    }

    /**
     * The first slot of the reserved block that holds the shulker boxes.
     *
     * @return the first slot of the tail block, or {@code regionSize} when nothing is reserved
     */
    static int shulkerTailStart(int regionSize, int otherCount, int shulkerCount)
    {
        if (shulkerCount <= 0)
        {
            return regionSize;
        }

        return Math.max(otherCount, regionSize - shulkerCount);
    }

    /** Destinations for the non-shulker stacks: the front of the region, in order. */
    static int[] otherDestinations(int otherCount)
    {
        int[] destinations = new int[otherCount];

        for (int i = 0; i < otherCount; i++)
        {
            destinations[i] = i;
        }

        return destinations;
    }

    /** Destinations for the shulker boxes: the reserved tail block, in order. */
    static int[] shulkerDestinations(int tailStart, int shulkerCount)
    {
        int[] destinations = new int[shulkerCount];

        for (int i = 0; i < shulkerCount; i++)
        {
            destinations[i] = tailStart + i;
        }

        return destinations;
    }
}
