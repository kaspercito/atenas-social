package com.atenas.social;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import net.minecraft.client.Minecraft;
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
    private static long retryAt;

    private OwnSkin() {}

    /** La skin real (en una partida, la del jugador), o null si todavia no llego o la cuenta no tiene (sin conexion). */
    static ResourceLocation get(Minecraft mc) {
        if (mc.player != null) return mc.player.getSkinTextureLocation();
        ResourceLocation s = skin;
        if (s != null) return s;
        long now = System.currentTimeMillis();
        if (!requested && now >= retryAt && mc.getUser() != null) {
            requested = true;
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
