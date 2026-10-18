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
    private static final int PANEL = 0xFF0F0D22, PANEL2 = 0xFF1E1B38, LINE = 0xFF2B2848, ACCENT = 0xFF8B5CF6;
    private static final int TEXT = 0xFFF1EEFC, MUTED = 0xFF9B97B8, FAINT = 0xFF6E6A8F, GREEN = 0xFF34D399, RED = 0xFFFB7185, AMBER = 0xFFFBBF24, CYAN = 0xFF22D3EE, OFFLINE = 0xFF5A5680;
    private static final int HEAD = 38, FOOT = 18, PAD = 10, CUT = 6, FIELD_H = 20, FRIEND_H = 32, REQ_H = 30, SEC_H = 20;
    private static final SimpleDateFormat HOUR = new SimpleDateFormat("HH:mm", Locale.ROOT);

    private static final class Row {
        int type; // 0 título, 1 solicitud recibida, 2 amigo, 3 solicitud enviada
        String text = "", key = "", sub = "";
        int color = TEXT, unread, count;
        boolean online, preview;
    }

    private static final class Hit {
        final int x, y, w, h;
        final Runnable run;
        Hit(int x, int y, int w, int h, Runnable run) { this.x = x; this.y = y; this.w = w; this.h = h; this.run = run; }
        boolean has(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final Screen parent;
    private final Ui.Backdrop backdrop = new Ui.Backdrop();
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
        super(Component.literal(Lang.t("Amigos", "Friends")));
        this.parent = parent;
    }

    // ------------------------------------------------------------ medidas (init y render usan las MISMAS)
    private int lw() { return pw < 440 ? 150 : 178; }
    private int bodyTop() { return py + HEAD + 8; }
    private int bodyBottom() { return py + ph - FOOT - 6; }
    private int listX() { return px + PAD; }
    private int listTop() { return bodyTop() + FIELD_H + 6; }
    private int cx() { return px + PAD + lw() + 8; }
    private int cw() { return pw - lw() - PAD * 2 - 8; }
    private int inputY() { return bodyBottom() - FIELD_H - 6; }
    private int inputX() { return cx() + 6; }
    private int inputW() { return cw() - 12 - 56 - 4; }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 520);
        ph = Math.min(height - 16, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        String keepAdd = addBox == null ? "" : addBox.getValue();
        String keepChat = chatBox == null ? "" : chatBox.getValue();

        int bw = lw() - 26;
        addBox = new EditBox(font, listX() + 22, bodyTop() + (FIELD_H - 8) / 2, bw - 22 - 6, 10, Component.literal("Nick"));
        addBox.setBordered(false);
        addBox.setHint(Component.literal(Lang.t("Agregar amigo…", "Add friend…")).withStyle(s -> s.withColor(0x6E6A8F)));
        addBox.setMaxLength(16);
        addBox.setValue(keepAdd);
        addRenderableWidget(addBox);
        addBtn = addRenderableWidget(new Ui.Btn(listX() + lw() - 22, bodyTop(), 22, FIELD_H, Component.literal("+"), true, b -> addFriend()));

        chatBox = new EditBox(font, inputX() + 8, inputY() + (FIELD_H - 8) / 2, inputW() - 16, 10, Component.literal("Mensaje"));
        chatBox.setBordered(false);
        chatBox.setHint(Component.literal(Lang.t("Escribe un mensaje…", "Type a message…")).withStyle(s -> s.withColor(0x6E6A8F)));
        chatBox.setMaxLength(300);
        chatBox.setValue(keepChat);
        addRenderableWidget(chatBox);
        sendBtn = addRenderableWidget(new Ui.Btn(cx() + cw() - 6 - 56, inputY(), 56, FIELD_H, Component.literal(Lang.t("Enviar", "Send")), true, b -> sendMessage()));
        removeBtn = addRenderableWidget(new Ui.Btn(cx() + cw() - 6 - 58, bodyTop() + 11, 58, 18, Component.literal(Lang.t("Quitar", "Remove")), false, b -> removeSelected()));
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
        if (!SocialClient.validName(n)) { say(Lang.t("Nick no válido (3-16 letras, números o _)", "Invalid name (3-16 letters, numbers or _)")); return; }
        addBox.setValue("");
        SocialClient.add(n, (v, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null) say(err);
            else say(v.startsWith("accepted") ? Lang.t("Ahora eres amigo de ", "You are now friends with ") + v.substring(v.indexOf('|') + 1) : Lang.t("Solicitud enviada a ", "Request sent to ") + v.substring(v.indexOf('|') + 1));
        }));
    }

    private void act(String what, String key) {
        SocialClient.Callback<String> cb = (v, err) -> Minecraft.getInstance().execute(() -> { if (err != null) say(err); });
        if (what.equals("accept")) SocialClient.accept(key, cb); else SocialClient.remove(key, cb);
    }

    private void removeSelected() {
        if (selected.isEmpty()) return;
        if (!confirmRemove) { confirmRemove = true; say(Lang.t("Pulsa «Quitar» otra vez para confirmar (borra el chat)", "Press «Remove» again to confirm (deletes the chat)")); return; }
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
            out.add(title(Lang.t("Solicitudes", "Requests"), s.incoming.size(), AMBER));
            for (SocialClient.Req r : s.incoming) { Row w = new Row(); w.type = 1; w.text = r.name; w.key = r.key; out.add(w); }
        }
        List<SocialClient.Friend> pl = new ArrayList<>(), on = new ArrayList<>(), off = new ArrayList<>();
        for (SocialClient.Friend f : s.friends) (f.state.equals("playing") ? pl : f.online() ? on : off).add(f);
        group(out, Lang.t("Jugando ahora", "Playing now"), GREEN, pl);
        group(out, Lang.t("En línea", "Online"), CYAN, on);
        group(out, Lang.t("Desconectados", "Offline"), OFFLINE, off);
        if (!s.outgoing.isEmpty()) {
            out.add(title(Lang.t("Enviadas", "Sent"), s.outgoing.size(), FAINT));
            for (SocialClient.Req r : s.outgoing) { Row w = new Row(); w.type = 3; w.text = r.name; w.key = r.key; out.add(w); }
        }
        return out;
    }

    private static Row title(String t, int count, int color) { Row r = new Row(); r.type = 0; r.text = t; r.count = count; r.color = color; return r; }

    private static void group(List<Row> out, String name, int color, List<SocialClient.Friend> list) {
        if (list.isEmpty()) return;
        out.add(title(name, list.size(), color));
        for (SocialClient.Friend f : list) {
            Row w = new Row(); w.type = 2; w.text = f.name; w.key = f.key; w.unread = f.unread; w.online = f.online();
            w.color = f.state.equals("playing") ? GREEN : f.online() ? CYAN : OFFLINE;
            w.preview = f.unread > 0 && !f.previewBody.isEmpty();
            w.sub = w.preview ? f.previewBody : stateText(f);
            out.add(w);
        }
    }

    private static String stateText(SocialClient.Friend f) {
        if (f.state.equals("playing")) return f.server.isEmpty() ? Lang.t("Jugando", "Playing") : Lang.t("Jugando en ", "Playing on ") + f.server;
        return f.online() ? Lang.t("En el launcher", "In the launcher") : Lang.t("Desconectado", "Offline");
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
            if (mx >= listX() && mx < listX() + lw() - 26 && my >= bodyTop() && my < bodyTop() + FIELD_H) { setFocused(addBox); return true; }
            if (chatBox.visible && mx >= inputX() && mx < inputX() + inputW() && my >= inputY() && my < inputY() + FIELD_H) { setFocused(chatBox); return true; }
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

    private void closeBtn(GuiGraphics g, int x, int y, int s, int mx, int my) {
        boolean hov = mx >= x && mx < x + s && my >= y && my < y + s;
        Ui.chamfer(g, x, y, s, s, 3, hov ? 0xFF3A1F3F : PANEL);
        Ui.chamferBorder(g, x, y, s, s, 3, hov ? RED : LINE);
        int c = hov ? RED : TEXT;
        for (int i = 0; i < 7; i++) {
            g.fill(x + 5 + i, y + 5 + i, x + 6 + i, y + 6 + i, c);
            g.fill(x + 11 - i, y + 5 + i, x + 12 - i, y + 6 + i, c);
        }
        hits.add(new Hit(x, y, s, s, this::onClose));
    }

    private void inputBox(GuiGraphics g, int x, int y, int w, int h, boolean focus) {
        Ui.chamfer(g, x, y, w, h, 3, 0xFF0A0918);
        Ui.chamferBorder(g, x, y, w, h, 3, focus ? Ui.ACCENT_LIGHT : LINE);
        if (focus) Ui.hbar(g, x + 4, y + h - 1, w - 8, 1, ACCENT, Ui.PINK);
    }

    private static void userIcon(GuiGraphics g, int x, int y, int c) {
        g.fill(x + 3, y, x + 7, y + 4, c);
        g.fill(x + 2, y + 5, x + 8, y + 6, c);
        g.fill(x + 1, y + 6, x + 9, y + 9, c);
    }

    // ------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        hits.clear();
        Minecraft mc = Minecraft.getInstance();
        long t = System.currentTimeMillis();
        if (mc.level != null) g.fillGradient(0, 0, width, height, 0xC0070914, 0xE00E0B22);
        else backdrop.render(g, width, height);

        // resplandor + panel de cristal con esquinas cortadas
        for (int k = 3; k >= 1; k--) Ui.chamfer(g, px - k * 2, py - k * 2, pw + k * 4, ph + k * 4, CUT + k * 2, (0x16 / k << 24) | 0x8B5CF6);
        Ui.chamfer(g, px, py, pw, ph, CUT, 0xF4090818);
        Ui.chamferBorder(g, px, py, pw, ph, CUT, 0xFF3A3466);

        // cabecera
        g.fillGradient(px + CUT, py + 1, px + pw - CUT, py + CUT, 0xFF1B1740, 0xFF181436);
        g.fillGradient(px + 1, py + CUT, px + pw - 1, py + HEAD, 0xFF181436, 0xFF0C0A1A);
        g.fill(px + 1, py + HEAD, px + pw - 1, py + HEAD + 1, LINE);
        Ui.hbar(g, px + CUT, py, pw - CUT * 2, 2, ACCENT, Ui.PINK);
        Ui.hbar(g, px + 14, py + HEAD, 70, 1, ACCENT, 0x008B5CF6);

        float pulse = (float) (0.5 + 0.5 * Math.sin(t * 0.003));
        g.fill(px + 10, py + 7, px + 36, py + 33, (((int) (12 + 16 * pulse)) << 24) | 0x8B5CF6);
        g.blit(LOGO, px + 12, py + 9, 22, 22, 0f, 0f, 256, 256, 256, 256);
        Ui.heading(g, font, Lang.t("Amigos", "Friends"), px + 42, py + 8, TEXT, 1.25f, false);

        String st = SocialClient.status();
        SocialClient.Snapshot snap = SocialClient.snapshot();
        int sx = px + 42, sy = py + 23;
        if (snap == null || st.equals("noaccount") || st.equals("error")) {
            boolean err = st.equals("error");
            String sub = st.equals("noaccount") ? Lang.t("Inicia sesión", "Sign in") : err ? Lang.t("Sin conexión", "Offline") : Lang.t("Conectando…", "Connecting…");
            g.fill(sx, sy + 2, sx + 5, sy + 7, err ? RED : AMBER);
            g.drawString(font, sub, sx + 9, sy, err ? RED : MUTED, false);
        } else {
            int on = 0;
            for (SocialClient.Friend f : snap.friends) if (f.online()) on++;
            g.fill(sx, sy + 2, sx + 5, sy + 7, on > 0 ? GREEN : OFFLINE);
            String line = snap.friends.isEmpty() ? Lang.t("Sin amigos todavía", "No friends yet") : Lang.t(on + " de " + snap.friends.size() + " conectados", on + " of " + snap.friends.size() + " online");
            g.drawString(font, line, sx + 9, sy, MUTED, false);
        }
        closeBtn(g, px + pw - 26, py + 11, 16, mx, my);
        if (flashTicks > 0 && !flash.isEmpty()) { // aviso dentro de la cabecera (hasta 2 líneas)
            int fx = px + 170, fw = pw - 170 - 40;
            List<FormattedCharSequence> ls = font.split(Component.literal(flash), fw - 12);
            int n = Math.min(2, ls.size());
            int fy = py + (HEAD - n * 10) / 2 + 1;
            Ui.chamfer(g, fx - 6, py + 6, fw, HEAD - 12, 2, 0xCC2A1A5E);
            g.fill(fx - 6, py + 8, fx - 4, py + HEAD - 8, AMBER);
            for (int i = 0; i < n; i++) g.drawString(font, ls.get(i), fx + 2, fy + i * 10, AMBER, false);
        }

        renderList(g, mx, my);
        renderChat(g, mx, my);
        renderFooter(g, mc);
        super.render(g, mx, my, pt);
    }

    private void renderFooter(GuiGraphics g, Minecraft mc) {
        int fy = py + ph - FOOT;
        g.fill(px + 1, fy, px + pw - 1, fy + 1, LINE);
        String hint = Lang.t("Esc cerrar  ·  Enter enviar  ·  Rueda para desplazar", "Esc close  ·  Enter send  ·  Scroll to move");
        if (pw >= 400) g.drawString(font, hint, px + PAD, fy + 6, FAINT, false);
        boolean ok = SocialClient.verified();
        String who = mc.getUser() == null ? "" : mc.getUser().getName();
        String tag = who + "  ·  " + (ok ? Lang.t("cuenta verificada", "verified account") : Lang.t("sin verificar", "not verified"));
        int tw = font.width(tag);
        int rx = px + pw - PAD - tw;
        g.fill(rx - 9, fy + 8, rx - 4, fy + 13, ok ? GREEN : AMBER);
        g.drawString(font, tag, rx, fy + 6, ok ? MUTED : AMBER, false);
    }

    private void renderList(GuiGraphics g, int mx, int my) {
        int lx = listX(), top = bodyTop(), w = lw();
        // campo para agregar
        inputBox(g, lx, top, w - 26, FIELD_H, addBox.isFocused());
        userIcon(g, lx + 7, top + 6, addBox.isFocused() ? Ui.ACCENT_LIGHT : FAINT);

        int ly = listTop(), lb = bodyBottom(), lh = lb - ly;
        Ui.chamfer(g, lx, ly, w, lh, 3, PANEL);
        Ui.chamferBorder(g, lx, ly, w, lh, 3, LINE);
        List<Row> rows = rows();
        if (snapshotMissing()) {
            boolean err = SocialClient.status().equals("error");
            g.drawWordWrap(font, Component.literal(err ? Lang.t("Sin conexión. ", "Offline. ") + SocialClient.error() : Lang.t("Conectando…", "Connecting…")), lx + 10, ly + 12, w - 20, err ? RED : MUTED);
            return;
        }
        if (rows.isEmpty()) {
            g.blit(LOGO, lx + w / 2 - 14, ly + lh / 2 - 48, 28, 28, 0f, 0f, 256, 256, 256, 256);
            g.drawCenteredString(font, Lang.t("Aún no tienes amigos", "No friends yet"), lx + w / 2, ly + lh / 2 - 12, TEXT);
            g.drawWordWrap(font, Component.literal(Lang.t("Escribe su nick arriba y envía una solicitud.", "Type their name above and send a request.")), lx + 12, ly + lh / 2 + 2, w - 24, MUTED);
            return;
        }
        int total = 0;
        for (Row r : rows) total += rowH(r);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - lh + 4)));
        g.enableScissor(lx + 1, ly + 1, lx + w - 1, lb - 1);
        int y = ly + 2 - scroll;
        for (Row r : rows) {
            int rh = rowH(r);
            if (y + rh < ly || y > lb) { y += rh; continue; }
            int right = lx + w - 8;
            if (r.type == 0) {
                String cnt = String.valueOf(r.count);
                g.fill(lx + 9, y + 8, lx + 13, y + 12, r.color);
                Ui.heading(g, font, r.text, lx + 18, y + 6, r.color, 1f, false);
                int hw = Ui.headingWidth(font, r.text);
                g.fill(lx + 18 + hw + 6, y + 10, right - font.width(cnt) - 6, y + 11, LINE);
                g.drawString(font, cnt, right - font.width(cnt), y + 6, MUTED, false);
            } else if (r.type == 2) {
                final String key = r.key;
                boolean active = key.equals(selected);
                boolean hover = mx >= lx && mx < lx + w && my >= y && my < y + rh && my >= ly && my < lb;
                if (active) {
                    g.fillGradient(lx + 1, y, lx + w - 1, y + rh, 0xFF2A2458, 0xFF1D1940);
                    g.fillGradient(lx + 1, y, lx + 3, y + rh, ACCENT, Ui.PINK);
                } else if (hover) g.fill(lx + 1, y, lx + w - 1, y + rh, 0xFF181530);
                avatar(g, r.text, lx + 9, y + 4, 24, r.color, !r.online, true);
                int badge = r.unread > 0 ? 24 : 0;
                int tw = w - 46 - badge;
                g.drawString(font, font.plainSubstrByWidth(r.text, tw), lx + 40, y + 6, r.online ? TEXT : MUTED, false);
                g.drawString(font, font.plainSubstrByWidth(r.sub, tw), lx + 40, y + 18, r.preview ? AMBER : r.color, false);
                if (r.unread > 0) {
                    String n = r.unread > 9 ? "9+" : String.valueOf(r.unread);
                    Ui.chamfer(g, right - 16, y + 9, 17, 13, 3, ACCENT);
                    g.fillGradient(right - 14, y + 10, right - 1, y + 21, Ui.ACCENT_LIGHT, Ui.PINK);
                    g.drawString(font, n, right - 7 - font.width(n) / 2, y + 12, 0xFFFFFFFF, false);
                }
                addClipped(lx, y, w, rh, ly + 1, lb - 1, () -> openChat(key, false));
            } else if (r.type == 1) {
                final String key = r.key;
                avatar(g, r.text, lx + 9, y + 3, 22, AMBER, false, false);
                g.drawString(font, font.plainSubstrByWidth(r.text, w - 98), lx + 38, y + 5, TEXT, false);
                g.drawString(font, font.plainSubstrByWidth(Lang.t("quiere ser tu amigo", "wants to be friends"), w - 98), lx + 38, y + 16, MUTED, false);
                smallBtn(g, "+", right - 38, y + 7, 17, 16, mx, my, GREEN, () -> act("accept", key));
                smallBtn(g, "x", right - 18, y + 7, 17, 16, mx, my, RED, () -> act("remove", key));
            } else {
                final String key = r.key;
                avatar(g, r.text, lx + 9, y + 3, 22, OFFLINE, true, false);
                g.drawString(font, font.plainSubstrByWidth(r.text, w - 70), lx + 38, y + 5, MUTED, false);
                g.drawString(font, font.plainSubstrByWidth(Lang.t("esperando respuesta…", "waiting for reply…"), w - 70), lx + 38, y + 16, FAINT, false);
                smallBtn(g, "x", right - 18, y + 7, 17, 16, mx, my, RED, () -> act("remove", key));
            }
            y += rh;
        }
        g.disableScissor();
        if (total > lh) { // barra de scroll fina
            int sbH = Math.max(14, lh * lh / total);
            int sbY = ly + 2 + (int) ((long) (lh - 4 - sbH) * scroll / Math.max(1, total - lh + 4));
            g.fill(lx + w - 3, sbY, lx + w - 1, sbY + sbH, 0xFF6D4FC7);
        }
    }

    private boolean snapshotMissing() { return SocialClient.snapshot() == null; }

    private void addClipped(int x, int y, int w, int h, int top, int bottom, Runnable r) {
        int hy = Math.max(y, top), hh = Math.min(y + h, bottom) - hy;
        if (hh > 0) hits.add(new Hit(x, hy, w, hh, r));
    }

    private void renderChat(GuiGraphics g, int mx, int my) {
        int cx = cx(), top = bodyTop(), cw = cw(), bottom = bodyBottom(), ch = bottom - top;
        Ui.chamfer(g, cx, top, cw, ch, 3, PANEL);
        Ui.chamferBorder(g, cx, top, cw, ch, 3, LINE);
        SocialClient.Friend f = SocialClient.find(selected);
        if (selected.isEmpty() || f == null) {
            if (f == null && !selected.isEmpty()) closeChat();
            g.blit(LOGO, cx + cw / 2 - 24, top + ch / 2 - 58, 48, 48, 0f, 0f, 256, 256, 256, 256);
            Ui.heading(g, font, Lang.t("Tus conversaciones", "Your conversations"), cx + cw / 2, top + ch / 2 - 2, TEXT, 1f, true);
            g.drawCenteredString(font, Lang.t("Elige a un amigo de la lista para chatear", "Pick a friend from the list to chat"), cx + cw / 2, top + ch / 2 + 13, MUTED);
            return;
        }
        // cabecera del chat
        g.fillGradient(cx + 1, top + 1, cx + cw - 1, top + 40, 0xFF1E1B38, 0xFF171431);
        g.fill(cx + 1, top + 40, cx + cw - 1, top + 41, LINE);
        int ring = f.state.equals("playing") ? GREEN : f.online() ? CYAN : OFFLINE;
        avatar(g, f.name, cx + 9, top + 7, 26, ring, !f.online(), true);
        g.drawString(font, f.name, cx + 44, top + 9, TEXT, false);
        int tx = cx + 44;
        if (f.state.equals("playing")) { PackInfo.drawIcon(g, tx, top + 22, PackInfo.kindOfName(f.pack)); tx += 14; }
        g.drawString(font, font.plainSubstrByWidth(stateText(f), cw - (tx - cx) - 76), tx, top + 22, ring, false);
        removeBtn.setMessage(Component.literal(confirmRemove ? Lang.t("¿Seguro?", "Sure?") : Lang.t("Quitar", "Remove")));

        // mensajes: se arman de abajo hacia arriba; la rueda los desplaza
        int logTop = top + 45, logBottom = inputY() - 6, logH = logBottom - logTop;
        int maxW = Math.max(60, (int) ((cw - 28) * 0.78f));
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
            bw[i] = w + 14;
            bh[i] = lines.size() * 10 + 8;
            SocialClient.Msg nx = i + 1 < n ? msgs.get(i + 1) : null;
            gap[i] = (nx == null || nx.mine != m.mine || nx.ts - m.ts > 120000L) ? 13 : 3;
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
                    Ui.chamfer(g, bx, y, bw[i], bh[i], 3, 0xFF3B2A86);
                    g.fillGradient(bx + 3, y + 1, bx + bw[i] - 3, y + bh[i] - 1, 0xFF6D4FC7, 0xFF9D3FD0);
                } else {
                    Ui.chamfer(g, bx, y, bw[i], bh[i], 3, PANEL2);
                    Ui.chamferBorder(g, bx, y, bw[i], bh[i], 3, 0xFF2F2B52);
                }
                List<FormattedCharSequence> lines = split.get(i);
                for (int j = 0; j < lines.size(); j++) g.drawString(font, lines.get(j), bx + 7, y + 4 + j * 10, TEXT, false);
                if (gap[i] > 3) {
                    String tm = HOUR.format(new Date(m.ts));
                    g.drawString(font, tm, m.mine ? bx + bw[i] - font.width(tm) : bx, y + bh[i] + 2, FAINT, false);
                }
            }
            y += bh[i] + gap[i];
        }
        g.disableScissor();
        if (n == 0) g.drawCenteredString(font, loadingChat ? Lang.t("Cargando…", "Loading…") : Lang.t("Aún no hay mensajes. ¡Saluda!", "No messages yet. Say hi!"), cx + cw / 2, logTop + logH / 2 - 4, MUTED);
        else if (scrollChat > 0) g.drawCenteredString(font, Lang.t("Desplazado: baja para ver lo más nuevo", "Scrolled: scroll down for the newest"), cx + cw / 2, logBottom - 10, 0xFF6D4FC7);

        // caja de mensaje (los widgets se pintan encima)
        inputBox(g, inputX(), inputY(), inputW(), FIELD_H, chatBox.isFocused());
    }
}
