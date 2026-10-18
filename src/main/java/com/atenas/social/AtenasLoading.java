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
            System.out.println("[Atenas] pantalla de carga propia instalada sobre " + o.getClass().getName());
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

    private static final String[] TIPS_ES = {"Sincronizando tu identidad", "Cargando cosm\u00e9ticos y mascotas", "Conectando con Atenas", "Preparando tu men\u00fa"};
    private static final String[] TIPS_EN = {"Syncing your identity", "Loading cosmetics and pets", "Connecting to Atenas", "Preparing your menu"};

    private void px(GuiGraphics g, float x, float y, float sz, int rgb, float al) {
        if (al <= 0.003f) return;
        int ix = Math.round(x - sz / 2f), iy = Math.round(y - sz / 2f), is = Math.max(1, Math.round(sz));
        g.fill(ix, iy, ix + is, iy + is, a(rgb, al));
    }

    private void bracket(GuiGraphics g, int x, int y, int len, int dx, int dy, int rgb, float al) {
        g.fill(Math.min(x, x + dx * len), y, Math.max(x, x + dx * len) + 1, y + 1, a(rgb, al));
        g.fill(x, Math.min(y, y + dy * len), x + 1, Math.max(y, y + dy * len) + 1, a(rgb, al));
    }

    private void draw(GuiGraphics g, float alpha, float t) {
        final int w = g.guiWidth(), h = g.guiHeight();
        final float k = Math.max(0.6f, Math.min(w / 427f, h / 240f)); // 1.0 = ventana de 427x240 unidades de GUI
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // fondo: degradado profundo + resplandor inferior
        g.fillGradient(0, 0, w, h, a(0x04030B, alpha), a(0x120A2E, alpha));
        g.fillGradient(0, (int) (h * 0.55f), w, h, a(0x22D3EE, 0f), a(0x8B5CF6, 0.20f * alpha));

        // rejilla en perspectiva que avanza hacia ti
        final float horizon = h * 0.58f, flow = (t * 0.35f) % 1f;
        for (int i = 0; i < 14; i++) {
            float p = (i + flow) / 14f;
            int gy = Math.round(horizon + (h - horizon) * p * p);
            g.fill(0, gy, w, gy + 1, a(0x8B5CF6, (0.05f + 0.20f * p) * alpha));
        }
        for (int i = -10; i <= 10; i++) {
            float gx = w / 2f + i * (w / 16f);
            for (int yy = (int) horizon; yy < h; yy += 3) {
                float p = (yy - horizon) / (h - horizon);
                int xx = Math.round(w / 2f + (gx - w / 2f) * (0.15f + p * 1.1f));
                g.fill(xx, yy, xx + 1, yy + 1, a(0x22D3EE, (0.03f + 0.10f * p) * alpha));
            }
        }
        for (int i = 0; i < sx.length; i++) {
            float tw = 0.25f + 0.55f * (0.5f + 0.5f * (float) Math.sin(t * 1.4f + sp[i]));
            int ppx = (int) (sx[i] * w), ppy = (int) (sy[i] * h * 0.75f), sz = Math.max(1, Math.round(ss[i] * Math.max(1f, k * 0.6f)));
            g.fill(ppx, ppy, ppx + sz, ppy + sz, a(0xFFFFFF, tw * 0.7f * alpha));
        }

        // marco HUD en las esquinas
        final int m = Math.round(14 * k), bl = Math.round(22 * k);
        bracket(g, m, m, bl, 1, 1, 0x22D3EE, 0.8f * alpha);
        bracket(g, w - m - 1, m, bl, -1, 1, 0x22D3EE, 0.8f * alpha);
        bracket(g, m, h - m - 1, bl, 1, -1, 0x8B5CF6, 0.8f * alpha);
        bracket(g, w - m - 1, h - m - 1, bl, -1, -1, 0x8B5CF6, 0.8f * alpha);

        final int cx = w / 2;
        final int logoS = Math.round(60 * k), ly = Math.round(h / 2f - 98 * k);
        final float lcx = cx, lcy = ly + logoS / 2f;

        // halo pulsante
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.6f);
        for (int n = 4; n >= 1; n--) {
            int gs = logoS + Math.round(n * 9 * k);
            g.fill(Math.round(lcx - gs / 2f), Math.round(lcy - gs / 2f), Math.round(lcx + gs / 2f), Math.round(lcy + gs / 2f), a(0x8B5CF6, (0.025f + 0.035f * pulse) / n * alpha));
        }
        if (logo == null) logo = loadLogo();
        if (logo != null) quad(g, logo.getId(), cx - logoS / 2f, ly, logoS, logoS, alpha);

        // anillos orbitales (puntos con cola luminosa), uno en cada sentido
        for (int ring = 0; ring < 2; ring++) {
            final int dots = ring == 0 ? 56 : 40;
            final float r = logoS * (ring == 0 ? 0.88f : 1.08f);
            final float head = t * (ring == 0 ? 1.5f : -1.1f);
            for (int i = 0; i < dots; i++) {
                float ang = (float) (i * 6.2831853 / dots);
                float d = ((head - ang) % 6.2831853f + 6.2831853f) % 6.2831853f; // distancia detras de la cabeza
                float glow = (float) Math.exp(-d * 1.6f);
                float x = lcx + (float) Math.cos(ang) * r * 1.15f, y = lcy + (float) Math.sin(ang) * r * 0.62f; // elipse: se ve inclinado
                px(g, x, y, Math.max(1f, k * (1f + glow * 1.6f)), ring == 0 ? 0x22D3EE : 0xA78BFA, (0.18f + 0.82f * glow) * alpha);
            }
        }

        float y = ly + logoS + 14 * k;
        y += text(g, "A T E N A S", 22 * k, 0xE9E3FF, cx, y, true, alpha) + 0;
        y += text(g, "M O D   \u00b7   1.20.1", 7.5f * k, 0x22D3EE, cx, y, true, alpha) + 8 * k;

        // barra segmentada futurista
        final int bw = Math.round(260 * k), bh = Math.max(5, Math.round(7 * k));
        final int bx = cx - bw / 2, by = Math.round(h / 2f + 52 * k);
        final int seg = Math.max(3, Math.round(5 * k)), gap = Math.max(1, Math.round(1.5f * k));
        final int n = Math.max(10, bw / (seg + gap));
        final float prog = Mth.clamp(shown, 0f, 1f);
        final int filled = Math.round(n * prog);
        g.fill(bx - 3, by - 3, bx + bw + 3, by + bh + 3, a(0x0B0E1C, 0.85f * alpha));
        bracket(g, bx - 4, by - 4, 8, 1, 1, 0x22D3EE, alpha);
        bracket(g, bx + bw + 3, by - 4, 8, -1, 1, 0x22D3EE, alpha);
        bracket(g, bx - 4, by + bh + 3, 8, 1, -1, 0x8B5CF6, alpha);
        bracket(g, bx + bw + 3, by + bh + 3, 8, -1, -1, 0x8B5CF6, alpha);
        final float scan = (t * n * 0.9f) % (n + 8);
        for (int i = 0; i < n; i++) {
            int sxp = bx + Math.round(i * (bw / (float) n));
            int wseg = Math.max(2, Math.round(bw / (float) n) - gap);
            if (i < filled) {
                float pp = i / (float) n;
                int c = pp < 0.5f ? mix(0x22D3EE, 0x8B5CF6, pp / 0.5f) : mix(0x8B5CF6, 0xEC4899, (pp - 0.5f) / 0.5f);
                float sh = Math.max(0f, 1f - Math.abs(i - scan) / 3f); // brillo que recorre lo cargado
                g.fill(sxp, by, sxp + wseg, by + bh, a(mix(c, 0xFFFFFF, sh * 0.7f), alpha));
                g.fill(sxp, by + bh, sxp + wseg, by + bh + Math.max(1, Math.round(2 * k)), a(c, 0.18f * alpha)); // reflejo
            } else {
                g.fill(sxp, by, sxp + wseg, by + bh, a(0x1B1A33, alpha));
            }
        }
        if (filled < n) { // segmento actual parpadeando
            int sxp = bx + Math.round(filled * (bw / (float) n));
            g.fill(sxp, by, sxp + Math.max(2, Math.round(bw / (float) n) - gap), by + bh, a(0xFFFFFF, (0.35f + 0.5f * pulse) * alpha));
        }
        // resplandor bajo lo cargado
        g.fillGradient(bx, by - Math.round(6 * k), bx + Math.round(bw * prog), by, a(0x8B5CF6, 0f), a(0x8B5CF6, 0.16f * alpha));

        // textos de estado
        String[] tips = Lang.es() ? TIPS_ES : TIPS_EN;
        String status = doneAt < 0 ? tips[(int) (t / 2.2f) % tips.length] : Lang.t("Abriendo el men\u00fa", "Opening the menu");
        int dots = 1 + (int) (t * 2.5f) % 3;
        text(g, status + "...".substring(0, dots), 7.5f * k, 0x9B97B8, bx + Math.round(46 * k), by + bh + 8 * k, false, alpha);
        text(g, Math.round(prog * 100f) + "%", 12 * k, 0xF1EEFC, bx + bw - Math.round(14 * k), by + bh + 5 * k, true, alpha);
        text(g, "ATENAS  \u00b7  " + Lang.t("iniciando", "booting"), 6 * k, 0x6D6A8F, cx, h - m - 8 * k, false, alpha);
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
