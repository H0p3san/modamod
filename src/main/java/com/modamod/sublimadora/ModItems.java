package com.modamod.sublimadora;

import com.modamod.Modamod;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

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
        return com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.REMERA) // la chaqueta también (2026-09-30)
                || com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.MEDIAS)
                || stack.getItem() == com.modamod.item.ModamodItems.PANTALON
                || stack.getItem() == com.modamod.item.ModamodItems.CALIENTABRAZOS
                // La pollera nueva (2026-09-29, "quiero poder... sublimarla"):
                // su tela es una caja de torso, ver EstampaTextures#POLLERA.
                || com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.POLLERA)
                || com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.CAPA)
                || com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.DELANTAL)
                // El banner (2026-10-06, "sublimadoras a banners y 16x"): una foto en el Frente, ver BannerHD.
                || stack.getItem() instanceof net.minecraft.item.BannerItem
                // Máscaras y barbijo (2026-10-24): una foto en el Frente, ver CaraRenderer.
                || stack.getItem() instanceof com.modamod.item.CaraItem
                // El cinto (2026-11-03): la placa es el frente y la correa la espalda, ver BandaRenderer.
                || (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.BANDA)
                        && com.modamod.item.BandaItem.zona(stack) == com.modamod.item.BandaZona.CINTURA)
                // Gorras con visera (2026-10-25): foto en el frente de la copa, ver SombreroRenderer.
                || (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.SOMBRERO)
                        && com.modamod.item.SombreroBrujaItem.ala(stack).esVisera());
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
