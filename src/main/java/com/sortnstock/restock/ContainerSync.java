package com.sortnstock.restock;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks the client's container-synchronisation sequence.
 *
 * <p>The client builds a menu when the server opens a screen, but the slot <b>contents</b> arrive
 * in a separate content packet afterwards. Clicking in between means planning against stale (often
 * empty) slots — the client then computes one move while the server applies another, which is how a
 * refill ends up with ghost items and a container that closes before the server agrees.</p>
 *
 * <p>So before clicking on a freshly opened container, wait until it has been synced at least once;
 * and before closing it, wait until the move has been confirmed by a sync. This class is the counter
 * those waits are based on. {@code MixinClientPacketListener} bumps it from the two packets that
 * carry container contents.</p>
 *
 * <p>Both waits are modelled on BetterShulkerHUDMod's {@code QuickShulkerExtractionController},
 * which uses the same technique.</p>
 */
public final class ContainerSync
{
    private static long version;
    private static final Map<Integer, Long> perContainer = new HashMap<>();

    private ContainerSync()
    {
    }

    /** Called from the packet handlers; records that this container's contents just arrived. */
    public static void onSync(int containerId)
    {
        perContainer.put(containerId, ++version);
    }

    /** The current global sequence, used as a baseline before an expected sync. */
    public static long version()
    {
        return version;
    }

    /** True when {@code containerId} has been synced since {@code baseline} was taken. */
    public static boolean hasSyncAfter(int containerId, long baseline)
    {
        Long latest = perContainer.get(containerId);
        return latest != null && latest > baseline;
    }

    /** Drops all history; called when leaving a world. */
    public static void clear()
    {
        version = 0;
        perContainer.clear();
    }
}
