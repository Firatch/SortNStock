package com.sortnstock.sort;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.sortnstock.SortNStock;

/**
 * Generates a click program that transforms the region's live contents into the target
 * arrangement.
 *
 * <p>Everything is expressed with ordinary container clicks, so the server stays authoritative
 * and no item can be created or destroyed. Two primitives are used:</p>
 *
 * <ul>
 *   <li><b>Cursor swap</b> — {@code PICKUP(i), PICKUP(j), PICKUP(i)}. With an empty cursor this
 *       rotates the two stacks through the cursor and leaves the cursor empty again. It is only
 *       valid when the two stacks are <i>different</i> items, because vanilla merges identical
 *       stacks instead of swapping them.</li>
 *   <li><b>Hotbar swap</b> — {@code SWAP(i,s), SWAP(j,s), SWAP(i,s)} using an empty hotbar slot
 *       {@code s} as a scratch. This works for any pair, including identical items, and always
 *       restores the scratch slot to empty.</li>
 * </ul>
 *
 * <p>Each returned group of operations is atomic: it must be executed entirely within a single
 * tick so that the cursor is empty again at every group boundary.</p>
 *
 * <p><b>Partial progress over all-or-nothing.</b> An earlier version aborted the entire program
 * when a single pair could not be swapped, which made sorting appear to do nothing at all on a
 * nearly-full inventory. Now an unsolvable pair is simply skipped and counted; every other pair
 * is still sorted. Skipping never modifies the inventory, so it carries no item-loss risk.</p>
 *
 * <p><b>Testability.</b> The planning itself runs on {@link SlotModel} — plain integers — so the
 * merge and placement logic can be unit tested without the game. Only the public entry point below
 * and {@link #findEmptyHotbarScratch} touch Minecraft types.</p>
 */
public final class SortPlanner
{
    /** Why a program ended up empty or short. */
    public enum Reason
    {
        /** The live contents already match the target. */
        ALREADY_SORTED,
        /** The target was structurally incompatible; nothing was attempted. */
        INVALID,
        /** Work was needed but no pair could be moved with the available primitives. */
        UNREACHABLE,
        /** A program was produced. */
        PLANNED
    }

    /**
     * @param groups  click groups to execute, each atomic within one tick
     * @param skipped pairs that could not be moved and were deliberately left alone
     * @param reason  why the program looks the way it does
     */
    public record Result(List<List<SortOp>> groups, int skipped, Reason reason)
    {
    }

    private SortPlanner()
    {
    }

    /**
     * Production entry point: plans against the live region contents.
     *
     * @param merge when true, same-item stacks are consolidated until every stack is as full as
     *              it can be before the ordering pass runs
     */
    public static Result plan(InventoryRegion region, List<ItemStack> target, boolean merge)
    {
        if (target.size() != region.size())
        {
            return new Result(List.of(), 0, Reason.INVALID);
        }

        SlotModels.Handles handles = new SlotModels.Handles();
        SlotModel live = SlotModels.fromRegion(region, handles);
        SlotModel wanted = SlotModels.fromStacks(target, handles);

        return plan(live, wanted, merge, findEmptyHotbarScratch(region));
    }

    /**
     * The testable core. Runs entirely on integer slot models.
     *
     * @param scratchHotbar an empty hotbar slot outside the region that the SWAP primitive may use
     *                      as a scratch, or -1 when none is available
     */
    static Result plan(SlotModel live, SlotModel target, boolean merge, int scratchHotbar)
    {
        if (target.size() != live.size())
        {
            return new Result(List.of(), 0, Reason.INVALID);
        }

        List<List<SortOp>> groups = new ArrayList<>();

        if (merge)
        {
            mergePhase(live, groups);
        }

        int skipped = sortPhase(live, target, scratchHotbar, groups);

        Reason reason;

        if (groups.isEmpty())
        {
            reason = (skipped > 0) ? Reason.UNREACHABLE : Reason.ALREADY_SORTED;
        }
        else
        {
            reason = Reason.PLANNED;
        }

        return new Result(groups, skipped, reason);
    }

    /**
     * Tops up same-item stacks until every stack is as full as it can be.
     *
     * <p>Merges greedily rather than consulting the target. An earlier version stopped as soon as
     * the number of stacks of an item matched the target's, which was wrong whenever the counts
     * differed: two stacks of 50 and 60 already have the same <i>stack count</i> as the ideal
     * 64 + 46, so nothing was merged and the stacks were left half full. Maximal filling produces a
     * unique set of counts, so a greedy fill lands exactly on what
     * {@code InventorySorter.mergeIdentical} computed for the target.</p>
     */
    private static void mergePhase(SlotModel live, List<List<SortOp>> groups)
    {
        boolean merged = true;
        int progressSteps = 0;

        // Safety bound. Every accepted merge is required to strictly increase the amount held in
        // some stack, so the loop cannot legitimately run anywhere near this many times. The bound
        // exists because this code runs on the client thread during a key press: an unbounded loop
        // here freezes the whole game rather than raising an error, which is exactly what a user
        // reported. Better to emit a partial plan than to hang.
        final int maxSteps = Math.max(32, 4 * live.size() * live.size());

        while (merged && progressSteps < maxSteps)
        {
            merged = false;

            final int n = live.size();

            for (int i = 0; i < n && merged == false; i++)
            {
                for (int j = i + 1; j < n; j++)
                {
                    if (live.isEmpty(i) || live.isEmpty(j) || live.sameItem(i, j) == false)
                    {
                        continue;
                    }

                    // A cursor merge can only fill 'a' from 'b', so 'a' needs room. Skipping when it
                    // has none is also what guarantees progress: every accepted merge strictly
                    // grows a stack, which bounds the loop.
                    int space = live.cap(i) - live.count(i);

                    if (space <= 0)
                    {
                        continue;
                    }

                    int moved = Math.min(space, live.count(j));

                    if (moved >= live.count(j))
                    {
                        // Everything fits: pick up j, merge into i, and j ends up empty.
                        groups.add(List.of(SortOp.pickup(j), SortOp.pickup(i)));
                        live.setCount(i, live.count(i) + moved);
                        live.clear(j);
                    }
                    else
                    {
                        // Fill i up to the cap and push the remainder back into j.
                        groups.add(List.of(SortOp.pickup(j), SortOp.pickup(i), SortOp.pickup(j)));
                        live.setCount(i, live.count(i) + moved);
                        live.setCount(j, live.count(j) - moved);
                    }

                    merged = true;
                    progressSteps++;
                    break;
                }
            }
        }

        if (progressSteps >= maxSteps)
        {
            SortNStock.LOGGER.error("sort: merge phase stopped at its safety bound of {} steps; " +
                    "emitting a partial plan instead of looping", maxSteps);
        }
    }

    /**
     * Places each non-empty target stack into its slot, in as many passes as it takes.
     *
     * <p>Positions whose target is empty are skipped: the live and target contents are the same
     * multiset, so every leftover stack necessarily ends up in one of those positions.</p>
     *
     * <p><b>Stacks move in either direction.</b> The shulker boxes are reserved into the tail of
     * the container, which means they usually have to travel <i>towards higher indices</i> — the
     * opposite of the compaction direction. A source may therefore sit on either side of its
     * destination; {@link #findMatch} looks over the whole region and {@link #emitTransfer} handles
     * both directions with the same two primitives.</p>
     *
     * <p><b>Several passes.</b> One pass is not always enough: taking a stack from an earlier slot
     * to fill a later one can leave that earlier slot wanting a stack that has already been passed
     * over. Repeating the pass until it makes no progress fixes those shapes. The pass bound keeps a
     * pathological target from looping on the client thread — sorting runs during a key press, so
     * hanging here would freeze the game.</p>
     *
     * @return the number of slots that could not be placed
     */
    private static int sortPhase(SlotModel live, SlotModel target, int scratchHotbar,
                                 List<List<SortOp>> groups)
    {
        final int n = live.size();
        final int maxPasses = Math.max(2, n);
        int skipped = 0;

        for (int pass = 0; pass < maxPasses; pass++)
        {
            int moved = 0;
            skipped = 0;

            for (int i = 0; i < n; i++)
            {
                if (live.matchesExactly(i, target, i))
                {
                    continue;
                }

                // An empty target slot needs no explicit action; see the method comment.
                if (target.isEmpty(i))
                {
                    continue;
                }

                int j = findMatch(live, target, i);

                if (j < 0)
                {
                    // The wanted stack is nowhere that may still be disturbed.
                    skipped++;
                    continue;
                }

                if (emitTransfer(live, i, j, scratchHotbar, groups))
                {
                    moved++;
                }
                else
                {
                    skipped++;
                }
            }

            if (moved == 0)
            {
                break;
            }
        }

        return skipped;
    }

    /**
     * Moves the stack at {@code j} into {@code i}, choosing the cheapest correct primitive.
     *
     * @return false if no safe primitive is available for this pair
     */
    private static boolean emitTransfer(SlotModel live, int i, int j, int scratchHotbar,
                                        List<List<SortOp>> groups)
    {
        if (live.isEmpty(i))
        {
            // Nothing to displace: a plain move. Pick j up and put it down at i.
            groups.add(List.of(SortOp.pickup(j), SortOp.pickup(i)));
            live.move(j, i);
            return true;
        }

        if (live.sameItem(i, j))
        {
            // A cursor swap would merge these instead of swapping them, so use the hotbar
            // scratch variant, which is item-type agnostic.
            if (scratchHotbar < 0)
            {
                return false;
            }

            groups.add(List.of(
                    SortOp.swap(i, scratchHotbar),
                    SortOp.swap(j, scratchHotbar),
                    SortOp.swap(i, scratchHotbar)));
        }
        else
        {
            groups.add(List.of(SortOp.pickup(i), SortOp.pickup(j), SortOp.pickup(i)));
        }

        live.swap(i, j);

        return true;
    }

    /**
     * Finds the stack wanted at {@code wantedIndex}, anywhere in the region.
     *
     * <p>Slots that already hold their final stack are never taken from, so a later placement
     * cannot undo an earlier one. Everything else is fair game, on either side of the destination —
     * the shulker boxes have to move towards the end of the container.</p>
     */
    private static int findMatch(SlotModel live, SlotModel target, int wantedIndex)
    {
        // Prefer an exact match first (same item, components *and* count). Once merging has run,
        // the live and target contents hold the same stacks, so an exact match always exists and
        // this puts each stack in precisely its target slot. Matching on the item alone would
        // happily drag a 46-count stack into the slot that asked for a 64, leaving same-item
        // stacks in the wrong order — noticeable when sorting by count.
        for (int i = 0; i < live.size(); i++)
        {
            if (isAvailableSource(live, target, i, wantedIndex) &&
                live.matchesExactly(i, target, wantedIndex))
            {
                return i;
            }
        }

        // Fallback: the right item, any count. Only reachable when the exact stack is not present.
        for (int i = 0; i < live.size(); i++)
        {
            if (isAvailableSource(live, target, i, wantedIndex) &&
                live.matchesItem(i, target, wantedIndex))
            {
                return i;
            }
        }

        return -1;
    }

    /** A slot may be used as a source unless it is the destination itself or already settled. */
    private static boolean isAvailableSource(SlotModel live, SlotModel target, int index, int wantedIndex)
    {
        return index != wantedIndex && live.matchesExactly(index, target, index) == false;
    }

    /**
     * Finds a hotbar slot that is empty and is not itself part of the region, so it can be used
     * as a scratch slot by the SWAP primitive.
     *
     * <p>Resolved once per plan rather than per swap: the scratch slot lies outside the region and
     * every SWAP group restores it to empty, so its state cannot change while planning.</p>
     *
     * @return the hotbar index 0-8, or -1 when there is no usable scratch slot
     */
    private static int findEmptyHotbarScratch(InventoryRegion region)
    {
        var player = Minecraft.getInstance().player;

        if (player == null)
        {
            return -1;
        }

        Inventory playerInventory = player.getInventory();
        AbstractContainerMenu menu = region.getMenu();

        for (int hotbar = 0; hotbar < Inventory.getSelectionSize(); hotbar++)
        {
            // Skip hotbar slots that are in the region being sorted.
            if (isInRegion(region, playerInventory, hotbar))
            {
                continue;
            }

            if (findMenuSlot(menu, playerInventory, hotbar) >= 0 && playerInventory.getItem(hotbar).isEmpty())
            {
                return hotbar;
            }
        }

        return -1;
    }

    private static boolean isInRegion(InventoryRegion region, Object container, int containerSlot)
    {
        for (int regionIdx = 0; regionIdx < region.size(); regionIdx++)
        {
            Slot slot = region.slot(regionIdx);

            if (slot.container == container && slot.getContainerSlot() == containerSlot)
            {
                return true;
            }
        }

        return false;
    }

    private static int findMenuSlot(AbstractContainerMenu menu, Object container, int containerSlot)
    {
        for (int i = 0; i < menu.slots.size(); i++)
        {
            Slot slot = menu.slots.get(i);

            if (slot.container == container && slot.getContainerSlot() == containerSlot)
            {
                return i;
            }
        }

        return -1;
    }
}
