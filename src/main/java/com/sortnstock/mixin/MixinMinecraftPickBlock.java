package com.sortnstock.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sortnstock.restock.PickBlockRestockHandler;

/**
 * Hooks vanilla's own pick block so it can reach into a shulker box.
 *
 * <p><b>Target:</b> {@code Minecraft#pickBlockOrEntity()}, the private method the pick-block key runs
 * (26.1.2). Injecting at the head means {@code hitResult} is already the frame's ray trace, so the
 * block under the crosshair can be resolved without repeating the trace.</p>
 *
 * <p>Vanilla is left to do the actual selection: this only asks for the item to be brought out of a
 * box when the player does not carry it. Nothing is cancelled or replaced, so a player who already has
 * the block sees exactly vanilla behaviour.</p>
 */
@Mixin(Minecraft.class)
public class MixinMinecraftPickBlock
{
    @Inject(method = "pickBlockOrEntity()V", at = @At("HEAD"))
    private void sortnstock$restockFromShulker(CallbackInfo ci)
    {
        PickBlockRestockHandler.onPickBlock((Minecraft) (Object) this);
    }
}
