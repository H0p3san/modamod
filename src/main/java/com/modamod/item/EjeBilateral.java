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
 * Igual que {@link com.modamod.region.RegionResolver#fijarDerecha}, tocar
 * la IZQUIERDA clava primero el valor actual en el override de la derecha
 * (si no tenía uno ya) — sin esto, cambiar un lado cambiaría "gratis" el
 * otro en cuanto no tuviera override propio, el mismo bug que ya se
 * encontró y arregló para color.
 */
public final class EjeBilateral {

    public static <E> E leer(ItemStack stack, Lado lado, ComponentType<E> primario,
                              ComponentType<E> override, E porDefecto) {
        if (lado == Lado.DERECHA) {
            E o = stack.get(override);
            if (o != null) return o;
        }
        E v = stack.get(primario);
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
    public static <E> boolean presente(ItemStack stack, Lado lado, ComponentType<E> primario, ComponentType<E> override) {
        if (lado == Lado.DERECHA && stack.get(override) != null) return true;
        return stack.get(primario) != null;
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

    public static <E> void escribir(ItemStack stack, Lado lado, ComponentType<E> primario,
                                     ComponentType<E> override, E porDefecto, E valor) {
        switch (lado) {
            case AMBAS -> {
                if (valor.equals(porDefecto)) stack.remove(primario); else stack.set(primario, valor);
                stack.remove(override);
            }
            case DERECHA -> {
                E izq = leer(stack, Lado.IZQUIERDA, primario, override, porDefecto);
                if (valor.equals(izq)) stack.remove(override); else stack.set(override, valor);
            }
            case IZQUIERDA -> {
                if (stack.get(override) == null) {
                    E actual = leer(stack, Lado.IZQUIERDA, primario, override, porDefecto);
                    stack.set(override, actual);
                }
                if (valor.equals(porDefecto)) stack.remove(primario); else stack.set(primario, valor);
            }
        }
    }

    private EjeBilateral() {}
}
