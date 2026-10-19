package com.sortnstock.sort;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import com.sortnstock.SortNStock;

/**
 * Plays the vanilla GUI button click when a sort actually starts, so the hotkey gives audible
 * feedback instead of appearing to do nothing.
 */
public final class SortFeedback
{
    private SortFeedback()
    {
    }

    /**
     * @param pitch 1.0F for the plain button click; a slightly higher pitch distinguishes the
     *              "nothing to do" case without introducing a custom sound resource.
     */
    public static void playClick(float pitch)
    {
        try
        {
            Minecraft mc = Minecraft.getInstance();

            if (mc.getSoundManager() != null)
            {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
            }
        }
        catch (Throwable t)
        {
            // Feedback must never be able to break the actual feature.
            SortNStock.debugLog("sort: could not play the click sound: {}", t.toString());
        }
    }

    public static void playSortStarted()
    {
        playClick(1.0F);
    }

    public static void playNothingToDo()
    {
        playClick(0.7F);
    }
}
