package com.atenas.social.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Mascotas cosmeticas (1.20.1). Cada jugador con mascota elegida (y a menos de {@link Cosmetics#MAX_DIST_SQ} de ti) tiene un
 * {@link PetBrain} que se mueve una vez por tick; se dibuja despues de las entidades del mundo. Solo cliente: el servidor no sabe de ellas.
 * La forma de cada modelo sale de pets/models.json (ver {@link PetSpec}); aqui se construye generica, pieza por pieza.
 */
public final class PetRender {
    private static ModelLayerLocation layer(String kind) { return CosmeticRender.loc("pet_" + kind); }

    // ------------------------------------------------------------------ definiciones
    public static void registerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (final PetSpec.Kind k : PetSpec.kinds()) e.registerLayerDefinition(layer(k.name), () -> definition(k));
    }

    private static LayerDefinition definition(PetSpec.Kind k) {
        MeshDefinition m = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        for (PetSpec.Piece p : k.pieces) {
            PartDefinition parent = p.p == null || p.p.isEmpty() ? m.getRoot() : defs.get(p.p);
            CubeListBuilder cb = CubeListBuilder.create().texOffs(p.uv[0], p.uv[1]).addBox(p.o[0], p.o[1], p.o[2], p.s[0], p.s[1], p.s[2]);
            defs.put(p.n, parent.addOrReplaceChild(p.n, cb, PartPose.offset(p.at[0], p.at[1], p.at[2])));
        }
        return LayerDefinition.create(m, PetSpec.texW, PetSpec.texH);
    }

    /** Un modelo ya horneado de un tipo, con sus piezas por ranura de pose. */
    private static final class Model {
        final PetSpec.Kind k;
        final ModelPart root;
        final ModelPart[] part = new ModelPart[PetSpec.N];
        final float[][] base = new float[PetSpec.N][];
        final PetSpec.Pose pose = new PetSpec.Pose();

        Model(PetSpec.Kind k, ModelPart root) {
            this.k = k; this.root = root;
            Map<String, ModelPart> byName = new HashMap<>();
            for (PetSpec.Piece p : k.pieces) {
                ModelPart parent = p.p == null || p.p.isEmpty() ? root : byName.get(p.p);
                ModelPart me = parent.getChild(p.n);
                byName.put(p.n, me);
                int s = PetSpec.slot(p.n);
                if (s >= 0) { part[s] = me; base[s] = p.at; }
            }
        }

        void render(PetBrain brain, float pt, PoseStack ps, VertexConsumer vc, int light) {
            PetSpec.pose(k, brain, pt, pose);
            for (int i = 0; i < PetSpec.N; i++) {
                ModelPart mp = part[i];
                if (mp == null) continue;
                mp.xRot = pose.rx[i]; mp.yRot = pose.ry[i]; mp.zRot = pose.rz[i];
                mp.x = base[i][0] + pose.dx[i]; mp.y = base[i][1] + pose.dy[i]; mp.z = base[i][2] + pose.dz[i];
            }
            root.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        }
    }

    // ------------------------------------------------------------------ estado
    private static final class Pet {
        final PetBrain brain;
        Cosmetics.Item item;
        boolean seen;
        String kind = "";
        Pet(long seed) { brain = new PetBrain(seed); }
    }

    private final Map<String, Pet> pets = new HashMap<>();
    private static final Map<String, Model> MODELS = new HashMap<>();

    private static Model modelFor(PetSpec.Kind k) {
        Model m = MODELS.get(k.name);
        if (m == null) {
            m = new Model(k, Minecraft.getInstance().getEntityModels().bakeLayer(layer(k.name)));
            MODELS.put(k.name, m);
        }
        return m;
    }

    /** El suelo y el agua para la mascota. */
    private static final PetBrain.Ground GROUND = new PetBrain.Ground() {
        @Override public double ground(double x, double z, double fromY) {
            Level w = Minecraft.getInstance().level;
            if (w == null) return Double.NaN;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            int top = (int) Math.floor(fromY);
            int min = w.getMinBuildHeight();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = top; y >= top - 8 && y >= min; y--) {
                pos.set(bx, y, bz);
                BlockState st = w.getBlockState(pos);
                VoxelShape shape = st.getCollisionShape(w, pos);
                if (!shape.isEmpty()) return y + shape.max(Direction.Axis.Y);
            }
            return Double.NaN;
        }

        @Override public double water(double x, double z, double y) {
            Level w = Minecraft.getInstance().level;
            if (w == null) return Double.NaN;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dy = 0; dy >= -1; dy--) {
                int yy = by + dy;
                pos.set(bx, yy, bz);
                if (!w.getFluidState(pos).is(FluidTags.WATER)) continue;
                for (int n = 0; n < 8; n++) {
                    pos.set(bx, yy + 1, bz);
                    if (w.getFluidState(pos).is(FluidTags.WATER)) yy++; else break;
                }
                pos.set(bx, yy, bz);
                return yy + w.getFluidState(pos).getHeight(w, pos);
            }
            return Double.NaN;
        }
    };

    // ------------------------------------------------------------------ vista previa (sin mundo)
    private static PetBrain previewBrain;
    private static String previewKey = "";
    private static long previewLast;
    private static final PetBrain.Ground FLAT = (x, z, fromY) -> 0.0;

    /** Mascota de la vista previa del menu de cosmeticos: en su sitio, haciendo sus gestos (las aves aletean en el aire). */
    static void renderPreview(PoseStack ps, MultiBufferSource buf, Cosmetics.Item item, int light) {
        if (item == null) return;
        PetSpec.Kind k = PetSpec.kindFor(item.modelId());
        if (k == null) return;
        Model m = modelFor(k);
        if (previewBrain == null || !previewKey.equals(item.id)) {
            previewBrain = new PetBrain(12345L);
            previewBrain.configure(k.flier, k.avoidWater, k.hop);
            previewBrain.teleport(0.0, 0.0, 0.0, 0f);
            previewKey = item.id; previewLast = Util.getMillis();
        }
        long now = Util.getMillis();
        int steps = 0;
        PetBrain.Owner o = new PetBrain.Owner();
        o.yaw = o.bodyYaw = 0f; o.onGround = true;
        while (now - previewLast >= 50L && steps < 5) { previewBrain.tick(o, FLAT); previewLast += 50L; steps++; }
        if (now - previewLast >= 50L) previewLast = now;
        float pt = Math.max(0f, Math.min(1f, (now - previewLast) / 50f));
        float sc = PetSpec.renderScale(k, previewBrain, pt);
        TexFilter.prepare(item.texture, -1);
        ps.pushPose();
        ps.translate(0.0, k.flier ? 0.55 + Math.sin(now * 0.004) * 0.06 : 0.0, 0.0);
        ps.scale(-sc, -sc, sc);
        ps.translate(0f, -1.501f, 0f);
        m.render(previewBrain, pt, ps, buf.getBuffer(RenderType.entityCutoutNoCull(item.texture)), light);
        ps.popPose();
    }

    // ------------------------------------------------------------------ eventos
    private static void fill(PetBrain.Owner o, AbstractClientPlayer p) {
        o.x = p.getX(); o.y = p.getY(); o.z = p.getZ();
        o.yaw = p.getYRot(); o.bodyYaw = p.yBodyRot; o.pitch = p.getXRot();
        o.sneaking = p.isCrouching(); o.sprinting = p.isSprinting();
        o.fallFlying = p.isFallFlying(); o.flying = p.getAbilities().flying;
        o.swimming = p.isSwimming(); o.inWater = p.isInWater();
        o.riding = p.isPassenger(); o.climbing = p.onClimbable(); o.onGround = p.onGround();
    }

    private final PetBrain.Owner owner = new PetBrain.Owner();

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { pets.clear(); return; }
        if (mc.isPaused()) return;
        for (Pet p : pets.values()) p.seen = false;
        for (AbstractClientPlayer p : mc.level.players()) {
            String name = p.getGameProfile().getName();
            if (name == null || p.isSpectator()) continue;
            Cosmetics.Item item = Cosmetics.of(p, Cosmetics.Type.PET);
            if (item == null || !Cosmetics.inRange(p)) continue;
            PetSpec.Kind k = PetSpec.kindFor(item.modelId());
            if (k == null) continue;
            Pet pet = pets.get(name);
            if (pet == null) { pet = new Pet(name.hashCode()); pets.put(name, pet); }
            if (!k.name.equals(pet.kind)) { pet.kind = k.name; pet.brain.configure(k.flier, k.avoidWater, k.hop); }
            pet.seen = true;
            pet.item = item;
            fill(owner, p);
            pet.brain.tick(owner, GROUND);
        }
        for (Iterator<Pet> it = pets.values().iterator(); it.hasNext();) if (!it.next().seen) it.remove();
    }

    @SubscribeEvent
    public void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || pets.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        java.util.Set<RenderType> used = new java.util.HashSet<>();

        for (Pet pet : pets.values()) {
            if (pet.item == null) continue;
            PetSpec.Kind k = PetSpec.kindFor(pet.item.modelId());
            if (k == null) continue;
            PetBrain b = pet.brain;
            double x = b.ix(pt), y = b.iy(pt), z = b.iz(pt);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y + 0.5, z));
            Model model = modelFor(k);
            ResourceLocation tex = pet.item.texture;
            TexFilter.prepare(tex, -1);
            RenderType type = RenderType.entityCutoutNoCull(tex);
            used.add(type);
            float sc = PetSpec.renderScale(k, b, pt);

            ps.pushPose();
            ps.translate(x - cam.x, y - cam.y, z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(180f - b.iyaw(pt)));
            float pitch = b.ipitch(pt), roll = b.iroll(pt);
            if (Math.abs(pitch) > 0.001f || Math.abs(roll) > 0.001f) {          // inclinada en el aire / tumbada con el dueño
                double cy = (k.legH + k.bodyH / 2f) / 16.0 * sc;
                ps.translate(0.0, cy, 0.0);
                ps.mulPose(Axis.ZP.rotation(roll));
                ps.mulPose(Axis.XP.rotation(-pitch));
                ps.translate(0.0, -cy, 0.0);
            }
            ps.scale(-sc, -sc, sc);
            ps.translate(0f, -1.501f, 0f);
            model.render(b, pt, ps, buf.getBuffer(type), light);
            ps.popPose();
        }
        for (RenderType t : used) buf.endBatch(t);
    }
}
