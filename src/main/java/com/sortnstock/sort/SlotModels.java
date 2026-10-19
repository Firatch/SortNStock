package com.sortnstock.sort;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Production adapter that builds a {@link SlotModel} from the game's real slots and item stacks.
 *
 * <p>This is deliberately the only part of the planner that touches Minecraft types. The arithmetic
 * lives in {@link SlotModel} and {@link SortPlanner}, where it can be unit tested; everything that
 * genuinely needs the live game — reading {@code Slot}s and deciding item equality from
 * {@code ItemStack.isSameItemSameComponents} — is confined here.</p>
 */
final class SlotModels
{
    private SlotModels()
    {
    }

    /**
     * Maps live stacks onto opaque handles by item <i>and</i> components, so two stacks of the same
     * item with different damage or NBT counts as different things — which is what the sort keys and
     * the merge rules assume.
     */
    static final class Handles
    {
        private final List<ItemStack> representatives = new ArrayList<>();

        Handles()
        {
            // Handle 0 is reserved for "empty", so the table is 1-based.
            this.representatives.add(ItemStack.EMPTY);
        }

        int of(ItemStack stack)
        {
            for (int handle = 1; handle < this.representatives.size(); handle++)
            {
                if (ItemStack.isSameItemSameComponents(this.representatives.get(handle), stack))
                {
                    return handle;
                }
            }

            this.representatives.add(stack.copy());

            return this.representatives.size() - 1;
        }
    }

    static SlotModel fromRegion(InventoryRegion region, Handles handles)
    {
        final int n = region.size();
        int[] items = new int[n];
        int[] counts = new int[n];
        int[] caps = new int[n];

        for (int i = 0; i < n; i++)
        {
            Slot slot = region.slot(i);
            ItemStack stack = slot.getItem();

            if (stack.isEmpty() == false)
            {
                items[i] = handles.of(stack);
                counts[i] = stack.getCount();
                caps[i] = InventorySorter.maxStackSize(region, stack);
            }
        }

        return new SlotModel(items, counts, caps);
    }

    /**
     * Builds the target arrangement, reusing the handles allocated for the live contents so that a
     * target stack of the same item as a live stack gets the same handle and the two models can be
     * compared directly.
     */
    static SlotModel fromStacks(List<ItemStack> stacks, Handles handles)
    {
        final int n = stacks.size();
        int[] items = new int[n];
        int[] counts = new int[n];
        int[] caps = new int[n];

        for (int i = 0; i < n; i++)
        {
            ItemStack stack = stacks.get(i);

            if (stack.isEmpty() == false)
            {
                items[i] = handles.of(stack);
                counts[i] = stack.getCount();
                // Only the live model's limits are consulted by the planner; set these to the
                // item's own limit so the target is self-consistent anyway.
                caps[i] = Math.max(1, stack.getItem().getDefaultMaxStackSize());
            }
        }

        return new SlotModel(items, counts, caps);
    }
}
