package com.cotc.etchgate.mixin;

import com.cotc.etchgate.Playback;
import gg.moonflower.etched.api.record.PlayableRecord;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Discs carrying a denied link (copies and album covers included) stop counting as playable. */
@Mixin(value = PlayableRecord.class, remap = false)
public class PlayableRecordMixin {
    @Inject(method = "isPlayableRecord", at = @At("HEAD"), cancellable = true, remap = false)
    private static void etchgate$hideDeniedRecord(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (Playback.isDenied(stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "playEntityRecord", at = @At("HEAD"), cancellable = true, remap = false)
    private static void etchgate$blockDeniedRecord(Entity entity, ItemStack record, boolean restart, CallbackInfo ci) {
        if (Playback.isDenied(record)) {
            ci.cancel();
        }
    }
}
