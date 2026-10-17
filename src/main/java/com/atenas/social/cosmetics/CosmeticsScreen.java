package com.atenas.social.cosmetics;

import com.atenas.social.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Elegir capa, alas y sombrero. Con un mundo abierto te ves puesto en vivo. */
public class CosmeticsScreen extends Screen {
    private static final int CW = 62, CH = 58, GAP = 6, PREVIEW_W = 118;

    private final Screen parent;
    private Cosmetics.Type tab = Cosmetics.Type.CAPE;
    private int px, py, pw, ph, gx, gy, gw, gh, scroll;

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Cosm\u00e9ticos"));
        this.parent = parent;
    }

    @Override
    protected void init() {
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
        l.addAll(Cosmetics.of(tab));
        return l;
    }

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
        renderBackground(g);
        g.fill(px, py, px + pw, py + ph, 0xF00E0C20);
        g.fill(px, py, px + pw, py + 2, Ui.ACCENT);
        g.drawString(font, "COSM\u00c9TICOS", px + 8, py + 8, Ui.TEXT, false);
        g.drawString(font, "Los ven todos los que usan Atenas", px + 8 + font.width("COSM\u00c9TICOS") + 8, py + 8, Ui.MUTED, false);

        // pesta\u00f1as resaltadas
        // (los botones ya se dibujan solos; subrayamos la activa)
        int tw = Math.min(70, (gw - 8) / Cosmetics.Type.values().length - 4), ti = 0;
        for (Cosmetics.Type t : Cosmetics.Type.values()) {
            if (t == tab) g.fill(gx + ti * (tw + 4), py + 44, gx + ti * (tw + 4) + tw, py + 46, Ui.ACCENT_LIGHT);
            ti++;
        }

        // cuadr\u00edcula
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
                g.drawCenteredString(font, font.plainSubstrByWidth(it.name, CW - 4), x + CW / 2, y + CH - 14, on ? Ui.TEXT : Ui.MUTED);
            }
            if (on) g.fill(x + CW - 9, y + 3, x + CW - 3, y + 9, Ui.ACCENT_LIGHT);
        }
        g.disableScissor();

        // vista previa
        int rx = px + pw - PREVIEW_W - 4, ry = py + 26, rw = PREVIEW_W - 4, rh = ph - 80;
        g.fill(rx, ry, rx + rw, ry + rh, Ui.NIGHT);
        Ui.border(g, rx, ry, rw, rh, Ui.LINE);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            int cx = rx + rw / 2, feet = ry + rh - 22;
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, feet, 42, cx - mx, feet - 66 - my, mc.player);
        } else {
            g.drawCenteredString(font, "Entra a un mundo", rx + rw / 2, ry + rh / 2 - 14, Ui.TEXT);
            g.drawCenteredString(font, "para verte puesto", rx + rw / 2, ry + rh / 2 - 3, Ui.MUTED);
        }
        int ly = ry + rh - 12;
        StringBuilder sb = new StringBuilder();
        for (Cosmetics.Type t : Cosmetics.Type.values()) {
            Cosmetics.Item s = Cosmetics.selected(t);
            if (s != null) { if (sb.length() > 0) sb.append(" \u00b7 "); sb.append(s.name); }
        }
        g.drawCenteredString(font, font.plainSubstrByWidth(sb.length() == 0 ? "Sin cosm\u00e9ticos" : sb.toString(), rw - 6), rx + rw / 2, ly, Ui.MUTED);

        super.render(g, mx, my, pt);
    }
}
