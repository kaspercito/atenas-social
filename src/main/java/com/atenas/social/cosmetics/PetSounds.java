package com.atenas.social.cosmetics;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Sonidos de las mascotas. Cada una tiene dos: uno "ambiente" (de vez en cuando, ronroneo/jadeo/pio) y uno de "saludo" (cuando te acercas
 * o la miras). Son sonidos del propio juego con otro tono y volumen: si el nombre no existe no pasa nada (solo no suena).
 * Para sonidos propios: pon un .ogg en assets/atenassocial/sounds/pets/, declaralo en sounds.json y cambia el nombre aqui por "atenassocial:pets/nombre".
 */
public final class PetSounds {
    private PetSounds() {}

    /** Poner en false para silenciar todas las mascotas. */
    public static volatile boolean enabled = true;

    private static final class S {
        final String name; final float pitch, vol;
        S(String name, float pitch, float vol) { this.name = name; this.pitch = pitch; this.vol = vol; }
    }

    private static final Map<String, S[]> T = new HashMap<String, S[]>();
    private static final Random RND = new Random();

    private static void put(String id, String amb, float ap, float av, String greet, float gp, float gv) {
        T.put(id, new S[] {new S(amb, ap, av), new S(greet, gp, gv)});
    }

    static {
        //  id          ambiente (nombre, tono, volumen)              saludo
        put("perro",      "entity.wolf.pant", 1.00f, 0.50f,   "entity.wolf.ambient",    1.15f, 0.55f);
        put("dalmata",    "entity.wolf.pant", 1.10f, 0.50f,   "entity.wolf.ambient",    1.30f, 0.55f);
        put("husky",      "entity.wolf.pant", 0.90f, 0.50f,   "entity.wolf.ambient",    0.85f, 0.55f);
        put("gato",       "entity.cat.purr",     1.00f, 0.40f,   "entity.cat.ambient",     1.10f, 0.50f);
        put("gatonegro",  "entity.cat.purr",     0.90f, 0.40f,   "entity.cat.ambient",     0.90f, 0.50f);
        put("catalina",   "entity.cat.purr",     1.20f, 0.40f,   "entity.cat.ambient",     1.30f, 0.50f);
        put("zorro",      "entity.fox.sniff",    1.00f, 0.45f,   "entity.fox.ambient",    1.20f, 0.50f);
        put("conejo",     "entity.rabbit.ambient",  1.00f, 0.45f,   "entity.rabbit.ambient",  1.25f, 0.50f);
        put("panda",      "entity.panda.ambient",      0.65f, 0.40f,   "entity.panda.ambient",      0.85f, 0.50f);
        put("loro",       "entity.parrot.ambient", 1.10f, 0.40f,   "entity.parrot.ambient",    1.45f, 0.45f);
        put("buho",       "entity.bat.ambient",     0.60f, 0.45f,   "entity.bat.ambient",     0.50f, 0.55f);
        put("dragoncito", "entity.blaze.ambient",1.30f, 0.35f,   "entity.ender_dragon.growl", 1.90f, 0.20f);
    }

    /** Reproduce el sonido de la mascota "id" para el evento {@link PetBrain#SND_AMBIENT} o {@link PetBrain#SND_GREET} en esa posicion. */
    public static void play(String id, int event, double x, double y, double z) {
        if (!enabled || event == PetBrain.SND_NONE) return;
        S[] t = T.get(id);
        if (t == null) t = T.get("perro");
        S s = t[event == PetBrain.SND_GREET ? 1 : 0];
        float pitch = s.pitch + (RND.nextFloat() - 0.5f) * 0.16f;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(s.name));
        if (ev == null) return;
        mc.level.playLocalSound(x, y, z, ev, SoundSource.NEUTRAL, s.vol, pitch, false);
    }
}
