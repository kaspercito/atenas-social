package com.atenas.social;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;

/** Deja el servidor del modpack primero en la lista de Multijugador, con una estrella. */
public final class FeaturedServer {
    public static final String STAR = "\u00A7e\u2605 \u00A7f";

    private FeaturedServer() {}

    public static boolean present() { return !PackInfo.server().isEmpty(); }

    public static String title() { return STAR + PackInfo.serverName(); }

    /** Se llama justo antes de abrir Multijugador: la pantalla lee la lista ya arreglada. */
    public static void ensure() {
        if (!present()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            String ip = PackInfo.server();
            ServerList list = new ServerList(mc);
            list.load();
            int found = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).ip.equalsIgnoreCase(ip)) { found = i; break; }
            }
            boolean changed = false;
            if (found < 0) {
                list.add(new ServerData(title(), ip, false), false);
                found = list.size() - 1;
                changed = true;
            } else if (!list.get(found).name.equals(title())) {
                list.get(found).name = title();
                changed = true;
            }
            for (int i = found; i > 0; i--) { list.swap(i, i - 1); changed = true; }
            if (changed) list.save();
        } catch (Exception e) {
            System.err.println("[AtenasSocial] servidor destacado: " + e);
        }
    }
}
