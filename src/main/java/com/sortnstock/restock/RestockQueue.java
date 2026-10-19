package com.sortnstock.restock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import com.sortnstock.SortNStock;

/**
 * Executes restock click groups across ticks.
 *
 * <p>Each group is a short sequence of {@code PICKUP} clicks that must run entirely within one
 * tick so the cursor is empty again afterwards. Throttling (cooldown plus a per-tick cap) keeps
 * the module from issuing high-frequency container operations.</p>
 */
public final class RestockQueue
{
    private static AbstractContainerMenu menu;
    private static Deque<int[]> groups;
    private static int groupsPerTick = 1;

    private RestockQueue()
    {
    }

    public static boolean isActive()
    {
        return groups != null && groups.isEmpty() == false;
    }

    public static void start(AbstractContainerMenu menu, List<int[]> groups, int groupsPerTick)
    {
        RestockQueue.menu = menu;
        RestockQueue.groups = new ArrayDeque<>(groups);
        RestockQueue.groupsPerTick = Math.max(1, groupsPerTick);

        SortNStock.debugLog("restock: queued {} click group(s)", groups.size());
    }

    public static void cancel()
    {
        menu = null;
        groups = null;
    }

    public static void tick(Minecraft mc)
    {
        if (groups == null || groups.isEmpty())
        {
            cancel();
            return;
        }

        if (mc.player == null || mc.gameMode == null || mc.player.containerMenu != menu)
        {
            SortNStock.debugLog("restock: container changed, aborting remaining operations");
            cancel();
            return;
        }

        if (menu.getCarried().isEmpty() == false)
        {
            SortNStock.debugLog("restock: cursor not empty at group boundary, aborting");
            cancel();
            return;
        }

        for (int i = 0; i < groupsPerTick && groups.isEmpty() == false; i++)
        {
            executeGroupNow(mc, menu, groups.poll());
        }

        if (groups.isEmpty())
        {
            cancel();
        }
    }

    /**
     * Dispatches one click group immediately. Used both by {@link #tick} and by the synchronous
     * Litematica restock path, so the click semantics live in exactly one place.
     */
    public static void executeGroupNow(Minecraft mc, AbstractContainerMenu menu, int[] group)
    {
        for (int menuSlotId : group)
        {
            mc.gameMode.handleContainerInput(menu.containerId, menuSlotId, 0, ContainerInput.PICKUP, mc.player);
        }
    }
}
