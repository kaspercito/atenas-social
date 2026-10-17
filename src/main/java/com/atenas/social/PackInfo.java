package com.atenas.social;

import net.minecraft.client.gui.GuiGraphics;

/** Datos del modpack que manda el launcher (-Datenas.*) y el icono según el tipo de pack. */
public final class PackInfo {
    private PackInfo() {}

    private static String prop(String k) {
        String v = System.getProperty(k);
        return v == null ? "" : v.trim();
    }

    public static String server() { return prop("atenas.server"); }
    public static String serverName() { String n = prop("atenas.servername"); return n.isEmpty() ? "Atenas" : n; }
    public static String packName() { String n = prop("atenas.pack"); return n.isEmpty() ? "Atenas 1.20.1" : n; }

    /** pvp | smp | mods | cube (el launcher lo calcula; si falta, se deduce del nombre). */
    public static String kind() {
        String k = prop("atenas.kind");
        return k.isEmpty() ? kindOfName(packName()) : k;
    }

    public static String kindOfName(String name) {
        String t = name == null ? "" : name.toLowerCase();
        if (t.contains("pvp") || t.contains("client") || t.contains("cliente") || t.contains("1.8")) return "pvp";
        if (t.contains("smp") || t.contains("survival") || t.contains("supervivencia")) return "smp";
        if (t.contains("1.20") || t.contains("forge") || t.contains("fabric") || t.contains("mod")) return "mods";
        return "cube";
    }

    /** Icono de 10x10 dibujado con rectángulos (sin texturas). */
    public static void drawIcon(GuiGraphics g, int x, int y, String kind) {
        switch (kind == null ? "" : kind) {
            case "pvp" -> { // espadas cruzadas
                for (int i = 0; i < 9; i++) {
                    g.fill(x + i, y + i, x + i + 2, y + i + 1, 0xFFE8F1FB);
                    g.fill(x + 8 - i, y + i, x + 10 - i, y + i + 1, 0xFFA9BDD3);
                }
                g.fill(x, y + 8, x + 3, y + 10, 0xFFB45309);
                g.fill(x + 7, y + 8, x + 10, y + 10, 0xFFB45309);
            }
            case "smp" -> { // árbol
                g.fill(x + 4, y + 6, x + 6, y + 10, 0xFF6B4423);
                g.fill(x + 1, y + 2, x + 9, y + 7, 0xFF2F8F46);
                g.fill(x + 3, y, x + 7, y + 3, 0xFF3BB55A);
            }
            case "mods" -> { // pieza de rompecabezas
                g.fill(x + 1, y + 3, x + 9, y + 9, 0xFF8B5CF6);
                g.fill(x + 3, y + 1, x + 7, y + 3, 0xFFA78BFA);
                g.fill(x + 4, y + 5, x + 6, y + 7, 0xFF22D3EE);
            }
            default -> { // cubo
                g.fill(x + 1, y + 1, x + 9, y + 9, 0xFF3F9A4B);
                g.fill(x + 1, y + 1, x + 9, y + 3, 0xFF67C877);
                g.fill(x + 1, y + 3, x + 9, y + 9, 0xFF7A5230);
            }
        }
    }
}
