package com.modamod.client;

import com.modamod.util.RedCapucha;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Tecla para subir/bajar la capucha (H por defecto, 2026-09-30) — ver {@link RedCapucha}. */
public final class CapuchaCliente {

    private CapuchaCliente() {}

    private static KeyBinding tecla;

    public static void init() {
        tecla = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modamod.capucha",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.modamod"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (tecla.wasPressed()) {
                if (client.player != null) ClientPlayNetworking.send(new RedCapucha.Alternar());
            }
        });
    }
}
