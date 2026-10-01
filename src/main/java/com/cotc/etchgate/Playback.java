package com.cotc.etchgate;

import gg.moonflower.etched.api.record.TrackData;
import gg.moonflower.etched.common.component.AlbumCoverComponent;
import gg.moonflower.etched.common.component.MusicTrackComponent;
import gg.moonflower.etched.core.registry.EtchedComponents;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Denying revokes: a disc (or an album cover holding one) with a denied link won't play. */
public final class Playback {
    /** An album cover inside an album cover is as deep as it goes. */
    private static final int MAX_DEPTH = 2;

    private Playback() {
    }

    public static boolean isDenied(ItemStack stack) {
        GateStore store = EtchGate.store();
        return store != null && isDenied(store, stack, 0);
    }

    private static boolean isDenied(GateStore store, ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty() || depth >= MAX_DEPTH) {
            return false;
        }
        MusicTrackComponent music = stack.get(EtchedComponents.MUSIC);
        if (music != null && anyDenied(store, music.tracks())) {
            return true;
        }
        AlbumCoverComponent cover = stack.get(EtchedComponents.ALBUM_COVER);
        if (cover != null) {
            for (ItemStack nested : cover.getItems()) {
                if (isDenied(store, nested, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean anyDenied(GateStore store, List<TrackData> tracks) {
        for (TrackData track : tracks) {
            String url = track.url();
            if (url != null && !url.isEmpty() && store.isDenied(Urls.key(url), Urls.host(url))) {
                return true;
            }
        }
        return false;
    }
}
