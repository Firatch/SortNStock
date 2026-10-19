package com.sortnstock.restock;

/**
 * The restock trigger: decides on each client tick whether something was consumed from the main
 * hand, and if so <i>what</i>.
 *
 * <p>Generic over the item type so the decision can be unit tested without the game — production
 * instantiates it with {@code Item}, a test with a {@code String}. What it actually encodes is three
 * pieces of state and the rules connecting them, none of which need Minecraft:</p>
 *
 * <ul>
 *   <li>the count must <b>decrease</b>, which is what "used or placed" looks like from outside;</li>
 *   <li>the selected hotbar slot must <b>not</b> have changed, otherwise moving from a stack of 64
 *       to a stack of 1 in another slot looks exactly like consuming 63 items;</li>
 *   <li>the consumed item is read <b>before</b> the state is updated, because a stack used up
 *       completely leaves the hand empty and the empty hand no longer says what was in it.</li>
 * </ul>
 *
 * <p>That last rule is the one that was missing: with the minimum count set to 0, the only moment
 * the rule fires is the moment the slot empties, so the identity was always lost and nothing was
 * ever refilled.</p>
 *
 * @param <T> the item type; {@code null} means "the hand is empty"
 */
final class RestockTrigger<T>
{
    private int lastCount = -1;
    private T lastItem;
    private int lastSlot = -1;

    /**
     * Feeds one tick in and returns what it implies.
     *
     * @param selectedSlot the currently selected hotbar slot
     * @param item         what the main hand holds, or null when it is empty
     * @param count        the size of the main hand stack, 0 when empty
     * @return the consumed item, or null when no restock should be considered
     */
    T observe(int selectedSlot, T item, int count)
    {
        final boolean switchedSlot = this.lastSlot >= 0 && selectedSlot != this.lastSlot;

        // Read before the state is overwritten: see the class comment.
        final T consumed = (item != null) ? item : this.lastItem;

        final boolean consumedSomething = this.lastCount >= 0 &&
                count < this.lastCount &&
                switchedSlot == false;

        this.lastCount = count;
        this.lastItem = item;
        this.lastSlot = selectedSlot;

        return consumedSomething ? consumed : null;
    }

    /**
     * Updates the tracked state <b>without</b> reporting a consumption.
     *
     * <p>Needed while a container screen is open. Items cannot be used with a container open, so a
     * shrinking hand there means the player is <i>storing</i> items — and treating that as
     * consumption made restock pull the stack straight back out of the box it had just been put
     * into. Simply skipping {@link #observe} would not do: the state would go stale and the next
     * tick after closing the screen would read the whole difference as a single consumption.</p>
     */
    void update(int selectedSlot, T item, int count)
    {
        this.lastCount = count;
        this.lastItem = item;
        this.lastSlot = selectedSlot;
    }

    /**
     * The hotbar slot the main hand was in on the most recent tick, which is where a refill belongs.
     * Only meaningful straight after {@link #observe} reported a consumption.
     */
    int originSlot()
    {
        return this.lastSlot;
    }
}
