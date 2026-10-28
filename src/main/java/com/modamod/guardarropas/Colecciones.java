package com.modamod.guardarropas;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Acceso a las colecciones de outfits del mundo y su envío a quienes las tienen abiertas. */
public final class Colecciones {

    private Colecciones() {}

    public static void init() {
        PayloadTypeRegistry.playS2C().register(OutfitsPayload.ID, OutfitsPayload.CODEC);
    }

    /** Una colección como {@link AlmacenOutfits}: lo que {@link Outfits} modifica va al mundo y se reenvía a los que la miran. */
    public static AlmacenOutfits almacen(MinecraftServer server, UUID id) {
        OutfitsGuardados estado = OutfitsGuardados.de(server);
        OutfitsGuardados.Coleccion c = estado.coleccion(id);
        return new AlmacenOutfits() {
            @Override public List<GuardarropasBlockEntity.Outfit> outfits() { return c.outfits; }
            @Override public Map<UUID, String> ultimos() { return c.ultimos; }

            @Override
            public void guardado() {
                estado.markDirty();
                enviarATodos(server, id);
            }
        };
    }

    /** Le manda a {@code jugador} la lista actual de la colección (al abrir la pantalla). */
    public static void enviar(ServerPlayerEntity jugador, UUID id) {
        ServerPlayNetworking.send(jugador, new OutfitsPayload(OutfitsGuardados.de(jugador.getServer()).datos(id)));
    }

    /** Después de un cambio: a todos los que tienen abierta una pantalla de esa colección. */
    public static void enviarATodos(MinecraftServer server, UUID id) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.currentScreenHandler instanceof VisorColeccion v && id.equals(v.coleccion())) enviar(p, id);
        }
    }
}
