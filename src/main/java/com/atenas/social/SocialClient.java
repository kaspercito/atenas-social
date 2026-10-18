package com.atenas.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import net.minecraft.client.Minecraft;

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
    /** Error que devolvió el servicio (con su código, p. ej. verify_required). Un IOException normal es un fallo de red. */
    public static final class ApiError extends IOException {
        public final String code;
        public ApiError(String code, String message) { super(message); this.code = code; }
    }

    public static volatile Supplier<String> nameSupplier = () -> "";
    /** Clave del chat abierto (para no avisar de mensajes que ya estás viendo). */
    public static volatile String viewingKey = "";

    private static String url = DEFAULT_URL;
    private static String token = "";
    private static boolean identityLoaded;
    /** Nick (en minúsculas) que ya demostró con Mojang que su cuenta premium es suya, y último intento que no se pudo verificar. */
    private static String verifiedFor = "";
    private static String failedFor = "";
    private static long failedAt;
    private static final long RETRY_UNVERIFIED_MS = 5L * 60L * 1000L;
    /** Por que fallo la ultima verificacion (vacio si fue bien o aun no se intento). Tambien sale en latest.log con el prefijo [AtenasSocial]. */
    private static volatile String verifyError = "";
    public static String verifyError() { return verifyError; }
    private static volatile Snapshot snap;
    private static volatile String status = "loading"; // loading | ok | error | noaccount
    private static volatile String error = "";
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final AtomicBoolean POLL_BUSY = new AtomicBoolean(false);
    /** Firma de la última consulta rápida (sin leer : último mensaje : solicitudes). Si cambia, llegó algo nuevo. */
    private static volatile String lastPoll;
    private static volatile long lastSync, lastPollAt;
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
    /** Carpeta de Atenas (la misma que usan el launcher y el cliente 1.8.9). */
    public static File root() {
        String home = System.getenv("ATENAS_HOME");
        return (home != null && !home.isEmpty()) ? new File(home) : new File(System.getProperty("user.home"), ".atenasclient");
    }

    /** Direccion del servicio (sin barra final). */
    public static String baseUrl() { loadIdentity(); return url; }

    private static File identityFile() { return new File(root(), "social.json"); }

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

    /** True si esta cuenta ya demostró con Mojang que es premium y suya (los cosméticos privados lo exigen). */
    public static synchronized boolean verified() { return myName().equalsIgnoreCase(verifiedFor); }

    /**
     * Verificación premium: pide un desafío al servicio, lo registra en Mojang con TU sesión (joinServer, solo el dueño de la
     * cuenta puede) y el servicio lo comprueba con Mojang (hasJoined). Con una cuenta sin conexión Mojang lo rechaza y la cuenta
     * queda sin verificar (todo funciona, menos los cosméticos privados). Nunca lanza: si falla, el servicio la trata como no verificada.
     */
    private static synchronized void ensureVerified(boolean force) {
        String name = myName().toLowerCase();
        if (!force && name.equals(verifiedFor)) return;
        if (!force && name.equals(failedFor) && System.currentTimeMillis() - failedAt < RETRY_UNVERIFIED_MS) return;
        try {
            JsonObject ch = rawPost("/auth/challenge", new JsonObject());
            Minecraft mc = Minecraft.getInstance();
            try {
                mc.getMinecraftSessionService().joinServer(mc.getUser().getGameProfile(), mc.getUser().getAccessToken(), str(ch, "serverId"));
            } catch (Exception e) {
                failedFor = name; failedAt = System.currentTimeMillis(); // Mojang no aceptó la sesión: cuenta sin conexión o sesión caducada
                verifyError = "Mojang rechazó la sesión del juego (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
                System.err.println("[AtenasSocial] verificación: " + verifyError + " · nick=" + name);
                return;
            }
            rawPost("/auth/verify", new JsonObject());
            verifiedFor = name;
            failedFor = "";
            verifyError = "";
        } catch (ApiError e) {
            verifyError = "el servicio respondió " + e.code + ": " + e.getMessage();
            System.err.println("[AtenasSocial] verificación: " + verifyError + " · nick=" + name);
            if ("not_verified".equals(e.code)) diagnoseVerify(name);
            if (!"mojang_down".equals(e.code) && !"too_many".equals(e.code) && !"no_challenge".equals(e.code) && !"server".equals(e.code)) {
                failedFor = name; failedAt = System.currentTimeMillis(); // el servicio dijo que no: se reintenta más tarde
            }
        } catch (IOException e) {
            verifyError = "sin conexión con el servicio de verificación (" + e.getMessage() + ")";
            System.err.println("[AtenasSocial] verificación: " + verifyError);
            // sin internet: se reintenta en la próxima llamada
        }
    }

    /**
     * Solo diagnóstico (se llama cuando el servicio dijo not_verified): muestra qué tipo de sesión tiene el juego y repite el registro en
     * Mojang preguntándole DIRECTO a Mojang si lo ve. Así se sabe si el fallo es de la sesión de este PC o del servicio.
     */
    private static void diagnoseVerify(String name) {
        try {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.User u = mc.getUser();
            String tok = u.getAccessToken() == null ? "" : u.getAccessToken();
            String shape = tok.length() + " caracteres" + (tok.matches("[0-9a-fA-F-]{32,36}") ? " (parece un UUID: sesión SIN CONEXIÓN)" : "");
            System.err.println("[AtenasSocial] diagnóstico: tipo de sesión=" + u.getType() + " · token=" + shape + " · uuid=" + u.getProfileId() + " · nick=" + u.getName());
            JsonObject ch = rawPost("/auth/challenge", new JsonObject());
            String sid = str(ch, "serverId");
            mc.getMinecraftSessionService().joinServer(u.getGameProfile(), u.getAccessToken(), sid);
            HttpURLConnection c = (HttpURLConnection) new URL("https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
                + java.net.URLEncoder.encode(u.getName(), "UTF-8") + "&serverId=" + java.net.URLEncoder.encode(sid, "UTF-8")).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(6000);
            int st = c.getResponseCode();
            System.err.println("[AtenasSocial] diagnóstico: Mojang hasJoined desde este PC = HTTP " + st
                + (st == 200 ? " (Mojang SÍ ve tu sesión: el fallo está en el servicio)" : st == 204 ? " (Mojang NO registró tu sesión)" : ""));
        } catch (Exception e) {
            System.err.println("[AtenasSocial] diagnóstico: " + e);
        }
    }

    /** POST al servicio. Lanza IOException con mensaje legible (ApiError si la respuesta vino del servicio). */
    public static JsonObject post(String route, JsonObject body) throws IOException {
        loadIdentity();
        String name = myName();
        if (!validName(name)) throw new IOException("Tu nick no es válido para el servicio de amigos.");
        ensureVerified(false);
        try {
            return rawPost(route, body);
        } catch (ApiError e) {
            // este nick está verificado desde otra instalación y esta aún no: se demuestra con la sesión de Minecraft y se reintenta una vez
            if (!"verify_required".equals(e.code)) throw e;
            synchronized (SocialClient.class) { verifiedFor = ""; failedFor = ""; }
            ensureVerified(true);
            if (!verified()) throw e;
            return rawPost(route, body);
        }
    }

    private static JsonObject rawPost(String route, JsonObject body) throws IOException {
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
            if (o != null && o.has("error")) throw new ApiError(o.get("error").getAsString(), msg);
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
        lastSync = System.currentTimeMillis();
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

    public static long sinceLastSync() { return System.currentTimeMillis() - lastSync; }

    public static long sinceLastPoll() { return System.currentTimeMillis() - lastPollAt; }

    /**
     * Consulta rápida (/poll): el servicio responde con 2 lecturas pequeñas en vez de bajar todo el estado. Se hace cada pocos
     * segundos y solo si algo cambió (mensaje o solicitud nueva) se pide el estado completo con /sync, así los avisos llegan casi
     * al instante sin cargar al servicio. La primera vez solo guarda la firma. No bloquea.
     */
    public static void poll(boolean inGame, String server) {
        if (snap == null || !validName(myName())) return; // sin estado completo, el sync normal se encarga
        if (!POLL_BUSY.compareAndSet(false, true)) return;
        lastPollAt = System.currentTimeMillis();
        final String who = myName();
        POOL.execute(() -> {
            try {
                JsonObject r = post("/poll", new JsonObject());
                if (!who.equals(myName())) return;
                String sig = str(r, "unread") + ":" + str(r, "last") + ":" + str(r, "incoming");
                boolean changed = lastPoll != null && !sig.equals(lastPoll);
                lastPoll = sig;
                if (changed) sync(inGame, server);
            } catch (Exception ignored) { /* sin conexión: el ciclo normal reintenta */
            } finally { POLL_BUSY.set(false); }
        });
    }

    /** Refresca el estado ahora mismo (tras una acción) sin avisos. */
    private static void refreshQuiet() {
        try { snap = parse(post("/sync", new JsonObject())); status = "ok"; error = ""; } catch (Exception ignored) { }
    }

    private static void notifyChanges(Snapshot prev, Snapshot next) {
        if (prev == null) return;
        Map<String, Friend> before = new HashMap<>();
        for (Friend f : prev.friends) before.put(f.key, f);
        java.util.Set<String> prevIn = new java.util.HashSet<>(), prevOut = new java.util.HashSet<>();
        for (Req r : prev.incoming) prevIn.add(r.key);
        for (Req r : prev.outgoing) prevOut.add(r.key);
        for (Req r : next.incoming) if (!prevIn.contains(r.key)) Toasts.push("Solicitud de amistad", r.name + " quiere ser tu amigo", r.name, Ui.ACCENT);
        for (Friend f : next.friends) {
            Friend p = before.get(f.key);
            if (p == null) { if (prevOut.contains(f.key)) Toasts.push("Nuevo amigo", f.name + " aceptó tu solicitud", f.name, 0xFF55FF99); continue; }
            if (f.unread > 0 && f.previewId > p.previewId && !f.key.equals(viewingKey)) Toasts.push(f.name, f.previewBody, f.name, 0xFF55CCFF);
            else if (!p.online() && f.online()) Toasts.push("Amigos", f.name + (f.state.equals("playing") ? " está jugando" : " se conectó"), f.name, 0xFF55FF99);
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
