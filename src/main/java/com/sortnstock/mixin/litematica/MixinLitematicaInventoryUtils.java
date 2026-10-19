package com.sortnstock.mixin.litematica;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sortnstock.compat.litematica.LitematicaRestockHandler;

/**
 * Restocks the hotbar at the last possible moment before Litematica's pick block switches slots.
 *
 * <p><b>Target:</b> {@code fi.dy.masa.litematica.util.InventoryUtils#schematicWorldPickBlock(ItemStack, BlockPos, Level, Minecraft)}</p>
 *
 * <p>This is the ideal seam because it is the first point that is guaranteed to be both
 * <i>after</i> Litematica has decided a block should be placed (all of its early-outs — air,
 * already-correct, empty required stack, cache, "too fast", cannot-place — have already run) and
 * <i>before</i> the hotbar slot is switched. The required item arrives as a parameter, so no
 * reflection, {@code @Local} or cache recomputation is needed.</p>
 *
 * <p>It also covers every caller of that method, including the post-rewrite easy place path, the
 * pre-rewrite path in {@code WorldUtils}, and the plain schematic pick block.</p>
 *
 * <p>Ordering within one right-click is therefore: SortNStock restock clicks → Litematica pick
 * block swap → easy place use packet. SortNStock never cancels the click or places blocks.</p>
 */
@Mixin(value = fi.dy.masa.litematica.util.InventoryUtils.class, priority = 1500)
public class MixinLitematicaInventoryUtils
{
    @Inject(
            method = "schematicWorldPickBlock(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V",
            at = @At("HEAD")
    )
    private static void sortnstock_restockBeforePickBlock(ItemStack requiredStack, BlockPos pos,
                                                          Level schematicWorld, Minecraft mc,
                                                          CallbackInfo ci)
    {
        LitematicaRestockHandler.onBeforePickBlock(requiredStack, mc);
    }
}
