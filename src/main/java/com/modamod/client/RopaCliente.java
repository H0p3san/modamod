package com.modamod.client;

import com.modamod.mixin.HandledScreenAccessor;
import com.modamod.ropa.RedRopa;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Cómo se abre la pantalla de Ropa (2026-10-05): botón en el inventario y tecla K ("asignale el q se te ocurra"). */
public final class RopaCliente {

    private RopaCliente() {}

    public static KeyBinding tecla;

    public static void init() {
        tecla = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modamod.ropa",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.modamod"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (tecla.wasPressed()) {
                // Con otra pantalla abierta (chat, cofre...) no hace nada; la pantalla de Ropa la cierra ella.
                if (client.player != null && client.currentScreen == null) ClientPlayNetworking.send(new RedRopa.Abrir());
            }
        });
        // Botón "Ropa" en el inventario de supervivencia, bajo la grilla de crafteo (junto al libro de recetas).
        ScreenEvents.AFTER_INIT.register((client, screen, ancho, alto) -> {
            if (!(screen instanceof InventoryScreen inv)) return;
            HandledScreenAccessor a = (HandledScreenAccessor) inv;
            Screens.getButtons(screen).add(ButtonWidget.builder(Text.translatable("modamod.ropa.boton"),
                            b -> ClientPlayNetworking.send(new RedRopa.Abrir()))
                    .dimensions(a.modamod$getX() + 126, a.modamod$getY() + 61, 44, 18).build());
        });
    }
}
