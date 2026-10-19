package com.sortnstock.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sortnstock.restock.RestockManager;

/**
 * Hides the shulker box screen that restocking opens for itself.
 *
 * <p>Restocking from a box has to open it — extracting items is server-authoritative and
 * QuickShulker's only mechanism is an open request. That would normally throw a container screen in
 * the player's face in the middle of building, which is exactly what this prevents: the incoming
 * screen is simply never installed.</p>
 *
 * <p>The <b>menu</b> is unaffected — it still becomes {@code ShulkerBoxMenu} server- and
 * client-side, so the clicks work — only the rendering is skipped. This is the same technique
 * BetterShulkerHUDMod uses ({@code MinecraftScreenMixin}).</p>
 *
 * <p>Boxes the player opens themselves are untouched: the guard is false unless this mod opened the
 * box and has not finished with it.</p>
 */
@Mixin(Minecraft.class)
public class MixinMinecraftSetScreen
{
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void sortnstock$hideTransientShulkerScreen(Screen screen, CallbackInfo ci)
    {
        if (screen instanceof ShulkerBoxScreen && RestockManager.shouldHideOpenedShulkerScreen())
        {
            ci.cancel();
        }
    }
}
