package com.sortnstock.config.options;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * Which slots the restock module is allowed to fill.
 */
public enum RestockTargetSlots implements IConfigOptionListEntry
{
    HOTBAR  ("hotbar",  "sortnstock.config.restock.target.hotbar"),
    OFFHAND ("offhand", "sortnstock.config.restock.target.offhand"),
    BOTH    ("both",    "sortnstock.config.restock.target.both");

    private final String configString;
    private final String translationKey;

    RestockTargetSlots(String configString, String translationKey)
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
    public RestockTargetSlots fromString(String value)
    {
        return fromStringStatic(value);
    }

    public static RestockTargetSlots fromStringStatic(String value)
    {
        for (RestockTargetSlots entry : values())
        {
            if (entry.configString.equalsIgnoreCase(value))
            {
                return entry;
            }
        }

        return RestockTargetSlots.HOTBAR;
    }
}
