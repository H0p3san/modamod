package com.modamod.item;

import com.modamod.region.Orientacion;
import com.modamod.sublimadora.Variante;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

/**
 * Componentes de datos custom para el sistema de personalización:
 * - PATTERN_ID: qué patrón está aplicado (ej. "modamod:stripe_top"),
 *   null/ausente = sin patrón (prenda lisa).
 * - PATTERN_COLOR: color del patrón (el segundo tinte). Antes se
 *   llamaba STRIPE_COLOR — se generalizó de "raya" a "cualquier patrón"
 *   ahora que hay una estación para aplicarlos.
 * El color BASE de la prenda sigue usando DataComponentTypes.DYED_COLOR
 * (el mecanismo vanilla, vía ClothingTrinketItem/ClothingArmorItem).
 */
public final class ModamodComponents {

    public static final ComponentType<Identifier> PATTERN_ID = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "pattern_id"),
            ComponentType.<Identifier>builder()
                    .codec(Identifier.CODEC)
                    .packetCodec(Identifier.PACKET_CODEC)
                    .build());

    /**
     * Capas 2 y 3 del patrón (§{@code TinturasBlockEntity#CAPAS_MAXIMO})
     * — a pedido (2026-09-18, "dale mandale 3": patrones apilados como
     * estandartes). PATTERN_ID sigue siendo la capa 1 (compatibilidad
     * con el Telar viejo y con guardados de antes de esta función, que
     * solo conocían una capa); estas dos son opcionales, ausente = esa
     * capa no existe.
     */
    private static ComponentType<Identifier> patternIdComponent(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", nombre),
                ComponentType.<Identifier>builder()
                        .codec(Identifier.CODEC)
                        .packetCodec(Identifier.PACKET_CODEC)
                        .build());
    }

    public static final ComponentType<Identifier> PATTERN_ID_2 = patternIdComponent("pattern_id_2");
    public static final ComponentType<Identifier> PATTERN_ID_3 = patternIdComponent("pattern_id_3");
    public static final ComponentType<Identifier> RIGHT_PATTERN_ID_2 = patternIdComponent("right_pattern_id_2");
    public static final ComponentType<Identifier> RIGHT_PATTERN_ID_3 = patternIdComponent("right_pattern_id_3");

    public static final ComponentType<Integer> PATTERN_COLOR = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "pattern_color"),
            ComponentType.<Integer>builder()
                    .codec(Codec.INT)
                    .packetCodec(PacketCodecs.INTEGER)
                    .build());

    /**
     * Color de las capas 2 y 3 — a pedido (2026-09-18, "para los tres
     * patrones necesitaria... cambio de color"): cada capa apilada tiene
     * SU PROPIO color, no comparten uno solo como antes.
     */
    private static ComponentType<Integer> patternColorComponent(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", nombre),
                ComponentType.<Integer>builder()
                        .codec(Codec.INT)
                        .packetCodec(PacketCodecs.INTEGER)
                        .build());
    }

    public static final ComponentType<Integer> PATTERN_COLOR_2 = patternColorComponent("pattern_color_2");
    public static final ComponentType<Integer> PATTERN_COLOR_3 = patternColorComponent("pattern_color_3");
    public static final ComponentType<Integer> RIGHT_PATTERN_COLOR_2 = patternColorComponent("right_pattern_color_2");
    public static final ComponentType<Integer> RIGHT_PATTERN_COLOR_3 = patternColorComponent("right_pattern_color_3");

    /** Escala del patrón (§{@link TamanoPatron}) — ausente = GRANDE. */
    public static final ComponentType<TamanoPatron> PATTERN_SIZE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "pattern_size"),
            ComponentType.<TamanoPatron>builder()
                    .codec(StringIdentifiable.createCodec(TamanoPatron::values))
                    .packetCodec(PacketCodecs.indexed(i -> TamanoPatron.values()[i], Enum::ordinal))
                    .build());

    /**
     * Tamaño de las capas 2 y 3 — a pedido (2026-09-19, "tendria q poder
     * variar orientacion y tamaño entre cada capa"): antes las 3
     * compartían un solo tamaño, igual que pasaba con el color antes de
     * {@link #PATTERN_COLOR_2}.
     */
    private static ComponentType<TamanoPatron> patternSizeComponent(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", nombre),
                ComponentType.<TamanoPatron>builder()
                        .codec(StringIdentifiable.createCodec(TamanoPatron::values))
                        .packetCodec(PacketCodecs.indexed(i -> TamanoPatron.values()[i], Enum::ordinal))
                        .build());
    }

    /**
     * Ángulo del patrón en grados (0°=horizontal, 90°=vertical, cualquier
     * valor es una diagonal) — ausente = 0°. Reemplaza a la vieja
     * Horizontal/Vertical (2026-09-19, "girarlo en angulo" — "flechitas
     * para posicionar... e incluso para girarlo en angulo"). Nombre
     * completo para no chocar con {@link Orientacion} (esa es la de
     * girado/espejado de la PRENDA, un concepto totalmente distinto).
     */
    private static ComponentType<Float> patternAngleComponent(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", nombre),
                ComponentType.<Float>builder()
                        .codec(Codec.FLOAT)
                        .packetCodec(PacketCodecs.FLOAT)
                        .build());
    }

    public static final ComponentType<Float> PATTERN_ANGLE = patternAngleComponent("pattern_angle");
    public static final ComponentType<Float> PATTERN_ANGLE_2 = patternAngleComponent("pattern_angle_2");
    public static final ComponentType<Float> PATTERN_ANGLE_3 = patternAngleComponent("pattern_angle_3");
    public static final ComponentType<Float> RIGHT_PATTERN_ANGLE = patternAngleComponent("right_pattern_angle");
    public static final ComponentType<Float> RIGHT_PATTERN_ANGLE_2 = patternAngleComponent("right_pattern_angle_2");
    public static final ComponentType<Float> RIGHT_PATTERN_ANGLE_3 = patternAngleComponent("right_pattern_angle_3");

    public static final ComponentType<TamanoPatron> PATTERN_SIZE_2 = patternSizeComponent("pattern_size_2");
    public static final ComponentType<TamanoPatron> PATTERN_SIZE_3 = patternSizeComponent("pattern_size_3");
    public static final ComponentType<TamanoPatron> RIGHT_PATTERN_SIZE_2 = patternSizeComponent("right_pattern_size_2");
    public static final ComponentType<TamanoPatron> RIGHT_PATTERN_SIZE_3 = patternSizeComponent("right_pattern_size_3");

    /**
     * Posición del patrón a lo largo del eje "d" del generador (0.0..1.0)
     * — ausente = 0.5 (medio). Solo la usa {@code Forma.TRES_RAYAS} por
     * ahora; el resto de las formas la ignora — a pedido (2026-09-19,
     * "quiero ver si le puedo cambiar la posicion" sobre triple_stripe).
     */
    private static ComponentType<Float> patternPositionComponent(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", nombre),
                ComponentType.<Float>builder()
                        .codec(Codec.FLOAT)
                        .packetCodec(PacketCodecs.FLOAT)
                        .build());
    }

    /**
     * Forma del patrón (§{@link com.modamod.render.PatronGenerador.Forma})
     * — ausente = la del ítem (§{@code ClothingPatternItem#forma}). A
     * pedido (2026-09-19, "que cualquier patron pueda elegir su forma"):
     * antes venía pegada al ítem, ahora es un eje más por capa, igual que
     * tamaño/orientación/posición.
     */
    private static ComponentType<com.modamod.render.PatronGenerador.Forma> patternFormComponent(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", nombre),
                ComponentType.<com.modamod.render.PatronGenerador.Forma>builder()
                        .codec(StringIdentifiable.createCodec(com.modamod.render.PatronGenerador.Forma::values))
                        .packetCodec(PacketCodecs.indexed(i -> com.modamod.render.PatronGenerador.Forma.values()[i], Enum::ordinal))
                        .build());
    }

    public static final ComponentType<com.modamod.render.PatronGenerador.Forma> PATTERN_FORM = patternFormComponent("pattern_form");
    public static final ComponentType<com.modamod.render.PatronGenerador.Forma> PATTERN_FORM_2 = patternFormComponent("pattern_form_2");
    public static final ComponentType<com.modamod.render.PatronGenerador.Forma> PATTERN_FORM_3 = patternFormComponent("pattern_form_3");
    public static final ComponentType<com.modamod.render.PatronGenerador.Forma> RIGHT_PATTERN_FORM = patternFormComponent("right_pattern_form");
    public static final ComponentType<com.modamod.render.PatronGenerador.Forma> RIGHT_PATTERN_FORM_2 = patternFormComponent("right_pattern_form_2");
    public static final ComponentType<com.modamod.render.PatronGenerador.Forma> RIGHT_PATTERN_FORM_3 = patternFormComponent("right_pattern_form_3");

    /**
     * A qué región anatómica se restringe la capa (2026-09-27, "pintar por
     * región" — mismo patrón bilateral que el resto de los ejes de capa).
     */
    private static ComponentType<com.modamod.region.RegionPintura> patternRegionComponent(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", nombre),
                ComponentType.<com.modamod.region.RegionPintura>builder()
                        .codec(StringIdentifiable.createCodec(com.modamod.region.RegionPintura::values))
                        .packetCodec(PacketCodecs.indexed(i -> com.modamod.region.RegionPintura.values()[i], Enum::ordinal))
                        .build());
    }

    /**
     * Capas de color de la Estación de Tintes, una por cuadradito fijado
     * (2026-09-27, "poner un color y patron al cuello otro a la manga otro
     * al pecho" + modos de mezcla y opacidad): lista en orden de pintado,
     * cada capa con su región, su molde opcional (sin molde = liso) y cómo
     * se funde con lo de abajo. Reemplaza, para lo que tiñe Tinturas, a los
     * 3 slots fijos PATTERN_* (que siguen existiendo para Telar/Modeladora).
     */
    public static final ComponentType<java.util.List<com.modamod.region.RegionResolver.CapaPatron>> CAPAS_TINTE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "capas_tinte"),
            ComponentType.<java.util.List<com.modamod.region.RegionResolver.CapaPatron>>builder()
                    .codec(com.modamod.region.RegionResolver.CapaPatron.CODEC.listOf())
                    .packetCodec(PacketCodecs.codec(com.modamod.region.RegionResolver.CapaPatron.CODEC.listOf()))
                    .build());

    public static final ComponentType<com.modamod.region.RegionPintura> PATTERN_REGION = patternRegionComponent("pattern_region");
    public static final ComponentType<com.modamod.region.RegionPintura> PATTERN_REGION_2 = patternRegionComponent("pattern_region_2");
    public static final ComponentType<com.modamod.region.RegionPintura> PATTERN_REGION_3 = patternRegionComponent("pattern_region_3");
    public static final ComponentType<com.modamod.region.RegionPintura> RIGHT_PATTERN_REGION = patternRegionComponent("right_pattern_region");
    public static final ComponentType<com.modamod.region.RegionPintura> RIGHT_PATTERN_REGION_2 = patternRegionComponent("right_pattern_region_2");
    public static final ComponentType<com.modamod.region.RegionPintura> RIGHT_PATTERN_REGION_3 = patternRegionComponent("right_pattern_region_3");

    public static final ComponentType<Float> PATTERN_POSITION = patternPositionComponent("pattern_position");
    public static final ComponentType<Float> PATTERN_POSITION_2 = patternPositionComponent("pattern_position_2");
    public static final ComponentType<Float> PATTERN_POSITION_3 = patternPositionComponent("pattern_position_3");
    public static final ComponentType<Float> RIGHT_PATTERN_POSITION = patternPositionComponent("right_pattern_position");
    public static final ComponentType<Float> RIGHT_PATTERN_POSITION_2 = patternPositionComponent("right_pattern_position_2");
    public static final ComponentType<Float> RIGHT_PATTERN_POSITION_3 = patternPositionComponent("right_pattern_position_3");

    /**
     * "Negativo" de la máscara del patrón — a pedido (2026-09-20, "invertir
     * los colores del patron"): donde la máscara pinta normalmente el
     * color del PATRÓN, invertido pinta el color BASE, y viceversa. Solo
     * presente cuando está prendido — ausente = false, mismo criterio que
     * el resto de los ejes de esta capa (no ocupa data extra en el caso
     * común). Es un booleano PROPIO por capa, no del patrón en sí: la
     * misma máscara sirve invertida o no.
     */
    private static ComponentType<Boolean> patternInvertComponent(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", nombre),
                ComponentType.<Boolean>builder()
                        .codec(Codec.BOOL)
                        .packetCodec(PacketCodecs.BOOL)
                        .build());
    }

    public static final ComponentType<Boolean> PATTERN_INVERT = patternInvertComponent("pattern_invert");
    public static final ComponentType<Boolean> PATTERN_INVERT_2 = patternInvertComponent("pattern_invert_2");
    public static final ComponentType<Boolean> PATTERN_INVERT_3 = patternInvertComponent("pattern_invert_3");
    public static final ComponentType<Boolean> RIGHT_PATTERN_INVERT = patternInvertComponent("right_pattern_invert");
    public static final ComponentType<Boolean> RIGHT_PATTERN_INVERT_2 = patternInvertComponent("right_pattern_invert_2");
    public static final ComponentType<Boolean> RIGHT_PATTERN_INVERT_3 = patternInvertComponent("right_pattern_invert_3");

    // --- Overrides de la pierna DERECHA ---
    // Ausentes = la derecha usa lo mismo que la izquierda, que es el caso
    // comun (par igual) y ademas mantiene validas las 16 recetas de crafteo,
    // que solo setean DYED_COLOR. Solo se escriben cuando el jugador elige
    // una pierna puntual en el telar.
    public static final ComponentType<Integer> RIGHT_DYED_COLOR = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "right_dyed_color"),
            ComponentType.<Integer>builder()
                    .codec(Codec.INT)
                    .packetCodec(PacketCodecs.INTEGER)
                    .build());

    public static final ComponentType<Identifier> RIGHT_PATTERN_ID = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "right_pattern_id"),
            ComponentType.<Identifier>builder()
                    .codec(Identifier.CODEC)
                    .packetCodec(Identifier.PACKET_CODEC)
                    .build());

    public static final ComponentType<Integer> RIGHT_PATTERN_COLOR = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "right_pattern_color"),
            ComponentType.<Integer>builder()
                    .codec(Codec.INT)
                    .packetCodec(PacketCodecs.INTEGER)
                    .build());

    public static final ComponentType<TamanoPatron> RIGHT_PATTERN_SIZE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "right_pattern_size"),
            ComponentType.<TamanoPatron>builder()
                    .codec(StringIdentifiable.createCodec(TamanoPatron::values))
                    .packetCodec(PacketCodecs.indexed(i -> TamanoPatron.values()[i], Enum::ordinal))
                    .build());

    /**
     * Como esta puesta la prenda: girada (frente/espalda) y/o espejada
     * (izquierda/derecha).
     *
     * Ausente = normal, y se BORRA al volver a la normal en vez de guardarse
     * con los dos bits en false: una prenda sin girar tiene que apilar con
     * otra igual, y un componente presente no apila contra uno ausente.
     *
     * No permuta nada guardado — lo aplica RegionResolver al resolver, asi
     * que sacar el flag devuelve la prenda a como estaba.
     */
    public static final ComponentType<Orientacion> ORIENTACION = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "orientacion"),
            ComponentType.<Orientacion>builder()
                    .codec(Orientacion.CODEC)
                    .packetCodec(Orientacion.PACKET_CODEC)
                    .build());

    // PANTALON_LARGO (un solo valor) se retiró: la cobertura de extremidad
    // ahora es DOS anclajes que se intersecan (ver PANTALON_LARGO_SUPERIOR/
    // _INFERIOR más abajo) — mismo criterio de retiro que CALIENTABRAZOS_TIRO,
    // un mundo viejo con el componente guardado lo ignora sin romperse.
    //
    // 2026-09-23: PantalonLargo (el TIPO del componente) se retiró también,
    // reemplazado por Botamanga (compartido con MediasLargo, ver esa
    // clase) — el pantalón deja de usar el anclaje SUPERIOR de acá en más
    // (ver PantalonItem#filasVisibles), pero el componente queda
    // registrado igual por si un mundo viejo lo tiene guardado.

    private static ComponentType<Botamanga> botamangaComponent(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", nombre),
                ComponentType.<Botamanga>builder()
                        .codec(StringIdentifiable.createCodec(Botamanga::values))
                        .packetCodec(PacketCodecs.indexed(i -> Botamanga.values()[i], Enum::ordinal))
                        .build());
    }

    /**
     * Anclaje SUPERIOR de cobertura del pantalón (cintura hacia abajo) y su
     * override de la pierna derecha. Ausente = {@code PIE} (completo). Ya
     * no se escribe desde la GUI (2026-09-23, "pantalones se fija solo el
     * corte inferior") — queda registrado por compatibilidad de guardado.
     * La tela final es la INTERSECCIÓN con {@link #PANTALON_LARGO_INFERIOR}
     * — ver {@link PantalonItem#filasVisibles}.
     */
    public static final ComponentType<Botamanga> PANTALON_LARGO_SUPERIOR = botamangaComponent("pantalon_largo_superior");
    public static final ComponentType<Botamanga> RIGHT_PANTALON_LARGO_SUPERIOR = botamangaComponent("right_pantalon_largo_superior");

    /** Anclaje INFERIOR (tobillo hacia arriba). Ausente = {@code PIE} (completo). */
    public static final ComponentType<Botamanga> PANTALON_LARGO_INFERIOR = botamangaComponent("pantalon_largo_inferior");
    public static final ComponentType<Botamanga> RIGHT_PANTALON_LARGO_INFERIOR = botamangaComponent("right_pantalon_largo_inferior");

    /**
     * El tiro del pantalón (§{@link PantalonTiro}): cuánto sube la cintura
     * sobre el torso. Eje independiente de {@code PANTALON_LARGO} — ausente
     * = {@code MEDIO}, la cintura natural.
     */
    public static final ComponentType<PantalonTiro> PANTALON_TIRO = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "pantalon_tiro"),
            ComponentType.<PantalonTiro>builder()
                    .codec(StringIdentifiable.createCodec(PantalonTiro::values))
                    .packetCodec(PacketCodecs.indexed(i -> PantalonTiro.values()[i], Enum::ordinal))
                    .build());

    /**
     * Calce (§{@link Calce}): cuán ajustada/voluminosa se ve la prenda —
     * transversal a las 4 (remera/pantalón/medias/calientabrazos), un solo
     * componente compartido. Ausente = {@code NORMAL}.
     */
    public static final ComponentType<Calce> CALCE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "calce"),
            ComponentType.<Calce>builder()
                    .codec(StringIdentifiable.createCodec(Calce::values))
                    .packetCodec(PacketCodecs.indexed(i -> Calce.values()[i], Enum::ordinal))
                    .build());

    /**
     * Corte de red (§{@link PatronRed}): agujerea la tela ya compuesta —
     * transversal a las 4 prendas con corte, mismo criterio que
     * {@code CALCE}. Ausente = sin red (tela lisa, sin agujeros).
     */
    public static final ComponentType<PatronRed> PATRON_RED = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "patron_red"),
            ComponentType.<PatronRed>builder()
                    .codec(StringIdentifiable.createCodec(PatronRed::values))
                    .packetCodec(PacketCodecs.indexed(i -> PatronRed.values()[i], Enum::ordinal))
                    .build());

    // MEDIAS_LARGO (un solo valor) se retiró — mismo criterio que
    // PANTALON_LARGO arriba: ahora son dos anclajes que se intersecan.
    // 2026-09-23: el TIPO también pasó de MediasLargo a Botamanga
    // (compartido con pantalón, ver esa clase) — medias sigue con los DOS
    // anclajes, sin cambios de comportamiento acá.

    /**
     * Anclaje SUPERIOR de la media (muslo hacia abajo) — eje nuevo, las
     * medias antes solo anclaban desde abajo. Ausente = {@code PIE}
     * (completo, hasta el final del rango — el largo real de siempre).
     */
    public static final ComponentType<Botamanga> MEDIAS_LARGO_SUPERIOR = botamangaComponent("medias_largo_superior");
    public static final ComponentType<Botamanga> RIGHT_MEDIAS_LARGO_SUPERIOR = botamangaComponent("right_medias_largo_superior");

    /** Anclaje INFERIOR (tobillo hacia arriba, el que ya existía). Ausente = {@code PIE}. */
    public static final ComponentType<Botamanga> MEDIAS_LARGO_INFERIOR = botamangaComponent("medias_largo_inferior");
    public static final ComponentType<Botamanga> RIGHT_MEDIAS_LARGO_INFERIOR = botamangaComponent("right_medias_largo_inferior");

    // CALIENTABRAZOS_COBERTURA (un solo valor) se retiró — mismo criterio.

    private static ComponentType<Variante.Manga> calientabrazosCoberturaComponent(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of("modamod", nombre),
                ComponentType.<Variante.Manga>builder()
                        .codec(StringIdentifiable.createCodec(Variante.Manga::values))
                        .packetCodec(PacketCodecs.indexed(i -> Variante.Manga.values()[i], Enum::ordinal))
                        .build());
    }

    /**
     * Cobertura de calientabrazos (§{@link com.modamod.sublimadora.Variante.Manga}),
     * ahora dos anclajes que se intersecan igual que pantalón/medias.
     * Anclaje SUPERIOR = hombro hacia abajo (el que ya existía, reusa el
     * mismo molde cíclico que la manga de la remera, `MoldeItem.Eje.MANGA`,
     * pero en un componente propio: no se pisa con la manga de una remera
     * puesta). Ausente = {@code LARGA} (completo).
     */
    public static final ComponentType<Variante.Manga> CALIENTABRAZOS_COBERTURA_SUPERIOR = calientabrazosCoberturaComponent("calientabrazos_cobertura_superior");
    public static final ComponentType<Variante.Manga> RIGHT_CALIENTABRAZOS_COBERTURA_SUPERIOR = calientabrazosCoberturaComponent("right_calientabrazos_cobertura_superior");

    /** Anclaje INFERIOR (muñeca hacia arriba) — eje nuevo. Ausente = {@code LARGA}. */
    public static final ComponentType<Variante.Manga> CALIENTABRAZOS_COBERTURA_INFERIOR = calientabrazosCoberturaComponent("calientabrazos_cobertura_inferior");
    public static final ComponentType<Variante.Manga> RIGHT_CALIENTABRAZOS_COBERTURA_INFERIOR = calientabrazosCoberturaComponent("right_calientabrazos_cobertura_inferior");

    /**
     * Override de la manga DERECHA de la remera (2026-09-24, "vamos con
     * mangas distintas") — la izquierda sigue viviendo en el {@code
     * Variante} de siempre ({@code ModItems.VARIANTE}, sin cambios); esto
     * es SOLO el override, mismo criterio primario-izquierda/override-
     * derecha que {@link #RIGHT_DYED_COLOR}, no un par Superior/Inferior
     * como calientabrazos. Ausente = hereda el valor izquierdo — ver
     * {@code RemeraItem#manga}.
     */
    public static final ComponentType<Variante.Manga> RIGHT_REMERA_MANGA = calientabrazosCoberturaComponent("right_remera_manga");

    // CALIENTABRAZOS_TIRO se retiró (ver docs/archivo/MAQUINAS.md §"Categorías de
    // patrones de modelado"): tiro es exclusivo de prendas inferiores, no
    // de cubrebrazos. Si un mundo viejo tiene el componente guardado, el
    // codec ya no está registrado y Minecraft simplemente lo ignora al leer
    // -no rompe el guardado, el dato queda huérfano sin efecto.

    /**
     * Varios ejes de forma combinados de la Mesa de Modelado (las fijadas y los
     * diseños de corte; el viejo Molde de corte se sacó el 2026-10-25). Ver
     * {@link com.modamod.modelado.ComboCorte}.
     */
    public static final ComponentType<com.modamod.modelado.ComboCorte> COMBO_CORTE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "combo_corte"),
            ComponentType.<com.modamod.modelado.ComboCorte>builder()
                    .codec(com.modamod.modelado.ComboCorte.CODEC)
                    .packetCodec(com.modamod.modelado.ComboCorte.PACKET_CODEC)
                    .build());

    /** Largo de la pollera (§{@link PolleraLargo}); ausente = MEDIO. */
    public static final ComponentType<PolleraLargo> POLLERA_LARGO = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "pollera_largo"),
            ComponentType.<PolleraLargo>builder()
                    .codec(StringIdentifiable.createCodec(PolleraLargo::values))
                    .packetCodec(PacketCodecs.indexed(i -> PolleraLargo.values()[i], Enum::ordinal))
                    .build());

    /**
     * Faldón de un Top (2026-10-11, "las chaquetas de malla"): una falda de malla colgando de la cintura, con el largo
     * del nivel de rango 0..6 (0 = el más corto); ausente = sin faldón.
     */
    public static final ComponentType<Integer> FALDON_TOP = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "faldon_top"),
            ComponentType.<Integer>builder()
                    .codec(com.mojang.serialization.Codec.intRange(0, 6))
                    .packetCodec(PacketCodecs.VAR_INT)
                    .build());

    /** Cintura de la pollera (2026-10-08): nivel de rango 0..6 (altura de la cintura = 2 × nivel px bajo el hombro); ausente = cadera. */
    public static final ComponentType<Integer> POLLERA_CINTURA = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "pollera_cintura"),
            ComponentType.<Integer>builder()
                    .codec(com.mojang.serialization.Codec.intRange(0, 6))
                    .packetCodec(PacketCodecs.VAR_INT)
                    .build());

    /** Volado del borde de abajo / de toda la pollera (2026-10-05); ausente = sin volado. */
    public static final ComponentType<PolleraVolado> POLLERA_VOLADO_RUEDO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "pollera_volado_ruedo"),
            ComponentType.<PolleraVolado>builder()
                    .codec(StringIdentifiable.createCodec(PolleraVolado::values))
                    .packetCodec(PacketCodecs.indexed(i -> PolleraVolado.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<PolleraVolado> POLLERA_VOLADO_TODO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "pollera_volado_todo"),
            ComponentType.<PolleraVolado>builder()
                    .codec(StringIdentifiable.createCodec(PolleraVolado::values))
                    .packetCodec(PacketCodecs.indexed(i -> PolleraVolado.values()[i], Enum::ordinal))
                    .build());

    /** Delantal (2026-10-11): nivel de rango 0..6 de dónde arranca (1 + 1,5·n px) y hasta dónde llega (9 + 2,5·n px). */
    public static final ComponentType<Integer> DELANTAL_INICIO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "delantal_inicio"),
            ComponentType.<Integer>builder()
                    .codec(com.mojang.serialization.Codec.intRange(0, 6))
                    .packetCodec(PacketCodecs.VAR_INT)
                    .build());
    public static final ComponentType<Integer> DELANTAL_LARGO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "delantal_largo"),
            ComponentType.<Integer>builder()
                    .codec(com.mojang.serialization.Codec.intRange(0, 6))
                    .packetCodec(PacketCodecs.VAR_INT)
                    .build());
    /** Volados de los costados y del borde de arriba del delantal (2026-10-11); ausente = sin volado. */
    public static final ComponentType<PolleraVolado> DELANTAL_VOLADO_LATERAL = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "delantal_volado_lateral"),
            ComponentType.<PolleraVolado>builder()
                    .codec(StringIdentifiable.createCodec(PolleraVolado::values))
                    .packetCodec(PacketCodecs.indexed(i -> PolleraVolado.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<PolleraVolado> DELANTAL_VOLADO_SUPERIOR = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "delantal_volado_superior"),
            ComponentType.<PolleraVolado>builder()
                    .codec(StringIdentifiable.createCodec(PolleraVolado::values))
                    .packetCodec(PacketCodecs.indexed(i -> PolleraVolado.values()[i], Enum::ordinal))
                    .build());
    /** Silueta del delantal; ausente = RECTO. */
    public static final ComponentType<DelantalForma> DELANTAL_FORMA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "delantal_forma"),
            ComponentType.<DelantalForma>builder()
                    .codec(StringIdentifiable.createCodec(DelantalForma::values))
                    .packetCodec(PacketCodecs.indexed(i -> DelantalForma.values()[i], Enum::ordinal))
                    .build());
    /** Tirantes del delantal; ausente = sin. */
    public static final ComponentType<DelantalTirantes> DELANTAL_TIRANTES = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "delantal_tirantes"),
            ComponentType.<DelantalTirantes>builder()
                    .codec(StringIdentifiable.createCodec(DelantalTirantes::values))
                    .packetCodec(PacketCodecs.indexed(i -> DelantalTirantes.values()[i], Enum::ordinal))
                    .build());
    /** Cobertura del delantal; ausente = ENVOLVENTE. */
    public static final ComponentType<DelantalCobertura> DELANTAL_COBERTURA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "delantal_cobertura"),
            ComponentType.<DelantalCobertura>builder()
                    .codec(StringIdentifiable.createCodec(DelantalCobertura::values))
                    .packetCodec(PacketCodecs.indexed(i -> DelantalCobertura.values()[i], Enum::ordinal))
                    .build());
    /** Embozo de la capa (2026-10-11): nivel de rango 0..6 = hasta dónde baja sobre los hombros; ausente = sin embozo. */
    public static final ComponentType<Integer> CAPA_EMBOZO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "capa_embozo"),
            ComponentType.<Integer>builder().codec(com.mojang.serialization.Codec.intRange(0, 6)).packetCodec(PacketCodecs.VAR_INT).build());
    /** Borde del embozo (cinta alrededor del contorno, cerrando por el frente); ausente = sin borde. */
    public static final ComponentType<Boolean> CAPA_BORDE_EMBOZO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "capa_borde_embozo"),
            ComponentType.<Boolean>builder().codec(com.mojang.serialization.Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());
    /** Esquinas de abajo del delantal (solo se guarda REDONDEADO). */
    public static final ComponentType<CapaRuedo> DELANTAL_ESQUINAS = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "delantal_esquinas"),
            ComponentType.<CapaRuedo>builder()
                    .codec(StringIdentifiable.createCodec(CapaRuedo::values))
                    .packetCodec(PacketCodecs.indexed(i -> CapaRuedo.values()[i], Enum::ordinal))
                    .build());
    /** Volado del ruedo del delantal (los de la pollera); ausente = sin volado. */
    public static final ComponentType<PolleraVolado> DELANTAL_VOLADO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "delantal_volado"),
            ComponentType.<PolleraVolado>builder()
                    .codec(StringIdentifiable.createCodec(PolleraVolado::values))
                    .packetCodec(PacketCodecs.indexed(i -> PolleraVolado.values()[i], Enum::ordinal))
                    .build());

    /** Forma de la pollera (§{@link PolleraForma}); ausente = CAMPANA. */
    public static final ComponentType<PolleraForma> POLLERA_FORMA = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "pollera_forma"),
            ComponentType.<PolleraForma>builder()
                    .codec(StringIdentifiable.createCodec(PolleraForma::values))
                    .packetCodec(PacketCodecs.indexed(i -> PolleraForma.values()[i], Enum::ordinal))
                    .build());

    // ── capa (2026-09-29) — ausentes = Medio / Recto / sin capucha / sin cuello ──
    public static final ComponentType<CapaLargo> CAPA_LARGO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "capa_largo"),
            ComponentType.<CapaLargo>builder()
                    .codec(StringIdentifiable.createCodec(CapaLargo::values))
                    .packetCodec(PacketCodecs.indexed(i -> CapaLargo.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<CapaRuedo> CAPA_RUEDO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "capa_ruedo"),
            ComponentType.<CapaRuedo>builder()
                    .codec(StringIdentifiable.createCodec(CapaRuedo::values))
                    .packetCodec(PacketCodecs.indexed(i -> CapaRuedo.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<Boolean> CAPA_CAPUCHA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "capa_capucha"),
            ComponentType.<Boolean>builder().codec(com.mojang.serialization.Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());
    public static final ComponentType<Boolean> CAPA_CUELLO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "capa_cuello"),
            ComponentType.<Boolean>builder().codec(com.mojang.serialization.Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());

    /**
     * Capucha puesta en la cabeza (true) o caída en la espalda (ausente/false)
     * de una chaqueta (2026-09-30, hoodie "con tecla para subir/bajar"). Vive
     * en el stack equipado: el servidor la cambia (ver {@code util/RedCapucha})
     * y Trinkets la sincroniza a todos.
     */
    public static final ComponentType<Boolean> CAPUCHA_ARRIBA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "capucha_arriba"),
            ComponentType.<Boolean>builder().codec(com.mojang.serialization.Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());

    /**
     * Frente abierto o cerrado AHORA (2026-10-11, "un keybind para abrir y cerrar la chaqueta"): true = abierto,
     * false = cerrado, ausente = como dice el corte ({@link #CHAQUETA_FRENTE}). Lo cambia el servidor con la tecla
     * (ver {@code util/RedFrente}) y Trinkets lo sincroniza, igual que {@link #CAPUCHA_ARRIBA}.
     */
    public static final ComponentType<Boolean> FRENTE_AHORA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "frente_ahora"),
            ComponentType.<Boolean>builder().codec(com.mojang.serialization.Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());

    /** Frente de la chaqueta (2026-10-07); ausente = cerrada. */
    public static final ComponentType<ChaquetaFrente> CHAQUETA_FRENTE = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "chaqueta_frente"),
            ComponentType.<ChaquetaFrente>builder()
                    .codec(StringIdentifiable.createCodec(ChaquetaFrente::values))
                    .packetCodec(PacketCodecs.indexed(i -> ChaquetaFrente.values()[i], Enum::ordinal))
                    .build());
    /** Ruedo de cada borde libre de la prenda (2026-10-07, {@link Ruedos}); lo ausente vale el de fábrica. */
    public static final ComponentType<java.util.Map<ZonaRuedo, Ruedo>> RUEDOS = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "ruedos"),
            ComponentType.<java.util.Map<ZonaRuedo, Ruedo>>builder()
                    .codec(Ruedos.CODEC)
                    .packetCodec(PacketCodecs.codec(Ruedos.CODEC))
                    .build());
    /** Solapas de la chaqueta (2026-10-07); ausente = ninguna. */
    public static final ComponentType<ChaquetaSolapa> CHAQUETA_SOLAPA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "chaqueta_solapa"),
            ComponentType.<ChaquetaSolapa>builder()
                    .codec(StringIdentifiable.createCodec(ChaquetaSolapa::values))
                    .packetCodec(PacketCodecs.indexed(i -> ChaquetaSolapa.values()[i], Enum::ordinal))
                    .build());
    /** El Top con capucha (2026-10-07, "un solo top"): solo se guarda cuando es true; ausente = sin capucha. */
    public static final ComponentType<Boolean> CON_CAPUCHA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "con_capucha"),
            ComponentType.<Boolean>builder().codec(com.mojang.serialization.Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());
    /** Marca de la copia que se dibuja en la capa exterior (el Top llevado en el slot de chaqueta); no se guarda en la prenda. */
    public static final ComponentType<Boolean> CAPA_EXTERIOR = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "capa_exterior"),
            ComponentType.<Boolean>builder().codec(com.mojang.serialization.Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());

    /** El contenido de un ítem "Diseño de sublimado" (2026-10-13): ajuste de las dos caras, fijadas y simetría. */
    public static final ComponentType<net.minecraft.nbt.NbtCompound> DISENO_SUBLIMADO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "diseno_sublimado"),
            ComponentType.<net.minecraft.nbt.NbtCompound>builder()
                    .codec(net.minecraft.nbt.NbtCompound.CODEC)
                    .packetCodec(PacketCodecs.codec(net.minecraft.nbt.NbtCompound.CODEC))
                    .build());

    /** El contenido de un ítem "Diseño de estilo" de la Estilista (2026-10-13): la prenda, su diseño codificado y un resumen. */
    public static final ComponentType<net.minecraft.nbt.NbtCompound> DISENO_ESTILO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "diseno_estilo"),
            ComponentType.<net.minecraft.nbt.NbtCompound>builder()
                    .codec(net.minecraft.nbt.NbtCompound.CODEC)
                    .packetCodec(PacketCodecs.codec(net.minecraft.nbt.NbtCompound.CODEC))
                    .build());

    /** El contenido de un ítem "Diseño de tinte" de la Estación de Tintes (2026-10-12): categoría, cuadraditos y orden. */
    public static final ComponentType<net.minecraft.nbt.NbtCompound> DISENO_TINTE = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "diseno_tinte"),
            ComponentType.<net.minecraft.nbt.NbtCompound>builder()
                    .codec(net.minecraft.nbt.NbtCompound.CODEC)
                    .packetCodec(PacketCodecs.codec(net.minecraft.nbt.NbtCompound.CODEC))
                    .build());

    /** El contenido de un ítem "Diseño de corte" de la Modeladora (2026-10-12) — ver {@code modelado/DisenoCorte}. */
    public static final ComponentType<com.modamod.modelado.DisenoCorte> DISENO_CORTE = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "diseno_corte"),
            ComponentType.<com.modamod.modelado.DisenoCorte>builder()
                    .codec(com.modamod.modelado.DisenoCorte.CODEC)
                    .packetCodec(PacketCodecs.registryCodec(com.modamod.modelado.DisenoCorte.CODEC))
                    .build());

    /** La plantilla de un molde de aplique personalizado (2026-10-04) — ver {@code aplique/MoldeApliquePersonalizadoItem}. */
    public static final ComponentType<com.modamod.aplique.Aplique> APLIQUE_PLANTILLA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "aplique_plantilla"),
            ComponentType.<com.modamod.aplique.Aplique>builder()
                    .codec(com.modamod.aplique.Aplique.CODEC)
                    .packetCodec(PacketCodecs.registryCodec(com.modamod.aplique.Aplique.CODEC))
                    .build());

    /** Apliques puestos en una prenda (2026-10-01, Mesa de estilado) — ver {@code aplique/Aplique}. */
    public static final ComponentType<java.util.List<com.modamod.aplique.Aplique>> APLIQUES = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "apliques"),
            ComponentType.<java.util.List<com.modamod.aplique.Aplique>>builder()
                    .codec(com.modamod.aplique.Aplique.CODEC.listOf())
                    .packetCodec(PacketCodecs.registryCodec(com.modamod.aplique.Aplique.CODEC.listOf()))
                    .build());

    /** Correas libres puestas en una prenda o wearable (2026-10-05) — ver {@code correa/Correa}. */
    public static final ComponentType<java.util.List<com.modamod.correa.Correa>> CORREAS = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "correas"),
            ComponentType.<java.util.List<com.modamod.correa.Correa>>builder()
                    .codec(com.modamod.correa.Correa.CODEC.listOf())
                    .packetCodec(PacketCodecs.registryCodec(com.modamod.correa.Correa.CODEC.listOf()))
                    .build());

    /** Los 3 colores de zona (RGB) de un retazo de aplique (2026-10-01). */
    /**
     * Volumen propio de la tela (2026-10-01, relieve) — ver {@link TexturaTela}.
     * Se pone con un Molde de textura en la Mesa de estilado.
     */
    public static final ComponentType<TexturaTela> TEXTURA_TELA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "textura_tela"),
            ComponentType.<TexturaTela>builder().codec(TexturaTela.CODEC)
                    .packetCodec(PacketCodecs.VAR_INT.xmap(i -> TexturaTela.values()[i], TexturaTela::ordinal)).build());

    /**
     * Acabado de tela con un trim (2026-10-06, "los trims puedan agregar a las prendas texturas animadas, brillo,
     * policromatismo, reflejos"): el id del patrón de trim (ej. {@code minecraft:snout}); el efecto sale del patrón
     * ({@code render.EfectoTrim}). Se pone en la Mesa de estilado / Estilista con un molde de trim de vanilla.
     */
    public static final ComponentType<Identifier> ACABADO_TRIM = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "acabado_trim"),
            ComponentType.<Identifier>builder().codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC).build());

    /**
     * Material del acabado (2026-10-06, "el material por separado, opcional"): el id del material de trim de vanilla
     * (ej. {@code minecraft:gold}); cambia la paleta del efecto. Ausente = la paleta de fábrica del patrón.
     */
    public static final ComponentType<Identifier> ACABADO_MATERIAL = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "acabado_material"),
            ComponentType.<Identifier>builder().codec(Identifier.CODEC).packetCodec(Identifier.PACKET_CODEC).build());

    public static final ComponentType<java.util.List<Integer>> COLORES_APLIQUE = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "colores_aplique"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
                    .build());

    // ── sombrero de bruja (2026-10-04) — ausentes = ala ancha / punta recta / colores de fábrica ──
    public static final ComponentType<SombreroAla> SOMBRERO_ALA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "sombrero_ala"),
            ComponentType.<SombreroAla>builder()
                    .codec(StringIdentifiable.createCodec(SombreroAla::values))
                    .packetCodec(PacketCodecs.indexed(i -> SombreroAla.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<SombreroPunta> SOMBRERO_PUNTA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "sombrero_punta"),
            ComponentType.<SombreroPunta>builder()
                    .codec(StringIdentifiable.createCodec(SombreroPunta::values))
                    .packetCodec(PacketCodecs.indexed(i -> SombreroPunta.values()[i], Enum::ordinal))
                    .build());
    /** La planta del ala plana (2026-10-14): ausente = redonda. */
    public static final ComponentType<SombreroPlanta> SOMBRERO_PLANTA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "sombrero_planta"),
            ComponentType.<SombreroPlanta>builder()
                    .codec(StringIdentifiable.createCodec(SombreroPlanta::values))
                    .packetCodec(PacketCodecs.indexed(i -> SombreroPlanta.values()[i], Enum::ordinal))
                    .build());
    /** Malla o cuboides (2026-10-14): ausente = malla. */
    public static final ComponentType<SombreroEstilo> SOMBRERO_ESTILO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "sombrero_estilo"),
            ComponentType.<SombreroEstilo>builder()
                    .codec(StringIdentifiable.createCodec(SombreroEstilo::values))
                    .packetCodec(PacketCodecs.indexed(i -> SombreroEstilo.values()[i], Enum::ordinal))
                    .build());
    /** Grosor de las alas (2026-10-14): ausente = fina. */
    public static final ComponentType<SombreroGrosor> SOMBRERO_GROSOR = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "sombrero_grosor"),
            ComponentType.<SombreroGrosor>builder()
                    .codec(StringIdentifiable.createCodec(SombreroGrosor::values))
                    .packetCodec(PacketCodecs.indexed(i -> SombreroGrosor.values()[i], Enum::ordinal))
                    .build());
    /** La banda del sombrero (2026-10-14, "molde para poner la banda, con el en blanco se va"): solo se guarda cuando es false; ausente = con banda. */
    public static final ComponentType<Boolean> SOMBRERO_BANDA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "sombrero_banda"),
            ComponentType.<Boolean>builder().codec(com.mojang.serialization.Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());
    public static final ComponentType<java.util.List<Integer>> COLORES_SOMBRERO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "colores_sombrero"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
                    .build());

    /** Banda (2026-10-05, cintos y chokers): zona, ancho, herraje, colores y dibujo de sus 3 partes. */
    public static final ComponentType<BandaZona> BANDA_ZONA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "banda_zona"),
            ComponentType.<BandaZona>builder()
                    .codec(StringIdentifiable.createCodec(BandaZona::values))
                    .packetCodec(PacketCodecs.indexed(i -> BandaZona.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<BandaAncho> BANDA_ANCHO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "banda_ancho"),
            ComponentType.<BandaAncho>builder()
                    .codec(StringIdentifiable.createCodec(BandaAncho::values))
                    .packetCodec(PacketCodecs.indexed(i -> BandaAncho.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<BandaHerraje> BANDA_HERRAJE = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "banda_herraje"),
            ComponentType.<BandaHerraje>builder()
                    .codec(StringIdentifiable.createCodec(BandaHerraje::values))
                    .packetCodec(PacketCodecs.indexed(i -> BandaHerraje.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<java.util.List<Integer>> COLORES_BANDA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "colores_banda"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
                    .build());
    public static final ComponentType<java.util.List<Integer>> PATRONES_BANDA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "patrones_banda"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
                    .build());

    /** Borcegos (2026-10-08, slot de calzado): altura de la caña, suela, cómo se lleva el pantalón, colores y dibujo de sus 3 zonas. */
    public static final ComponentType<BorcegoCana> BORCEGO_CANA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_cana"),
            ComponentType.<BorcegoCana>builder()
                    .codec(StringIdentifiable.createCodec(BorcegoCana::values))
                    .packetCodec(PacketCodecs.indexed(i -> BorcegoCana.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<BorcegoSuela> BORCEGO_SUELA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_suela"),
            ComponentType.<BorcegoSuela>builder()
                    .codec(StringIdentifiable.createCodec(BorcegoSuela::values))
                    .packetCodec(PacketCodecs.indexed(i -> BorcegoSuela.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<BorcegoBotamanga> BORCEGO_BOTAMANGA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_botamanga"),
            ComponentType.<BorcegoBotamanga>builder()
                    .codec(StringIdentifiable.createCodec(BorcegoBotamanga::values))
                    .packetCodec(PacketCodecs.indexed(i -> BorcegoBotamanga.values()[i], Enum::ordinal))
                    .build());
    /** Pie izquierdo (2026-10-11, "no se puede independizar derecho e izquierdo?"): ausente = igual que el derecho. */
    public static final ComponentType<BorcegoCana> BORCEGO_CANA_IZQ = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_cana_izq"),
            ComponentType.<BorcegoCana>builder()
                    .codec(StringIdentifiable.createCodec(BorcegoCana::values))
                    .packetCodec(PacketCodecs.indexed(i -> BorcegoCana.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<BorcegoSuela> BORCEGO_SUELA_IZQ = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_suela_izq"),
            ComponentType.<BorcegoSuela>builder()
                    .codec(StringIdentifiable.createCodec(BorcegoSuela::values))
                    .packetCodec(PacketCodecs.indexed(i -> BorcegoSuela.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<BorcegoBotamanga> BORCEGO_BOTAMANGA_IZQ = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_botamanga_izq"),
            ComponentType.<BorcegoBotamanga>builder()
                    .codec(StringIdentifiable.createCodec(BorcegoBotamanga::values))
                    .packetCodec(PacketCodecs.indexed(i -> BorcegoBotamanga.values()[i], Enum::ordinal))
                    .build());
    /**
     * Cuboides o malla de la capucha del top y de la capa (2026-10-23, "metelas al mod con dos moldes nuevos para cambiar
     * entre cuboide o malla"); ausente = malla.
     */
    public static final ComponentType<SombreroEstilo> CAPUCHA_ESTILO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "capucha_estilo"),
            ComponentType.<SombreroEstilo>builder()
                    .codec(StringIdentifiable.createCodec(SombreroEstilo::values))
                    .packetCodec(PacketCodecs.indexed(i -> SombreroEstilo.values()[i], Enum::ordinal))
                    .build());
    /** Cuboides o malla del calzado (2026-10-14, "el mismo molde cuboide"): los dos pies juntos; ausente = malla. */
    public static final ComponentType<SombreroEstilo> BORCEGO_ESTILO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_estilo"),
            ComponentType.<SombreroEstilo>builder()
                    .codec(StringIdentifiable.createCodec(SombreroEstilo::values))
                    .packetCodec(PacketCodecs.indexed(i -> SombreroEstilo.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<CalzadoPunta> BORCEGO_PUNTA = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_punta"),
            ComponentType.<CalzadoPunta>builder()
                    .codec(StringIdentifiable.createCodec(CalzadoPunta::values))
                    .packetCodec(PacketCodecs.indexed(i -> CalzadoPunta.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<CalzadoPunta> BORCEGO_PUNTA_IZQ = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_punta_izq"),
            ComponentType.<CalzadoPunta>builder()
                    .codec(StringIdentifiable.createCodec(CalzadoPunta::values))
                    .packetCodec(PacketCodecs.indexed(i -> CalzadoPunta.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<CalzadoCierre> BORCEGO_CIERRE = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_cierre"),
            ComponentType.<CalzadoCierre>builder()
                    .codec(StringIdentifiable.createCodec(CalzadoCierre::values))
                    .packetCodec(PacketCodecs.indexed(i -> CalzadoCierre.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<CalzadoCierre> BORCEGO_CIERRE_IZQ = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_cierre_izq"),
            ComponentType.<CalzadoCierre>builder()
                    .codec(StringIdentifiable.createCodec(CalzadoCierre::values))
                    .packetCodec(PacketCodecs.indexed(i -> CalzadoCierre.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<CalzadoDetalle> BORCEGO_DETALLE = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_detalle"),
            ComponentType.<CalzadoDetalle>builder()
                    .codec(StringIdentifiable.createCodec(CalzadoDetalle::values))
                    .packetCodec(PacketCodecs.indexed(i -> CalzadoDetalle.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<CalzadoDetalle> BORCEGO_DETALLE_IZQ = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "borcego_detalle_izq"),
            ComponentType.<CalzadoDetalle>builder()
                    .codec(StringIdentifiable.createCodec(CalzadoDetalle::values))
                    .packetCodec(PacketCodecs.indexed(i -> CalzadoDetalle.values()[i], Enum::ordinal))
                    .build());
    public static final ComponentType<java.util.List<Integer>> COLORES_BORCEGOS_IZQ = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "colores_borcegos_izq"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
                    .build());
    public static final ComponentType<java.util.List<Integer>> PATRONES_BORCEGOS_IZQ = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "patrones_borcegos_izq"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
                    .build());
    public static final ComponentType<java.util.List<Integer>> COLORES_BORCEGOS = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "colores_borcegos"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
                    .build());
    public static final ComponentType<java.util.List<Integer>> PATRONES_BORCEGOS = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "patrones_borcegos"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
                    .build());

    /** Dibujo de cada zona del sombrero (2026-10-05): ordinales de {@link SombreroPatron}, ala/cono/cinta. */
    public static final ComponentType<java.util.List<Integer>> PATRONES_SOMBRERO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "patrones_sombrero"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
                    .build());

    /** La mezcla C/M/Y/K/T de una muestra de color (2026-09-30), 5 niveles de 0..20. */
    public static final ComponentType<java.util.List<Integer>> MEZCLA_COLOR = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "mezcla_color"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
                    .build());

    public static void init() {
        // fuerza class-loading
    }

    private ModamodComponents() {}

    /** La colección de outfits (de un Guardarropas) a la que está vinculado un Maletín (2026-10-21), ver {@code guardarropas/MaletinItem}. */
    public static final ComponentType<java.util.UUID> MALETIN_COLECCION = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "maletin_coleccion"),
            ComponentType.<java.util.UUID>builder()
                    .codec(net.minecraft.util.Uuids.CODEC)
                    .packetCodec(net.minecraft.util.Uuids.PACKET_CODEC)
                    .build());
}
