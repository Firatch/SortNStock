package com.sortnstock.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sortnstock.sort.ShulkerOrder.Fill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the ordering inside the shulker-box group.
 *
 * <p>Stated priority: {@code 1 > 2 > 5 > 3 = 4} — fill state, then colour, then mixed-before-pure,
 * then the contents. Levels 1, 2, 5 and the "least contents first" half of level 4 live in
 * {@link ShulkerOrder}; level 4 for full boxes compares their first contents as ordinary items and
 * therefore lives in {@code InventorySorter}, where the item comparator is available.</p>
 */
class ShulkerOrderTest
{
    private record Box(ShulkerOrder.Key key, String name)
    {
    }

    /** Full priority chain followed by the contents rule, as the production code composes it. */
    private static final Comparator<Box> ORDER = (a, b) ->
    {
        int byPriority = ShulkerOrder.comparePriority(a.key(), b.key());

        return byPriority != 0 ? byPriority : ShulkerOrder.compareContent(a.key(), b.key());
    };

    private static ShulkerOrder.Key key(Fill fill, int color, boolean mixed, int amount)
    {
        return new ShulkerOrder.Key(fill, color, mixed, amount);
    }

    private static List<String> names(List<Box> boxes)
    {
        List<Box> copy = new ArrayList<>(boxes);
        copy.sort(ORDER);

        return copy.stream().map(Box::name).toList();
    }

    /** Level 1: empty, then partly filled, then full — whatever the colours do. */
    @Test
    void fillStateIsThePrimaryCriterion()
    {
        List<Box> boxes = List.of(
                new Box(key(Fill.FULL, 0, true, 1728), "full-white"),
                new Box(key(Fill.EMPTY, 15, false, 0), "empty-black"),
                new Box(key(Fill.PARTIAL, 0, true, 300), "partial-white"),
                new Box(key(Fill.EMPTY, 0, false, 0), "empty-white"),
                new Box(key(Fill.FULL, 15, true, 1728), "full-black"));

        assertEquals(
                List.of("empty-white", "empty-black", "partial-white", "full-white", "full-black"),
                names(boxes));
    }

    /** The explicit statement of priority 1 &gt; 2: fill state beats colour. */
    @Test
    void fillStateBeatsColour()
    {
        List<Box> boxes = List.of(
                new Box(key(Fill.FULL, 0, true, 1728), "full-white"),
                new Box(key(Fill.EMPTY, 15, false, 0), "empty-black"));

        assertEquals(List.of("empty-black", "full-white"), names(boxes));
    }

    /** Level 2 within one fill state: the creative-inventory colour order. */
    @Test
    void colourOrderAppliesWithinAFillState()
    {
        List<Box> boxes = List.of(
                new Box(key(Fill.PARTIAL, 11, false, 5), "purple"),
                new Box(key(Fill.PARTIAL, 0, false, 5), "white"),
                new Box(key(Fill.PARTIAL, 15, false, 5), "black"),
                new Box(key(Fill.PARTIAL, 3, false, 5), "light-blue"));

        assertEquals(List.of("white", "light-blue", "purple", "black"), names(boxes));
    }

    /** Level 5: a box holding several kinds of item comes before one holding a single kind. */
    @Test
    void mixedComesBeforePure()
    {
        List<Box> boxes = List.of(
                new Box(key(Fill.PARTIAL, 0, false, 64), "pure"),
                new Box(key(Fill.PARTIAL, 0, true, 64), "mixed"));

        assertEquals(List.of("mixed", "pure"), names(boxes));
    }

    /** Priority 2 &gt; 5: colour decides before mixed/pure does. */
    @Test
    void colourBeatsMixedBeforePure()
    {
        List<Box> boxes = List.of(
                new Box(key(Fill.PARTIAL, 15, true, 64), "mixed-black"),
                new Box(key(Fill.PARTIAL, 0, false, 64), "pure-white"));

        assertEquals(List.of("pure-white", "mixed-black"), names(boxes));
    }

    /** Level 3: among boxes that are not full, the one holding least comes first. */
    @Test
    void leastContentsComesFirst()
    {
        List<Box> boxes = List.of(
                new Box(key(Fill.PARTIAL, 0, false, 300), "lots"),
                new Box(key(Fill.PARTIAL, 0, false, 1), "almost-empty"),
                new Box(key(Fill.PARTIAL, 0, false, 64), "some"));

        assertEquals(List.of("almost-empty", "some", "lots"), names(boxes));
    }

    /** The undyed box sorts before every dyed one at the same fill state and mixedness. */
    @Test
    void theUndyedBoxComesFirst()
    {
        List<Box> boxes = List.of(
                new Box(key(Fill.EMPTY, 0, false, 0), "empty-white"),
                new Box(key(Fill.EMPTY, ShulkerOrder.UNDYED_COLOR_RANK, false, 0), "empty-plain"),
                new Box(key(Fill.EMPTY, 15, false, 0), "empty-black"));

        assertEquals(List.of("empty-plain", "empty-white", "empty-black"), names(boxes));
    }

    /** An empty box holds nothing, so it can never be "mixed". */
    @Test
    void emptyBoxesAreNeverMixed()
    {
        assertTrue(key(Fill.EMPTY, 0, false, 0).mixed() == false);
    }
}
