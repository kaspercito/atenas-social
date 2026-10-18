package com.atenas.social.cosmetics;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vista previa de los cosmeticos SIN mundo (menu principal): un maniqui hecho solo con el modelo del jugador (no hay entidad ni
 * ClientLevel, asi que no depende de que haya partida abierta). Gira solo para que se vean la capa y las alas, que van a la espalda.
 * Usa las mismas mallas y la misma geometria que las capas del juego ({@link CosmeticRender}); lo que cambia es que aqui el
 * movimiento es fijo (un balanceo suave). Con partida abierta el menu sigue usando al jugador real.
 */
final class CosmeticPreview {
    private static PlayerModel<AbstractClientPlayer> modelDefault, modelSlim;
    private static final Map<String, ModelPart> HATS = new HashMap<>();
    private static ModelPart hatRoot;
    private static final CapeMesh CAPE = new CapeMesh();
    private static final WingMesh WINGS = new WingMesh();
    private static final WingPlane PLANE = new WingPlane();
    private static final CosmeticRender.VertexSink SINK = new CosmeticRender.VertexSink();

    private static volatile ResourceLocation skin;
    private static boolean skinRequested;
    private static volatile boolean slim;

    private CosmeticPreview() {}

    private static void resolve(Minecraft mc) {
        if (modelDefault == null) {
            modelDefault = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            modelSlim = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        if (skin == null) {
            // mientras llega tu skin real se usa el aspecto por defecto de la cuenta (Steve / Alex)
            UUID id = mc.getUser() != null && mc.getUser().getProfileId() != null ? mc.getUser().getProfileId() : UUID.randomUUID();
            skin = DefaultPlayerSkin.getDefaultSkin(id);
            slim = "slim".equals(DefaultPlayerSkin.getSkinModelName(id));
        }
        // En una partida: la skin del jugador tal cual.
        if (mc.player != null) {
            skin = mc.player.getSkinTextureLocation();
            slim = "slim".equals(mc.player.getModelName());
        } else if (!skinRequested && mc.getUser() != null) {
            // En el menu no hay jugador: se pide tu skin real a Mojang (en segundo plano) con la misma ruta que usa el juego.
            skinRequested = true;
            try {
                mc.getSkinManager().registerSkins(mc.getUser().getGameProfile(), (type, loc, tex) -> {
                    if (type == com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN) {
                        skin = loc;
                        slim = "slim".equals(tex.getMetadata("model"));
                    }
                }, false);
            } catch (RuntimeException e) {
                System.err.println("[AtenasSocial] skin: " + e);
            }
        }
        if (hatRoot == null) hatRoot = mc.getEntityModels().bakeLayer(CosmeticRender.HATS);
    }

    private static ModelPart hat(String id) {
        ModelPart p = HATS.get(id);
        if (p == null) {
            try { p = hatRoot.getChild(id); } catch (RuntimeException e) { return null; }
            HATS.put(id, p);
        }
        return p;
    }

    /**
     * Dibuja el maniqui con lo que tienes elegido. (cx, feetY) = pies en pantalla; scale = pixeles por bloque (vanilla usa 42 en el menu).
     * Lanza excepcion si algo del juego no coopera: quien llama debe atraparla y mostrar el texto de respaldo.
     */
    static void draw(GuiGraphics g, int cx, int feetY, int scale) {
        Minecraft mc = Minecraft.getInstance();
        resolve(mc);
        PlayerModel<AbstractClientPlayer> m = slim ? modelSlim : modelDefault;
        long ms = Util.getMillis();
        float age = ms / 50f;                           // "ageInTicks"
        float spin = (ms % 9000L) / 9000f * 360f;       // una vuelta cada 9 s

        // El modelo nace con young = true (version "bebe": cuerpo a la mitad, anclado a los pies, cabeza mas chica). El juego lo apaga al
        // dibujar un jugador de verdad; aqui dibujamos el modelo a mano, asi que hay que apagarlo para que tenga la altura normal.
        m.young = false;

        // pose suave: brazos que se balancean un poco
        float sw = (float) Math.sin(age * 0.07f) * 0.06f;
        m.head.xRot = 0f; m.head.yRot = 0f; m.head.zRot = 0f;
        m.rightArm.xRot = sw; m.leftArm.xRot = -sw;
        m.rightArm.zRot = 0.05f; m.leftArm.zRot = -0.05f;
        m.hat.copyFrom(m.head);
        m.leftSleeve.copyFrom(m.leftArm); m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg); m.rightPants.copyFrom(m.rightLeg);
        m.jacket.copyFrom(m.body);

        PoseStack ps = g.pose();
        MultiBufferSource.BufferSource buf = g.bufferSource();
        int light = LightTexture.FULL_BRIGHT;

        ps.pushPose();
        ps.translate((double) cx, (double) feetY, 50.0);
        ps.mulPoseMatrix(new Matrix4f().scaling((float) scale, (float) scale, (float) -scale));
        ps.mulPose(Axis.ZP.rotationDegrees(180f));
        Lighting.setupForEntityInInventory();

        // mascota: a un lado, en el marco "y hacia arriba" (igual que PetRender)
        Cosmetics.Item pet = Cosmetics.selected(Cosmetics.Type.PET);
        if (pet != null) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(spin));
            ps.translate(0.95, 0.0, 0.0);
            PetRender.renderPreview(ps, buf, pet, light);
            ps.popPose();
        }

        // jugador: mismo marco que LivingEntityRenderer (giro, escala -1,-1,1 y pies en y = 1.501)
        ps.mulPose(Axis.YP.rotationDegrees(spin));
        ps.scale(-1f, -1f, 1f);
        ps.translate(0f, -1.501f, 0f);

        VertexConsumer body = buf.getBuffer(RenderType.entityCutoutNoCull(skin));
        m.renderToBuffer(ps, body, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);

        Cosmetics.Item cape = Cosmetics.selected(Cosmetics.Type.CAPE);
        Cosmetics.Item wings = Cosmetics.selected(Cosmetics.Type.WINGS);
        Cosmetics.Item hat = Cosmetics.selected(Cosmetics.Type.HAT);

        if (cape != null) drawCape(ps, buf, cape, age, light);
        if (wings != null) drawWings(ps, buf, m, wings, cape != null, age, light);
        if (hat != null && !hat.custom) {
            HatMesh mesh = HatMesh.get(hat.id);
            if (mesh != null) {
                ps.pushPose();
                m.head.translateAndRotate(ps);
                mesh.draw(ps, buf, hat, light, age);
                ps.popPose();
            }
        } else if (hat != null) {
            ModelPart part = hat(hat.modelId());
            if (part != null) {
                ps.pushPose();
                m.head.translateAndRotate(ps);
                if (hat.modelId().equals("halo")) ps.translate(0.0, Math.sin(age * 0.08f) * 0.025, 0.0);
                part.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(hat.texture)), hat.glow ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
        }

        g.flush();
        ps.popPose();
        Lighting.setupFor3DItems();
    }

    /** Igual que CosmeticRender.CapeLayer con el jugador quieto (un balanceo suave en lugar de la fisica real). */
    private static void drawCape(PoseStack ps, MultiBufferSource buf, Cosmetics.Item item, float age, int light) {
        ps.pushPose();
        ps.translate(0.0, 0.0, 0.135);
        float swing = 0f; // vista previa: la capa cuelga quieta
        float sway = 0f;
        float tilt = swing / 2f;
        ps.mulPose(Axis.XP.rotationDegrees(6.0f + tilt * 0.35f));
        ps.mulPose(Axis.ZP.rotationDegrees(sway / 4.0f));
        ps.mulPose(Axis.YP.rotationDegrees(180.0f - sway / 2.0f));
        boolean anim = item.frames > 1;
        int frames = anim ? item.frames : 1;
        int frame = anim ? Cosmetics.frameOf(item) % frames : 0;
        TexFilter.prepare(item.texture, anim ? 3 : -1);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(item.texture));
        SINK.begin(vc, ps.last(), item.glow ? LightTexture.FULL_BRIGHT : light);
        CAPE.emit(SINK, frame, frames, 0.0625F, (float) Math.toRadians(tilt * 0.65f), CapeWave.ampFor(swing), age, (float) Math.toRadians(sway / 4.0f));
        SINK.vc = null;
        ps.popPose();
    }

    /** Igual que CosmeticRender.WingsLayer en el suelo (respiro leve), con las dos mallas (plumas y membrana). */
    private static void drawWings(PoseStack ps, MultiBufferSource buf, PlayerModel<AbstractClientPlayer> m, Cosmetics.Item item, boolean cape, float age, int light) {
        float phase = age * 0.35f;
        WingPlane.Style ws = WingPlane.styleOf(item.modelId());
        float open = 0.85f + (float) Math.sin(phase * 0.2f) * 0.05f;
        if (item.membrane) open = (open + ws.idle * (float) Math.sin(phase * ws.idleFreq)) * ws.open;
        if (cape) open *= 0.80f;
        open *= item.openMul;
        float outset = cape ? 1.2f : 0f, zoff = cape ? 2.0f : 0f;
        WINGS.outset = outset; WINGS.zoff = zoff;
        PLANE.outset = outset; PLANE.zoff = zoff;
        float flap = 0.05f;

        ps.pushPose();
        m.body.translateAndRotate(ps);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(item.texture));
        SINK.begin(vc, ps.last(), item.glow ? LightTexture.FULL_BRIGHT : light);
        if (item.membrane) PLANE.emit(SINK, open, phase * ws.speed, flap * ws.amp, true, WingPlane.MAX_N, 0.0625F);
        else WINGS.emit(SINK, open, phase, flap, WingMesh.MAX_SEGS, true, 0.0625F);
        SINK.vc = null;
        ps.popPose();
    }
}
