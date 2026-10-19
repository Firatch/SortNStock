package com.sortnstock.config.options;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * Primary sort key for the inventory sorting module.
 */
public enum SortKey implements IConfigOptionListEntry
{
    REGISTRY_NAME ("registryName", "sortnstock.config.sort.key.registry_name"),
    DISPLAY_NAME  ("displayName",  "sortnstock.config.sort.key.display_name"),
    COUNT         ("count",        "sortnstock.config.sort.key.count"),
    DURABILITY    ("durability",   "sortnstock.config.sort.key.durability"),
    NBT           ("nbt",          "sortnstock.config.sort.key.nbt"),
    TAG           ("tag",          "sortnstock.config.sort.key.tag");

    private final String configString;
    private final String translationKey;

    SortKey(String configString, String translationKey)
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
    public SortKey fromString(String value)
    {
        return fromStringStatic(value);
    }

    public static SortKey fromStringStatic(String value)
    {
        for (SortKey key : values())
        {
            if (key.configString.equalsIgnoreCase(value))
            {
                return key;
            }
        }

        return SortKey.REGISTRY_NAME;
    }
}
