package com.sortnstock.event;

import net.minecraft.client.Minecraft;

import fi.dy.masa.malilib.interfaces.IClientTickHandler;

import com.sortnstock.compat.litematica.LitematicaRestockHandler;
import com.sortnstock.restock.RestockManager;
import com.sortnstock.sort.SortQueue;

/**
 * Drives all per-tick work. Registered with MaLiLib's {@code TickHandler}.
 */
public class ClientTickHandler implements IClientTickHandler
{
    public static final ClientTickHandler INSTANCE = new ClientTickHandler();

    private ClientTickHandler()
    {
    }

    @Override
    public void onClientTick(Minecraft mc)
    {
        if (mc.player == null || mc.level == null)
        {
            return;
        }

        RestockManager.onClientTick(mc);
        LitematicaRestockHandler.onClientTick(mc);
        SortQueue.tick(mc);
    }
}
