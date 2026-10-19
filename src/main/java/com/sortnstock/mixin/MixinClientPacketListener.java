package com.sortnstock.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.sortnstock.restock.ContainerSync;

/**
 * Feeds {@link ContainerSync} from the two packets that carry container contents.
 *
 * <p>Restocking needs to know when a container's slots have actually arrived from the server: a
 * freshly opened menu exists before its contents do, and clicking in that window plans against
 * stale slots. This is the hook that makes that wait possible.</p>
 */
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener
{
    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void sortnstock$onContainerContent(ClientboundContainerSetContentPacket packet,
                                               CallbackInfo ci)
    {
        ContainerSync.onSync(packet.containerId());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void sortnstock$onContainerSlot(ClientboundContainerSetSlotPacket packet,
                                            CallbackInfo ci)
    {
        ContainerSync.onSync(packet.getContainerId());
    }
}
