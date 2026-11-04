package com.modamod.ropa;

import net.minecraft.screen.slot.Slot;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * La grilla de Ropa que comparten la pantalla de Ropa, el Guardarropas y el Maniquí (rediseño, tanda 3): los
 * {@link ListaDeRopa#LUGARES} lugares en {@link ListaDeRopa#COLUMNAS} columnas, de afuera (arriba a la izquierda) hacia
 * adentro, y la fila de la armadura metida entre dos filas, que se mueve con las flechas de su izquierda. Cada pantalla pone
 * sus propios slots (de Trinkets, del maniquí); acá van las posiciones y cuándo se ve cada uno.
 */
public final class GrillaDeRopa {

    private GrillaDeRopa() {}

    public static final int PASO_FILA = 20, PASO_COLUMNA = 22;
    /** Ancho de la grilla y lo que quedan las flechas a su izquierda (px). */
    public static final int ANCHO = ListaDeRopa.COLUMNAS * PASO_COLUMNA, FLECHAS = 14;
    /** Los botones de las flechas: la fila de la armadura una más arriba o más abajo. */
    public static final int BTN_FILA_ARRIBA = 50, BTN_FILA_ABAJO = 51;

    /** La silueta de vanilla de cada pieza de armadura vacía (casco, pechera, pantalón, botas). */
    public static final net.minecraft.util.Identifier[] FONDO_ARMADURA = {
            net.minecraft.screen.PlayerScreenHandler.EMPTY_HELMET_SLOT_TEXTURE, net.minecraft.screen.PlayerScreenHandler.EMPTY_CHESTPLATE_SLOT_TEXTURE,
            net.minecraft.screen.PlayerScreenHandler.EMPTY_LEGGINGS_SLOT_TEXTURE, net.minecraft.screen.PlayerScreenHandler.EMPTY_BOOTS_SLOT_TEXTURE};

    /** Crea el slot del lugar (o de la pieza de armadura) {@code indice} en {@code x, y}, que solo se ve cuando {@code visible}. */
    public interface Fabrica {
        Slot crear(int indice, int x, int y, BooleanSupplier visible);
    }

    /**
     * Agrega los slots de la grilla con esquina en {@code x0, y0}: cada lugar en sus dos alturas posibles (la suya, o una más
     * abajo si la fila de la armadura queda arriba de él) y cada pieza de armadura en las cinco alturas de su fila; se ve el
     * de la altura que toca con la {@code fila} de ahora. Primero los lugares y después la armadura.
     */
    public static void armar(int x0, int y0, IntSupplier fila, Fabrica lugar, Fabrica armadura, Consumer<Slot> agregar) {
        for (int i = 0; i < ListaDeRopa.LUGARES; i++) {
            for (int bajada = 0; bajada < 2; bajada++) {
                int indice = i, alto = i / ListaDeRopa.COLUMNAS + bajada;
                agregar.accept(lugar.crear(i, x0 + (i % ListaDeRopa.COLUMNAS) * PASO_COLUMNA, y0 + alto * PASO_FILA,
                        () -> ListaDeRopa.filaEnPantalla(indice, fila.getAsInt()) == alto));
            }
        }
        for (int f = 0; f <= Cosmeticos.FILAS; f++) {
            for (int z = 0; z < Cosmeticos.ZONAS.length; z++) {
                int estaFila = f;
                agregar.accept(armadura.crear(z, x0 + z * PASO_COLUMNA, y0 + f * PASO_FILA, () -> fila.getAsInt() == estaFila));
            }
        }
    }

    /** La fila de la armadura después de un click en sus flechas, o -1 si {@code boton} no es una flecha. */
    public static int filaTrasBoton(int fila, int boton) {
        if (boton == BTN_FILA_ARRIBA) return Math.max(0, fila - 1);
        if (boton == BTN_FILA_ABAJO) return Math.min(Cosmeticos.FILAS, fila + 1);
        return -1;
    }
}
