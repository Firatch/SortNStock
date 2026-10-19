package com.sortnstock.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IConfigGuiAllTab;
import fi.dy.masa.malilib.util.StringUtils;

import com.sortnstock.Reference;
import com.sortnstock.config.Configs;
import com.sortnstock.config.Hotkeys;

/**
 * SortNStock config screen, rendered entirely by MaLiLib's {@link GuiConfigsBase}.
 * It is registered with {@code Registry.CONFIG_SCREEN} in {@code InitHandler}.
 */
public class GuiConfigs extends GuiConfigsBase implements IConfigGuiAllTab
{
    private static ConfigGuiTab currentTab = ConfigGuiTab.ALL;

    public GuiConfigs()
    {
        super(10, 50, Reference.MOD_ID, null, "sortnstock.gui.title.configs", Reference.MOD_VERSION);
    }

    @Override
    public void initGui()
    {
        super.initGui();
        this.clearOptions();

        int x = 10;
        final int y = 26;

        for (ConfigGuiTab tab : ConfigGuiTab.values())
        {
            if (tab == ConfigGuiTab.ALL && this.useAllTab() == false)
            {
                continue;
            }

            x += this.createButton(x, y, -1, tab);
        }
    }

    private int createButton(int x, int y, int width, ConfigGuiTab tab)
    {
        ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());
        button.setEnabled(currentTab != tab);
        this.addButton(button, new ButtonListener(tab, this));

        return button.getWidth() + 2;
    }

    @Override
    protected int getConfigWidth()
    {
        return 200;
    }

    @Override
    protected boolean useKeybindSearch()
    {
        return currentTab == ConfigGuiTab.ALL || currentTab == ConfigGuiTab.HOTKEYS;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs()
    {
        // The ALL tab has to be handled explicitly. MaLiLib's base class only calls
        // getAllConfigs() from its own list-building path, so falling through to an empty default
        // here is what previously left the "All" tab blank.
        if (currentTab == ConfigGuiTab.ALL)
        {
            return this.getAllConfigs();
        }

        List<? extends IConfigBase> configs = switch (currentTab)
        {
            case SORTING     -> Configs.Sorting.OPTIONS;
            case RESTOCK     -> Configs.Restock.OPTIONS;
            case QUICKSHULKER -> Configs.QuickShulker.OPTIONS;
            case LITEMATICA  -> Configs.Litematica.OPTIONS;
            case DEBUG       -> Configs.Debug.OPTIONS;
            case HOTKEYS     -> Hotkeys.HOTKEY_LIST;
            case ALL         -> Configs.Sorting.OPTIONS;
        };

        return ConfigOptionWrapper.createFor(configs);
    }

    @Override
    public boolean useAllTab()
    {
        return true;
    }

    @Override
    public List<ConfigOptionWrapper> getAllConfigs()
    {
        List<ConfigOptionWrapper> configs = new ArrayList<>();

        configs.addAll(ConfigOptionWrapper.createFor(Configs.Sorting.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Configs.Restock.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Configs.QuickShulker.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Configs.Litematica.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Configs.Debug.OPTIONS));
        configs.addAll(ConfigOptionWrapper.createFor(Hotkeys.HOTKEY_LIST));

        return configs;
    }

    private record ButtonListener(ConfigGuiTab tab, GuiConfigs parent) implements IButtonActionListener
    {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton)
        {
            currentTab = this.tab;
            this.parent.reCreateListWidget();
            Objects.requireNonNull(this.parent.getListWidget()).resetScrollbarPosition();
            this.parent.initGui();
        }
    }

    public enum ConfigGuiTab
    {
        ALL         (IConfigGuiAllTab.getTranslationKey()),
        SORTING     ("sortnstock.gui.button.config_gui.sorting"),
        RESTOCK     ("sortnstock.gui.button.config_gui.restock"),
        QUICKSHULKER("sortnstock.gui.button.config_gui.quickshulker"),
        LITEMATICA  ("sortnstock.gui.button.config_gui.litematica"),
        DEBUG       ("sortnstock.gui.button.config_gui.debug"),
        HOTKEYS     ("sortnstock.gui.button.config_gui.hotkeys");

        private final String translationKey;

        ConfigGuiTab(String translationKey)
        {
            this.translationKey = translationKey;
        }

        public String getDisplayName()
        {
            return StringUtils.translate(this.translationKey);
        }
    }
}
