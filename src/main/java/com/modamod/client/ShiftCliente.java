package com.modamod.client;

/** Tecla Shift para los tooltips (2026-10-05): vive en el paquete del cliente porque usa clases solo de cliente. */
public final class ShiftCliente {

    private ShiftCliente() {}

    public static boolean apretado() {
        return net.minecraft.client.gui.screen.Screen.hasShiftDown();
    }
}
