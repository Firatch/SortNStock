package com.sortnstock.config.options;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * A plain on/off pair, used instead of a boolean for options that simply enable something.
 *
 * <p>These were {@code ConfigBoolean}s until it became clear that MaLiLib renders every boolean as
 * a button reading "yes"/"no", and there is no hook for a mod to supply its own boolean widget
 * ({@code WidgetConfigOption} picks the widget from the closed {@code ConfigType} enum). An option
 * list is the one form whose displayed text a mod does control, and it still switches on a single
 * click.</p>
 */
public enum Toggle implements IConfigOptionListEntry
{
    ON  ("on",  "sortnstock.config.toggle.on"),
    OFF ("off", "sortnstock.config.toggle.off");

    private final String configString;
    private final String translationKey;

    Toggle(String configString, String translationKey)
    {
        this.configString = configString;
        this.translationKey = translationKey;
    }

    @Override
    public String getStringValue()
    {
        return this.configString;
    }

    @Override
    public String getDisplayName()
    {
        return StringUtils.translate(this.translationKey);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward)
    {
        int id = this.ordinal();

        if (forward)
        {
            if (++id >= values().length)
            {
                id = 0;
            }
        }
        else
        {
            if (--id < 0)
            {
                id = values().length - 1;
            }
        }

        return values()[id % values().length];
    }

    @Override
    public Toggle fromString(String value)
    {
        return fromStringStatic(value);
    }

    public static Toggle fromStringStatic(String value)
    {
        for (Toggle entry : values())
        {
            if (entry.configString.equalsIgnoreCase(value))
            {
                return entry;
            }
        }

        return Toggle.OFF;
    }

    public static Toggle of(boolean value)
    {
        return value ? ON : OFF;
    }
}
