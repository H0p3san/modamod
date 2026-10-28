package com.modamod.estilado;

import com.modamod.Modamod;
import com.modamod.bloque.BloqueGeoItem;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** Registro de la Mesa de estilado (2026-09-30, modelo GeckoLib "styling_table"). */
public final class EstiladoMod {

    public static final EstiladoBlock ESTILADO_BLOCK = new EstiladoBlock(
            AbstractBlock.Settings.create()
                    .strength(2.5f, 4.0f)
                    .sounds(BlockSoundGroup.WOOD)
                    // INVISIBLE (lo dibuja GeckoLib): sin esto cullea las caras vecinas.
                    .nonOpaque());

    public static final BloqueGeoItem ESTILADO_BLOCK_ITEM =
            new BloqueGeoItem(ESTILADO_BLOCK, new Item.Settings(), "styling_table");

    /** Versión creativa (2026-10-01): sin retazos y con todos los moldes a mano — ver {@code util.MaquinaCreativa}. */
    public static final EstiladoBlock ESTILADO_CREATIVA = com.modamod.util.MaquinaCreativa.creativa(new EstiladoBlock(
            AbstractBlock.Settings.create().strength(2.5f, 4.0f).sounds(BlockSoundGroup.WOOD).nonOpaque()));
    public static final BloqueGeoItem ESTILADO_CREATIVA_ITEM =
            new BloqueGeoItem(ESTILADO_CREATIVA, new Item.Settings(), "styling_table");

    public static final BlockEntityType<EstiladoBlockEntity> ESTILADO_BLOCK_ENTITY =
            BlockEntityType.Builder.create(EstiladoBlockEntity::new, ESTILADO_BLOCK, ESTILADO_CREATIVA).build();

    public static void register() {
        ComandoMoldes.init();
        Identifier id = Identifier.of(Modamod.MOD_ID, "mesa_estilado");
        Registry.register(Registries.BLOCK, id, ESTILADO_BLOCK);
        Registry.register(Registries.ITEM, id, ESTILADO_BLOCK_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id, ESTILADO_BLOCK_ENTITY);
        Identifier creativaId = Identifier.of(Modamod.MOD_ID, "mesa_estilado_creativa");
        Registry.register(Registries.BLOCK, creativaId, ESTILADO_CREATIVA);
        Registry.register(Registries.ITEM, creativaId, ESTILADO_CREATIVA_ITEM);

        // Click en la vista 3D (2026-10-01).
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(PonerApliquePayload.ID, PonerApliquePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(PonerApliquePayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    var partes = com.modamod.garment.Parte.values();
                    var caras = net.minecraft.util.math.Direction.values();
                    var superficies = com.modamod.aplique.Aplique.Superficie.values();
                    if (payload.parte() < 0 || payload.parte() >= partes.length
                            || payload.cara() < 0 || payload.cara() >= caras.length
                            || payload.superficie() < 0 || payload.superficie() >= superficies.length) return;
                    if (EstiladoBlockEntity.en(context.player().getWorld(), payload.pos()) instanceof EstiladoBlockEntity be
                            && be.canPlayerUse(context.player())) {
                        be.poner(partes[payload.parte()], payload.x(), payload.y(), payload.z(), caras[payload.cara()],
                                superficies[payload.superficie()], payload.padre());
                    }
                }));

        // Correas libres (2026-10-05): dos clicks, la valida el servidor.
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(PonerCorreaPayload.ID, PonerCorreaPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(PonerCorreaPayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    var partes = com.modamod.garment.Parte.values();
                    var caras = net.minecraft.util.math.Direction.values();
                    var modos = com.modamod.correa.ModoCorrea.values();
                    if (payload.parte() < 0 || payload.parte() >= partes.length
                            || payload.caraDesde() < 0 || payload.caraDesde() >= caras.length
                            || payload.caraHasta() < 0 || payload.caraHasta() >= caras.length
                            || payload.modo() < 0 || payload.modo() >= modos.length
                            || payload.superficie() < 0 || payload.superficie() >= com.modamod.correa.Correa.Superficie.values().length) return;
                    if (EstiladoBlockEntity.en(context.player().getWorld(), payload.pos()) instanceof EstiladoBlockEntity be
                            && be.canPlayerUse(context.player())) {
                        float[] d = payload.desde(), h = payload.hasta();
                        be.ponerCorrea(partes[payload.parte()],
                                new com.modamod.correa.Correa.Punto(d[0], d[1], d[2], caras[payload.caraDesde()]),
                                new com.modamod.correa.Correa.Punto(h[0], h[1], h[2], caras[payload.caraHasta()]),
                                modos[payload.modo()], payload.ancho(),
                                com.modamod.correa.Correa.Superficie.values()[payload.superficie()], payload.largo());
                    }
                }));

        // Panel lateral: colocación, oscilación y blandura (2026-10-04).
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(AjustarApliquePayload.ID, AjustarApliquePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(AjustarApliquePayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    if (EstiladoBlockEntity.en(context.player().getWorld(), payload.pos()) instanceof EstiladoBlockEntity be
                            && be.canPlayerUse(context.player())) {
                        be.ajustar(payload.indice(), payload.colocacion(), payload.oscilacion(), payload.blandura());
                    }
                }));

        // Crear molde con nombre (2026-10-04).
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(CrearMoldePayload.ID, CrearMoldePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(CrearMoldePayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    if (EstiladoBlockEntity.en(context.player().getWorld(), payload.pos()) instanceof EstiladoBlockEntity be
                            && be.canPlayerUse(context.player())) {
                        be.crearMolde(context.player(), payload.nombre());
                    }
                }));
    }

    private EstiladoMod() {}
}
