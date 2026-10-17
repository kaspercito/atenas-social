package com.atenas.social.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Catálogo de cosméticos y lo que tienes elegido (se guarda en config/atenas-cosmetics.json). */
public final class Cosmetics {
    public static final String MODID = "atenassocial";

    public enum Type {
        CAPE("Capas", "capes"), WINGS("Alas", "wings"), HAT("Sombreros", "hats"), PET("Mascotas", "pets");
        public final String label, folder;
        Type(String label, String folder) { this.label = label; this.folder = folder; }
        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final class Item {
        public final Type type;
        public final String id, name;
        public final int color;
        /** Se dibuja con luz máxima (brilla de noche). */
        public final boolean glow;
        /** Capa animada: la textura es una tira vertical de ANIM_FRAMES imagenes. 1 = capa normal. */
        public final int frames;
        public final ResourceLocation texture, icon;

        Item(Type type, String id, String name, int color, boolean glow, int frames) {
            this.frames = frames;
            this.type = type; this.id = id; this.name = name; this.color = color; this.glow = glow;
            this.texture = new ResourceLocation(MODID, "textures/cosmetics/" + type.folder + "/" + id + ".png");
            this.icon = new ResourceLocation(MODID, "textures/cosmetics/icons/" + type.key() + "_" + id + ".png");
        }
    }

    /** Fotogramas de toda capa animada (el modelo trae una pieza por fotograma). */
    public static final int ANIM_FRAMES = 12;
    /** Milisegundos por fotograma (~11 fps). */
    public static final int FRAME_MS = 90;
    /** Los cosmeticos de otros jugadores solo se dibujan dentro de esta distancia (bloques al cuadrado). */
    public static final double MAX_DIST_SQ = 40.0 * 40.0;
    /** Capas animadas encendidas; apagadas se ve el primer fotograma. */
    public static boolean animate = true;

    public static final List<Item> ALL = new ArrayList<>();
    private static final Map<Type, Item> SELECTED = new EnumMap<>(Type.class);

    static {
        add(Type.CAPE, "atenas", "Atenas", 0xFF8B5CF6, false);
        add(Type.CAPE, "violeta", "Violeta", 0xFF6D4FC7, false);
        add(Type.CAPE, "fuego", "Fuego", 0xFFFF6A00, true);
        add(Type.CAPE, "hielo", "Hielo", 0xFF7FD6FF, false);
        add(Type.CAPE, "galaxia", "Galaxia", 0xFF7B4DFF, false);
        add(Type.CAPE, "arcoiris", "Arco\u00edris", 0xFFFF55FF, false);
        add(Type.CAPE, "esmeralda", "Esmeralda", 0xFF2ECC71, false);
        add(Type.CAPE, "oceano", "Oc\u00e9ano", 0xFF1E7FD8, false);
        add(Type.CAPE, "atardecer", "Atardecer", 0xFFFF7A59, false);
        add(Type.CAPE, "matrix", "Matrix", 0xFF22DD66, true);
        add(Type.CAPE, "neon", "Ne\u00f3n", 0xFF22D3EE, true);
        add(Type.CAPE, "oro", "Oro", 0xFFE8B923, false);
        add(Type.CAPE, "noche", "Noche", 0xFF2B2F6B, false);
        add(Type.CAPE, "creeper", "Creeper", 0xFF4CAF50, false);
        addAnim(Type.CAPE, "llamas", "Llamas (anim)", 0xFFFF6A00, true);
        addAnim(Type.CAPE, "aurora", "Aurora (anim)", 0xFF35E0A8, true);
        addAnim(Type.CAPE, "cosmos", "Cosmos (anim)", 0xFF7B4DFF, true);
        addAnim(Type.CAPE, "lluvia", "Lluvia Matrix (anim)", 0xFF22DD66, true);
        addAnim(Type.CAPE, "mareas", "Mareas (anim)", 0xFF1E9FE8, false);
        addAnim(Type.CAPE, "pulso", "Pulso Ne\u00f3n (anim)", 0xFFEC4899, true);

        add(Type.WINGS, "doradas", "Doradas", 0xFFF5C542, false);
        add(Type.WINGS, "oscuras", "Oscuras", 0xFF8E2DE2, false);
        add(Type.WINGS, "angel", "\u00c1ngel", 0xFFEAF6FF, true);
        add(Type.WINGS, "atenas", "Atenas", 0xFF8B5CF6, false);
        add(Type.WINGS, "fuego", "Fuego", 0xFFFF6A00, true);
        add(Type.WINGS, "hielo", "Hielo", 0xFF9ADCFF, false);
        add(Type.WINGS, "neon", "Ne\u00f3n", 0xFF22D3EE, true);
        add(Type.WINGS, "esmeralda", "Esmeralda", 0xFF2ECC71, false);
        add(Type.WINGS, "demonio", "Demonio", 0xFFC0182A, false);

        add(Type.HAT, "halo", "Halo", 0xFFFFE680, true);
        add(Type.HAT, "corona", "Corona", 0xFFF5C542, false);
        add(Type.HAT, "galera", "Galera", 0xFF3A3A4E, false);
        add(Type.HAT, "gato", "Orejas de gato", 0xFF2A2A34, false);
        add(Type.HAT, "cuernos", "Cuernos", 0xFFC0182A, false);
        add(Type.HAT, "mago", "Mago", 0xFF5B2FA8, false);
        add(Type.HAT, "auriculares", "Auriculares", 0xFF28283A, false);
        add(Type.HAT, "conejo", "Orejas de conejo", 0xFFF2F2F7, false);
        add(Type.HAT, "gorra", "Gorra", 0xFF2447B8, false);
        // Mascotas (el modelo se elige por id en PetSpec: perro/dalmata = dog, husky, gato/gatonegro = cat, zorro = fox)
        add(Type.PET, "perro", "Perro", 0xFFCD9654, false);
        add(Type.PET, "husky", "Husky", 0xFF68707E, false);
        add(Type.PET, "dalmata", "D\u00e1lmata", 0xFFF4F4F6, false);
        add(Type.PET, "gato", "Gato", 0xFFE28E3E, false);
        add(Type.PET, "gatonegro", "Gato negro", 0xFF24242E, false);
        add(Type.PET, "zorro", "Zorro", 0xFFE87028, false);
        load();
    }

    private Cosmetics() {}

    private static void add(Type t, String id, String name, int color, boolean glow) { ALL.add(new Item(t, id, name, color, glow, 1)); }

    private static void addAnim(Type t, String id, String name, int color, boolean glow) { ALL.add(new Item(t, id, name, color, glow, ANIM_FRAMES)); }

    /** Fotograma actual de una capa animada (0 si esta apagado o no es animada). */
    public static int frameOf(Item it) {
        if (it.frames <= 1 || !animate) return 0;
        return (int) ((net.minecraft.Util.getMillis() / FRAME_MS) % it.frames);
    }

    /** True si este jugador esta lo bastante cerca como para dibujarle cosmeticos (el tuyo siempre). */
    public static boolean inRange(AbstractClientPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null || p == mc.player || p.distanceToSqr(mc.player) <= MAX_DIST_SQ;
    }

    public static List<Item> of(Type t) {
        List<Item> out = new ArrayList<>();
        for (Item i : ALL) if (i.type == t) out.add(i);
        return out;
    }

    public static Item find(Type t, String id) {
        if (id == null) return null;
        for (Item i : ALL) if (i.type == t && i.id.equals(id)) return i;
        return null;
    }

    public static Item selected(Type t) { return SELECTED.get(t); }

    public static void select(Type t, Item i) {
        if (i == null) SELECTED.remove(t); else SELECTED.put(t, i);
        save();
        CosmeticSync.markDirty();
    }

    /** Aplica lo que el servicio tiene para TI (otra versi\u00f3n del juego lo cambi\u00f3). No lo vuelve a subir. */
    public static void applyRemote(Type t, String id) {
        if (id == null) return;
        Item cur = SELECTED.get(t);
        if ("none".equals(id)) { if (cur != null) { SELECTED.remove(t); save(); } return; }
        Item next = find(t, id);
        if (next != null && next != cur) { SELECTED.put(t, next); save(); }
    }

    /** Lo que lleva puesto otro jugador, ya resuelto (se recalcula solo cuando llegan datos nuevos del servicio). */
    private static final class Look {
        final Item[] items = new Item[Type.values().length];
        int stamp = -1;
    }

    /** Cache por jugador (clave = nick tal como lo da el perfil; su hash ya viene calculado). Solo el hilo principal la toca. */
    private static final Map<String, Look> LOOKS = new HashMap<>();

    /** Cosmético a dibujar en un jugador: el tuyo, o el que bajamos del servicio para los demás (con cache: sin buscar ni crear texto cada cuadro). */
    public static Item of(AbstractClientPlayer p, Type t) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player) return selected(t);
        String n = p.getGameProfile().getName();
        if (n == null) return null;
        Look l = LOOKS.get(n);
        if (l == null) {
            if (LOOKS.size() > 256) LOOKS.clear();
            l = new Look();
            LOOKS.put(n, l);
        }
        int stamp = CosmeticSync.stamp();
        if (l.stamp != stamp) {
            String[] ids = CosmeticSync.idsOf(n);
            for (Type ty : Type.values()) {
                String id = ids == null || ty.ordinal() >= ids.length ? null : ids[ty.ordinal()];
                l.items[ty.ordinal()] = find(ty, id);
            }
            l.stamp = stamp;
        }
        return l.items[t.ordinal()];
    }

    private static Path file() { return FMLPaths.CONFIGDIR.get().resolve("atenas-cosmetics.json"); }

    private static void load() {
        Path f = file();
        if (!Files.exists(f)) { SELECTED.put(Type.CAPE, find(Type.CAPE, "atenas")); return; }
        try {
            JsonObject o = JsonParser.parseString(new String(Files.readAllBytes(f), StandardCharsets.UTF_8)).getAsJsonObject();
            for (Type t : Type.values()) {
                Item it = o.has(t.key()) ? find(t, o.get(t.key()).getAsString()) : null;
                if (it != null) SELECTED.put(t, it);
            }
            if (o.has("animate")) animate = o.get("animate").getAsBoolean();
        } catch (Exception e) { System.err.println("[AtenasSocial] cosm\u00e9ticos: " + e); }
    }

    public static void toggleAnimate() { animate = !animate; save(); }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            for (Type t : Type.values()) o.addProperty(t.key(), SELECTED.containsKey(t) ? SELECTED.get(t).id : "none");
            o.addProperty("animate", animate);
            Files.write(file(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { System.err.println("[AtenasSocial] guardar cosm\u00e9ticos: " + e.getMessage()); }
    }
}
