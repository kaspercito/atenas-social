package com.atenas.social;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Paleta de Atenas (la misma del cliente y del launcher) y un botón con ese estilo. */
public final class Ui {
    public static final int ACCENT = 0xFF8B5CF6, ACCENT_LIGHT = 0xFFA78BFA, MAGENTA = 0xFFC026D3, PINK = 0xFFEC4899, CYAN = 0xFF22D3EE;
    public static final int PANEL = 0xFF14122A, PANEL_HI = 0xFF1E1B38, LINE = 0xFF2B2848, NIGHT = 0xFF090812;
    public static final int TEXT = 0xFFF1EEFC, MUTED = 0xFF9B97B8, DIM = 0xFF3A3658, GOLD = 0xFFFACC15;

    private Ui() {}

    public static void border(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** Botón del menú: oscuro con borde; "primary" lleva el degradado violeta → rosa. */
    public static final class Btn extends Button {
        private final boolean primary;

        public Btn(int x, int y, int w, int h, Component label, boolean primary, OnPress onPress) {
            super(x, y, w, h, label, onPress, DEFAULT_NARRATION);
            this.primary = primary;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            boolean hot = isHoveredOrFocused() && active;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            if (primary) {
                g.fillGradient(x, y, x + w, y + h, hot ? 0xFFA78BFA : ACCENT, hot ? 0xFFEC4899 : 0xFF6D4FC7);
            } else {
                g.fill(x, y, x + w, y + h, hot ? PANEL_HI : PANEL);
                border(g, x, y, w, h, hot ? ACCENT : LINE);
                if (hot) g.fill(x, y, x + 2, y + h, ACCENT_LIGHT);
            }
            Font f = net.minecraft.client.Minecraft.getInstance().font;
            g.drawCenteredString(f, getMessage(), x + w / 2, y + (h - 8) / 2, active ? TEXT : DIM);
        }
    }
}
