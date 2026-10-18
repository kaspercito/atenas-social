package com.atenas.social.cosmetics;

import com.atenas.social.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Elegir capa, alas, sombrero y mascota. Te ves puesto en vivo: con un mundo abierto con tu jugador, y sin mundo con un maniqui que gira ({@link CosmeticPreview}). */
public class CosmeticsScreen extends Screen {
    private final Ui.Backdrop backdrop = new Ui.Backdrop();
    private static final int CW = 62, CH = 58, GAP = 6, PREVIEW_W = 118;

    private final Screen parent;
    private Cosmetics.Type tab = Cosmetics.Type.CAPE;
    private int px, py, pw, ph, gx, gy, gw, gh, scroll;
    /** La vista previa sin mundo fallo una vez (algo del juego no coopero): se muestra el texto de respaldo y no se reintenta. */
    private boolean previewFailed;

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Cosm\u00e9ticos"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        CosmeticSync.refreshNow();
        pw = Math.min(width - 16, 440);
        ph = Math.min(height - 16, 270);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        gx = px + 8;
        gy = py + 52;
        gw = pw - PREVIEW_W - 24;
        gh = ph - 60;
        int tw = Math.min(70, (gw - 8) / Cosmetics.Type.values().length - 4);
        int i = 0;
        for (Cosmetics.Type t : Cosmetics.Type.values()) {
            final Cosmetics.Type tt = t;
            addRenderableWidget(new Ui.Btn(gx + i * (tw + 4), py + 26, tw, 18, Component.literal(t.label), false, b -> { tab = tt; scroll = 0; }));
            i++;
        }
        final Ui.Btn anim = new Ui.Btn(px + pw - PREVIEW_W - 4, py + ph - 48, PREVIEW_W - 4, 18, animLabel(), false, b -> {
            Cosmetics.toggleAnimate();
            b.setMessage(animLabel());
        });
        addRenderableWidget(anim);
        addRenderableWidget(new Ui.Btn(px + pw - PREVIEW_W - 4, py + ph - 26, PREVIEW_W - 4 + 0, 18, Component.literal("Listo"), true, b -> onClose()));
    }

    private static Component animLabel() { return Component.literal("Animadas: " + (Cosmetics.animate ? "S\u00ed" : "No")); }

    private List<Cosmetics.Item> entries() {
        List<Cosmetics.Item> l = new ArrayList<>();
        l.add(null); // "Ninguno"
        l.addAll(Cosmetics.visible(tab)); // sin los privados ajenos (ej. Catalina)
        return l;
    }


    /** Recorta con "..." (antes se cortaba en seco: "Atenas (Ex", "Oceano al"). */
    private String fit(String s, int w) {
        if (font.width(s) <= w) return s;
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width("..."))) + "...";
    }

    private Cosmetics.Item hovered;

    private int cols() { return Math.max(1, (gw + GAP) / (CW + GAP)); }

    private int maxScroll() {
        int rows = (entries().size() + cols() - 1) / cols();
        return Math.max(0, rows * (CH + GAP) - gh);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) (delta * 24)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && mx >= gx && mx < gx + gw && my >= gy && my < gy + gh) {
            List<Cosmetics.Item> list = entries();
            for (int i = 0; i < list.size(); i++) {
                int x = gx + (i % cols()) * (CW + GAP), y = gy + (i / cols()) * (CH + GAP) - scroll;
                if (mx >= x && mx < x + CW && my >= y && my < y + CH) {
                    Cosmetics.select(tab, list.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (minecraft.level != null) g.fillGradient(0, 0, width, height, 0xC0070914, 0xE00E0B22);
        else backdrop.render(g, width, height);
        for (int k = 3; k >= 1; k--) Ui.chamfer(g, px - k * 2, py - k * 2, pw + k * 4, ph + k * 4, 6 + k * 2, (0x16 / k << 24) | 0x8B5CF6);
        Ui.chamfer(g, px, py, pw, ph, 6, 0xF4090818);
        Ui.chamferBorder(g, px, py, pw, ph, 6, 0xFF3A3466);
        Ui.hbar(g, px + 6, py, pw - 12, 2, Ui.ACCENT, Ui.PINK);
        Ui.heading(g, font, "Cosm\u00e9ticos", px + 10, py + 8, Ui.TEXT, 1f, false);
        int hw = Ui.headingWidth(font, "Cosm\u00e9ticos");
        boolean warn = !Cosmetics.notice.isEmpty() && System.currentTimeMillis() < Cosmetics.noticeUntil;
        String syncProblem = warn ? null : CosmeticSync.problem(); // la sincronizacion esta fallando: se dice por que
        if (syncProblem != null) warn = true;
        g.drawString(font, font.plainSubstrByWidth(syncProblem != null ? syncProblem : warn ? Cosmetics.notice : "Los ven todos los que usan Atenas", pw - hw - 30),
            px + 10 + hw + 10, py + 8, warn ? 0xFFFFB454 : Ui.MUTED, false);

        // pesta\u00f1as resaltadas
        // (los botones ya se dibujan solos; subrayamos la activa)
        int tw = Math.min(70, (gw - 8) / Cosmetics.Type.values().length - 4), ti = 0;
        for (Cosmetics.Type t : Cosmetics.Type.values()) {
            if (t == tab) g.fill(gx + ti * (tw + 4), py + 44, gx + ti * (tw + 4) + tw, py + 46, Ui.ACCENT_LIGHT);
            ti++;
        }

        // cuadr\u00edcula
        hovered = null;
        g.enableScissor(gx, gy, gx + gw, gy + gh);
        List<Cosmetics.Item> list = entries();
        Cosmetics.Item cur = Cosmetics.selected(tab);
        for (int i = 0; i < list.size(); i++) {
            Cosmetics.Item it = list.get(i);
            int x = gx + (i % cols()) * (CW + GAP), y = gy + (i / cols()) * (CH + GAP) - scroll;
            if (y + CH < gy || y > gy + gh) continue;
            boolean on = it == cur, hot = mx >= x && mx < x + CW && my >= y && my < y + CH && my >= gy && my < gy + gh;
            g.fill(x, y, x + CW, y + CH, hot ? Ui.PANEL_HI : Ui.PANEL);
            Ui.border(g, x, y, CW, CH, on ? Ui.ACCENT_LIGHT : hot ? Ui.ACCENT : Ui.LINE);
            if (it == null) {
                g.drawCenteredString(font, "\u2715", x + CW / 2, y + 14, Ui.MUTED);
                g.drawCenteredString(font, "Ninguno", x + CW / 2, y + CH - 14, on ? Ui.TEXT : Ui.MUTED);
            } else {
                g.blit(it.icon, x + (CW - 32) / 2, y + 6, 32, 32, 0, 0, 32, 32, 32, 32);
                g.drawCenteredString(font, fit(it.name, CW - 4), x + CW / 2, y + CH - 14, on ? Ui.TEXT : Ui.MUTED);
                if (hot) hovered = it;
            }
            if (on) g.fill(x + CW - 9, y + 3, x + CW - 3, y + 9, Ui.ACCENT_LIGHT);
        }
        g.disableScissor();

        // vista previa
        int rx = px + pw - PREVIEW_W - 4, ry = py + 26, rw = PREVIEW_W - 4, rh = ph - 80;
        g.fill(rx, ry, rx + rw, ry + rh, Ui.NIGHT);
        Ui.border(g, rx, ry, rw, rh, Ui.LINE);
        Minecraft mc = Minecraft.getInstance();
        int cx = rx + rw / 2, feet = ry + rh - 22;
        if (mc.player != null && mc.level != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feet, 42, cx - mx, feet - 66 - my, mc.player);
        } else {
            boolean drawn = false;
            if (!previewFailed) {
                g.enableScissor(rx + 1, ry + 1, rx + rw - 1, ry + rh - 1);
                try {
                    CosmeticPreview.draw(g, cx, feet, 42);
                    drawn = true;
                } catch (Throwable t) {
                    previewFailed = true;
                    System.err.println("[AtenasSocial] vista previa sin mundo: " + t);
                } finally {
                    g.disableScissor();
                }
            }
            if (!drawn) {
                g.drawCenteredString(font, "Entra a un mundo", rx + rw / 2, ry + rh / 2 - 14, Ui.TEXT);
                g.drawCenteredString(font, "para verte puesto", rx + rw / 2, ry + rh / 2 - 3, Ui.MUTED);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Cosmetics.Type t : Cosmetics.Type.values()) {
            Cosmetics.Item s = Cosmetics.selected(t);
            if (s != null) { if (sb.length() > 0) sb.append(" \u00b7 "); sb.append(s.name); }
        }
        // el resumen puede ser largo (capa + alas + sombrero + mascota): se parte en hasta 2 lineas en vez de cortarse
        java.util.List<net.minecraft.util.FormattedCharSequence> lines = font.split(
            net.minecraft.network.chat.Component.literal(sb.length() == 0 ? "Sin cosm\u00e9ticos" : sb.toString()), rw - 6);
        int shown = Math.min(2, lines.size()), ly = ry + rh - 3 - shown * 9;
        for (int li = 0; li < shown; li++) {
            net.minecraft.util.FormattedCharSequence ln = lines.get(li);
            g.drawString(font, ln, rx + (rw - font.width(ln)) / 2, ly + li * 9, Ui.MUTED, false);
        }

        super.render(g, mx, my, pt);
        if (hovered != null && font.width(hovered.name) > CW - 4) g.renderTooltip(font, net.minecraft.network.chat.Component.literal(hovered.name), mx, my);
    }
}
