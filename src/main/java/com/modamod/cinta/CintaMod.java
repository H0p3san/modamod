package com.modamod.cinta;

import com.modamod.Modamod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** Registro de la cinta transportadora (2026-10-04, "geometria de cinta transportadora"). */
public final class CintaMod {

    public static final CintaBlock CINTA_BLOCK = new CintaBlock(
            AbstractBlock.Settings.create()
                    .strength(1.5f, 3.0f)
                    .sounds(BlockSoundGroup.METAL)
                    .nonOpaque());

    public static final BlockItem CINTA_ITEM = new BlockItem(CINTA_BLOCK, new Item.Settings());

    public static final BlockEntityType<CintaBlockEntity> CINTA_BLOCK_ENTITY =
            BlockEntityType.Builder.create(CintaBlockEntity::new, CINTA_BLOCK).build();

    /** Empalme (2026-10-05): hasta tres entradas laterales + tolva arriba, una salida. */
    public static final EmpalmeBlock EMPALME_BLOCK = new EmpalmeBlock(
            AbstractBlock.Settings.create().strength(1.5f, 3.0f).sounds(BlockSoundGroup.METAL).nonOpaque());
    public static final BlockItem EMPALME_ITEM = new BlockItem(EMPALME_BLOCK, new Item.Settings());
    public static final BlockEntityType<EmpalmeBlockEntity> EMPALME_BLOCK_ENTITY =
            BlockEntityType.Builder.create(EmpalmeBlockEntity::new, EMPALME_BLOCK).build();

    /** Tubos de transporte (2026-10-20): el tramo con sentido (6 caras) y el nudo de 6 caras con filtros. */
    public static final TuboBlock TUBO_BLOCK = new TuboBlock(
            AbstractBlock.Settings.create().strength(1.5f, 3.0f).sounds(BlockSoundGroup.COPPER).nonOpaque());
    public static final BlockItem TUBO_ITEM = new BlockItem(TUBO_BLOCK, new Item.Settings());
    public static final BlockEntityType<TuboBlockEntity> TUBO_BLOCK_ENTITY =
            BlockEntityType.Builder.create(TuboBlockEntity::new, TUBO_BLOCK).build();
    public static final EmpalmeTuboBlock EMPALME_TUBO_BLOCK = new EmpalmeTuboBlock(
            AbstractBlock.Settings.create().strength(1.5f, 3.0f).sounds(BlockSoundGroup.COPPER).nonOpaque());
    public static final BlockItem EMPALME_TUBO_ITEM = new BlockItem(EMPALME_TUBO_BLOCK, new Item.Settings());
    public static final BlockEntityType<EmpalmeTuboBlockEntity> EMPALME_TUBO_BLOCK_ENTITY =
            BlockEntityType.Builder.create(EmpalmeTuboBlockEntity::new, EMPALME_TUBO_BLOCK).build();

    /** Extractor (2026-10-21): saca de un contenedor por atrás y empuja hacia adelante, de a uno, con filtro. */
    public static final ExtractorBlock EXTRACTOR_BLOCK = new ExtractorBlock(
            AbstractBlock.Settings.create().strength(1.5f, 3.0f).sounds(BlockSoundGroup.COPPER).nonOpaque());
    public static final BlockItem EXTRACTOR_ITEM = new BlockItem(EXTRACTOR_BLOCK, new Item.Settings());
    public static final BlockEntityType<ExtractorBlockEntity> EXTRACTOR_BLOCK_ENTITY =
            BlockEntityType.Builder.create(ExtractorBlockEntity::new, EXTRACTOR_BLOCK).build();

    /** Válvula antirretorno (2026-10-21): un tramo de tubo que deja pasar en un solo sentido. */
    public static final ValvulaBlock VALVULA_BLOCK = new ValvulaBlock(
            AbstractBlock.Settings.create().strength(1.5f, 3.0f).sounds(BlockSoundGroup.COPPER).nonOpaque());
    public static final BlockItem VALVULA_ITEM = new BlockItem(VALVULA_BLOCK, new Item.Settings());
    public static final BlockEntityType<ValvulaBlockEntity> VALVULA_BLOCK_ENTITY =
            BlockEntityType.Builder.create(ValvulaBlockEntity::new, VALVULA_BLOCK).build();

    public static void register() {
        Identifier id = Identifier.of(Modamod.MOD_ID, "cinta");
        Registry.register(Registries.BLOCK, id, CINTA_BLOCK);
        Registry.register(Registries.ITEM, id, CINTA_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id, CINTA_BLOCK_ENTITY);
        Identifier ide = Identifier.of(Modamod.MOD_ID, "empalme");
        Registry.register(Registries.BLOCK, ide, EMPALME_BLOCK);
        Registry.register(Registries.ITEM, ide, EMPALME_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, ide, EMPALME_BLOCK_ENTITY);
        Identifier idt = Identifier.of(Modamod.MOD_ID, "tubo");
        Registry.register(Registries.BLOCK, idt, TUBO_BLOCK);
        Registry.register(Registries.ITEM, idt, TUBO_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, idt, TUBO_BLOCK_ENTITY);
        Identifier idet = Identifier.of(Modamod.MOD_ID, "empalme_tubo");
        Registry.register(Registries.BLOCK, idet, EMPALME_TUBO_BLOCK);
        Registry.register(Registries.ITEM, idet, EMPALME_TUBO_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, idet, EMPALME_TUBO_BLOCK_ENTITY);
        Identifier idx = Identifier.of(Modamod.MOD_ID, "extractor");
        Registry.register(Registries.BLOCK, idx, EXTRACTOR_BLOCK);
        Registry.register(Registries.ITEM, idx, EXTRACTOR_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, idx, EXTRACTOR_BLOCK_ENTITY);
        Identifier idv = Identifier.of(Modamod.MOD_ID, "valvula");
        Registry.register(Registries.BLOCK, idv, VALVULA_BLOCK);
        Registry.register(Registries.ITEM, idv, VALVULA_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, idv, VALVULA_BLOCK_ENTITY);
    }

    private CintaMod() {}
}
