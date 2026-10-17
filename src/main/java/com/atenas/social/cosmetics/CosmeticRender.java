package com.atenas.social.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.util.HashMap;
import java.util.Map;

/** Modelos y capas de dibujo de los cosméticos (capa, alas y sombreros). */
public final class CosmeticRender {
    public static final ModelLayerLocation CAPE = loc("cape");
    public static final ModelLayerLocation WINGS = loc("wings");
    public static final ModelLayerLocation HATS = loc("hats");

    private CosmeticRender() {}

    private static ModelLayerLocation loc(String n) { return new ModelLayerLocation(new ResourceLocation(Cosmetics.MODID, n), "main"); }

    // ------------------------------------------------------------------ registro
    public static void registerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(CAPE, CosmeticRender::capeDef);
        e.registerLayerDefinition(WINGS, CosmeticRender::wingsDef);
        e.registerLayerDefinition(HATS, CosmeticRender::hatsDef);
    }

    public static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (String skin : new String[] {"default", "slim"}) {
            PlayerRenderer r = e.getSkin(skin);
            if (r == null) continue;
            r.addLayer(new CapeLayer(r, e.getEntityModels()));
            r.addLayer(new WingsLayer(r, e.getEntityModels()));
            r.addLayer(new HatLayer(r, e.getEntityModels()));
        }
    }

    // ------------------------------------------------------------------ definiciones
    /** Textura 64x32 lógica (el PNG puede ser más grande: las coordenadas son proporcionales). */
    private static LayerDefinition capeDef() {
        MeshDefinition m = new MeshDefinition();
        m.getRoot().addOrReplaceChild("cape", CubeListBuilder.create().texOffs(0, 0).addBox(-5f, 0f, -1f, 10f, 16f, 1f), PartPose.ZERO);
        return LayerDefinition.create(m, 64, 32);
    }

    /** Cada ala = 7 plumas en abanico. Pluma i usa la franja v = 4*i de la textura 64x64. */
    static final float[] FEATHER_LEN = {18, 17, 16, 14, 12, 10, 8};

    private static LayerDefinition wingsDef() {
        MeshDefinition m = new MeshDefinition();
        PartDefinition root = m.getRoot();
        PartDefinition right = root.addOrReplaceChild("wing_r", CubeListBuilder.create(), PartPose.offset(-1f, 1f, 2.2f));
        PartDefinition left = root.addOrReplaceChild("wing_l", CubeListBuilder.create(), PartPose.offset(1f, 1f, 2.2f));
        for (int i = 0; i < FEATHER_LEN.length; i++) {
            float len = FEATHER_LEN[i];
            float ang = 0.30f - i * 0.17f;
            float z = i * 0.12f; // cada pluma un poco más atrás: sin parpadeo entre capas
            right.addOrReplaceChild("f" + i, CubeListBuilder.create().texOffs(0, 4 * i).addBox(-len, -1.5f, 0f, len, 3f, 1f),
                PartPose.offsetAndRotation(0f, 0f, z, 0f, 0f, ang));
            left.addOrReplaceChild("f" + i, CubeListBuilder.create().texOffs(0, 4 * i).mirror().addBox(0f, -1.5f, 0f, len, 3f, 1f),
                PartPose.offsetAndRotation(0f, 0f, z, 0f, 0f, -ang));
        }
        return LayerDefinition.create(m, 64, 64);
    }

    private static void hat(PartDefinition root, String id, CubeListBuilder b) { root.addOrReplaceChild(id, b, PartPose.ZERO); }

    /**
     * Sombreros con cajas. Origen = pivote de la cabeza (y negativo = arriba; la cabeza va de -8 a 0).
     * Textura 64x64: lienzo = color base; franja v>=48 = color de acento; "galera" tiene banda propia en v=32.
     */
    private static LayerDefinition hatsDef() {
        MeshDefinition m = new MeshDefinition();
        PartDefinition r = m.getRoot();

        hat(r, "halo", CubeListBuilder.create().texOffs(0, 0)
            .addBox(-5f, -12f, -5f, 10, 1, 1).addBox(-5f, -12f, 4f, 10, 1, 1)
            .addBox(-5f, -12f, -4f, 1, 1, 8).addBox(4f, -12f, -4f, 1, 1, 8));

        hat(r, "corona", CubeListBuilder.create().texOffs(0, 0)
            .addBox(-5f, -10f, -5f, 10, 2, 1).addBox(-5f, -10f, 4f, 10, 2, 1)
            .addBox(-5f, -10f, -4f, 1, 2, 8).addBox(4f, -10f, -4f, 1, 2, 8)
            .addBox(-5f, -13f, -5f, 2, 3, 2).addBox(3f, -13f, -5f, 2, 3, 2)
            .addBox(-5f, -13f, 3f, 2, 3, 2).addBox(3f, -13f, 3f, 2, 3, 2)
            .addBox(-1f, -12f, -5f, 2, 2, 1).addBox(-1f, -12f, 4f, 2, 2, 1));

        hat(r, "galera", CubeListBuilder.create().texOffs(0, 0).addBox(-6f, -9f, -6f, 12, 1, 12)
            .texOffs(0, 16).addBox(-4f, -16f, -4f, 8, 7, 8)
            .texOffs(0, 32).addBox(-4f, -11f, -4f, 8, 2, 8, new CubeDeformation(0.25f)));

        hat(r, "gato", CubeListBuilder.create().texOffs(0, 0)
            .addBox(-4f, -11f, -3f, 3, 3, 2).addBox(1f, -11f, -3f, 3, 3, 2)
            .addBox(-4f, -13f, -3f, 1, 2, 2).addBox(3f, -13f, -3f, 1, 2, 2)
            .texOffs(0, 48).addBox(-3.5f, -10.5f, -3.2f, 2, 2, 1).addBox(1.5f, -10.5f, -3.2f, 2, 2, 1));

        hat(r, "cuernos", CubeListBuilder.create().texOffs(0, 0)
            .addBox(-5f, -10f, -1f, 2, 2, 2).addBox(3f, -10f, -1f, 2, 2, 2)
            .addBox(-5.5f, -12f, -0.5f, 1, 2, 1).addBox(4.5f, -12f, -0.5f, 1, 2, 1)
            .texOffs(0, 48).addBox(-6f, -14f, -0.5f, 1, 2, 1).addBox(5f, -14f, -0.5f, 1, 2, 1));

        hat(r, "mago", CubeListBuilder.create().texOffs(0, 0)
            .addBox(-7f, -9f, -7f, 14, 1, 14).addBox(-4f, -12f, -4f, 8, 3, 8)
            .addBox(-3f, -15f, -3f, 6, 3, 6).addBox(-2f, -18f, -2f, 4, 3, 4).addBox(-1f, -20f, -1f, 2, 2, 2)
            .texOffs(0, 48).addBox(-4f, -10f, -4f, 8, 1, 8, new CubeDeformation(0.3f)));

        hat(r, "auriculares", CubeListBuilder.create().texOffs(0, 0)
            .addBox(-5.5f, -10f, -0.5f, 11, 1, 1).addBox(-5.5f, -9f, -0.5f, 1, 4, 1).addBox(4.5f, -9f, -0.5f, 1, 4, 1)
            .addBox(-6.5f, -5f, -2f, 2, 4, 4).addBox(4.5f, -5f, -2f, 2, 4, 4)
            .texOffs(0, 48).addBox(-7f, -4.5f, -1f, 1, 3, 2).addBox(6f, -4.5f, -1f, 1, 3, 2));

        hat(r, "conejo", CubeListBuilder.create().texOffs(0, 0)
            .addBox(-3.5f, -17f, -1f, 2, 9, 1).addBox(1.5f, -17f, -1f, 2, 9, 1)
            .texOffs(0, 48).addBox(-3f, -16f, -1.2f, 1, 7, 1).addBox(2f, -16f, -1.2f, 1, 7, 1));

        hat(r, "gorra", CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -9f, -4f, 8, 2, 8, new CubeDeformation(0.5f))
            .texOffs(0, 48).addBox(-4f, -8.5f, -8f, 8, 1, 4).addBox(-0.5f, -10.5f, -0.5f, 1, 1, 1));

        return LayerDefinition.create(m, 64, 64);
    }

    private static int light(Cosmetics.Item it, int light) { return it.glow ? LightTexture.FULL_BRIGHT : light; }

    // ------------------------------------------------------------------ capas de dibujo
    private abstract static class Base extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Base(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> p) { super(p); }

        boolean skip(AbstractClientPlayer p) {
            return p.isInvisible() || p.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        }
    }

    /** Capa propia: mismo movimiento que la de Mojang, un poco más atrás para taparla. */
    public static final class CapeLayer extends Base {
        private final ModelPart cape;

        public CapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet set) {
            super(parent);
            cape = set.bakeLayer(CAPE).getChild("cape");
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                           float pt, float age, float headYaw, float headPitch) {
            if (skip(p)) return;
            Cosmetics.Item item = Cosmetics.of(p, Cosmetics.Type.CAPE);
            if (item == null) return;

            ps.pushPose();
            ps.translate(0.0D, 0.0D, 0.15D);
            double dx = Mth.lerp(pt, p.xCloakO, p.xCloak) - Mth.lerp(pt, p.xo, p.getX());
            double dy = Mth.lerp(pt, p.yCloakO, p.yCloak) - Mth.lerp(pt, p.yo, p.getY());
            double dz = Mth.lerp(pt, p.zCloakO, p.zCloak) - Mth.lerp(pt, p.zo, p.getZ());
            float body = p.yBodyRotO + (p.yBodyRot - p.yBodyRotO);
            double sin = Mth.sin(body * ((float) Math.PI / 180F));
            double cos = -Mth.cos(body * ((float) Math.PI / 180F));
            float lift = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
            float swing = Mth.clamp((float) (dx * sin + dz * cos) * 100.0F, 0.0F, 150.0F);
            float sway = Mth.clamp((float) (dx * cos - dz * sin) * 100.0F, -20.0F, 20.0F);
            lift += Mth.sin(Mth.lerp(pt, p.walkDistO, p.walkDist) * 6.0F) * 32.0F * Mth.lerp(pt, p.oBob, p.bob);
            if (p.isCrouching()) lift += 25.0F;
            ps.mulPose(Axis.XP.rotationDegrees(6.0F + swing / 2.0F + lift));
            ps.mulPose(Axis.ZP.rotationDegrees(sway / 2.0F));
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - sway / 2.0F));

            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(item.texture));
            cape.render(ps, vc, light(item, light), OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }

    /** Alas: quietas con un respiro leve en el suelo, abiertas y aleteando en el aire. */
    public static final class WingsLayer extends Base {
        private final ModelPart right, left;

        public WingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet set) {
            super(parent);
            ModelPart root = set.bakeLayer(WINGS);
            right = root.getChild("wing_r");
            left = root.getChild("wing_l");
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                           float pt, float age, float headYaw, float headPitch) {
            if (skip(p)) return;
            Cosmetics.Item item = Cosmetics.of(p, Cosmetics.Type.WINGS);
            if (item == null) return;

            boolean air = !p.onGround() && !p.isInWater() && !p.isPassenger();
            float open = air ? 0.45F + Mth.sin(age * 0.55F) * 0.30F : 0.95F + Mth.sin(age * 0.07F) * 0.06F;
            if (p.isCrouching()) open = 1.15F;
            right.yRot = open;
            left.yRot = -open;

            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(item.texture));
            int l = light(item, light);
            right.render(ps, vc, l, OverlayTexture.NO_OVERLAY);
            left.render(ps, vc, l, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }

    /** Sombreros: siguen la cabeza. El halo flota. */
    public static final class HatLayer extends Base {
        private final Map<String, ModelPart> hats = new HashMap<>();

        public HatLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet set) {
            super(parent);
            ModelPart root = set.bakeLayer(HATS);
            for (Cosmetics.Item it : Cosmetics.of(Cosmetics.Type.HAT)) hats.put(it.id, root.getChild(it.id));
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                           float pt, float age, float headYaw, float headPitch) {
            if (p.isInvisible()) return;
            Cosmetics.Item item = Cosmetics.of(p, Cosmetics.Type.HAT);
            if (item == null) return;
            ModelPart part = hats.get(item.id);
            if (part == null) return;

            ps.pushPose();
            getParentModel().head.translateAndRotate(ps);
            if (item.id.equals("halo")) ps.translate(0.0D, Mth.sin(age * 0.08F) * 0.025D, 0.0D);
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(item.texture));
            part.render(ps, vc, light(item, light), OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
