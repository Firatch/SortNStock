package com.sortnstock.sort;

/**
 * The ordering <i>within</i> the shulker-box group.
 *
 * <p>Requested order, with the stated priority {@code 1 > 2 > 5 > 3 = 4}:</p>
 *
 * <ol>
 *   <li><b>Fill state</b> — empty boxes, then partly filled, then full.</li>
 *   <li><b>Colour</b> — creative-inventory colour order (white, orange, …, black); the undyed box
 *       sorts before every colour.</li>
 *   <li><b>Mixed before pure</b> — a box holding several different items comes before a box holding
 *       a single kind of item.</li>
 *   <li><b>Contents</b> — for boxes that are not full, the one holding least comes first; for full
 *       boxes, the box whose first contents come first in the ordinary item order. These two never
 *       compete, since a box is either full or it is not.</li>
 * </ol>
 *
 * <p>This class holds the pure part — levels 1, 2, 5 and the "least contents first" half of level 4.
 * Level 4 for full boxes compares the boxes' first contents <i>as ordinary items</i>, which needs
 * Minecraft's item comparisons, so that half lives in {@code InventorySorter} and reuses the very
 * same comparator the ordinary items are sorted with ("顺序与普通物品一致").</p>
 *
 * <p>Everything is ascending: the sort-direction option was removed, so there is no descending mode
 * to mirror any of this for.</p>
 */
final class ShulkerOrder
{
    /** How full a shulker box is. Declaration order is the required sort order. */
    enum Fill
    {
        EMPTY,
        PARTIAL,
        FULL
    }

    /** Colour rank of the undyed box, which sorts before every dyed colour. */
    static final int UNDYED_COLOR_RANK = -1;

    /**
     * The comparison inputs derived from one box.
     *
     * @param contentAmount total number of items inside, used by level 3
     * @param mixed         true when the box holds more than one kind of item
     */
    record Key(Fill fill, int colorRank, boolean mixed, int contentAmount)
    {
    }

    private ShulkerOrder()
    {
    }

    /** Levels 1, 2 and 5: fill state, then colour, then mixed before pure. */
    static int comparePriority(Key a, Key b)
    {
        int byFill = Integer.compare(a.fill().ordinal(), b.fill().ordinal());

        if (byFill != 0)
        {
            return byFill;
        }

        int byColor = Integer.compare(a.colorRank(), b.colorRank());

        if (byColor != 0)
        {
            return byColor;
        }

        // true ("mixed") sorts before false ("pure").
        return Boolean.compare(b.mixed(), a.mixed());
    }

    /** Level 3: the box holding least comes first. */
    static int compareContent(Key a, Key b)
    {
        return Integer.compare(a.contentAmount(), b.contentAmount());
    }
}
