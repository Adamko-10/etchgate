package com.cotc.etchgate;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

/**
 * Etch Gate: staff approval for the audio links players put on Etched discs and radios.
 * The gate itself is {@link Gate}; decisions are kept in {@link GateStore}.
 */
@Mod(EtchGate.MOD_ID)
public class EtchGate {
    public static final String MOD_ID = "etchgate";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Ops at this level skip the gate and can decide requests. */
    public static final int STAFF_PERMISSION_LEVEL = 2;
    public static final int MAX_PENDING_PER_PLAYER = 3;

    private static volatile MinecraftServer server;
    private static volatile GateStore store;

    public EtchGate(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.addListener(EtchGate::onServerStarted);
        NeoForge.EVENT_BUS.addListener(EtchGate::onServerStopping);
        NeoForge.EVENT_BUS.addListener(EtchGateCommands::register);
        NeoForge.EVENT_BUS.addListener(EtchGate::onPlayerLoggedOut);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        store = event.getServer().overworld().getDataStorage().computeIfAbsent(GateStore.FACTORY, GateStore.FILE_ID);
        LOGGER.info("[EtchGate] armed - {} approved, {} denied, {} awaiting review",
                store.approvedList().size(), store.deniedList().size(), store.pendingRequests().size());
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        server = null;
        store = null;
        Gate.clear();
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Gate.forget(event.getEntity().getUUID());
    }

    @Nullable
    public static MinecraftServer server() {
        return server;
    }

    @Nullable
    public static GateStore store() {
        return store;
    }

    /**
     * Staff: ops (permission level 2+) and, since 1.0.1, the owner of a singleplayer or LAN world, who isn't an op when
     * cheats are off (their own links would otherwise wait for staff that doesn't exist).
     */
    public static boolean isStaff(ServerPlayer player) {
        return player.hasPermissions(STAFF_PERMISSION_LEVEL) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    public static MutableComponent prefix() {
        return Component.literal("[EtchGate] ").withStyle(ChatFormatting.DARK_AQUA);
    }

    public static void tellPlayer(ServerPlayer player, Component body) {
        player.sendSystemMessage(prefix().append(body));
    }

    /** Tells every online op about a new request, with clickable Approve / Deny buttons. */
    public static void notifyStaff(GateStore.Request request) {
        MinecraftServer srv = server;
        if (srv == null) {
            return;
        }
        Component line = prefix()
                .append(Component.literal(request.playerName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" wants to use a music URL (#" + request.id() + ")").withStyle(ChatFormatting.GRAY));
        Component urlLine = Component.literal("  " + Urls.forDisplay(request.url())).withStyle(style -> style
                .withColor(ChatFormatting.YELLOW)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(Urls.forDisplay(request.url())))));
        Component buttons = Component.literal("  ")
                .append(button("[Approve]", ChatFormatting.GREEN, "/etchgate approve " + request.id(), "Let anyone use this link"))
                .append(Component.literal(" "))
                .append(button("[Deny]", ChatFormatting.RED, "/etchgate deny " + request.id(), "Block this link for everyone"));
        for (ServerPlayer staff : srv.getPlayerList().getPlayers()) {
            if (isStaff(staff)) {
                staff.sendSystemMessage(line);
                staff.sendSystemMessage(urlLine);
                staff.sendSystemMessage(buttons);
            }
        }
        LOGGER.info("[EtchGate] request #{} from {}: {}", request.id(), request.playerName(), Urls.forDisplay(request.url()));
    }

    private static Component button(String label, ChatFormatting color, String command, String tooltip) {
        return Component.literal(label).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(tooltip))));
    }
}
