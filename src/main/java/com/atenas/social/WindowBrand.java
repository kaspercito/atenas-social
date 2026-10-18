package com.atenas.social;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Nombre e icono de la ventana (igual que el cliente 1.8.9: "Atenas 1.0.0 | Minecraft 1.20.1" con el logo de Atenas).
 * Minecraft/Forge vuelven a poner su título al entrar o salir de un mundo, así que se reaplica cada ~2 s.
 */
final class WindowBrand {
    private static boolean iconTried;
    private static String title;

    private WindowBrand() {}

    private static String title() {
        if (title == null) {
            String v = ModList.get().getModContainerById("atenassocial").map(c -> c.getModInfo().getVersion().toString()).orElse("");
            title = (v.isEmpty() || v.startsWith("0.0.0") ? "Atenas" : "Atenas " + v) + " | Minecraft 1.20.1";
        }
        return title;
    }

    /** Llamar desde el tick del cliente (hilo principal). */
    static void tick(Minecraft mc, int ticks) {
        try {
            if (mc.getWindow() == null) return;
            if (ticks % 40 == 1) mc.getWindow().setTitle(title());
            if (!iconTried) { iconTried = true; setIcon(mc); }
        } catch (Throwable ignored) { /* el nombre y el icono son opcionales */ }
    }

    private static void setIcon(Minecraft mc) {
        ByteBuffer file = null;
        try (InputStream in = WindowBrand.class.getResourceAsStream("/assets/atenassocial/textures/gui/logo.png")) {
            if (in == null) return;
            byte[] data = in.readAllBytes();
            file = MemoryUtil.memAlloc(data.length);
            file.put(data).flip();
            try (MemoryStack st = MemoryStack.stackPush()) {
                IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), c = st.mallocInt(1);
                ByteBuffer px = STBImage.stbi_load_from_memory(file, w, h, c, 4);
                if (px == null) return;
                try {
                    GLFWImage.Buffer icons = GLFWImage.malloc(1, st);
                    icons.position(0).width(w.get(0)).height(h.get(0)).pixels(px);
                    GLFW.glfwSetWindowIcon(mc.getWindow().getWindow(), icons);
                } finally {
                    STBImage.stbi_image_free(px);
                }
            }
        } catch (Throwable ignored) { /* sin icono: se queda el de Minecraft */
        } finally {
            if (file != null) MemoryUtil.memFree(file);
        }
    }
}
