package com.modamod.item;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StringIdentifiable;

/**
 * Corte de red/fishnet — a pedido (2026-09-20): agujerea la tela ya
 * compuesta en un enrejado diagonal, dejando ver la piel de abajo pixel por
 * pixel, con un refuerzo sólido automático en los bordes (cuello, mangas,
 * puños, dobladillo — ver {@code ClothingTextureCache#perforarRed}).
 *
 * A diferencia de los demás ejes de Modelado, NO cambia geometría ni filas
 * visibles: perfora la textura YA compuesta (color+patrón+recorte), en el
 * {@code Encima} de cada pieza en {@code PiezasDelMod}. Transversal a las 4
 * prendas con corte, mismo criterio que {@link Calce}.
 */
public enum PatronRed implements StringIdentifiable {
    /** Malla chica y apretada, hilo fino. */
    FINA("fina", 4, 1, Dibujo.ROMBO),
    /** Malla grande, con espacios bien visibles entre hilo e hilo — a pedido
     *  (2026-09-20, "uno mas grande que se vea bien los espacios"). */
    GRUESA("gruesa", 9, 2, Dibujo.ROMBO),
    /** Sin malla: NO se guarda en la prenda, borra la red que tuviera (molde "textura lisa", 2026-09-26). */
    LISA("lisa", 0, 0, Dibujo.ROMBO),
    // Al final a propósito: el ordinal viaja por red y el nombre se guarda
    // en la prenda — agregar en el medio corría los valores de los viejos.
    /** Panal de abejas: celdas hexagonales (2026-09-28, "media red hexagonal"). {@code celda} = ancho de cada hexágono. */
    HEXAGONAL("hexagonal", 16, 2, Dibujo.HEXAGONO),
    /** Tela sólida con agujeritos redondos en tresbolillo, tipo broderie/ojalillos (2026-09-28). {@code hilo} = radio del agujero. */
    PERFORADA("perforada", 10, 3, Dibujo.AGUJEROS),
    /** Rombos grandes con un puntito sólido en el medio de cada uno (2026-09-28). */
    ENCAJE("encaje", 16, 2, Dibujo.ENCAJE),
    /** Franjas horizontales abiertas cortadas por puentes alternados, tipo tejido calado (2026-09-28). {@code hilo} = alto de cada franja abierta. */
    RAYAS("rayas", 6, 2, Dibujo.RAYAS),
    /** Cuadrícula recta (no diagonal), estilo escocés (2026-09-28). */
    ESCOCESA("escocesa", 8, 2, Dibujo.CUADRICULA),
    // Arneses (2026-09-28): todo agujero salvo tiras que siguen los bordes
    // de corte + anillos plateados — ver ClothingTextureCache#perforarArnes.
    // celda/hilo no se usan: la geometría sale del tramo visible de cada cara.
    /** Dos tiras cruzadas en X por cara, anillo en el cruce. */
    ARNES_X("arnes_x", 0, 0, Dibujo.ARNES_X),
    /** Dos tirantes verticales + banda horizontal al medio, anillos en los cruces. */
    ARNES_TIRANTES("arnes_tirantes", 0, 0, Dibujo.ARNES_TIRANTES),
    /** Solo bandas horizontales (a los tercios) + los bordes, un anillo por banda. */
    ARNES_BANDAS("arnes_bandas", 0, 0, Dibujo.ARNES_BANDAS),
    // Materiales (2026-10-08, "un solo molde así como hay tramas que haya materiales"): no agujerean, le dan trama a
    // la tela — ver render/MaterialTela.
    /** Denim: sarga diagonal con desgaste. */
    DENIM("denim", 0, 0, Dibujo.MATERIAL),
    /** Cuero: grano de poros con brillo especular pintado. */
    CUERO("cuero", 0, 0, Dibujo.MATERIAL),
    /** Pana (2026-10-22, "sigamos"): canaletas verticales en relieve, como el corderoy. */
    PANA("pana", 0, 0, Dibujo.MATERIAL);

    /** Qué forma tiene el enrejado — ver {@code ClothingTextureCache#esHilo}. */
    public enum Dibujo { ROMBO, HEXAGONO, AGUJEROS, ENCAJE, RAYAS, CUADRICULA, ARNES_X, ARNES_TIRANTES, ARNES_BANDAS, MATERIAL }

    /** ¿Es un material (denim, cuero) en vez de un enrejado? Le da trama a la tela en lugar de perforarla. */
    public boolean esMaterial() { return dibujo == Dibujo.MATERIAL; }

    public boolean esArnes() {
        return dibujo == Dibujo.ARNES_X || dibujo == Dibujo.ARNES_TIRANTES || dibujo == Dibujo.ARNES_BANDAS;
    }

    public final String clave;
    /** Lado de cada celda de la malla, en pixeles — más grande = agujeros más grandes. */
    public final int celda;
    /** Ancho del hilo (la parte sólida entre agujeros), en pixeles. En AGUJEROS, el radio de cada agujero. */
    public final int hilo;
    public final Dibujo dibujo;

    PatronRed(String clave, int celda, int hilo, Dibujo dibujo) {
        this.clave = clave;
        this.celda = celda;
        this.hilo = hilo;
        this.dibujo = dibujo;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.patronred." + clave;
    }

    public static PatronRed leer(ItemStack stack) {
        return stack.get(ModamodComponents.PATRON_RED);
    }

    public static void escribir(ItemStack stack, PatronRed valor) {
        if (valor == null || valor == LISA) stack.remove(ModamodComponents.PATRON_RED);
        else stack.set(ModamodComponents.PATRON_RED, valor);
    }
}
