package com.atenas.social.cosmetics;

import com.atenas.social.SocialClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Catalogo de cosmeticos del servicio: reglas de exclusividad de los incorporados (ej. la mascota Catalina) y cosmeticos nuevos
 * con textura. Se pide cada 45 s con la ultima revision que tenemos (si no cambio, el servicio contesta {unchanged:true}) y se guarda
 * en disco: sin internet el cliente arranca con el ultimo catalogo conocido. La aplicacion (Cosmetics.applyCatalog) va en el hilo principal.
 * Es el mismo servicio y el mismo archivo de cache que el cliente 1.8.9 (carpeta cosmetics de .atenasclient).
 */
public final class CatalogSync {
    public static final long PERIOD_MS = 45000L;

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Atenas-Catalog");
        t.setDaemon(true);
        return t;
    });
    private static String rev = "";
    private static List<Cosmetics.Def> last = new ArrayList<>();
    private static volatile long lastTry;
    private static boolean cacheLoaded;
    private static String lastMe = "";

    private CatalogSync() {}

    private static File cacheFile() { return new File(CosmeticAssets.dir(), "catalog.json"); }

    /** Una vez por segundo desde el tick del cliente (hilo principal). */
    public static void tick(Minecraft mc) {
        String me = SocialClient.myName();
        boolean valid = SocialClient.validName(me);
        String low = valid ? me.toLowerCase(Locale.ROOT) : "";
        if (!cacheLoaded) {
            cacheLoaded = true;
            lastMe = low;
            loadCache();
        } else if (!low.equals(lastMe)) {
            // cambio la cuenta: las reglas privadas dependen del nick, se vuelven a aplicar y se pide el catalogo ya
            lastMe = low;
            lastTry = 0;
            if (!last.isEmpty()) Cosmetics.applyCatalog(last);
        }
        if (!valid) return;
        long now = System.currentTimeMillis();
        if (now - lastTry < PERIOD_MS || !BUSY.compareAndSet(false, true)) return;
        lastTry = now;
        final String sent = rev;
        POOL.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("rev", sent);
                JsonObject res = SocialClient.post("/cosmetics/catalog", body);
                if (res.has("unchanged") && res.get("unchanged").getAsBoolean()) {
                    mc.execute(CosmeticAssets::retryMissing); // por si alguna textura fallo
                    return;
                }
                JsonArray items = res.has("items") && res.get("items").isJsonArray() ? res.getAsJsonArray("items") : new JsonArray();
                final List<Cosmetics.Def> defs = parse(items);
                final String newRev = res.has("rev") && !res.get("rev").isJsonNull() ? res.get("rev").getAsString() : "";
                save(newRev, items);
                mc.execute(() -> {
                    rev = newRev;
                    last = defs;
                    Cosmetics.applyCatalog(defs);
                });
            } catch (Exception ex) {
                lastTry = System.currentTimeMillis() - PERIOD_MS + 10000L; // reintenta en ~10 s
                System.err.println("[AtenasSocial] catalogo: " + ex.getMessage());
            } finally {
                BUSY.set(false);
            }
        });
    }

    /** Lee el catalogo guardado en disco y lo aplica (sin red). HILO PRINCIPAL. */
    private static void loadCache() {
        File f = cacheFile();
        if (!f.isFile()) return;
        try {
            JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            List<Cosmetics.Def> defs = parse(o.has("items") && o.get("items").isJsonArray() ? o.getAsJsonArray("items") : null);
            rev = o.has("rev") && !o.get("rev").isJsonNull() ? o.get("rev").getAsString() : "";
            last = defs;
            Cosmetics.applyCatalog(defs);
        } catch (Exception e) {
            System.err.println("[AtenasSocial] catalogo en disco: " + e);
            rev = "";
        }
    }

    private static void save(String newRev, JsonArray items) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("rev", newRev);
            o.add("items", items);
            File f = cacheFile();
            File tmp = new File(f.getPath() + ".tmp");
            Files.write(tmp.toPath(), o.toString().getBytes(StandardCharsets.UTF_8));
            if (f.exists()) f.delete();
            tmp.renameTo(f);
        } catch (IOException ex) {
            System.err.println("[AtenasSocial] no se pudo guardar el catalogo: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ JSON

    static List<Cosmetics.Def> parse(JsonArray arr) {
        List<Cosmetics.Def> out = new ArrayList<>();
        if (arr == null) return out;
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            try {
                Cosmetics.Type type = null;
                String ts = str(o, "type");
                for (Cosmetics.Type t : Cosmetics.Type.values()) if (t.key().equals(ts)) type = t;
                String id = str(o, "id");
                if (type == null || id == null || !id.matches("[a-z0-9_]{1,24}")) continue;
                Set<String> owners = null;
                if (o.has("owners") && o.get("owners").isJsonArray()) {
                    owners = new HashSet<>();
                    for (JsonElement n : o.getAsJsonArray("owners")) owners.add(n.getAsString().toLowerCase(Locale.ROOT));
                }
                String name = str(o, "name");
                out.add(new Cosmetics.Def(type, id, name == null ? id : name,
                    o.has("color") && !o.get("color").isJsonNull() ? (int) o.get("color").getAsLong() : 0xFF8B5CF6,
                    bool(o, "glow"), str(o, "base"), o.has("frames") && !o.get("frames").isJsonNull() ? o.get("frames").getAsInt() : 1,
                    "private".equals(str(o, "scope")) ? "private" : "global", bool(o, "custom"),
                    o.has("ver") && !o.get("ver").isJsonNull() ? o.get("ver").getAsInt() : 0, owners));
            } catch (RuntimeException ex) {
                System.err.println("[AtenasSocial] entrada del catalogo ignorada: " + ex);
            }
        }
        return out;
    }

    private static String str(JsonObject o, String k) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }

    private static boolean bool(JsonObject o, String k) {
        if (!o.has(k) || o.get(k).isJsonNull()) return false;
        try { return o.get(k).getAsBoolean(); } catch (RuntimeException e) { return o.get(k).getAsInt() != 0; }
    }
}
