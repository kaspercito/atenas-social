package com.atenas.social;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * La skin REAL de la cuenta con la que juegas, para dibujar tu cabeza en el menu (donde todavia no hay jugador).
 * <p>
 * {@code getInsecureSkinLocation(getUser().getGameProfile())} NO sirve: ese perfil viene sin las propiedades de textura, asi que
 * siempre devuelve el Steve/Alex por defecto. {@code registerSkins} si completa el perfil de la cuenta logeada (es la ruta del propio juego)
 * y avisa cuando la skin ya esta descargada. Solo el hilo principal llama a {@link #get}.
 */
final class OwnSkin {
    private static volatile ResourceLocation skin;
    private static boolean requested;
    private static long retryAt, firstAsk;

    private OwnSkin() {}

    /** La skin real (en una partida, la del jugador), o null si todavia no llego o la cuenta no tiene (sin conexion). */
    static ResourceLocation get(Minecraft mc) {
        if (mc.player != null) return mc.player.getSkinTextureLocation();
        ResourceLocation s = skin;
        if (s != null) return s;
        long now = System.currentTimeMillis();
        if (mc.getUser() != null) {
            String type = String.valueOf(mc.getUser().getType()).toLowerCase(java.util.Locale.ROOT);
            // cuenta sin conexion: no hay skin que bajar, se usa la Steve/Alex de esa cuenta; premium: solo si Mojang tarda demasiado
            boolean premium = type.equals("msa") || type.equals("mojang");
            if (!premium || (firstAsk > 0 && now - firstAsk > 12000L)) return DefaultPlayerSkin.getDefaultSkin(mc.getUser().getProfileId());
        }
        if (!requested && now >= retryAt && mc.getUser() != null) {
            requested = true;
            if (firstAsk == 0) firstAsk = now;
            try {
                mc.getSkinManager().registerSkins(mc.getUser().getGameProfile(), (type, loc, tex) -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) skin = loc;
                }, false);
            } catch (RuntimeException e) {
                requested = false;
                retryAt = now + 15000L; // reintenta en 15 s
                System.err.println("[AtenasSocial] skin propia: " + e);
            }
        }
        return null;
    }
}
