package com.modamod.guardarropas;

import com.modamod.Modamod;
import com.modamod.bloque.BloqueGeoItem;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** Registro del Guardarropas (modelo GeckoLib "wardrobe" desde 2026-09-30). */
public final class GuardarropasMod {

    public static final GuardarropasBlock GUARDARROPAS_BLOCK = new GuardarropasBlock(
            AbstractBlock.Settings.create()
                    .strength(2.5f, 4.0f)
                    .sounds(BlockSoundGroup.WOOD)
                    // INVISIBLE (lo dibuja GeckoLib): sin esto vanilla lo
                    // trata como cubo opaco y cullea las caras vecinas.
                    .nonOpaque());

    public static final BloqueGeoItem GUARDARROPAS_BLOCK_ITEM =
            new BloqueGeoItem(GUARDARROPAS_BLOCK, new Item.Settings(), "wardrobe");

    public static final BlockEntityType<GuardarropasBlockEntity> GUARDARROPAS_BLOCK_ENTITY =
            BlockEntityType.Builder.create(GuardarropasBlockEntity::new, GUARDARROPAS_BLOCK).build();

    /** El maletín apoyado en el mundo (2026-10-23): el ítem es el MaletinItem de siempre, que lo apoya él mismo. */
    public static final MaletinBlock MALETIN_BLOCK = new MaletinBlock(MaletinBlock.ajustes());
    public static final BlockEntityType<MaletinBlockEntity> MALETIN_BLOCK_ENTITY =
            BlockEntityType.Builder.create(MaletinBlockEntity::new, MALETIN_BLOCK).build();

    public static void register() {
        Colecciones.init();
        // "Guardar mi outfit" con nombre (2026-10-21): ver GuardarOutfitPayload.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(GuardarOutfitPayload.ID, GuardarOutfitPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(GuardarOutfitPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    if (context.player().currentScreenHandler instanceof GuardarropasScreenHandler h) h.guardar(payload.nombre());
                }));
        Identifier id = Identifier.of(Modamod.MOD_ID, "guardarropas");
        Registry.register(Registries.BLOCK, id, GUARDARROPAS_BLOCK);
        Registry.register(Registries.ITEM, id, GUARDARROPAS_BLOCK_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id, GUARDARROPAS_BLOCK_ENTITY);
        Identifier maletin = Identifier.of(Modamod.MOD_ID, "maletin");
        Registry.register(Registries.BLOCK, maletin, MALETIN_BLOCK);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, maletin, MALETIN_BLOCK_ENTITY);
    }

    private GuardarropasMod() {}
}
