package com.atenas.social.cosmetics;

import com.atenas.social.SocialClient;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        /** Capa animada: la textura es una tira vertical de 'frames' imagenes. 1 = capa normal. */
        public int frames;
        /** Textura e icono. Los cosmeticos del catalogo (x_...) usan los de su modelo base hasta que termina la descarga. */
        public ResourceLocation texture, icon;
        /** Viene del catalogo del servicio (id "x_...") y no del mod. */
        public final boolean custom;
        /** Cosmetico del mod cuya forma reutiliza (solo los del catalogo). */
        public final String base;
        /** Alas de membrana (se dibujan con {@link MembraneWing} en vez de plumas). */
        public final boolean membrane;
        /** Multiplicador de la apertura de las alas (1 = normal). */
        public float openMul = 1f;
        /** Privado y tu nick no esta en su lista: no aparece en tu menu ni puedes elegirlo. */
        public boolean locked;
        /** Version de la textura ya cargada (los del catalogo); -1 = aun no. */
        int loadedVer = -1;
        /** Version que pide el catalogo. */
        int wantVer;
        /** Texturas dinamicas ya creadas (para soltar la vieja al cambiar de version). */
        ResourceLocation dynTex, dynIcon;

        Item(Type type, String id, String name, int color, boolean glow, int frames) {
            this(type, id, name, color, glow, frames, false, null, false);
        }

        Item(Type type, String id, String name, int color, boolean glow, int frames, boolean custom, String base, boolean membrane) {
            this.frames = frames;
            this.type = type; this.id = id; this.name = name; this.color = color; this.glow = glow;
            this.custom = custom; this.base = base == null ? "" : base; this.membrane = membrane;
            this.texture = new ResourceLocation(MODID, "textures/cosmetics/" + type.folder + "/" + id + ".png");
            this.icon = new ResourceLocation(MODID, "textures/cosmetics/icons/" + type.key() + "_" + id + ".png");
        }

        /** Id del modelo que dibuja este cosmetico: el suyo, o el de su base si viene del catalogo. */
        public String modelId() { return custom && !base.isEmpty() ? base : id; }

        public String key() { return type.key() + ":" + id; }
    }

    /** Una entrada del catalogo del servicio (/cosmetics/catalog). */
    public static final class Def {
        public final Type type;
        public final String id, name, base, scope;
        public final int color, frames, ver;
        public final boolean glow, custom;
        /** Nicks (minusculas) que pueden usarlo si es privado; null si es global. */
        public final Set<String> owners;

        public Def(Type type, String id, String name, int color, boolean glow, String base, int frames, String scope, boolean custom, int ver, Set<String> owners) {
            this.type = type; this.id = id; this.name = name; this.color = color; this.glow = glow;
            this.base = base == null ? "" : base; this.frames = frames; this.scope = scope; this.custom = custom;
            this.ver = ver; this.owners = owners;
        }

        boolean isPrivate() { return "private".equals(scope); }
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
    // OJO: va antes del bloque static (load() lo usa al cargar)
    /** Lo que elegiste pero todavia no existe aqui (un cosmetico del catalogo que aun no se ha bajado): se aplica cuando llega. */
    private static final Map<Type, String> PENDING = new EnumMap<>(Type.class);

    static {
        addAnim(Type.CAPE, "atenas", "Atenas (Exclusiva)", 0xFFF5C542, true);
        add(Type.CAPE, "atardecer", "Atardecer Lunar", 0xFFFF7A59, false);
        addAnim(Type.CAPE, "aurora", "Aurora", 0xFF35E0A8, true);
        add(Type.CAPE, "sakura", "Sakura", 0xFFF8A9C8, false);
        add(Type.CAPE, "oceano", "Oc\u00e9ano al Atardecer", 0xFF1E7FD8, false);
        add(Type.CAPE, "bosque", "Bosque Brumoso", 0xFF4F8F72, false);
        addAnim(Type.CAPE, "galaxia", "Galaxia", 0xFF7B4DFF, true);
        addAnim(Type.CAPE, "lava", "Monta\u00f1a de Lava", 0xFFFF6A1A, true);
        add(Type.CAPE, "hielo", "Cristal de Hielo", 0xFF7FD6FF, false);
        addAnim(Type.CAPE, "llamas", "Llamas", 0xFFFF7A1A, true);
        addAnim(Type.CAPE, "matrix", "Matrix", 0xFF1BFF5A, true);
        addAnim(Type.CAPE, "pulso", "Pulso Synthwave", 0xFFFF2DA6, true);
        addAnim(Type.CAPE, "rey", "Corona Real", 0xFFF5C542, true);
        addAnim(Type.CAPE, "rayo", "Tormenta El\u00e9ctrica", 0xFFFFE23D, true);
        addAnim(Type.CAPE, "calavera", "Calavera Infernal", 0xFFE8503A, true);
        addAnim(Type.CAPE, "espadas", "Escudo de Guerra", 0xFF2457C5, true);
        addAnim(Type.CAPE, "luna", "Luna M\u00e1gica", 0xFFF4F1DE, true);
        addAnim(Type.CAPE, "corazon", "Coraz\u00f3n Latiendo", 0xFFFF4F8B, true);
        addAnim(Type.CAPE, "diamante", "Diamante Radiante", 0xFF5FD8FF, true);
        addAnim(Type.CAPE, "brujula", "Br\u00fajula M\u00edstica", 0xFFD4AF37, true);
        addAnim(Type.CAPE, "sol", "Sol Naciente", 0xFFFFB400, true);
        addAnim(Type.CAPE, "trebol", "Tr\u00e9bol Encantado", 0xFF2ECC71, true);
        addAnim(Type.CAPE, "arcoiris", "Arco\u00edris", 0xFF9B7BFF, true);
        addAnim(Type.CAPE, "mareas", "Mareas", 0xFF2E6FC4, true);
        addAnim(Type.CAPE, "eclipse", "Eclipse Solar", 0xFFFFB84D, true);
        addAnim(Type.CAPE, "tormenta", "Tormenta", 0xFF5B7CFA, true);
        addAnim(Type.CAPE, "nieve", "Ventisca", 0xFFBFE3FF, true);
        addAnim(Type.CAPE, "luciernagas", "Luci\u00e9rnagas", 0xFFB8FF4A, true);
        addAnim(Type.CAPE, "oroliquido", "Oro L\u00edquido", 0xFFFFC83D, true);
        addAnim(Type.CAPE, "cristal", "Cristal Prisma", 0xFF8FE9FF, true);
        addAnim(Type.CAPE, "portal", "Portal M\u00edstico", 0xFFB04DFF, true);
        addAnim(Type.CAPE, "dragon", "Drag\u00f3n", 0xFFD6202A, true);

        add(Type.WINGS, "doradas", "Alas Doradas", 0xFFF5C542, false);
        add(Type.WINGS, "oscuras", "Alas Oscuras", 0xFF8E2DE2, false);
        add(Type.WINGS, "angel", "Alas de \u00c1ngel", 0xFFEAF6FF, true);
        add(Type.WINGS, "atenas", "Alas Atenas", 0xFF8B5CF6, false);
        add(Type.WINGS, "fuego", "Alas de Fuego", 0xFFFF6A00, true);
        add(Type.WINGS, "hielo", "Alas de Hielo", 0xFF9ADCFF, false);
        add(Type.WINGS, "neon", "Alas de Ne\u00f3n", 0xFF22D3EE, true);
        add(Type.WINGS, "esmeralda", "Alas Esmeralda", 0xFF2ECC71, false);
        add(Type.WINGS, "ambar", "Alas \u00c1mbar", 0xFFFF7814, true);
        add(Type.WINGS, "sakura", "Alas Sakura", 0xFFFFAACD, false);
        add(Type.WINGS, "aurora", "Alas Aurora", 0xFF28E6AA, true);
        add(Type.WINGS, "lunar", "Alas Lunares", 0xFF7896E6, false);
        add(Type.WINGS, "demonio", "Alas de Demonio", 0xFFC0182A, false);
        // Alas de membrana (otra malla: MembraneWing; textura 128x128 de tools/gen_membrane.py)
        addMembrane("murcielago", "Alas de Murci\u00e9lago", 0xFF7860A8);
        addMembrane("vampiro", "Alas de Vampiro", 0xFFAA1E34);
        addMembrane("dragon", "Alas de Drag\u00f3n", 0xFFFF8C28);
        addMembrane("abismo", "Alas del Abismo", 0xFF14AABE);
        addMembrane("fantasma", "Alas Fantasma", 0xFFEBFAFF);
        addMembrane("jade", "Alas de Jade", 0xFF5AD796);
        addMembrane("solar", "Alas Solares", 0xFFFFE06E);
        addMembrane("ceniza", "Alas de Ceniza", 0xFF78767E);
        addMembrane("cristal", "Alas de Cristal", 0xFFC8F5FF);
        addMembrane("nebulosa", "Alas Nebulosa", 0xFF9646DC);
        addMembrane("mariposa", "Alas de Mariposa", 0xFFFF9A1E);
        addMembrane("hada", "Alas de Hada", 0xFFFFB6E6);
        addMembrane("fenix", "Alas de F\u00e9nix", 0xFFFF7A14);
        addMembrane("tormenta", "Alas de Tormenta", 0xFF5078E6);
        addMembrane("cibernetico", "Alas Cibern\u00e9ticas", 0xFF00DCFF);
        addMembrane("esqueleto", "Alas de Esqueleto", 0xFFDDD6C4);

        add(Type.HAT, "halo", "Halo", 0xFFFFE680, true);
        add(Type.HAT, "corona", "Corona", 0xFFF5C542, false);
        add(Type.HAT, "galera", "Galera", 0xFF3A3A4E, false);
        add(Type.HAT, "gato", "Orejas de Gato", 0xFF2A2A34, false);
        add(Type.HAT, "cuernos", "Cuernos", 0xFFC0182A, false);
        add(Type.HAT, "mago", "Sombrero de Mago", 0xFF5B2FA8, false);
        add(Type.HAT, "auriculares", "Auriculares", 0xFF28283A, false);
        add(Type.HAT, "conejo", "Orejas de Conejo", 0xFFF2F2F7, false);
        add(Type.HAT, "gorra", "Gorra", 0xFF2447B8, false);
        add(Type.HAT, "pirata", "Sombrero Pirata", 0xFF17171D, false);
        add(Type.HAT, "vikingo", "Casco Vikingo", 0xFFA9B0BF, false);
        add(Type.HAT, "laurel", "Corona de Laurel", 0xFFE8B84A, false);
        // Mascotas (el modelo de cada id sale de pets/models.json)
        add(Type.PET, "perro", "Perro", 0xFFCD9654, false);
        add(Type.PET, "husky", "Husky", 0xFF68707E, false);
        add(Type.PET, "dalmata", "D\u00e1lmata", 0xFFF4F4F6, false);
        add(Type.PET, "gato", "Gato", 0xFFE28E3E, false);
        add(Type.PET, "gatonegro", "Gato negro", 0xFF24242E, false);
        add(Type.PET, "zorro", "Zorro", 0xFFE87028, false);
        add(Type.PET, "catalina", "Catalina", 0xFFF2F0EE, false);
        add(Type.PET, "conejo", "Conejo", 0xFFCEBCA8, false);
        add(Type.PET, "panda", "Panda", 0xFFF4F4F2, false);
        add(Type.PET, "loro", "Loro", 0xFFD82626, false);
        add(Type.PET, "buho", "B\u00faho", 0xFF8A6848, false);
        add(Type.PET, "dragoncito", "Dragoncito", 0xFF42A86C, false);
        // Catalina es privada: hasta que el catalogo diga que tu nick esta en su lista, no aparece ni se puede elegir.
        find(Type.PET, "catalina").locked = true;
        load();
    }

    private Cosmetics() {}

    private static void add(Type t, String id, String name, int color, boolean glow) { ALL.add(new Item(t, id, name, color, glow, 1)); }

    private static void addAnim(Type t, String id, String name, int color, boolean glow) { ALL.add(new Item(t, id, name, color, glow, ANIM_FRAMES)); }

    private static void addMembrane(String id, String name, int color) {
        boolean glow = id.equals("fenix") || id.equals("cibernetico") || id.equals("tormenta") || id.equals("hada") || id.equals("abismo") || id.equals("solar");
        ALL.add(new Item(Type.WINGS, id, name, color, glow, 1, false, null, true)); // las que brillan se ven a plena luz
    }

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
        if (i != null && i.locked) return; // privado y no es tuyo
        PENDING.remove(t);
        if (i == null) SELECTED.remove(t); else SELECTED.put(t, i);
        save();
        CosmeticSync.markDirty();
    }

    // ------------------------------------------------------------------ seleccion pendiente

    /** Id pendiente de este tipo (o null): se conserva en el archivo y no se sube como "none" mientras baja el catalogo. */
    public static String pendingId(Type t) { return PENDING.get(t); }

    /** Mensaje corto para el menu (por ejemplo "no puedes llevar X"). Se apaga solo. */
    public static volatile String notice = "";
    public static volatile long noticeUntil;

    static void notice(String msg) { notice = msg; noticeUntil = System.currentTimeMillis() + 8000L; }

    /** El servicio rechazo un cosmetico (privado y tu nick no esta en la lista): se quita de tu seleccion y se avisa. Hilo principal. */
    public static void denied(Type t, String id) {
        Item cur = SELECTED.get(t);
        if (cur != null && cur.id.equals(id)) SELECTED.remove(t);
        if (id != null && id.equals(PENDING.get(t))) PENDING.remove(t);
        Item it = find(t, id);
        if (it != null) it.locked = true;
        String nm = it != null ? it.name : id;
        notice(com.atenas.social.SocialClient.verified()
            ? "No puedes llevar \"" + nm + "\": es privado."
            : nm + " es privada: no se pudo verificar tu cuenta (ver latest.log)");
        save();
    }

    /** Cosmeticos de un tipo que puedes elegir (los privados ajenos no salen). */
    public static List<Item> visible(Type t) {
        List<Item> out = new ArrayList<>();
        for (Item i : ALL) if (i.type == t && !i.locked) out.add(i);
        return out;
    }

    /** Aplica lo que el servicio tiene para TI (otra version del juego lo cambio). No lo vuelve a subir. */
    public static void applyRemote(Type t, String id) {
        if (id == null) return;
        Item cur = SELECTED.get(t);
        if ("none".equals(id)) {
            PENDING.remove(t);
            if (cur != null) { SELECTED.remove(t); save(); }
            return;
        }
        Item next = find(t, id);
        if (next == null || (next.locked && !catalogApplied)) { // aun no tenemos el catalogo: se aplicara cuando llegue
            if (!id.equals(PENDING.get(t))) { PENDING.put(t, id); save(); }
            return;
        }
        if (next.locked || next == cur) return;
        PENDING.remove(t);
        SELECTED.put(t, next);
        save();
    }

    // ------------------------------------------------------------------ catalogo del servicio

    /** True cuando ya se aplico un catalogo (del disco o del servicio). Antes de eso los cosmeticos privados incorporados siguen ocultos. */
    private static boolean catalogApplied;

    public static boolean catalogApplied() { return catalogApplied; }

    /**
     * Aplica el catalogo del servicio. HILO PRINCIPAL.
     *  - reglas sobre cosmeticos incorporados: privado => solo los nicks de "owners" lo ven y lo eligen;
     *  - cosmeticos nuevos (id x_...): se crean/actualizan (con la forma de su "base") y se piden sus texturas;
     *  - los que ya no estan en el catalogo se quitan (y si lo llevabas, se desequipa).
     * Despues resuelve las elecciones pendientes.
     */
    public static void applyCatalog(List<Def> defs) {
        String me = SocialClient.myName();
        me = me == null ? "" : me.toLowerCase(Locale.ROOT);
        Map<String, Def> byKey = new HashMap<>();
        for (Def d : defs) byKey.put(d.type.key() + ":" + d.id, d);

        // 1) cosmeticos incorporados: bloqueo segun la regla
        for (Item i : ALL) {
            if (i.custom) continue;
            Def d = byKey.get(i.key());
            i.locked = d != null && d.isPrivate() && !(d.owners != null && d.owners.contains(me));
        }
        // 2) cosmeticos del catalogo: quitar los que ya no estan, crear/actualizar el resto
        for (Iterator<Item> it = ALL.iterator(); it.hasNext();) {
            Item i = it.next();
            if (!i.custom || byKey.containsKey(i.key())) continue;
            it.remove();
            if (SELECTED.get(i.type) == i) SELECTED.remove(i.type);
        }
        for (Def d : defs) {
            if (!d.custom) continue;
            Item base = d.base.length() > 0 ? find(d.type, d.base) : null;
            Item cur = find(d.type, d.id);
            boolean membrane = base != null && base.membrane;
            if (cur == null || cur.glow != d.glow || cur.membrane != membrane || !cur.name.equals(d.name) || cur.color != d.color || !cur.base.equals(d.base)) {
                Item n = new Item(d.type, d.id, d.name, d.color, d.glow, d.type == Type.CAPE ? Math.max(1, d.frames) : 1, true, d.base, membrane);
                if (base != null) { n.texture = base.texture; n.icon = base.icon; n.openMul = base.openMul; }
                if (cur != null) {
                    ALL.set(ALL.indexOf(cur), n);
                    if (SELECTED.get(d.type) == cur) SELECTED.put(d.type, n);
                } else {
                    ALL.add(n);
                }
                cur = n;
            }
            cur.frames = d.type == Type.CAPE ? Math.max(1, d.frames) : 1;
            cur.wantVer = d.ver;
            cur.locked = d.isPrivate() && !(d.owners != null && d.owners.contains(me));
            CosmeticAssets.request(cur);
        }
        // 3) lo que llevas puesto pero ya no puedes llevar se quita (y se sincroniza)
        boolean dropped = false;
        for (Type t : Type.values()) {
            Item c = SELECTED.get(t);
            if (c != null && c.locked) { SELECTED.remove(t); dropped = true; notice("No puedes llevar \"" + c.name + "\": es privado."); }
        }
        // 4) elecciones pendientes que ya existen (las privadas que no son tuyas se descartan)
        boolean resolved = false;
        for (Iterator<Map.Entry<Type, String>> it = PENDING.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Type, String> e = it.next();
            final Type pt = e.getKey();
            Item f = find(pt, e.getValue());
            if (f == null) continue; // sigue sin existir (se queda pendiente)
            it.remove(); // OJO: despues de remove() la entrada del EnumMap ya no se puede leer (por eso se guardo la clave antes)
            resolved = true;
            if (!f.locked && SELECTED.get(pt) == null) SELECTED.put(pt, f);
        }
        catalogApplied = true;
        CosmeticSync.touch(); // los demas jugadores se vuelven a resolver con el catalogo nuevo
        if (dropped) CosmeticSync.markDirty();
        if (dropped || resolved) save();
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
                if (!o.has(t.key())) continue;
                String id = o.get(t.key()).getAsString();
                if (id == null || id.isEmpty() || "none".equals(id)) continue;
                Item it = find(t, id);
                if (it != null && !it.locked) SELECTED.put(t, it);
                else PENDING.put(t, id); // del catalogo (aun no bajado) o privado hasta que el catalogo confirme que es tuyo
            }
            if (o.has("animate")) animate = o.get("animate").getAsBoolean();
        } catch (Exception e) { System.err.println("[AtenasSocial] cosm\u00e9ticos: " + e); }
    }

    public static void toggleAnimate() { animate = !animate; save(); }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            for (Type t : Type.values()) o.addProperty(t.key(), SELECTED.containsKey(t) ? SELECTED.get(t).id : PENDING.containsKey(t) ? PENDING.get(t) : "none");
            o.addProperty("animate", animate);
            Files.write(file(), o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) { System.err.println("[AtenasSocial] guardar cosm\u00e9ticos: " + e.getMessage()); }
    }
}
