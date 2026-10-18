package com.atenas.social;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

/** Paleta de Atenas (la misma del cliente y del launcher) y un botón futurista con el mismo diseño que el cliente 1.8.9. */
public final class Ui {
    public static final int ACCENT = 0xFF8B5CF6, ACCENT_LIGHT = 0xFFA78BFA, MAGENTA = 0xFFC026D3, PINK = 0xFFEC4899, CYAN = 0xFF22D3EE;
    public static final int PANEL = 0xFF14122A, PANEL_HI = 0xFF1E1B38, LINE = 0xFF2B2848, NIGHT = 0xFF090812;
    public static final int TEXT = 0xFFF1EEFC, MUTED = 0xFF9B97B8, DIM = 0xFF3A3658, GOLD = 0xFFFACC15;

    /** Letra propia (font/ui.ttf). */
    public static final ResourceLocation UI_FONT = new ResourceLocation("atenassocial", "ui");
    /** Fuente con el logo de Atenas como un solo caracter (\uE000). */
    public static final ResourceLocation BADGE_FONT = new ResourceLocation("atenassocial", "badge");

    private Ui() {}

    /** Mezcla dos colores ARGB (f = 0 → a, f = 1 → b). */
    public static int mix(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int out = 0;
        for (int sh = 24; sh >= 0; sh -= 8) {
            int ca = (a >>> sh) & 255, cb = (b >>> sh) & 255;
            out |= ((int) (ca + (cb - ca) * f) & 255) << sh;
        }
        return out;
    }

    /** Barra horizontal con degradado de izquierda a derecha. */
    public static void hbar(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        for (int i = 0; i < w; i += 3) g.fill(x + i, y, Math.min(x + w, x + i + 3), y + h, mix(c1, c2, i / (float) Math.max(1, w)));
    }

    /** Ancho (en px de GUI, sin escala) que ocupa {@link #heading}. */
    public static int headingWidth(Font f, String s) {
        String up = s.toUpperCase(java.util.Locale.ROOT);
        int w = 0;
        for (int i = 0; i < up.length(); i++) w += f.width(ui(String.valueOf(up.charAt(i)))) + 1;
        return w;
    }

    /** Fondo noche con rejilla de neón en perspectiva, estrellas y línea de escaneo (el mismo del menú principal). */
    public static final class Backdrop {
        private static final int STARS = 110;
        private final float[] sx = new float[STARS], sy = new float[STARS], sv = new float[STARS], ss = new float[STARS];

        public Backdrop() {
            Random r = new Random(1234L);
            for (int i = 0; i < STARS; i++) { sx[i] = r.nextFloat(); sy[i] = r.nextFloat(); sv[i] = 0.2f + r.nextFloat() * 0.8f; ss[i] = r.nextFloat(); }
        }

        public void render(GuiGraphics g, int width, int height) {
            long t = System.currentTimeMillis();
            g.fillGradient(0, 0, width, height, 0xFF080A14, 0xFF171C3C);
            int hz = height - 90;
            g.fillGradient(0, hz - 40, width, hz, 0x008B5CF6, 0x268B5CF6);
            g.fill(0, hz, width, height, 0xFF070913);
            g.fill(0, hz, width, hz + 1, 0x808B5CF6);
            for (int i = 1; i < 7; i++) {
                float k = (float) Math.pow((i + (t % 2000) / 2000f) / 7f, 2.0);
                int yy = hz + (int) (k * (height - hz));
                g.fill(0, yy, width, yy + 1, (((int) (60 * k) + 14) << 24) | 0x8B5CF6);
            }
            for (int i = -10; i <= 10; i++) {
                int bx = width / 2 + i * (width / 14), ex = width / 2 + i * (width / 3), steps = 12;
                for (int s = 0; s < steps; s++) {
                    float a0 = s / (float) steps;
                    int px = bx + (int) ((ex - bx) * a0), py = hz + (int) ((height - hz) * a0);
                    g.fill(px, py, px + 1, py + (height - hz) / steps + 1, (((int) (10 + 40 * a0)) << 24) | 0x8B5CF6);
                }
            }
            for (int i = 0; i < STARS; i++) {
                float yy = (sy[i] * hz + t * sv[i] * 0.003f) % hz;
                int a = (int) (50 + 110 * (0.5 + 0.5 * Math.sin(t * 0.002 + i)));
                int size = ss[i] > 0.8f ? 2 : 1;
                int px = (int) (sx[i] * width);
                g.fill(px, (int) yy, px + size, (int) yy + size, (a << 24) | 0xFFFFFF);
            }
            int scan = (int) ((t / 14) % (height + 80)) - 40;
            g.fillGradient(0, scan, width, scan + 14, 0x008B5CF6, 0x128B5CF6);
        }
    }

    public static MutableComponent ui(String s) { return Component.literal(s).withStyle(Style.EMPTY.withFont(UI_FONT)); }

    public static void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** Rectángulo con las esquinas cortadas en diagonal. */
    public static void chamfer(GuiGraphics g, int x, int y, int w, int h, int cut, int c) {
        g.fill(x, y + cut, x + w, y + h - cut, c);
        for (int i = 0; i < cut; i++) {
            int inset = cut - i;
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, c);
        }
    }

    public static void chamferBorder(GuiGraphics g, int x, int y, int w, int h, int cut, int c) {
        g.fill(x + cut, y, x + w - cut, y + 1, c);
        g.fill(x + cut, y + h - 1, x + w - cut, y + h, c);
        g.fill(x, y + cut, x + 1, y + h - cut, c);
        g.fill(x + w - 1, y + cut, x + w, y + h - cut, c);
        for (int i = 0; i < cut; i++) {
            g.fill(x + cut - 1 - i, y + i, x + cut - i, y + i + 1, c);
            g.fill(x + w - cut + i, y + i, x + w - cut + i + 1, y + i + 1, c);
            g.fill(x + cut - 1 - i, y + h - 1 - i, x + cut - i, y + h - i, c);
            g.fill(x + w - cut + i, y + h - 1 - i, x + w - cut + i + 1, y + h - i, c);
        }
    }

    /** Botón futurista: cristal oscuro con esquinas cortadas, borde de neón y brillo animado al pasar el mouse; "primary" lleva degradado violeta → rosa. */
    public static final class Btn extends Button {
        private final boolean primary;
        private float glow;

        public Btn(int x, int y, int w, int h, Component label, boolean primary, OnPress onPress) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hot = isHoveredOrFocused() && active;
            glow += ((hot ? 1f : 0f) - glow) * 0.22f;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight(), cut = h >= 18 ? 4 : 3;
            if (primary) {
                chamfer(g, x, y, w, h, cut, 0xFF2A1A5E);
                g.fillGradient(x + cut, y + 1, x + w - cut, y + h - 1, hot ? 0xFFA78BFA : ACCENT, hot ? 0xFFEC4899 : 0xFF6D4FC7);
                chamferBorder(g, x, y, w, h, cut, hot ? 0xFFFFFFFF : ACCENT_LIGHT);
            } else {
                chamfer(g, x, y, w, h, cut, active ? 0xE60D0B1E : 0xA0090812);
                int a = (int) (0x46 * glow);
                g.fillGradient(x + cut, y + h / 2, x + w - cut, y + h - 1, 0x008B5CF6, (a << 24) | 0x8B5CF6);
                chamferBorder(g, x, y, w, h, cut, hot ? ACCENT_LIGHT : (active ? 0xFF3A3466 : 0xFF241F3C));
                int bar = (int) (glow * (w - 2 * cut));
                if (bar > 0) {
                    g.fillGradient(x + w / 2 - bar / 2, y + h - 2, x + w / 2 + bar / 2, y + h - 1, ACCENT, CYAN);
                }
                g.fill(x + w - cut - 5, y, x + w - cut, y + 1, hot ? CYAN : 0xFF6D4FC7);
                g.fill(x + cut, y + h - 1, x + cut + 5, y + h, hot ? PINK : 0xFF6D4FC7);
            }
            Font f = net.minecraft.client.Minecraft.getInstance().font;
            String up = getMessage().getString().toUpperCase(java.util.Locale.ROOT);
            Component label = ui(up);
            int tw = f.width(label) + up.length(); // +1 px entre letras
            int tx = x + (w - tw) / 2 + (int) (glow * 1.5f), ty = y + (h - 8) / 2;
            int col = active ? (hot ? 0xFFFFFFFF : TEXT) : DIM;
            for (int i = 0; i < up.length(); i++) {
                Component ch = ui(String.valueOf(up.charAt(i)));
                g.drawString(f, ch, tx, ty, col, true);
                tx += f.width(ch) + 1;
            }
        }
    }

    /** Texto en mayúsculas con letras separadas, a una escala; centrado en cx o desde cx si !centered. */
    public static void heading(GuiGraphics g, Font f, String s, int cx, int y, int color, float scale, boolean centered) {
        String up = s.toUpperCase(java.util.Locale.ROOT);
        int w = 0;
        for (int i = 0; i < up.length(); i++) w += f.width(ui(String.valueOf(up.charAt(i)))) + 1;
        g.pose().pushPose();
        g.pose().translate(centered ? cx - w * scale / 2f : cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        int x = 0;
        for (int i = 0; i < up.length(); i++) {
            Component ch = ui(String.valueOf(up.charAt(i)));
            g.drawString(f, ch, x, 0, color, true);
            x += f.width(ch) + 1;
        }
        g.pose().popPose();
    }

    /** Botón del menú principal (igual que el del cliente): título a la izquierda, pista a la derecha, barra de acento que crece. */
    public static final class MenuBtn extends Button {
        private final String sub;
        private final boolean secondary;
        private float glow;

        public MenuBtn(int x, int y, int w, int h, Component label, String sub, boolean secondary, OnPress onPress) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
            this.sub = sub;
            this.secondary = secondary;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hot = isHoveredOrFocused() && active;
            glow += ((hot ? 1f : 0f) - glow) * 0.22f;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight(), cut = 4;
            chamfer(g, x, y, w, h, cut, secondary ? 0xCC0A0C1B : 0xE60C0F20);
            int a = (int) (0x40 * glow);
            g.fillGradient(x + cut, y + 1, x + w - cut, y + h - 1, (a << 24) | 0x8B5CF6, (a << 24) | 0xEC4899);
            chamferBorder(g, x, y, w, h, cut, hot ? ACCENT : 0xFF2A2E52);
            int bh = 6 + (int) (glow * (h - 12));
            g.fill(x + 3, y + (h - bh) / 2, x + 5, y + (h - bh) / 2 + bh, hot ? CYAN : (secondary ? 0xFF4A4F78 : 0xFF6D4FC7));
            Font f = net.minecraft.client.Minecraft.getInstance().font;
            int ty = y + (h - 8) / 2;
            int col = active ? (hot ? 0xFFFFFFFF : TEXT) : DIM;
            heading(g, f, getMessage().getString(), x + 14 + (int) (glow * 3), ty, col, 1f, false);
            if (sub != null) g.drawString(f, ui(sub), x + w - f.width(ui(sub)) - 12, ty, hot ? CYAN : MUTED, true);
        }
    }
}
