package com.atenas.social.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
 * Mascotas cosméticas (1.20.1). Cada jugador con mascota elegida (y a menos de {@link Cosmetics#MAX_DIST_SQ} de ti) tiene un
 * {@link PetBrain} que se mueve una vez por tick; se dibuja después de las entidades del mundo, con la luz del lugar donde está.
 * Solo cliente: la mascota no existe para el servidor ni choca con nada. Las medidas y la animación salen de {@link PetSpec},
 * así que se ve igual que en la versión 1.8.9.
 *
 * Se registra una vez en el bus de Forge (ClientInit); las definiciones de los modelos las pide CosmeticRender.registerDefinitions.
 */
public final class PetRender {
    /** Un modelo por tipo (perro, husky, gato, zorro); las texturas son las del id de cada mascota. */
    private static final ModelLayerLocation[] LAYERS = new ModelLayerLocation[] {
        CosmeticRender.loc("pet_dog"), CosmeticRender.loc("pet_husky"), CosmeticRender.loc("pet_cat"), CosmeticRender.loc("pet_fox")
    };
    private static final PetSpec.Kind[] KINDS = new PetSpec.Kind[] {PetSpec.DOG, PetSpec.HUSKY, PetSpec.CAT, PetSpec.FOX};

    // ------------------------------------------------------------------ definiciones
    public static void registerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
        for (int i = 0; i < KINDS.length; i++) {
            final PetSpec.Kind k = KINDS[i];
            e.registerLayerDefinition(LAYERS[i], () -> definition(k));
        }
    }

    private static CubeListBuilder box(PetSpec.Part p, float x, float y, float z) {
        return CubeListBuilder.create().texOffs(p.u, p.v).addBox(x, y, z, p.w, p.h, p.d);
    }

    /**
     * Misma jerarquía que PetModel de 1.8.9: el cuerpo es el centro; de él cuelgan cabeza (con hocico y orejas) y cola (en dos partes);
     * las cuatro patas van sueltas, con el pivote en la cadera. Hoja lógica de 64 x 32: la imagen real puede ser más grande,
     * las coordenadas se normalizan.
     */
    private static LayerDefinition definition(PetSpec.Kind k) {
        MeshDefinition m = new MeshDefinition();
        PartDefinition root = m.getRoot();

        PartDefinition body = root.addOrReplaceChild("body", box(k.body, -k.body.w / 2f, -k.body.h / 2f, -k.body.d / 2f),
            PartPose.offset(0f, k.bodyY(), 0f));

        PartDefinition head = body.addOrReplaceChild("head", box(k.head, -k.head.w / 2f, -k.head.h / 2f, -k.head.d),
            PartPose.offset(0f, k.headY(), k.headZ()));
        head.addOrReplaceChild("snout", box(k.snout, -k.snout.w / 2f, 0f, -k.snout.d), PartPose.offset(0f, k.snoutY(), k.snoutZ()));
        head.addOrReplaceChild("earL", box(k.ear, -k.ear.w / 2f, -k.ear.h, -k.ear.d / 2f), PartPose.offset(k.earX(), k.earY(), k.earZ()));
        head.addOrReplaceChild("earR", box(k.ear, -k.ear.w / 2f, -k.ear.h, -k.ear.d / 2f), PartPose.offset(-k.earX(), k.earY(), k.earZ()));

        PartDefinition tail1 = body.addOrReplaceChild("tail1", box(k.tail1, -k.tail1.w / 2f, -k.tail1.h / 2f, 0f),
            PartPose.offset(0f, k.tailY(), k.tailZ()));
        tail1.addOrReplaceChild("tail2", box(k.tail2, -k.tail2.w / 2f, -k.tail2.h / 2f, 0f), PartPose.offset(0f, 0f, k.tail1.d));

        root.addOrReplaceChild("legFR", box(k.leg, -k.leg.w / 2f, 0f, -k.leg.d / 2f), PartPose.offset(-k.legX(), k.legY(), k.frontLegZ()));
        root.addOrReplaceChild("legFL", box(k.leg, -k.leg.w / 2f, 0f, -k.leg.d / 2f), PartPose.offset(k.legX(), k.legY(), k.frontLegZ()));
        root.addOrReplaceChild("legRR", box(k.leg, -k.leg.w / 2f, 0f, -k.leg.d / 2f), PartPose.offset(-k.legX(), k.legY(), k.rearLegZ()));
        root.addOrReplaceChild("legRL", box(k.leg, -k.leg.w / 2f, 0f, -k.leg.d / 2f), PartPose.offset(k.legX(), k.legY(), k.rearLegZ()));

        return LayerDefinition.create(m, 64, 32);
    }

    /** Un modelo ya horneado de un tipo, con sus piezas a mano para poner la pose. */
    private static final class Model {
        final PetSpec.Kind k;
        final ModelPart root, body, head, snout, earL, earR, tail1, tail2, legFR, legFL, legRR, legRL;
        final float[] pose = new float[PetSpec.POSE_SIZE];

        Model(PetSpec.Kind k, ModelPart root) {
            this.k = k;
            this.root = root;
            body = root.getChild("body");
            head = body.getChild("head");
            snout = head.getChild("snout");
            earL = head.getChild("earL");
            earR = head.getChild("earR");
            tail1 = body.getChild("tail1");
            tail2 = tail1.getChild("tail2");
            legFR = root.getChild("legFR");
            legFL = root.getChild("legFL");
            legRR = root.getChild("legRR");
            legRL = root.getChild("legRL");
        }

        /** Pone la pose y dibuja (el PoseStack ya está en el marco del modelo: pies en y = 24). */
        void render(PetBrain brain, float pt, PoseStack ps, VertexConsumer vc, int light) {
            PetSpec.pose(k, brain, pt, pose);
            body.xRot = pose[PetSpec.BODY_X];
            body.y = k.bodyY() + pose[PetSpec.BODY_DY];
            head.xRot = pose[PetSpec.HEAD_X];
            head.yRot = pose[PetSpec.HEAD_Y];
            snout.xRot = pose[PetSpec.SNOUT_X];
            earL.zRot = pose[PetSpec.EAR_L_Z];
            earR.zRot = pose[PetSpec.EAR_R_Z];
            tail1.xRot = pose[PetSpec.TAIL_X];
            tail1.yRot = pose[PetSpec.TAIL_Y];
            tail2.xRot = pose[PetSpec.TAIL2_X];
            legFR.xRot = pose[PetSpec.LEG_FR];
            legFL.xRot = pose[PetSpec.LEG_FL];
            legRR.xRot = pose[PetSpec.LEG_RR];
            legRL.xRot = pose[PetSpec.LEG_RL];
            legRR.y = legRL.y = k.legY() + pose[PetSpec.REAR_DY];
            legRR.z = legRL.z = k.rearLegZ() + pose[PetSpec.REAR_DZ];
            root.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        }
    }

    // ------------------------------------------------------------------ estado
    private static final class Pet {
        final PetBrain brain;
        Cosmetics.Item item;
        boolean seen;
        Pet(long seed) { brain = new PetBrain(seed); }
    }

    private final Map<String, Pet> pets = new HashMap<>();
    private final Map<PetSpec.Kind, Model> models = new HashMap<>();

    private Model modelFor(PetSpec.Kind k) {
        Model m = models.get(k);
        if (m == null) {
            ModelLayerLocation loc = LAYERS[0];
            for (int i = 0; i < KINDS.length; i++) if (KINDS[i] == k) loc = LAYERS[i];
            m = new Model(k, Minecraft.getInstance().getEntityModels().bakeLayer(loc));
            models.put(k, m);
        }
        return m;
    }

    /** El suelo para la mascota: la cara de arriba del bloque sólido más alto que esté a "fromY" o más abajo (hasta 8 bloques). */
    private static final PetBrain.Ground GROUND = (x, z, fromY) -> {
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
    };

    // ------------------------------------------------------------------ eventos
    /** Una vez por tick del juego (20 por segundo), con el juego en marcha. */
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) { pets.clear(); return; }
        if (mc.isPaused()) return; // en pausa (un solo jugador) la mascota se queda quieta
        for (Pet p : pets.values()) p.seen = false;
        for (AbstractClientPlayer p : mc.level.players()) {
            String name = p.getGameProfile().getName();
            if (name == null || p.isSpectator()) continue;
            Cosmetics.Item item = Cosmetics.of(p, Cosmetics.Type.PET);
            if (item == null || !Cosmetics.inRange(p)) continue;
            Pet pet = pets.get(name);
            if (pet == null) {
                pet = new Pet(name.hashCode());
                pets.put(name, pet);
            }
            pet.seen = true;
            pet.item = item;
            pet.brain.tick(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.isCrouching(), GROUND);
        }
        // quien ya no está (se fue, se quitó la mascota o se alejó) pierde su mascota; al volver aparece junto a él
        for (Iterator<Pet> it = pets.values().iterator(); it.hasNext();) if (!it.next().seen) it.remove();
    }

    /** Dibuja las mascotas justo después de las entidades del mundo. */
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
            PetBrain b = pet.brain;
            double x = b.ix(pt), y = b.iy(pt), z = b.iz(pt);
            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y + 0.5, z));
            Model model = modelFor(PetSpec.kindFor(pet.item.id));
            ResourceLocation tex = pet.item.texture;
            RenderType type = RenderType.entityCutoutNoCull(tex);
            used.add(type);

            ps.pushPose();
            ps.translate(x - cam.x, y - cam.y, z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(180f - b.iyaw(pt)));
            ps.scale(-PetSpec.SCALE, -PetSpec.SCALE, PetSpec.SCALE);
            ps.translate(0f, -1.501f, 0f); // en el modelo los pies están en y = 24 (1.5 bloques)
            model.render(b, pt, ps, buf.getBuffer(type), light);
            ps.popPose();
        }
        for (RenderType t : used) buf.endBatch(t);
    }
}
