package com.atenas.social.cosmetics;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Tipos de dibujo propios. Extiende RenderType solo para poder usar sus piezas protegidas (RenderStateShard).
 * glow = lo mismo que el brillo de los ojos de la arana (sin luz, aditivo, sin escribir profundidad) pero SIN culling:
 * las piezas que brillan son planos de una sola cara y se tienen que ver desde cualquier lado (como en el cliente 1.8.9).
 */
final class AtenasTypes extends RenderType {
    private AtenasTypes(String n, VertexFormat f, VertexFormat.Mode m, int size, boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(n, f, m, size, crumbling, sort, setup, clear);
    }

    private static final Function<ResourceLocation, RenderType> GLOW = Util.memoize(tex -> RenderType.create(
        "atenas_glow",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        256,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_EYES_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
            .setTransparencyState(ADDITIVE_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setWriteMaskState(COLOR_WRITE)
            .createCompositeState(false)));

    static RenderType glow(ResourceLocation tex) { return GLOW.apply(tex); }
}
