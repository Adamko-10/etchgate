package com.cotc.etchgate;

import gg.moonflower.etched.api.record.TrackData;
import gg.moonflower.etched.common.menu.UrlMenu;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Decides whether a URL a player sent from an Etching Table or a Radio may go through. Called at the start of Etched's
 * handler for that packet (on the server thread); true means it's held back.
 */
public final class Gate {
    /** At most one reply line per player every 2 s (the client re-sends while you type). */
    private static final long REPLY_COOLDOWN_MS = 2000L;
    private static final Map<UUID, Long> lastReply = new ConcurrentHashMap<>();

    private Gate() {
    }

    public static boolean blocks(String rawUrl, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (!(serverPlayer.containerMenu instanceof UrlMenu)) {
            return false; // Etched ignores it too
        }
        if (rawUrl == null || rawUrl.isBlank()) {
            return false; // clearing the field
        }
        if (EtchGate.isStaff(serverPlayer)) {
            return false;
        }
        if (!Urls.isSane(rawUrl) || !TrackData.isValidURL(rawUrl)) {
            reply(serverPlayer, Component.literal("That is not a usable audio link.").withStyle(ChatFormatting.RED));
            return true;
        }
        GateStore store = EtchGate.store();
        if (store == null) {
            reply(serverPlayer, Component.literal("Music links are unavailable right now. Try again in a moment.").withStyle(ChatFormatting.RED));
            return true;
        }
        String key = Urls.key(rawUrl);
        String host = Urls.host(rawUrl);
        if (store.isApproved(key)) {
            return false;
        }
        if (store.isDenied(key, host)) {
            reply(serverPlayer, Component.literal("That link is not allowed on this server.").withStyle(ChatFormatting.RED));
            return true;
        }
        if (store.isPending(key)) {
            reply(serverPlayer, Component.literal("That link is already waiting for staff review. Hang tight.").withStyle(ChatFormatting.YELLOW));
            return true;
        }
        submit(store, key, serverPlayer);
        return true;
    }

    private static void submit(GateStore store, String key, ServerPlayer player) {
        if (store.pendingCountFor(player.getUUID()) >= EtchGate.MAX_PENDING_PER_PLAYER) {
            reply(player, Component.literal("You already have " + EtchGate.MAX_PENDING_PER_PLAYER
                    + " links waiting for review. Wait for those before sending more.").withStyle(ChatFormatting.RED));
            return;
        }
        GateStore.Request request = store.submit(key, player.getGameProfile().getName(), player.getUUID(), System.currentTimeMillis());
        EtchGate.tellPlayer(player, Component.literal("Sent to staff for approval. You'll be able to use this link once it's cleared.")
                .withStyle(ChatFormatting.YELLOW));
        EtchGate.notifyStaff(request);
    }

    private static void reply(ServerPlayer player, Component message) {
        long now = System.currentTimeMillis();
        Long previous = lastReply.get(player.getUUID());
        if (previous == null || now - previous >= REPLY_COOLDOWN_MS) {
            lastReply.put(player.getUUID(), now);
            EtchGate.tellPlayer(player, message);
        }
    }

    public static void forget(UUID playerId) {
        lastReply.remove(playerId);
    }

    public static void clear() {
        lastReply.clear();
    }
}
