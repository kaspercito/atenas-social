package com.atenas.social.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
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
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
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
import java.util.List;
import java.util.Map;

/** Modelos y capas de dibujo de los cosméticos (capa de tela, alas de plumas y sombreros en malla HD). Las mascotas están en {@link PetRender}. */
public final class CosmeticRender {
    public static final ModelLayerLocation HATS = loc("hats");

    private CosmeticRender() {}

    static ModelLayerLocation loc(String n) { return new ModelLayerLocation(new ResourceLocation(Cosmetics.MODID, n), "main"); }

    // ------------------------------------------------------------------ registro
    public static void registerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
        PetRender.registerDefinitions(e);
        e.registerLayerDefinition(HATS, CosmeticRender::hatsDef);
    }

    public static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (String skin : new String[] {"default", "slim"}) {
            PlayerRenderer r = e.getSkin(skin);
            if (r == null) continue;
            guardVanillaCape(r);
            r.addLayer(new CapeLayer(r, e.getEntityModels()));
            r.addLayer(new WingsLayer(r, e.getEntityModels()));
            r.addLayer(new HatLayer(r, e.getEntityModels()));
        }
    }

    /**
     * Envuelve la capa de Mojang: si llevas una capa de Atenas, la de Minecraft no se dibuja
     * (si no, se ve su borde por debajo de la nuestra). Busca la lista de capas por tipo, no por nombre.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void guardVanillaCape(PlayerRenderer r) {
        try {
            for (java.lang.reflect.Field f : LivingEntityRenderer.class.getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                List layers = (List) f.get(r);
                for (int i = 0; i < layers.size(); i++) {
                    if (layers.get(i) instanceof net.minecraft.client.renderer.entity.layers.CapeLayer) {
                        layers.set(i, new VanillaCapeGuard(r, (RenderLayer) layers.get(i)));
                    }
                }
                return;
            }
        } catch (Exception ex) {
            System.err.println("[AtenasSocial] capa vanilla: " + ex);
        }
    }

    /** Capa de Mojang que se salta el dibujo cuando hay una capa de Atenas puesta. */
    public static final class VanillaCapeGuard extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> inner;

        VanillaCapeGuard(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                         RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> inner) {
            super(parent);
            this.inner = inner;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                           float pt, float age, float headYaw, float headPitch) {
            if (Cosmetics.of(p, Cosmetics.Type.CAPE) != null) return;
            inner.render(ps, buf, light, p, limbSwing, limbSwingAmount, pt, age, headYaw, headPitch);
        }
    }

    // ------------------------------------------------------------------ definiciones
    private static void hat(PartDefinition root, String id, CubeListBuilder b) { root.addOrReplaceChild(id, b, PartPose.ZERO); }

    /**
     * Sombreros con cajas. Origen = pivote de la cabeza (y negativo = arriba; la cabeza va de -8 a 0).
     * Textura 64x64: lienzo = color base; franja v>=48 = color de acento; "galera" tiene banda propia en v=32.
     */
    private static LayerDefinition hatsDef() {
        MeshDefinition m = new MeshDefinition();
        PartDefinition r = m.getRoot();

        hat(r, "halo", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-5.0f, -12.0f, -5.0f, 10, 1, 1)
            .addBox(-5.0f, -12.0f, 4.0f, 10, 1, 1)
            .addBox(-5.0f, -12.0f, -4.0f, 1, 1, 8)
            .addBox(4.0f, -12.0f, -4.0f, 1, 1, 8)
            .addBox(-4.0f, -13.0f, -4.0f, 8, 1, 1, new CubeDeformation(-0.2f))
            .addBox(-4.0f, -13.0f, 3.0f, 8, 1, 1, new CubeDeformation(-0.2f))
            .addBox(-4.0f, -13.0f, -3.0f, 1, 1, 6, new CubeDeformation(-0.2f))
            .addBox(3.0f, -13.0f, -3.0f, 1, 1, 6, new CubeDeformation(-0.2f))
            .texOffs(0, 48)
            .addBox(-1.0f, -12.0f, -6.0f, 2, 1, 1)
            .addBox(-1.0f, -12.0f, 5.0f, 2, 1, 1)
            .addBox(-6.0f, -12.0f, -1.0f, 1, 1, 2)
            .addBox(5.0f, -12.0f, -1.0f, 1, 1, 2));

        hat(r, "corona", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-5.0f, -10.0f, -5.0f, 10, 2, 1)
            .addBox(-5.0f, -10.0f, 4.0f, 10, 2, 1)
            .addBox(-5.0f, -10.0f, -4.0f, 1, 2, 8)
            .addBox(4.0f, -10.0f, -4.0f, 1, 2, 8)
            .addBox(-5.0f, -13.0f, -5.0f, 2, 3, 2)
            .addBox(3.0f, -13.0f, -5.0f, 2, 3, 2)
            .addBox(-5.0f, -13.0f, 3.0f, 2, 3, 2)
            .addBox(3.0f, -13.0f, 3.0f, 2, 3, 2)
            .addBox(-1.0f, -12.0f, -5.0f, 2, 2, 1)
            .addBox(-1.0f, -12.0f, 4.0f, 2, 2, 1)
            .addBox(-5.0f, -12.0f, -1.0f, 1, 2, 2)
            .addBox(4.0f, -12.0f, -1.0f, 1, 2, 2)
            .addBox(-5.0f, -14.0f, -5.0f, 1, 1, 1)
            .addBox(4.0f, -14.0f, -5.0f, 1, 1, 1)
            .addBox(-5.0f, -14.0f, 4.0f, 1, 1, 1)
            .addBox(4.0f, -14.0f, 4.0f, 1, 1, 1)
            .addBox(-1.0f, -13.0f, -5.0f, 2, 1, 1, new CubeDeformation(-0.25f))
            .addBox(-1.0f, -13.0f, 4.0f, 2, 1, 1, new CubeDeformation(-0.25f))
            .texOffs(0, 48)
            .addBox(-1.0f, -9.0f, -6.0f, 2, 1, 1)
            .addBox(-6.0f, -9.0f, -1.0f, 1, 1, 2)
            .addBox(5.0f, -9.0f, -1.0f, 1, 1, 2)
            .addBox(-1.0f, -9.0f, 5.0f, 2, 1, 1)
            .addBox(-4.0f, -11.0f, -6.0f, 1, 1, 1)
            .addBox(3.0f, -11.0f, -6.0f, 1, 1, 1));

        hat(r, "galera", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-6.0f, -9.0f, -6.0f, 12, 1, 12)
            .addBox(-4.0f, -16.0f, -4.0f, 8, 7, 8)
            .addBox(-4.0f, -17.0f, -4.0f, 8, 1, 8, new CubeDeformation(-0.3f))
            .addBox(-6.0f, -10.0f, -6.0f, 12, 1, 12, new CubeDeformation(-0.45f))
            .texOffs(0, 32)
            .addBox(-4.0f, -11.0f, -4.0f, 8, 2, 8, new CubeDeformation(0.25f))
            .texOffs(0, 48)
            .addBox(3.0f, -13.0f, -5.0f, 1, 3, 2)
            .addBox(4.0f, -14.0f, -5.0f, 1, 2, 1)
            .addBox(-1.0f, -11.0f, -5.0f, 2, 2, 1, new CubeDeformation(0.1f)));

        hat(r, "gato", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-4.0f, -11.0f, -3.0f, 3, 3, 2)
            .addBox(1.0f, -11.0f, -3.0f, 3, 3, 2)
            .addBox(-4.0f, -13.0f, -3.0f, 1, 2, 2)
            .addBox(3.0f, -13.0f, -3.0f, 1, 2, 2)
            .addBox(-4.0f, -14.0f, -3.0f, 1, 1, 1, new CubeDeformation(-0.2f))
            .addBox(3.0f, -14.0f, -3.0f, 1, 1, 1, new CubeDeformation(-0.2f))
            .addBox(-4.0f, -9.0f, -4.0f, 8, 1, 8, new CubeDeformation(0.2f))
            .texOffs(0, 48)
            .addBox(-3.0f, -10.0f, -4.0f, 2, 2, 1)
            .addBox(1.0f, -10.0f, -4.0f, 2, 2, 1)
            .addBox(-3.0f, -12.0f, -3.0f, 1, 1, 1, new CubeDeformation(-0.15f))
            .addBox(2.0f, -12.0f, -3.0f, 1, 1, 1, new CubeDeformation(-0.15f))
            .addBox(-1.0f, -9.0f, -5.0f, 2, 1, 1, new CubeDeformation(0.1f)));

        hat(r, "cuernos", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-6.0f, -10.0f, -2.0f, 3, 2, 3)
            .addBox(3.0f, -10.0f, -2.0f, 3, 2, 3)
            .addBox(-6.0f, -12.0f, -1.0f, 2, 2, 2)
            .addBox(4.0f, -12.0f, -1.0f, 2, 2, 2)
            .addBox(-7.0f, -14.0f, -1.0f, 2, 2, 2, new CubeDeformation(-0.2f))
            .addBox(5.0f, -14.0f, -1.0f, 2, 2, 2, new CubeDeformation(-0.2f))
            .addBox(-8.0f, -16.0f, -1.0f, 1, 2, 1)
            .addBox(7.0f, -16.0f, -1.0f, 1, 2, 1)
            .texOffs(0, 48)
            .addBox(-8.0f, -18.0f, -1.0f, 1, 2, 1, new CubeDeformation(-0.15f))
            .addBox(7.0f, -18.0f, -1.0f, 1, 2, 1, new CubeDeformation(-0.15f))
            .addBox(-8.0f, -17.0f, -1.0f, 1, 1, 1, new CubeDeformation(0.1f))
            .addBox(7.0f, -17.0f, -1.0f, 1, 1, 1, new CubeDeformation(0.1f)));

        hat(r, "mago", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-7.0f, -9.0f, -7.0f, 14, 1, 14)
            .addBox(-4.0f, -12.0f, -4.0f, 8, 3, 8)
            .addBox(-3.0f, -15.0f, -3.0f, 6, 3, 6)
            .addBox(-2.0f, -18.0f, -2.0f, 4, 3, 4)
            .addBox(-1.0f, -20.0f, -1.0f, 2, 2, 2)
            .addBox(0.0f, -22.0f, 0.0f, 2, 2, 2)
            .addBox(1.0f, -23.0f, 1.0f, 1, 1, 2, new CubeDeformation(-0.1f))
            .addBox(-7.0f, -10.0f, -7.0f, 14, 1, 14, new CubeDeformation(-0.4f))
            .texOffs(0, 48)
            .addBox(-4.0f, -10.0f, -4.0f, 8, 1, 8, new CubeDeformation(0.3f))
            .addBox(-1.0f, -10.0f, -5.0f, 2, 2, 1, new CubeDeformation(0.2f))
            .addBox(2.0f, -13.0f, -5.0f, 1, 1, 1)
            .addBox(-3.0f, -16.0f, -4.0f, 1, 1, 1)
            .addBox(1.0f, -17.0f, -3.0f, 1, 1, 1)
            .addBox(-3.0f, -11.0f, -5.0f, 1, 1, 1));

        hat(r, "auriculares", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-5.0f, -10.0f, -1.0f, 11, 1, 2)
            .addBox(-6.0f, -9.0f, -1.0f, 1, 4, 2)
            .addBox(5.0f, -9.0f, -1.0f, 1, 4, 2)
            .addBox(-7.0f, -5.0f, -3.0f, 2, 4, 5)
            .addBox(5.0f, -5.0f, -3.0f, 2, 4, 5)
            .addBox(-6.0f, -10.0f, -1.0f, 1, 1, 2, new CubeDeformation(-0.1f))
            .addBox(5.0f, -10.0f, -1.0f, 1, 1, 2, new CubeDeformation(-0.1f))
            .texOffs(0, 48)
            .addBox(-8.0f, -4.0f, -2.0f, 1, 3, 3)
            .addBox(7.0f, -4.0f, -2.0f, 1, 3, 3)
            .addBox(-7.0f, -2.0f, -5.0f, 1, 1, 3)
            .addBox(-7.0f, -2.0f, -6.0f, 2, 1, 1, new CubeDeformation(0.1f))
            .addBox(-2.0f, -11.0f, -1.0f, 4, 1, 2, new CubeDeformation(-0.15f)));

        hat(r, "conejo", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-4.0f, -17.0f, -1.0f, 2, 9, 1)
            .addBox(2.0f, -17.0f, -1.0f, 2, 9, 1)
            .addBox(-4.0f, -19.0f, -2.0f, 2, 2, 2, new CubeDeformation(-0.1f))
            .addBox(2.0f, -19.0f, -2.0f, 2, 2, 2, new CubeDeformation(-0.1f))
            .addBox(-4.0f, -9.0f, -4.0f, 8, 1, 8, new CubeDeformation(0.2f))
            .texOffs(0, 48)
            .addBox(-3.0f, -16.0f, -1.0f, 1, 7, 1)
            .addBox(3.0f, -16.0f, -1.0f, 1, 7, 1)
            .addBox(-3.0f, -18.0f, -2.0f, 1, 1, 1)
            .addBox(3.0f, -18.0f, -2.0f, 1, 1, 1));

        hat(r, "gorra", CubeListBuilder.create()
            .texOffs(0, 0)
            .addBox(-4.0f, -9.0f, -4.0f, 8, 2, 8, new CubeDeformation(0.5f))
            .addBox(-4.0f, -10.0f, -4.0f, 8, 1, 8, new CubeDeformation(0.2f))
            .texOffs(0, 48)
            .addBox(-4.0f, -8.0f, -8.0f, 8, 1, 4)
            .addBox(-5.0f, -8.0f, -6.0f, 1, 1, 2)
            .addBox(4.0f, -8.0f, -6.0f, 1, 1, 2)
            .addBox(-1.0f, -11.0f, -1.0f, 2, 1, 2, new CubeDeformation(-0.25f))
            .addBox(-2.0f, -9.0f, -5.0f, 4, 2, 1));

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

    /**
     * Capa de tela propia: mismo movimiento que la de Mojang, un poco más atrás para taparla, pero la tela es una malla continua
     * ({@link CapeMesh}) que se curva y ondea sin cortes entre tira y tira.
     */
    public static final class CapeLayer extends Base {
        private final CapeMesh mesh = new CapeMesh();
        private final VertexSink sink = new VertexSink();
        private final float[] rx = new float[CapeWave.SEGS], rz = new float[CapeWave.SEGS];

        public CapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet set) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                           float pt, float age, float headYaw, float headPitch) {
            if (skip(p)) return;
            Cosmetics.Item item = Cosmetics.of(p, Cosmetics.Type.CAPE);
            if (item == null || !Cosmetics.inRange(p)) return;

            ps.pushPose();
            // pegada a la espalda (la de Mojang va a 0.125); con alas puestas NO se aleja: las alas nacen detrás de la capa (WingMesh.zoff)
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
            // La inclinación total es la de siempre, pero solo el 35 % la gira el cuerpo entero: el resto lo doblan las tiras de tela.
            float tilt = swing / 2.0F + lift;
            ps.mulPose(Axis.XP.rotationDegrees(Math.max(8.0F, 6.0F + tilt * 0.35F) + (p.isCrouching() ? 6.0F : 0.0F)));
            ps.mulPose(Axis.ZP.rotationDegrees(sway / 4.0F));
            ps.mulPose(Axis.YP.rotationDegrees(180.0F - sway / 2.0F));
            if (p.isCrouching()) ps.translate(0.0D, 0.115D, 0.0875D); // el modelo baja y se adelanta al agacharse

            Minecraft mc = Minecraft.getInstance();
            boolean far = p != mc.player && mc.player != null && p.distanceToSqr(mc.player) > 256.0;
            boolean anim = item.frames > 1;
            int frames = anim ? item.frames : 1;
            int frame = anim ? Cosmetics.frameOf(item) % frames : 0;
            TexFilter.prepare(item.texture, anim ? 3 : -1);
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(item.texture));
            sink.begin(vc, ps.last(), light(item, light));
            mesh.emit(sink, frame, frames, 0.0625F, (float) Math.toRadians(tilt * 0.65F), CapeWave.ampFor(swing), age,
                (float) Math.toRadians(sway / 4.0F) + Mth.clamp((p.yBodyRot - p.yBodyRotO) * 0.012F, -0.3F, 0.3F), far);
            sink.vc = null;
            ps.popPose();
        }
    }

    /** Entrega los vértices de {@link WingMesh} a un VertexConsumer (formato de entidad: posición, color, textura, overlay, luz, normal). */
    static final class VertexSink implements WingMesh.Sink {
        VertexConsumer vc;
        PoseStack.Pose pose;
        int light;
        float nx, ny = 1f, nz;

        void begin(VertexConsumer vc, PoseStack.Pose pose, int light) { this.vc = vc; this.pose = pose; this.light = light; }

        @Override public void normal(float x, float y, float z) { nx = x; ny = y; nz = z; }

        @Override public void vertex(float x, float y, float z, float u, float v) {
            vc.vertex(pose.pose(), x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(pose.normal(), nx, ny, nz).endVertex();
        }
    }

    /**
     * Alas de plumas: la forma sale de {@link WingMesh} (cada pluma es una tira de segmentos que ondula). Quietas con un respiro leve en el
     * suelo, abiertas y aleteando en el aire. De lejos se dibujan con menos segmentos y una sola cara.
     */
    public static final class WingsLayer extends Base {
        private final WingMesh mesh = new WingMesh();
        private final MembraneWing membrane = new MembraneWing();
        private final VertexSink sink = new VertexSink();
        /** Estado por jugador: la fase del aleteo nunca se detiene y la mezcla suelo/aire es gradual. */
        private static final class State { float air, phase, spd, vy; long last; }
        private final Map<Integer, State> states = new HashMap<>();

        public WingsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet set) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                           float pt, float age, float headYaw, float headPitch) {
            if (skip(p)) return;
            Cosmetics.Item item = Cosmetics.of(p, Cosmetics.Type.WINGS);
            if (item == null || !Cosmetics.inRange(p)) return;

            Minecraft mc = Minecraft.getInstance();
            double d2 = (mc.player == null || p == mc.player) ? 0.0 : p.distanceToSqr(mc.player);
            int segs = WingMesh.segsFor(d2);
            boolean twoSided = WingMesh.twoSidedFor(d2);

            boolean airNow = !p.onGround() && !p.isInWater() && !p.isPassenger();
            if (states.size() > 200) states.clear();
            State st = states.computeIfAbsent(p.getId(), k -> { State n = new State(); n.last = System.nanoTime(); return n; });
            long now = System.nanoTime();
            float dt = Math.min(0.1F, (now - st.last) / 1.0e9F) * 20F; // ticks
            st.last = now;
            st.air += ((airNow ? 1F : 0F) - st.air) * Math.min(1F, dt * 0.25F);
            float mvx = (float) (p.getX() - p.xo), mvz = (float) (p.getZ() - p.zo);
            st.spd += (Math.min(1F, Mth.sqrt(mvx * mvx + mvz * mvz) * 4F) - st.spd) * Math.min(1F, dt * 0.2F);
            st.vy += (Mth.clamp((float) (p.getY() - p.yo) * 2.5F, -1F, 1F) - st.vy) * Math.min(1F, dt * 0.3F);
            st.phase += dt * (0.35F + st.air * 0.65F + st.spd * 0.25F + Math.abs(st.vy) * 0.5F * st.air);
            float a = st.air;
            float open = (0.85F + Mth.sin(st.phase * 0.2F) * 0.07F + Mth.sin(st.phase * 0.47F) * 0.025F - st.spd * 0.22F) * (1F - a)
                + (0.50F + Mth.sin(st.phase * 0.55F) * 0.32F - st.vy * 0.18F) * a;
            // Con capa: las alas nacen a los lados de la capa (outset) y se abren más hacia los lados que hacia atrás, así no la tapan.
            boolean cape = Cosmetics.of(p, Cosmetics.Type.CAPE) != null;
            if (cape) open *= 0.80F;
            open *= item.openMul; // cada cosmético puede abrir más o menos (por defecto 1)
            mesh.outset = cape ? 1.2F : 0F; // casi pegadas a la columna: nacen detrás de la capa, no separadas
            mesh.zoff = cape ? 1.8F : 0F;
            membrane.outset = mesh.outset;
            membrane.zoff = mesh.zoff;
            if (p.isCrouching()) open = (cape ? 0.9F : 1.15F) * item.openMul;
            float flap = 0.025F + 0.075F * a + 0.04F * st.spd; // siempre ondulan un poco; más en el aire

            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(item.texture));
            sink.begin(vc, ps.last(), light(item, light));
            if (item.membrane) membrane.emit(sink, open, st.phase, flap, twoSided, 0.0625F);
            else mesh.emit(sink, open, st.phase, flap, segs, twoSided, 0.0625F);
            sink.vc = null;
            ps.popPose();
        }
    }

    /** Ids que tienen modelo de cajas (64x64): solo los usan los sombreros del catálogo descargados, que traen textura con ese UV. */
    private static final String[] BOX_HATS = {"halo", "corona", "galera", "gato", "cuernos", "mago", "auriculares", "conejo", "gorra"};

    /**
     * Sombreros: siguen la cabeza. Los de Atenas son mallas HD (.atm, las mismas del cliente 1.8.9, ver {@link HatMesh}); los del
     * catálogo (texturas subidas con el UV de cajas) siguen con el modelo de cajas. El halo flota.
     */
    public static final class HatLayer extends Base {
        private final Map<String, ModelPart> hats = new HashMap<>();

        public HatLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet set) {
            super(parent);
            ModelPart root = set.bakeLayer(HATS);
            for (String id : BOX_HATS) hats.put(id, root.getChild(id));
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limbSwing, float limbSwingAmount,
                           float pt, float age, float headYaw, float headPitch) {
            if (p.isInvisible()) return;
            Cosmetics.Item item = Cosmetics.of(p, Cosmetics.Type.HAT);
            if (item == null) return;
            Minecraft mc = Minecraft.getInstance();
            if (p != mc.player && mc.player != null && p.distanceToSqr(mc.player) > HatMesh.MAX_DIST_SQ) return;

            if (!item.custom) {
                HatMesh mesh = HatMesh.get(item.id);
                if (mesh == null) return; // sin malla no hay nada que dibujar (la textura es un atlas, no sirve para cajas)
                ps.pushPose();
                getParentModel().head.translateAndRotate(ps);
                mesh.draw(ps, buf, item, light, age);
                ps.popPose();
                return;
            }

            ModelPart part = hats.get(item.modelId());
            if (part == null) return;
            ps.pushPose();
            getParentModel().head.translateAndRotate(ps);
            if (item.modelId().equals("halo")) ps.translate(0.0D, Mth.sin(age * 0.08F) * 0.025D, 0.0D);
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(item.texture));
            part.render(ps, vc, light(item, light), OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }
}
