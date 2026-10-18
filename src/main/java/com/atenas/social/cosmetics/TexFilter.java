package com.atenas.social.cosmetics;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Minecraft carga las texturas de entidades con filtro NEAREST y sin mipmaps: una textura HD se ve granulada y titila de lejos.
 * Aqui se pone filtro suave + mipmaps UNA vez por textura (igual que TexFilter del cliente 1.8.9). maxLevel limita los mipmaps
 * (las capas animadas son tiras de fotogramas: pocos niveles para que no se mezclen entre fotogramas); -1 = cadena completa.
 * Llamar desde el hilo de dibujo, justo antes de usar la textura.
 */
public final class TexFilter {
    private static final Set<ResourceLocation> DONE = Collections.newSetFromMap(new WeakHashMap<>());

    private TexFilter() {}

    public static void prepare(ResourceLocation loc, int maxLevel) {
        if (loc == null || DONE.contains(loc)) return;
        DONE.add(loc);
        try {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(loc);
            t.bind();
            GlStateManager._texParameter(3553, 10240, 9729);   // MAG_FILTER = LINEAR
            GlStateManager._texParameter(3553, 10241, 9987);   // MIN_FILTER = LINEAR_MIPMAP_LINEAR
            if (maxLevel >= 0) GlStateManager._texParameter(3553, 33085, maxLevel); // TEXTURE_MAX_LEVEL
            GlStateManager._glGenerateMipmap(3553);
        } catch (Throwable e) {
            System.err.println("[AtenasSocial] filtro de textura: " + e);
        }
    }
}
