package com.modamod.item;

import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

/**
 * Remate de un borde libre de una prenda (2026-10-07, "un ruedo ajustado que podria servir para ruedo de remera mangas
 * botamangas y hasta polleras" + "fusionemos borde pollera con ruedo asi tenemos mas opciones"): un solo molde y un solo
 * pin por zona ({@link ZonaRuedo}). Reemplaza al remate de la chaqueta ({@code ChaquetaRemate}) y al borde de la
 * pollera (el molde de borde). Por ordinal: nuevos al final.
 *
 * <p>RECTO, AJUSTADO y CAMPANA valen en cualquier zona de tela; ONDULADO, FESTONEADO y PICO cambian el largo a lo largo
 * de la vuelta y hoy solo los dibuja la pollera ({@link PolleraBorde}).
 */
public enum Ruedo implements StringIdentifiable {
    RECTO("recto"),
    /** Elástico: la última fila aprieta hacia el cuerpo y la tela no cuelga (puños y ruedo del hoodie). */
    AJUSTADO("ajustado"),
    /** La tela se abre hacia afuera en las últimas filas. */
    CAMPANA("campana"),
    ONDULADO("ondulado"),
    FESTONEADO("festoneado"),
    PICO("pico"),
    /** Volado de malla en un puño, botamanga o borde de calentador/media (2026-10-11); ver {@code render.VoladoMalla}. */
    VOLADO_RECTO("volado_recto"), VOLADO_CIRCULAR("volado_circular"),
    /**
     * Esquinas redondeadas y cola (2026-10-12, "los ruedos de todo los podemos unificar con moldes de ruedo"): los dos
     * remates de la capa (y las esquinas del delantal) pasan a ser moldes de ruedo. Solo valen en el ruedo de la capa
     * y en las esquinas del delantal; no se guardan en {@link Ruedos}, van a {@code CapaRuedo}.
     */
    REDONDEADO("redondeado"), COLA("cola"),
    /**
     * Tela fruncida (2026-10-17, "quiero que se bunchee asi la tela de las medias y los armwarmers" → "que sea con un
     * molde"): las filas de ese borde alternan hacia afuera y hacia adentro, como un acordeón. Solo medias y calientabrazos.
     * Al final: viaja por red por ordinal.
     */
    FRUNCIDO("fruncido");

    public final String clave;

    Ruedo(String clave) { this.clave = clave; }

    @Override
    public String asString() { return clave; }

    public String traduccion() { return "modamod.ruedo." + clave; }

    /** El borde decorativo de la pollera que corresponde, o null si no cambia el largo de la vuelta. */
    @Nullable
    public PolleraBorde borde() {
        return switch (this) {
            case ONDULADO -> PolleraBorde.ONDULADO;
            case FESTONEADO -> PolleraBorde.FESTONEADO;
            case PICO -> PolleraBorde.PICO;
            default -> null;
        };
    }

    /** El volado de malla que corresponde, o null. */
    @Nullable
    public PolleraVolado volado() {
        return switch (this) {
            case VOLADO_RECTO -> PolleraVolado.RECTO;
            case VOLADO_CIRCULAR -> PolleraVolado.CIRCULAR;
            default -> null;
        };
    }

    /** Los remates que son de la capa (y de las esquinas del delantal): cambian la forma o el largo del paño. */
    public boolean esDeCapa() { return this == REDONDEADO || this == COLA; }

    /** Si vale para una zona que no es la pollera (el borde decorativo hoy solo existe ahí). */
    public boolean valeEnTela() { return borde() == null && !esDeCapa(); }
}
