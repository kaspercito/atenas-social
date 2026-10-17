package com.atenas.social;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Amigos + chat de Atenas. Izquierda: lista con caras y solicitudes. Derecha: conversación. */
public class SocialScreen extends Screen {
    private static final ResourceLocation LOGO = new ResourceLocation("atenassocial", "textures/gui/logo.png");
    private static final int BG = 0xF00A0818, PANEL = 0xFF14122A, PANEL2 = 0xFF1E1B38, LINE = 0xFF2B2848, ACCENT = 0xFF8B5CF6;
    private static final int TEXT = 0xFFF1EEFC, MUTED = 0xFF9B97B8, FAINT = 0xFF6E6A8F, GREEN = 0xFF34D399, RED = 0xFFFB7185, AMBER = 0xFFFBBF24, CYAN = 0xFF22D3EE, OFFLINE = 0xFF5A5680;
    private static final int LW = 150, HEAD = 28, FRIEND_H = 28, REQ_H = 26, SEC_H = 16;
    private static final SimpleDateFormat HOUR = new SimpleDateFormat("HH:mm", Locale.ROOT);

    private static final class Row {
        int type; // 0 título, 1 solicitud recibida, 2 amigo, 3 solicitud enviada
        String text = "", key = "", sub = "";
        int color = TEXT, unread;
        boolean online, preview;
    }

    private static final class Hit {
        final int x, y, w, h;
        final Runnable run;
        Hit(int x, int y, int w, int h, Runnable run) { this.x = x; this.y = y; this.w = w; this.h = h; this.run = run; }
        boolean has(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private EditBox addBox, chatBox;
    private Ui.Btn addBtn, sendBtn, removeBtn;
    private int px, py, pw, ph, scroll, scrollChat;
    private String selected = "";
    private final List<SocialClient.Msg> msgs = new ArrayList<>();
    private long lastId;
    private boolean loadingChat, confirmRemove;
    private String flash = "";
    private int flashTicks, tickCount;

    public SocialScreen(Screen parent) {
        super(Component.literal("Amigos"));
        this.parent = parent;
    }

    // ------------------------------------------------------------ medidas (init y render usan las MISMAS)
    private int bodyTop() { return py + HEAD + 6; }
    private int bodyBottom() { return py + ph - 8; }
    private int listX() { return px + 8; }
    private int listTop() { return bodyTop() + 24; }
    private int cx() { return px + 8 + LW + 8; }
    private int cw() { return pw - LW - 24; }
    private int inputY() { return bodyBottom() - 24; }
    private int inputX() { return cx() + 6; }
    private int inputW() { return cw() - 12 - 52; }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 440);
        ph = Math.min(height - 16, 270);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        String keepAdd = addBox == null ? "" : addBox.getValue();
        String keepChat = chatBox == null ? "" : chatBox.getValue();

        int ax = listX() + 20, aw = LW - 20 - 28;
        addBox = new EditBox(font, ax + 5, bodyTop() + 5, aw - 10, 10, Component.literal("Nick"));
        addBox.setBordered(false);
        addBox.setHint(Component.literal("Agregar amigo…").withStyle(s -> s.withColor(0x3A3658)));
        addBox.setMaxLength(16);
        addBox.setValue(keepAdd);
        addRenderableWidget(addBox);
        addBtn = addRenderableWidget(new Ui.Btn(listX() + LW - 26, bodyTop(), 26, 18, Component.literal("+"), true, b -> addFriend()));

        chatBox = new EditBox(font, inputX() + 5, inputY() + 5, inputW() - 10, 10, Component.literal("Mensaje"));
        chatBox.setBordered(false);
        chatBox.setHint(Component.literal("Escribe un mensaje…").withStyle(s -> s.withColor(0x3A3658)));
        chatBox.setMaxLength(300);
        chatBox.setValue(keepChat);
        addRenderableWidget(chatBox);
        sendBtn = addRenderableWidget(new Ui.Btn(cx() + cw() - 6 - 46, inputY(), 46, 18, Component.literal("Enviar"), true, b -> sendMessage()));
        removeBtn = addRenderableWidget(new Ui.Btn(cx() + cw() - 56, bodyTop() + 9, 48, 14, Component.literal("Quitar"), false, b -> removeSelected()));
        chatBox.visible = false;
        sendBtn.visible = false;
        removeBtn.visible = false;
        SocialClient.sync(Minecraft.getInstance().player != null, "");
        if (!selected.isEmpty()) openChat(selected, true);
    }

    @Override
    public void removed() { SocialClient.viewingKey = ""; }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    private void say(String s) { flash = s; flashTicks = 120; }

    // ------------------------------------------------------------ acciones
    private void addFriend() {
        String n = addBox.getValue().trim();
        if (n.isEmpty()) return;
        if (!SocialClient.validName(n)) { say("Nick no válido (3-16 letras, números o _)"); return; }
        addBox.setValue("");
        SocialClient.add(n, (v, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null) say(err);
            else say(v.startsWith("accepted") ? "Ahora eres amigo de " + v.substring(v.indexOf('|') + 1) : "Solicitud enviada a " + v.substring(v.indexOf('|') + 1));
        }));
    }

    private void act(String what, String key) {
        SocialClient.Callback<String> cb = (v, err) -> Minecraft.getInstance().execute(() -> { if (err != null) say(err); });
        if (what.equals("accept")) SocialClient.accept(key, cb); else SocialClient.remove(key, cb);
    }

    private void removeSelected() {
        if (selected.isEmpty()) return;
        if (!confirmRemove) { confirmRemove = true; say("Pulsa «Quitar» otra vez para confirmar (borra el chat)"); return; }
        act("remove", selected);
        closeChat();
    }

    private void closeChat() {
        selected = ""; msgs.clear(); lastId = 0; confirmRemove = false; scrollChat = 0;
        SocialClient.viewingKey = "";
        if (chatBox != null) chatBox.visible = false;
        if (sendBtn != null) sendBtn.visible = false;
        if (removeBtn != null) removeBtn.visible = false;
    }

    private void openChat(String key, boolean force) {
        if (!force && key.equals(selected)) return;
        selected = key; msgs.clear(); lastId = 0; confirmRemove = false; scrollChat = 0;
        SocialClient.viewingKey = key;
        chatBox.visible = true; sendBtn.visible = true; removeBtn.visible = true;
        setFocused(chatBox);
        loadMessages(0);
    }

    private void loadMessages(long after) {
        if (loadingChat || selected.isEmpty()) return;
        loadingChat = true;
        final String k = selected;
        SocialClient.history(k, after, (list, err) -> Minecraft.getInstance().execute(() -> {
            loadingChat = false;
            if (!k.equals(selected)) return;
            if (err != null) { say(err); return; }
            boolean added = false;
            for (SocialClient.Msg m : list) {
                boolean dup = false;
                for (SocialClient.Msg x : msgs) if (x.id == m.id && m.id != 0) { dup = true; break; }
                if (!dup) { msgs.add(m); added = true; }
                if (m.id > lastId) lastId = m.id;
            }
            msgs.removeIf(m -> m.pending);
            if (added) { msgs.sort((a, b) -> Long.compare(a.id, b.id)); scrollChat = 0; }
        }));
    }

    private void sendMessage() {
        String text = chatBox.getValue().trim();
        if (text.isEmpty() || selected.isEmpty()) return;
        chatBox.setValue("");
        final String k = selected;
        SocialClient.send(k, text, (m, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null) { say(err); return; }
            if (k.equals(selected) && m != null) {
                boolean dup = false;
                for (SocialClient.Msg x : msgs) if (x.id == m.id) { dup = true; break; }
                if (!dup) msgs.add(m);
                if (m.id > lastId) lastId = m.id;
                msgs.sort((a, b) -> Long.compare(a.id, b.id));
                scrollChat = 0;
            }
        }));
    }

    // ------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        tickCount++;
        if (flashTicks > 0) flashTicks--;
        if (tickCount % 50 == 0) loadMessages(lastId);
        if (tickCount % 200 == 0) SocialClient.sync(Minecraft.getInstance().player != null, "");
        if (confirmRemove && tickCount % 80 == 0) confirmRemove = false;
    }

    // ------------------------------------------------------------ lista
    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        SocialClient.Snapshot s = SocialClient.snapshot();
        if (s == null) return out;
        if (!s.incoming.isEmpty()) {
            out.add(title("SOLICITUDES  " + s.incoming.size()));
            for (SocialClient.Req r : s.incoming) { Row w = new Row(); w.type = 1; w.text = r.name; w.key = r.key; out.add(w); }
        }
        List<SocialClient.Friend> pl = new ArrayList<>(), on = new ArrayList<>(), off = new ArrayList<>();
        for (SocialClient.Friend f : s.friends) (f.state.equals("playing") ? pl : f.online() ? on : off).add(f);
        group(out, "JUGANDO AHORA", pl); group(out, "EN LÍNEA", on); group(out, "DESCONECTADOS", off);
        if (!s.outgoing.isEmpty()) {
            out.add(title("ENVIADAS  " + s.outgoing.size()));
            for (SocialClient.Req r : s.outgoing) { Row w = new Row(); w.type = 3; w.text = r.name; w.key = r.key; out.add(w); }
        }
        return out;
    }

    private static Row title(String t) { Row r = new Row(); r.type = 0; r.text = t; return r; }

    private static void group(List<Row> out, String name, List<SocialClient.Friend> list) {
        if (list.isEmpty()) return;
        out.add(title(name + "  " + list.size()));
        for (SocialClient.Friend f : list) {
            Row w = new Row(); w.type = 2; w.text = f.name; w.key = f.key; w.unread = f.unread; w.online = f.online();
            w.color = f.state.equals("playing") ? GREEN : f.online() ? CYAN : OFFLINE;
            w.preview = f.unread > 0 && !f.previewBody.isEmpty();
            w.sub = w.preview ? f.previewBody : stateText(f);
            out.add(w);
        }
    }

    private static String stateText(SocialClient.Friend f) {
        return f.state.equals("playing") ? (f.server.isEmpty() ? "Jugando" : "Jugando en " + f.server) : f.online() ? "En el launcher" : "Desconectado";
    }

    private static int rowH(Row r) { return r.type == 0 ? SEC_H : r.type == 2 ? FRIEND_H : REQ_H; }

    // ------------------------------------------------------------ input
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < cx() - 4) { scroll = Math.max(0, scroll - (int) Math.signum(delta) * 18); return true; }
        scrollChat = Math.max(0, scrollChat + (int) Math.signum(delta) * 20);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.has(mx, my)) { h.run.run(); return true; }
            }
            // clic en cualquier parte de la caja de texto = enfocarla
            if (mx >= listX() + 20 && mx < listX() + LW - 28 && my >= bodyTop() && my < bodyTop() + 18) { setFocused(addBox); return true; }
            if (chatBox.visible && mx >= inputX() && mx < inputX() + inputW() && my >= inputY() && my < inputY() + 18) { setFocused(chatBox); return true; }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            if (chatBox.isFocused()) { sendMessage(); return true; }
            if (addBox.isFocused()) { addFriend(); return true; }
        }
        if (key == GLFW.GLFW_KEY_TAB && (mods & GLFW.GLFW_MOD_SHIFT) != 0) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------ piezas de dibujo
    private void avatar(GuiGraphics g, String name, int x, int y, int s, int ring, boolean dim, boolean dot) {
        g.fill(x - 1, y - 1, x + s + 1, y + s + 1, ring);
        Faces.Face face = Faces.get(name);
        if (face != null) g.blit(face.tex, x, y, s, s, 0f, 0f, face.w, face.h, face.w, face.h);
        else {
            g.fill(x, y, x + s, y + s, 0xFF2A2550);
            String ini = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            g.drawString(font, ini, x + (s - font.width(ini)) / 2, y + (s - 8) / 2, Ui.ACCENT_LIGHT, false);
        }
        if (dim) g.fill(x, y, x + s, y + s, 0x88090812);
        if (dot) {
            g.fill(x + s - 5, y + s - 5, x + s + 2, y + s + 2, PANEL);
            g.fill(x + s - 4, y + s - 4, x + s + 1, y + s + 1, ring);
        }
    }

    private void smallBtn(GuiGraphics g, String label, int x, int y, int w, int h, int mx, int my, int color, Runnable r) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        Ui.chamfer(g, x, y, w, h, 2, hov ? PANEL2 : PANEL);
        Ui.chamferBorder(g, x, y, w, h, 2, hov ? color : LINE);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, hov ? color : TEXT, false);
        hits.add(new Hit(x, y, w, h, r));
    }

    private void inputBox(GuiGraphics g, int x, int y, int w, int h, boolean focus) {
        Ui.chamfer(g, x, y, w, h, 2, 0xFF0B0A1C);
        Ui.chamferBorder(g, x, y, w, h, 2, focus ? Ui.ACCENT_LIGHT : LINE);
        if (focus) g.fillGradient(x + 3, y + h - 1, x + w - 3, y + h, ACCENT, Ui.PINK);
    }

    // ------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hits.clear();
        renderBackground(g);
        // resplandor + panel
        g.fill(px - 3, py - 3, px + pw + 3, py + ph + 3, 0x30A78BFA);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0x55A78BFA);
        g.fill(px, py, px + pw, py + ph, BG);
        Ui.border(g, px, py, pw, ph, LINE);

        // cabecera
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + HEAD, 0xFF1B1740, 0xFF0C0A1A);
        g.fill(px, py + HEAD, px + pw, py + HEAD + 1, LINE);
        g.fill(px, py, px + pw / 2, py + 2, ACCENT);
        g.fill(px + pw / 2, py, px + pw, py + 2, Ui.PINK);
        g.fillGradient(px + pw / 2 - 40, py, px + pw / 2 + 40, py + 2, ACCENT, Ui.PINK);
        g.blit(LOGO, px + 9, py + 6, 18, 18, 0f, 0f, 256, 256, 256, 256);
        Ui.heading(g, font, "Amigos", px + 33, py + 5, TEXT, 1f, false);
        String st = SocialClient.status();
        SocialClient.Snapshot snap = SocialClient.snapshot();
        if (snap == null || st.equals("noaccount") || st.equals("error")) {
            String sub = st.equals("noaccount") ? "Inicia sesión" : st.equals("error") ? "Sin conexión" : "Conectando…";
            g.drawString(font, sub, px + 34, py + 17, st.equals("error") ? RED : MUTED, false);
        } else {
            int on = 0;
            for (SocialClient.Friend f : snap.friends) if (f.online()) on++;
            g.fill(px + 34, py + 18, px + 39, py + 23, on > 0 ? GREEN : OFFLINE);
            g.drawString(font, snap.friends.isEmpty() ? "Sin amigos todavía" : on + " de " + snap.friends.size() + " conectados", px + 43, py + 17, MUTED, false);
        }
        smallBtn(g, "x", px + pw - 22, py + 7, 14, 14, mx, my, RED, this::onClose);
        if (flashTicks > 0 && !flash.isEmpty()) { // aviso dentro de la cabecera (hasta 2 líneas)
            int fw = pw - 210;
            List<FormattedCharSequence> ls = font.split(Component.literal(flash), fw);
            int n = Math.min(2, ls.size());
            int fx = px + 190, fy = py + (HEAD - n * 10) / 2 + 1;
            g.fill(fx - 6, py + 4, fx + fw + 4, py + HEAD - 3, 0xAA2A1A5E);
            g.fill(fx - 6, py + 4, fx - 4, py + HEAD - 3, AMBER);
            for (int i = 0; i < n; i++) g.drawString(font, ls.get(i), fx, fy + i * 10, AMBER, false);
        }

        renderList(g, mx, my);
        renderChat(g, mx, my);

        // fondos de las cajas de texto (los widgets se pintan encima)
        inputBox(g, listX() + 20, bodyTop(), LW - 20 - 28, 18, addBox.isFocused());
        if (chatBox.visible) inputBox(g, inputX(), inputY(), inputW(), 18, chatBox.isFocused());
        super.render(g, mx, my, pt);
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        int lx = listX(), top = bodyTop();
        g.fill(lx, top, lx + 18, top + 18, PANEL2);
        Ui.border(g, lx, top, 18, 18, LINE);
        g.drawString(font, "+", lx + 9 - font.width("+") / 2, top + 5, Ui.ACCENT_LIGHT, false);

        int ly = listTop(), lb = bodyBottom(), lh = lb - ly;
        g.fill(lx, ly, lx + LW, lb, PANEL);
        Ui.border(g, lx, ly, LW, lh, LINE);
        List<Row> rows = rows();
        if (snapshotMissing()) {
            g.drawWordWrap(font, Component.literal(SocialClient.status().equals("error") ? "Sin conexión. " + SocialClient.error() : "Conectando…"), lx + 10, ly + 12, LW - 20, MUTED);
            return;
        }
        if (rows.isEmpty()) {
            g.blit(LOGO, lx + LW / 2 - 12, ly + lh / 2 - 44, 24, 24, 0f, 0f, 256, 256, 256, 256);
            g.drawCenteredString(font, "Aún no tienes amigos", lx + LW / 2, ly + lh / 2 - 12, TEXT);
            g.drawWordWrap(font, Component.literal("Escribe su nick arriba y envía una solicitud."), lx + 12, ly + lh / 2 + 2, LW - 24, MUTED);
            return;
        }
        int total = 0;
        for (Row r : rows) total += rowH(r);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - lh + 4)));
        g.enableScissor(lx + 1, ly + 1, lx + LW - 1, lb - 1);
        int y = ly + 2 - scroll;
        for (Row r : rows) {
            int rh = rowH(r);
            if (y + rh < ly || y > lb) { y += rh; continue; }
            int right = lx + LW - 8;
            if (r.type == 0) {
                g.drawString(font, r.text, lx + 9, y + 5, 0xFF6D4FC7, false);
                int tw = font.width(r.text);
                g.fill(lx + 9 + tw + 6, y + 9, lx + LW - 8, y + 10, LINE);
            } else if (r.type == 2) {
                final String key = r.key;
                boolean active = key.equals(selected);
                boolean hover = mx >= lx && mx < lx + LW && my >= y && my < y + rh && my >= ly && my < lb;
                if (active) {
                    g.fill(lx + 1, y, lx + LW - 1, y + rh, 0xFF2A2450);
                    g.fillGradient(lx + 1, y, lx + 3, y + rh, ACCENT, Ui.PINK);
                } else if (hover) g.fill(lx + 1, y, lx + LW - 1, y + rh, 0xFF191631);
                avatar(g, r.text, lx + 9, y + 4, 20, r.color, !r.online, true);
                int badge = r.unread > 0 ? 20 : 0;
                g.drawString(font, font.plainSubstrByWidth(r.text, LW - 46 - badge), lx + 36, y + 5, r.online ? TEXT : MUTED, false);
                g.drawString(font, font.plainSubstrByWidth(r.sub, LW - 46 - badge), lx + 36, y + 16, r.preview ? AMBER : r.color, false);
                if (r.unread > 0) {
                    String n = r.unread > 9 ? "9+" : String.valueOf(r.unread);
                    g.fillGradient(right - 14, y + 8, right + 1, y + 20, ACCENT, Ui.PINK);
                    g.drawString(font, n, right - 6 - font.width(n) / 2, y + 10, 0xFFFFFFFF, false);
                }
                addClipped(lx, y, LW, rh, ly + 1, lb - 1, () -> openChat(key, false));
            } else if (r.type == 1) {
                final String key = r.key;
                avatar(g, r.text, lx + 9, y + 3, 20, AMBER, false, false);
                g.drawString(font, font.plainSubstrByWidth(r.text, LW - 100), lx + 36, y + 3, TEXT, false);
                g.drawString(font, "quiere ser tu amigo", lx + 36, y + 14, MUTED, false);
                smallBtn(g, "+", right - 34, y + 6, 16, 14, mx, my, GREEN, () -> act("accept", key));
                smallBtn(g, "x", right - 16, y + 6, 16, 14, mx, my, RED, () -> act("remove", key));
            } else {
                final String key = r.key;
                avatar(g, r.text, lx + 9, y + 3, 20, OFFLINE, true, false);
                g.drawString(font, font.plainSubstrByWidth(r.text, LW - 80), lx + 36, y + 3, MUTED, false);
                g.drawString(font, "esperando respuesta…", lx + 36, y + 14, 0xFF3A3658, false);
                smallBtn(g, "x", right - 16, y + 6, 16, 14, mx, my, RED, () -> act("remove", key));
            }
            y += rh;
        }
        g.disableScissor();
        if (total > lh) { // barra de scroll fina
            int sbH = Math.max(14, lh * lh / total);
            int sbY = ly + 2 + (int) ((long) (lh - 4 - sbH) * scroll / Math.max(1, total - lh + 4));
            g.fill(lx + LW - 3, sbY, lx + LW - 1, sbY + sbH, 0xFF6D4FC7);
        }
    }

    private boolean snapshotMissing() { return SocialClient.snapshot() == null; }

    private void addClipped(int x, int y, int w, int h, int top, int bottom, Runnable r) {
        int hy = Math.max(y, top), hh = Math.min(y + h, bottom) - hy;
        if (hh > 0) hits.add(new Hit(x, hy, w, hh, r));
    }

    private void renderChat(GuiGraphics g, int mx, int my) {
        int cx = cx(), top = bodyTop(), cw = cw(), bottom = bodyBottom(), ch = bottom - top;
        g.fill(cx, top, cx + cw, bottom, PANEL);
        Ui.border(g, cx, top, cw, ch, LINE);
        SocialClient.Friend f = SocialClient.find(selected);
        if (selected.isEmpty() || f == null) {
            if (f == null && !selected.isEmpty()) closeChat();
            g.blit(LOGO, cx + cw / 2 - 20, top + ch / 2 - 52, 40, 40, 0f, 0f, 256, 256, 256, 256);
            g.drawCenteredString(font, "Tus conversaciones", cx + cw / 2, top + ch / 2 - 2, TEXT);
            g.drawCenteredString(font, "Elige a un amigo de la lista para chatear", cx + cw / 2, top + ch / 2 + 11, MUTED);
            return;
        }
        // cabecera del chat
        g.fillGradient(cx + 1, top + 1, cx + cw - 1, top + 32, 0xFF1E1B38, 0xFF171431);
        g.fill(cx + 1, top + 32, cx + cw - 1, top + 33, LINE);
        int ring = f.state.equals("playing") ? GREEN : f.online() ? CYAN : OFFLINE;
        avatar(g, f.name, cx + 8, top + 5, 22, ring, !f.online(), true);
        g.drawString(font, f.name, cx + 38, top + 6, TEXT, false);
        int tx = cx + 38;
        if (f.state.equals("playing")) { PackInfo.drawIcon(g, tx, top + 18, PackInfo.kindOfName(f.pack)); tx += 14; }
        g.drawString(font, font.plainSubstrByWidth(stateText(f), cw - (tx - cx) - 70), tx, top + 18, ring, false);
        removeBtn.setMessage(Component.literal(confirmRemove ? "¿Seguro?" : "Quitar"));

        // mensajes: se arman de abajo hacia arriba; la rueda los desplaza
        int logTop = top + 36, logBottom = inputY() - 6, logH = logBottom - logTop;
        int maxW = Math.max(60, (int) ((cw - 24) * 0.78f));
        int n = msgs.size();
        List<List<FormattedCharSequence>> split = new ArrayList<>(n);
        int[] bw = new int[n], bh = new int[n], gap = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            SocialClient.Msg m = msgs.get(i);
            List<FormattedCharSequence> lines = font.split(Component.literal(m.body), maxW);
            split.add(lines);
            int w = 0;
            for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
            bw[i] = w + 12;
            bh[i] = lines.size() * 10 + 6;
            SocialClient.Msg nx = i + 1 < n ? msgs.get(i + 1) : null;
            gap[i] = (nx == null || nx.mine != m.mine || nx.ts - m.ts > 120000L) ? 12 : 3;
            total += bh[i] + gap[i];
        }
        scrollChat = Math.max(0, Math.min(scrollChat, Math.max(0, total - logH + 4)));
        g.enableScissor(cx + 1, logTop, cx + cw - 1, logBottom);
        int y = logBottom - 2 + scrollChat - total;
        for (int i = 0; i < n; i++) {
            SocialClient.Msg m = msgs.get(i);
            if (y + bh[i] + gap[i] > logTop - 2 && y < logBottom) {
                int bx = m.mine ? cx + cw - 10 - bw[i] : cx + 10;
                if (m.mine) {
                    Ui.chamfer(g, bx, y, bw[i], bh[i], 2, 0xFF3B2A86);
                    g.fillGradient(bx + 2, y + 1, bx + bw[i] - 2, y + bh[i] - 1, 0xFF6D4FC7, 0xFF9D3FD0);
                } else {
                    Ui.chamfer(g, bx, y, bw[i], bh[i], 2, PANEL2);
                    Ui.chamferBorder(g, bx, y, bw[i], bh[i], 2, 0xFF2F2B52);
                }
                List<FormattedCharSequence> lines = split.get(i);
                for (int j = 0; j < lines.size(); j++) g.drawString(font, lines.get(j), bx + 6, y + 3 + j * 10, TEXT, false);
                if (gap[i] > 3) {
                    String t = HOUR.format(new Date(m.ts));
                    g.drawString(font, t, m.mine ? bx + bw[i] - font.width(t) : bx, y + bh[i] + 2, FAINT, false);
                }
            }
            y += bh[i] + gap[i];
        }
        g.disableScissor();
        if (n == 0) g.drawCenteredString(font, loadingChat ? "Cargando…" : "Aún no hay mensajes. ¡Saluda!", cx + cw / 2, logTop + logH / 2 - 4, MUTED);
        else if (scrollChat > 0) g.drawCenteredString(font, "Desplazado: baja para ver lo más nuevo", cx + cw / 2, logBottom - 10, 0xFF6D4FC7);
    }
}
