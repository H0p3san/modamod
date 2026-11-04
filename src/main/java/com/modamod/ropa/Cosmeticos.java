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
 * pantalón, botas) un ítem que SOLO se ve (los stats siguen saliendo de la armadura real), puesto en la fila de la
 * armadura de la lista de ropa, y un interruptor <b>ocultar</b> que esconde la real (2026-11-04, "las que esten aqui
 * seran cosmeticas y si se pone hide se esconde la real"). Vive como dato
 * adjunto al jugador, sincronizado a todos los que lo ven, y persiste con él (también al morir).
 *
 * <p>El render lo lee con {@link #visual}: {@code LivingEntity#getEquippedStack} devuelve esto MIENTRAS se dibuja
 * esa entidad, así lo ven también las armaduras y capas de otros mods.
 */
public record Cosmeticos(List<ItemStack> piezas, int ocultar, int fila) {

    /** Las piezas en el orden de los slots y de los bits de {@link #ocultar}. */
    public static final EquipmentSlot[] ZONAS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /**
     * Dónde va la fila de la armadura en la grilla de Ropa (rediseño, tanda 3): 0 = arriba de todo (la armadura encima de
     * toda la ropa) hasta {@link #FILAS} = abajo de todo. Lo de las filas de arriba se dibuja encima de la armadura.
     */
    public static final int FILAS = 4, FILA_DE_FABRICA = 1;

    public static final Cosmeticos VACIO = new Cosmeticos(List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY), 0,
            FILA_DE_FABRICA);

    public static final Codec<Cosmeticos> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("piezas").forGetter(Cosmeticos::piezas),
            Codec.INT.optionalFieldOf("ocultar", 0).forGetter(Cosmeticos::ocultar),
            Codec.intRange(0, FILAS).optionalFieldOf("fila", FILA_DE_FABRICA).forGetter(Cosmeticos::fila)
    ).apply(i, Cosmeticos::new));

    public static final PacketCodec<RegistryByteBuf, Cosmeticos> PACKET_CODEC = PacketCodec.tuple(
            ItemStack.OPTIONAL_LIST_PACKET_CODEC, Cosmeticos::piezas,
            PacketCodecs.VAR_INT, Cosmeticos::ocultar,
            PacketCodecs.VAR_INT, Cosmeticos::fila,
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

    public Cosmeticos conPieza(int zona, ItemStack stack) {
        List<ItemStack> nuevas = new ArrayList<>(piezas);
        while (nuevas.size() < ZONAS.length) nuevas.add(ItemStack.EMPTY);
        nuevas.set(zona, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        return new Cosmeticos(List.copyOf(nuevas), ocultar, fila);
    }

    public Cosmeticos alternarOcultar(int zona) {
        return new Cosmeticos(piezas, ocultar ^ (1 << zona), fila);
    }

    public Cosmeticos sinPiezas() {
        return new Cosmeticos(VACIO.piezas(), ocultar, fila);
    }

    public Cosmeticos conFila(int nueva) {
        return new Cosmeticos(piezas, ocultar, Math.max(0, Math.min(FILAS, nueva)));
    }

    /** La pieza de armadura que le toca a {@code stack} (armaduras, élitros, calabaza, cabezas), o -1. */
    public static int zonaDeArmadura(ItemStack stack) {
        net.minecraft.item.Equipment equipo = net.minecraft.item.Equipment.fromStack(stack);
        return equipo == null ? -1 : zonaDe(equipo.getSlotType());
    }

    public static int zonaDe(EquipmentSlot slot) {
        for (int i = 0; i < ZONAS.length; i++) if (ZONAS[i] == slot) return i;
        return -1;
    }

    /**
     * Lo que se DIBUJA en esa pieza, o {@code null} si no cambia nada (se ve la armadura real): el cosmético si hay;
     * si no, oculta → nada.
     */
    @Nullable
    public static ItemStack visual(PlayerEntity jugador, EquipmentSlot slot) {
        int z = zonaDe(slot);
        if (z < 0) return null;
        Cosmeticos c = de(jugador);
        if (!c.pieza(z).isEmpty()) return c.pieza(z);
        return c.oculta(z) ? ItemStack.EMPTY : null;
    }
}
