package com.modamod.client;

import com.modamod.util.RedTwirl;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * El twirl del lado del cliente (2026-09-30, "me gustaria poder hacer un
 * twirl apretando alguna tecla con pollera equipada y que se abran y
 * giren"): la tecla (R por defecto, se cambia en Controles) le pide la vuelta
 * al servidor ({@link RedTwirl}); cuando llega el aviso se anota cuándo
 * empezó. {@link #progreso} lo leen el giro del cuerpo
 * ({@code LivingEntityRendererMixin}) y la pollera ({@code PolleraMalla}).
 */
public final class TwirlCliente {

    private TwirlCliente() {}

    private static KeyBinding tecla;
    /** Tick del mundo en que arrancó la vuelta, por id de entidad. */
    private static final Map<Integer, Long> INICIO = new HashMap<>();

    public static void init() {
        tecla = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modamod.twirl",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.modamod"));

        ClientPlayNetworking.registerGlobalReceiver(RedTwirl.Girar.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().world != null) {
                        INICIO.put(payload.entidad(), context.client().world.getTime());
                    }
                }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (tecla.wasPressed()) {
                if (client.player != null && progreso(client.player, 0f) < 0f) {
                    ClientPlayNetworking.send(new RedTwirl.Pedir());
                }
            }
            if (client.world != null && !INICIO.isEmpty()) {
                long ahora = client.world.getTime();
                INICIO.values().removeIf(t -> ahora - t > RedTwirl.DURACION + 1);
            }
        });
    }

    /** 0..1 durante la vuelta de {@code entidad}, -1 si no está girando. */
    public static float progreso(Entity entidad, float tickDelta) {
        Long inicio = INICIO.get(entidad.getId());
        MinecraftClient client = MinecraftClient.getInstance();
        if (inicio == null || client.world == null) return -1f;
        float t = (client.world.getTime() - inicio + tickDelta) / RedTwirl.DURACION;
        return t < 0f || t > 1f ? -1f : t;
    }

    /** Ángulo de la vuelta (grados), con arranque y frenada suaves. */
    public static float angulo(float progreso) {
        if (progreso < 0f) return 0f;
        return 360f * progreso * progreso * (3f - 2f * progreso);
    }
}
