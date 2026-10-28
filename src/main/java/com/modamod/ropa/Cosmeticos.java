package com.modamod.ropa;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Armadura cosmética (2026-10-05, "para no tener que instalar el cosmetic armor directamente meter aqui esa
 * funcionalidad... lo visual en el mod de ropa y los stats de la gui normal"): por cada pieza (casco, pechera,
 * pantalón, botas) un ítem que SOLO se ve (los stats siguen saliendo de la armadura real) y dos interruptores:
 * <b>ver cosmético</b> y <b>ocultar</b> ("si ver cosmético no tiene nada cuenta como ocultar"). Vive como dato
 * adjunto al jugador, sincronizado a todos los que lo ven, y persiste con él (también al morir).
 *
 * <p>El render lo lee con {@link #visual}: {@code LivingEntity#getEquippedStack} devuelve esto MIENTRAS se dibuja
 * esa entidad, así lo ven también las armaduras y capas de otros mods.
 */
public record Cosmeticos(List<ItemStack> piezas, int ocultar, int ver) {

    /** Las piezas en el orden de los slots y de los bits de {@link #ocultar} / {@link #ver}. */
    public static final EquipmentSlot[] ZONAS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static final Cosmeticos VACIO = new Cosmeticos(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), 0, 0);

    public static final Codec<Cosmeticos> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("piezas").forGetter(Cosmeticos::piezas),
            Codec.INT.optionalFieldOf("ocultar", 0).forGetter(Cosmeticos::ocultar),
            Codec.INT.optionalFieldOf("ver", 0).forGetter(Cosmeticos::ver)
    ).apply(i, Cosmeticos::new));

    public static final PacketCodec<RegistryByteBuf, Cosmeticos> PACKET_CODEC = PacketCodec.tuple(
            ItemStack.OPTIONAL_LIST_PACKET_CODEC, Cosmeticos::piezas,
            PacketCodecs.VAR_INT, Cosmeticos::ocultar,
            PacketCodecs.VAR_INT, Cosmeticos::ver,
            Cosmeticos::new);

    public static final AttachmentType<Cosmeticos> TIPO = AttachmentRegistry.create(
            Identifier.of("modamod", "cosmeticos"),
            b -> b.initializer(() -> VACIO)
                    .persistent(CODEC)
                    .syncWith(PACKET_CODEC, AttachmentSyncPredicate.all())
                    .copyOnDeath());

    /** Fuerza el registro del dato adjunto (se llama al iniciar el mod). */
    public static void init() {}

    public static Cosmeticos de(PlayerEntity jugador) {
        Cosmeticos c = jugador.getAttachedOrCreate(TIPO);
        return c.piezas().size() == ZONAS.length ? c : VACIO;
    }

    public static void guardar(PlayerEntity jugador, Cosmeticos c) {
        jugador.setAttached(TIPO, c);
    }

    public ItemStack pieza(int zona) {
        return zona < piezas.size() ? piezas.get(zona) : ItemStack.EMPTY;
    }

    public boolean oculta(int zona) { return (ocultar >> zona & 1) != 0; }

    public boolean veCosmetico(int zona) { return (ver >> zona & 1) != 0; }

    public Cosmeticos conPieza(int zona, ItemStack stack) {
        List<ItemStack> nuevas = new ArrayList<>(piezas);
        while (nuevas.size() < ZONAS.length) nuevas.add(ItemStack.EMPTY);
        nuevas.set(zona, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        return new Cosmeticos(List.copyOf(nuevas), ocultar, ver);
    }

    public Cosmeticos alternarOcultar(int zona) {
        return new Cosmeticos(piezas, ocultar ^ (1 << zona), ver);
    }

    public Cosmeticos alternarVer(int zona) {
        return new Cosmeticos(piezas, ocultar, ver ^ (1 << zona));
    }

    public static int zonaDe(EquipmentSlot slot) {
        for (int i = 0; i < ZONAS.length; i++) if (ZONAS[i] == slot) return i;
        return -1;
    }

    /**
     * Lo que se DIBUJA en esa pieza, o {@code null} si no cambia nada (se ve la armadura real): oculta → nada;
     * ver cosmético → el cosmético (vacío = nada).
     */
    @Nullable
    public static ItemStack visual(PlayerEntity jugador, EquipmentSlot slot) {
        int z = zonaDe(slot);
        if (z < 0) return null;
        Cosmeticos c = de(jugador);
        if (c.oculta(z)) return ItemStack.EMPTY;
        if (c.veCosmetico(z)) return c.pieza(z);
        return null;
    }
}
