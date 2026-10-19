package com.sortnstock.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import com.sortnstock.gui.GuiConfigs;

/**
 * Supplies the Mod Menu config button for SortNStock.
 *
 * <p>Mod Menu only knows how to find config screens for Cloth Config's AutoConfig and for mods that
 * register this entry point — it has no idea about MaLiLib. MaLiLib's own {@code ModMenuImpl} gives
 * a screen for MaLiLib itself (which contains a mod list), but it does not bridge other mods'
 * screens, so every MaLiLib-based mod has to provide this small class of its own. Litematica ships
 * an identical one.</p>
 *
 * <p>This class is referenced only from the {@code modmenu} entry point in {@code fabric.mod.json}.
 * Mod Menu is what asks Fabric Loader for that entry point, so when Mod Menu is not installed the
 * class is never loaded and no dependency is needed at runtime — which is why Mod Menu is a
 * {@code compileOnly} dependency.</p>
 */
public class ModMenuImpl implements ModMenuApi
{
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory()
    {
        return parent ->
        {
            GuiConfigs gui = new GuiConfigs();
            gui.setParent(parent);

            return gui;
        };
    }
}
