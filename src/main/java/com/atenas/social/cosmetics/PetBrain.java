package com.atenas.social.cosmetics;

/**
 * Cerebro de una mascota cosmética. NO usa nada de Minecraft: recibe la posición del dueño y un "suelo" (interfaz Ground)
 * y calcula dónde está la mascota y qué animación hace. Se ejecuta una vez por tick (20 veces por segundo) y el dibujo
 * interpola entre el estado anterior y el actual.
 *
 * Comportamiento: sigue al dueño a un paso tranquilo (corre si se queda lejos), se sienta si el dueño está quieto o agachado,
 * salta pequeños escalones, mira al dueño, mueve la cola y jadea al correr. Si se atasca o queda muy lejos, aparece junto al dueño.
 * Unidades: bloques y ticks. Yaw en grados con la convención de Minecraft (0 = sur/+z, 90 = oeste/-x).
 */
public final class PetBrain {
    /** El mundo, visto desde la mascota. */
    public interface Ground {
        /** Altura de la superficie (y de la cara de arriba) del suelo más alto que esté a "fromY" o más abajo (hasta 8 bloques), o NaN si no hay. */
        double ground(double x, double z, double fromY);
    }

    public static final int IDLE = 0, WALK = 1, RUN = 2, SIT = 3, AIR = 4;

    // ---- estado actual y anterior (para interpolar al dibujar)
    public double x, y, z, px, py, pz;
    public float yaw, pyaw;
    public float walkPos, pwalkPos, swing, pswing;
    public float sit, psit;         // 0 = de pie, 1 = sentado
    public float mouth, pmouth;     // boca abierta (jadeo) 0..1
    public float headYaw, pheadYaw, headPitch, pheadPitch; // relativos al cuerpo (grados)
    public float tail, ptail;       // ángulo de meneo (radianes), ya calculado
    public float earTwitch, pearTwitch;
    public int mode = IDLE;
    public int age;
    public boolean spawned;

    private double vy;
    private int idleTicks, stuck, twitchLeft, nextTwitch = 60;
    private double lastOx, lastOz;
    private final java.util.Random rnd;

    public PetBrain(long seed) { rnd = new java.util.Random(seed); }

    public static final double FOLLOW_NEAR = 2.2, FOLLOW_FAR = 3.6, RUN_DIST = 6.0, TELEPORT_DIST = 16.0;

    public void teleport(double ox, double oy, double oz, float ownerYaw) {
        double r = Math.toRadians(ownerYaw);
        // junto al dueño, un poco a su izquierda y atrás
        x = ox + Math.cos(r) * 1.0 + Math.sin(r) * 0.8;
        z = oz + Math.sin(r) * 1.0 - Math.cos(r) * 0.8;
        y = oy;
        px = x; py = y; pz = z; vy = 0; stuck = 0;
        pyaw = yaw = ownerYaw;
        spawned = true;
    }

    public void tick(double ox, double oy, double oz, float ownerYaw, boolean ownerSneaking, Ground g) {
        age++;
        if (!spawned) { teleport(ox, oy, oz, ownerYaw); lastOx = ox; lastOz = oz; }
        // guardar lo anterior
        px = x; py = y; pz = z; pyaw = yaw; pwalkPos = walkPos; pswing = swing; psit = sit; pmouth = mouth;
        pheadYaw = headYaw; pheadPitch = headPitch; ptail = tail; pearTwitch = earTwitch;

        double dx = ox - x, dz = oz - z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        double ownerSpeed = Math.sqrt((ox - lastOx) * (ox - lastOx) + (oz - lastOz) * (oz - lastOz));
        lastOx = ox; lastOz = oz;

        if (dist > TELEPORT_DIST || Math.abs(oy - y) > 9 || stuck > 40) { teleport(ox, oy, oz, ownerYaw); dx = ox - x; dz = oz - z; dist = Math.sqrt(dx * dx + dz * dz); }

        // ---- ¿hay que moverse?
        boolean chasing = mode == WALK || mode == RUN;
        boolean wantMove = dist > (chasing ? FOLLOW_NEAR : FOLLOW_FAR);
        double speed = 0;
        if (wantMove) {
            speed = 0.10 + Math.max(0, dist - FOLLOW_NEAR) * 0.045;
            if (dist > RUN_DIST) speed += 0.05;
            if (speed > 0.34) speed = 0.34;
            if (!chasing) speed *= 0.6; // arranque suave
            idleTicks = 0;
        } else idleTicks++;

        // ---- mover con colisión sencilla (escalón de 1 bloque, sin saltar al vacío)
        boolean moved = false;
        if (speed > 0) {
            double len = Math.max(dist, 1e-6), sx = dx / len * speed, sz = dz / len * speed;
            double x0 = x, z0 = z;
            if (!tryStep(x + sx, z + sz, g)) { if (!tryStep(x + sx, z, g)) tryStep(x, z + sz, g); }
            // solo cuenta como avance si de verdad se movió (deslizarse un pelo por una pared no cuenta)
            double got = Math.sqrt((x - x0) * (x - x0) + (z - z0) * (z - z0));
            moved = got > speed * 0.4;
            if (!moved) { stuck++; if (onGround()) vy = 0.42; } // saltar para librar el obstáculo
            else stuck = Math.max(0, stuck - 2);
        } else stuck = Math.max(0, stuck - 1);

        // ---- vertical: seguir el suelo, saltar escalones, caer con gravedad
        double gy = g.ground(x, z, Math.max(oy, y) + 1.2);
        if (Double.isNaN(gy)) gy = Math.min(y, oy);
        if (y < gy) {
            if (gy - y <= 1.05 && vy <= 0) { vy = gy - y > 0.55 ? 0.40 : 0; if (vy == 0) y = gy; }
            else if (vy <= 0) vy = 0.42;
        }
        if (vy != 0 || y > gy) {
            y += vy; vy -= 0.08; vy *= 0.98;
            if (y <= gy && vy <= 0) { y = gy; vy = 0; }
        }
        boolean air = y > gy + 0.05;

        // ---- orientación: hacia donde camina; quieto, mira al dueño despacio
        float want = yaw;
        if (moved) want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        else if (dist > 0.6) want = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float maxTurn = moved ? 26f : 6f;
        yaw += clampf(wrap(want - yaw), -maxTurn, maxTurn);

        // ---- modo
        boolean ownerStill = ownerSpeed < 0.02;
        if (air) mode = AIR;
        else if (moved) mode = speed > 0.21 ? RUN : WALK;
        else if (ownerSneaking || (ownerStill && idleTicks > 100 && dist < 4.5)) mode = SIT;
        else mode = IDLE;
        if (mode == SIT && !ownerStill && !ownerSneaking) mode = IDLE;

        // ---- animaciones
        float targetSwing = mode == RUN ? 1f : mode == WALK ? 0.7f : 0f;
        swing += (targetSwing - swing) * 0.3f;
        walkPos += (float) (moved ? speed * 5.0 : 0) + (mode == RUN ? 0.25f : 0f);
        sit += ((mode == SIT ? 1f : 0f) - sit) * 0.12f;
        if (sit < 0.01f) sit = 0f;
        mouth += ((mode == RUN ? 1f : mode == WALK ? 0.35f : 0f) - mouth) * 0.15f;
        float lookYaw = clampf(wrap((float) Math.toDegrees(Math.atan2(-dx, dz)) - yaw), -55f, 55f);
        headYaw += ((mode == IDLE || mode == SIT ? lookYaw : 0f) - headYaw) * 0.12f;
        headPitch += ((mode == SIT ? -10f : mode == RUN ? 8f : 0f) - headPitch) * 0.1f;
        float wagSpeed = mode == RUN ? 0.9f : (mode == IDLE || mode == SIT ? 0.55f : 0.35f);
        float wagAmp = mode == RUN ? 0.35f : (dist < 3.5 ? 0.55f : 0.25f);
        tail = (float) Math.sin(age * wagSpeed) * wagAmp;
        if (--nextTwitch <= 0) { twitchLeft = 8; nextTwitch = 50 + rnd.nextInt(120); }
        if (twitchLeft > 0) { twitchLeft--; earTwitch = (float) Math.sin(twitchLeft * 1.6) * 0.5f; } else earTwitch = 0f;
    }

    private boolean onGround() { return vy == 0; }

    private boolean tryStep(double nx, double nz, Ground g) {
        double gy = g.ground(nx, nz, y + 1.1);
        if (Double.isNaN(gy)) return false;
        if (gy - y > 1.05) return false;      // pared
        if (y - gy > 3.2) return false;       // precipicio
        x = nx; z = nz;
        return true;
    }

    private static float wrap(float a) { while (a > 180f) a -= 360f; while (a < -180f) a += 360f; return a; }
    private static float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // ---- valores interpolados para dibujar (pt = 0..1)
    public double ix(float pt) { return px + (x - px) * pt; }
    public double iy(float pt) { return py + (y - py) * pt; }
    public double iz(float pt) { return pz + (z - pz) * pt; }
    public float iyaw(float pt) { return pyaw + wrap(yaw - pyaw) * pt; }
    public float iwalk(float pt) { return pwalkPos + (walkPos - pwalkPos) * pt; }
    public float iswing(float pt) { return pswing + (swing - pswing) * pt; }
    public float isit(float pt) { return psit + (sit - psit) * pt; }
    public float imouth(float pt) { return pmouth + (mouth - pmouth) * pt; }
    public float iheadYaw(float pt) { return pheadYaw + (headYaw - pheadYaw) * pt; }
    public float iheadPitch(float pt) { return pheadPitch + (headPitch - pheadPitch) * pt; }
    public float itail(float pt) { return ptail + (tail - ptail) * pt; }
    public float iear(float pt) { return pearTwitch + (earTwitch - pearTwitch) * pt; }
}
