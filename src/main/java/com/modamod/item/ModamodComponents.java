package com.modamod.item;

import com.modamod.region.Orientacion;
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

    // MEDIAS_LARGO (un solo valor) se retiró — mismo criterio que
    // PANTALON_LARGO arriba: ahora son dos anclajes que se intersecan.
    // 2026-09-23: el TIPO también pasó de MediasLargo a Botamanga
    // (compartido con pantalón, ver esa clase) — medias sigue con los DOS
    // anclajes, sin cambios de comportamiento acá.

    // CALIENTABRAZOS_COBERTURA (un solo valor) se retiró — mismo criterio.

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

    // ── capa (2026-09-29) — ausentes = Medio / Recto / sin capucha / sin cuello ──

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
    public static final ComponentType<java.util.List<Integer>> COLORES_SOMBRERO = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "colores_sombrero"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(com.mojang.serialization.Codec.INT.listOf())
                    .packetCodec(PacketCodecs.VAR_INT.collect(PacketCodecs.toList()))
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
