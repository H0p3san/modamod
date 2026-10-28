package com.modamod.util;

import com.modamod.Modamod;
import com.modamod.item.CapaItem;
import com.modamod.item.TopCorte;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

import java.util.List;

/**
 * Subir/bajar la capucha con una tecla (2026-09-30, hoodie "con tecla para
 * subir/bajar" / "lo mismo con la capucha de la capa activada por tecla").
 * El cliente solo pide ({@link Alternar}); el servidor cambia
 * {@code modamod:capucha_arriba} en los stacks equipados y Trinkets los
 * sincroniza a todos los que te ven — no hace falta un paquete de vuelta.
 *
 * <p>Alterna TODAS las capuchas puestas a la vez (hoodies y la capa si tiene
 * capucha): si alguna estaba arriba, bajan todas; si no, suben todas.
 */
public final class RedCapucha {

    private RedCapucha() {}

    public record Alternar() implements CustomPayload {
        public static final Id<Alternar> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "capucha_alternar"));
        public static final PacketCodec<RegistryByteBuf, Alternar> CODEC = PacketCodec.unit(new Alternar());

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(Alternar.ID, Alternar.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Alternar.ID, (payload, context) ->
                context.server().execute(() -> alternar(context.player())));
    }

    private static boolean tieneCapucha(ItemStack stack) {
        return TopCorte.tieneCapucha(stack)
                || (stack.getItem() instanceof CapaItem && CapaItem.capucha(stack));
    }

    private static void alternar(ServerPlayerEntity jugador) {
        TrinketsApi.getTrinketComponent(jugador).ifPresent(componente -> {
            List<Pair<SlotReference, ItemStack>> conCapucha =
                    componente.getEquipped(RedCapucha::tieneCapucha);
            if (conCapucha.isEmpty()) return;
            boolean algunaArriba = conCapucha.stream().anyMatch(p -> TopCorte.capuchaArriba(p.getRight()));
            for (Pair<SlotReference, ItemStack> par : conCapucha) {
                ItemStack copia = par.getRight().copy();
                TopCorte.setCapuchaArriba(copia, !algunaArriba);
                // setStack (y no cambiar el stack en el lugar) para que Trinkets lo sincronice.
                par.getLeft().inventory().setStack(par.getLeft().index(), copia);
            }
        });
    }
}
