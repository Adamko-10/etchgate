package com.cotc.etchgate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /etchgate ...}, all for staff: ops (permission level 2), the console, and a singleplayer/LAN world's owner. */
public final class EtchGateCommands {
    private EtchGateCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static boolean isStaff(CommandSourceStack src) {
        return src.hasPermission(EtchGate.STAFF_PERMISSION_LEVEL) || src.getPlayer() != null && EtchGate.isStaff(src.getPlayer());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("etchgate")
                .requires(EtchGateCommands::isStaff)
                .then(Commands.literal("list").executes(EtchGateCommands::list))
                .then(Commands.literal("approved").executes(ctx -> showDecisions(ctx, true)))
                .then(Commands.literal("denied").executes(ctx -> showDecisions(ctx, false)))
                .then(Commands.literal("approve").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> decideById(ctx, true))))
                .then(Commands.literal("deny").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> decideById(ctx, false))))
                .then(Commands.literal("allow").then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> decideByUrl(ctx, true))))
                .then(Commands.literal("block").then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> decideByUrl(ctx, false))))
                .then(Commands.literal("blockhost").then(Commands.argument("host", StringArgumentType.greedyString())
                        .executes(ctx -> decideHost(ctx, false))))
                .then(Commands.literal("unblockhost").then(Commands.argument("host", StringArgumentType.greedyString())
                        .executes(ctx -> decideHost(ctx, true))))
                .then(Commands.literal("hosts").executes(EtchGateCommands::listHosts))
                .then(Commands.literal("forget").then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(EtchGateCommands::forget))));
    }

    private static GateStore store(CommandContext<CommandSourceStack> ctx) {
        GateStore store = EtchGate.store();
        if (store == null) {
            ctx.getSource().sendFailure(Component.literal("EtchGate is not loaded yet."));
        }
        return store;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        GateStore store = store(ctx);
        if (store == null) {
            return 0;
        }
        List<GateStore.Request> pending = store.pendingRequests();
        if (pending.isEmpty()) {
            ctx.getSource().sendSuccess(() -> EtchGate.prefix().append(Component.literal("Nothing waiting for review.").withStyle(ChatFormatting.GRAY)), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> EtchGate.prefix().append(Component.literal(pending.size() + " waiting for review:").withStyle(ChatFormatting.GRAY)), false);
        for (GateStore.Request r : pending) {
            Component row = Component.literal(" #" + r.id() + " ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(r.playerName() + " ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(Urls.forDisplay(r.url())).withStyle(style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(Urls.forDisplay(r.url()))))))
                    .append(Component.literal(" "))
                    .append(action("[Approve]", ChatFormatting.GREEN, "/etchgate approve " + r.id()))
                    .append(Component.literal(" "))
                    .append(action("[Deny]", ChatFormatting.RED, "/etchgate deny " + r.id()));
            ctx.getSource().sendSuccess(() -> row, false);
        }
        return pending.size();
    }

    private static Component action(String label, ChatFormatting color, String command) {
        return Component.literal(label).withStyle(style -> style.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private static int showDecisions(CommandContext<CommandSourceStack> ctx, boolean approvedList) {
        GateStore store = store(ctx);
        if (store == null) {
            return 0;
        }
        List<GateStore.Decision> decisions = approvedList ? store.approvedList() : store.deniedList();
        String label = approvedList ? "approved" : "denied";
        if (decisions.isEmpty()) {
            ctx.getSource().sendSuccess(() -> EtchGate.prefix().append(Component.literal("No " + label + " links yet.").withStyle(ChatFormatting.GRAY)), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> EtchGate.prefix().append(Component.literal(decisions.size() + " " + label + ":").withStyle(ChatFormatting.GRAY)), false);
        for (GateStore.Decision d : decisions) {
            Component row = Component.literal("  " + Urls.forDisplay(d.url())).withStyle(style -> style
                    .withColor(approvedList ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(Urls.forDisplay(d.url()) + "\nby " + d.decidedBy()))));
            ctx.getSource().sendSuccess(() -> row, false);
        }
        return decisions.size();
    }

    private static int decideById(CommandContext<CommandSourceStack> ctx, boolean approve) {
        GateStore store = store(ctx);
        if (store == null) {
            return 0;
        }
        int id = IntegerArgumentType.getInteger(ctx, "id");
        GateStore.Request request = store.request(id);
        if (request == null) {
            ctx.getSource().sendFailure(Component.literal("No request #" + id + " is waiting. It may already have been decided - /etchgate list"));
            return 0;
        }
        apply(ctx, store, request.url(), approve);
        notifyRequester(request, approve);
        return 1;
    }

    private static int decideByUrl(CommandContext<CommandSourceStack> ctx, boolean approve) {
        GateStore store = store(ctx);
        if (store == null) {
            return 0;
        }
        String key = Urls.key(StringArgumentType.getString(ctx, "url"));
        if (key.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Give a link."));
            return 0;
        }
        List<GateStore.Request> affected = new ArrayList<>();
        for (GateStore.Request r : store.pendingRequests()) {
            if (r.url().equals(key)) {
                affected.add(r);
            }
        }
        apply(ctx, store, key, approve);
        for (GateStore.Request r : affected) {
            notifyRequester(r, approve);
        }
        return 1;
    }

    private static int decideHost(CommandContext<CommandSourceStack> ctx, boolean allow) {
        GateStore store = store(ctx);
        if (store == null) {
            return 0;
        }
        String raw = StringArgumentType.getString(ctx, "host").trim();
        String host = raw.contains("://") ? Urls.host(raw) : raw.toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Give a host, like example.com"));
            return 0;
        }
        if (allow) {
            if (!store.allowHost(host)) {
                ctx.getSource().sendFailure(Component.literal(host + " is not on the blocked-host list."));
                return 0;
            }
        } else {
            store.denyHost(host);
        }
        ctx.getSource().sendSuccess(() -> EtchGate.prefix()
                .append(Component.literal(allow ? "Unblocked host " : "Blocked host ").withStyle(allow ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(host).withStyle(ChatFormatting.WHITE)), true);
        return 1;
    }

    private static int listHosts(CommandContext<CommandSourceStack> ctx) {
        GateStore store = store(ctx);
        if (store == null) {
            return 0;
        }
        List<String> hosts = store.deniedHosts();
        if (hosts.isEmpty()) {
            ctx.getSource().sendSuccess(() -> EtchGate.prefix().append(Component.literal("No blocked hosts.").withStyle(ChatFormatting.GRAY)), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> EtchGate.prefix().append(Component.literal(hosts.size() + " blocked hosts:").withStyle(ChatFormatting.GRAY)), false);
        for (String h : hosts) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + h).withStyle(ChatFormatting.RED), false);
        }
        return hosts.size();
    }

    private static void apply(CommandContext<CommandSourceStack> ctx, GateStore store, String url, boolean approve) {
        String by = ctx.getSource().getTextName();
        if (approve) {
            store.approve(url, by, System.currentTimeMillis());
        } else {
            store.deny(url, by, System.currentTimeMillis());
        }
        ctx.getSource().sendSuccess(() -> EtchGate.prefix()
                .append(Component.literal(approve ? "Approved " : "Denied ").withStyle(approve ? ChatFormatting.GREEN : ChatFormatting.RED))
                .append(Component.literal(Urls.forDisplay(url)).withStyle(ChatFormatting.GRAY)), true);
    }

    private static void notifyRequester(GateStore.Request request, boolean approve) {
        if (EtchGate.server() == null) {
            return;
        }
        ServerPlayer player = EtchGate.server().getPlayerList().getPlayer(request.playerId());
        if (player != null) {
            // 1.0.1: worded for the Radio too, not only the Etching Table
            EtchGate.tellPlayer(player, approve
                    ? Component.literal("Your music link was approved. Enter it again to use it.").withStyle(ChatFormatting.GREEN)
                    : Component.literal("Your music link was not approved.").withStyle(ChatFormatting.RED));
        }
    }

    private static int forget(CommandContext<CommandSourceStack> ctx) {
        GateStore store = store(ctx);
        if (store == null) {
            return 0;
        }
        String url = Urls.key(StringArgumentType.getString(ctx, "url"));
        if (url.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Give a link."));
            return 0;
        }
        if (!store.forget(url)) {
            ctx.getSource().sendFailure(Component.literal("That link is not on either list."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> EtchGate.prefix()
                .append(Component.literal("Cleared ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Urls.forDisplay(url)).withStyle(ChatFormatting.WHITE)), true);
        return 1;
    }
}
