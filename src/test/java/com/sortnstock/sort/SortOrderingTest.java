package com.sortnstock.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the sort ordering's composition rule.
 *
 * <p>The planner's own arithmetic is covered in {@link SortPlannerTest}; what matters here is that
 * the fixed category rank keeps shulker boxes after the ordinary items regardless of what the
 * within-category ordering does. The ordering itself is ascending only — the sort-direction option
 * was removed — so there is no descending case to mirror.</p>
 *
 * <p>{@code InventorySorter.categoryFirst} is generic precisely so this can be checked without
 * constructing {@code ItemStack}s, which Minecraft 26.1 cannot do outside a running game.</p>
 */
class SortOrderingTest
{
    /** Stand-in for a stack: a category (1 = shulker box), a sort key, and a tie-break name. */
    private record Entry(int category, int key, String name)
    {
    }

    private static final Comparator<Entry> BY_KEY =
            Comparator.comparingInt(Entry::key).thenComparing(Entry::name);

    private static List<String> namesOf(List<Entry> entries)
    {
        List<Entry> sorted = new ArrayList<>(entries);

        sorted.sort(InventorySorter.categoryFirst(Entry::category, BY_KEY));

        return sorted.stream().map(Entry::name).toList();
    }

    /**
     * Mixed input: category 0 is ordinary items, category 1 is shulker boxes.
     */
    private static List<Entry> sample()
    {
        return List.of(
                new Entry(1, 10, "shulker-b"),
                new Entry(0, 30, "ordinary-c"),
                new Entry(1, 5, "shulker-a"),
                new Entry(0, 10, "ordinary-a"),
                new Entry(0, 20, "ordinary-b"));
    }

    @Test
    void theCategoryRankGroupsTheBoxesAfterTheOrdinaryItems()
    {
        assertEquals(
                List.of("ordinary-a", "ordinary-b", "ordinary-c", "shulker-a", "shulker-b"),
                namesOf(sample()));
    }

    @Test
    void onlyOneCategoryBehavesLikeAPlainSort()
    {
        List<Entry> ordinary = List.of(
                new Entry(0, 30, "c"), new Entry(0, 10, "a"), new Entry(0, 20, "b"));

        assertEquals(List.of("a", "b", "c"), namesOf(ordinary));
    }

    /** Equal keys must not reorder between runs, so the tie-break has to be applied. */
    @Test
    void equalKeysFallBackToTheTieBreak()
    {
        List<Entry> entries = List.of(
                new Entry(0, 7, "zebra"), new Entry(0, 7, "alpha"));

        assertEquals(List.of("alpha", "zebra"), namesOf(entries));
    }
}
