package com.modamod.client;

import com.modamod.util.RedFrente;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Tecla para abrir/cerrar la chaqueta (J por defecto, 2026-10-11) — ver {@link RedFrente}. */
public final class FrenteCliente {

    private FrenteCliente() {}

    private static KeyBinding tecla;

    public static void init() {
        tecla = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modamod.frente",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.modamod"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (tecla.wasPressed()) {
                if (client.player != null) ClientPlayNetworking.send(new RedFrente.Alternar());
            }
        });
    }
}
