package com.atenas.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Cliente del servicio social de Atenas (el mismo Worker del launcher y del cliente 1.8.9).
 * Comparte la clave con el launcher en ~/.atenasclient/social.json, así que eres la misma persona.
 * Toda la red va en un hilo aparte; la interfaz solo lee {@link #snapshot()}.
 */
public final class SocialClient {
    public static final String DEFAULT_URL = "https://atenas-presence.misafi282.workers.dev";
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    public static final class Friend {
        public String name = "", key = "", state = "offline", server = "", pack = "", previewBody = "";
        public int unread;
        public long previewId;
        public boolean online() { return !"offline".equals(state); }
    }
    public static final class Req { public String name = "", key = ""; public long ts; }
    public static final class Msg { public long id; public boolean mine; public String body = ""; public long ts; public boolean pending, failed; }
    public static final class Snapshot {
        public final List<Friend> friends = new ArrayList<>();
        public final List<Req> incoming = new ArrayList<>();
        public final List<Req> outgoing = new ArrayList<>();
        public int unread;
    }
    public interface Callback<T> { void done(T value, String error); }

    public static volatile Supplier<String> nameSupplier = () -> "";
    /** Clave del chat abierto (para no avisar de mensajes que ya estás viendo). */
    public static volatile String viewingKey = "";

    private static String url = DEFAULT_URL;
    private static String token = "";
    private static boolean identityLoaded;
    private static volatile Snapshot snap;
    private static volatile String status = "loading"; // loading | ok | error | noaccount
    private static volatile String error = "";
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Atenas-Social");
        t.setDaemon(true);
        return t;
    });

    private SocialClient() {}

    public static Snapshot snapshot() { return snap; }
    public static String status() { return status; }
    public static String error() { return error; }
    public static String myName() { String n = nameSupplier.get(); return n == null ? "" : n; }
    public static boolean validName(String n) { return n != null && NAME.matcher(n).matches(); }

    public static Friend find(String key) {
        Snapshot s = snap;
        if (s != null && key != null) for (Friend f : s.friends) if (f.key.equals(key)) return f;
        return null;
    }

    // ---------------------------------------------------------------- identidad compartida
    private static File identityFile() {
        String home = System.getenv("ATENAS_HOME");
        File root = (home != null && !home.isEmpty()) ? new File(home) : new File(System.getProperty("user.home"), ".atenasclient");
        return new File(root, "social.json");
    }

    private static synchronized void loadIdentity() {
        if (identityLoaded) return;
        identityLoaded = true;
        File f = identityFile();
        try {
            if (f.exists()) {
                JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("token") && o.get("token").getAsString().length() >= 16) token = o.get("token").getAsString();
                if (o.has("url") && !o.get("url").getAsString().trim().isEmpty()) url = o.get("url").getAsString().trim();
            }
        } catch (Exception ignored) { /* se crea una nueva */ }
        if (token.length() < 16) {
            token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            try {
                f.getParentFile().mkdirs();
                JsonObject o = new JsonObject();
                o.addProperty("token", token);
                o.addProperty("url", url);
                Files.write(f.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) { System.err.println("[AtenasSocial] social.json: " + ex.getMessage()); }
        }
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
    }

    // ---------------------------------------------------------------- red
    private static String readAll(InputStream is) throws IOException {
        try (InputStream in = is) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static JsonObject post(String route, JsonObject body) throws IOException {
        loadIdentity();
        String name = myName();
        if (!validName(name)) throw new IOException("Tu nick no es válido para el servicio de amigos.");
        HttpURLConnection c = (HttpURLConnection) new URL(url + route).openConnection();
        c.setConnectTimeout(6000);
        c.setReadTimeout(8000);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("User-Agent", "AtenasSocial/1.20.1");
        c.setRequestProperty("x-name", name);
        c.setRequestProperty("x-token", token);
        c.setDoOutput(true);
        int code;
        try {
            try (OutputStream os = c.getOutputStream()) { os.write((body == null ? "{}" : body.toString()).getBytes(StandardCharsets.UTF_8)); }
            code = c.getResponseCode();
        } catch (IOException e) { throw new IOException("Sin conexión con el servicio de amigos."); }
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String res = is == null ? "" : readAll(is);
        JsonObject o = null;
        try { o = JsonParser.parseString(res).getAsJsonObject(); } catch (Exception ignored) { }
        if (o == null || code >= 400 || (o.has("ok") && !o.get("ok").getAsBoolean())) {
            String msg = (o != null && o.has("message")) ? o.get("message").getAsString() : "El servicio respondió con error " + code + ".";
            throw new IOException(msg);
        }
        return o;
    }

    private static String str(JsonObject o, String k) { return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : ""; }

    private static Snapshot parse(JsonObject o) {
        Snapshot s = new Snapshot();
        s.unread = o.has("unread") ? o.get("unread").getAsInt() : 0;
        for (JsonElement el : o.getAsJsonArray("friends")) {
            JsonObject f = el.getAsJsonObject();
            Friend fr = new Friend();
            fr.name = str(f, "name"); fr.key = str(f, "key"); fr.state = str(f, "state");
            fr.server = str(f, "server"); fr.pack = str(f, "pack");
            fr.unread = f.has("unread") ? f.get("unread").getAsInt() : 0;
            if (f.has("preview") && f.get("preview").isJsonObject()) {
                JsonObject p = f.getAsJsonObject("preview");
                fr.previewBody = str(p, "body");
                fr.previewId = p.has("id") ? p.get("id").getAsLong() : 0;
            }
            s.friends.add(fr);
        }
        fill(s.incoming, o.getAsJsonArray("incoming"));
        fill(s.outgoing, o.getAsJsonArray("outgoing"));
        return s;
    }

    private static void fill(List<Req> out, JsonArray arr) {
        for (JsonElement el : arr) {
            JsonObject r = el.getAsJsonObject();
            Req q = new Req();
            q.name = str(r, "name"); q.key = str(r, "key"); q.ts = r.has("ts") ? r.get("ts").getAsLong() : 0;
            out.add(q);
        }
    }

    // ---------------------------------------------------------------- sincronización
    public static void sync(boolean inGame, String server) {
        if (!validName(myName())) { status = "noaccount"; return; }
        if (!BUSY.compareAndSet(false, true)) return;
        final String who = myName();
        POOL.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                if (inGame) {
                    JsonObject p = new JsonObject();
                    p.addProperty("source", "game");
                    p.addProperty("server", server == null ? "" : server);
                    p.addProperty("pack", PackInfo.packName());
                    p.addProperty("playing", true);
                    body.add("presence", p);
                }
                Snapshot next = parse(post("/sync", body));
                if (!who.equals(myName())) return;
                notifyChanges(snap, next);
                snap = next;
                status = "ok";
                error = "";
            } catch (Exception e) {
                status = "error";
                error = String.valueOf(e.getMessage());
            } finally { BUSY.set(false); }
        });
    }

    /** Refresca el estado ahora mismo (tras una acción) sin avisos. */
    private static void refreshQuiet() {
        try { snap = parse(post("/sync", new JsonObject())); status = "ok"; error = ""; } catch (Exception ignored) { }
    }

    private static void toast(String title, String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> SystemToast.add(mc.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
            Component.literal(title), Component.literal(text)));
    }

    private static void notifyChanges(Snapshot prev, Snapshot next) {
        if (prev == null) return;
        Map<String, Friend> before = new HashMap<>();
        for (Friend f : prev.friends) before.put(f.key, f);
        java.util.Set<String> prevIn = new java.util.HashSet<>(), prevOut = new java.util.HashSet<>();
        for (Req r : prev.incoming) prevIn.add(r.key);
        for (Req r : prev.outgoing) prevOut.add(r.key);
        for (Req r : next.incoming) if (!prevIn.contains(r.key)) toast("Solicitud de amistad", r.name + " quiere ser tu amigo");
        for (Friend f : next.friends) {
            Friend p = before.get(f.key);
            if (p == null) { if (prevOut.contains(f.key)) toast("Atenas", f.name + " aceptó tu solicitud"); continue; }
            if (f.unread > 0 && f.previewId > p.previewId && !f.key.equals(viewingKey)) toast(f.name, f.previewBody);
            else if (!p.online() && f.online()) toast("Atenas", f.name + (f.state.equals("playing") ? " está jugando" : " se conectó"));
        }
    }

    // ---------------------------------------------------------------- acciones
    private static void action(String route, JsonObject body, Callback<String> cb) {
        POOL.execute(() -> {
            try {
                JsonObject r = post(route, body);
                refreshQuiet();
                if (cb != null) cb.done(str(r, "result") + "|" + str(r, "name"), null);
            } catch (Exception e) { if (cb != null) cb.done(null, String.valueOf(e.getMessage())); }
        });
    }

    public static void add(String to, Callback<String> cb) { JsonObject b = new JsonObject(); b.addProperty("to", to); action("/friends/add", b, cb); }
    public static void accept(String key, Callback<String> cb) { JsonObject b = new JsonObject(); b.addProperty("name", key); action("/friends/accept", b, cb); }
    public static void remove(String key, Callback<String> cb) { JsonObject b = new JsonObject(); b.addProperty("name", key); action("/friends/remove", b, cb); }

    public static void history(String key, long after, Callback<List<Msg>> cb) {
        POOL.execute(() -> {
            try {
                JsonObject b = new JsonObject();
                b.addProperty("with", key);
                b.addProperty("after", after);
                JsonObject r = post("/chat/history", b);
                List<Msg> out = new ArrayList<>();
                for (JsonElement el : r.getAsJsonArray("messages")) {
                    JsonObject m = el.getAsJsonObject();
                    Msg x = new Msg();
                    x.id = m.get("id").getAsLong(); x.mine = m.get("mine").getAsBoolean();
                    x.body = str(m, "body"); x.ts = m.get("ts").getAsLong();
                    out.add(x);
                }
                cb.done(out, null);
            } catch (Exception e) { cb.done(Collections.<Msg>emptyList(), String.valueOf(e.getMessage())); }
        });
    }

    public static void send(String key, String text, Callback<Msg> cb) {
        JsonObject b = new JsonObject();
        b.addProperty("to", key);
        b.addProperty("body", text);
        POOL.execute(() -> {
            try {
                JsonObject m = post("/chat/send", b).getAsJsonObject("message");
                Msg x = new Msg();
                x.id = m.get("id").getAsLong(); x.mine = true; x.body = str(m, "body"); x.ts = m.get("ts").getAsLong();
                cb.done(x, null);
            } catch (Exception e) { cb.done(null, String.valueOf(e.getMessage())); }
        });
    }
}
