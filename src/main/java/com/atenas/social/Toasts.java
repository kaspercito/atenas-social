package com.atenas.social;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Avisos propios de Atenas (solicitudes, mensajes, amigos en línea), iguales a los del cliente 1.8.9. Cada aviso trae la cara del
 * jugador, entra deslizándose desde la derecha con un pequeño rebote, se acomoda suave cuando otro desaparece, muestra una barrita
 * que se vacía y sale deslizándose. Se dibujan desde {@link ClientInit} (RenderGuiEvent.Post en la partida y
 * ScreenEvent.Render.Post sobre cualquier pantalla).
 */
public final class Toasts {
    private static final int LIFE_MS = 5500, IN_MS = 380, OUT_MS = 320, W = 176, H = 34, GAP = 4, TOP = 6, MAX = 4;

    /** Apagar los avisos (no se guarda: es solo para quien quiera silenciarlos desde código). */
    public static volatile boolean enabled = true;

    private static final class Toast {
        final String title, text, face;
        final int accent;
        final long born = System.currentTimeMillis();
        float y = -1f; // -1 = todavía sin posición: aparece directamente en su sitio
        Toast(String title, String text, String face, int accent) { this.title = title; this.text = text; this.face = face; this.accent = accent; }
    }

    private static final List<Toast> LIST = new ArrayList<>();
    private static long lastFrame;

    private Toasts() {}

    /** Desde cualquier hilo. "face" es el nick del jugador (o null si el aviso no es de nadie). El color "accent" lleva alfa: 0xFFRRGGBB. */
    public static synchronized void push(String title, String text, String face, int accent) {
        if (!enabled) return;
        if (LIST.size() >= MAX) LIST.remove(0);
        LIST.add(new Toast(title, text == null ? "" : text, face, accent));
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static int withAlpha(int rgb, float a) {
        int al = Math.max(0, Math.min(255, (int) (a * 255f)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    /** Dibuja los avisos (hilo principal). Una vez por cuadro. */
    public static synchronized void draw(GuiGraphics g) {
        long now = System.currentTimeMillis();
        for (int i = LIST.size() - 1; i >= 0; i--) if (now - LIST.get(i).born > LIFE_MS) LIST.remove(i);
        if (LIST.isEmpty()) { lastFrame = now; return; }
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();

        g.pose().pushPose();
        g.pose().translate(0f, 0f, 400f); // por encima de la pantalla y de sus textos
        RenderSystem.enableBlend();
        for (int i = 0; i < LIST.size(); i++) {
            Toast t = LIST.get(i);
            float target = TOP + i * (H + GAP);
            t.y = t.y < 0f ? target : t.y + (target - t.y) * Math.min(1f, dt * 12f);

            long age = now - t.born;
            float in = Math.min(1f, age / (float) IN_MS);
            float out = Math.max(0f, (age - (LIFE_MS - OUT_MS)) / (float) OUT_MS);
            float slide = (1f - easeOutBack(in)) * (W + 12) + out * out * (W + 12);
            float alpha = Math.min(1f, age / 160f) * (1f - out * 0.6f);

            int x = sw - W - 6 + (int) slide, y = (int) t.y;
            g.fill(x, y, x + W, y + H, withAlpha(Ui.NIGHT, 0.90f * alpha));
            g.fill(x, y, x + 2, y + H, withAlpha(t.accent, alpha));
            Ui.border(g, x, y, W, H, withAlpha(0x2A3558, 0.7f * alpha));

            // cara (o la inicial si todavía no llegó)
            int fx = x + 7, fy = y + 5, fs = 24;
            Faces.Face face = t.face == null ? null : Faces.get(t.face);
            if (face != null) {
                g.setColor(1f, 1f, 1f, alpha);
                g.blit(face.tex, fx, fy, fs, fs, 0f, 0f, face.w, face.h, face.w, face.h);
                g.setColor(1f, 1f, 1f, 1f);
            } else if (t.face != null && alpha > 0.05f) {
                g.fill(fx, fy, fx + fs, fy + fs, withAlpha(0x2A2550, alpha));
                String ini = t.face.substring(0, 1).toUpperCase(Locale.ROOT);
                g.drawString(font, ini, fx + (fs - font.width(ini)) / 2, fy + 8, withAlpha(Ui.ACCENT_LIGHT, alpha), false);
            }
            int tx = t.face != null ? fx + fs + 7 : x + 8, tw = W - (tx - x) - 6;
            if (alpha > 0.05f) {
                g.drawString(font, font.plainSubstrByWidth(t.title, tw), tx, y + 6, withAlpha(t.accent, alpha), false);
                g.drawString(font, font.plainSubstrByWidth(t.text, tw), tx, y + 18, withAlpha(Ui.TEXT, alpha), false);
            }

            // barrita de tiempo que se vacía
            int bw = (int) (W * Math.max(0f, 1f - age / (float) LIFE_MS));
            g.fill(x, y + H - 2, x + bw, y + H, withAlpha(t.accent, 0.55f * alpha));
        }
        RenderSystem.disableBlend();
        g.pose().popPose();
    }
}
