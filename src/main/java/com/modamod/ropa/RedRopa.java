package com.modamod.ropa;

import com.modamod.Modamod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** El cliente pide abrir la pantalla de Ropa (botón del inventario o tecla); el servidor la abre. */
public final class RedRopa {

    private RedRopa() {}

    public record Abrir() implements CustomPayload {
        public static final Id<Abrir> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "ropa_abrir"));
        public static final PacketCodec<RegistryByteBuf, Abrir> CODEC = PacketCodec.unit(new Abrir());

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(Abrir.ID, Abrir.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Abrir.ID, (payload, context) -> context.server().execute(() ->
                context.player().openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId, inv, p) -> new RopaScreenHandler(syncId, inv),
                        Text.translatable("modamod.ropa.titulo")))));
    }
}
