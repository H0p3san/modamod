package com.modamod.sublimadora;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class SublimadoraModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // El render del bloque lo hace GeckoLib desde el block entity.
        BlockEntityRendererFactories.register(
                ModBlocks.SUBLIMADORA_ENTITY, ctx -> new SublimadoraRenderer());

        // La remera tiene alfa: sin cutout el recorte se rellena y queda un cuadrado.
        // Cubre el modelo JSON del item en el inventario y la variante sin GeckoLib.
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SUBLIMADORA, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.SUBLIMADORA_CREATIVA, RenderLayer.getCutout());

        // La remera la dibuja un renderer propio para poder estamparle la
        // foto. item/corte_<corte> no lo referencia ningun blockstate ni ningun
        // item, asi que hay que pedir que igual se hornee: si no, el renderer
        // no lo encuentra y la remera se ve invisible.
        ModelLoadingPlugin.register(contexto -> {
            for (Variante v : Variante.todas()) contexto.addModels(v.modeloItem());
        });
        // Ícono de 64x64 con la tela real (2026-09-29); el de antes queda de respaldo.
        BuiltinItemRendererRegistry.INSTANCE.register(ModItems.REMERA,
                new com.modamod.client.IconoPrendaItemRenderer(new RemeraItemRenderer()));

        // El icono lo tine vanilla con el proveedor de color: item/generated
        // le pone tintIndex 0 a layer0, igual que a una armadura de cuero.
        //
        // El 0xFF de adelante NO es decorativo: el tinte de item es ARGB y el
        // alfa cuenta. Devolver un RGB pelado deja el item INVISIBLE, que ya
        // costo un rato en este repo. Vanilla usa -1 para "sin tinte".
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(
                (stack, capa) -> capa == 0 ? 0xFF000000 | RemeraItem.color(stack) : -1,
                ModItems.REMERA);

        // La remera puesta sobre el cuerpo ya NO se registra aca. La dibuja
        // el GarmentFeatureRenderer de ModaMod junto con el resto de la
        // ropa, que es lo unico que permite ordenarla por capa contra las
        // otras prendas. Lo que la remera declara -sus piezas- esta en
        // PiezasDelMod.
    }
}
