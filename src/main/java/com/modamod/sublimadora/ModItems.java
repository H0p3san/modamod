package com.modamod.sublimadora;

import com.modamod.Modamod;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * La remera: en blanco al craftearla, estampada despues de pasar por la
 * sublimadora.
 *
 * Es UN solo item, no dos. Lo que cambia son los componentes de estampa: sin
 * ninguno esta en blanco, y puede tener frente, espalda o las dos. Se guarda
 * el UUID de la foto y no la imagen, que es lo que hace que esto escale — la
 * imagen ya vive en el almacenamiento de Camerapture, y el ItemStack pesa lo
 * mismo con o sin estampa.
 */
public final class ModItems {

    /** Foto estampada en el frente. Ausente = ese lado en blanco. */
    public static final ComponentType<Estampa> ESTAMPA_FRENTE = registrarEstampa("estampa_frente");
    /** Idem en la espalda. Estampar las dos caras cuesta dos pasadas. */
    public static final ComponentType<Estampa> ESTAMPA_ESPALDA = registrarEstampa("estampa_espalda");
    /**
     * Foto sublimada en un banner (2026-10-06, "solo tintes y sublimadoras a banners y 16x"): la dibuja
     * {@link BannerHD} a 16x sobre la cara del banner y, si el banner se usa en la receta del escudo, sobre el escudo.
     */
    public static final ComponentType<Estampa> BANNER_ESTAMPA = registrarEstampa("banner_estampa");

    /**
     * Las capas de estampa con máscara (2026-10-02, "mascaras de sublimacion...
     * varias layers 12 quizas entre frente y atras"): en orden, cada una pinta
     * encima de la anterior, después de las estampas de frente y espalda.
     */
    public static final ComponentType<java.util.List<CapaEstampa>> ESTAMPAS_CAPAS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Modamod.MOD_ID, "estampas_capas"),
            ComponentType.<java.util.List<CapaEstampa>>builder()
                    .codec(CapaEstampa.CODEC.listOf())
                    .packetCodec(net.minecraft.network.codec.PacketCodecs.codec(CapaEstampa.CODEC.listOf()))
                    .build());

    /** Los moldes de máscara, uno por forma (en el orden de {@link FormaMascara}). */
    public static final MoldeMascaraItem[] MOLDES_MASCARA = registrarMoldesMascara();

    private static MoldeMascaraItem[] registrarMoldesMascara() {
        FormaMascara[] formas = FormaMascara.values();
        MoldeMascaraItem[] out = new MoldeMascaraItem[formas.length];
        for (int i = 0; i < formas.length; i++) {
            out[i] = Registry.register(Registries.ITEM,
                    Identifier.of(Modamod.MOD_ID, "molde_mascara_" + formas[i].clave),
                    new MoldeMascaraItem(new Item.Settings().maxCount(1), formas[i]));
        }
        return out;
    }

    /**
     * El componente viejo, de cuando la estampa era un UUID pelado y siempre
     * iba centrada en el frente. Sigue registrado para poder leer las remeras
     * que ya existen en mundos guardados; nada lo escribe.
     */
    public static final ComponentType<UUID> PICTURE_ID = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Modamod.MOD_ID, "picture_id"),
            ComponentType.<UUID>builder()
                    .codec(Uuids.CODEC)
                    .packetCodec(Uuids.PACKET_CODEC)
                    .build());

    /**
     * Las cuatro cargas de tinta, para que viajen adentro del item cuando se
     * levanta la maquina. Cuatro enteros en orden C, M, Y, K.
     *
     * Es un componente y no NBT crudo porque asi lo copia la loot table sola
     * con minecraft:copy_components, sin codigo de por medio, y ademas se ve
     * en el tooltip como cualquier otro dato del item.
     */
    public static final ComponentType<java.util.List<Integer>> CARGAS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Modamod.MOD_ID, "cargas"),
            ComponentType.<java.util.List<Integer>>builder()
                    .codec(Codec.INT.listOf())
                    .packetCodec(net.minecraft.network.codec.PacketCodecs.INTEGER
                            .collect(net.minecraft.network.codec.PacketCodecs.toList()))
                    .build());

    /**
     * Papel cargado en el tanque (0..{@code SublimadoraBlockEntity#CARGA_MAXIMA})
     * — a pedido (2026-09-19, "usa papel y ya no consume la imagen"): la foto
     * pasa a ser reutilizable, y lo que se gasta por cada prensado es 1
     * papel de este tanque en vez de la foto misma. Mismo criterio que
     * {@link #CARGAS} para sobrevivir a romper el bloque.
     */
    public static final ComponentType<Integer> PAPEL_CARGADO = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Modamod.MOD_ID, "papel_cargado"),
            ComponentType.<Integer>builder()
                    .codec(Codec.INT)
                    .packetCodec(net.minecraft.network.codec.PacketCodecs.INTEGER)
                    .build());

    /** Que corte de prenda es. Ausente = remera comun. */
    public static final ComponentType<Variante> VARIANTE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Modamod.MOD_ID, "variante"),
            ComponentType.<Variante>builder()
                    .codec(Variante.CODEC)
                    .packetCodec(Variante.PACKET_CODEC)
                    .build());

    // maxCount(16): dos remeras con distinto corte/color/estampa tienen
    // distintos componentes, así que Minecraft nunca las apila entre sí de
    // todos modos -esto solo junta remeras REALMENTE iguales (recién
    // crafteadas, en blanco, mismo corte)-, conveniente para tener varias
    // de sobra. El telar/sublimadora ya toman de a 1 del stack
    // (garmentSlot.takeStack(1)), así que un stack >1 en el slot es seguro.
    public static final RemeraItem REMERA = Registry.register(
            Registries.ITEM,
            Identifier.of(Modamod.MOD_ID, "remera"),
            new RemeraItem(new Item.Settings().maxCount(16)));

    /**
     * Si la sublimadora puede imprimir sobre esto.
     *
     * Vive aca y no en EstampaTextures porque el bloque lo pregunta en el
     * servidor, y EstampaTextures es codigo de cliente: tocarlo del lado del
     * servidor cargaria NativeImage y MinecraftClient en un dedicado.
     */
    public static boolean esEstampable(net.minecraft.item.ItemStack stack) {
        // A pedido (2026-09-19, "hace todas las prendas sublimables") —
        // pantalón y calientabrazos se suman a remera/medias.
        return stack.getItem() instanceof RemeraItem // la chaqueta también (2026-09-30)
                || stack.getItem() == com.modamod.item.ModamodItems.SOCKS_SOLID
                || stack.getItem() == com.modamod.item.ModamodItems.PANTALON
                || stack.getItem() == com.modamod.item.ModamodItems.CALIENTABRAZOS
                // La pollera nueva (2026-09-29, "quiero poder... sublimarla"):
                // su tela es una caja de torso, ver EstampaTextures#POLLERA.
                || stack.getItem() instanceof com.modamod.item.PolleraItem
                || stack.getItem() instanceof com.modamod.item.CapaItem
                || stack.getItem() instanceof com.modamod.item.DelantalItem
                // El banner (2026-10-06, "sublimadoras a banners y 16x"): una foto en el Frente, ver BannerHD.
                || stack.getItem() instanceof net.minecraft.item.BannerItem
                // Máscaras y barbijo (2026-10-24): una foto en el Frente, ver CaraRenderer.
                || stack.getItem() instanceof com.modamod.item.CaraItem
                // Gorras con visera (2026-10-25): foto en el frente de la copa, ver SombreroRenderer.
                || (stack.getItem() instanceof com.modamod.item.SombreroBrujaItem
                        && com.modamod.item.SombreroBrujaItem.ala(stack).esVisera());
    }

    private static ComponentType<Estampa> registrarEstampa(String nombre) {
        return Registry.register(
                Registries.DATA_COMPONENT_TYPE,
                Identifier.of(Modamod.MOD_ID, nombre),
                ComponentType.<Estampa>builder()
                        .codec(Estampa.CODEC)
                        .packetCodec(Estampa.PACKET_CODEC)
                        .build());
    }

    /**
     * El molde de manga cíclico (único que queda de los cíclicos: el de
     * cuello y los de largo por valor se retiraron el 2026-09-28, "saca lo
     * que ya no se usa" — los reemplazan el molde de torso unificado y los
     * de cuello por valor).
     */
    public static final MoldeItem MOLDE_MANGA = molde("manga", MoldeItem.Eje.MANGA);

    /**
     * Moldes de cuello POR VALOR (no cíclicos) — a pedido, mismo cambio
     * que largo/manga. Ver {@link MoldeCuelloItem}.
     */
    public static final MoldeCuelloItem MOLDE_CUELLO_REDONDO =
            moldeCuello("molde_cuello_redondo", Variante.Cuello.REDONDO);
    public static final MoldeCuelloItem MOLDE_CUELLO_V =
            moldeCuello("molde_cuello_v", Variante.Cuello.V);
    public static final MoldeCuelloItem MOLDE_CUELLO_POLERA =
            moldeCuello("molde_cuello_polera", Variante.Cuello.POLERA);

    public static final MoldeCuelloItem MOLDE_CUELLO_CUADRADO =
            moldeCuello("molde_cuello_cuadrado", Variante.Cuello.CUADRADO);
    public static final MoldeCuelloItem MOLDE_CUELLO_CORAZON =
            moldeCuello("molde_cuello_corazon", Variante.Cuello.CORAZON);
    public static final MoldeCuelloItem MOLDE_CUELLO_CAMISA =
            moldeCuello("molde_cuello_camisa", Variante.Cuello.CAMISA);

    private static MoldeItem molde(String nombre, MoldeItem.Eje eje) {
        return Registry.register(Registries.ITEM,
                Identifier.of(Modamod.MOD_ID, "molde_" + nombre),
                new MoldeItem(new Item.Settings().maxCount(1), eje));
    }

    private static MoldeCuelloItem moldeCuello(String id, Variante.Cuello valor) {
        return Registry.register(Registries.ITEM,
                Identifier.of(Modamod.MOD_ID, id),
                new MoldeCuelloItem(new Item.Settings().maxCount(1), valor));
    }

    public static void register() {
        // fuerza class-loading
    }

    private ModItems() {}
}
