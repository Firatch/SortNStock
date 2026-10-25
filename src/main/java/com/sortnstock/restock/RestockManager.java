package com.sortnstock.restock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.sortnstock.SortNStock;
import com.sortnstock.compat.quickshulker.QuickShulkerCompat;
import com.sortnstock.config.ConfigToggles;
import com.sortnstock.config.Configs;
import com.sortnstock.config.options.RestockTargetSlots;

/**
 * Automatic restocking: keeps the configured target slots stocked from the player's own items.
 *
 * <p>SortNStock never creates items. Every movement is an ordinary container click, so the
 * server validates each one exactly as if the player had clicked it.</p>
 */
public final class RestockManager
{
    
    private static int cooldownTicks;

    /**
     * A refill that could not be completed yet, usually because the item lives in a carried shulker
     * box that still has to be opened.
     *
     * <p>Opening a box is asynchronous: the request goes out, and the server answers with the box's
     * menu on a later tick, only after which its slots can be moved out of. The consumption trigger
     * fires exactly once, so without remembering the request here the box would open and the refill
     * would never happen — which is precisely what a user reported as "restocking from shulker boxes
     * does not work".</p>
     */
    @Nullable
    private static Item pendingItem;
    /** The hotbar slot the item was consumed from, 0-8, or -1. */
    private static int pendingOrigin = -1;
    private static int pendingTicksLeft;
    /** True when this mod opened a shulker box and therefore knows the box on screen is its doing. */
    private static boolean pendingOpenedBox;

    /** Global container-sync sequence when the open request was sent. */
    private static long openSyncBaseline;

    /** Container id of the box this mod opened, once its menu has appeared. */
    private static int openedMenuId = -1;

    /** Set once the refill is done: close the box, but only after the move is confirmed. */
    private static boolean closeAfterSync;
    private static long closeSyncBaseline;
    /**
     * False until a baseline has been taken <i>after</i> the move's clicks were dispatched.
     *
     * <p>Taking the baseline when the move was merely <i>queued</i> made the confirmation a false
     * positive: the click goes out on the next tick, so the box's initial content sync — or any
     * unrelated sync — already satisfied it. The box then closed before the server's slot updates
     * for the move arrived, those updates were dropped because the client had moved to another
     * menu, and the item stayed invisible until something forced a full refresh.</p>
     */
    private static boolean closeBaselineArmed;

    /**
     * Ticks remaining in which QuickShulker's box sounds are still silenced after closing.
     *
     * <p>The close sound is played when the <b>server</b> processes the close packet, which is after
     * the flag that hid the screen has already been cleared. A short tail keeps the sound silenced to
     * the end.</p>
     */
    private static int silenceTicksLeft;

    private static final int SILENCE_AFTER_CLOSE_TICKS = 6;

    /** How many times the open request may be re-issued for the same pending item. */
    private static final int MAX_OPEN_ATTEMPTS = 4;

    private static int pendingOpenAttempts;
    private static int closeGraceTicks;
    private static int closeMenuId = -1;

    /**
     * How long a local, unconfirmed result is accepted before closing anyway, in client ticks.
     *
     * <p>The same value BetterShulkerHUDMod uses as {@code LOCAL_CONFIRMATION_GRACE_TICKS}: if the
     * server never sends an explicit sync, the locally observed result is trusted after a few ticks
     * rather than leaving the box on screen forever.</p>
     */
    private static final int LOCAL_CONFIRMATION_GRACE_TICKS = 6;

    /** How long a pending refill is retried before it is given up, in client ticks (~2 s). */
    private static final int PENDING_TIMEOUT_TICKS = 40;

    /**
     * Cooldown after sending a shulker-box open request, in client ticks.
     *
     * <p>Deliberately much shorter than {@code restockCooldownTicks}: that setting is a user-facing
     * spacing between restocks, and reusing it here made every shulker refill wait 10 ticks (~half a
     * second) doing nothing before it was even allowed to look at the box it had just opened. This
     * value only needs to stop the open request being re-sent every tick.</p>
     */
    private static final int OPEN_REQUEST_COOLDOWN_TICKS = 2;

    /**
     * The trigger's state. Extracted into {@link RestockTrigger} so the rules — a genuine decrease,
     * no slot switch, and identifying a stack that was used up completely — can be unit tested.
     */
    private static final RestockTrigger<Item> TRIGGER = new RestockTrigger<>();

    private RestockManager()
    {
    }

    public static void onClientTick(Minecraft mc)
    {
        if (mc.player == null || mc.level == null)
        {
            return;
        }

        if (cooldownTicks > 0)
        {
            cooldownTicks--;
        }

        if (silenceTicksLeft > 0)
        {
            silenceTicksLeft--;
        }

        if (Configs.Restock.RESTOCK_ENABLED.getBooleanValue() == false)
        {
            if (RestockQueue.isActive())
            {
                RestockQueue.cancel();
            }

            return;
        }

        // Keep the queue draining regardless of whether a trigger fires this tick.
        RestockQueue.tick(mc);

        // A refill that had to wait for a shulker box to open is completed here, on the ticks after
        // the request, once the server has answered with the box's menu.
        tryPendingRefill(mc);

        // Closing a box waits for the server to confirm the move it carried.
        tickDeferredClose(mc);

        // The single, unconditional trigger: the main hand stack shrank. That covers both using
        // or consuming an item (eating, throwing, drinking) and placing a block, and it is the
        // only moment at which the player has demonstrably consumed something from the hand.
        // Periodic, pickup and container-open triggers were removed on purpose, and there is no
        // longer an option to disable this one — restockEnabled is the master switch.
        final ItemStack mainHand = mc.player.getMainHandItem();
        final int selectedSlot = mc.player.getInventory().getSelectedSlot();

        // A container screen being open rules out consumption entirely: items cannot be used with a
        // GUI open, so a shrinking main hand there means the player is *storing* the stack. Treating
        // that as consumption made restock pull the stack straight back out of the box it had just
        // been put into — the deposit undo bug.
        //
        // The tracked state still has to follow the count, or closing the screen would present the
        // whole difference as one consumption on the next tick.
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)
        {
            TRIGGER.update(selectedSlot,
                    mainHand.isEmpty() ? null : mainHand.getItem(),
                    mainHand.getCount());
            return;
        }

        final Item consumed = TRIGGER.observe(
                selectedSlot,
                mainHand.isEmpty() ? null : mainHand.getItem(),
                mainHand.getCount());

        if (consumed == null)
        {
            return;
        }

        SortNStock.debugLog("restock: main hand shrank (consumed {}), checking", consumed);

        // The player was placing from the selected hotbar slot, so that is where the refill should
        // go back. Resolved here, while it is still the slot the count came from.
        final int originSlot = SlotLookup.findMenuSlot(
                mc.player.containerMenu, mc.player.getInventory(), TRIGGER.originSlot());

        boolean queued = planAndQueue(mc, null, consumed, originSlot);

        if (queued == false)
        {
            // Nothing reachable held the item. It may be inside a carried shulker box; remember the
            // request so it is retried once any box that was asked to open has actually opened.
            //
            // The origin is remembered as a *hotbar index*, not a menu slot id: opening a shulker box
            // replaces the open menu, so a menu slot id from this moment points somewhere else by the
            // time the retry runs. That mismatch is why a refilled stack used to land in the first
            // hotbar slot instead of the one that had just been used.
            pendingItem = consumed;
            pendingOrigin = TRIGGER.originSlot();
            pendingTicksLeft = PENDING_TIMEOUT_TICKS;
        }
        else
        {
            finishPending(mc, true);
        }
    }

    /**
     * Retries a refill that could not be completed when its trigger fired.
     *
     * <p>Bounded by {@link #PENDING_TIMEOUT_TICKS} so a request that can never succeed — no box
     * holds the item, the box refused to open, QuickShulker is absent — does not retry forever.
     * Respects the cooldown and the queue so it cannot race the normal path.</p>
     */
    private static void tryPendingRefill(Minecraft mc)
    {
        if (pendingItem == null)
        {
            return;
        }

        if (--pendingTicksLeft <= 0)
        {
            SortNStock.debugLog("restock: giving up on the pending refill for {}", pendingItem);
            finishPending(mc, true);
            return;
        }

        if (cooldownTicks > 0 || RestockQueue.isActive())
        {
            return;
        }

        final net.minecraft.world.inventory.AbstractContainerMenu menu = mc.player.containerMenu;

        // A box this mod opened has to be *synced* before anything is clicked. The menu exists as
        // soon as the screen opens, but its slots stay empty until the server's content packet
        // arrives; planning a move in that window means planning against empty slots, and the
        // client then disagrees with the server about the result. That is the ghost-item bug.
        if (pendingOpenedBox && menu instanceof net.minecraft.world.inventory.ShulkerBoxMenu)
        {
            if (openedMenuId != menu.containerId)
            {
                openedMenuId = menu.containerId;
                SortNStock.debugLog("restock: shulker box menu {} opened, waiting for its contents",
                        openedMenuId);
            }

            if (ContainerSync.hasSyncAfter(menu.containerId, openSyncBaseline) == false)
            {
                return;
            }
        }

        // Resolved against the menu that is open *now*, which may be the shulker box's menu rather
        // than the one the item was consumed in.
        int originMenuSlot = pendingOrigin < 0
                ? -1
                : SlotLookup.findMenuSlot(mc.player.containerMenu, mc.player.getInventory(), pendingOrigin);

        if (planAndQueue(mc, null, pendingItem, originMenuSlot))
        {
            SortNStock.debugLog("restock: completed the pending refill for {}", pendingItem);
            finishPending(mc, true);

            // Dispatch now instead of waiting for the next tick. RestockQueue#tick re-checks the
            // menu and the cursor before clicking, so this is the same safe path, just without the
            // extra tick of latency — the player is mid-placement when this runs.
            RestockQueue.tick(mc);
        }
    }

    /**
     * Closes a box this mod opened, once the move it carried has been confirmed.
     *
     * <p>Closing immediately after queuing the move was the earlier bug: the click group is
     * dispatched on the *next* tick, so the menu was already gone and nothing moved. Closing before
     * the server has acknowledged the move is also what produced a screen that vanished while the
     * close sound arrived late. Waiting for a sync of the box's container — or for the local result
     * to hold for {@link #LOCAL_CONFIRMATION_GRACE_TICKS} ticks — keeps the client and server in
     * agreement.</p>
     */
    private static void tickDeferredClose(Minecraft mc)
    {
        if (closeAfterSync == false)
        {
            return;
        }

        // The clicks that perform the move are still queued; closing now would cancel them.
        if (RestockQueue.isActive())
        {
            return;
        }

        // The clicks have now been dispatched, so anything the server sends from here on is a
        // response to them. Only now is a baseline meaningful.
        if (closeBaselineArmed == false)
        {
            closeBaselineArmed = true;
            closeSyncBaseline = ContainerSync.version();
            return;
        }

        boolean serverConfirmed = closeMenuId >= 0 &&
                                  ContainerSync.hasSyncAfter(closeMenuId, closeSyncBaseline);

        if (serverConfirmed || ++closeGraceTicks >= LOCAL_CONFIRMATION_GRACE_TICKS)
        {
            closeAfterSync = false;
            closeBaselineArmed = false;
            closeMenuId = -1;

            SortNStock.debugLog(
                    "restock: closing the shulker box this mod opened (serverConfirmed={}, graceTicks={})",
                    serverConfirmed, closeGraceTicks);

            if (mc.player != null)
            {
                // LocalPlayer#closeContainer, NOT clientSideCloseContainer.
                //
                // The latter only switches the client's menu back to the inventory; the server is
                // never told, so it keeps the shulker box open and keeps addressing slot updates to
                // that box's container id. The client has already moved on, so vanilla discards
                // those updates: the moved items exist server-side but never reach the client's
                // inventory until something forces a full refresh — the ghost item. The close sound
                // also arrives late for the same reason.
                //
                // BetterShulkerHUDMod closes with Player#closeContainer for exactly this reason.
                //
                // The close sound is played when the SERVER processes this, i.e. after the flags that
                // hid the screen have already been cleared, so a tail of silence is needed to cover
                // it. Without that the open sound was silenced and the close sound still played.
                silenceTicksLeft = SILENCE_AFTER_CLOSE_TICKS;
                mc.player.closeContainer();
            }
        }
    }

    /**
     * Ends a pending refill, optionally arranging for the box it opened to be closed once the move
     * is confirmed.
     *
     * <p>The close is never done here: it is deferred to {@link #tickDeferredClose} so it cannot be
     * issued on top of clicks that have not been dispatched yet, nor before the server has agreed
     * with what the move did.</p>
     */
    private static void finishPending(Minecraft mc, boolean closeWhenConfirmed)
    {
        if (closeWhenConfirmed && pendingOpenedBox)
        {
            closeAfterSync = true;
            closeBaselineArmed = false;
            closeGraceTicks = 0;
            closeMenuId = openedMenuId;
        }

        pendingItem = null;
        pendingOrigin = -1;
        pendingTicksLeft = 0;
        pendingOpenedBox = false;
        pendingOpenAttempts = 0;
        openedMenuId = -1;
    }

    /**
     * True while a shulker box opened by this mod should not be shown on screen.
     *
     * <p>Read by {@code MixinMinecraftSetScreen}, which cancels {@code Minecraft#setScreen} for the
     * shulker box's screen. The menu still opens, so the move works; the player just keeps seeing the
     * world instead of a container GUI popping up mid-build. Boxes the player opens themselves are
     * unaffected.</p>
     */
    public static boolean shouldHideOpenedShulkerScreen()
    {
        return (pendingOpenedBox && pendingTicksLeft > 0) || closeAfterSync;
    }

    /** Whether QuickShulker's box sounds should be silenced right now. */
    public static boolean shouldSilenceShulkerSounds()
    {
        return shouldHideOpenedShulkerScreen() || silenceTicksLeft > 0;
    }

    /** Forces the cooldown to start; used by the Litematica integration after its own restock. */
    public static void applyCooldown(int ticks)
    {
        cooldownTicks = Math.max(cooldownTicks, ticks);
    }

    public static int getCooldownTicks()
    {
        return cooldownTicks;
    }

    /**
     * Plans and queues a restock pass.
     *
     * @param required when non-empty, only this exact item is considered (the Litematica path);
     *                 when null, every target slot whose contents are at or below its threshold
     *                 is considered
     * @return true if at least one click group was queued
     */
    public static boolean planAndQueue(Minecraft mc, ItemStack required)
    {
        return plan(mc, required, null, -1, false, 0, null);
    }

    /**
     * Plans and queues a restock pass, additionally told which item was just consumed and which
     * slot it came from.
     *
     * @param justConsumed the item that was in the main hand before it shrank. A stack that gets
     *                     used up completely leaves its slot empty, at which point the slot no
     *                     longer says what used to be in it, so the trigger has to carry the
     *                     identity through. May be null.
     * @param preferredDestination the menu slot the item was consumed from, or -1. An emptied slot
     *                     is refilled in place rather than in whatever empty hotbar slot happens
     *                     to come first.
     */
    public static boolean planAndQueue(Minecraft mc, ItemStack required, Item justConsumed,
                                       int preferredDestination)
    {
        return plan(mc, required, justConsumed, preferredDestination, false, 0, null);
    }

    /**
     * Synchronous restock used by the Litematica integration.
     *
     * <p>The clicks are dispatched immediately, inside the current tick and before Litematica's
     * pick block runs, so the very same right-click can use the restocked stack. This is what
     * makes the "restock → pick block → easy place" ordering work within one click, instead of
     * requiring a second right-click.</p>
     *
     * @param maxGroups hard cap on how many click groups may be sent in this one call
     * @param explicitTargets when non-empty, the exact destination slots to use instead of the
     *                        configured hotbar/offhand targets. The Litematica integration
     *                        passes its own pick-blockable hotbar slots here, so SortNStock never
     *                        keeps a second slot list of its own.
     * @return the number of click groups actually executed
     */
    public static int planAndExecuteNow(Minecraft mc, ItemStack required, int maxGroups,
                                        List<Integer> explicitTargets)
    {
        lastExecutedGroups = 0;
        plan(mc, required, null, -1, true, Math.max(1, maxGroups), explicitTargets);

        return lastExecutedGroups;
    }

    private static int lastExecutedGroups;

    private static boolean plan(Minecraft mc, ItemStack required, Item justConsumed, int preferredDestination,
                                boolean immediate, int maxImmediateGroups, List<Integer> explicitTargets)
    {
        final Player player = mc.player;

        if (player == null || mc.gameMode == null)
        {
            return false;
        }

        // The queued path respects the shared cooldown/throttle. The immediate path is driven by
        // the Litematica integration, which enforces its own, separate cooldown.
        if (immediate == false && (cooldownTicks > 0 || RestockQueue.isActive()))
        {
            return false;
        }

        final AbstractContainerMenu menu = player.containerMenu;

        // Never act while the player is carrying something on the cursor.
        if (menu.getCarried().isEmpty() == false)
        {
            return false;
        }

        // The Litematica path supplies its own destination slots (Litematica's
        // pickBlockableSlots); every other path uses the configured hotbar/offhand targets.
        final List<Integer> targets = (explicitTargets != null && explicitTargets.isEmpty() == false)
                ? explicitTargets
                : targetSlots(menu, player);

        if (targets.isEmpty())
        {
            return false;
        }

        final List<Item> wanted = new ArrayList<>();

        if (required != null && required.isEmpty() == false)
        {
            if (Thresholds.supportsCountRestock(required))
            {
                wanted.add(required.getItem());
            }
        }
        else
        {
            for (int menuSlotId : targets)
            {
                ItemStack stack = menu.slots.get(menuSlotId).getItem();

                if (Thresholds.isAtOrBelowThreshold(stack) && wanted.contains(stack.getItem()) == false)
                {
                    wanted.add(stack.getItem());
                }
            }

            // If the item that was just used up had emptied its slot, the scan above cannot see it
            // any more — an empty slot carries no item to compare against. Reconsider it by name,
            // applying the same threshold rule so this cannot become an unconditional refill.
            if (justConsumed != null &&
                justConsumed != Items.AIR &&
                wanted.contains(justConsumed) == false &&
                Thresholds.supportsCountRestock(justConsumed))
            {
                int have = countInSlots(menu, targets, justConsumed);

                if (have <= Thresholds.threshold())
                {
                    wanted.add(justConsumed);

                    SortNStock.debugLog("restock: '{}' was used up (have {}, threshold {}), refilling",
                            justConsumed, have, Thresholds.threshold());
                }
            }
        }

        if (wanted.isEmpty())
        {
            return false;
        }

        // Simulated per-item counts in the target slots, updated as groups are planned so a
        // single pass can pull from several source stacks.
        final Map<Item, Integer> simulated = new HashMap<>();

        for (Item item : wanted)
        {
            simulated.put(item, countInSlots(menu, targets, item));
        }

        final List<int[]> groups = new ArrayList<>();

        for (int sourceSlotId : sourceSlots(menu, player))
        {
            ItemStack source = menu.slots.get(sourceSlotId).getItem();

            if (source.isEmpty())
            {
                continue;
            }

            Integer have = simulated.get(source.getItem());

            if (have == null)
            {
                continue;
            }

            int need = Thresholds.targetFor(source.getItem()) - have;

            if (need <= 0)
            {
                continue;
            }

            int destination = findDestination(menu, targets, source.getItem(), preferredDestination);

            if (destination < 0)
            {
                // No target slot can take the stack: every pick-blockable slot is occupied by
                // something else. Rather than giving up, swap the held item with the stack inside the
                // open shulker box — the player ends up holding what they needed and the box keeps
                // what they were holding. Nothing is created or destroyed, and the container keeps
                // the same number of items.
                //
                // Only possible while a shulker box is open, and only for a slot that belongs to the
                // box; swapping against the player's own inventory would just shuffle the backpack.
                if (menu instanceof ShulkerBoxMenu)
                {
                    int handSlot = SlotLookup.findMenuSlot(
                            menu, player.getInventory(),
                            player.getInventory().getSelectedSlot());

                    boolean sourceIsBoxSlot = handSlot >= 0 &&
                            handSlot != sourceSlotId &&
                            menu.slots.get(sourceSlotId).container != player.getInventory();

                    if (sourceIsBoxSlot)
                    {
                        // pickup(box), pickup(hand), pickup(box): cursor carries the wanted stack,
                        // swaps it with the held item, then drops the displaced stack into the box.
                        groups.add(new int[] { sourceSlotId, handSlot, sourceSlotId });
                        simulated.put(source.getItem(), have + source.getCount());

                        SortNStock.debugLog(
                                "restock: inventory full, swapping held item for '{}' from the box",
                                source.getItem());
                    }
                }

                continue;
            }

            int[] group = null;
            Slot destinationSlot = menu.slots.get(destination);
            int cap = destinationSlot.getMaxStackSize(source);
            int space = destinationSlot.getItem().isEmpty()
                    ? cap
                    : Math.max(0, cap - destinationSlot.getItem().getCount());

            if (space <= 0)
            {
                continue;
            }

            int moved = Math.min(Math.min(source.getCount(), need), space);

            if (moved <= 0)
            {
                continue;
            }

            if (moved == source.getCount())
            {
                // The whole stack fits: pick it up and drop it into the destination.
                group = new int[] { sourceSlotId, destination };
            }
            else
            {
                // Partial move: the remainder is left on the cursor and must be put back.
                group = new int[] { sourceSlotId, destination, sourceSlotId };
            }

            groups.add(group);
            simulated.put(source.getItem(), have + moved);
        }

        if (groups.isEmpty())
        {
            // Nothing could be sourced from the containers that are already reachable. Ask
            // QuickShulker to open a carried box that holds one of the wanted items; the move then
            // happens on a later tick, once the box is open and its slots are part of the menu.
            //
            // Requires the *server* to accept the packet, not merely the mod to be installed.
            if (Configs.QuickShulker.QUICKSHULKER_RESTOCK_FROM_SHULKER.getBooleanValue() &&
                QuickShulkerCompat.canSend())
            {
                for (Item item : wanted)
                {
                    if (QuickShulkerCompat.requestOpenShulkerWithItem(mc, player, item))
                    {
                        // Establish the pending refill here rather than in the caller. Both callers
                        // need it: the consumption trigger and the Litematica hook. Without it the
                        // box opened and nobody completed the move or closed it.
                        //
                        // The timeout is only started for a NEW cycle. Resetting it on every request
                        // (including the retries) meant a box that never completed kept the pending
                        // state — and with it the GUI suppression — alive forever, so even opening a
                        // shulker box by hand showed nothing.
                        if (pendingItem == null || pendingItem.equals(item) == false)
                        {
                            pendingItem = item;
                            pendingOrigin = player.getInventory().getSelectedSlot();
                            pendingTicksLeft = PENDING_TIMEOUT_TICKS;
                            pendingOpenAttempts = 0;
                        }

                        // Stop hammering the server once the box clearly refuses to cooperate; the
                        // timeout above then expires and the state is released.
                        if (pendingOpenAttempts >= MAX_OPEN_ATTEMPTS)
                        {
                            SortNStock.debugLog(
                                    "restock: giving up on opening a box for '{}' after {} attempt(s)",
                                    item, pendingOpenAttempts);
                            return false;
                        }

                        pendingOpenAttempts++;
                        pendingOpenedBox = true;
                        openedMenuId = -1;
                        openSyncBaseline = ContainerSync.version();
                        applyCooldown(OPEN_REQUEST_COOLDOWN_TICKS);
                        return false;
                    }
                }
            }

            return false;
        }

        if (immediate)
        {
            int executed = 0;

            for (int[] group : groups)
            {
                if (executed >= maxImmediateGroups)
                {
                    break;
                }

                RestockQueue.executeGroupNow(mc, menu, group);
                executed++;
            }

            lastExecutedGroups = executed;
        }
        else
        {
            RestockQueue.start(menu, groups, Configs.Restock.RESTOCK_MAX_OPS_PER_TICK.getIntegerValue());
        }

        applyCooldown(Configs.Restock.RESTOCK_COOLDOWN_TICKS.getIntegerValue());

        SortNStock.debugLog("restock: planned {} group(s) for {} item(s) (immediate={})",
                groups.size(), wanted.size(), immediate);

        return true;
    }

    /** Target slots (hotbar and/or offhand) as menu slot ids. */
    public static List<Integer> targetSlots(AbstractContainerMenu menu, Player player)
    {
        RestockTargetSlots mode = (RestockTargetSlots) Configs.Restock.RESTOCK_TARGET_SLOTS.getOptionListValue();
        List<Integer> ids = new ArrayList<>();

        if (mode == RestockTargetSlots.HOTBAR || mode == RestockTargetSlots.BOTH)
        {
            ids.addAll(SlotLookup.hotbarSlots(menu, player));
        }

        if (mode == RestockTargetSlots.OFFHAND || mode == RestockTargetSlots.BOTH)
        {
            int offhand = SlotLookup.offhandSlot(menu, player);

            if (offhand >= 0)
            {
                ids.add(offhand);
            }
        }

        return ids;
    }

    /**
     * Source slots, in the fixed order the player's own inventory first, then the open container.
     *
     * <p>The order used to be configurable. It was removed: with the duplicate shulker tier gone
     * only two sources remain, and the useful order is "things I am carrying before things in the
     * chest I happen to have open" — offering the reverse was a setting nobody needed.</p>
     */
    public static List<Integer> sourceSlots(AbstractContainerMenu menu, Player player)
    {
        List<Integer> ids = new ArrayList<>();
        ids.addAll(SlotLookup.mainInventorySlots(menu, player));
        ids.addAll(SlotLookup.openContainerSlots(menu, player));

        return ids;
    }

    /**
     * Picks where a restocked stack should go.
     *
     * <p>Thin adapter over {@link DestinationRules}, which holds the actual decision so it can be
     * unit tested without a menu.</p>
     *
     * @param preferred the menu slot the player was working from, or -1
     */
    private static int findDestination(AbstractContainerMenu menu, List<Integer> targets, Item item,
                                       int preferred)
    {
        int[] slots = new int[targets.size()];

        for (int i = 0; i < slots.length; i++)
        {
            slots[i] = targets.get(i);
        }

        return DestinationRules.choose(
                slots,
                slot -> menu.slots.get(slot).getItem().isEmpty(),
                slot ->
                {
                    ItemStack stack = menu.slots.get(slot).getItem();

                    return stack.isEmpty() == false && stack.getItem() == item;
                },
                preferred);
    }

    private static int countInSlots(AbstractContainerMenu menu, List<Integer> slotIds, Item item)
    {
        int count = 0;

        for (int menuSlotId : slotIds)
        {
            ItemStack stack = menu.slots.get(menuSlotId).getItem();

            if (stack.isEmpty() == false && stack.getItem() == item)
            {
                count += stack.getCount();
            }
        }

        return count;
    }
}
