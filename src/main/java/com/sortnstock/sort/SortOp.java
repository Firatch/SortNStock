package com.sortnstock.sort;

/**
 * A single container click in a sort program.
 *
 * @param kind      which {@code ContainerInput} to send
 * @param regionIdx region-relative slot index the click targets
 * @param hotbarIdx for {@link Kind#SWAP}, the hotbar slot (0-8) to swap against; ignored for
 *                  {@link Kind#PICKUP}
 */
public record SortOp(Kind kind, int regionIdx, int hotbarIdx)
{
    public enum Kind
    {
        /** Left-click: pick up / place / merge with the cursor stack. */
        PICKUP,
        /** Swap the target slot with a hotbar slot. */
        SWAP
    }

    public static SortOp pickup(int regionIdx)
    {
        return new SortOp(Kind.PICKUP, regionIdx, -1);
    }

    public static SortOp swap(int regionIdx, int hotbarIdx)
    {
        return new SortOp(Kind.SWAP, regionIdx, hotbarIdx);
    }
}
