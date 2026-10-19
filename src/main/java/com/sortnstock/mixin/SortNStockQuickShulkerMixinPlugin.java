package com.sortnstock.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Applies the QuickShulker mixins only when QuickShulker is installed.
 *
 * <p>Same pattern as {@link SortNStockLitematicaMixinPlugin}: the config is listed unconditionally in
 * {@code fabric.mod.json}, and this plugin decides at load time whether the target classes exist. Mixin
 * has no built-in "optional mod" condition, so without this an absent QuickShulker would fail the
 * whole mod.</p>
 */
public class SortNStockQuickShulkerMixinPlugin implements IMixinConfigPlugin
{
    private static final String REQUIRED_MOD = "quickshulker";

    @Override
    public void onLoad(String mixinPackage)
    {
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        boolean present = FabricLoader.getInstance().isModLoaded(REQUIRED_MOD);

        if (present == false)
        {
            return false;
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {
    }

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                         IMixinInfo mixinInfo)
    {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName,
                          IMixinInfo mixinInfo)
    {
    }
}
