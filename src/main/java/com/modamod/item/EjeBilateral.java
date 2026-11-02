package com.modamod.item;

import com.modamod.region.Lado;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;

/**
 * Lectura/escritura izquierda-primaria / derecha-override para un eje
 * cualquiera de un solo valor — mismo criterio que ya usa
 * {@link com.modamod.region.RegionResolver} para color/patrón, pero
 * genérico para no repetirlo por cada eje nuevo (cobertura de extremidad
 * ahora tiene 6: superior/inferior × pantalón/medias/calientabrazos).
 *
 * IZQUIERDA es la primaria: vive en {@code primario}. DERECHA guarda un
 * override opcional en {@code override}; ausente = hereda de la izquierda.
 * Tocar la IZQUIERDA clava primero el valor actual en el override de la derecha
 * (si no tenía uno ya) — sin esto, cambiar un lado cambiaría "gratis" el
 * otro en cuanto no tuviera override propio.
 */
public final class EjeBilateral {

    public static <E> E leer(ItemStack stack, Lado lado, com.modamod.prenda.EjeDeCorte<E> primario,
                              com.modamod.prenda.EjeDeCorte<E> override, E porDefecto) {
        if (lado == Lado.DERECHA) {
            E o = com.modamod.prenda.Corte.get(stack, override);
            if (o != null) return o;
        }
        E v = com.modamod.prenda.Corte.get(stack, primario);
        return v == null ? porDefecto : v;
    }

    /**
     * True si este eje/lado tiene ALGÚN valor guardado (heredado o propio) —
     * false solo en el estado prístino, nunca tocado. Lo usan los ejes de
     * cobertura de extremidad para distinguir "anclaje sin usar todavía"
     * (que tiene que dejar pasar todas las filas, sea cual sea el valor
     * numérico del enum más "completo" de esa prenda — medias no tiene un
     * valor que llegue a las 12 filas, {@code CANCAN} son 10 nomás) de
     * "anclaje puesto en su valor más largo a propósito".
     */
    public static <E> boolean presente(ItemStack stack, Lado lado, com.modamod.prenda.EjeDeCorte<E> primario, com.modamod.prenda.EjeDeCorte<E> override) {
        if (lado == Lado.DERECHA && com.modamod.prenda.Corte.get(stack, override) != null) return true;
        return com.modamod.prenda.Corte.get(stack, primario) != null;
    }

    /**
     * La intersección de dos anclajes sobre un cuboide de 12 filas: el
     * superior tapa {@code [0, filasSuperior)}, el inferior
     * {@code [12-filasInferior, 12)}. Devuelve {@code [desde, hasta)},
     * {@code hasta} exclusivo; si no se tocan, {@code desde==hasta} (sin
     * tela, hueco real).
     */
    public static int[] interseccion(int filasSuperior, int filasInferior) {
        int desde = Math.max(0, 12 - filasInferior);
        int hasta = Math.min(filasSuperior, 12);
        return new int[]{desde, Math.max(desde, hasta)};
    }

    public static <E> void escribir(ItemStack stack, Lado lado, com.modamod.prenda.EjeDeCorte<E> primario,
                                     com.modamod.prenda.EjeDeCorte<E> override, E porDefecto, E valor) {
        switch (lado) {
            case AMBAS -> {
                if (valor.equals(porDefecto)) com.modamod.prenda.Corte.remove(stack, primario); else com.modamod.prenda.Corte.set(stack, primario, valor);
                com.modamod.prenda.Corte.remove(stack, override);
            }
            case DERECHA -> {
                E izq = leer(stack, Lado.IZQUIERDA, primario, override, porDefecto);
                if (valor.equals(izq)) com.modamod.prenda.Corte.remove(stack, override); else com.modamod.prenda.Corte.set(stack, override, valor);
            }
            case IZQUIERDA -> {
                if (com.modamod.prenda.Corte.get(stack, override) == null) {
                    E actual = leer(stack, Lado.IZQUIERDA, primario, override, porDefecto);
                    com.modamod.prenda.Corte.set(stack, override, actual);
                }
                if (valor.equals(porDefecto)) com.modamod.prenda.Corte.remove(stack, primario); else com.modamod.prenda.Corte.set(stack, primario, valor);
            }
        }
    }

    private EjeBilateral() {}
}
