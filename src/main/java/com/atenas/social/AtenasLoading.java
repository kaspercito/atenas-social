package com.atenas.social;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Pantalla de carga de Atenas en lugar de la de Mojang / Forge (la que sale al abrir Minecraft), dentro de la misma ventana.
 *
 * Como funciona: el juego crea su LoadingOverlay en el arranque; en el primer cuadro lo envolvemos con este Overlay. El original
 * SIGUE corriendo (es el que termina la recarga de recursos, avisa a Forge y abre el menu), pero lo tapamos por completo con
 * nuestra pantalla. Al terminar, se muestra el menu debajo y nuestra pantalla se desvanece justo cuando el original se quita.
 *
 * Seguridad: todo va dentro de try/catch; si algo falla se devuelve el overlay original y el juego sigue como siempre.
 * Los campos privados del original se buscan por TIPO (no por nombre), asi sirve igual en desarrollo y en el juego real.
 */
final class AtenasLoading extends Overlay {
    private static boolean handled;

    private final Minecraft mc;
    private final Overlay orig;
    private final ReloadInstance reload;
    private final long t0 = System.currentTimeMillis();
    private long doneAt = -1;
    private float shown;
    private boolean failed;

    private DynamicTexture logo;
    private final Map<String, Tex> texts = new HashMap<>();
    private Font baseFont;
    private final float[] sx = new float[70], sy = new float[70], ss = new float[70], sp = new float[70];

    private static final class Tex {
        final DynamicTexture tex; final int w, h;
        Tex(DynamicTexture tex, int w, int h) { this.tex = tex; this.w = w; this.h = h; }
    }

    private AtenasLoading(Minecraft mc, Overlay orig, ReloadInstance reload) {
        this.mc = mc;
        this.orig = orig;
        this.reload = reload;
        Random r = new Random(7);
        for (int i = 0; i < sx.length; i++) { sx[i] = r.nextFloat(); sy[i] = r.nextFloat() * 0.8f; ss[i] = 1f + r.nextInt(2); sp[i] = r.nextFloat() * 6.28f; }
    }

    /** Llamar en cada cuadro (inicio del render). Solo actua una vez: con el overlay de carga del arranque. */
    static void tryInstall(Minecraft mc) {
        if (handled) return;
        try {
            Overlay o = mc.getOverlay();
            if (o == null) return;           // todavia no hay (o ya termino)
            handled = true;                  // solo el de arranque: F3+T y otras recargas siguen con el normal
            if (!(o instanceof LoadingOverlay) || o instanceof AtenasLoading) return;
            ReloadInstance ri = findReload(o);
            if (ri == null) return;
            mc.setOverlay(new AtenasLoading(mc, o, ri));
        } catch (Throwable t) {
            handled = true;
        }
    }

    private static ReloadInstance findReload(Overlay o) throws IllegalAccessException {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (ReloadInstance.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    if (v instanceof ReloadInstance) return (ReloadInstance) v;
                }
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (failed) { orig.render(g, mx, my, pt); return; }
        try {
            orig.render(g, mx, my, pt); // la logica real (terminar recursos, avisar a Forge, quitar el overlay)
            long now = System.currentTimeMillis();
            if (doneAt < 0 && reload.isDone()) doneAt = now;
            float f = doneAt < 0 ? -1f : (now - doneAt) / 1000f;

            float target = doneAt < 0 ? Mth.clamp(reload.getActualProgress(), 0f, 0.97f) : 1f;
            shown += (target - shown) * 0.12f;
            if (shown > target) shown = target;

            float alpha = 1f;
            if (f >= 1f) {
                // debajo ya esta el menu: se vuelve a dibujar para tapar lo que el original deja al desvanecerse
                if (mc.screen != null) mc.screen.render(g, mx, my, pt);
                alpha = 1f - Mth.clamp(f - 1f, 0f, 1f);
            }
            if (alpha > 0.003f) draw(g, alpha, (now - t0) / 1000f);
        } catch (Throwable t) {
            failed = true;
            System.out.println("[Atenas] pantalla de carga propia desactivada: " + t);
            try { mc.setOverlay(orig); } catch (Throwable ignored) { /* el original ya esta corriendo */ }
        }
    }

    // ------------------------------------------------------------------ dibujo

    private static int a(int rgb, float alpha) {
        return (Mth.clamp((int) (alpha * 255f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int c0, int c1, float f) {
        int r = (int) (((c0 >> 16) & 255) + ((((c1 >> 16) & 255) - ((c0 >> 16) & 255)) * f));
        int gg = (int) (((c0 >> 8) & 255) + ((((c1 >> 8) & 255) - ((c0 >> 8) & 255)) * f));
        int b = (int) ((c0 & 255) + (((c1 & 255) - (c0 & 255)) * f));
        return (r << 16) | (gg << 8) | b;
    }

    private void draw(GuiGraphics g, float alpha, float t) {
        final int w = g.guiWidth(), h = g.guiHeight();
        final float k = Math.max(0.6f, Math.min(w / 427f, h / 240f)); // 1.0 = ventana de 427x240 unidades de GUI
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        g.fill(0, 0, w, h, a(0x07060F, alpha));
        g.fillGradient(0, (int) (h * 0.62f), w, h, a(0x8B5CF6, 0f), a(0x8B5CF6, 0.33f * alpha));
        for (int i = 0; i < sx.length; i++) {
            float tw = 0.25f + 0.55f * (0.5f + 0.5f * (float) Math.sin(t * 1.4f + sp[i]));
            int px = (int) (sx[i] * w), py = (int) (sy[i] * h), sz = Math.max(1, Math.round(ss[i] * Math.max(1f, k * 0.6f)));
            g.fill(px, py, px + sz, py + sz, a(0xFFFFFF, tw * 0.8f * alpha));
        }

        final int cx = w / 2;
        final int logoS = Math.round(64 * k), ly = Math.round(h / 2f - 92 * k);
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3f);
        for (int n = 3; n >= 1; n--) {
            int gs = logoS + Math.round(n * 10 * k);
            g.fill(cx - gs / 2, ly + logoS / 2 - gs / 2, cx + gs / 2, ly + logoS / 2 + gs / 2, a(0x8B5CF6, (0.03f + 0.04f * pulse) / n * alpha));
        }
        if (logo == null) logo = loadLogo();
        if (logo != null) quad(g, logo.getId(), cx - logoS / 2f, ly, logoS, logoS, alpha);

        float y = ly + logoS + 8 * k;
        y += text(g, "ATENAS", 26 * k, 0xA78BFA, cx, y, true, alpha) + 1;
        y += text(g, "M  O  D   \u00b7   1.20.1", 8 * k, 0x22D3EE, cx, y, true, alpha) + 6 * k;
        g.fill(Math.round(cx - 80 * k), (int) y, Math.round(cx + 80 * k), (int) y + 1, a(0x6D4FC7, alpha));
        g.fill(cx - Math.round(12 * k), (int) y - 1, cx + Math.round(12 * k), (int) y + 2, a(0x8B5CF6, alpha));

        // barra de carga
        final int bw = Math.round(200 * k), bh = Math.max(3, Math.round(4 * k));
        final int bx = cx - bw / 2, by = Math.round(h / 2f + 62 * k);
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, a(0x2B2848, alpha));
        g.fill(bx, by, bx + bw, by + bh, a(0x0B0E1C, alpha));
        int fill = Math.round(bw * Mth.clamp(shown, 0f, 1f));
        for (int i = 0; i < fill; i++) {
            float p = (float) i / bw;
            int c = p < 0.5f ? mix(0x22D3EE, 0x8B5CF6, p / 0.5f) : mix(0x8B5CF6, 0xEC4899, (p - 0.5f) / 0.5f);
            g.fill(bx + i, by, bx + i + 1, by + bh, a(c, alpha));
        }
        if (fill > 1) g.fill(bx + fill - 1, by - 1, bx + fill, by + bh + 1, a(0xFFFFFF, 0.9f * alpha));

        String status = doneAt < 0 ? Lang.t("Cargando recursos", "Loading resources") : Lang.t("Abriendo el men\u00fa", "Opening the menu");
        text(g, status, 7.5f * k, 0x9B97B8, cx - Math.round(14 * k), by + bh + 5 * k, false, alpha);
        text(g, Math.round(Mth.clamp(shown, 0f, 1f) * 100f) + "%", 7.5f * k, 0xF1EEFC, bx + bw - Math.round(8 * k), by + bh + 5 * k, false, alpha);
    }

    private void quad(GuiGraphics g, int texId, float x, float y, float w, float h, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texId);
        RenderSystem.enableBlend();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int c = 255, al = Mth.clamp((int) (alpha * 255f), 0, 255);
        b.vertex(m, x, y, 0).uv(0, 0).color(c, c, c, al).endVertex();
        b.vertex(m, x, y + h, 0).uv(0, 1).color(c, c, c, al).endVertex();
        b.vertex(m, x + w, y + h, 0).uv(1, 1).color(c, c, c, al).endVertex();
        b.vertex(m, x + w, y, 0).uv(1, 0).color(c, c, c, al).endVertex();
        BufferUploader.drawWithShader(b.end());
    }

    // ------------------------------------------------------------------ texturas propias (sin recursos ni fuentes del juego: aun no estan cargados)

    private DynamicTexture loadLogo() {
        try (InputStream in = AtenasLoading.class.getResourceAsStream("/assets/atenassocial/textures/gui/logo.png")) {
            if (in == null) return null;
            return new DynamicTexture(NativeImage.read(in));
        } catch (Throwable t) {
            return null;
        }
    }

    /** Texto centrado en cx con la letra de Atenas (ui.ttf) pintada con Java2D. "size" en unidades de GUI. Devuelve el alto usado. */
    private float text(GuiGraphics g, String s, float size, int rgb, float cx, float y, boolean bold, float alpha) {
        if (s == null || s.isEmpty()) return size;
        int scale = Math.max(1, (int) mc.getWindow().getGuiScale());
        String key = s + "|" + Math.round(size * 10) + "|" + rgb + "|" + bold + "|" + scale;
        Tex t = texts.get(key);
        if (t == null) {
            t = makeText(s, Math.max(8, Math.round(size * scale)), rgb, bold);
            if (texts.size() > 40) { for (Tex o : texts.values()) if (o.tex != null) o.tex.close(); texts.clear(); }
            texts.put(key, t);
        }
        if (t.tex == null) return size;
        float dw = t.w / (float) scale, dh = t.h / (float) scale;
        quad(g, t.tex.getId(), cx - dw / 2f, y, dw, dh, alpha);
        return dh;
    }

    private Tex makeText(String s, int px, int rgb, boolean bold) {
        try {
            if (baseFont == null) {
                try (InputStream in = AtenasLoading.class.getResourceAsStream("/assets/atenassocial/font/ui.ttf")) {
                    baseFont = in != null ? Font.createFont(Font.TRUETYPE_FONT, in) : new Font("SansSerif", Font.PLAIN, 12);
                }
            }
            Font f = baseFont.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) px);
            BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D pg = probe.createGraphics();
            pg.setFont(f);
            FontMetrics fm = pg.getFontMetrics();
            int tw = fm.stringWidth(s) + 8, th = fm.getHeight() + 6, asc = fm.getAscent();
            pg.dispose();
            BufferedImage img = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(f);
            g2.setColor(new Color(rgb));
            g2.drawString(s, 4, 3 + asc);
            g2.dispose();
            NativeImage ni = new NativeImage(tw, th, false);
            for (int yy = 0; yy < th; yy++) {
                for (int xx = 0; xx < tw; xx++) {
                    int p = img.getRGB(xx, yy); // ARGB -> NativeImage usa ABGR
                    ni.setPixelRGBA(xx, yy, (p & 0xFF00FF00) | ((p >> 16) & 0xFF) | ((p & 0xFF) << 16));
                }
            }
            return new Tex(new DynamicTexture(ni), tw, th);
        } catch (Throwable e) {
            return new Tex(null, 0, 0);
        }
    }
}
