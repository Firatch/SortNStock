package com.sortnstock.restock;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import com.sortnstock.SortNStock;
import com.sortnstock.compat.quickshulker.QuickShulkerCompat;
import com.sortnstock.config.Configs;
import com.sortnstock.restock.RestockQueue;

/**
 * Restocks from a shulker box when the player picks a block they do not carry.
 *
 * <p>Vanilla's middle-click pick block selects a block only if it is already in the inventory; when it
 * is not, the click does nothing. This closes that gap: the block under the crosshair is looked up,
 * and if the player carries none of it, a carried shulker box holding it is opened and the stack is
 * taken out. Because opening a box is a server round trip, the item arrives a tick or two later — the
 * player middle-clicks again and vanilla pick block selects it.</p>
 *
 * <p>Only the {@linkplain Configs.QuickShulker#QUICKSHULKER_RESTOCK_FROM_SHULKER existing "restock
 * from shulker boxes" switch} is used; there is no second setting for this path, because "take it out
 * of a box" is the same intent however the request was started.</p>
 *
 * <p>Deliberately does nothing in creative mode: pick block there clones the item instead of taking it
 * from the inventory, so there is nothing to restock.</p>
 */
public final class PickBlockRestockHandler
{
    private PickBlockRestockHandler()
    {
    }

    /**
     * Called from the head of {@code Minecraft.pickBlockOrEntity()}, i.e. for the player's own
     * pick-block key and nothing else.
     */
    public static void onPickBlock(Minecraft mc)
    {
        if (Configs.QuickShulker.QUICKSHULKER_RESTOCK_FROM_SHULKER.getBooleanValue() == false)
        {
            return;
        }

        if (mc.player == null || mc.level == null || mc.gameMode == null)
        {
            return;
        }

        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK)
        {
            return;
        }

        if (mc.player.isCreative() || mc.player.isSpectator())
        {
            return;
        }

        if (QuickShulkerCompat.canSend() == false)
        {
            return;
        }

        // A refill is already in flight. Placing a block shrinks the main hand, so this path and the
        // consumption trigger fire for the same action; without this guard each asked for its own box,
        // two boxes opened, and the stack count flickered between the client's prediction and the
        // server's state ("shows 63, actually 64").
        if (RestockManager.isWaitingForShulkerBox() || RestockQueue.isActive())
        {
            return;
        }

        final BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        final BlockState state = mc.level.getBlockState(pos);
        final Item item = state.getBlock().asItem();

        // Air, fluids and technical blocks have no item to pick.
        if (item == Items.AIR)
        {
            return;
        }

        // Vanilla handles anything the player already carries, and it does so better than we could.
        if (carries(mc.player, item))
        {
            return;
        }

        SortNStock.debugLog("pickblock: '{}' is not carried, asking the shulker boxes for it", item);
        RestockManager.requestFromShulker(mc, item);
    }

    /** True when the player has at least one of the item anywhere in the inventory. */
    private static boolean carries(Player player, Item item)
    {
        final Inventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            final ItemStack stack = inventory.getItem(slot);

            if (stack.isEmpty() == false && stack.getItem() == item)
            {
                return true;
            }
        }

        return false;
    }
}
