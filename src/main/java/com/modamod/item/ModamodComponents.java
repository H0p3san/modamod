package com.modamod.item;

import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Componentes de datos sueltos del mod. La pintura de una prenda es el {@code dyed_color} vanilla más
 * {@link #CAPAS_TINTE}; el corte vive en {@link com.modamod.prenda.Corte}.
 */
public final class ModamodComponents {

    /**
     * Capas de color de la Estación de Tintes, una por cuadradito fijado
     * (2026-09-27, "poner un color y patron al cuello otro a la manga otro
     * al pecho" + modos de mezcla y opacidad): lista en orden de pintado,
     * cada capa con su región, su molde opcional (sin molde = liso) y cómo
     * se funde con lo de abajo. Es toda la pintura de la prenda además del
     * color base; la región de cada capa es también su lado.
     */
    public static final ComponentType<java.util.List<com.modamod.region.RegionResolver.CapaPatron>> CAPAS_TINTE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of("modamod", "capas_tinte"),
            ComponentType.<java.util.List<com.modamod.region.RegionResolver.CapaPatron>>builder()
                    .codec(com.modamod.region.RegionResolver.CapaPatron.CODEC.listOf())
                    .packetCodec(PacketCodecs.codec(com.modamod.region.RegionResolver.CapaPatron.CODEC.listOf()))
                    .build());

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

    public static final ComponentType<java.util.List<Integer>> COLORES_BORCEGOS_IZQ = Registry.register(
            Registries.DATA_COMPONENT_TYPE, Identifier.of("modamod", "colores_borcegos_izq"),
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
