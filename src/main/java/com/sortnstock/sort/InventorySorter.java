package com.sortnstock.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import com.sortnstock.config.options.SortKey;

/**
 * Pure sorting logic: turns the region's current contents into the desired arrangement.
 *
 * <p>This class performs no I/O and issues no clicks, which keeps the ordering rules easy to
 * reason about and to unit test.</p>
 */
public final class InventorySorter
{
    private InventorySorter()
    {
    }

    /**
     * Computes the desired arrangement of the region, as a list of stacks of the same length
     * as the region.
     *
     * <p>Merging partial stacks and compacting towards the front are not options: both are always
     * applied. A "tidy up" is expected to merge and to pack, so neither was worth asking about.</p>
     */
    public static List<ItemStack> computeTarget(InventoryRegion region, SortKey key)
    {
        final int n = region.size();

        List<ItemStack> current = new ArrayList<>(n);
        List<Integer> occupied = new ArrayList<>();

        for (int i = 0; i < n; i++)
        {
            ItemStack stack = region.slot(i).getItem();
            current.add(stack.copy());

            if (stack.isEmpty() == false)
            {
                occupied.add(i);
            }
        }

        List<ItemStack> sorted = occupied.stream()
                .map(i -> current.get(i).copy())
                .sorted(comparator(region, key))
                .collect(Collectors.toCollection(ArrayList::new));

        sorted = mergeIdentical(region, sorted);

        List<ItemStack> target = new ArrayList<>(n);

        for (int i = 0; i < n; i++)
        {
            target.add(ItemStack.EMPTY);
        }

        // Split the sorted list, which already has the shulker boxes last, into the ordinary items
        // and the boxes, so each group can go to its own region of the container.
        List<ItemStack> ordinary = new ArrayList<>();
        List<ItemStack> shulkers = new ArrayList<>();

        for (ItemStack stack : sorted)
        {
            if (isShulkerBox(stack))
            {
                shulkers.add(stack);
            }
            else
            {
                ordinary.add(stack);
            }
        }

        int tailStart = TargetLayout.shulkerTailStart(n, ordinary.size(), shulkers.size());
        int[] ordinaryDestinations = TargetLayout.otherDestinations(ordinary.size());
        int[] shulkerDestinations = TargetLayout.shulkerDestinations(tailStart, shulkers.size());

        for (int i = 0; i < ordinary.size(); i++)
        {
            target.set(ordinaryDestinations[i], ordinary.get(i));
        }

        for (int i = 0; i < shulkers.size(); i++)
        {
            target.set(shulkerDestinations[i], shulkers.get(i));
        }

        return target;
    }

    /**
     * The ordering used to produce the target arrangement.
     *
     * <p>Two rules compose here:</p>
     *
     * <ol>
     *   <li><b>Shulker boxes always go last.</b> They are storage rather than building material, so
     *       they are grouped at the end of the container whatever the sort key and whichever
     *       direction is chosen. The category rank is therefore applied <i>outside</i> the
     *       ascending/descending reversal — otherwise reversing would move them to the front.</li>
     *   <li>The chosen key, plus a deterministic registry-name tie-break so equal keys do not
     *       reorder randomly between runs.</li>
     * </ol>
     */
    public static Comparator<ItemStack> comparator(InventoryRegion region, SortKey key)
    {
        Comparator<ItemStack> bySortKey = (a, b) -> compareByKey(a, b, key);

        // Deterministic tie-break so equal primary keys do not reorder randomly between runs.
        bySortKey = bySortKey.thenComparing(InventorySorter::registryName);

        final Comparator<ItemStack> keyOrder = bySortKey;

        // Within a category: the shulker boxes follow their own rule (fill state, colour, mixed
        // before pure, then their contents), everything else follows the chosen key. All of it is
        // ascending — the direction option was removed.
        Comparator<ItemStack> within = (a, b) ->
                isShulkerBox(a) ? compareShulkerBoxes(a, b, keyOrder) : keyOrder.compare(a, b);

        return categoryFirst(stack -> isShulkerBox(stack) ? 1 : 0, within);
    }

    /**
     * Puts one category last.
     *
     * <p>Split out so that "shulker boxes always come after ordinary items" stays an explicit,
     * testable property of the composition rather than being buried in the comparator above.</p>
     *
     * @param categoryRank smaller values sort first
     */
    static <T> Comparator<T> categoryFirst(ToIntFunction<T> categoryRank, Comparator<T> within)
    {
        return Comparator.comparingInt(categoryRank).thenComparing(within);
    }

    /**
     * Orders two shulker boxes by the requested priority chain.
     *
     * <p>Fill state, then colour, then mixed-before-pure come from {@link ShulkerOrder}. After that
     * the box's contents decide: for a box that is not full, the one holding least comes first; for a
     * full box, its first contents are compared <i>as ordinary items</i>, using the very same
     * comparator the ordinary items were sorted with — "顺序与普通物品一致".</p>
     */
    private static int compareShulkerBoxes(ItemStack a, ItemStack b, Comparator<ItemStack> itemOrder)
    {
        ShulkerOrder.Key keyA = shulkerKey(a);
        ShulkerOrder.Key keyB = shulkerKey(b);

        int byPriority = ShulkerOrder.comparePriority(keyA, keyB);

        if (byPriority != 0)
        {
            return byPriority;
        }

        int byContents;

        if (keyA.fill() == ShulkerOrder.Fill.FULL)
        {
            byContents = itemOrder.compare(firstContent(a), firstContent(b));
        }
        else
        {
            byContents = ShulkerOrder.compareContent(keyA, keyB);
        }

        if (byContents != 0)
        {
            return byContents;
        }

        return registryName(a).compareTo(registryName(b));
    }

    /**
     * Derives the comparison inputs of one shulker box.
     *
     * <p>A box with no contents component is empty; vanilla drops the component when the last item
     * is removed. A box counts as full only when every one of its slots is occupied <i>and</i> every
     * stack in it is at its own maximum — "no room left", which is what "满盒" means in practice. A
     * box holding 27 single items therefore counts as partly filled, not full.</p>
     */
    private static ShulkerOrder.Key shulkerKey(ItemStack stack)
    {
        ShulkerOrder.Fill fill;
        boolean mixed = false;
        int amount = 0;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);

        if (contents == null)
        {
            fill = ShulkerOrder.Fill.EMPTY;
        }
        else
        {
            List<ItemStack> items = contents.nonEmptyItemCopyStream().toList();

            if (items.isEmpty())
            {
                fill = ShulkerOrder.Fill.EMPTY;
            }
            else
            {
                fill = ShulkerOrder.Fill.FULL;

                for (ItemStack item : items)
                {
                    amount += item.getCount();

                    if (item.getCount() < item.getItem().getDefaultMaxStackSize())
                    {
                        fill = ShulkerOrder.Fill.PARTIAL;
                    }
                }

                if (items.size() < ShulkerBoxBlockEntity.CONTAINER_SIZE)
                {
                    fill = ShulkerOrder.Fill.PARTIAL;
                }

                mixed = hasSeveralKinds(items);
            }
        }

        return new ShulkerOrder.Key(fill, shulkerColorRank(stack), mixed, amount);
    }

    /** True when the box holds more than one kind of item — the "杂盒" case. */
    private static boolean hasSeveralKinds(List<ItemStack> items)
    {
        Item first = items.get(0).getItem();

        for (ItemStack item : items)
        {
            if (item.getItem() != first)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The item in the box's first occupied contents slot, used to order full boxes by their first
     * contents "in the same order as ordinary items". Only called for full boxes, which always have
     * contents.
     */
    private static ItemStack firstContent(ItemStack stack)
    {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);

        if (contents == null)
        {
            return ItemStack.EMPTY;
        }

        return contents.nonEmptyItemCopyStream().findFirst().orElse(ItemStack.EMPTY);
    }

    /**
     * The creative-inventory colour position of a box.
     *
     * <p>{@code DyeColor}'s declaration order is exactly the order the creative inventory lists the
     * coloured blocks in, so its ordinal is used directly. The plain box has no colour and sorts
     * before them, because the creative inventory does not list it among the colours.</p>
     */
    private static int shulkerColorRank(ItemStack stack)
    {
        if (stack.getItem() instanceof BlockItem blockItem &&
            blockItem.getBlock() instanceof ShulkerBoxBlock shulkerBox)
        {
            DyeColor color = shulkerBox.getColor();

            return color == null ? ShulkerOrder.UNDYED_COLOR_RANK : color.ordinal();
        }

        return ShulkerOrder.UNDYED_COLOR_RANK;
    }

    /**
     * True for any shulker box, including the sixteen dyed variants and modded ones.
     *
     * <p>Two checks, either of which is enough, because there are two legitimate notions of "is a
     * shulker box":</p>
     *
     * <ul>
     *   <li>the vanilla {@code minecraft:shulker_boxes} <b>item tag</b> — the authoritative
     *       grouping, which a mod adding its own shulker box can join;</li>
     *   <li>the item being a {@link BlockItem} over a {@link ShulkerBoxBlock} — true by
     *       construction for the vanilla boxes, and independent of whether tags happen to be
     *       loaded.</li>
     * </ul>
     *
     * <p>Neither alone is sufficient: relying on the block alone misses a modded box with a custom
     * item class, and relying on the tag alone misses anything that is a shulker box but not tagged
     * as one.</p>
     */
    static boolean isShulkerBox(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return false;
        }

        if (BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).is(ItemTags.SHULKER_BOXES))
        {
            return true;
        }

        return stack.getItem() instanceof BlockItem blockItem &&
               blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    /**
     * The region slots that currently hold a shulker box, for diagnostic logging.
     *
     * <p>With {@link #shulkerPositions} on the target this makes each sort self-describing: the
     * live positions before the clicks, the positions the target asks for, and the difference
     * afterwards. Comparing the live positions across two consecutive presses shows whether the
     * previous press moved anything at all.</p>
     */
    static List<Integer> liveShulkerPositions(InventoryRegion region)
    {
        List<Integer> positions = new ArrayList<>();

        for (int i = 0; i < region.size(); i++)
        {
            if (isShulkerBox(region.slot(i).getItem()))
            {
                positions.add(i);
            }
        }

        return positions;
    }

    /**
     * The target slots that hold a shulker box, for diagnostic logging.
     *
     * <p>Together with {@link #countShulkerBoxes} this separates the two ways the "shulker boxes
     * sort last" rule can fail: if the count is right but these positions are not at the end of the
     * filled range, the ordering is wrong; if the count is wrong, detection is wrong.</p>
     */
    static List<Integer> shulkerPositions(List<ItemStack> target)
    {
        List<Integer> positions = new ArrayList<>();

        for (int i = 0; i < target.size(); i++)
        {
            if (isShulkerBox(target.get(i)))
            {
                positions.add(i);
            }
        }

        return positions;
    }

    private static int compareByKey(ItemStack a, ItemStack b, SortKey key)
    {
        return switch (key)
        {
            case REGISTRY_NAME -> registryName(a).compareTo(registryName(b));
            case DISPLAY_NAME  -> a.getHoverName().getString().compareTo(b.getHoverName().getString());
            case COUNT         -> Integer.compare(a.getCount(), b.getCount());
            case DURABILITY    -> Integer.compare(remainingDurability(a), remainingDurability(b));
            case NBT           -> a.getComponents().toString().compareTo(b.getComponents().toString());
            case TAG           -> tagString(a).compareTo(tagString(b));
        };
    }

    private static String registryName(ItemStack stack)
    {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Remaining durability; non-damageable items sort as if fully intact, and are grouped. */
    private static int remainingDurability(ItemStack stack)
    {
        if (stack.isDamageableItem() == false)
        {
            return Integer.MAX_VALUE;
        }

        return stack.getMaxDamage() - stack.getDamageValue();
    }

    private static String tagString(ItemStack stack)
    {
        // Item#builtInRegistryHolder() is deprecated in 26.1; wrapAsHolder is the supported path.
        return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).tags()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * Top-up merging: folds partial stacks of identical items into full stacks, in the order
     * they were sorted. Never creates items; the total count per item is preserved exactly.
     */
    private static List<ItemStack> mergeIdentical(InventoryRegion region, List<ItemStack> stacks)
    {
        List<ItemStack> result = new ArrayList<>(stacks.size());

        for (ItemStack stack : stacks)
        {
            if (stack.isEmpty())
            {
                continue;
            }

            int remaining = stack.getCount();

            for (ItemStack existing : result)
            {
                if (remaining <= 0)
                {
                    break;
                }

                if (ItemStack.isSameItemSameComponents(existing, stack) == false)
                {
                    continue;
                }

                int cap = maxStackSize(region, existing);
                int space = cap - existing.getCount();

                if (space > 0)
                {
                    int moved = Math.min(space, remaining);
                    existing.setCount(existing.getCount() + moved);
                    remaining -= moved;
                }
            }

            if (remaining > 0)
            {
                ItemStack copy = stack.copy();
                copy.setCount(remaining);
                result.add(copy);
            }
        }

        return result;
    }

    /**
     * Max stack size for this item as accepted by the region's slots.
     *
     * <p>This deliberately does not ask a single arbitrary slot. It previously asked slot 0, which
     * broke as soon as a region could contain a restricted slot — the player inventory region used
     * to begin with the helmet slot, whose limit is 1, so every item was treated as unstackable
     * and merging silently did nothing. The best limit available across the region is used
     * instead, seeded with the item's own limit.</p>
     */
    public static int maxStackSize(InventoryRegion region, ItemStack stack)
    {
        // NOTE: ItemStack has no max-stack-size accessor in 26.1; the item default is the
        // correct fallback when there is no slot to ask.
        int max = stack.getItem().getDefaultMaxStackSize();

        for (int i = 0; i < region.size(); i++)
        {
            max = Math.max(max, region.slot(i).getMaxStackSize(stack));
        }

        return max;
    }
}
