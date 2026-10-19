package com.sortnstock.mixin.quickshulker;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sortnstock.restock.RestockManager;

/**
 * Silences the shulker box's open/close sounds while restocking opens one for itself.
 *
 * <p>Restocking has to open a carried box, and QuickShulker plays the box's own sound when it does.
 * The screen is already hidden (see {@code MixinMinecraftSetScreen}); the sound has to go with it, or
 * the player hears an unexplained box opening mid-build.</p>
 *
 * <p>Every QuickShulker sound — open and close — funnels through this one static helper, which makes
 * it the precise choke point. Only sounds played during this mod's own transient window are
 * cancelled; boxes the player opens themselves keep their sounds.</p>
 *
 * <p>Gated behind {@code SortNStockQuickShulkerMixinPlugin}, so it is never applied when QuickShulker
 * is absent.</p>
 */
@Mixin(targets = "net.kyrptonaught.quickshulker.api.Util")
public class MixinQuickShulkerUtil
{
    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private static void sortnstock$silenceRestockSounds(Player player, SoundEvent sound, CallbackInfo ci)
    {
        if (RestockManager.shouldSilenceShulkerSounds())
        {
            ci.cancel();
        }
    }
}
