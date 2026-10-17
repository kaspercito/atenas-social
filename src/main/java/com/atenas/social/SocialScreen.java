package com.atenas.social;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Amigos + chat de Atenas. Izquierda: lista y solicitudes. Derecha: conversación. */
public class SocialScreen extends Screen {
    private static final int BG = 0xEE0E0C20, PANEL = 0xFF14122A, LINE = 0xFF2B2848, ACCENT = 0xFF8B5CF6;
    private static final int TEXT = 0xFFF1EEFC, MUTED = 0xFF9B97B8, GREEN = 0xFF4ADE80, RED = 0xFFF87171, GOLD = 0xFFFACC15;
    private static final int LW = 132, ROW = 18;

    private static final class Row {
        int type; // 0 título, 1 solicitud recibida, 2 amigo, 3 solicitud enviada
        String text = "", key = "", sub = "";
        int color = TEXT, unread;
    }

    private final Screen parent;
    private EditBox addBox, chatBox;
    private Button addBtn, removeBtn;
    private int px, py, pw, ph, scroll;
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

    @Override
    protected void init() {
        pw = Math.min(width - 16, 400);
        ph = Math.min(height - 16, 250);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        addBox = new EditBox(font, px + 6, py + 24, LW - 12, 16, Component.literal("Nick"));
        addBox.setHint(Component.literal("Nick de Minecraft"));
        addBox.setMaxLength(16);
        addRenderableWidget(addBox);
        addBtn = addRenderableWidget(Button.builder(Component.literal("Enviar solicitud"), b -> addFriend())
            .bounds(px + 6, py + 43, LW - 12, 16).build());
        chatBox = new EditBox(font, px + LW + 8, py + ph - 22, pw - LW - 16, 16, Component.literal("Mensaje"));
        chatBox.setHint(Component.literal("Escribe un mensaje…"));
        chatBox.setMaxLength(300);
        addRenderableWidget(chatBox);
        removeBtn = addRenderableWidget(Button.builder(Component.literal("Quitar"), b -> removeSelected())
            .bounds(px + pw - 54, py + 5, 48, 14).build());
        removeBtn.visible = false;
        chatBox.visible = false;
        SocialClient.sync(Minecraft.getInstance().player != null, "");
        if (!selected.isEmpty()) openChat(selected, true);
    }

    @Override
    public void removed() { SocialClient.viewingKey = ""; }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }

    private void say(String s) { flash = s; flashTicks = 100; }

    // ------------------------------------------------------------ acciones
    private void addFriend() {
        String n = addBox.getValue().trim();
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
        selected = ""; msgs.clear(); lastId = 0; confirmRemove = false;
        SocialClient.viewingKey = "";
        if (chatBox != null) chatBox.visible = false;
        if (removeBtn != null) removeBtn.visible = false;
    }

    private void openChat(String key, boolean force) {
        if (!force && key.equals(selected)) return;
        selected = key; msgs.clear(); lastId = 0; confirmRemove = false;
        SocialClient.viewingKey = key;
        chatBox.visible = true; removeBtn.visible = true;
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
            for (SocialClient.Msg m : list) {
                boolean dup = false;
                for (SocialClient.Msg x : msgs) if (x.id == m.id && m.id != 0) { dup = true; break; }
                if (!dup) msgs.add(m);
                if (m.id > lastId) lastId = m.id;
            }
            msgs.removeIf(m -> m.pending);
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
            out.add(title("Solicitudes (" + s.incoming.size() + ")"));
            for (SocialClient.Req r : s.incoming) { Row w = new Row(); w.type = 1; w.text = r.name; w.key = r.key; out.add(w); }
        }
        List<SocialClient.Friend> pl = new ArrayList<>(), on = new ArrayList<>(), off = new ArrayList<>();
        for (SocialClient.Friend f : s.friends) (f.state.equals("playing") ? pl : f.online() ? on : off).add(f);
        group(out, "Jugando", pl); group(out, "En línea", on); group(out, "Desconectados", off);
        if (!s.outgoing.isEmpty()) {
            out.add(title("Enviadas"));
            for (SocialClient.Req r : s.outgoing) { Row w = new Row(); w.type = 3; w.text = r.name; w.key = r.key; w.color = MUTED; out.add(w); }
        }
        if (s.friends.isEmpty() && s.incoming.isEmpty() && s.outgoing.isEmpty()) {
            Row w = title("Aún no tienes amigos"); out.add(w);
        }
        return out;
    }

    private static Row title(String t) { Row r = new Row(); r.type = 0; r.text = t; r.color = MUTED; return r; }

    private static void group(List<Row> out, String name, List<SocialClient.Friend> list) {
        if (list.isEmpty()) return;
        out.add(title(name + " (" + list.size() + ")"));
        for (SocialClient.Friend f : list) {
            Row w = new Row(); w.type = 2; w.text = f.name; w.key = f.key; w.unread = f.unread;
            w.color = f.state.equals("playing") ? GREEN : f.online() ? GOLD : MUTED;
            out.add(w);
        }
    }

    private int listTop() { return py + 66; }
    private int listBottom() { return py + ph - 6; }
    private int visibleRows() { return Math.max(1, (listBottom() - listTop()) / ROW); }

    // ------------------------------------------------------------ input
    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx < px + LW) { scroll = Math.max(0, scroll - (int) Math.signum(delta)); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx >= px && mx < px + LW && my >= listTop() && my < listBottom()) {
            List<Row> rows = rows();
            int idx = scroll + (int) ((my - listTop()) / ROW);
            if (idx >= 0 && idx < rows.size()) {
                Row r = rows.get(idx);
                int right = px + LW - 6;
                if (r.type == 1) {
                    if (mx >= right - 26 && mx < right - 13) { act("accept", r.key); return true; }
                    if (mx >= right - 13 && mx < right) { act("remove", r.key); return true; }
                } else if (r.type == 2) { openChat(r.key, false); return true; }
                else if (r.type == 3 && mx >= right - 13) { act("remove", r.key); return true; }
            }
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

    // ------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(px, py, px + pw, py + ph, BG);
        g.fill(px, py, px + pw, py + 1, ACCENT);
        g.fill(px + LW, py + 1, px + LW + 1, py + ph, LINE);
        g.drawString(font, "AMIGOS", px + 6, py + 7, TEXT, false);

        String st = SocialClient.status();
        String sub = st.equals("noaccount") ? "Inicia sesión" : st.equals("error") ? "Sin conexión" : SocialClient.snapshot() == null ? "Conectando…" : "";
        if (!sub.isEmpty()) g.drawString(font, sub, px + 52, py + 7, st.equals("error") ? RED : MUTED, false);

        // lista
        List<Row> rows = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visibleRows())));
        g.enableScissor(px, listTop(), px + LW, listBottom());
        int y = listTop();
        for (int i = scroll; i < rows.size() && y < listBottom(); i++, y += ROW) {
            Row r = rows.get(i);
            int right = px + LW - 6;
            if (r.type == 0) { g.drawString(font, r.text, px + 6, y + 5, r.color, false); continue; }
            boolean active = r.type == 2 && r.key.equals(selected);
            boolean hover = mx >= px && mx < px + LW && my >= y && my < y + ROW;
            if (active || hover) g.fill(px + 2, y, px + LW - 2, y + ROW - 1, active ? 0xFF2A2450 : 0xFF1E1B38);
            g.fill(px + 8, y + 7, px + 12, y + 11, r.type == 2 ? r.color : MUTED);
            g.drawString(font, font.plainSubstrByWidth(r.text, LW - 70), px + 17, y + 5, r.type == 3 ? MUTED : TEXT, false);
            if (r.type == 1) { g.drawString(font, "+", right - 24, y + 5, GREEN, false); g.drawString(font, "x", right - 11, y + 5, RED, false); }
            else if (r.type == 3) g.drawString(font, "x", right - 11, y + 5, RED, false);
            else if (r.unread > 0) {
                String n = r.unread > 9 ? "9+" : String.valueOf(r.unread);
                g.fill(right - 14, y + 3, right, y + 14, ACCENT);
                g.drawString(font, n, right - 12, y + 5, TEXT, false);
            }
        }
        g.disableScissor();

        // chat
        int cx = px + LW + 8, cw = pw - LW - 16;
        SocialClient.Friend f = SocialClient.find(selected);
        if (selected.isEmpty() || f == null) {
            if (f == null && !selected.isEmpty()) closeChat();
            g.drawCenteredString(font, "Chat con tus amigos", px + LW + (pw - LW) / 2, py + ph / 2 - 10, TEXT);
            g.drawCenteredString(font, "Elige a un amigo de la lista", px + LW + (pw - LW) / 2, py + ph / 2 + 2, MUTED);
        } else {
            g.drawString(font, f.name, cx, py + 7, TEXT, false);
            String stateText = f.state.equals("playing") ? (f.server.isEmpty() ? "Jugando" : "Jugando en " + f.server) : f.online() ? "En el launcher" : "Desconectado";
            int tx = cx;
            if (f.state.equals("playing")) { PackInfo.drawIcon(g, cx, py + 17, PackInfo.kindOfName(f.pack)); tx = cx + 14; }
            g.drawString(font, stateText, tx, py + 18, f.online() ? GREEN : MUTED, false);
            drawMessages(g, cx, py + 32, cw, py + ph - 28);
            removeBtn.setMessage(Component.literal(confirmRemove ? "¿Seguro?" : "Quitar"));
        }

        super.render(g, mx, my, pt);
        if (flashTicks > 0 && !flash.isEmpty()) g.drawCenteredString(font, flash, px + pw / 2, py + ph + 4 > height - 10 ? py - 12 : py + ph + 4, GOLD);
    }

    private void drawMessages(GuiGraphics g, int x, int top, int w, int bottom) {
        // se arma de abajo hacia arriba: lo último que cabe
        int y = bottom;
        for (int i = msgs.size() - 1; i >= 0 && y > top; i--) {
            SocialClient.Msg m = msgs.get(i);
            List<FormattedCharSequence> lines = font.split(Component.literal(m.body), w - 14);
            int h = lines.size() * 10 + 4;
            y -= h + 3;
            if (y + h < top) break;
            int bx = m.mine ? x + w - Math.min(w, maxWidth(lines) + 10) : x;
            int bw = Math.min(w, maxWidth(lines) + 10);
            g.fill(bx, y, bx + bw, y + h, m.mine ? 0xFF4B3A9A : PANEL);
            for (int j = 0; j < lines.size(); j++) g.drawString(font, lines.get(j), bx + 5, y + 2 + j * 10, TEXT, false);
        }
    }

    private int maxWidth(List<FormattedCharSequence> lines) {
        int m = 0;
        for (FormattedCharSequence l : lines) m = Math.max(m, font.width(l));
        return m;
    }
}
