package com.modamod.client;

import com.modamod.body.PerfilesDeCuerpo;
import com.modamod.body.RedCuerpo;
import com.modamod.render.GarmentFeatureRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Cuándo se abre {@link ElegirCuerpoScreen} (2026-09-29, "la primera vez que
 * uno se pone una prenda del mod te lance una gui"):
 * <ul>
 *   <li>Sola, la primera vez que el jugador tiene puesta alguna prenda del
 *   mod y su perfil todavía no fue elegido. Una vez por sesión: si la
 *   cierra con "Ahora no", no insiste hasta la próxima vez que entre.</li>
 *   <li>Con {@code /modamod elegir} (paquete {@link RedCuerpo.Abrir}).</li>
 * </ul>
 */
public final class ElegirCuerpoCliente {

    /**
     * Espera antes de mirar el perfil al entrar: el attachment sincronizado
     * llega unos ticks después del jugador, y hasta entonces se lee el
     * default (sin elegir) — sin esto saltaría a quien ya eligió.
     */
    private static final int TICKS_DE_GRACIA = 60;

    private static boolean yaMostradaEstaSesion = false;

    private ElegirCuerpoCliente() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(RedCuerpo.Abrir.ID, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new ElegirCuerpoScreen())));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> yaMostradaEstaSesion = false);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity jugador = client.player;
            if (yaMostradaEstaSesion || jugador == null || client.currentScreen != null) return;
            if (jugador.age < TICKS_DE_GRACIA) return;
            if (PerfilesDeCuerpo.de(jugador).elegido()) return;
            if (GarmentFeatureRenderer.equipadas(jugador).isEmpty()) return;
            yaMostradaEstaSesion = true;
            client.setScreen(new ElegirCuerpoScreen());
        });
    }
}
