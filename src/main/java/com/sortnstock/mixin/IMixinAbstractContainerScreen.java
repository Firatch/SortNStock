package com.sortnstock.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * SortNStock's own accessor for the slot under the mouse in a container screen.
 *
 * <p>This replaces an earlier dependency on MaLiLib's internal
 * {@code fi.dy.masa.malilib.mixin.screen.IMixinAbstractContainerScreen}. Depending on another
 * mod's internal mixin interface meant that if MaLiLib changed or did not apply it, sorting would
 * silently do nothing at all. Owning the accessor removes that failure mode.</p>
 */
@Mixin(AbstractContainerScreen.class)
public interface IMixinAbstractContainerScreen
{
    @Accessor("hoveredSlot")
    Slot sortnstock_getHoveredSlot();
}
