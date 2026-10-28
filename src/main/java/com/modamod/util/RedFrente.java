package com.modamod.util;

import com.modamod.Modamod;
import com.modamod.item.TopCorte;
import com.modamod.sublimadora.RemeraItem;
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
 * Abrir y cerrar la chaqueta con una tecla (2026-10-11, "quiero que agreguemos un keybind para abrir y cerrar la
 * chaqueta"). Igual que la capucha ({@link RedCapucha}): el cliente solo pide, el servidor cambia
 * {@code modamod:frente_ahora} en los Tops equipados y Trinkets lo sincroniza a todos.
 *
 * <p>Alterna TODOS los Tops de chaqueta puestos (con capucha, solapas, frente abierto, o los del slot
 * {@code torso/chaqueta}): si alguno estaba abierto, se cierran todos; si no, se abren todos.
 */
public final class RedFrente {

    private RedFrente() {}

    public record Alternar() implements CustomPayload {
        public static final Id<Alternar> ID = new Id<>(Identifier.of(Modamod.MOD_ID, "frente_alternar"));
        public static final PacketCodec<RegistryByteBuf, Alternar> CODEC = PacketCodec.unit(new Alternar());

        @Override
        public Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void init() {
        PayloadTypeRegistry.playC2S().register(Alternar.ID, Alternar.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Alternar.ID, (payload, context) ->
                context.server().execute(() -> alternar(context.player())));
    }

    private static boolean esChaqueta(SlotReference ref, ItemStack stack) {
        // La capa con embozo (2026-10-11, "abra un poco con la tecla de abrir el frente") también obedece a la tecla.
        if (stack.getItem() instanceof com.modamod.item.CapaItem) return com.modamod.item.CapaItem.embozo(stack) >= 0;
        if (!(stack.getItem() instanceof RemeraItem)) return false;
        return TopCorte.conRasgosDeChaqueta(stack) || "chaqueta".equals(ref.inventory().getSlotType().getName());
    }

    private static boolean abierta(ItemStack s) {
        return s.getItem() instanceof com.modamod.item.CapaItem ? com.modamod.item.CapaItem.embozoAbierto(s) : TopCorte.frente(s).abre();
    }

    private static void alternar(ServerPlayerEntity jugador) {
        TrinketsApi.getTrinketComponent(jugador).ifPresent(componente -> {
            List<Pair<SlotReference, ItemStack>> chaquetas = componente.getAllEquipped().stream()
                    .filter(p -> esChaqueta(p.getLeft(), p.getRight())).toList();
            if (chaquetas.isEmpty()) return;
            boolean algunaAbierta = chaquetas.stream().anyMatch(p -> abierta(p.getRight()));
            for (Pair<SlotReference, ItemStack> par : chaquetas) {
                ItemStack copia = par.getRight().copy();
                if (copia.getItem() instanceof com.modamod.item.CapaItem) copia.set(com.modamod.item.ModamodComponents.FRENTE_AHORA, !algunaAbierta);
                else TopCorte.setFrenteAbierto(copia, !algunaAbierta);
                // setStack (y no cambiar el stack en el lugar) para que Trinkets lo sincronice.
                par.getLeft().inventory().setStack(par.getLeft().index(), copia);
            }
        });
    }
}
