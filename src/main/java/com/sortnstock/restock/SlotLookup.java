package com.sortnstock.restock;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Helpers for locating slots inside whatever menu is currently open.
 *
 * <p>Every menu that can be open — including {@code InventoryMenu} — contains the player's
 * inventory slots, so restocking never needs to change menus just to reach them.</p>
 */
public final class SlotLookup
{
    private SlotLookup()
    {
    }

    public static int findMenuSlot(AbstractContainerMenu menu, Container container, int containerSlot)
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

    /** Menu slot ids of the player's hotbar, in hotbar order. */
    public static List<Integer> hotbarSlots(AbstractContainerMenu menu, Player player)
    {
        Inventory inventory = player.getInventory();
        List<Integer> ids = new ArrayList<>();

        for (int hotbar = 0; hotbar < Inventory.getSelectionSize(); hotbar++)
        {
            int id = findMenuSlot(menu, inventory, hotbar);

            if (id >= 0)
            {
                ids.add(id);
            }
        }

        return ids;
    }

    public static int offhandSlot(AbstractContainerMenu menu, Player player)
    {
        return findMenuSlot(menu, player.getInventory(), Inventory.SLOT_OFFHAND);
    }

    /** Menu slot ids of the player's main inventory (i.e. everything except the hotbar). */
    public static List<Integer> mainInventorySlots(AbstractContainerMenu menu, Player player)
    {
        Inventory inventory = player.getInventory();
        List<Integer> ids = new ArrayList<>();

        for (int i = 0; i < menu.slots.size(); i++)
        {
            Slot slot = menu.slots.get(i);

            if (slot.container != inventory)
            {
                continue;
            }

            int containerSlot = slot.getContainerSlot();

            // Skip hotbar, armour and offhand: only the 27 main inventory slots are wanted.
            if (containerSlot >= Inventory.getSelectionSize() && containerSlot < Inventory.INVENTORY_SIZE)
            {
                ids.add(i);
            }
        }

        return ids;
    }

    /**
     * Menu slot ids belonging to a container other than the player's own inventory, i.e. the
     * container the player currently has open.
     */
    public static List<Integer> openContainerSlots(AbstractContainerMenu menu, Player player)
    {
        Inventory inventory = player.getInventory();
        List<Integer> ids = new ArrayList<>();

        for (int i = 0; i < menu.slots.size(); i++)
        {
            if (menu.slots.get(i).container != inventory)
            {
                ids.add(i);
            }
        }

        return ids;
    }

    /** True when something other than the player's own inventory menu is open. */
    public static boolean hasOpenContainer(AbstractContainerMenu menu, Player player)
    {
        return openContainerSlots(menu, player).isEmpty() == false;
    }
}
