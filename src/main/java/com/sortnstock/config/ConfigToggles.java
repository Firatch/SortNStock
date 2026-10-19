package com.sortnstock.config;

import fi.dy.masa.malilib.config.IConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigOptionList;

import com.sortnstock.config.options.Toggle;

/**
 * Helper for the on/off options that are stored as two-entry option lists.
 *
 * <p>They are option lists rather than booleans only because MaLiLib renders every boolean as a
 * button reading "yes"/"no" and offers no way for a mod to supply its own boolean widget; an option
 * list is the one form whose displayed text a mod controls. Keeping the conversion here means the
 * rest of the code still reads naturally, and means switching back to plain booleans would touch
 * only this file and the declarations.</p>
 *
 * <p>There is deliberately no sort-direction helper: the direction option was removed and every
 * sort is ascending. See {@code SortingHandler}.</p>
 */
public final class ConfigToggles
{
    private ConfigToggles()
    {
    }

    /** Declares an on/off option, stored as an option list. */
    public static ConfigOptionList toggle(String name, boolean defaultValue, String category)
    {
        return new ConfigOptionList(name, Toggle.of(defaultValue)).apply(category);
    }

    public static boolean isOn(IConfigOptionList option)
    {
        return option.getOptionListValue() == Toggle.ON;
    }
}
