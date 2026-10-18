package com.atenas.social.cosmetics;

/**
 * Cerebro de una mascota cosmetica. NO usa nada de Minecraft: recibe el estado del dueño ({@link Owner}) y un "suelo" ({@link Ground})
 * y calcula donde esta la mascota y que animacion hace. Se ejecuta una vez por tick (20 por segundo); el dibujo interpola.
 *
 * Mascotas de suelo: siguen al dueño (corren si se alejan, saltan escalones, se sientan si el dueño se queda quieto), NADAN junto a el
 * en el agua (los gatos y conejos no: se suben a su hombro) y se SUBEN AL HOMBRO cuando el dueño vuela (elytras / creativo), cae de
 * lejos, trepa, va montado o nada.
 * Mascotas voladoras (aves, dragon): vuelan a su lado (planean con las elytras, aletean cerca del suelo) y se posan en el hombro
 * cuando el dueño se queda quieto.
 * Unidades: bloques y ticks. Yaw en grados con la convencion de Minecraft (0 = sur/+z, 90 = oeste/-x).
 */
public final class PetBrain {
    /** El mundo, visto desde la mascota. */
    public interface Ground {
        /** Altura de la superficie del suelo mas alto que este a "fromY" o mas abajo (hasta 8 bloques), o NaN si no hay. */
        double ground(double x, double z, double fromY);
        /** Altura de la superficie del agua en esa columna cerca de "y" (si hay agua ahi), o NaN. */
        default double water(double x, double z, double y) { return Double.NaN; }
    }

    /** Lo que la mascota sabe de su dueño, una vez por tick (lo llena PetRender). */
    public static final class Owner {
        public double x, y, z;
        /** Giro de la cabeza, del cuerpo (grados) y cabeceo (grados, positivo = mirando hacia abajo). */
        public float yaw, bodyYaw, pitch;
        public boolean sneaking, sprinting, fallFlying, flying, swimming, inWater, riding, climbing, onGround;
    }

    public static final int IDLE = 0, WALK = 1, RUN = 2, SIT = 3, AIR = 4, SWIM = 5, PERCH = 6, FLY = 7, GLIDE = 8;

    // ---- rasgos del modelo (los pone PetRender con configure)
    public boolean flier, avoidWater, hop;

    // ---- estado actual y anterior (para interpolar al dibujar). x,y,z = donde se DIBUJA (ya mezclado con el hombro)
    public double x, y, z, px, py, pz;
    public float yaw, pyaw, pitch, ppitch, roll, proll;
    public float walkPos, pwalkPos, swing, pswing;
    public float sit, psit, mouth, pmouth, swim, pswim, perch, pperch, fly, pfly, flap, pflap, spread, pspread;
    public float headYaw, pheadYaw, headPitch, pheadPitch, headTilt, pheadTilt;
    public float tail, ptail, earTwitch, pearTwitch, flapAmp, pflapAmp;
    public float wagSpeed = 0.5f, wagAmp = 0.4f;
    public int mode = IDLE;

    /** Atencion (0..1): sube cuando te acercas o la miras; la mascota te mira, mueve la cola y te saluda. */
    public float attention;
    /** Evento de sonido de este tick (lo lee y reproduce el render; la logica no sabe de Minecraft). */
    public static final int SND_NONE = 0, SND_AMBIENT = 1, SND_GREET = 2;
    public int sound;

    /** Gesto en curso sobre el hombro (0 = ninguno; 1..PetSpec.GESTURES). Lo que hace cada uno depende del tipo de mascota: ver PetSpec.gesture*. gp = progreso 0..1. */
    public int gest;
    public float gp, pgp;
    private int gLen = 60, nextGest = 40, lastGest;
    public float igp(float pt) { return li(pgp, gp, pt); }
    private int nextAmbient = 500, greetCool = 100, farTicks;
    private boolean joyJump;
    public int age;
    public boolean spawned;

    // ---- estado fisico (sin mezclar con el hombro)
    private double sx, sy, sz, vy, svx, svy, svz;
    private float syaw;
    private float perchT;            // 0..1 lineal; perch = suavizado
    private boolean perchWant;
    private int flip, idleTicks, stuck, twitchLeft, nextTwitch = 60, stillTicks, airTicks, lookLeft, chirpLeft, nextChirp = 80;
    private double lastOx, lastOy, lastOz, ovx, ovz, ovy;
    private float fb;                // 0..1: cuerpo del dueño tumbado (elytras / nadando)
    private float lookTarget, lookPitchTarget;
    private final java.util.Random rnd;
    private final double[] tmp = new double[3];

    public PetBrain(long seed) { rnd = new java.util.Random(seed); }

    public void configure(boolean flier, boolean avoidWater, boolean hop) { this.flier = flier; this.avoidWater = avoidWater; this.hop = hop; }

    public static final double FOLLOW_NEAR = 2.2, FOLLOW_FAR = 3.6, RUN_DIST = 6.0, TELEPORT_DIST = 28.0;

    // ================================================================== compatibilidad con la version anterior
    public void teleport(double ox, double oy, double oz, float ownerYaw) {
        double r = Math.toRadians(ownerYaw);
        sx = ox + Math.cos(r) * 1.0 + Math.sin(r) * 0.8;
        sz = oz + Math.sin(r) * 1.0 - Math.cos(r) * 0.8;
        sy = flier ? oy + 1.6 : oy;
        x = sx; y = sy; z = sz; px = x; py = y; pz = z; vy = 0; stuck = 0; svx = svy = svz = 0;
        pyaw = yaw = syaw = ownerYaw;
        spawned = true;
    }

    /** Version simple (vista previa del menu): dueño quieto en el suelo. */
    public void tick(double ox, double oy, double oz, float ownerYaw, boolean ownerSneaking, Ground g) {
        Owner o = new Owner();
        o.x = ox; o.y = oy; o.z = oz; o.yaw = o.bodyYaw = ownerYaw; o.sneaking = ownerSneaking; o.onGround = true;
        tick(o, g);
    }

    // ================================================================== tick
    public void tick(Owner o, Ground g) {
        age++;
        sound = SND_NONE;
        pgp = gp;
        if (!spawned) { teleport(o.x, o.y, o.z, o.bodyYaw); lastOx = o.x; lastOy = o.y; lastOz = o.z; }
        px = x; py = y; pz = z; pyaw = yaw; ppitch = pitch; proll = roll;
        pwalkPos = walkPos; pswing = swing; psit = sit; pmouth = mouth; pswim = swim; pperch = perch; pfly = fly; pflap = flap; pspread = spread;
        pheadYaw = headYaw; pheadPitch = headPitch; pheadTilt = headTilt; ptail = tail; pearTwitch = earTwitch; pflapAmp = flapAmp;

        // ---- el dueño
        ovx = o.x - lastOx; ovy = o.y - lastOy; ovz = o.z - lastOz;
        lastOx = o.x; lastOy = o.y; lastOz = o.z;
        double ownerSpeed = Math.sqrt(ovx * ovx + ovz * ovz);
        boolean flightLike = o.fallFlying || (o.flying && !o.onGround);
        stillTicks = (ownerSpeed < 0.03 && Math.abs(ovy) < 0.05 && !flightLike) ? stillTicks + 1 : 0;
        airTicks = (!o.onGround && !o.inWater && !flightLike && !o.climbing) ? airTicks + 1 : 0;
        fb += (((o.fallFlying || o.swimming) ? 1f : 0f) - fb) * 0.2f;

        if (dist2(o.x, o.z, sx, sz) > TELEPORT_DIST * TELEPORT_DIST || Math.abs(o.y - sy) > (flier ? 40 : 9)) { teleport(o.x, o.y, o.z, o.bodyYaw); }

        // ---- ¿subirse al hombro?
        double dToOwner = Math.sqrt(dist2(o.x, o.z, sx, sz));
        boolean want;
        if (flier) {
            want = stillTicks > 50 && !o.inWater && !o.swimming && !o.riding && dToOwner < 6;
        } else {
            double wsurf = g.water(sx, sz, sy + 0.3);
            boolean petDeep = !Double.isNaN(wsurf) && wsurf - groundOr(g, sx, sz, sy, o.y) > 0.7;
            want = flightLike || o.riding || o.climbing || airTicks > 20
                    || (avoidWater && (o.inWater || petDeep))
                    || (avoidWater && stillTicks > 160 && dToOwner < 3.2 && !o.sneaking);
            // al aterrizar / salir del agua no baja hasta que el dueño se mueva con normalidad (evita parpadeos)
        }
        if (want != perchWant) { if (++flip > 5) { perchWant = want; flip = 0; } } else flip = 0;
        perchT += perchWant ? 0.07f : -0.09f;
        perchT = perchT < 0f ? 0f : (perchT > 1f ? 1f : perchT);
        perch = perchT * perchT * (3f - 2f * perchT);

        shoulder(o, tmp);
        double shX = tmp[0], shY = tmp[1], shZ = tmp[2];

        // ---- ¿te ve? (te acercas o la miras): se pone atenta, te mira, mueve la cola y a veces te saluda
        double adx = x - o.x, ady = (y + 0.3) - (o.y + 1.62), adz = z - o.z, ad = Math.sqrt(adx * adx + ady * ady + adz * adz);
        boolean noticed = false;
        if (ad < 7.0 && ad > 0.3 && perch < 0.2f) {
            double ly = Math.toRadians(o.yaw), lp = Math.toRadians(o.pitch);
            double lx = -Math.sin(ly) * Math.cos(lp), lyy = -Math.sin(lp), lz = Math.cos(ly) * Math.cos(lp);
            noticed = (lx * adx + lyy * ady + lz * adz) / ad > 0.93;      // la estas mirando (unos 20 grados)
        }
        boolean returned = false;
        if (ad > 6.0) farTicks++; else if (ad < 3.2) { if (farTicks >= 40) returned = true; farTicks = 0; }
        float attT = perch > 0.2f ? 0f : (noticed ? 1f : (ad < 2.4 ? 0.55f : 0f));
        float prevAtt = attention;
        attention += (attT - attention) * 0.15f;
        if (greetCool > 0) greetCool--;
        if (perch < 0.2f && greetCool <= 0 && ((attention > 0.7f && prevAtt <= 0.7f) || returned)) {
            sound = SND_GREET; greetCool = 400;
            if (hop || !avoidWater) joyJump = !flier;                       // saltito de alegria (conejo y los de suelo que no temen al agua)
        }
        if (--nextAmbient <= 0) { nextAmbient = 600 + rnd.nextInt(900); if (sound == SND_NONE) sound = SND_AMBIENT; }

        // ---- simulacion fisica
        if (flier) simFlier(o, g, ownerSpeed, flightLike, shX, shY, shZ);
        else simGround(o, g, ownerSpeed, flightLike);

        // ---- mezcla con el hombro (salto en arco al subir / bajar)
        double arc = Math.sin(Math.PI * perchT) * (flier ? 0.25 : 0.9);
        if (perch > 0.001f) {
            x = sx + (shX - sx) * perch; z = sz + (shZ - sz) * perch;
            y = sy + (shY - sy) * perch + arc;
        } else { x = sx; y = sy; z = sz; }
        if (perch >= 0.999f && !flier) { sx = o.x + 0.8; sz = o.z; sy = o.y; vy = 0; landSpot(o, g); }

        // ---- orientacion y gestos
        float fbBody = o.bodyYaw;
        yaw = syaw + wrap(fbBody - syaw) * perch;
        float tiltOwner = 0f; // sobre su espalda la mascota va de pie, como en el mundo
        float flyPitch = flier ? flyPitchOf() : 0f;
        pitch = tiltOwner * perch + flyPitch * (1f - perch);
        float targetRoll = flier ? clampf(-wrap(syaw - pyawSim) * 0.05f, -0.7f, 0.7f) * (1f - perch) : 0f;
        roll += (targetRoll - roll) * 0.2f;
        pyawSim = syaw;

        animate(o, ownerSpeed, flightLike);
    }

    private float pyawSim;

    private float flyPitchOf() {
        double hs = Math.sqrt(svx * svx + svz * svz);
        return (float) (-Math.atan2(svy, Math.max(hs, 0.12)) * 0.8);
    }

    // ------------------------------------------------------------------ hombro derecho del dueño (en el marco del mundo)
    private void shoulder(Owner o, double[] out) {
        double by = Math.toRadians(o.bodyYaw);
        double rx = -Math.cos(by), rz = -Math.sin(by), fx = -Math.sin(by), fz = Math.cos(by);
        double ax = 0, ay = 1, az = 0, nx = 0, ny = 1, nz = 0;
        if (fb > 0.01f) {
            double ly = Math.toRadians(o.yaw), lp = Math.toRadians(o.pitch);
            double lx = -Math.sin(ly) * Math.cos(lp), lyy = -Math.sin(lp), lz = Math.cos(ly) * Math.cos(lp);
            double ux = -Math.sin(ly) * Math.sin(lp), uy = Math.cos(lp), uz = Math.cos(ly) * Math.sin(lp);
            ax += (lx - ax) * fb; ay += (lyy - ay) * fb; az += (lz - az) * fb;
            nx += (ux - nx) * fb; ny += (uy - ny) * fb; nz += (uz - nz) * fb;
        }
        double h = o.sneaking && fb < 0.5f ? 1.35 : 1.47;   // la mascota pisa su hombrera (ver PetRender): 1.47 = parte de arriba de la hombrera
        double fwd = (o.sneaking && fb < 0.5f) ? 0.12 : 0.0;
        double side = 0.44;   // sobre el hombro/brazo, fuera del ancho de la cabeza (a 0.30 quedaba medio tapada por la cabeza)
        double nn = 0.02 + 0.12 * fb;
        out[0] = o.x + ax * h + rx * side + fx * fwd + nx * nn;
        out[1] = o.y + ay * h + ny * nn;
        out[2] = o.z + az * h + rz * side + fz * fwd + nz * nn;
        if (o.riding) out[1] -= 0.45;
    }

    private void landSpot(Owner o, Ground g) {
        double by = Math.toRadians(o.bodyYaw);
        double lx = o.x + (-Math.cos(by)) * 0.9 + (-Math.sin(by)) * 0.3, lz = o.z + (-Math.sin(by)) * 0.9 + Math.cos(by) * 0.3;
        double gy = g.ground(lx, lz, o.y + 1.2);
        sx = lx; sz = lz; sy = Double.isNaN(gy) ? o.y : gy;
    }

    private double groundOr(Ground g, double gx, double gz, double fromY, double dflt) {
        double v = g.ground(gx, gz, fromY + 1.2);
        return Double.isNaN(v) ? dflt : v;
    }

    // ------------------------------------------------------------------ mascota de suelo
    private void simGround(Owner o, Ground g, double ownerSpeed, boolean flightLike) {
        double ox = o.x, oy = o.y, oz = o.z;
        double dx = ox - sx, dz = oz - sz;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (perchT > 0.5f && perchWant) { /* ya va en el hombro: solo gestos */ mode = PERCH; stuck = 0; syaw = o.bodyYaw; return; }

        boolean chasing = mode == WALK || mode == RUN || mode == SWIM;
        boolean wantMove = dist > (chasing ? FOLLOW_NEAR : FOLLOW_FAR);
        double speed = 0;
        if (wantMove) {
            speed = 0.10 + Math.max(0, dist - FOLLOW_NEAR) * 0.045;
            if (dist > RUN_DIST) speed += 0.05;
            if (speed > 0.34) speed = 0.34;
            if (!chasing) speed *= 0.6;
            idleTicks = 0;
        } else idleTicks++;

        double ws = g.water(sx, sz, sy + 0.3);
        double gyHere = groundOr(g, sx, sz, sy, Math.min(sy, oy));
        boolean deep = !Double.isNaN(ws) && ws - gyHere > 0.7;
        if (deep) speed *= 0.75;

        boolean moved = false;
        if (speed > 0) {
            double len = Math.max(dist, 1e-6), mx = dx / len * speed, mz = dz / len * speed;
            double x0 = sx, z0 = sz;
            if (!tryStep(sx + mx, sz + mz, g, deep) && !tryStep(sx + mx, sz, g, deep)) tryStep(sx, sz + mz, g, deep);
            double got = Math.sqrt((sx - x0) * (sx - x0) + (sz - z0) * (sz - z0));
            moved = got > speed * 0.4;
            if (!moved) { stuck++; if (vy == 0 && !deep) vy = 0.42; } else stuck = Math.max(0, stuck - 2);
        } else stuck = Math.max(0, stuck - 1);
        if (stuck > 40) { teleport(ox, oy, oz, o.bodyYaw); return; }

        // vertical
        double gy = g.ground(sx, sz, Math.max(oy, sy) + 1.2);
        if (Double.isNaN(gy)) gy = Math.min(sy, oy);
        if (joyJump) { if (vy == 0 && sy <= gy + 0.02) vy = 0.30; joyJump = false; }
        boolean swimming = false;
        double wsurf = g.water(sx, sz, sy + 0.3);
        if (!Double.isNaN(wsurf) && wsurf - gy > 0.7) {
            swimming = true;                                   // aguas hondas: flota (el cuerpo queda medio hundido)
            double target = wsurf - 0.42;
            sy += (target - sy) * 0.3; vy = 0;
        } else {
            if (sy < gy) {
                if (gy - sy <= 1.05 && vy <= 0) { vy = gy - sy > 0.55 ? 0.40 : 0; if (vy == 0) sy = gy; }
                else if (vy <= 0) vy = 0.42;
            }
            if (vy != 0 || sy > gy) {
                sy += vy; vy -= 0.08; vy *= 0.98;
                if (sy <= gy && vy <= 0) { sy = gy; vy = 0; }
            }
        }
        boolean air = !swimming && sy > gy + 0.05;

        float want = syaw;
        if (moved || dist > 0.6) want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        syaw += clampf(wrap(want - syaw), moved ? -26f : -6f, moved ? 26f : 6f);

        boolean ownerStill = ownerSpeed < 0.02;
        if (swimming) mode = SWIM;
        else if (air) mode = AIR;
        else if (moved) mode = speed > 0.21 ? RUN : WALK;
        else if (o.sneaking || (ownerStill && idleTicks > 100 && dist < 4.5)) mode = SIT;
        else mode = IDLE;
        if (mode == SIT && !ownerStill && !o.sneaking) mode = IDLE;
        walkSpeed = moved ? speed : 0;
    }

    private double walkSpeed;

    private boolean tryStep(double nx, double nz, Ground g, boolean deep) {
        double gy = g.ground(nx, nz, sy + 1.1);
        double ws = g.water(nx, nz, sy + 0.3);
        if (!Double.isNaN(ws) && (Double.isNaN(gy) || ws - gy > 0.7)) { sx = nx; sz = nz; return true; }   // nadar
        if (Double.isNaN(gy)) return false;
        if (gy - sy > 1.05) return false;
        if (sy - gy > 3.2 && !deep) return false;
        sx = nx; sz = nz;
        return true;
    }

    // ------------------------------------------------------------------ mascota voladora
    private void simFlier(Owner o, Ground g, double ownerSpeed, boolean flightLike, double shX, double shY, double shZ) {
        double by = Math.toRadians(o.bodyYaw);
        double rx = -Math.cos(by), rz = -Math.sin(by), fx = -Math.sin(by), fz = Math.cos(by);
        double tx, ty, tz, k;
        double bob = Math.sin(age * 0.11) * 0.18;
        if (perchWant) { tx = shX; ty = shY + 0.25; tz = shZ; k = 0.25; }
        else if (flightLike) {
            // Volando con el dueño: se queda a su lado, un poco atras (antes se adelantaba 4 veces su velocidad y se iba lejos)
            double lx = Math.max(-1.2, Math.min(1.2, ovx * 1.0)), ly = Math.max(-1.0, Math.min(1.0, ovy * 1.0)), lz = Math.max(-1.2, Math.min(1.2, ovz * 1.0));
            tx = o.x + lx + rx * 1.7 - fx * 0.6; ty = o.y + ly + 1.0 + bob; tz = o.z + lz + rz * 1.7 - fz * 0.6;
            k = 0.5;
        } else {
            double orb = age * 0.035;
            // al costado y un poco ATRAS del dueño, bajo la altura de sus ojos: asi no se cruza en su pantalla al caminar o girar
            double ahead = Math.max(-0.8, Math.min(0.8, ownerSpeed * 1.0));
            tx = o.x + rx * 1.3 + fx * (-0.7 + Math.cos(orb) * 0.3) + fx * ahead; ty = o.y + 1.35 + bob + Math.sin(orb * 1.3) * 0.12; tz = o.z + rz * 1.3 + fz * (-0.7 + Math.cos(orb) * 0.3) + fz * ahead;
            if (o.inWater || o.swimming) ty += 0.8;
            k = ownerSpeed > 0.1 ? 0.22 : 0.10;
        }
        double ox = sx, oy = sy, oz = sz;
        sx += (tx - sx) * k; sy += (ty - sy) * k; sz += (tz - sz) * k;
        double gy = g.ground(sx, sz, sy + 2.0);
        if (!Double.isNaN(gy) && sy < gy + 0.25) sy = gy + 0.25;
        if (!perchWant) {   // correa: nunca se aleja demasiado del dueño (volando de cerca, en el suelo un poco mas holgada)
            double cy = o.y + 1.0, ldx = sx - o.x, ldy = sy - cy, ldz = sz - o.z, ld = Math.sqrt(ldx * ldx + ldy * ldy + ldz * ldz), lmax = flightLike ? 3.5 : 7.0;
            if (ld > lmax) { double f = lmax / ld; sx = o.x + ldx * f; sy = cy + ldy * f; sz = o.z + ldz * f; }
        }
        svx = sx - ox; svy = sy - oy; svz = sz - oz;
        double hs = Math.sqrt(svx * svx + svz * svz);
        float want = syaw;
        if (perchWant && perchT > 0.6f) want = o.bodyYaw;
        else if (hs > 0.04) want = (float) Math.toDegrees(Math.atan2(-svx, svz));
        else want = o.yaw;
        syaw += clampf(wrap(want - syaw), -18f, 18f);
        double speedAll = Math.sqrt(svx * svx + svy * svy + svz * svz);
        boolean airborne = !(perchT > 0.5f && perchWant);
        if (!airborne) mode = PERCH;
        else if (flightLike || o.fallFlying) mode = GLIDE;
        else mode = FLY;
        walkSpeed = speedAll;
    }

    // ------------------------------------------------------------------ gestos
    private void animate(Owner o, double ownerSpeed, boolean flightLike) {
        // ---- gestos en el hombro: cada cierto tiempo hace algo (mirar, asearse, bostezar, aletear...)
        if (perch > 0.7f) {
            if (gest == 0) {
                if (--nextGest <= 0) {
                    int ng; do { ng = 1 + rnd.nextInt(PetSpec.GESTURES); } while (ng == lastGest);   // nunca repite el anterior
                    gest = lastGest = ng; gLen = 50 + rnd.nextInt(40); gp = 0f; pgp = 0f; nextGest = 35 + rnd.nextInt(70);
                    if (sound == SND_NONE && rnd.nextInt(2) == 0) sound = SND_AMBIENT;
                }
            } else { gp += 1f / gLen; if (gp >= 1f) { gest = 0; gp = 0f; pgp = 0f; } }
        } else {
            if (gest != 0) { gest = 0; gp = 0f; pgp = 0f; }
            if (nextGest > 50) nextGest = 50;
        }
        boolean perched = perch > 0.5f;
        if (flier) {
            float flyT = perched ? 0f : 1f;
            fly += (flyT - fly) * 0.15f;
            spread += (((mode == GLIDE) ? 1f : (mode == FLY ? 0.85f : 0f)) - spread) * 0.2f;
            float ampT = mode == GLIDE ? (0.12f + (float) Math.max(0, svy) * 1.2f) : (mode == FLY ? 1f : 0f);
            flapAmp += (clampf(ampT, 0f, 1f) - flapAmp) * 0.15f;
            flap += (mode == GLIDE ? 0.35f : 0.95f) + (float) walkSpeed * 0.8f;
            swing = 0; walkPos = 0; sit = 0;
        } else {
            float targetSwing = mode == RUN ? 1f : mode == WALK ? 0.7f : mode == SWIM ? 0.9f : 0f;
            swing += (targetSwing - swing) * 0.3f;
            walkPos += (float) (walkSpeed * 5.0) + (mode == RUN ? 0.25f : 0f) + (mode == SWIM ? 0.5f : 0f);
            float sitT = (mode == SIT) ? 1f : 0f;
            sit += (sitT - sit) * 0.12f;
            if (sit < 0.01f) sit = 0f;
            swim += ((mode == SWIM ? 1f : 0f) - swim) * 0.15f;
            if (swim < 0.01f) swim = 0f;
        }
        // boca: jadea al correr; las aves "pian" de vez en cuando
        float mt = mode == RUN ? 1f : mode == WALK ? 0.35f : mode == SWIM ? 0.4f : 0f;
        if (flier) {
            if (chirpLeft > 0) { chirpLeft--; mt = 0.5f + 0.5f * (float) Math.sin(chirpLeft * 1.7); }
            else if (--nextChirp <= 0) { chirpLeft = 10; nextChirp = 100 + rnd.nextInt(200); }
        }
        if (!flier && !hop) mt = Math.max(mt, 0.45f * attention);   // jadea / maulla al verte
        mouth += (mt - mouth) * 0.2f;

        // mirada: al dueño; en reposo, a veces curiosea a los lados
        double dx = o.x - sx, dz = o.z - sz;
        float lookYaw = clampf(wrap((float) Math.toDegrees(Math.atan2(-dx, dz)) - syaw), -55f, 55f);
        boolean calm = mode == IDLE || mode == SIT || mode == PERCH;
        if (calm) {
            if (--lookLeft <= 0) {
                lookLeft = 40 + rnd.nextInt(90);
                boolean away = rnd.nextInt(3) == 0;
                lookTarget = away ? (rnd.nextFloat() - 0.5f) * 110f : lookYaw;
                lookPitchTarget = away ? (rnd.nextFloat() - 0.5f) * 30f : 0f;
            }
        } else { lookTarget = 0f; lookPitchTarget = mode == RUN ? 8f : mode == SWIM ? -14f : 0f; lookLeft = 20; }
        if (attention > 0.4f && calm && !perched) { lookTarget = lookYaw; lookPitchTarget = -12f * attention; lookLeft = Math.max(lookLeft, 10); }   // te mira a ti
        if (perched) lookTarget = clampf(lookTarget, -70f, 70f);
        headYaw += (lookTarget - headYaw) * 0.10f;
        headPitch += (((mode == SIT ? -10f : 0f) + lookPitchTarget) - headPitch) * 0.1f;
        float tiltT = calm ? (float) Math.sin(age * 0.021) * 0.22f * (Math.abs(lookTarget) < 5f ? 1f : 0f) : 0f;
        headTilt += (tiltT - headTilt) * 0.08f;

        wagSpeed = mode == RUN ? 0.9f : (calm ? 0.55f : 0.35f);
        wagAmp = mode == RUN ? 0.35f : (Math.sqrt(dx * dx + dz * dz) < 3.5 || perched ? 0.55f : 0.25f);
        if (mode == SWIM) { wagSpeed = 0.5f; wagAmp = 0.15f; }
        if (flier) { wagAmp = 0.12f; wagSpeed = 0.2f; }
        if (attention > 0.05f && !flier && mode != SWIM) { wagSpeed += 0.45f * attention; wagAmp = Math.max(wagAmp, 0.30f + 0.30f * attention); }
        tail = (float) Math.sin(age * wagSpeed) * wagAmp;
        if (--nextTwitch <= 0) { twitchLeft = 8; nextTwitch = 50 + rnd.nextInt(120); }
        if (twitchLeft > 0) { twitchLeft--; earTwitch = (float) Math.sin(twitchLeft * 1.6) * 0.5f; } else earTwitch = 0f;
        if (attention > 0.05f) earTwitch += 0.25f * attention;   // orejas atentas
    }

    private static double dist2(double ax, double az, double bx, double bz) { return (ax - bx) * (ax - bx) + (az - bz) * (az - bz); }
    private static float wrap(float a) { while (a > 180f) a -= 360f; while (a < -180f) a += 360f; return a; }
    private static float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // ---- valores interpolados para dibujar (pt = 0..1)
    private static float li(float a, float b, float t) { return a + (b - a) * t; }
    public double ix(float pt) { return px + (x - px) * pt; }
    public double iy(float pt) { return py + (y - py) * pt; }
    public double iz(float pt) { return pz + (z - pz) * pt; }
    public float iyaw(float pt) { return pyaw + wrap(yaw - pyaw) * pt; }
    public float ipitch(float pt) { return li(ppitch, pitch, pt); }
    public float iroll(float pt) { return li(proll, roll, pt); }
    public float iwalk(float pt) { return li(pwalkPos, walkPos, pt); }
    public float iswing(float pt) { return li(pswing, swing, pt); }
    public float isit(float pt) { return li(psit, sit, pt); }
    public float imouth(float pt) { return li(pmouth, mouth, pt); }
    public float iswim(float pt) { return li(pswim, swim, pt); }
    public float iperch(float pt) { return li(pperch, perch, pt); }
    public float ifly(float pt) { return li(pfly, fly, pt); }
    public float iflap(float pt) { return li(pflap, flap, pt); }
    public float ispread(float pt) { return li(pspread, spread, pt); }
    public float iflapAmp(float pt) { return li(pflapAmp, flapAmp, pt); }
    public float iheadYaw(float pt) { return li(pheadYaw, headYaw, pt); }
    public float iheadPitch(float pt) { return li(pheadPitch, headPitch, pt); }
    public float iheadTilt(float pt) { return li(pheadTilt, headTilt, pt); }
    public float itail(float pt) { return li(ptail, tail, pt); }
    public float iear(float pt) { return li(pearTwitch, earTwitch, pt); }
}
