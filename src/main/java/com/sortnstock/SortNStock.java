package com.sortnstock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.ModInitializer;

import fi.dy.masa.malilib.event.InitializationHandler;

import com.sortnstock.config.ConfigToggles;
import com.sortnstock.config.Configs;

/**
 * SortNStock — client-side inventory sorting and automatic restocking.
 *
 * <p>Client-only. This mod never creates, duplicates or otherwise fabricates items;
 * it only moves items the player already owns, and every move is issued as an ordinary
 * container click so the server remains fully authoritative.</p>
 */
public class SortNStock implements ModInitializer
{
    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID);

    @Override
    public void onInitialize()
    {
        LOGGER.info("SortNStock {} initialising (client-side)", Reference.MOD_VERSION);

        // MaLiLib drives mod initialisation: config registration, config GUI and keybinds.
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
    }

    public static void debugLog(String message, Object... args)
    {
        if (Configs.Debug.DEBUG_LOGGING.getBooleanValue())
        {
            LOGGER.info("[SortNStock] " + message, args);
        }
    }
}
