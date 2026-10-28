package com.modamod.region;

import com.modamod.item.ClothingArmorItem;
import com.modamod.item.ClothingTrinketItem;
import com.modamod.item.ModamodComponents;
import com.modamod.item.ModamodDye;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * EL resolvedor: dado un ItemStack y una region, que valor corresponde.
 *
 * Absorbe lo que era ClothingStyle y le suma dos cosas que antes no existian
 * en ningun lado: la {@link Orientacion} —que se aplica al resolver, no
 * permutando datos— y el eje {@link Cara}.
 *
 * Por que una sola clase: la regla "la izquierda guarda, la derecha hereda"
 * estaba escrita en el telar, en el renderer y en el tooltip, y cada vez que
 * se tocaba una habia que acordarse de las otras. El bug de "tenir la
 * izquierda tine las dos" salio justamente de eso.
 *
 * <h2>La regla del eje Lado</h2>
 * La IZQUIERDA es primaria: guarda el valor en los componentes base. La
 * DERECHA guarda overrides opcionales (RIGHT_*) y cuando faltan hereda. Un
 * par parejo no ocupa data extra.
 *
 * <h2>La trampa de AMBAS</h2>
 * AMBAS no es "aplicar dos veces". Al escribir, setea el primario y BORRA el
 * override del otro lado: es el caso "parejo", y dejar los dos escritos haria
 * que un par igual pesara el doble sin ninguna ganancia. Al leer, AMBAS
 * devuelve el primario.
 *
 * <h2>Orientacion</h2>
 * Todo lo que entra por aca pasa antes por {@link #fisico}. Se aplica igual a
 * lecturas y a escrituras a proposito: si el jugador ve la prenda espejada y
 * pide tenir "la izquierda", quiere que cambie la que VE. Guardar del otro
 * lado y leer espejado da esa ilusion completa, y sacar el flag deja la
 * prenda exactamente como estaba.
 */
public final class RegionResolver {

    private RegionResolver() {}

    // --- Orientacion ---

    public static Orientacion orientacion(ItemStack stack) {
        Orientacion o = stack.get(ModamodComponents.ORIENTACION);
        return o == null ? Orientacion.NORMAL : o;
    }

    /**
     * Guarda la orientacion, o borra el componente si volvio a la normal.
     *
     * Borrarlo importa: una prenda sin girar ni espejar tiene que apilar con
     * otra igual, y un componente presente con los dos bits en false no
     * apilaria con uno ausente.
     */
    public static void ponerOrientacion(ItemStack stack, Orientacion o) {
        if (o.esNormal()) stack.remove(ModamodComponents.ORIENTACION);
        else stack.set(ModamodComponents.ORIENTACION, o);
    }

    /**
     * El lado donde REALMENTE vive el dato, dado el lado que se ve.
     *
     * AMBAS no se toca: no tiene lado opuesto.
     */
    public static Lado fisico(ItemStack stack, Lado visible) {
        return orientacion(stack).espejado() ? visible.opuesto() : visible;
    }

    /** Lo mismo para el eje frente/espalda. */
    public static Cara fisica(ItemStack stack, Cara visible) {
        return orientacion(stack).girado() ? visible.opuesta() : visible;
    }

    // --- Lectura ---

    public static int colorBase(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Integer override = stack.get(ModamodComponents.RIGHT_DYED_COLOR);
            if (override != null) return override;
        }
        if (stack.getItem() instanceof ClothingTrinketItem prenda) return prenda.getColor(stack);
        if (stack.getItem() instanceof ClothingArmorItem prenda) return prenda.getColor(stack);
        return 0xFFFFFF;
    }

    @Nullable
    public static Identifier patronId(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Identifier override = stack.get(ModamodComponents.RIGHT_PATTERN_ID);
            if (override != null) return override;
            // Una derecha con color propio pero sin patron propio es una
            // pierna configurada aparte: lisa, no hereda el patron de la otra.
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return null;
        }
        return stack.get(ModamodComponents.PATTERN_ID);
    }

    @Nullable
    private static Identifier patronId2(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Identifier override = stack.get(ModamodComponents.RIGHT_PATTERN_ID_2);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return null;
        }
        return stack.get(ModamodComponents.PATTERN_ID_2);
    }

    @Nullable
    private static Identifier patronId3(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Identifier override = stack.get(ModamodComponents.RIGHT_PATTERN_ID_3);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return null;
        }
        return stack.get(ModamodComponents.PATTERN_ID_3);
    }

    /**
     * Escala del patrón aplicado, CAPA 1 (§{@link com.modamod.item.TamanoPatron}).
     * Mismo criterio lado/override que {@link #patronId} — a diferencia de
     * ESE método, acá SIEMPRE hay un valor por defecto (GRANDE), nunca null:
     * el tamaño no tiene un estado "ausente" con significado propio, es una
     * escala de la máscara que ya está puesta.
     */
    public static com.modamod.item.TamanoPatron tamanoPatron(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            com.modamod.item.TamanoPatron override = stack.get(ModamodComponents.RIGHT_PATTERN_SIZE);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return com.modamod.item.TamanoPatron.GRANDE;
        }
        com.modamod.item.TamanoPatron t = stack.get(ModamodComponents.PATTERN_SIZE);
        return t != null ? t : com.modamod.item.TamanoPatron.GRANDE;
    }

    private static com.modamod.item.TamanoPatron tamanoPatron2(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            com.modamod.item.TamanoPatron override = stack.get(ModamodComponents.RIGHT_PATTERN_SIZE_2);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return com.modamod.item.TamanoPatron.GRANDE;
        }
        com.modamod.item.TamanoPatron t = stack.get(ModamodComponents.PATTERN_SIZE_2);
        return t != null ? t : com.modamod.item.TamanoPatron.GRANDE;
    }

    private static com.modamod.item.TamanoPatron tamanoPatron3(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            com.modamod.item.TamanoPatron override = stack.get(ModamodComponents.RIGHT_PATTERN_SIZE_3);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return com.modamod.item.TamanoPatron.GRANDE;
        }
        com.modamod.item.TamanoPatron t = stack.get(ModamodComponents.PATTERN_SIZE_3);
        return t != null ? t : com.modamod.item.TamanoPatron.GRANDE;
    }

    /** Ángulo del patrón en grados, CAPA 1 (0°=horizontal) — mismo criterio que {@link #tamanoPatron}. */
    public static float anguloPatron(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Float override = stack.get(ModamodComponents.RIGHT_PATTERN_ANGLE);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return 0f;
        }
        Float a = stack.get(ModamodComponents.PATTERN_ANGLE);
        return a != null ? a : 0f;
    }

    private static float anguloPatron2(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Float override = stack.get(ModamodComponents.RIGHT_PATTERN_ANGLE_2);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return 0f;
        }
        Float a = stack.get(ModamodComponents.PATTERN_ANGLE_2);
        return a != null ? a : 0f;
    }

    private static float anguloPatron3(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Float override = stack.get(ModamodComponents.RIGHT_PATTERN_ANGLE_3);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return 0f;
        }
        Float a = stack.get(ModamodComponents.PATTERN_ANGLE_3);
        return a != null ? a : 0f;
    }

    /** Posición del patrón a lo largo del eje "d" (§{@code PatronGenerador#mascaraPara}) — mismo criterio que {@link #tamanoPatron}. */
    public static float posicionPatron(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Float override = stack.get(ModamodComponents.RIGHT_PATTERN_POSITION);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return 0.5f;
        }
        Float p = stack.get(ModamodComponents.PATTERN_POSITION);
        return p != null ? p : 0.5f;
    }

    private static float posicionPatron2(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Float override = stack.get(ModamodComponents.RIGHT_PATTERN_POSITION_2);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return 0.5f;
        }
        Float p = stack.get(ModamodComponents.PATTERN_POSITION_2);
        return p != null ? p : 0.5f;
    }

    /**
     * "Negativo" de la máscara, CAPA 1 — a pedido (2026-09-20, "invertir
     * los colores del patron"). Mismo criterio lado/override que
     * {@link #anguloPatron}, ausente = false.
     */
    public static boolean invertidoPatron(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Boolean override = stack.get(ModamodComponents.RIGHT_PATTERN_INVERT);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return false;
        }
        Boolean b = stack.get(ModamodComponents.PATTERN_INVERT);
        return b != null && b;
    }

    private static boolean invertidoPatron2(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Boolean override = stack.get(ModamodComponents.RIGHT_PATTERN_INVERT_2);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return false;
        }
        Boolean b = stack.get(ModamodComponents.PATTERN_INVERT_2);
        return b != null && b;
    }

    private static boolean invertidoPatron3(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Boolean override = stack.get(ModamodComponents.RIGHT_PATTERN_INVERT_3);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return false;
        }
        Boolean b = stack.get(ModamodComponents.PATTERN_INVERT_3);
        return b != null && b;
    }

    private static float posicionPatron3(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Float override = stack.get(ModamodComponents.RIGHT_PATTERN_POSITION_3);
            if (override != null) return override;
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) != null) return 0.5f;
        }
        Float p = stack.get(ModamodComponents.PATTERN_POSITION_3);
        return p != null ? p : 0.5f;
    }

    /**
     * Forma del patrón, CAPA 1 — a diferencia de tamaño/orientación/
     * posición, sin override el default no es una constante fija: es la
     * forma propia del ÍTEM puesto ({@code ClothingPatternItem#forma}), a
     * pedido (2026-09-19, "que cualquier patron pueda elegir su forma").
     * Si no hay patrón puesto en este lado, devuelve ALTERNADO (no se usa
     * para nada, no hay capa que dibujar).
     */
    public static com.modamod.render.PatronGenerador.Forma formaPatron(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            com.modamod.render.PatronGenerador.Forma override = stack.get(ModamodComponents.RIGHT_PATTERN_FORM);
            if (override != null) return override;
        }
        com.modamod.render.PatronGenerador.Forma f = stack.get(ModamodComponents.PATTERN_FORM);
        if (f != null) return f;
        Identifier id = patronId(stack, visible);
        com.modamod.item.ClothingPatternItem item = id == null ? null : com.modamod.item.ClothingPatternItem.porId(id);
        return item != null ? item.forma : com.modamod.render.PatronGenerador.Forma.ALTERNADO;
    }

    private static com.modamod.render.PatronGenerador.Forma formaPatron2(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            com.modamod.render.PatronGenerador.Forma override = stack.get(ModamodComponents.RIGHT_PATTERN_FORM_2);
            if (override != null) return override;
        }
        com.modamod.render.PatronGenerador.Forma f = stack.get(ModamodComponents.PATTERN_FORM_2);
        if (f != null) return f;
        Identifier id = patronId2(stack, visible);
        com.modamod.item.ClothingPatternItem item = id == null ? null : com.modamod.item.ClothingPatternItem.porId(id);
        return item != null ? item.forma : com.modamod.render.PatronGenerador.Forma.ALTERNADO;
    }

    private static com.modamod.render.PatronGenerador.Forma formaPatron3(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            com.modamod.render.PatronGenerador.Forma override = stack.get(ModamodComponents.RIGHT_PATTERN_FORM_3);
            if (override != null) return override;
        }
        com.modamod.render.PatronGenerador.Forma f = stack.get(ModamodComponents.PATTERN_FORM_3);
        if (f != null) return f;
        Identifier id = patronId3(stack, visible);
        com.modamod.item.ClothingPatternItem item = id == null ? null : com.modamod.item.ClothingPatternItem.porId(id);
        return item != null ? item.forma : com.modamod.render.PatronGenerador.Forma.ALTERNADO;
    }

    /**
     * A qué región anatómica se restringe la capa 1 — {@link RegionPintura#TODO}
     * (toda la prenda) si nunca se tocó, mismo patrón bilateral que el
     * resto de los ejes de capa (2026-09-27, "pintar por región").
     */
    public static RegionPintura regionPatron(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            RegionPintura override = stack.get(ModamodComponents.RIGHT_PATTERN_REGION);
            if (override != null) return override;
        }
        RegionPintura r = stack.get(ModamodComponents.PATTERN_REGION);
        return r != null ? r : RegionPintura.TODO;
    }

    private static RegionPintura regionPatron2(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            RegionPintura override = stack.get(ModamodComponents.RIGHT_PATTERN_REGION_2);
            if (override != null) return override;
        }
        RegionPintura r = stack.get(ModamodComponents.PATTERN_REGION_2);
        return r != null ? r : RegionPintura.TODO;
    }

    private static RegionPintura regionPatron3(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            RegionPintura override = stack.get(ModamodComponents.RIGHT_PATTERN_REGION_3);
            if (override != null) return override;
        }
        RegionPintura r = stack.get(ModamodComponents.PATTERN_REGION_3);
        return r != null ? r : RegionPintura.TODO;
    }

    public static int colorPatron(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Integer override = stack.get(ModamodComponents.RIGHT_PATTERN_COLOR);
            if (override != null) return override;
        }
        Integer base = stack.get(ModamodComponents.PATTERN_COLOR);
        return base != null ? base : 0xFFFFFF;
    }

    private static int colorPatron2(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Integer override = stack.get(ModamodComponents.RIGHT_PATTERN_COLOR_2);
            if (override != null) return override;
        }
        Integer base = stack.get(ModamodComponents.PATTERN_COLOR_2);
        return base != null ? base : 0xFFFFFF;
    }

    private static int colorPatron3(ItemStack stack, Lado visible) {
        Lado lado = fisico(stack, visible);
        if (lado == Lado.DERECHA) {
            Integer override = stack.get(ModamodComponents.RIGHT_PATTERN_COLOR_3);
            if (override != null) return override;
        }
        Integer base = stack.get(ModamodComponents.PATTERN_COLOR_3);
        return base != null ? base : 0xFFFFFF;
    }

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
     * Las capas que hay que PINTAR: las viejas de los componentes PATTERN_*
     * (Telar/Modeladora, por lado) + las de Tinturas por cuadradito
     * ({@link ModamodComponents#CAPAS_TINTE}, 2026-09-27), que ya traen
     * su propia región y no dependen del lado que se esté componiendo —
     * una capa de la pierna derecha pintada en la textura de la izquierda
     * cae fuera de lo que esa pieza dibuja, no hace falta filtrarla.
     * Solo para RENDER: quien reescribe capas (ver {@code PrendaModelado})
     * sigue usando {@link #capasAplicadas}, que no mezcla las dos.
     */
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
        boolean camisa = stack.getItem() instanceof com.modamod.sublimadora.RemeraItem
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

    public static java.util.List<CapaPatron> capasTinte(ItemStack stack, Lado visible) {
        java.util.List<CapaPatron> nuevas = especializarSolapas(stack,
                stack.getOrDefault(ModamodComponents.CAPAS_TINTE, java.util.List.of()));
        java.util.List<CapaPatron> viejas = capasAplicadas(stack, visible);
        if (nuevas.isEmpty()) return viejas;
        java.util.List<CapaPatron> todas = new java.util.ArrayList<>(viejas.size() + nuevas.size());
        todas.addAll(viejas);
        todas.addAll(nuevas);
        return todas;
    }

    /**
     * Las hasta 3 capas de patrón puestas, cada una con SU PROPIO color/
     * tamaño/orientación (§{@code TinturasBlockEntity#CAPAS_MAXIMO}) — a
     * pedido (2026-09-18/19: "orden y cambio de color", después "variar
     * orientacion y tamaño entre cada capa"). En orden (capa 1 primero)
     * porque el orden de pintado importa cuando se superponen — la
     * última pinta ENCIMA de las anteriores.
     */
    public static java.util.List<CapaPatron> capasAplicadas(ItemStack stack, Lado visible) {
        java.util.List<CapaPatron> lista = new java.util.ArrayList<>(3);
        Identifier p1 = patronId(stack, visible);
        if (p1 != null) lista.add(new CapaPatron(p1, colorPatron(stack, visible), tamanoPatron(stack, visible), anguloPatron(stack, visible), posicionPatron(stack, visible), formaPatron(stack, visible), invertidoPatron(stack, visible), regionPatron(stack, visible)));
        Identifier p2 = patronId2(stack, visible);
        if (p2 != null) lista.add(new CapaPatron(p2, colorPatron2(stack, visible), tamanoPatron2(stack, visible), anguloPatron2(stack, visible), posicionPatron2(stack, visible), formaPatron2(stack, visible), invertidoPatron2(stack, visible), regionPatron2(stack, visible)));
        Identifier p3 = patronId3(stack, visible);
        if (p3 != null) lista.add(new CapaPatron(p3, colorPatron3(stack, visible), tamanoPatron3(stack, visible), anguloPatron3(stack, visible), posicionPatron3(stack, visible), formaPatron3(stack, visible), invertidoPatron3(stack, visible), regionPatron3(stack, visible)));
        return lista;
    }

    public static boolean tienePatron(ItemStack stack, Lado visible) {
        return patronId(stack, visible) != null;
    }

    /** True si los dos lados se ven exactamente igual. */
    public static boolean parejo(ItemStack stack) {
        return Objects.equals(patronId(stack, Lado.IZQUIERDA), patronId(stack, Lado.DERECHA))
                && colorBase(stack, Lado.IZQUIERDA) == colorBase(stack, Lado.DERECHA)
                && colorPatron(stack, Lado.IZQUIERDA) == colorPatron(stack, Lado.DERECHA);
    }

    // --- Escritura ---

    public static void ponerColorBase(ItemStack stack, Lado visible, int rgb) {
        if (visible == Lado.AMBAS) {
            ModamodDye.setBaseColor(stack, rgb);
            limpiarDerecha(stack);
            return;
        }
        if (fisico(stack, visible) == Lado.DERECHA) {
            stack.set(ModamodComponents.RIGHT_DYED_COLOR, rgb);
        } else {
            fijarDerecha(stack);
            ModamodDye.setBaseColor(stack, rgb);
        }
    }

    public static void ponerPatron(ItemStack stack, Lado visible, Identifier patronId, int rgb) {
        ponerPatron(stack, visible, patronId, rgb, com.modamod.item.TamanoPatron.GRANDE);
    }

    /**
     * Como {@link #ponerPatron(ItemStack, Lado, Identifier, int)} pero
     * grabando también la escala — usado por la Estación de Tintes, que
     * tiene el botón de tamaño (§{@code TinturasBlockEntity#BTN_TAMANO}).
     * El Telar viejo sigue usando el overload de arriba (siempre GRANDE),
     * no hace falta tocarlo.
     */
    public static void ponerPatron(ItemStack stack, Lado visible, Identifier patronId, int rgb,
                                    com.modamod.item.TamanoPatron tamano) {
        ponerPatron(stack, visible, patronId, rgb, tamano, 0f);
    }

    /**
     * Como el overload de arriba pero grabando también el ángulo
     * (§{@code TinturasBlockEntity#BTN_ANGULO}) — a pedido (2026-09-18,
     * "pensaba en agregar la posibilidad de rotacion del patron"; ángulo
     * libre desde 2026-09-19, "girarlo en angulo").
     */
    public static void ponerPatron(ItemStack stack, Lado visible, Identifier patronId, int rgb,
                                    com.modamod.item.TamanoPatron tamano, float angulo) {
        com.modamod.item.ClothingPatternItem item = com.modamod.item.ClothingPatternItem.porId(patronId);
        com.modamod.render.PatronGenerador.Forma forma = item != null ? item.forma : com.modamod.render.PatronGenerador.Forma.ALTERNADO;
        ponerPatron(stack, visible, java.util.List.of(new CapaPatron(patronId, rgb, tamano, angulo, 0.5f, forma, false)));
    }

    /**
     * Hasta 3 capas de patrón apiladas, CADA UNA con su propio color,
     * tamaño Y orientación (§{@code TinturasBlockEntity#CAPAS_MAXIMO}) —
     * a pedido (2026-09-18 "dale mandale 3" y "orden y cambio de color",
     * 2026-09-19 "variar orientacion y tamaño entre cada capa"). {@code
     * capas} nunca vacía (quien no quiere patrón usa {@link
     * #ponerColorBase}); la capa 1 va en los componentes PATTERN_* de
     * siempre (así el Telar viejo y los chequeos de {@code Garment} no
     * notan la diferencia), 2 y 3 en componentes nuevos, ausentes si esa
     * capa no existe. El ORDEN de la lista es el orden de pintado (ver
     * {@code ClothingTextureCache#composeGarmentCapas}): la capa 1 pinta
     * primero, la 3 queda arriba de todo.
     */
    public static void ponerPatron(ItemStack stack, Lado visible, java.util.List<CapaPatron> capas) {
        CapaPatron c1 = capas.get(0);
        CapaPatron c2 = capas.size() > 1 ? capas.get(1) : null;
        CapaPatron c3 = capas.size() > 2 ? capas.get(2) : null;
        if (visible == Lado.AMBAS) {
            escribirCapa1(stack, ModamodComponents.PATTERN_ID, ModamodComponents.PATTERN_COLOR,
                    ModamodComponents.PATTERN_SIZE, ModamodComponents.PATTERN_ANGLE,
                    ModamodComponents.PATTERN_POSITION, ModamodComponents.PATTERN_FORM,
                    ModamodComponents.PATTERN_INVERT, ModamodComponents.PATTERN_REGION, c1);
            escribirCapaOpcional(stack, ModamodComponents.PATTERN_ID_2, ModamodComponents.PATTERN_COLOR_2,
                    ModamodComponents.PATTERN_SIZE_2, ModamodComponents.PATTERN_ANGLE_2,
                    ModamodComponents.PATTERN_POSITION_2, ModamodComponents.PATTERN_FORM_2,
                    ModamodComponents.PATTERN_INVERT_2, ModamodComponents.PATTERN_REGION_2, c2);
            escribirCapaOpcional(stack, ModamodComponents.PATTERN_ID_3, ModamodComponents.PATTERN_COLOR_3,
                    ModamodComponents.PATTERN_SIZE_3, ModamodComponents.PATTERN_ANGLE_3,
                    ModamodComponents.PATTERN_POSITION_3, ModamodComponents.PATTERN_FORM_3,
                    ModamodComponents.PATTERN_INVERT_3, ModamodComponents.PATTERN_REGION_3, c3);
            limpiarDerecha(stack);
            return;
        }
        if (fisico(stack, visible) == Lado.DERECHA) {
            escribirCapa1(stack, ModamodComponents.RIGHT_PATTERN_ID, ModamodComponents.RIGHT_PATTERN_COLOR,
                    ModamodComponents.RIGHT_PATTERN_SIZE, ModamodComponents.RIGHT_PATTERN_ANGLE,
                    ModamodComponents.RIGHT_PATTERN_POSITION, ModamodComponents.RIGHT_PATTERN_FORM,
                    ModamodComponents.RIGHT_PATTERN_INVERT, ModamodComponents.RIGHT_PATTERN_REGION, c1);
            escribirCapaOpcional(stack, ModamodComponents.RIGHT_PATTERN_ID_2, ModamodComponents.RIGHT_PATTERN_COLOR_2,
                    ModamodComponents.RIGHT_PATTERN_SIZE_2, ModamodComponents.RIGHT_PATTERN_ANGLE_2,
                    ModamodComponents.RIGHT_PATTERN_POSITION_2, ModamodComponents.RIGHT_PATTERN_FORM_2,
                    ModamodComponents.RIGHT_PATTERN_INVERT_2, ModamodComponents.RIGHT_PATTERN_REGION_2, c2);
            escribirCapaOpcional(stack, ModamodComponents.RIGHT_PATTERN_ID_3, ModamodComponents.RIGHT_PATTERN_COLOR_3,
                    ModamodComponents.RIGHT_PATTERN_SIZE_3, ModamodComponents.RIGHT_PATTERN_ANGLE_3,
                    ModamodComponents.RIGHT_PATTERN_POSITION_3, ModamodComponents.RIGHT_PATTERN_FORM_3,
                    ModamodComponents.RIGHT_PATTERN_INVERT_3, ModamodComponents.RIGHT_PATTERN_REGION_3, c3);
            // Sin color base propio la derecha vuelve a heredar el patron de
            // la izquierda por el fallback de patronId.
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) == null) {
                stack.set(ModamodComponents.RIGHT_DYED_COLOR, colorBase(stack, Lado.IZQUIERDA));
            }
        } else {
            fijarDerecha(stack);
            escribirCapa1(stack, ModamodComponents.PATTERN_ID, ModamodComponents.PATTERN_COLOR,
                    ModamodComponents.PATTERN_SIZE, ModamodComponents.PATTERN_ANGLE,
                    ModamodComponents.PATTERN_POSITION, ModamodComponents.PATTERN_FORM,
                    ModamodComponents.PATTERN_INVERT, ModamodComponents.PATTERN_REGION, c1);
            escribirCapaOpcional(stack, ModamodComponents.PATTERN_ID_2, ModamodComponents.PATTERN_COLOR_2,
                    ModamodComponents.PATTERN_SIZE_2, ModamodComponents.PATTERN_ANGLE_2,
                    ModamodComponents.PATTERN_POSITION_2, ModamodComponents.PATTERN_FORM_2,
                    ModamodComponents.PATTERN_INVERT_2, ModamodComponents.PATTERN_REGION_2, c2);
            escribirCapaOpcional(stack, ModamodComponents.PATTERN_ID_3, ModamodComponents.PATTERN_COLOR_3,
                    ModamodComponents.PATTERN_SIZE_3, ModamodComponents.PATTERN_ANGLE_3,
                    ModamodComponents.PATTERN_POSITION_3, ModamodComponents.PATTERN_FORM_3,
                    ModamodComponents.PATTERN_INVERT_3, ModamodComponents.PATTERN_REGION_3, c3);
        }
    }

    /** La capa 1 SIEMPRE existe (capas nunca está vacía acá) — sin el if de ausencia de {@link #escribirCapaOpcional}. */
    private static void escribirCapa1(ItemStack stack, net.minecraft.component.ComponentType<Identifier> idComp,
                                       net.minecraft.component.ComponentType<Integer> colorComp,
                                       net.minecraft.component.ComponentType<com.modamod.item.TamanoPatron> tamanoComp,
                                       net.minecraft.component.ComponentType<Float> anguloComp,
                                       net.minecraft.component.ComponentType<Float> posComp,
                                       net.minecraft.component.ComponentType<com.modamod.render.PatronGenerador.Forma> formaComp,
                                       net.minecraft.component.ComponentType<Boolean> invertComp,
                                       net.minecraft.component.ComponentType<RegionPintura> regionComp,
                                       CapaPatron capa) {
        stack.set(idComp, capa.patronId());
        stack.set(colorComp, capa.color());
        if (capa.tamano() == com.modamod.item.TamanoPatron.GRANDE) stack.remove(tamanoComp);
        else stack.set(tamanoComp, capa.tamano());
        if (capa.angulo() == 0f) stack.remove(anguloComp);
        else stack.set(anguloComp, capa.angulo());
        if (capa.posicion() == 0.5f) stack.remove(posComp);
        else stack.set(posComp, capa.posicion());
        // Default = la forma propia del ÍTEM (no una constante) — ver
        // formaPatron. Ausente si coincide, así una prenda que nunca tocó
        // el botón Forma no ocupa data extra.
        com.modamod.item.ClothingPatternItem item = com.modamod.item.ClothingPatternItem.porId(capa.patronId());
        com.modamod.render.PatronGenerador.Forma formaDefault = item != null ? item.forma : com.modamod.render.PatronGenerador.Forma.ALTERNADO;
        if (capa.forma() == formaDefault) stack.remove(formaComp);
        else stack.set(formaComp, capa.forma());
        if (!capa.invertido()) stack.remove(invertComp);
        else stack.set(invertComp, true);
        if (capa.region() == RegionPintura.TODO) stack.remove(regionComp);
        else stack.set(regionComp, capa.region());
    }

    /** Capas 2/3: ausentes del todo si esa capa no existe. */
    private static void escribirCapaOpcional(ItemStack stack, net.minecraft.component.ComponentType<Identifier> idComp,
                                              net.minecraft.component.ComponentType<Integer> colorComp,
                                              net.minecraft.component.ComponentType<com.modamod.item.TamanoPatron> tamanoComp,
                                              net.minecraft.component.ComponentType<Float> anguloComp,
                                              net.minecraft.component.ComponentType<Float> posComp,
                                              net.minecraft.component.ComponentType<com.modamod.render.PatronGenerador.Forma> formaComp,
                                              net.minecraft.component.ComponentType<Boolean> invertComp,
                                              net.minecraft.component.ComponentType<RegionPintura> regionComp,
                                              @Nullable CapaPatron capa) {
        if (capa == null) {
            stack.remove(idComp);
            stack.remove(colorComp);
            stack.remove(tamanoComp);
            stack.remove(anguloComp);
            stack.remove(posComp);
            stack.remove(formaComp);
            stack.remove(invertComp);
            stack.remove(regionComp);
        } else {
            escribirCapa1(stack, idComp, colorComp, tamanoComp, anguloComp, posComp, formaComp, invertComp, regionComp, capa);
        }
    }

    public static void quitarPatron(ItemStack stack, Lado visible) {
        if (visible == Lado.AMBAS) {
            stack.remove(ModamodComponents.PATTERN_ID);
            stack.remove(ModamodComponents.PATTERN_ID_2);
            stack.remove(ModamodComponents.PATTERN_ID_3);
            stack.remove(ModamodComponents.PATTERN_COLOR);
            stack.remove(ModamodComponents.PATTERN_COLOR_2);
            stack.remove(ModamodComponents.PATTERN_COLOR_3);
            stack.remove(ModamodComponents.PATTERN_SIZE);
            stack.remove(ModamodComponents.PATTERN_SIZE_2);
            stack.remove(ModamodComponents.PATTERN_SIZE_3);
            stack.remove(ModamodComponents.PATTERN_ANGLE);
            stack.remove(ModamodComponents.PATTERN_ANGLE_2);
            stack.remove(ModamodComponents.PATTERN_ANGLE_3);
            stack.remove(ModamodComponents.PATTERN_POSITION);
            stack.remove(ModamodComponents.PATTERN_POSITION_2);
            stack.remove(ModamodComponents.PATTERN_POSITION_3);
            stack.remove(ModamodComponents.PATTERN_FORM);
            stack.remove(ModamodComponents.PATTERN_FORM_2);
            stack.remove(ModamodComponents.PATTERN_FORM_3);
            stack.remove(ModamodComponents.PATTERN_INVERT);
            stack.remove(ModamodComponents.PATTERN_INVERT_2);
            stack.remove(ModamodComponents.PATTERN_INVERT_3);
            stack.remove(ModamodComponents.PATTERN_REGION);
            stack.remove(ModamodComponents.PATTERN_REGION_2);
            stack.remove(ModamodComponents.PATTERN_REGION_3);
            stack.remove(ModamodComponents.CAPAS_TINTE);
            limpiarDerecha(stack);
            return;
        }
        if (fisico(stack, visible) == Lado.DERECHA) {
            stack.remove(ModamodComponents.RIGHT_PATTERN_ID);
            stack.remove(ModamodComponents.RIGHT_PATTERN_ID_2);
            stack.remove(ModamodComponents.RIGHT_PATTERN_ID_3);
            stack.remove(ModamodComponents.RIGHT_PATTERN_COLOR);
            stack.remove(ModamodComponents.RIGHT_PATTERN_COLOR_2);
            stack.remove(ModamodComponents.RIGHT_PATTERN_COLOR_3);
            stack.remove(ModamodComponents.RIGHT_PATTERN_SIZE);
            stack.remove(ModamodComponents.RIGHT_PATTERN_SIZE_2);
            stack.remove(ModamodComponents.RIGHT_PATTERN_SIZE_3);
            stack.remove(ModamodComponents.RIGHT_PATTERN_ANGLE);
            stack.remove(ModamodComponents.RIGHT_PATTERN_ANGLE_2);
            stack.remove(ModamodComponents.RIGHT_PATTERN_ANGLE_3);
            stack.remove(ModamodComponents.RIGHT_PATTERN_POSITION);
            stack.remove(ModamodComponents.RIGHT_PATTERN_POSITION_2);
            stack.remove(ModamodComponents.RIGHT_PATTERN_POSITION_3);
            stack.remove(ModamodComponents.RIGHT_PATTERN_FORM);
            stack.remove(ModamodComponents.RIGHT_PATTERN_FORM_2);
            stack.remove(ModamodComponents.RIGHT_PATTERN_FORM_3);
            stack.remove(ModamodComponents.RIGHT_PATTERN_INVERT);
            stack.remove(ModamodComponents.RIGHT_PATTERN_INVERT_2);
            stack.remove(ModamodComponents.RIGHT_PATTERN_INVERT_3);
            stack.remove(ModamodComponents.RIGHT_PATTERN_REGION);
            stack.remove(ModamodComponents.RIGHT_PATTERN_REGION_2);
            stack.remove(ModamodComponents.RIGHT_PATTERN_REGION_3);
            if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) == null) {
                stack.set(ModamodComponents.RIGHT_DYED_COLOR, colorBase(stack, Lado.IZQUIERDA));
            }
        } else {
            fijarDerecha(stack);
            stack.remove(ModamodComponents.PATTERN_ID);
            stack.remove(ModamodComponents.PATTERN_ID_2);
            stack.remove(ModamodComponents.PATTERN_ID_3);
            stack.remove(ModamodComponents.PATTERN_COLOR);
            stack.remove(ModamodComponents.PATTERN_COLOR_2);
            stack.remove(ModamodComponents.PATTERN_COLOR_3);
            stack.remove(ModamodComponents.PATTERN_SIZE);
            stack.remove(ModamodComponents.PATTERN_SIZE_2);
            stack.remove(ModamodComponents.PATTERN_SIZE_3);
            stack.remove(ModamodComponents.PATTERN_ANGLE);
            stack.remove(ModamodComponents.PATTERN_ANGLE_2);
            stack.remove(ModamodComponents.PATTERN_ANGLE_3);
            stack.remove(ModamodComponents.PATTERN_POSITION);
            stack.remove(ModamodComponents.PATTERN_POSITION_2);
            stack.remove(ModamodComponents.PATTERN_POSITION_3);
            stack.remove(ModamodComponents.PATTERN_FORM);
            stack.remove(ModamodComponents.PATTERN_FORM_2);
            stack.remove(ModamodComponents.PATTERN_FORM_3);
            stack.remove(ModamodComponents.PATTERN_INVERT);
            stack.remove(ModamodComponents.PATTERN_INVERT_2);
            stack.remove(ModamodComponents.PATTERN_INVERT_3);
            stack.remove(ModamodComponents.PATTERN_REGION);
            stack.remove(ModamodComponents.PATTERN_REGION_2);
            stack.remove(ModamodComponents.PATTERN_REGION_3);
        }
    }

    /**
     * Clava en la derecha lo que hoy hereda, ANTES de tocar la izquierda.
     *
     * Es EL unico camino para tocar un lado sin arrastrar el otro, y por eso
     * ahora lo llaman solos los setters de aca arriba en vez de dejarselo al
     * que llama: olvidarselo era exactamente el bug de "tenir una pierna tine
     * las dos", y un guard que hay que acordarse de invocar vuelve a fallar
     * tarde o temprano.
     */
    public static void fijarDerecha(ItemStack stack) {
        if (stack.get(ModamodComponents.RIGHT_PATTERN_ID) == null) {
            Identifier patronIzq = stack.get(ModamodComponents.PATTERN_ID);
            if (patronIzq != null) {
                stack.set(ModamodComponents.RIGHT_PATTERN_ID, patronIzq);
                stack.set(ModamodComponents.RIGHT_PATTERN_COLOR, colorPatron(stack, Lado.IZQUIERDA));
                stack.set(ModamodComponents.RIGHT_PATTERN_SIZE, tamanoPatron(stack, Lado.IZQUIERDA));
                stack.set(ModamodComponents.RIGHT_PATTERN_ANGLE, anguloPatron(stack, Lado.IZQUIERDA));
                stack.set(ModamodComponents.RIGHT_PATTERN_POSITION, posicionPatron(stack, Lado.IZQUIERDA));
                stack.set(ModamodComponents.RIGHT_PATTERN_FORM, formaPatron(stack, Lado.IZQUIERDA));
                stack.set(ModamodComponents.RIGHT_PATTERN_INVERT, invertidoPatron(stack, Lado.IZQUIERDA));
                stack.set(ModamodComponents.RIGHT_PATTERN_REGION, regionPatron(stack, Lado.IZQUIERDA));
                Identifier patron2Izq = stack.get(ModamodComponents.PATTERN_ID_2);
                if (patron2Izq != null) {
                    stack.set(ModamodComponents.RIGHT_PATTERN_ID_2, patron2Izq);
                    stack.set(ModamodComponents.RIGHT_PATTERN_COLOR_2, colorPatron2(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_SIZE_2, tamanoPatron2(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_ANGLE_2, anguloPatron2(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_POSITION_2, posicionPatron2(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_FORM_2, formaPatron2(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_INVERT_2, invertidoPatron2(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_REGION_2, regionPatron2(stack, Lado.IZQUIERDA));
                }
                Identifier patron3Izq = stack.get(ModamodComponents.PATTERN_ID_3);
                if (patron3Izq != null) {
                    stack.set(ModamodComponents.RIGHT_PATTERN_ID_3, patron3Izq);
                    stack.set(ModamodComponents.RIGHT_PATTERN_COLOR_3, colorPatron3(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_SIZE_3, tamanoPatron3(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_ANGLE_3, anguloPatron3(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_POSITION_3, posicionPatron3(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_FORM_3, formaPatron3(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_INVERT_3, invertidoPatron3(stack, Lado.IZQUIERDA));
                    stack.set(ModamodComponents.RIGHT_PATTERN_REGION_3, regionPatron3(stack, Lado.IZQUIERDA));
                }
            }
        }
        // El color va ULTIMO: patronId(DERECHA) usa la ausencia de
        // RIGHT_DYED_COLOR para decidir si hereda el patron de la izquierda.
        if (stack.get(ModamodComponents.RIGHT_DYED_COLOR) == null) {
            stack.set(ModamodComponents.RIGHT_DYED_COLOR, colorBase(stack, Lado.IZQUIERDA));
        }
    }

    /** Borra los overrides: el par vuelve a ser parejo. */
    public static void limpiarDerecha(ItemStack stack) {
        stack.remove(ModamodComponents.RIGHT_DYED_COLOR);
        stack.remove(ModamodComponents.RIGHT_PATTERN_ID);
        stack.remove(ModamodComponents.RIGHT_PATTERN_ID_2);
        stack.remove(ModamodComponents.RIGHT_PATTERN_ID_3);
        stack.remove(ModamodComponents.RIGHT_PATTERN_COLOR);
        stack.remove(ModamodComponents.RIGHT_PATTERN_COLOR_2);
        stack.remove(ModamodComponents.RIGHT_PATTERN_COLOR_3);
        stack.remove(ModamodComponents.RIGHT_PATTERN_SIZE);
        stack.remove(ModamodComponents.RIGHT_PATTERN_SIZE_2);
        stack.remove(ModamodComponents.RIGHT_PATTERN_SIZE_3);
        stack.remove(ModamodComponents.RIGHT_PATTERN_ANGLE);
        stack.remove(ModamodComponents.RIGHT_PATTERN_ANGLE_2);
        stack.remove(ModamodComponents.RIGHT_PATTERN_ANGLE_3);
        stack.remove(ModamodComponents.RIGHT_PATTERN_POSITION);
        stack.remove(ModamodComponents.RIGHT_PATTERN_POSITION_2);
        stack.remove(ModamodComponents.RIGHT_PATTERN_POSITION_3);
        stack.remove(ModamodComponents.RIGHT_PATTERN_FORM);
        stack.remove(ModamodComponents.RIGHT_PATTERN_FORM_2);
        stack.remove(ModamodComponents.RIGHT_PATTERN_FORM_3);
        stack.remove(ModamodComponents.RIGHT_PATTERN_INVERT);
        stack.remove(ModamodComponents.RIGHT_PATTERN_INVERT_2);
        stack.remove(ModamodComponents.RIGHT_PATTERN_INVERT_3);
        stack.remove(ModamodComponents.RIGHT_PATTERN_REGION);
        stack.remove(ModamodComponents.RIGHT_PATTERN_REGION_2);
        stack.remove(ModamodComponents.RIGHT_PATTERN_REGION_3);
    }

    /**
     * Intercambia izquierda y derecha permutando los datos guardados.
     *
     * Es la accion de estacion "espejar esta capa", distinta del flag
     * {@code espejado} de la orientacion: aca los valores cambian de lugar de
     * verdad, asi que despues se pueden seguir editando por separado. Si la
     * derecha heredaba, la que pasa a heredar es la izquierda.
     */
    public static void espejarCapa(ItemStack stack) {
        int colorIzq = colorBase(stack, Lado.IZQUIERDA);
        Identifier patronIzq = patronId(stack, Lado.IZQUIERDA);
        int colorPatronIzq = colorPatron(stack, Lado.IZQUIERDA);

        int colorDer = colorBase(stack, Lado.DERECHA);
        Identifier patronDer = patronId(stack, Lado.DERECHA);
        int colorPatronDer = colorPatron(stack, Lado.DERECHA);

        limpiarDerecha(stack);
        ModamodDye.setBaseColor(stack, colorDer);
        if (patronDer != null) {
            stack.set(ModamodComponents.PATTERN_ID, patronDer);
            stack.set(ModamodComponents.PATTERN_COLOR, colorPatronDer);
        } else {
            stack.remove(ModamodComponents.PATTERN_ID);
            stack.remove(ModamodComponents.PATTERN_COLOR);
        }
        // Si los dos lados eran iguales no se escribe override: un par parejo
        // espejado sigue sin ocupar data extra.
        if (colorIzq != colorDer || !Objects.equals(patronIzq, patronDer)
                || colorPatronIzq != colorPatronDer) {
            stack.set(ModamodComponents.RIGHT_DYED_COLOR, colorIzq);
            if (patronIzq != null) {
                stack.set(ModamodComponents.RIGHT_PATTERN_ID, patronIzq);
                stack.set(ModamodComponents.RIGHT_PATTERN_COLOR, colorPatronIzq);
            }
        }
    }
}
