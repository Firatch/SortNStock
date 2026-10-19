package com.sortnstock.compat.litematica;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.fabricmc.loader.api.FabricLoader;

import com.sortnstock.SortNStock;

/**
 * Optional Litematica integration.
 *
 * <p>Every reference to a Litematica type lives inside {@link Holder}, which is only loaded
 * after the mod-loaded check, so running without Litematica cannot produce a
 * {@code NoClassDefFoundError}.</p>
 *
 * <p>This mod is intentionally a <i>consumer</i> of Litematica: it reads the schematic's
 * required item and the allowed pick-block slots, and nothing else. It never places blocks,
 * never builds and never modifies a schematic.</p>
 */
public final class LitematicaCompat
{
    private static final boolean AVAILABLE = FabricLoader.getInstance().isModLoaded("litematica");

    private LitematicaCompat()
    {
    }

    public static boolean isAvailable()
    {
        return AVAILABLE;
    }

    /**
     * The hotbar slots Litematica allows its pick block to use, as 0-based hotbar indices.
     *
     * <p>Litematica stores this as the comma-separated {@code pickBlockableSlots} config string
     * (1-based, e.g. {@code "1,2,3,4,5"}) and keeps its parsed form private, so SortNStock
     * re-parses the same string. That keeps Litematica's config the single source of truth
     * without SortNStock maintaining a second slot list.</p>
     */
    public static Set<Integer> getPickBlockableHotbarSlots()
    {
        if (AVAILABLE == false)
        {
            return Collections.emptySet();
        }

        try
        {
            return Holder.pickBlockableHotbarSlots();
        }
        catch (Throwable t)
        {
            SortNStock.debugLog("litematica: failed to read pickBlockableSlots: {}", t.toString());
            return Collections.emptySet();
        }
    }

    /** True when a schematic placement is currently selected. */
    public static boolean hasActivePlacement()
    {
        if (AVAILABLE == false)
        {
            return false;
        }

        try
        {
            return Holder.hasActivePlacement();
        }
        catch (Throwable t)
        {
            SortNStock.debugLog("litematica: failed to query the active placement: {}", t.toString());
            return false;
        }
    }

    /**
     * Resolves the item the schematic requires at the block the crosshair is pointing at.
     *
     * @return the required stack, or {@link ItemStack#EMPTY} when there is nothing resolvable
     */
    public static ItemStack getRequiredItemAtCrosshair(Minecraft mc)
    {
        if (AVAILABLE == false)
        {
            return ItemStack.EMPTY;
        }

        try
        {
            return Holder.requiredItemAtCrosshair(mc);
        }
        catch (Throwable t)
        {
            SortNStock.debugLog("litematica: failed to resolve the required item: {}", t.toString());
            return ItemStack.EMPTY;
        }
    }

    /** Isolated holder: the only place that touches Litematica's classes. */
    private static final class Holder
    {
        private static Set<Integer> pickBlockableHotbarSlots()
        {
            String raw = fi.dy.masa.litematica.config.Configs.Generic.PICK_BLOCKABLE_SLOTS.getStringValue();
            Set<Integer> slots = new LinkedHashSet<>();

            for (String part : raw.split(","))
            {
                try
                {
                    int hotbarIndex = Integer.parseInt(part.trim()) - 1;

                    if (net.minecraft.world.entity.player.Inventory.isHotbarSlot(hotbarIndex))
                    {
                        slots.add(hotbarIndex);
                    }
                }
                catch (NumberFormatException ignored)
                {
                    // Mirrors Litematica's own lenient parsing: invalid entries are skipped.
                }
            }

            return slots;
        }

        private static boolean hasActivePlacement()
        {
            return fi.dy.masa.litematica.data.DataManager.getSchematicPlacementManager()
                    .getSelectedSchematicPlacement() != null;
        }

        private static ItemStack requiredItemAtCrosshair(Minecraft mc)
        {
            Level schematicWorld = fi.dy.masa.litematica.world.SchematicWorldHandler.getSchematicWorld();

            if (schematicWorld == null || mc.level == null)
            {
                return ItemStack.EMPTY;
            }

            Entity entity = fi.dy.masa.malilib.util.EntityUtils.getCameraEntity();

            if (entity == null)
            {
                return ItemStack.EMPTY;
            }

            double reach = fi.dy.masa.litematica.util.EasyPlaceUtils.getValidBlockRange(mc);

            fi.dy.masa.litematica.util.RayTraceUtils.RayTraceWrapper wrapper =
                    fi.dy.masa.litematica.util.RayTraceUtils.getGenericTrace(mc.level, entity, reach, true, false, false);

            if (wrapper == null ||
                wrapper.getHitType() != fi.dy.masa.litematica.util.RayTraceUtils.RayTraceWrapper.HitType.SCHEMATIC_BLOCK)
            {
                return ItemStack.EMPTY;
            }

            BlockHitResult hit = wrapper.getBlockHitResult();

            if (hit == null)
            {
                return ItemStack.EMPTY;
            }

            BlockPos pos = hit.getBlockPos();
            BlockState state = schematicWorld.getBlockState(pos);

            // "No schematic block at this position" — the same check Litematica's own easy place
            // flow performs before doing anything with the position.
            if (state.is(net.minecraft.tags.BlockTags.AIR))
            {
                return ItemStack.EMPTY;
            }

            return fi.dy.masa.litematica.materials.MaterialCache.getInstance()
                    .getRequiredBuildItemForState(state, schematicWorld, pos);
        }
    }
}
