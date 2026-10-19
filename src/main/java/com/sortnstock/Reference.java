package com.sortnstock;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Static mod metadata.
 *
 * <p>The mod id must match the {@code id} field in {@code fabric.mod.json}, and the
 * MaLiLib config file name is derived from it.</p>
 */
public final class Reference
{
    public static final String MOD_ID = "sortnstock";
    public static final String MOD_NAME = "SortNStock";

    /** Resolved from the loaded mod metadata so it can never drift from fabric.mod.json. */
    public static final String MOD_VERSION = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("dev");

    /** Litematica config we reuse for "which hotbar slots may be used" — see LitematicaCompat. */
    public static final String LITEMATICA_MOD_ID = "litematica";
    public static final String QUICKSHULKER_MOD_ID = "quickshulker";
    public static final String MALILIB_MOD_ID = "malilib";

    private Reference()
    {
    }
}
