package com.sortnstock.sort;

/**
 * Mutable, Minecraft-free working copy of a region's contents.
 *
 * <p>For every slot it keeps three integers: an opaque <b>item handle</b> (0 means the slot is
 * empty), the stack <b>count</b>, and the slot's stack <b>limit</b>. All of the sort planner's
 * arithmetic runs on this, which is the whole point: the interesting logic — merging until stacks
 * are full, deciding which slot a stack belongs in, guaranteeing the merge terminates — can then be
 * unit tested with plain arrays instead of the game.</p>
 *
 * <p>This is not merely a convenience. Minecraft 26.1 binds an item's data components during the
 * datapack load phase, so an {@code ItemStack} cannot be constructed in a plain unit test at all
 * (see {@code MinecraftBootstrapSpikeTest}). Reducing the arithmetic to integers is what makes it
 * reachable from a test, and the production adapter in {@link SlotModels} is the only part that
 * still needs the real game types.</p>
 *
 * <p>Handles are opaque: the planner never asks what an item <i>is</i>, only whether two slots hold
 * the same thing. Productions maps "same thing" to {@code ItemStack.isSameItemSameComponents},
 * while a test can simply write down the handle it wants.</p>
 */
final class SlotModel
{
    private final int[] items;
    private final int[] counts;
    private final int[] caps;

    /**
     * @param items  one item handle per slot, 0 meaning empty
     * @param counts one stack count per slot, 0 for empty slots
     * @param caps   one stack limit per slot
     */
    SlotModel(int[] items, int[] counts, int[] caps)
    {
        if (items.length != counts.length || items.length != caps.length)
        {
            throw new IllegalArgumentException("SlotModel arrays must all have the same length");
        }

        this.items = items;
        this.counts = counts;
        this.caps = caps;
    }

    int size()
    {
        return this.items.length;
    }

    /** Opaque item handle, or 0 when the slot is empty. */
    int item(int index)
    {
        return this.items[index];
    }

    boolean isEmpty(int index)
    {
        return this.items[index] == 0;
    }

    int count(int index)
    {
        return this.counts[index];
    }

    int cap(int index)
    {
        return this.caps[index];
    }

    /** Sets a count, emptying the slot outright if the result is not positive. */
    void setCount(int index, int count)
    {
        if (count <= 0)
        {
            clear(index);
            return;
        }

        this.counts[index] = count;
    }

    void clear(int index)
    {
        this.items[index] = 0;
        this.counts[index] = 0;
    }

    /** True when both slots hold the same item; false if either is empty. */
    boolean sameItem(int a, int b)
    {
        return this.items[a] != 0 && this.items[a] == this.items[b];
    }

    /** True when both slots are empty, or hold the same item in the same amount. */
    boolean sameStack(int a, int b)
    {
        if (this.items[a] == 0 || this.items[b] == 0)
        {
            return this.items[a] == this.items[b];
        }

        return this.items[a] == this.items[b] && this.counts[a] == this.counts[b];
    }

    /** Same as {@link #sameStack(int, int)} but comparing a slot of this model to one of another. */
    boolean matchesExactly(int index, SlotModel other, int otherIndex)
    {
        if (this.items[index] == 0 || other.items[otherIndex] == 0)
        {
            return this.items[index] == other.items[otherIndex];
        }

        return this.items[index] == other.items[otherIndex] &&
               this.counts[index] == other.counts[otherIndex];
    }

    /** True when this slot is non-empty and holds the same item as the other model's slot. */
    boolean matchesItem(int index, SlotModel other, int otherIndex)
    {
        return this.items[index] != 0 && this.items[index] == other.items[otherIndex];
    }

    /** Moves the whole stack from {@code from} to {@code to}, leaving {@code from} empty. */
    void move(int from, int to)
    {
        this.items[to] = this.items[from];
        this.counts[to] = this.counts[from];
        this.caps[to] = this.caps[from];
        clear(from);
    }

    /** Exchanges the contents of two slots. */
    void swap(int a, int b)
    {
        int item = this.items[a];
        int count = this.counts[a];
        int cap = this.caps[a];

        this.items[a] = this.items[b];
        this.counts[a] = this.counts[b];
        this.caps[a] = this.caps[b];

        this.items[b] = item;
        this.counts[b] = count;
        this.caps[b] = cap;
    }

    /**
     * Total amount of the item in {@code handle} held across the given slots.
     */
    int totalOf(int handle, int[] slots)
    {
        int total = 0;

        for (int slot : slots)
        {
            if (this.items[slot] == handle)
            {
                total += this.counts[slot];
            }
        }

        return total;
    }

    /**
     * True when no two slots hold the same item with room to merge between them. This is the
     * post-condition the merge phase has to establish, and the property the tests assert.
     */
    boolean isFullyMerged()
    {
        for (int i = 0; i < size(); i++)
        {
            if (isEmpty(i) || count(i) >= cap(i))
            {
                continue;
            }

            for (int j = i + 1; j < size(); j++)
            {
                if (sameItem(i, j))
                {
                    return false;
                }
            }
        }

        return true;
    }
}
