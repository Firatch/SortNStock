package com.sortnstock.event;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;

import com.sortnstock.SortNStock;
import com.sortnstock.config.Hotkeys;
import com.sortnstock.gui.GuiConfigs;
import com.sortnstock.sort.SortingHandler;

/**
 * Binds each MaLiLib hotkey to its action.
 *
 * <p>Only two hotkeys exist: sorting and opening the config screen. Everything else is a config
 * option with a sensible default rather than something worth claiming a key for.</p>
 */
public class KeyCallbacks
{
    public static void init()
    {
        Hotkeys.SORT_INVENTORY.getKeybind().setCallback(new KeyCallbackAction("sortInventory")
        {
            @Override
            protected boolean run()
            {
                return SortingHandler.sortHoveredContainer();
            }
        });

        Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback(new KeyCallbackAction("openConfigGui")
        {
            @Override
            protected boolean run()
            {
                GuiBase.openGui(new GuiConfigs());
                return true;
            }
        });
    }

    /**
     * Convenience base class so each action hotkey only implements {@link #run()}.
     */
    private abstract static class KeyCallbackAction implements IHotkeyCallback
    {
        private final String name;

        private KeyCallbackAction(String name)
        {
            this.name = name;
        }

        protected abstract boolean run();

        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key)
        {
            if (action != KeyAction.PRESS)
            {
                return false;
            }

            boolean handled = this.run();
            SortNStock.debugLog("hotkey '{}' handled={}", this.name, handled);

            return handled;
        }
    }
}
