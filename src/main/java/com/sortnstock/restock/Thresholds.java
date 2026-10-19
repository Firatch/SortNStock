package com.sortnstock.restock;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.sortnstock.config.Configs;

/**
 * Resolves the restock threshold and target amount for an item.
 *
 * <pre>
 *   threshold = restockMinCount
 *   target(item) = the item's own maximum stack size ("one full stack")
 * </pre>
 *
 * <p>A restock is considered once the amount in the target slots has fallen to or below the
 * threshold, and it then fills straight up to a full stack. There is deliberately no hysteresis
 * setting: the item itself defines how big a stack is — 64 for most things, 16 for eggs, snowballs
 * and ender pearls, and so on — so "top it back up to a full stack" needs no extra number.</p>
 *
 * <p>There are deliberately no per-item overrides either. With a single threshold and a target that
 * is always one full stack, an override had only two possible readings — "restock this item
 * earlier" or "keep more of it" — and could not express the second one anyway, so it caused more
 * confusion than it removed.</p>
 *
 * <p>With the minimum count at 0 the rule reduces to "only top up once the slot has actually run
 * dry", which is a useful setting in its own right.</p>
 */
public final class Thresholds
{
    private Thresholds()
    {
    }

    /** The amount at or below which a restock is considered. */
    public static int threshold()
    {
        return Configs.Restock.RESTOCK_MIN_COUNT.getIntegerValue();
    }

    /**
     * The amount to top up to: exactly one full stack of this item, so the slot ends up as full
     * as the item allows (64, 16, or 1 for unstackable items).
     */
    public static int targetFor(Item item)
    {
        return Math.max(1, item.getDefaultMaxStackSize());
    }

    /** Count-based restocking only makes sense for stackable items. */
    public static boolean supportsCountRestock(ItemStack stack)
    {
        return stack.isEmpty() == false && supportsCountRestock(stack.getItem());
    }

    /** Count-based restocking only makes sense for stackable items. */
    public static boolean supportsCountRestock(Item item)
    {
        return item != Items.AIR && item.getDefaultMaxStackSize() > 1;
    }

    public static boolean isAtOrBelowThreshold(ItemStack stack)
    {
        return supportsCountRestock(stack) && stack.getCount() <= threshold();
    }
}
