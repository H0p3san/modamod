package com.modamod.region;

import com.modamod.item.ModamodComponents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * La pintura de una prenda: un color base (el {@code dyed_color} de siempre) y la lista ordenada de capas de Tintes
 * ({@link ModamodComponents#CAPAS_TINTE}). Cada capa trae su región, así que izquierda y derecha, frente y espalda, o
 * cada cuadradito se pintan por separado sin componentes aparte: la región ES el lado. La primera capa pinta primero
 * y la última queda arriba.
 *
 * <p>También resuelve la {@link Orientacion} (girar o espejar la prenda): se aplica al leer, no permutando datos.
 */
public final class RegionResolver {

    private RegionResolver() {}

    // --- Orientacion ---

    public static Orientacion orientacion(ItemStack stack) {
        Orientacion o = com.modamod.prenda.Corte.get(stack, com.modamod.prenda.Ejes.ORIENTACION);
        return o == null ? Orientacion.NORMAL : o;
    }

    /** Guarda la orientación, o la borra si volvió a la normal (así apila con una prenda igual sin girar). */
    public static void ponerOrientacion(ItemStack stack, Orientacion o) {
        if (o.esNormal()) com.modamod.prenda.Corte.remove(stack, com.modamod.prenda.Ejes.ORIENTACION);
        else com.modamod.prenda.Corte.set(stack, com.modamod.prenda.Ejes.ORIENTACION, o);
    }

    /** El lado donde REALMENTE vive el dato, dado el lado que se ve. AMBAS no se toca. */
    public static Lado fisico(ItemStack stack, Lado visible) {
        return orientacion(stack).espejado() ? visible.opuesto() : visible;
    }

    /** Lo mismo para el eje frente/espalda. */
    public static Cara fisica(ItemStack stack, Cara visible) {
        return orientacion(stack).girado() ? visible.opuesta() : visible;
    }

    // --- Color base ---

    public static int colorBase(ItemStack stack) {
        if (stack.getItem() instanceof com.modamod.prenda.PrendaItem prenda) return prenda.getColor(stack);
        return 0xFFFFFF;
    }

    public static void ponerColorBase(ItemStack stack, int rgb) {
        com.modamod.prenda.PrendaItem.setColor(stack, rgb);
    }

    // --- Capas ---

    /**
     * Una capa de patrón ya resuelta: qué patrón, con qué color, tamaño
     * y orientación PROPIOS — a pedido (2026-09-19, "tendria q poder
     * variar orientacion y tamaño entre cada capa").
     */
    public record CapaPatron(@Nullable Identifier patronId, int color, com.modamod.item.TamanoPatron tamano,
                              float angulo, float posicion,
                              com.modamod.render.PatronGenerador.Forma forma, boolean invertido,
                              RegionPintura region, ModoMezcla modo, int opacidad,
                              com.modamod.render.Repeticion repeticion, int semilla,
                              java.util.List<Integer> extras, boolean contorno,
                              com.modamod.render.Variacion variacion,
                              boolean fueraDeRegion,
                              com.modamod.render.DistribucionPatron distribucion) {
        /** Sin distribución propia (giro de motivo 0, distancias de siempre) — todo lo de antes del 2026-09-30. */
        public CapaPatron(@Nullable Identifier patronId, int color, com.modamod.item.TamanoPatron tamano,
                           float angulo, float posicion,
                           com.modamod.render.PatronGenerador.Forma forma, boolean invertido,
                           RegionPintura region, ModoMezcla modo, int opacidad,
                           com.modamod.render.Repeticion repeticion, int semilla,
                           java.util.List<Integer> extras, boolean contorno,
                           com.modamod.render.Variacion variacion,
                           boolean fueraDeRegion) {
            this(patronId, color, tamano, angulo, posicion, forma, invertido, region, modo, opacidad,
                    repeticion, semilla, extras, contorno, variacion, fueraDeRegion,
                    com.modamod.render.DistribucionPatron.DEFECTO);
        }

        /**
         * Una capa normal: pinta ADENTRO de su región. {@code fueraDeRegion}
         * (Fase B, 2026-09-28, resaltar en 3D la zona con el mouse encima,
         * "resto apagado") pinta al revés, todo MENOS su región — solo lo
         * usa el velo de la vista previa de Tintes, nunca se guarda (no está
         * en el {@link #CODEC}).
         */
        public CapaPatron(@Nullable Identifier patronId, int color, com.modamod.item.TamanoPatron tamano,
                           float angulo, float posicion,
                           com.modamod.render.PatronGenerador.Forma forma, boolean invertido,
                           RegionPintura region, ModoMezcla modo, int opacidad,
                           com.modamod.render.Repeticion repeticion, int semilla,
                           java.util.List<Integer> extras, boolean contorno,
                           com.modamod.render.Variacion variacion) {
            this(patronId, color, tamano, angulo, posicion, forma, invertido, region, modo, opacidad,
                    repeticion, semilla, extras, contorno, variacion, false);
        }

        /**
         * Compatibilidad: una sola tinta (sin Color 2/3), sin contorno ni
         * variación — todas las capas de antes de la Fase 2 (2026-09-28).
         */
        public CapaPatron(Identifier patronId, int color, com.modamod.item.TamanoPatron tamano,
                           float angulo, float posicion,
                           com.modamod.render.PatronGenerador.Forma forma, boolean invertido,
                           RegionPintura region, ModoMezcla modo, int opacidad,
                           com.modamod.render.Repeticion repeticion, int semilla) {
            this(patronId, color, tamano, angulo, posicion, forma, invertido, region, modo, opacidad,
                    repeticion, semilla, java.util.List.of(), false, com.modamod.render.Variacion.FIJO);
        }

        /** Los colores activos de la capa, en orden: {@code color} (Color 1) y después los extras (Color 2, 3). */
        public int[] paleta() {
            int[] p = new int[1 + extras.size()];
            p[0] = color;
            for (int i = 0; i < extras.size(); i++) p[i + 1] = extras.get(i);
            return p;
        }

        /** Compatibilidad: sin repetición/semilla (moldes de rayas, o capas de antes de los motivos). */
        public CapaPatron(Identifier patronId, int color, com.modamod.item.TamanoPatron tamano,
                           float angulo, float posicion,
                           com.modamod.render.PatronGenerador.Forma forma, boolean invertido,
                           RegionPintura region, ModoMezcla modo, int opacidad) {
            this(patronId, color, tamano, angulo, posicion, forma, invertido, region, modo, opacidad,
                    com.modamod.render.Repeticion.GRILLA, 0);
        }

        /** Compatibilidad: sin región explícita, toda la prenda — comportamiento de siempre. */
        public CapaPatron(Identifier patronId, int color, com.modamod.item.TamanoPatron tamano,
                           float angulo, float posicion,
                           com.modamod.render.PatronGenerador.Forma forma, boolean invertido) {
            this(patronId, color, tamano, angulo, posicion, forma, invertido, RegionPintura.TODO);
        }

        /** Compatibilidad: sin modo/opacidad, tapa normal al 100% — como pintaban siempre las capas. */
        public CapaPatron(Identifier patronId, int color, com.modamod.item.TamanoPatron tamano,
                           float angulo, float posicion,
                           com.modamod.render.PatronGenerador.Forma forma, boolean invertido,
                           RegionPintura region) {
            this(patronId, color, tamano, angulo, posicion, forma, invertido, region, ModoMezcla.NORMAL, 100);
        }

        /** Sin molde = color liso en toda su región (2026-09-27, capas por cuadradito de Tinturas). */
        public boolean lisa() { return patronId == null; }

        public static final com.mojang.serialization.Codec<CapaPatron> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
                        Identifier.CODEC.optionalFieldOf("patron").forGetter(c -> java.util.Optional.ofNullable(c.patronId())),
                        com.mojang.serialization.Codec.INT.fieldOf("color").forGetter(CapaPatron::color),
                        net.minecraft.util.StringIdentifiable.createCodec(com.modamod.item.TamanoPatron::values)
                                .optionalFieldOf("tamano", com.modamod.item.TamanoPatron.GRANDE).forGetter(CapaPatron::tamano),
                        com.mojang.serialization.Codec.FLOAT.optionalFieldOf("angulo", 0f).forGetter(CapaPatron::angulo),
                        com.mojang.serialization.Codec.FLOAT.optionalFieldOf("posicion", 0.5f).forGetter(CapaPatron::posicion),
                        net.minecraft.util.StringIdentifiable.createCodec(com.modamod.render.PatronGenerador.Forma::values)
                                .optionalFieldOf("forma", com.modamod.render.PatronGenerador.Forma.ALTERNADO).forGetter(CapaPatron::forma),
                        com.mojang.serialization.Codec.BOOL.optionalFieldOf("invertido", false).forGetter(CapaPatron::invertido),
                        net.minecraft.util.StringIdentifiable.createCodec(RegionPintura::values)
                                .optionalFieldOf("region", RegionPintura.TODO).forGetter(CapaPatron::region),
                        net.minecraft.util.StringIdentifiable.createCodec(ModoMezcla::values)
                                .optionalFieldOf("modo", ModoMezcla.NORMAL).forGetter(CapaPatron::modo),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("opacidad", 100).forGetter(CapaPatron::opacidad),
                        net.minecraft.util.StringIdentifiable.createCodec(com.modamod.render.Repeticion::values)
                                .optionalFieldOf("repeticion", com.modamod.render.Repeticion.GRILLA).forGetter(CapaPatron::repeticion),
                        com.mojang.serialization.Codec.INT.optionalFieldOf("semilla", 0).forGetter(CapaPatron::semilla),
                        com.mojang.serialization.Codec.INT.listOf().optionalFieldOf("extras", java.util.List.of()).forGetter(CapaPatron::extras),
                        com.mojang.serialization.Codec.BOOL.optionalFieldOf("contorno", false).forGetter(CapaPatron::contorno),
                        net.minecraft.util.StringIdentifiable.createCodec(com.modamod.render.Variacion::values)
                                .optionalFieldOf("variacion", com.modamod.render.Variacion.FIJO).forGetter(CapaPatron::variacion),
                        com.modamod.render.DistribucionPatron.CODEC
                                .optionalFieldOf("distribucion", com.modamod.render.DistribucionPatron.DEFECTO)
                                .forGetter(CapaPatron::distribucion)
                ).apply(i, (p, color, tamano, angulo, posicion, forma, invertido, region, modo, opacidad, repeticion, semilla,
                             extras, contorno, variacion, distribucion) ->
                        new CapaPatron(p.orElse(null), color, tamano, angulo, posicion, forma, invertido, region, modo, opacidad,
                                repeticion, semilla, extras, contorno, variacion, false, distribucion)));
    }

    /**
     * La zona Solapas que se elige en Tintes (2026-10-08) se vuelve la variante del tipo de solapa y del frente de ESTA
     * prenda (más el cuello de camisa, si lo tiene); si no tiene ni uno ni otro, la capa no pinta nada (se descarta).
     */
    private static java.util.List<CapaPatron> especializarSolapas(ItemStack stack, java.util.List<CapaPatron> capas) {
        boolean hay = false;
        for (CapaPatron c : capas) if (c.region() == RegionPintura.SOLAPAS) { hay = true; break; }
        if (!hay) return capas;
        com.modamod.item.ChaquetaSolapa tipo = com.modamod.item.TopCorte.solapa(stack);
        boolean abierta = com.modamod.item.TopCorte.frente(stack).abre();
        boolean camisa = com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.REMERA)
                && com.modamod.sublimadora.RemeraItem.variante(stack).cuello() == com.modamod.sublimadora.Variante.Cuello.CAMISA;
        java.util.List<CapaPatron> out = new java.util.ArrayList<>(capas.size());
        for (CapaPatron c : capas) {
            if (c.region() != RegionPintura.SOLAPAS) { out.add(c); continue; }
            RegionPintura r = c.region().paraSolapa(tipo, abierta);
            if (r != null) out.add(conRegion(c, r));
            // El cuello de camisa (aro y puntas) va con la misma zona.
            if (camisa) out.add(conRegion(c, RegionPintura.SOLAPA_CAMISA));
        }
        return out;
    }

    public static CapaPatron conRegion(CapaPatron c, RegionPintura r) {
        return new CapaPatron(c.patronId(), c.color(), c.tamano(), c.angulo(), c.posicion(), c.forma(), c.invertido(),
                    r, c.modo(), c.opacidad(), c.repeticion(), c.semilla(), c.extras(), c.contorno(), c.variacion(),
                    c.fueraDeRegion(), c.distribucion());
    }

    /** Las capas a pintar, en orden, con la zona Solapas ya traducida a las piezas de esta prenda. */
    public static java.util.List<CapaPatron> capas(ItemStack stack) {
        return especializarSolapas(stack, stack.getOrDefault(ModamodComponents.CAPAS_TINTE, java.util.List.of()));
    }

    /** El primer molde que lleva la prenda (para el nombre en el tooltip), o null si es lisa. */
    @Nullable
    public static CapaPatron primerPatron(ItemStack stack) {
        for (CapaPatron c : stack.getOrDefault(ModamodComponents.CAPAS_TINTE, java.util.List.<CapaPatron>of())) {
            if (!c.lisa()) return c;
        }
        return null;
    }

    public static void quitarCapas(ItemStack stack) {
        stack.remove(ModamodComponents.CAPAS_TINTE);
    }
}
