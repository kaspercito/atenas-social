package com.atenas.social.cosmetics;

/**
 * Movimiento de la capa de tela. NO usa nada de Minecraft: calcula cuanto gira cada tira de la cadena (la capa son SEGS tiras
 * que cuelgan una de otra). Las dos versiones del juego usan este mismo calculo.
 */
public final class CapeWave {
    private CapeWave() {}

    /** Filas de la capa: la capa mide 16 de alto, cada fila 1 (16 filas comparten vertices, ver CapeModel: sin cortes entre ellas). */
    public static final int SEGS = 16;
    public static final int SEG_H = 16 / SEGS;

    /**
     * Rellena los giros de cada tira. La tira 0 (la de los hombros) no se dobla: la gira el cuerpo entero.
     *
     * @param bend  cuanto se dobla la capa en total (radianes, hacia atras); se reparte entre las articulaciones
     * @param amp   fuerza de la onda (radianes en la punta); 0 = cuelga quieta
     * @param time  tiempo en ticks
     * @param side  balanceo lateral total (radianes), repartido igual
     * @param rx    salida: giro de cada tira alrededor de x (adelante/atras)
     * @param rz    salida: giro de cada tira alrededor de z (a los lados)
     */
    /**
     * Signo del giro en el marco de la malla: ese marco esta girado 180 grados respecto al cuerpo (la cara de afuera es -z), asi que
     * un giro POSITIVO alrededor de x acerca la punta al cuerpo. Para curvar hacia atras hay que girar en negativo. Si en el juego la
     * capa se curvara al reves, basta con poner aqui 1f.
     */
    private static final float BACK = -1f;

    public static void angles(float bend, float amp, float time, float side, float[] rx, float[] rz) {
        float per = bend / (SEGS - 1);
        float k8 = 8f / SEGS; // la onda se pensó para 8 tiras: con mas filas se reparte para que la tela ondee igual
        rx[0] = 0f;
        rz[0] = 0f;
        float cum = 0f;
        for (int i = 1; i < SEGS; i++) {
            float k = (float) i / (SEGS - 1); // 0 arriba, 1 en la punta
            float wave = (float) Math.sin(time * 0.30f - i * 0.85f * k8) * amp * k8 * (0.35f + 0.65f * k);
            float r = per + wave;
            // r > 0 = la tela se curva HACIA ATRAS (lejos del cuerpo); el giro acumulado nunca baja de 0 (no se mete al cuerpo)
            if (cum + r < 0f) r = -cum;
            cum += r;
            rx[i] = BACK * r;
            rz[i] = side / (SEGS - 1) + (float) Math.sin(time * 0.21f - i * 0.6f * k8 + 1.3f) * amp * 0.25f * k8 * k;
        }
    }

    /** Fuerza de la onda segun lo fuerte que se mueve la capa (swing = empuje hacia atras al correr, 0..150). */
    public static float ampFor(float swing) { return 0.08f + 0.20f * Math.min(1f, swing / 60f); }
}
