package com.atenas.social;

import com.atenas.social.cosmetics.Cosmetics;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Caras (cabezas) de los jugadores para los avisos. Se bajan una sola vez por nick, en un hilo aparte, de la misma página que usa
 * el launcher (mc-heads.net). Mientras llegan, o si no hay conexión, el aviso muestra la inicial del nick. Solo el hilo principal
 * llama a {@link #get}.
 */
final class Faces {
    private static final int SIZE = 40;

    /** Textura ya lista y su tamaño real en píxeles. */
    static final class Face {
        final ResourceLocation tex;
        final int w, h;
        Face(ResourceLocation tex, int w, int h) { this.tex = tex; this.w = w; this.h = h; }
    }

    private static final Map<String, Face> READY = new HashMap<>();
    private static final Set<String> BUSY = new HashSet<>();

    private Faces() {}

    /** La cara, o null si todavía no llegó (o no se pudo bajar). */
    static Face get(String name) {
        if (name == null || !SocialClient.validName(name)) return null;
        final String key = name.toLowerCase(Locale.ROOT);
        Face f = READY.get(key);
        if (f != null || BUSY.contains(key)) return f;
        BUSY.add(key); // se intenta una vez por sesión: si falla, queda la inicial
        Thread t = new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://mc-heads.net/avatar/" + name + "/" + SIZE).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(6000);
                c.setRequestProperty("User-Agent", "AtenasSocial/1.20.1");
                final NativeImage img;
                try (InputStream in = c.getInputStream()) { img = NativeImage.read(in); }
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    ResourceLocation rl = new ResourceLocation(Cosmetics.MODID, "face/" + key);
                    mc.getTextureManager().register(rl, new DynamicTexture(img));
                    READY.put(key, new Face(rl, img.getWidth(), img.getHeight()));
                });
            } catch (Exception ignored) { /* sin cara: se ve la inicial */ }
        }, "Atenas-Face");
        t.setDaemon(true);
        t.start();
        return null;
    }
}
