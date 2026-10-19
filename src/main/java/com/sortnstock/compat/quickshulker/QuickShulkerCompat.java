package com.sortnstock.compat.quickshulker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.loader.api.FabricLoader;

import fi.dy.masa.malilib.util.InventoryUtils;

import com.sortnstock.SortNStock;
import com.sortnstock.restock.SlotLookup;

/**
 * Optional QuickShulker integration.
 *
 * <p>Two separate concerns live here:</p>
 *
 * <ol>
 *   <li><b>Reading</b> what is inside a carried shulker box. This uses only the vanilla
 *       {@code DataComponents.CONTAINER} component through MaLiLib's {@link InventoryUtils},
 *       so it works with or without QuickShulker installed.</li>
 *   <li><b>Opening</b> a carried shulker box so its items can actually be moved out. Mutating a
 *       shulker's contents is server-authoritative, so the box has to be opened through
 *       QuickShulker's own packet. This is the only part that requires QuickShulker.</li>
 * </ol>
 *
 * <p>No reflection and no obfuscated names are used. QuickShulker's classes are touched only
 * through {@link Holder}, which is loaded lazily after the mod-loaded check, so a missing
 * QuickShulker can never cause a {@code NoClassDefFoundError}.</p>
 */
public final class QuickShulkerCompat
{
    private static final boolean AVAILABLE = FabricLoader.getInstance().isModLoaded("quickshulker");

    private QuickShulkerCompat()
    {
    }

    public static boolean isAvailable()
    {
        return AVAILABLE;
    }

    /**
     * True when the open request can actually reach the server.
     *
     * <p>QuickShulker being installed client-side is not enough: the packet is a custom payload, so
     * the server has to accept it too. Sending into the void looks like "the box never opened".
     * Modelled on the same guard in BetterShulkerHUDMod.</p>
     */
    public static boolean canSend()
    {
        if (AVAILABLE == false)
        {
            return false;
        }

        try
        {
            return Holder.canSend();
        }
        catch (Throwable t)
        {
            SortNStock.LOGGER.warn("quickshulker: could not check the packet channel", t);
            return false;
        }
    }

    /**
     * Reading what a carried shulker box holds, and asking QuickShulker to open it.
     *
     * <p>The old {@code sourceSlots} helper is gone with the shulker source tier: an open shulker
     * box's slots are ordinary menu slots that the "open container" tier already returns, so it
     * duplicated them.</p>
     */

    /** Items stored inside a shulker box item stack (vanilla read, no QuickShulker needed). */
    public static NonNullList<ItemStack> readContents(ItemStack shulkerStack)
    {
        return InventoryUtils.getStoredItems(shulkerStack);
    }

    public static boolean containsItem(ItemStack shulkerStack, Item item)
    {
        for (ItemStack stored : readContents(shulkerStack))
        {
            if (stored.isEmpty() == false && stored.getItem() == item)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Asks QuickShulker to open the first carried shulker box that contains the wanted item.
     *
     * <p>Opening is asynchronous: the server answers with a real {@code ShulkerBoxMenu}, which
     * only appears on a later client tick. The caller therefore re-runs its restock pass after
     * this returns true, rather than expecting items to move immediately.</p>
     *
     * @return true if an open request was sent
     */
    public static boolean requestOpenShulkerWithItem(Minecraft mc, Player player, Item item)
    {
        if (AVAILABLE == false)
        {
            return false;
        }

        final AbstractContainerMenu menu = player.containerMenu;
        final Inventory inventory = player.getInventory();

        // Check the whole inventory, not just the hotbar and offhand: a box of building material is
        // usually kept in the backpack, and QuickShulker's packet addresses a slot of the open menu,
        // which exists for every inventory slot.
        for (int containerSlot = 0; containerSlot < inventory.getContainerSize(); containerSlot++)
        {
            ItemStack stack = inventory.getItem(containerSlot);

            if (stack.isEmpty() == false && containsItem(stack, item))
            {
                int menuSlotId = SlotLookup.findMenuSlot(menu, inventory, containerSlot);

                if (menuSlotId >= 0)
                {
                    // In a creative screen the menu's slot indexing does not match what the server
                    // expects, so the id has to be translated before it is sent. Sending the raw
                    // menu index there addresses a different slot — a defect BetterShulkerHUDMod
                    // works around with the same ClientUtil call.
                    int serverSlotId = Holder.toServerSlot(menu, menu.slots.get(menuSlotId), menuSlotId);

                    SortNStock.debugLog("quickshulker: requesting open of slot {} (menu slot {}) for item {}",
                            serverSlotId, menuSlotId, item);
                    Holder.sendOpenPacket(serverSlotId);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Isolated holder so that QuickShulker's classes are only resolved once it is known to be
     * present.
     */
    private static final class Holder
    {
        private static void sendOpenPacket(int serverSlotId)
        {
            net.kyrptonaught.quickshulker.network.OpenShulkerPacket.sendOpenPacket(serverSlotId);
        }

        private static boolean canSend()
        {
            return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                    net.kyrptonaught.quickshulker.network.OpenShulkerPacket.OPEN_SHULKER_PACKET_ID);
        }

        /**
         * Translates a menu slot id into the id the server expects.
         *
         * <p>Creative screens index their slots differently, so the raw menu index means something
         * else server-side. Outside a creative screen the ids are the same.</p>
         */
        private static int toServerSlot(AbstractContainerMenu menu, Slot slot, int menuSlotId)
        {
            Player player = Minecraft.getInstance().player;

            if (player != null &&
                net.kyrptonaught.quickshulker.client.ClientUtil.isCreativeScreen(player))
            {
                return net.kyrptonaught.quickshulker.client.ClientUtil.getSlotId(menu, slot);
            }

            return menuSlotId;
        }
    }
}
