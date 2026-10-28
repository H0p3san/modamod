package com.modamod.render;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Cómo se reparte un patrón además de su ángulo (2026-09-30, "me gustaria q
 * los patrones si se puedan girar y que haya un slider vertical y horizontal
 * para ponerlos mas juntos"):
 * <ul>
 *   <li>{@code giroMotivo}: cada motivo gira en su lugar (grados). El ángulo
 *       de la capa ({@code CapaPatron#angulo}) gira la grilla entera — a
 *       pedido, "los dos, por separado";</li>
 *   <li>{@code distanciaH}/{@code distanciaV}: separación horizontal/vertical
 *       como factor de la de siempre (1 = como antes; 0.5 = pegados o
 *       apenas superpuestos; 3 = el triple). En las rayas es la distancia
 *       entre rayas, mezclando las dos según hacia dónde corran.</li>
 *   <li>{@code espejo}: cada motivo dado vuelta (izquierda↔derecha,
 *       arriba↔abajo o los dos) — 2026-09-30, "vamos con todo";</li>
 *   <li>{@code alternancia}: fila, columna o casillero de por medio, el
 *       motivo sale espejado (con Giro arma un zigzag tipo tejido);</li>
 *   <li>{@code simetria}: en el torso, la mitad izquierda es el reflejo de
 *       la derecha, espejada en el centro del frente y de la espalda (las
 *       rayas en diagonal quedan en V). Brazos y piernas ya se espejan solos.</li>
 * </ul>
 * Va aparte del {@code CapaPatron} porque su codec ya tiene los 16 campos
 * que admite {@code RecordCodecBuilder}.
 */
public record DistribucionPatron(float giroMotivo, float distanciaH, float distanciaV,
                                 Espejo espejo, Alternancia alternancia, boolean simetria,
                                 GiroAzar giroAzar, float posX) {

    public static final float MINIMO = 0.5f, MAXIMO = 3.0f, PASO = 0.05f;
    public static final DistribucionPatron DEFECTO =
            new DistribucionPatron(0f, 1f, 1f, Espejo.NINGUNO, Alternancia.NINGUNA, false, GiroAzar.NINGUNO, 0.5f);

    public DistribucionPatron(float giroMotivo, float distanciaH, float distanciaV) {
        this(giroMotivo, distanciaH, distanciaV, Espejo.NINGUNO, Alternancia.NINGUNA, false, GiroAzar.NINGUNO, 0.5f);
    }

    public DistribucionPatron(float giroMotivo, float distanciaH, float distanciaV,
                              Espejo espejo, Alternancia alternancia, boolean simetria) {
        this(giroMotivo, distanciaH, distanciaV, espejo, alternancia, simetria, GiroAzar.NINGUNO, 0.5f);
    }

    public DistribucionPatron(float giroMotivo, float distanciaH, float distanciaV,
                              Espejo espejo, Alternancia alternancia, boolean simetria, GiroAzar giroAzar) {
        this(giroMotivo, distanciaH, distanciaV, espejo, alternancia, simetria, giroAzar, 0.5f);
    }

    /**
     * Giro al azar de cada motivo (2026-10-12, "una rotacion random cuando
     * hay varios patrones"): el ángulo sale de la semilla (botón Azar) y de la
     * celda, así es estable y se guarda con el diseño. Se suma al Giro fijo.
     * Se guarda por nombre; agregar valores al final.
     */
    public enum GiroAzar implements net.minecraft.util.StringIdentifiable {
        NINGUNO(0), CUARTOS(90), OCTAVOS(45), LIBRE(360);

        /** Paso en grados de los modos discretos; en LIBRE, 360 = cualquier ángulo. */
        public final int grados;

        GiroAzar(int grados) { this.grados = grados; }

        /** Ángulo en radianes para un número al azar {@code r} en [0,1). */
        public double angulo(double r) {
            return switch (this) {
                case NINGUNO -> 0;
                case CUARTOS -> Math.toRadians(Math.floor(r * 4) * 90);
                case OCTAVOS -> Math.toRadians(r < 0.5 ? -45 : 45);
                case LIBRE -> Math.toRadians(r * 360);
            };
        }

        public GiroAzar siguiente() { return values()[(ordinal() + 1) % values().length]; }
        public String traduccion() { return "modamod.tinturas.giro_azar." + asString(); }
        @Override public String asString() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** Cómo se da vuelta cada motivo. Se guarda por nombre. */
    public enum Espejo implements net.minecraft.util.StringIdentifiable {
        NINGUNO(false, false), HORIZONTAL(true, false), VERTICAL(false, true), AMBOS(true, true);

        public final boolean horizontal, vertical;

        Espejo(boolean horizontal, boolean vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }

        public Espejo siguiente() { return values()[(ordinal() + 1) % values().length]; }
        public String traduccion() { return "modamod.tinturas.espejo." + asString(); }
        @Override public String asString() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    /** Qué motivos salen espejados de por medio. Se guarda por nombre. */
    public enum Alternancia implements net.minecraft.util.StringIdentifiable {
        NINGUNA, FILAS, COLUMNAS, DAMERO;

        /** ¿La celda (col, fila) sale espejada? */
        public boolean espeja(int col, int fila) {
            return switch (this) {
                case NINGUNA -> false;
                case FILAS -> Math.floorMod(fila, 2) == 1;
                case COLUMNAS -> Math.floorMod(col, 2) == 1;
                case DAMERO -> Math.floorMod(col + fila, 2) == 1;
            };
        }

        public Alternancia siguiente() { return values()[(ordinal() + 1) % values().length]; }
        public String traduccion() { return "modamod.tinturas.alternancia." + asString(); }
        @Override public String asString() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public static final Codec<DistribucionPatron> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("giro_motivo", 0f).forGetter(DistribucionPatron::giroMotivo),
            Codec.FLOAT.optionalFieldOf("distancia_h", 1f).forGetter(DistribucionPatron::distanciaH),
            Codec.FLOAT.optionalFieldOf("distancia_v", 1f).forGetter(DistribucionPatron::distanciaV),
            net.minecraft.util.StringIdentifiable.createCodec(Espejo::values)
                    .optionalFieldOf("espejo", Espejo.NINGUNO).forGetter(DistribucionPatron::espejo),
            net.minecraft.util.StringIdentifiable.createCodec(Alternancia::values)
                    .optionalFieldOf("alternancia", Alternancia.NINGUNA).forGetter(DistribucionPatron::alternancia),
            Codec.BOOL.optionalFieldOf("simetria", false).forGetter(DistribucionPatron::simetria),
            net.minecraft.util.StringIdentifiable.createCodec(GiroAzar::values)
                    .optionalFieldOf("giro_azar", GiroAzar.NINGUNO).forGetter(DistribucionPatron::giroAzar),
            Codec.FLOAT.optionalFieldOf("pos_x", 0.5f).forGetter(DistribucionPatron::posX)
    ).apply(i, DistribucionPatron::new));

    /** Cantidad de escalones de un slider de distancia (0.5..3.0 de a 0.05). */
    public static final int NIVELES = Math.round((MAXIMO - MINIMO) / PASO) + 1;

    public static float distanciaDeNivel(int nivel) {
        return MINIMO + Math.max(0, Math.min(NIVELES - 1, nivel)) * PASO;
    }

    public static int nivelDeDistancia(float distancia) {
        return Math.max(0, Math.min(NIVELES - 1, Math.round((distancia - MINIMO) / PASO)));
    }

    /** Clave estable para caches (redondeada a los escalones). */
    public String clave() {
        return Math.round(giroMotivo) + "/" + nivelDeDistancia(distanciaH) + "/" + nivelDeDistancia(distanciaV)
                + "/" + espejo.ordinal() + "/" + alternancia.ordinal() + "/" + (simetria ? 1 : 0) + "/" + giroAzar.ordinal() + "/" + Math.round(posX * 10);
    }
}
