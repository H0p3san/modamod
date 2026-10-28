package com.modamod.client;

import com.modamod.item.ModamodItems;
import com.modamod.region.Lado;
import com.modamod.region.RegionResolver;
import com.modamod.render.ClothingTextureCache;
import com.modamod.render.GarmentFeatureRenderer;
import com.modamod.screen.ModamodScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

public class ModamodClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Falla al arrancar si la columna de la Estilista tiene controles superpuestos (2026-10-13, infraestructura de chequeo).
        DisposicionEstilista.autoverificar();
        DisposicionSublimadora.autoverificar();
        // Acabado con trim (2026-10-06): la prenda lo dice en su descripción.
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, contexto, tipo, lineas) -> {
            net.minecraft.util.Identifier trim = stack.get(com.modamod.item.ModamodComponents.ACABADO_TRIM);
            if (trim == null) return;
            net.minecraft.text.MutableText linea = net.minecraft.text.Text.translatable("modamod.acabado.tooltip",
                    net.minecraft.text.Text.translatable(
                            "modamod.efecto." + com.modamod.render.EfectoTrim.tipoDe(trim).name().toLowerCase()));
            net.minecraft.util.Identifier material = stack.get(com.modamod.item.ModamodComponents.ACABADO_MATERIAL);
            if (material != null) {
                linea.append(" · ").append(net.minecraft.text.Text.translatable(
                        "trim_material." + material.getNamespace() + "." + material.getPath()));
            }
            lineas.add(linea.formatted(net.minecraft.util.Formatting.LIGHT_PURPLE));
        });
        HandledScreens.register(ModamodScreenHandlers.MODELADO, ModeladoScreen::new);
        HandledScreens.register(ModamodScreenHandlers.TINTURAS, TinturasScreen::new);
        HandledScreens.register(ModamodScreenHandlers.SUBLIMADORA, SublimadoraScreen::new);
        HandledScreens.register(ModamodScreenHandlers.GUARDARROPAS, GuardarropasScreen::new);
        HandledScreens.register(ModamodScreenHandlers.MALETIN, MaletinScreen::new);
        // La lista de outfits de la colección abierta llega del servidor (al abrir y después de cada cambio).
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(com.modamod.guardarropas.OutfitsPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    if (context.player().currentScreenHandler instanceof com.modamod.guardarropas.VisorColeccion v) v.setDatos(payload.datos());
                }));
        HandledScreens.register(ModamodScreenHandlers.MANIQUI, ManiquiScreen::new);
        HandledScreens.register(ModamodScreenHandlers.ROPA, RopaScreen::new);
        HandledScreens.<com.modamod.telar.TelarScreenHandler, TelarScreen>register(ModamodScreenHandlers.TELAR, TelarScreen::new);
        HandledScreens.<com.modamod.estilado.EstiladoScreenHandler, EstilistaScreen>register(ModamodScreenHandlers.ESTILISTA,
                (h, inv, titulo) -> new EstilistaScreen((com.modamod.estilista.EstilistaScreenHandler) h, inv, titulo));
        HandledScreens.register(ModamodScreenHandlers.ESTILADO, EstiladoScreen::new);

        // Los 3 bloques del zip "Bloque de sublimadora Minecraft" (2026-09-30):
        // Guardarropas y Mesa de estilado con el GeoModel genérico; el
        // Maniquí con su renderer propio (dibuja además la ropa puesta).
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.guardarropas.GuardarropasMod.GUARDARROPAS_BLOCK_ENTITY,
                ctx -> new software.bernie.geckolib.renderer.GeoBlockRenderer<>(
                        new com.modamod.bloque.ModeloGeo<com.modamod.guardarropas.GuardarropasBlockEntity>("wardrobe")));
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.estilado.EstiladoMod.ESTILADO_BLOCK_ENTITY,
                ctx -> new software.bernie.geckolib.renderer.GeoBlockRenderer<>(
                        new com.modamod.bloque.ModeloGeo<com.modamod.estilado.EstiladoBlockEntity>("styling_table")));
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.maniqui.ManiquiMod.MANIQUI_BLOCK_ENTITY,
                com.modamod.maniqui.ManiquiRenderer::new);

        // Cinta transportadora (2026-10-04): la banda tiene huecos (alfa) en las curvas.
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.cinta.CintaMod.CINTA_BLOCK_ENTITY, com.modamod.cinta.CintaRenderer::new);
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.modamod.cinta.CintaMod.CINTA_BLOCK, net.minecraft.client.render.RenderLayer.getCutout());
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.cinta.CintaMod.EMPALME_BLOCK_ENTITY, com.modamod.cinta.EmpalmeRenderer::new);
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.modamod.cinta.CintaMod.EMPALME_BLOCK, net.minecraft.client.render.RenderLayer.getCutout());
        // Tubos de transporte (2026-10-20): el vidrio del caño tiene huecos (alfa).
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.cinta.CintaMod.TUBO_BLOCK_ENTITY, com.modamod.cinta.TuboRenderer.Tubo::new);
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.cinta.CintaMod.EMPALME_TUBO_BLOCK_ENTITY, com.modamod.cinta.TuboRenderer.Empalme::new);
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.modamod.cinta.CintaMod.TUBO_BLOCK, net.minecraft.client.render.RenderLayer.getTranslucent());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.modamod.cinta.CintaMod.EMPALME_TUBO_BLOCK, net.minecraft.client.render.RenderLayer.getCutout());
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.cinta.CintaMod.EXTRACTOR_BLOCK_ENTITY, com.modamod.cinta.TuboRenderer.Extractor::new);
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.modamod.cinta.CintaMod.EXTRACTOR_BLOCK, net.minecraft.client.render.RenderLayer.getCutout());

        // El render de la Mesa de Modelado lo hace GeckoLib desde el block
        // entity (modelo garment_shaper) — mismo patrón que la sublimadora.
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.modelado.ModeladoMod.MODELADO_BLOCK_ENTITY,
                ctx -> new com.modamod.modelado.ModeladoRenderer());
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.estilista.EstilistaMod.ESTILISTA_BLOCK_ENTITY, ctx -> new com.modamod.estilista.EstilistaRenderer());
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.telar.TelarMod.TELAR_BLOCK_ENTITY, ctx -> new com.modamod.telar.TelarRenderer());
        // garment_shaper_atlas.png tiene alfa (huecos reales entre piezas del
        // modelo) — sin cutout el recorte se rellena y queda un cuadrado.
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.modamod.modelado.ModeladoMod.MODELADO_BLOCK, net.minecraft.client.render.RenderLayer.getCutout());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.modamod.modelado.ModeladoMod.MODELADO_CREATIVA, net.minecraft.client.render.RenderLayer.getCutout());

        // El render de la Estación de Tintes lo hace GeckoLib desde el block
        // entity (modelo dye_station, assets reales bajados por el usuario).
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.tinturas.TinturasMod.TINTURAS_BLOCK_ENTITY,
                ctx -> new com.modamod.tinturas.TinturasRenderer());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.modamod.tinturas.TinturasMod.TINTURAS_BLOCK, net.minecraft.client.render.RenderLayer.getCutout());
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.modamod.tinturas.TinturasMod.TINTURAS_CREATIVA, net.minecraft.client.render.RenderLayer.getCutout());

        PiezasDelMod.init();
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.modamod.guardarropas.GuardarropasMod.MALETIN_BLOCK_ENTITY, MaletinBlockRenderer::new);
        // /modamoddebug solo en desarrollo o con -Dmodamod.debug=true (2026-10-22, "saquemos lo que no sirve asi lo
        // largamos como version 0.1.0 alpha"): el jugador comun no lo ve.
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("modamod.debug")) {
            DebugApariencia.init();
            PerfHud.init();
        }
        ElegirCuerpoCliente.init();
        TwirlCliente.init();
        ParticulasTrim.init();
        CapuchaCliente.init();
        FrenteCliente.init();
        RopaCliente.init();

        // TODA la ropa del mod se dibuja desde un solo feature renderer.
        //
        // Antes cada prenda se registraba en TrinketRendererRegistry y se
        // dibujaba sola. Eso alcanzaba con una prenda por parte del cuerpo,
        // pero el orden lo decidia Trinkets por el order de los slots: no
        // habia forma de decir "el short va arriba de la media". El ordinal
        // de capa necesita un unico punto de dibujo.
        //
        // LivingEntityFeatureRendererRegistrationCallback es un hook publico
        // de Fabric API — sigue sin hacer falta ningun Mixin para la ropa.
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (tipo, renderer, helper, ctx) -> {
                    if (renderer instanceof PlayerEntityRenderer jugador) {
                        helper.register(new GarmentFeatureRenderer<>(jugador));
                    }
                });

        // Icono: capa 0 = la prenda tenida con el color base, capa 1 (si el
        // modelo la tiene) = las rayas tenidas con el color del patron. Sin
        // patron, la capa 1 se pinta del MISMO color que la base y las rayas
        // desaparecen — un ItemColorProvider no puede ocultar una capa, pero
        // si fundirla.
        //
        // Shorts comparte el mismo callback aunque su modelo sea de una sola
        // capa: tintIndex 1 nunca se pide para ese item, asi que la rama de
        // patron simplemente no se ejecuta.
        // Muestra de color (2026-09-30): el líquido (capa 0) toma el color de la mezcla.
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            int[] m = com.modamod.item.MuestraColorItem.mezcla(stack);
            return m == null ? 0xFFB0B0B0 : 0xFF000000 | com.modamod.item.MuestraColorItem.rgb(m);
        }, ModamodItems.TINTE_MEZCLA);
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            int base = RegionResolver.colorBase(stack, Lado.IZQUIERDA);
            int color = base;
            if (tintIndex == 1) {
                Identifier patron = RegionResolver.patronId(stack, Lado.IZQUIERDA);
                if (patron != null) color = RegionResolver.colorPatron(stack, Lado.IZQUIERDA);
            }
            // El tinte de item es ARGB y el alfa CUENTA: vanilla devuelve -1
            // para "sin tinte". Nuestros colores son 0xRRGGBB, o sea alfa 0,
            // y sin este OR el item se dibuja transparente — desaparecia.
            return 0xFF000000 | color;
        }, ModamodItems.SOCKS_SOLID, ModamodItems.PANTALON);

        // Íconos de 64x64 armados con la tela real (2026-09-29, "ok pero los
        // hagamos 64x64" / "B"): muestran capas, patrones, redes, estampas y
        // el largo de verdad. La remera se registra en SublimadoraModClient.
        IconoPrendaItemRenderer icono = new IconoPrendaItemRenderer(null);
        for (var item : new net.minecraft.item.Item[]{ModamodItems.PANTALON, ModamodItems.POLLERA,
                ModamodItems.SOCKS_SOLID, ModamodItems.CALIENTABRAZOS, ModamodItems.CAPA, ModamodItems.DELANTAL}) {
            net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(item, icono);
        }

        // Sombrero, calzado y banda (2026-10-15): la pieza armada de verdad, no un PNG fijo.
        IconoAccesorioItemRenderer iconoAccesorio = new IconoAccesorioItemRenderer();
        // El maletín también (2026-10-23): sus zonas teñidas y sus apliques, en el inventario, en la mano y apoyado.
        for (var item : new net.minecraft.item.Item[]{ModamodItems.SOMBRERO_BRUJA, ModamodItems.BORCEGOS, ModamodItems.BANDA, ModamodItems.MALETIN}) {
            net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(item, iconoAccesorio);
        }

        // Molde de aplique personalizado (2026-10-04): papel kraft con el objeto encima.
        net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(
                ModamodItems.MOLDE_APLIQUE_PERSONALIZADO, new MoldeApliqueItemRenderer());

        // Retazo de aplique (2026-10-01): el ícono provisorio se tiñe con la zona 1.
        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex == 0
                ? 0xFF000000 | com.modamod.aplique.RetazoApliqueItem.colores(stack).get(0) : -1,
                ModamodItems.RETAZO_APLIQUE);

        // Sin esto, F3+T releía los .png del disco pero ClothingTextureCache
        // seguía devolviendo la composición vieja desde su cache estático
        // -encontrado jugando, iterando la textura de calientabrazos: hacía
        // falta relanzar el cliente entero para ver un cambio de asset.
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public Identifier getFabricId() {
                        return Identifier.of("modamod", "clothing_texture_cache");
                    }

                    @Override
                    public void reload(ResourceManager manager) {
                        ClothingTextureCache.limpiarCache();
                        com.modamod.render.IconoPrenda.limpiar();
                    }
                });
    }
}
