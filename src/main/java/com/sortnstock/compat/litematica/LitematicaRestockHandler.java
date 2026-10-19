package com.sortnstock.compat.litematica;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.sortnstock.SortNStock;
import com.sortnstock.config.ConfigToggles;
import com.sortnstock.config.Configs;
import com.sortnstock.restock.RestockManager;
import com.sortnstock.restock.SlotLookup;
import com.sortnstock.restock.Thresholds;

/**
 * Decides when the Litematica integration should restock, and does it at the right moment.
 *
 * <p>There is exactly one evaluation point: the head of Litematica's pick block, reached from a
 * right-click on a schematic position. That is the only moment at which a restock can land before
 * the hotbar slot is switched, so the same click can use the restocked stack. There is no
 * trigger-timing option — a tick-based mode could never help the click in progress.</p>
 *
 * <p>Design boundary (deliberately narrow): SortNStock only moves the schematic's required item
 * into the hotbar slots Litematica is allowed to use. Slot switching itself is left to
 * Litematica's pick block, and block placement is left to Easy Place. SortNStock never
 * intercepts or cancels the right-click.</p>
 */
public final class LitematicaRestockHandler
{
    /**
     * Upper bound on click groups dispatched synchronously before pick block. A single hotbar
     * slot normally needs one or two source stacks, so this is generous while still bounded.
     */
    private static final int MAX_SYNC_GROUPS = 4;

    private static int cooldownTicks;

    private LitematicaRestockHandler()
    {
    }

    public static boolean isEnabled()
    {
        return LitematicaCompat.isAvailable()
                && Configs.Litematica.LITEMATICA_AUTO_RESTOCK.getBooleanValue();
    }

    /**
     * Called from the Mixin injected at the head of Litematica's
     * {@code InventoryUtils.schematicWorldPickBlock}, i.e. after Litematica has decided a block
     * should be placed and before it switches the hotbar slot.
     *
     * <p>Because the clicks are sent immediately, Litematica's pick block observes the
     * restocked stack in the very same tick, so the same right-click can use it.</p>
     */
    public static void onBeforePickBlock(ItemStack requiredStack, Minecraft mc)
    {
        if (isEnabled() == false || requiredStack == null || requiredStack.isEmpty())
        {
            return;
        }

        if (mc.player == null || mc.gameMode == null)
        {
            return;
        }

        if (cooldownTicks > 0)
        {
            return;
        }

        final Player player = mc.player;
        final Item required = requiredStack.getItem();
        final ItemStack mainHand = player.getMainHandItem();
        final boolean holdsRequired = mainHand.isEmpty() == false && mainHand.getItem() == required;

        if (holdsRequired == false)
        {
            // The hand is empty, or holds something other than the required item.
            if (ConfigToggles.isOn(Configs.Litematica.LITEMATICA_FROM_SHULKER_BOX) == false)
            {
                return;
            }

            // If the item is already somewhere in the player's inventory, Litematica's own pick
            // block will select that slot. Restocking as well fights it: after a refill had landed
            // in slot 2 while slot 1 was selected, the next right-click restocked again instead of
            // letting pick block switch to slot 2. Only restock when pick block genuinely has
            // nothing to select — which is also the only case where a shulker box can help, since
            // pick block cannot see inside one.
            if (hasItemInInventory(player, required))
            {
                SortNStock.debugLog(
                        "litematica: '{}' is already in the inventory, leaving the pick block to it",
                        required);
                return;
            }
        }
        else
        {
            // The right item is held, so only the count can be the problem.
            if (ConfigToggles.isOn(Configs.Litematica.LITEMATICA_LOW_COUNT) == false)
            {
                return;
            }

            if (Thresholds.supportsCountRestock(mainHand) == false ||
                mainHand.getCount() > Thresholds.threshold())
            {
                return;
            }
        }

        restockNow(mc, player, requiredStack);
    }

    /**
     * True when the player already carries the item anywhere in the inventory.
     *
     * <p>Litematica's pick block can select any hotbar slot holding the item, and can move it out of
     * the main inventory into one. So a carried item needs no restock — restocking would compete
     * with the pick block instead of helping it.</p>
     */
    private static boolean hasItemInInventory(Player player, Item required)
    {
        Inventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);

            if (stack.isEmpty() == false && stack.getItem() == required)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Only advances the cooldown. The integration has no tick-driven evaluation any more, but the
     * cooldown still has to decay somewhere, and the client tick is the natural place.
     */
    public static void onClientTick(Minecraft mc)
    {
        if (cooldownTicks > 0)
        {
            cooldownTicks--;
        }
    }

    private static void restockNow(Minecraft mc, Player player, ItemStack requiredStack)
    {
        // Target slots come solely from Litematica's own pickBlockableSlots config. If nothing
        // is allowed there, skip this restock entirely rather than inventing a fallback.
        final Set<Integer> allowedHotbarSlots = LitematicaCompat.getPickBlockableHotbarSlots();

        if (allowedHotbarSlots.isEmpty())
        {
            SortNStock.debugLog("litematica: pickBlockableSlots is empty, skipping restock");
            return;
        }

        final AbstractContainerMenu menu = player.containerMenu;
        final List<Integer> targets = new ArrayList<>();

        for (int menuSlotId : SlotLookup.hotbarSlots(menu, player))
        {
            int containerSlot = menu.slots.get(menuSlotId).getContainerSlot();

            if (allowedHotbarSlots.contains(containerSlot))
            {
                targets.add(menuSlotId);
            }
        }

        if (targets.isEmpty())
        {
            SortNStock.debugLog("litematica: no pick-blockable hotbar slot is present in this menu");
            return;
        }

        int executed = RestockManager.planAndExecuteNow(mc, requiredStack, MAX_SYNC_GROUPS, targets);

        if (executed > 0)
        {
            cooldownTicks = Configs.Litematica.LITEMATICA_COOLDOWN_TICKS.getIntegerValue();
            SortNStock.debugLog("litematica: restocked for '{}' with {} group(s) before pick block",
                    requiredStack.getItem(), executed);
        }
    }
}
