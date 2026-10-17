package com.atenas.social;

import net.minecraft.client.Minecraft;

/** Idioma del mod = idioma del juego: español si el juego está en español (es_es, es_mx, es_ec...), inglés con cualquier otro. */
public final class Lang {
    private Lang() {}

    public static boolean es() {
        Minecraft mc = Minecraft.getInstance();
        String code = mc == null || mc.options == null ? null : mc.options.languageCode;
        return code == null || code.toLowerCase(java.util.Locale.ROOT).startsWith("es");
    }

    /** Texto según el idioma, escrito en el sitio: t("Un jugador", "Singleplayer"). */
    public static String t(String es, String en) { return es() ? es : en; }
}
