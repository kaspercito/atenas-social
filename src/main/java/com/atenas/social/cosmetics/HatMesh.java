package com.atenas.social.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Sombrero como malla 3D (formato .atm generado por tools/gen_hats.py del cliente 1.8.9; son los MISMOS archivos):
 * grupos de quads con UV y normales suaves. Flags de cada grupo: 1 emisivo (aditivo, sin luz), 2 pulsa, 4 gira sobre Y, 8 brillo suave.
 * Formato (big endian): 'ATM1', int grupos; por grupo: UTF nombre, byte flags, int quads, 32 floats por quad (4 vertices x: x y z u v nx ny nz).
 * Las coordenadas ya vienen en el marco de ModelPart (y hacia abajo, cabeza de y=-8 a 0), asi que se dibujan tal cual desde el pivote de la cabeza.
 */
public final class HatMesh {
    public static final int EMIT = 1, PULSE = 2, SPIN = 4, SOFT = 8;
    private static final Map<String, HatMesh> CACHE = new HashMap<>();
    private static final HatMesh NONE = new HatMesh(new float[0][], new int[0]);
    /** Los sombreros de otros jugadores solo se dibujan a menos de 24 bloques (cada malla trae miles de vertices). */
    public static final double MAX_DIST_SQ = 24.0 * 24.0;

    /** Por grupo: 32 floats por quad, posicion ya en bloques (x 1/16). */
    private final float[][] quads;
    public final int[] flags;
    private final boolean anyEmit;

    private HatMesh(float[][] quads, int[] flags) {
        this.quads = quads;
        this.flags = flags;
        boolean e = false;
        for (int f : flags) if ((f & EMIT) != 0) e = true;
        this.anyEmit = e;
    }

    /** Malla del sombrero (o null si no existe o no se pudo leer). HILO PRINCIPAL. */
    public static HatMesh get(String id) {
        HatMesh m = CACHE.get(id);
        if (m == null) {
            try {
                m = load(id);
            } catch (Throwable t) {
                System.err.println("[AtenasSocial] malla de sombrero " + id + ": " + t);
                m = NONE;
            }
            CACHE.put(id, m);
        }
        return m == NONE ? null : m;
    }

    private static HatMesh load(String id) throws Exception {
        Resource res = Minecraft.getInstance().getResourceManager()
            .getResource(new ResourceLocation(Cosmetics.MODID, "models/hats/" + id + ".atm"))
            .orElseThrow(() -> new java.io.FileNotFoundException("models/hats/" + id + ".atm"));
        try (InputStream raw = res.open(); DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1 << 16))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'A' || magic[1] != 'T' || magic[2] != 'M' || magic[3] != '1') throw new Exception("formato");
            int groups = in.readInt();
            float[][] quads = new float[groups][];
            int[] flags = new int[groups];
            final float s = 0.0625f;
            for (int g = 0; g < groups; g++) {
                in.readUTF();
                flags[g] = in.readByte();
                int n = in.readInt();
                float[] f = new float[n * 32];
                for (int i = 0; i < f.length; i++) f[i] = in.readFloat();
                // posiciones a bloques una sola vez (x y z de cada vertice = indices 0..2 de cada bloque de 8)
                for (int v = 0; v < n * 4; v++) {
                    f[v * 8] *= s;
                    f[v * 8 + 1] *= s;
                    f[v * 8 + 2] *= s;
                }
                quads[g] = f;
            }
            return new HatMesh(quads, flags);
        }
    }

    private void emit(int g, VertexConsumer vc, PoseStack.Pose pose, int light, float alpha) {
        float[] f = quads[g];
        for (int o = 0; o < f.length; o += 8) {
            vc.vertex(pose.pose(), f[o], f[o + 1], f[o + 2]).color(1f, 1f, 1f, alpha).uv(f[o + 3], f[o + 4])
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), f[o + 5], f[o + 6], f[o + 7]).endVertex();
        }
    }

    /**
     * Dibuja el sombrero. {@code ps} ya esta en el pivote de la cabeza. Piezas normales con luz del entorno; las que brillan van aparte,
     * sin luz, aditivas y a pleno brillo (pulso / giro / suave segun sus flags).
     */
    public void draw(PoseStack ps, MultiBufferSource buf, Cosmetics.Item item, int light, float age) {
        ps.pushPose();
        if ("halo".equals(item.id)) ps.translate(0.0D, Math.sin(age * 0.1f) * 0.02D, 0.0D);
        TexFilter.prepare(item.texture, -1);

        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(item.texture));
        for (int g = 0; g < quads.length; g++) {
            if ((flags[g] & EMIT) == 0) emit(g, vc, ps.last(), light, 1f);
        }
        if (anyEmit) {
            VertexConsumer glow = buf.getBuffer(AtenasTypes.glow(item.texture));
            for (int g = 0; g < quads.length; g++) {
                int fl = flags[g];
                if ((fl & EMIT) == 0) continue;
                float a = 1f;
                if ((fl & PULSE) != 0) a = 0.62f + 0.38f * (float) Math.sin(age * 0.13f + g * 1.7f);
                if ((fl & SOFT) != 0) a *= 0.7f;
                if ((fl & SPIN) != 0) {
                    ps.pushPose();
                    ps.mulPose(Axis.YP.rotationDegrees(age * 1.6f));
                    emit(g, glow, ps.last(), LightTexture.FULL_BRIGHT, a);
                    ps.popPose();
                } else {
                    emit(g, glow, ps.last(), LightTexture.FULL_BRIGHT, a);
                }
            }
        }
        ps.popPose();
    }
}
