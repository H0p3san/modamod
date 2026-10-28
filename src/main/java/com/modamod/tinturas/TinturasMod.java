package com.modamod.tinturas;

import com.modamod.Modamod;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.List;

/** Registro de la Estación de Tintes: bloque, item, block entity type, componente del tanque. */
public final class TinturasMod {

    public static final TinturasBlock TINTURAS_BLOCK = new TinturasBlock(
            AbstractBlock.Settings.create()
                    .strength(2.5f, 4.0f)
                    .sounds(BlockSoundGroup.WOOD)
                    // El bloque es INVISIBLE (GeckoLib lo dibuja desde el
                    // block entity, modelo dye_station) — sin esto vanilla
                    // lo trata como cubo opaco sólido y cullea la cara del
                    // bloque de abajo (mismo bug que tuvo Modeladora).
                    .nonOpaque()
                    .luminance(com.modamod.util.LuzMaquina::luminancia));

    public static final TinturasBlockItem TINTURAS_BLOCK_ITEM = new TinturasBlockItem(TINTURAS_BLOCK, new Item.Settings());

    /** Versión creativa (2026-10-01): sin espera ni tinta, viene con todos los patrones — ver {@code util.MaquinaCreativa}. */
    public static final TinturasBlock TINTURAS_CREATIVA = com.modamod.util.MaquinaCreativa.creativa(new TinturasBlock(
            AbstractBlock.Settings.create().strength(2.5f, 4.0f).sounds(BlockSoundGroup.WOOD).nonOpaque()
                    .luminance(com.modamod.util.LuzMaquina::luminancia)));
    public static final TinturasBlockItem TINTURAS_CREATIVA_ITEM = new TinturasBlockItem(TINTURAS_CREATIVA, new Item.Settings());

    public static final BlockEntityType<TinturasBlockEntity> TINTURAS_BLOCK_ENTITY =
            BlockEntityType.Builder.create(TinturasBlockEntity::new, TINTURAS_BLOCK, TINTURAS_CREATIVA).build();

    /**
     * El tanque de 16 colores viaja adentro del ítem al romper el bloque
     * (mismo mecanismo que {@code ModItems.CARGAS} de la Sublimadora) —
     * es un componente y no NBT crudo para que la loot table lo copie
     * sola con {@code minecraft:copy_components}.
     */
    public static final ComponentType<List<Integer>> CARGAS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Modamod.MOD_ID, "tinturas_cargas"),
            ComponentType.<List<Integer>>builder()
                    .codec(Codec.INT.listOf())
                    .build());

    public static void register() {
        Identifier bloqueId = Identifier.of(Modamod.MOD_ID, "tinturas");
        Registry.register(Registries.BLOCK, bloqueId, TINTURAS_BLOCK);
        Registry.register(Registries.ITEM, bloqueId, TINTURAS_BLOCK_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, bloqueId, TINTURAS_BLOCK_ENTITY);
        Identifier creativaId = Identifier.of(Modamod.MOD_ID, "tinturas_creativa");
        Registry.register(Registries.BLOCK, creativaId, TINTURAS_CREATIVA);
        Registry.register(Registries.ITEM, creativaId, TINTURAS_CREATIVA_ITEM);

        // "Guardar diseño" con nombre (2026-09-27): el nombre viaja como paquete propio, ver GuardarDisenoTinturasPayload.
        PayloadTypeRegistry.playC2S().register(GuardarDisenoTinturasPayload.ID, GuardarDisenoTinturasPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GuardarDisenoTinturasPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    if (context.player().getWorld().getBlockEntity(payload.pos()) instanceof TinturasBlockEntity be
                            && be.canPlayerUse(context.player())) {
                        be.crearDisenoItem(context.player(), payload.nombre());
                    }
                }));

        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "diseno_tinta"), DISENO_TINTA);

        // Elegir un patrón de la bolsa para un cuadradito / sacarlo de la bolsa (2026-10-12): ver ElegirPatronPayload.
        PayloadTypeRegistry.playC2S().register(ElegirPatronPayload.ID, ElegirPatronPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ElegirPatronPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    if (context.player().getWorld().getBlockEntity(payload.pos()) instanceof TinturasBlockEntity be
                            && be.canPlayerUse(context.player()) && !be.trabajando()) {
                        switch (payload.modo()) {
                            case ElegirPatronPayload.SACAR -> be.sacarPatron(context.player(), payload.item());
                            case ElegirPatronPayload.CARGAR_DISENO -> be.cargarDisenoItem(payload.casilla());
                            case ElegirPatronPayload.SACAR_DISENO -> be.sacarDiseno(context.player(), payload.casilla());
                            case ElegirPatronPayload.EXPORTAR_DISENO -> be.exportarDiseno(context.player(), payload.casilla());
                            default -> be.elegirPatron(payload.casilla(), payload.item());
                        }
                    }
                }));
    }

    /** Diseño de tinte (2026-10-12): ítem con los cuadraditos de una categoría. */
    public static final DisenoTintaItem DISENO_TINTA = new DisenoTintaItem(new net.minecraft.item.Item.Settings().maxCount(1));

    private TinturasMod() {}
}
