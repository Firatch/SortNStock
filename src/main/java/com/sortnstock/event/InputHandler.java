package com.sortnstock.event;

import java.util.List;

import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

import com.sortnstock.Reference;
import com.sortnstock.config.Configs;
import com.sortnstock.config.Hotkeys;

/**
 * Registers SortNStock's keybinds with MaLiLib.
 *
 * <p>MaLiLib calls {@link #addKeysToMap} whenever the key-to-keybind map is rebuilt, and
 * {@link #addHotkeys} once when this provider is registered so the bindings show up in the
 * combined hotkey list. Conflict detection is MaLiLib's.</p>
 *
 * <h2>Two kinds of hotkey</h2>
 *
 * <p>There are standalone hotkeys ({@link Hotkeys}) and per-option toggle hotkeys, which belong to a
 * {@code ConfigBooleanHotkeyed}. The toggle configs wire their own
 * {@code KeyCallbackToggleBooleanConfigWithMessage}, so the key would do the right thing — but only
 * if the keybind is in MaLiLib's key map. Registering the provider for {@link Hotkeys} alone left
 * those keys out of the map: the hotkey field was editable and pressing the key did nothing.</p>
 */
public class InputHandler implements IKeybindProvider
{
    private static final InputHandler INSTANCE = new InputHandler();

    /** Options whose on/off state can be toggled with a bound key. */
    private static final List<IHotkey> OPTION_TOGGLES = List.of(
            Configs.Restock.RESTOCK_ENABLED,
            Configs.Litematica.LITEMATICA_AUTO_RESTOCK,
            Configs.Debug.DEBUG_LOGGING,
            Configs.QuickShulker.QUICKSHULKER_RESTOCK_FROM_SHULKER);

    private InputHandler()
    {
    }

    public static InputHandler getInstance()
    {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager)
    {
        for (IHotkey hotkey : Hotkeys.HOTKEY_LIST)
        {
            manager.addKeybindToMap(hotkey.getKeybind());
        }

        for (IHotkey hotkey : OPTION_TOGGLES)
        {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager)
    {
        manager.addHotkeysForCategory(Reference.MOD_NAME,
                                      Reference.MOD_ID + ".hotkeys.category.generic",
                                      Hotkeys.HOTKEY_LIST);

        manager.addHotkeysForCategory(Reference.MOD_NAME,
                                      Reference.MOD_ID + ".hotkeys.category.options",
                                      OPTION_TOGGLES);
    }
}
