package com.sortnstock.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.loader.api.FabricLoader;

import com.sortnstock.SortNStock;

/**
 * Applies the Litematica mixin only when Litematica is actually installed.
 *
 * <p>Without this, Fabric Loader would try to apply a mixin whose target class does not exist,
 * which is a hard crash on a vanilla-plus-SortNStock install. Litematica is a
 * {@code recommends}, not a {@code depends}, so this guard is required.</p>
 */
public class SortNStockLitematicaMixinPlugin implements IMixinConfigPlugin
{
    private static final boolean LITEMATICA_PRESENT = FabricLoader.getInstance().isModLoaded("litematica");

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
        if (LITEMATICA_PRESENT == false)
        {
            SortNStock.LOGGER.info("Litematica not present; skipping mixin {}", mixinClassName);
        }

        return LITEMATICA_PRESENT;
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
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {
    }
}
