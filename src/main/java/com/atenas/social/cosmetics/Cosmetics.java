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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Catálogo de cosméticos y lo que tienes elegido (se guarda en config/atenas-cosmetics.json). */
public final class Cosmetics {
    public static final String MODID = "atenassocial";

    public enum Type {
        CAPE("Capas", "capes"), WINGS("Alas", "wings"), HAT("Sombreros", "hats");
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
        public final ResourceLocation texture, icon;

        Item(Type type, String id, String name, int color, boolean glow) {
            this.type = type; this.id = id; this.name = name; this.color = color; this.glow = glow;
            this.texture = new ResourceLocation(MODID, "textures/cosmetics/" + type.folder + "/" + id + ".png");
            this.icon = new ResourceLocation(MODID, "textures/cosmetics/icons/" + type.key() + "_" + id + ".png");
        }
    }

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
        load();
    }

    private Cosmetics() {}

    private static void add(Type t, String id, String name, int color, boolean glow) { ALL.add(new Item(t, id, name, color, glow)); }

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

    /** Cosmético a dibujar en un jugador: el tuyo, o el que bajamos del servicio para los demás. */
    public static Item of(AbstractClientPlayer p, Type t) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player) return selected(t);
        return find(t, CosmeticSync.get(p.getGameProfile().getName(), t));
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
        } catch (Exception e) { System.err.println("[AtenasSocial] cosm\u00e9ticos: " + e); }
    }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            for (Type t : Type.values()) o.addProperty(t.key(), SELECTED.containsKey(t) ? SELECTED.get(t).id : "none");
            Files.write(file(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { System.err.println("[AtenasSocial] guardar cosm\u00e9ticos: " + e.getMessage()); }
    }
}
