package com.sortnstock.sort;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;

import com.sortnstock.SortNStock;

/**
 * Executes a planned sort program through vanilla container-input packets.
 *
 * <p>The whole plan is dispatched in one client tick. This is the same basic approach used by
 * inventory-management mods: sorting is a sequence of server-authoritative slot operations, not
 * simulated cursor movement. The groups still keep the cursor empty between compound moves, so a
 * failed or interrupted operation can stop safely.</p>
 */
public final class SortQueue
{
    private static InventoryRegion region;
    private static List<net.minecraft.world.item.ItemStack> target;
    private static Deque<List<SortOp>> groups;

    private SortQueue()
    {
    }

    public static boolean isActive()
    {
        return groups != null && groups.isEmpty() == false;
    }

    public static void start(InventoryRegion region,
                             List<net.minecraft.world.item.ItemStack> target,
                             List<List<SortOp>> plan)
    {
        SortQueue.region = region;
        SortQueue.target = target;
        SortQueue.groups = new ArrayDeque<>(plan);
        SortNStock.debugLog("sort: queued {} operation group(s) for one-tick dispatch", plan.size());
    }

    public static void cancel()
    {
        region = null;
        target = null;
        groups = null;
    }

    public static void tick(Minecraft mc)
    {
        if (groups == null || groups.isEmpty())
        {
            cancel();
            return;
        }

        if (mc.player == null || mc.gameMode == null || mc.player.containerMenu != region.getMenu())
        {
            SortNStock.debugLog("sort: container changed, aborting remaining operations");
            cancel();
            return;
        }

        if (mc.player.containerMenu.getCarried().isEmpty() == false)
        {
            SortNStock.debugLog("sort: cursor not empty at sort start, aborting");
            cancel();
            return;
        }

        int executed = 0;

        while (groups.isEmpty() == false)
        {
            List<SortOp> group = groups.removeFirst();

            for (SortOp op : group)
            {
                execute(mc, op);
            }

            executed++;

            // Every planner group is required to leave the cursor empty. Do not continue if a
            // server/modded menu rejected a click and left an unexpected carried stack.
            if (mc.player.containerMenu.getCarried().isEmpty() == false)
            {
                SortNStock.debugLog("sort: cursor became non-empty after group {}, aborting", executed);
                cancel();
                return;
            }
        }

        logResidualMismatches();

        SortNStock.debugLog("sort: finished in one tick ({} operation group(s))", executed);
        cancel();
    }

    /**
     * Compares the live region against the target after the clicks have been issued.
     *
     * <p>This is the measurement that separates "the plan was wrong" from "the clicks did not
     * achieve the plan". A non-zero count here means the executed operations did not produce the
     * arrangement the planner simulated, which is what makes repeated sorts keep finding work to do
     * instead of reporting ALREADY_SORTED.</p>
     */
    private static void logResidualMismatches()
    {
        if (region == null || target == null)
        {
            return;
        }

        List<Integer> differing = new ArrayList<>();

        for (int i = 0; i < target.size(); i++)
        {
            net.minecraft.world.item.ItemStack live = region.slot(i).getItem();
            net.minecraft.world.item.ItemStack want = target.get(i);
            boolean same;

            if (live.isEmpty() && want.isEmpty())
            {
                same = true;
            }
            else if (live.isEmpty() || want.isEmpty())
            {
                same = false;
            }
            else
            {
                same = net.minecraft.world.item.ItemStack.isSameItemSameComponents(live, want) &&
                       live.getCount() == want.getCount();
            }

            if (same == false)
            {
                differing.add(i);
            }
        }

        SortNStock.debugLog("sort: after execution, {} slot(s) still differ from the target: {}",
                differing.size(), differing);
    }

    private static void execute(Minecraft mc, SortOp op)
    {
        final int menuSlotId = region.menuSlotId(op.regionIdx());
        final int containerId = region.getMenu().containerId;

        switch (op.kind())
        {
            case PICKUP -> mc.gameMode.handleContainerInput(
                    containerId, menuSlotId, 0, ContainerInput.PICKUP, mc.player);
            case SWAP -> mc.gameMode.handleContainerInput(
                    containerId, menuSlotId, op.hotbarIdx(), ContainerInput.SWAP, mc.player);
        }
    }
}
