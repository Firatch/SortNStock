package com.sortnstock.mixin.litematica;

import net.minecraft.world.InteractionResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.sortnstock.restock.RestockManager;

/**
 * Suppresses Litematica's easy place while a shulker box is being opened for a refill.
 *
 * <p>Opening a box takes a server round trip, so the items cannot be in the player's hand during the
 * click that requested it. Litematica then evaluates its own pick block: if the required item is not
 * in the hotbar it normally aborts, but the abort happens <i>after</i> its position and state checks,
 * and on the retries that follow the click the held (wrong) block could still be placed. The reported
 * symptom was "right-click triggers the box opening and swap, but a placement happened first and put
 * down the wrong block".</p>
 *
 * <p>Returning {@code FAIL} at the head of {@code handleEasyPlace} makes Litematica treat the click as
 * handled-and-refused for that moment, so nothing is placed. Once the refill completes the pending
 * state is cleared and easy place resumes normally on the next click.</p>
 *
 * <p><b>Target:</b> {@code fi.dy.masa.litematica.util.EasyPlaceUtils#handleEasyPlace()}</p>
 */
@Mixin(value = fi.dy.masa.litematica.util.EasyPlaceUtils.class, priority = 1500)
public class MixinLitematicaEasyPlace
{
    @Inject(
            method = "handleEasyPlace()Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sortnstock_skipWhileBoxOpens(CallbackInfoReturnable<InteractionResult> cir)
    {
        if (RestockManager.isWaitingForShulkerBox())
        {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
