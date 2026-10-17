package com.atenas.social;

import java.util.Locale;

import com.atenas.social.cosmetics.CosmeticSync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Logo de Atenas junto al nombre (nametag y lista TAB) de quienes usan el mod o el cliente. El logo es un carácter de la fuente "badge". */
public final class Badge {
    public static final String GLYPH = "\uE000";

    public static boolean isUser(String name) {
        if (name == null || name.isEmpty()) return false;
        if (name.equalsIgnoreCase(SocialClient.myName())) return true;
        return CosmeticSync.idsOf(name.toLowerCase(Locale.ROOT)) != null;
    }

    /**
     * Logo + nombre. IMPORTANTE: el logo va en su propio componente y el resto como hermano de un contenedor vacio. Si el nombre
     * se agregara con append() al propio componente del logo, heredaria su fuente (\"badge\", que solo tiene el logo) y todas las
     * letras saldrian como cuadros vacios. Ademas se fija la fuente por defecto en el nombre por si ya traia la del logo.
     */
    private static MutableComponent withBadge(Component base) {
        // Solo el logo va en la fuente "badge". El espacio de despues va en la fuente por defecto: la fuente "badge" no tiene
        // el caracter " " y Minecraft lo dibuja como un cuadrito (el "error" que salia junto al nombre).
        MutableComponent logo = Component.literal(GLYPH).withStyle(Style.EMPTY.withFont(Ui.BADGE_FONT));
        MutableComponent gap = Component.literal(" ").withStyle(Style.EMPTY.withFont(Style.DEFAULT_FONT));
        MutableComponent rest = base.copy();
        if (Ui.BADGE_FONT.equals(rest.getStyle().getFont())) rest.setStyle(rest.getStyle().withFont(Style.DEFAULT_FONT));
        return Component.empty().append(logo).append(gap).append(rest);
    }

    @SubscribeEvent
    public void onNameTag(RenderNameTagEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getContent().getString().startsWith(GLYPH)) return;
        if (!isUser(p.getGameProfile().getName())) return;
        e.setContent(withBadge(e.getContent()));
    }

    /** Pone el logo en el nombre del TAB (se repite cada medio segundo porque el servidor puede reponer el nombre). */
    public static void updateTab(Minecraft mc) {
        if (mc.getConnection() == null) return;
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().getName();
            if (!isUser(name)) continue;
            Component cur = info.getTabListDisplayName();
            if (cur != null && cur.getString().startsWith(GLYPH)) continue;
            Component base = cur != null ? cur : PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(name));
            info.setTabListDisplayName(withBadge(base));
        }
    }
}
