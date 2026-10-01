package com.cotc.etchgate.mixin;

import com.cotc.etchgate.Gate;
import gg.moonflower.etched.common.network.play.SetUrlPacket;
import gg.moonflower.etched.common.network.play.handler.EtchedServerPlayPacketHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The one packet both the Etching Table and the Radio use to send a URL to the server. */
@Mixin(value = EtchedServerPlayPacketHandler.class, remap = false)
public class EtchedServerPlayPacketHandlerMixin {
    @Inject(method = "handleSetUrl", at = @At("HEAD"), cancellable = true, remap = false)
    private static void etchgate$requireApproval(SetUrlPacket pkt, IPayloadContext ctx, CallbackInfo ci) {
        if (Gate.blocks(pkt.url(), ctx.player())) {
            ci.cancel();
        }
    }
}
