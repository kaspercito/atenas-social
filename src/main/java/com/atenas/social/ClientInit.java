package com.atenas.social;

import com.atenas.social.cosmetics.CosmeticRender;
import com.atenas.social.cosmetics.CosmeticSync;
import com.atenas.social.cosmetics.CosmeticsScreen;
import com.atenas.social.cosmetics.PetRender;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

/** Solo se carga en el cliente: menú de Atenas, teclas, cosméticos, servidor destacado y ciclo de sincronización. */
public final class ClientInit {
    private static KeyMapping openKey, cosmeticsKey;
    private static int ticks;

    private ClientInit() {}

    static void init() {
        IEventBus mod = FMLJavaModLoadingContext.get().getModEventBus();
        mod.addListener(ClientInit::registerKeys);
        mod.addListener(CosmeticRender::registerDefinitions);
        mod.addListener(CosmeticRender::addLayers);
        MinecraftForge.EVENT_BUS.register(new ClientInit());
        MinecraftForge.EVENT_BUS.register(new PetRender());
        MinecraftForge.EVENT_BUS.register(new Badge());
    }

    private static void registerKeys(RegisterKeyMappingsEvent e) {
        openKey = new KeyMapping("Abrir amigos de Atenas", GLFW.GLFW_KEY_J, "Atenas");
        cosmeticsKey = new KeyMapping("Abrir cosm\u00e9ticos", GLFW.GLFW_KEY_UNKNOWN, "Atenas");
        e.register(openKey);
        e.register(cosmeticsKey);
    }

    private static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof SocialScreen)) mc.setScreen(new SocialScreen(mc.screen));
    }

    /** Menú principal de Atenas en lugar del de Minecraft; y la lista de Multijugador con el servidor del pack arriba. */
    @SubscribeEvent
    public void onOpening(ScreenEvent.Opening e) {
        Screen s = e.getNewScreen();
        if (s != null && s.getClass() == TitleScreen.class) e.setNewScreen(new AtenasTitleScreen());
        else if (s instanceof JoinMultiplayerScreen) FeaturedServer.ensure();
    }

    /** Shift+Tab o la tecla configurada, con el juego en marcha. */
    @SubscribeEvent
    public void onKey(InputEvent.Key e) {
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        boolean shiftTab = e.getKey() == GLFW.GLFW_KEY_TAB && (e.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (shiftTab || (openKey != null && openKey.matches(e.getKey(), e.getScanCode()))) open();
        else if (cosmeticsKey != null && cosmeticsKey.matches(e.getKey(), e.getScanCode())) mc.setScreen(new CosmeticsScreen(null));
    }

    /** Shift+Tab también desde menús (no dentro del chat, donde Tab autocompleta). */
    @SubscribeEvent
    public void onScreenKey(ScreenEvent.KeyPressed.Pre e) {
        if (e.getScreen() instanceof SocialScreen || e.getScreen() instanceof ChatScreen) return;
        if (e.getKeyCode() == GLFW.GLFW_KEY_TAB && (e.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
            Minecraft mc = Minecraft.getInstance();
            Screen sc = e.getScreen();
            if (sc instanceof TitleScreen || sc instanceof AtenasTitleScreen || sc instanceof PauseScreen || mc.level != null) {
                e.setCanceled(true);
                mc.setScreen(new SocialScreen(sc));
            }
        }
    }

    @SubscribeEvent
    public void onInitScreen(ScreenEvent.Init.Post e) {
        Screen sc = e.getScreen();
        if (sc instanceof TitleScreen || sc instanceof PauseScreen) {
            e.addListener(new Ui.Btn(4, 4, 70, 18, Component.literal("Amigos"), false, b -> Minecraft.getInstance().setScreen(new SocialScreen(sc))));
            e.addListener(new Ui.Btn(4, 24, 70, 18, Component.literal("Cosm\u00e9ticos"), false, b -> Minecraft.getInstance().setScreen(new CosmeticsScreen(sc))));
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() == null) return;
        SocialClient.nameSupplier = () -> mc.getUser().getName();
        ticks++;
        if (ticks % 20 == 0) CosmeticSync.tick(mc);
        if (ticks % 10 == 0) Badge.updateTab(mc);
        if (ticks % 10 != 0) return; // cada medio segundo
        boolean inGame = mc.player != null;
        boolean social = mc.screen instanceof SocialScreen;
        long every = social ? 4000L : (inGame ? 25000L : 15000L);
        String server = (inGame && mc.getCurrentServer() != null) ? mc.getCurrentServer().ip : "";
        if (SocialClient.sinceLastSync() > (SocialClient.snapshot() == null ? 5000L : every)) { // sin estado: reintenta cada 5 s, no cada tick
            SocialClient.sync(inGame, server);
        } else if (!social && SocialClient.sinceLastPoll() > 4000L) {
            // consulta rápida: solo si algo cambió se baja el estado completo (ver SocialClient.poll)
            SocialClient.poll(inGame, server);
        }
    }

    /** Avisos en la partida (sin pantalla abierta; con pantalla los dibuja onScreenRender). */
    @SubscribeEvent
    public void onHud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui) return;
        Toasts.draw(e.getGuiGraphics());
    }

    /** Avisos sobre cualquier pantalla (menú principal, pausa, inventario...). */
    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post e) {
        Toasts.draw(e.getGuiGraphics());
    }
}
