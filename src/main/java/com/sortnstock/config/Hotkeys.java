package com.sortnstock.config;

import com.google.common.collect.ImmutableList;

import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;

import com.sortnstock.Reference;

/**
 * All hotkeys are declared here and registered with MaLiLib through
 * {@link com.sortnstock.event.InputHandler}.
 */
public class Hotkeys
{
    private static final String KEY = Reference.MOD_ID + ".config.hotkeys";

    /**
     * Sorting must work while a container screen is open — that is the entire point of the
     * feature. MaLiLib's {@link KeybindSettings#DEFAULT} uses {@code Context.INGAME}, which
     * requires the current screen to be {@code null} (see {@code KeybindMulti#updateIsPressed}),
     * so a DEFAULT keybind is silently dead in exactly the situation sorting needs it.
     * {@code Context.ANY} removes that gate.
     */
    private static final KeybindSettings SORT_SETTINGS =
            KeybindSettings.create(KeybindSettings.Context.ANY, KeyAction.PRESS, true, true, false, true);

    /** Opening the config GUI has to work both in-game and from other screens. */
    private static final KeybindSettings GUI_SETTINGS =
            KeybindSettings.create(KeybindSettings.Context.ANY, KeyAction.PRESS, true, true, false, true);

    // Middle mouse button ("BUTTON_3" in MaLiLib's naming). Note this is also vanilla's
    // "pick block" button, so the two will fire together; see the user test checklist.
    public static final ConfigHotkey SORT_INVENTORY    = new ConfigHotkey("sortHotkey",           "BUTTON_3", SORT_SETTINGS).apply(KEY);
    public static final ConfigHotkey OPEN_CONFIG_GUI   = new ConfigHotkey("openConfigGuiHotkey",  "T,K",      GUI_SETTINGS).apply(KEY);

    // Only the two hotkeys needed to get started exist. The restock module is switched on and off
    // from the config screen, and binding a key that is empty by default just adds a row the user
    // has to decide about; if someone wants one later it is a few lines to add back.

    public static final ImmutableList<ConfigHotkey> HOTKEY_LIST = ImmutableList.of(
            SORT_INVENTORY,
            OPEN_CONFIG_GUI
    );
}
