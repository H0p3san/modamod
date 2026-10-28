package com.modamod.util;

import com.modamod.Modamod;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El twirl (2026-09-30, "me gustaria poder hacer un twirl apretando alguna
 * tecla con pollera equipada y que se abran y giren"): el cliente pide girar
 * ({@link Pedir}), el servidor mira que tenga una pollera puesta y avisa a
 * todos los que lo ven, él incluido ({@link Girar}), así todos ven la vuelta.
 * El dibujo está en {@code client/TwirlCliente}.
 */
public final class RedTwirl {

    private RedTwirl() {}

    /** Largo de la vuelta, en ticks. */
    public static final int DURACION = 16;

    public record Pedir() implements CustomPayload {
        public static final Id<Pedir> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "twirl_pedir"));
        public static final PacketCodec<RegistryByteBuf, Pedir> CODEC = PacketCodec.unit(new Pedir());

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record Girar(int entidad) implements CustomPayload {
        public static final Id<Girar> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "twirl_girar"));
        public static final PacketCodec<RegistryByteBuf, Girar> CODEC =
                PacketCodecs.VAR_INT.<RegistryByteBuf>cast().xmap(Girar::new, Girar::entidad);

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Último twirl por jugador (tick del mundo), para no dejar encadenar vueltas más rápido que su duración. */
    private static final Map<UUID, Long> ULTIMO = new HashMap<>();

    public static void init() {
        PayloadTypeRegistry.playC2S().register(Pedir.ID, Pedir.CODEC);
        PayloadTypeRegistry.playS2C().register(Girar.ID, Girar.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Pedir.ID, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayerEntity jugador = context.player();
                    if (!tienePollera(jugador)) return;
                    long ahora = jugador.getWorld().getTime();
                    Long antes = ULTIMO.get(jugador.getUuid());
                    if (antes != null && ahora - antes < DURACION) return;
                    ULTIMO.put(jugador.getUuid(), ahora);
                    Girar girar = new Girar(jugador.getId());
                    ServerPlayNetworking.send(jugador, girar);
                    for (ServerPlayerEntity otro : PlayerLookup.tracking(jugador)) {
                        if (otro != jugador) ServerPlayNetworking.send(otro, girar);
                    }
                }));
    }

    private static boolean tienePollera(ServerPlayerEntity jugador) {
        return TrinketsApi.getTrinketComponent(jugador)
                .map(c -> c.isEquipped(s -> s.getItem() instanceof com.modamod.item.PolleraItem))
                .orElse(false);
    }
}
