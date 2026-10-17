package com.atenas.social;

import com.atenas.social.cosmetics.CosmeticsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.ModListScreen;

/** Menú principal de Atenas: mismo panorama de Minecraft, paleta violeta, logo y acceso directo al servidor del pack. */
public class AtenasTitleScreen extends Screen {
    private static final ResourceLocation LOGO = new ResourceLocation("atenassocial", "textures/gui/logo.png");
    private static final PanoramaRenderer PANORAMA = new PanoramaRenderer(TitleScreen.CUBE_MAP);
    private static final int BW = 200;

    private int logoSize, logoY, titleY;
    private boolean showLogo;

    public AtenasTitleScreen() { super(Component.literal("Atenas")); }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        boolean server = FeaturedServer.present();
        int rows = server ? 5 : 4;
        int rowH = height < 260 ? 20 : 22, gap = height < 260 ? 3 : 4;
        int buttons = rows * (rowH + gap);
        showLogo = height >= 250;
        logoSize = Mth.clamp((height - buttons - 50) / 2, 40, 84);
        int header = showLogo ? logoSize + 30 : 22;
        int top = Math.max(6, (height - header - buttons) / 2);
        logoY = top;
        titleY = top + (showLogo ? logoSize + 4 : 0);
        int y = top + header;
        int x = (width - BW) / 2;

        addRenderableWidget(new Ui.Btn(x, y, BW, rowH, Component.literal("Un jugador"), false, b -> mc.setScreen(new SelectWorldScreen(this))));
        y += rowH + gap;
        addRenderableWidget(new Ui.Btn(x, y, BW, rowH, Component.literal("Multijugador"), false, b ->
            mc.setScreen(mc.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))));
        y += rowH + gap;
        if (server) {
            addRenderableWidget(new Ui.Btn(x, y, BW, rowH, Component.literal("\u2605 Entrar a " + PackInfo.serverName()), true, b -> join()));
            y += rowH + gap;
        }
        int half = (BW - 4) / 2;
        addRenderableWidget(new Ui.Btn(x, y, half, rowH, Component.literal("Cosm\u00e9ticos"), false, b -> mc.setScreen(new CosmeticsScreen(this))));
        addRenderableWidget(new Ui.Btn(x + half + 4, y, half, rowH, Component.literal("Amigos"), false, b -> mc.setScreen(new SocialScreen(this))));
        y += rowH + gap;
        int third = (BW - 8) / 3;
        addRenderableWidget(new Ui.Btn(x, y, third, rowH, Component.literal("Opciones"), false, b -> mc.setScreen(new OptionsScreen(this, mc.options))));
        addRenderableWidget(new Ui.Btn(x + third + 4, y, third, rowH, Component.literal("Mods"), false, b -> mc.setScreen(new ModListScreen(this))));
        addRenderableWidget(new Ui.Btn(x + (third + 4) * 2, y, third, rowH, Component.literal("Salir"), false, b -> mc.stop()));
    }

    private void join() {
        Minecraft mc = Minecraft.getInstance();
        String ip = PackInfo.server();
        ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(ip), new ServerData(FeaturedServer.title(), ip, false), false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        PANORAMA.render(pt, 1.0F);
        g.fillGradient(0, 0, width, height, 0xB0090812, 0xE614102E);

        // motas de luz que suben despacio (cian y violeta, como los nodos del logo)
        long t = System.currentTimeMillis();
        for (int i = 0; i < 36; i++) {
            float sx = (i * 97 % 100) / 100f, speed = 0.012f + (i % 7) * 0.003f;
            float fy = 1f - ((t * speed / 1000f + (i * 53 % 100) / 100f) % 1f);
            int px = (int) (sx * width + Mth.sin(t / 1800f + i) * 6), py = (int) (fy * height);
            int a = (int) (Mth.sin(fy * (float) Math.PI) * 120);
            g.fill(px, py, px + 2, py + 2, (a << 24) | (i % 3 == 0 ? 0x22D3EE : 0xA78BFA));
        }

        if (showLogo) g.blit(LOGO, (width - logoSize) / 2, logoY, logoSize, logoSize, 0, 0, 256, 256, 256, 256);
        g.pose().pushPose();
        g.pose().scale(2f, 2f, 1f);
        g.drawCenteredString(font, "ATENAS", width / 4, titleY / 2, Ui.ACCENT_LIGHT);
        g.pose().popPose();
        g.drawCenteredString(font, "Minecraft 1.20.1", width / 2, titleY + 19, Ui.MUTED);

        super.render(g, mx, my, pt);

        // abajo a la izquierda: el pack que estás jugando, con su icono
        PackInfo.drawIcon(g, 6, height - 16, PackInfo.kind());
        g.drawString(font, PackInfo.packName(), 20, height - 15, Ui.MUTED, false);
        String who = minecraft.getUser().getName();
        g.drawString(font, who, width - font.width(who) - 6, height - 15, Ui.MUTED, false);
    }
}
