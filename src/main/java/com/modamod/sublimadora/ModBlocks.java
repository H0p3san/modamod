package com.modamod.sublimadora;

import com.modamod.Modamod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** Registro de bloque, item y block entity type. Fabric 1.21.1. */
public final class ModBlocks {
    private ModBlocks() {}

    public static final Block SUBLIMADORA = new SublimadoraBlock(
            AbstractBlock.Settings.create()
                    // Sin requiresTool: a mano tarda unos 3.7 s y devuelve el
                    // bloque igual, con pico de hierro menos de uno. Con
                    // requiresTool la mano no dropeaba nada, que para una
                    // maquina que puede tener una remera adentro es cruel.
                    .strength(2.5f, 4.0f)
                    .nonOpaque()                       // la tapa abierta sale del cubo
                    .sounds(BlockSoundGroup.METAL)
                    .pistonBehavior(PistonBehavior.BLOCK)
                    .luminance(com.modamod.util.LuzMaquina::luminancia)
    );

    // BlockItem propio para que el icono del inventario lo dibuje GeckoLib
    // con la misma malla que el bloque, en vez de un modelo vanilla paralelo.
    public static final SublimadoraBlockItem SUBLIMADORA_ITEM =
            new SublimadoraBlockItem(SUBLIMADORA, new Item.Settings());

    /** Versión creativa (2026-10-01): sin espera, tinta ni papel — ver {@code util.MaquinaCreativa}. */
    public static final Block SUBLIMADORA_CREATIVA = com.modamod.util.MaquinaCreativa.creativa(new SublimadoraBlock(
            AbstractBlock.Settings.create().strength(2.5f, 4.0f).nonOpaque().sounds(BlockSoundGroup.METAL)
                    .pistonBehavior(PistonBehavior.BLOCK).luminance(com.modamod.util.LuzMaquina::luminancia)));
    public static final SublimadoraBlockItem SUBLIMADORA_CREATIVA_ITEM =
            new SublimadoraBlockItem(SUBLIMADORA_CREATIVA, new Item.Settings());

    public static final BlockEntityType<SublimadoraBlockEntity> SUBLIMADORA_ENTITY =
            BlockEntityType.Builder.create(SublimadoraBlockEntity::new, SUBLIMADORA, SUBLIMADORA_CREATIVA).build();

    public static final DisenoSublimadoItem DISENO_SUBLIMADO = new DisenoSublimadoItem(new Item.Settings().maxCount(1));

    public static void register() {
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "diseno_sublimado"), DISENO_SUBLIMADO);
        Identifier id = Identifier.of(Modamod.MOD_ID, "sublimadora");
        Registry.register(Registries.BLOCK, id, SUBLIMADORA);
        Registry.register(Registries.ITEM, id, SUBLIMADORA_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id, SUBLIMADORA_ENTITY);
        Identifier creativaId = Identifier.of(Modamod.MOD_ID, "sublimadora_creativa");
        Registry.register(Registries.BLOCK, creativaId, SUBLIMADORA_CREATIVA);
        Registry.register(Registries.ITEM, creativaId, SUBLIMADORA_CREATIVA_ITEM);

        // Escanear estampa (2026-10-02): una imagen del disco como foto de Camerapture.
        EscanearEstampa.init();
        // "Guardar diseño" con nombre (2026-09-28), mismo mecanismo que Tintes.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(GuardarDisenoSublimadoraPayload.ID, GuardarDisenoSublimadoraPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(GuardarDisenoSublimadoraPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    if (context.player().getWorld().getBlockEntity(payload.pos()) instanceof SublimadoraBlockEntity be
                            && be.canPlayerUse(context.player())) {
                        be.crearDisenoItem(context.player(), payload.nombre());
                    }
                }));
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(SublimadoraBolsaPayload.ID, SublimadoraBolsaPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(SublimadoraBolsaPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    if (context.player().getWorld().getBlockEntity(payload.pos()) instanceof SublimadoraBlockEntity be
                            && be.canPlayerUse(context.player()) && !be.trabajando()) {
                        switch (payload.accion()) {
                            case SublimadoraBolsaPayload.ELEGIR -> {
                                if (payload.tipo() == 0) be.elegirFoto(payload.indice()); else be.elegirMascara(payload.indice());
                            }
                            case SublimadoraBolsaPayload.SACAR -> be.sacarDeBolsa(context.player(), payload.tipo(), payload.indice());
                            case SublimadoraBolsaPayload.QUITAR -> {
                                if (payload.tipo() == 0) be.quitarFoto(); else be.quitarMascara();
                            }
                            case SublimadoraBolsaPayload.CARGAR_DISENO -> be.cargarDisenoItem(context.player(), payload.indice());
                            case SublimadoraBolsaPayload.SACAR_DISENO -> be.sacarDisenoItem(context.player(), payload.indice());
                            case SublimadoraBolsaPayload.EXPORTAR_DISENO -> be.exportarDiseno(context.player(), payload.indice());
                            default -> { }
                        }
                    }
                }));
    }
}
