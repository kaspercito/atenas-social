package com.atenas.social;

import com.atenas.social.cosmetics.CosmeticsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.ModListScreen;

/**
 * Menú principal de Atenas, con el mismo diseño que el del cliente 1.8.9: fondo noche con rejilla de neón en perspectiva, estrellas,
 * línea de escaneo, logo con pulso y botones futuristas. Mantiene el acceso directo al servidor del pack.
 */
public class AtenasTitleScreen extends Screen {
    private static final ResourceLocation LOGO = new ResourceLocation("atenassocial", "textures/gui/logo.png");
    private static final int BW = 230;

    private final Ui.Backdrop backdrop = new Ui.Backdrop();
    private int logoSize, logoY, titleY;
    private boolean showLogo;

    public AtenasTitleScreen() {
        super(Component.literal("Atenas"));
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        boolean server = FeaturedServer.present();
        int rows = server ? 5 : 4;
        int rowH = height < 260 ? 20 : 24, gap = height < 260 ? 3 : 5;
        int buttons = rows * (rowH + gap) + 6;
        showLogo = height >= 250;
        logoSize = Mth.clamp((height - buttons - 70) / 2, 40, 76);
        int header = showLogo ? logoSize + 40 : 28;
        int top = Math.max(6, (height - header - buttons) / 2);
        logoY = top;
        titleY = top + (showLogo ? logoSize + 6 : 0);
        int y = top + header;
        int x = (width - BW) / 2;

        addRenderableWidget(new Ui.MenuBtn(x, y, BW, rowH, Component.literal(Lang.t("Un jugador", "Singleplayer")), null, false, b -> mc.setScreen(new SelectWorldScreen(this))));
        y += rowH + gap;
        addRenderableWidget(new Ui.MenuBtn(x, y, BW, rowH, Component.literal(Lang.t("Multijugador", "Multiplayer")), null, false, b ->
            mc.setScreen(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))));
        y += rowH + gap;
        if (server) {
            addRenderableWidget(new Ui.MenuBtn(x, y, BW, rowH, Component.literal(Lang.t("Entrar a ", "Join ") + PackInfo.serverName()), "\u2605", false, b -> join()));
            y += rowH + gap;
        }
        y += 6;
        int sh = rowH - 3, half = (BW - 6) / 2;
        addRenderableWidget(new Ui.MenuBtn(x, y, half, sh, Component.literal(Lang.t("Cosméticos", "Cosmetics")), null, true, b -> mc.setScreen(new CosmeticsScreen(this))));
        addRenderableWidget(new Ui.MenuBtn(x + half + 6, y, half, sh, Component.literal(Lang.t("Amigos", "Friends")), "Shift+Tab", true, b -> mc.setScreen(new SocialScreen(this))));
        y += sh + gap;
        int third = (BW - 12) / 3;
        addRenderableWidget(new Ui.MenuBtn(x, y, third, sh, Component.literal(Lang.t("Opciones", "Options")), null, true, b -> mc.setScreen(new OptionsScreen(this, mc.options))));
        addRenderableWidget(new Ui.MenuBtn(x + third + 6, y, third, sh, Component.literal("Mods"), null, true, b -> mc.setScreen(new ModListScreen(this))));
        addRenderableWidget(new Ui.MenuBtn(x + (third + 6) * 2, y, third, sh, Component.literal(Lang.t("Salir", "Quit")), null, true, b -> mc.stop()));
    }

    private void join() {
        Minecraft mc = Minecraft.getInstance();
        String ip = PackInfo.server();
        ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(ip), new ServerData(FeaturedServer.title(), ip, false), false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long t = System.currentTimeMillis();
        backdrop.render(g, width, height);

        // marca: logo con pulso, nombre y subtítulo
        if (showLogo) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(t * 0.003));
            for (int k = 3; k >= 1; k--) {
                int gs = logoSize + k * 10;
                g.fill(width / 2 - gs / 2, logoY + logoSize / 2 - gs / 2, width / 2 + gs / 2, logoY + logoSize / 2 + gs / 2, (((int) ((8 + 10 * pulse) / k)) << 24) | 0x8B5CF6);
            }
            g.blit(LOGO, (width - logoSize) / 2, logoY, logoSize, logoSize, 0, 0, 256, 256, 256, 256);
        }
        Ui.heading(g, font, "ATENAS", width / 2, titleY, Ui.ACCENT_LIGHT, 2.4f, true);
        int subY = titleY + 24;
        Ui.heading(g, font, "M   O   D   \u00b7   1.20.1", width / 2, subY, Ui.CYAN, 1f, true);
        g.fill(width / 2 - 60, subY + 12, width / 2 + 60, subY + 13, 0xFF6D4FC7);
        g.fill(width / 2 - 8, subY + 11, width / 2 + 8, subY + 14, Ui.ACCENT);

        super.render(g, mx, my, pt);

        // greca arriba y esquinas HUD
        for (int gx = 0; gx + 12 <= width; gx += 12) {
            int ga = 0x558B5CF6;
            g.fill(gx, 3, gx + 8, 4, ga); g.fill(gx, 3, gx + 1, 9, ga); g.fill(gx, 8, gx + 5, 9, ga); g.fill(gx + 4, 5, gx + 5, 8, ga);
        }
        int c = 0xAA8B5CF6;
        g.fill(8, 14, 30, 15, c); g.fill(8, 14, 9, 36, c);
        g.fill(8, height - 9, 30, height - 8, c); g.fill(8, height - 30, 9, height - 8, c);

        // pie: pack a la izquierda, usuario a la derecha
        PackInfo.drawIcon(g, 18, height - 20, PackInfo.kind());
        g.drawString(font, Ui.ui(PackInfo.packName()), 32, height - 19, Ui.MUTED, true);
        String who = minecraft.getUser().getName();
        g.drawString(font, Ui.ui(who), width - font.width(Ui.ui(who)) - 18, height - 19, Ui.TEXT, true);
    }
}
