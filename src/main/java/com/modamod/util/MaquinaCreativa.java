package com.modamod.util;

import com.modamod.Modamod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Máquinas creativas (2026-10-01, "saquemos los debug de instantaneo y gratis
 * y directamente hagamos 3 maquinas alternativas que tengan velocidad de
 * espera 0 y requisitos de materiales 0 y tengan adentro todos los patrones de
 * cada maquina, distinguibles de alguna manera de las maquinas normales" + la
 * Mesa de estilado): misma clase de bloque y de block entity que la normal,
 * otro bloque registrado. Acá se anota cuáles son, y las máquinas preguntan
 * con {@link #es} para no esperar, no gastar insumos y venir cargadas al
 * colocarlas. Se ven con la textura recoloreada ({@link #textura}, de
 * {@code tools/generar_texturas_creativas.py}).
 */
public final class MaquinaCreativa {

    private MaquinaCreativa() {}

    private static final Set<Block> BLOQUES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Marca {@code bloque} como versión creativa y lo devuelve. */
    public static <B extends Block> B creativa(B bloque) {
        BLOQUES.add(bloque);
        return bloque;
    }

    public static boolean es(Block bloque) {
        return BLOQUES.contains(bloque);
    }

    public static boolean es(BlockState estado) {
        return estado != null && es(estado.getBlock());
    }

    public static boolean es(BlockEntity be) {
        return be != null && es(be.getCachedState());
    }

    public static boolean es(Item item) {
        return item instanceof BlockItem bi && es(bi.getBlock());
    }

    /** Lo que trae adentro una máquina creativa al colocarla. */
    public interface Cargable {
        void cargarCreativa();
    }

    /**
     * Al colocar una creativa nueva, la carga ("cargados una vez"). Si el
     * ítem trae el contenido de una máquina rota ({@code block_entity_data}),
     * no: ya viene con lo suyo.
     */
    public static void alColocar(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
                                 BlockState estado, net.minecraft.item.ItemStack stack) {
        if (world.isClient || !es(estado) || stack.contains(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA)) return;
        if (world.getBlockEntity(pos) instanceof Cargable c) c.cargarCreativa();
    }

    /** Lo que tarda de verdad un proceso: 1 tick en una creativa. */
    public static int duracion(BlockEntity be, int ticks) {
        return es(be) ? 1 : ticks;
    }

    /** La textura recoloreada de la versión creativa: {@code x.png} → {@code x_creativa.png}. */
    public static Identifier textura(Identifier normal) {
        String p = normal.getPath();
        return Identifier.of(Modamod.MOD_ID, p.substring(0, p.length() - 4) + "_creativa.png");
    }

    /** La textura que toca según sea creativa o no. */
    public static Identifier textura(Identifier normal, boolean creativa) {
        return creativa ? textura(normal) : normal;
    }
}
