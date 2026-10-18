package com.atenas.social.cosmetics;

import com.atenas.social.SocialClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Comparte lo que llevas puesto y baja lo de los jugadores que tienes cerca.
 * Usa el mismo servicio (y la misma identidad) que Amigos: solo viaja tu nick y 4 ids de cosm\u00e9ticos.
 */
public final class CosmeticSync {
    private static final Map<String, String[]> REMOTE = new ConcurrentHashMap<>();
    private static final AtomicBoolean PUSHING = new AtomicBoolean(), FETCHING = new AtomicBoolean();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Atenas-Cosmetics");
        t.setDaemon(true);
        return t;
    });
    /** Empieza en false: primero se baja lo que hay en el servicio; solo se sube si cambias algo o el servicio no te conoce. */
    private static volatile boolean dirty = false;
    /** Sube con cada elecci\u00f3n tuya: una respuesta m\u00e1s vieja que tu \u00faltimo cambio se descarta. */
    private static volatile int rev;
    private static volatile boolean selfKnown;
    private static final AtomicBoolean SELF = new AtomicBoolean();
    private static long lastPush, lastFetch, lastSelf;
    private static String lastNames = "";

    /** Maximo de nombres por peticion /cosmetics/get (el servicio no contesta mas de 40). */
    private static final int MAX_NAMES = 40;

    private CosmeticSync() {}

    private static volatile String lastErr = "";
    /** Por que NO se esta sincronizando (null = todo bien). Lo muestra el menu de cosmeticos para que el fallo no quede oculto en el log. */
    private static volatile String problem;

    public static String problem() { return problem; }

    private static void ok() { problem = null; }

    /** Antes los fallos de sincronizacion se tragaban en silencio. Ahora se muestran en el menu y quedan en latest.log (una vez por mensaje distinto). */
    private static void logErr(String where, Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String code = e instanceof SocialClient.ApiError ? ((SocialClient.ApiError) e).code : "";
        if ("verify_required".equals(code) || "not_verified".equals(code)) {
            msg = "Sin sincronizar: este nick es de una cuenta premium. Entra con tu cuenta de Microsoft.";
        } else if ("not_found".equals(code)) {
            msg = "Sin sincronizar: el servicio no tiene los cosm\u00e9ticos (hay que actualizar el Worker).";
        } else if (msg.toLowerCase(Locale.ROOT).contains("sin conexi")) {
            msg = "Sin sincronizar: sin conexi\u00f3n con el servicio.";
        } else {
            msg = "Sin sincronizar: " + msg;
        }
        problem = msg;
        String m = where + ": " + msg;
        if (m.equals(lastErr)) return;
        lastErr = m;
        System.err.println("[AtenasSocial] sync " + m + " (nick=" + SocialClient.myName() + ")");
    }

    public static void markDirty() { dirty = true; rev++; lastPush = 0; }

    /** Pide ya lo que tienes elegido en el servicio (se llama al abrir el menu: un cambio hecho en el cliente 1.8.9 aparece al instante). */
    public static void refreshNow() { lastSelf = 0; }

    /** Cambiaste de cuenta: se baja lo que ESA cuenta ten\u00eda elegido. */
    public static void accountChanged() { dirty = false; selfKnown = false; lastSelf = 0; rev++; }

    /** Sube cada vez que cambia lo que sabemos de otros jugadores: Cosmetics vuelve a resolver su cache. */
    private static volatile int stamp;

    public static int stamp() { return stamp; }

    /** Llego un catalogo nuevo: los demas jugadores se vuelven a resolver (hilo principal). */
    public static void touch() { stamp++; }

    /** Ids de un jugador en el orden de Cosmetics.Type (capa, alas, sombrero, mascota), o null si no lo conocemos. */
    public static String[] idsOf(String name) {
        return name == null ? null : REMOTE.get(name.toLowerCase(Locale.ROOT));
    }

    public static String get(String name, Cosmetics.Type t) {
        String[] v = idsOf(name);
        return v == null ? null : v[t.ordinal()];
    }

    /** Una vez por segundo desde el tick del cliente. */
    public static void tick(Minecraft mc) {
        if (!SocialClient.validName(SocialClient.myName())) return;
        long now = System.currentTimeMillis();

        if (dirty && now - lastPush > 4000 && PUSHING.compareAndSet(false, true)) {
            lastPush = now;
            JsonObject b = new JsonObject();
            for (Cosmetics.Type t : Cosmetics.Type.values()) {
                Cosmetics.Item i = Cosmetics.selected(t);
                // eleccion que aun espera al catalogo: no se manda (el servicio conserva el campo que no viene)
                if (i == null && Cosmetics.pendingId(t) != null) continue;
                b.addProperty(t.key(), i == null ? "none" : i.id);
            }
            final int sent = rev;
            POOL.execute(() -> {
                try {
                    JsonObject res = SocialClient.post("/cosmetics/set", b);
                    if (sent == rev) dirty = false;
                    selfKnown = true;
                    ok();
                    // el servicio no dejo guardar algo privado: se quita de tu seleccion y se avisa
                    final List<String[]> denied = new ArrayList<>();
                    boolean unverified = false;
                    if (res.has("denied") && res.get("denied").isJsonArray()) {
                        for (JsonElement el : res.getAsJsonArray("denied")) {
                            if (!el.isJsonObject()) continue;
                            String ty = str(el.getAsJsonObject(), "type"), id = str(el.getAsJsonObject(), "id");
                            // "unverified": es tuyo pero esta sesion aun no se verifico con Mojang -> NO se quita, se reintenta
                            if ("unverified".equals(str(el.getAsJsonObject(), "reason"))) { unverified = true; continue; }
                            if (ty != null && id != null) denied.add(new String[] {ty, id});
                        }
                    }
                    if (unverified) { com.atenas.social.SocialClient.retryVerify(); dirty = true; lastPush = System.currentTimeMillis() + 20000L; }
                    if (!denied.isEmpty()) {
                        mc.execute(() -> {
                            for (String[] d : denied) {
                                for (Cosmetics.Type t : Cosmetics.Type.values()) if (t.key().equals(d[0])) Cosmetics.denied(t, d[1]);
                            }
                        });
                    }
                }
                catch (Exception e) { logErr("subir tus cosméticos", e); lastPush = System.currentTimeMillis() + 11000; /* reintenta en ~15 s */ }
                finally { PUSHING.set(false); }
            });
        }

        // Lo TUYO: la primera vez y cada 30 s se baja del servicio, as\u00ed un cambio hecho en 1.8.9 aparece aqu\u00ed.
        if (!dirty && !PUSHING.get() && now - lastSelf > (selfKnown ? 15000 : 5000) && SELF.compareAndSet(false, true)) {
            lastSelf = now;
            final int seen = rev;
            final String me = SocialClient.myName().toLowerCase(Locale.ROOT);
            POOL.execute(() -> {
                try {
                    JsonObject b = new JsonObject();
                    JsonArray arr = new JsonArray();
                    arr.add(me);
                    b.add("names", arr);
                    JsonObject players = SocialClient.post("/cosmetics/get", b).getAsJsonObject("players");
                    JsonElement el = players.get(me);
                    if (el != null && el.isJsonObject()) {
                        JsonObject v = el.getAsJsonObject();
                        selfKnown = true;
                        ok();
                        mc.execute(() -> {
                            if (seen != rev || dirty) return; // elegiste algo mientras tanto: gana lo tuyo
                            for (Cosmetics.Type t : Cosmetics.Type.values()) Cosmetics.applyRemote(t, str(v, t.key()));
                        });
                    } else if (seen == rev) { selfKnown = true; ok(); dirty = true; /* el servicio no te conoce: sube lo que tienes */ }
                } catch (Exception e) {
                    logErr("bajar tus cosméticos", e);
                    lastSelf = System.currentTimeMillis() - 20000; // reintenta pronto
                } finally { SELF.set(false); }
            });
        }

        if (mc.level == null || mc.player == null) return;
        // Los cercanos van primero (son los que se dibujan) y el servicio contesta como mucho 40 nombres por peticion:
        // antes se mandaban hasta 80 ordenados por alfabeto y los ultimos (z...) nunca recibian sus cosmeticos.
        List<String> near = new ArrayList<>();
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p == mc.player) continue;
            String n = p.getGameProfile().getName();
            if (SocialClient.validName(n)) near.add(n.toLowerCase(Locale.ROOT));
        }
        List<String> far = new ArrayList<>();
        if (mc.getConnection() != null) { // tambien los de la lista TAB, para el logo aunque esten lejos
            for (net.minecraft.client.multiplayer.PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                String n = info.getProfile().getName();
                String low = n == null ? null : n.toLowerCase(Locale.ROOT);
                if (low != null && SocialClient.validName(n) && !low.equals(SocialClient.myName().toLowerCase(Locale.ROOT)) && !near.contains(low) && !far.contains(low)) far.add(low);
            }
        }
        Collections.sort(near);
        Collections.sort(far);
        List<String> names = new ArrayList<>(near);
        names.addAll(far);
        if (names.isEmpty()) return;
        if (names.size() > MAX_NAMES) names = new ArrayList<>(names.subList(0, MAX_NAMES));
        String joined = String.join(",", names);
        if ((joined.equals(lastNames) && now - lastFetch < 15000) || !FETCHING.compareAndSet(false, true)) return;
        lastNames = joined;
        lastFetch = now;
        final List<String> wanted = names;
        POOL.execute(() -> {
            try {
                JsonObject b = new JsonObject();
                JsonArray arr = new JsonArray();
                for (String n : wanted) arr.add(n);
                b.add("names", arr);
                JsonObject players = SocialClient.post("/cosmetics/get", b).getAsJsonObject("players");
                for (String n : wanted) {
                    JsonElement el = players.get(n);
                    if (el != null && el.isJsonObject()) {
                        JsonObject v = el.getAsJsonObject();
                        // un elemento por Cosmetics.Type, en el mismo orden (CAPE, WINGS, HAT, PET)
                        String[] ids = new String[Cosmetics.Type.values().length];
                        for (Cosmetics.Type t : Cosmetics.Type.values()) ids[t.ordinal()] = str(v, t.key());
                        REMOTE.put(n, ids);
                    } else REMOTE.remove(n);
                }
                stamp++;
            } catch (Exception e) {
                logErr("bajar los de otros jugadores", e);
                lastFetch = 0; // vuelve a intentarlo en el pr\u00f3ximo ciclo
            } finally { FETCHING.set(false); }
        });
    }

    private static String str(JsonObject o, String k) {
        return (o.has(k) && !o.get(k).isJsonNull()) ? o.get(k).getAsString() : null;
    }
}
