package com.cotc.etchgate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Approved and denied links, blocked hosts and pending requests, saved with the world ({@code data/etchgate.dat}).
 * Lookups read immutable copies that are swapped after every change.
 */
public class GateStore extends SavedData {
    public static final String FILE_ID = "etchgate";
    public static final SavedData.Factory<GateStore> FACTORY = new SavedData.Factory<>(GateStore::new, GateStore::load, null);

    private final Map<String, Decision> approved = new LinkedHashMap<>();
    private final Map<String, Decision> denied = new LinkedHashMap<>();
    private final Set<String> deniedHosts = new LinkedHashSet<>();
    private final Map<Integer, Request> pending = new LinkedHashMap<>();
    private int nextId = 1;

    private volatile Set<String> approvedView = Set.of();
    private volatile Set<String> deniedView = Set.of();
    private volatile Set<String> deniedHostView = Set.of();
    private volatile Set<String> pendingView = Set.of();

    public record Decision(String url, String decidedBy, long decidedAt) {
    }

    public record Request(int id, String url, String playerName, UUID playerId, long submittedAt) {
    }

    public boolean isApproved(String key) {
        return this.approvedView.contains(key);
    }

    public boolean isDenied(String key, String host) {
        return this.deniedView.contains(key) || !host.isEmpty() && this.deniedHostView.contains(host);
    }

    public boolean isPending(String key) {
        return this.pendingView.contains(key);
    }

    public List<Request> pendingRequests() {
        return List.copyOf(this.pending.values());
    }

    public List<Decision> approvedList() {
        return List.copyOf(this.approved.values());
    }

    public List<Decision> deniedList() {
        return List.copyOf(this.denied.values());
    }

    public List<String> deniedHosts() {
        return List.copyOf(this.deniedHosts);
    }

    public Request request(int id) {
        return this.pending.get(id);
    }

    public int pendingCountFor(UUID playerId) {
        int n = 0;
        for (Request r : this.pending.values()) {
            if (r.playerId().equals(playerId)) {
                n++;
            }
        }
        return n;
    }

    public Request submit(String key, String playerName, UUID playerId, long now) {
        Request r = new Request(this.nextId++, key, playerName, playerId, now);
        this.pending.put(r.id(), r);
        this.republish();
        return r;
    }

    public void approve(String key, String decidedBy, long now) {
        this.denied.remove(key);
        this.approved.put(key, new Decision(key, decidedBy, now));
        this.clearPendingFor(key);
        this.republish();
    }

    public void deny(String key, String decidedBy, long now) {
        this.approved.remove(key);
        this.denied.put(key, new Decision(key, decidedBy, now));
        this.clearPendingFor(key);
        this.republish();
    }

    public void denyHost(String host) {
        if (host.isEmpty()) {
            return;
        }
        this.deniedHosts.add(host);
        this.pending.values().removeIf(r -> Urls.host(r.url()).equals(host));
        this.republish();
    }

    public boolean allowHost(String host) {
        if (!this.deniedHosts.remove(host)) {
            return false;
        }
        this.republish();
        return true;
    }

    /** Takes a link off both lists and drops its requests. */
    public boolean forget(String key) {
        boolean changed = this.approved.remove(key) != null;
        changed |= this.denied.remove(key) != null;
        changed |= this.clearPendingFor(key);
        if (changed) {
            this.republish();
        }
        return changed;
    }

    private boolean clearPendingFor(String key) {
        return this.pending.values().removeIf(r -> r.url().equals(key));
    }

    private void republish() {
        this.approvedView = Set.copyOf(this.approved.keySet());
        this.deniedView = Set.copyOf(this.denied.keySet());
        this.deniedHostView = Set.copyOf(this.deniedHosts);
        List<String> urls = new ArrayList<>(this.pending.size());
        for (Request r : this.pending.values()) {
            urls.add(r.url());
        }
        this.pendingView = Set.copyOf(urls);
        this.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextId", this.nextId);
        tag.put("Approved", saveDecisions(this.approved));
        tag.put("Denied", saveDecisions(this.denied));
        ListTag hosts = new ListTag();
        for (String h : this.deniedHosts) {
            CompoundTag t = new CompoundTag();
            t.putString("Host", h);
            hosts.add(t);
        }
        tag.put("DeniedHosts", hosts);
        ListTag pendingTag = new ListTag();
        for (Request r : this.pending.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("Id", r.id());
            t.putString("Url", r.url());
            t.putString("Player", r.playerName());
            t.putUUID("PlayerId", r.playerId());
            t.putLong("At", r.submittedAt());
            pendingTag.add(t);
        }
        tag.put("Pending", pendingTag);
        return tag;
    }

    private static ListTag saveDecisions(Map<String, Decision> map) {
        ListTag list = new ListTag();
        for (Decision d : map.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("Url", d.url());
            t.putString("By", d.decidedBy());
            t.putLong("At", d.decidedAt());
            list.add(t);
        }
        return list;
    }

    public static GateStore load(CompoundTag tag, HolderLookup.Provider registries) {
        GateStore store = new GateStore();
        store.nextId = Math.max(1, tag.getInt("NextId"));
        loadDecisions(tag.getList("Approved", Tag.TAG_COMPOUND), store.approved);
        loadDecisions(tag.getList("Denied", Tag.TAG_COMPOUND), store.denied);
        ListTag hosts = tag.getList("DeniedHosts", Tag.TAG_COMPOUND);
        for (int i = 0; i < hosts.size(); i++) {
            String h = hosts.getCompound(i).getString("Host");
            if (!h.isEmpty()) {
                store.deniedHosts.add(h);
            }
        }
        ListTag pendingTag = tag.getList("Pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingTag.size(); i++) {
            CompoundTag t = pendingTag.getCompound(i);
            if (t.hasUUID("PlayerId")) {
                Request r = new Request(t.getInt("Id"), t.getString("Url"), t.getString("Player"), t.getUUID("PlayerId"), t.getLong("At"));
                store.pending.put(r.id(), r);
                store.nextId = Math.max(store.nextId, r.id() + 1);
            }
        }
        store.republish();
        store.setDirty(false);
        return store;
    }

    private static void loadDecisions(ListTag list, Map<String, Decision> into) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            String url = t.getString("Url");
            if (!url.isEmpty()) {
                into.put(url, new Decision(url, t.getString("By"), t.getLong("At")));
            }
        }
    }
}
