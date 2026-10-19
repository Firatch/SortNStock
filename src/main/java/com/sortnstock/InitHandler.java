package com.sortnstock;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InitializationHandler;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;

import com.sortnstock.config.Configs;
import com.sortnstock.event.ClientTickHandler;
import com.sortnstock.event.InputHandler;
import com.sortnstock.event.KeyCallbacks;
import com.sortnstock.gui.GuiConfigs;

/**
 * The single place where SortNStock wires itself into MaLiLib.
 */
public class InitHandler implements IInitializationHandler
{
    @Override
    public void registerModHandlers()
    {
        // Config: MaLiLib owns the file, the GUI and hotkey conflict detection.
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new Configs());
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(Reference.MOD_ID, Reference.MOD_NAME, GuiConfigs::new));

        // Keybinds.
        InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());

        // Per-tick work.
        TickHandler.getInstance().registerClientTickHandler(ClientTickHandler.INSTANCE);

        // Hotkey actions.
        KeyCallbacks.init();

        SortNStock.LOGGER.info("SortNStock initialised; config registered with MaLiLib");
    }
}
