package com.modamod.estilista;

import com.modamod.Modamod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Registro de la Estilista automática (2026-10-05, "rearmemos la estiladora como un bloque de la cadena, copia la base
 * de la modeladora ponele la tapa de la autostyler arriba y vamos a migrar toda la funcionalidad de la mesa
 * estilizadora a esta nueva maquina"). Etapa 1: el bloque, el modelo y la cadena; la Mesa de estilado sigue hasta que
 * esta ande.
 */
public final class EstilistaMod {

    public static final EstilistaBlock ESTILISTA_BLOCK = new EstilistaBlock(
            AbstractBlock.Settings.create().strength(2.5f, 4.0f).sounds(BlockSoundGroup.WOOD).nonOpaque()
                    .luminance(com.modamod.util.LuzMaquina::luminancia));
    public static final EstilistaBlockItem ESTILISTA_ITEM = new EstilistaBlockItem(ESTILISTA_BLOCK, new Item.Settings());

    /** Versión creativa: sin espera ni insumos, como las otras máquinas ({@code util.MaquinaCreativa}). */
    public static final EstilistaBlock ESTILISTA_CREATIVA = com.modamod.util.MaquinaCreativa.creativa(new EstilistaBlock(
            AbstractBlock.Settings.create().strength(2.5f, 4.0f).sounds(BlockSoundGroup.WOOD).nonOpaque()
                    .luminance(com.modamod.util.LuzMaquina::luminancia)));
    public static final EstilistaBlockItem ESTILISTA_CREATIVA_ITEM = new EstilistaBlockItem(ESTILISTA_CREATIVA, new Item.Settings());

    public static final BlockEntityType<EstilistaBlockEntity> ESTILISTA_BLOCK_ENTITY =
            BlockEntityType.Builder.create(EstilistaBlockEntity::new, ESTILISTA_BLOCK, ESTILISTA_CREATIVA).build();

    public static void register() {
        Identifier id = Identifier.of(Modamod.MOD_ID, "estilista");
        Registry.register(Registries.BLOCK, id, ESTILISTA_BLOCK);
        Registry.register(Registries.ITEM, id, ESTILISTA_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id, ESTILISTA_BLOCK_ENTITY);
        Identifier creativa = Identifier.of(Modamod.MOD_ID, "estilista_creativa");
        Registry.register(Registries.BLOCK, creativa, ESTILISTA_CREATIVA);
        Registry.register(Registries.ITEM, creativa, ESTILISTA_CREATIVA_ITEM);

        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "diseno_estilo"), DISENO_ESTILO);

        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(GuardarDisenoPayload.ID, GuardarDisenoPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(GuardarDisenoPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    if (context.player().getWorld().getBlockEntity(payload.pos()) instanceof EstilistaBlockEntity be
                            && be.canPlayerUse(context.player())) {
                        be.crearDisenoItem(context.player(), payload.nombre());
                    }
                }));

        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(EditorBolsaPayload.ID, EditorBolsaPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(EditorBolsaPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    if (context.player().getWorld().getBlockEntity(payload.pos()) instanceof EstilistaBlockEntity be
                            && be.canPlayerUse(context.player()) && !be.trabajando()) {
                        switch (payload.accion()) {
                            case EditorBolsaPayload.ELEGIR -> be.elegirDeBolsa(payload.tipo(), payload.indice());
                            case EditorBolsaPayload.SACAR -> be.sacarDeBolsa(context.player(), payload.tipo(), payload.indice());
                            case EditorBolsaPayload.TRIM -> be.elegirTrim(payload.id());
                            case EditorBolsaPayload.MATERIAL -> be.elegirMaterial(payload.id());
                            case EditorBolsaPayload.CAMBIAR_PESTANA -> be.cambiarPestana(payload.tipo(), payload.indice());
                            case EditorBolsaPayload.FIJAR -> be.alternarFijado(payload.tipo());
                            case EditorBolsaPayload.CARGAR_DISENO -> be.cargarDisenoItem(context.player(), payload.indice());
                            case EditorBolsaPayload.SACAR_DISENO -> be.sacarDisenoItem(context.player(), payload.indice());
                            case EditorBolsaPayload.EXPORTAR_DISENO -> be.exportarDiseno(context.player(), payload.indice());
                            default -> { }
                        }
                    }
                }));
    }

    public static final DisenoEstiloItem DISENO_ESTILO = new DisenoEstiloItem(new net.minecraft.item.Item.Settings().maxCount(1));

    private EstilistaMod() {}
}
