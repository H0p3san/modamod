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

        // El editor de la prenda de muestra (antes la Mesa de estilado): apliques, correas, ajustes y moldes con nombre.
        ComandoMoldes.init();
        // Click en la vista 3D (2026-10-01).
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(PonerApliquePayload.ID, PonerApliquePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(PonerApliquePayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    var partes = com.modamod.prenda.Parte.values();
                    var caras = net.minecraft.util.math.Direction.values();
                    var superficies = com.modamod.aplique.Aplique.Superficie.values();
                    if (payload.parte() < 0 || payload.parte() >= partes.length
                            || payload.cara() < 0 || payload.cara() >= caras.length
                            || payload.superficie() < 0 || payload.superficie() >= superficies.length) return;
                    if (EditorEstilo.en(context.player().getWorld(), payload.pos()) instanceof EditorEstilo be
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
                    var partes = com.modamod.prenda.Parte.values();
                    var caras = net.minecraft.util.math.Direction.values();
                    var modos = com.modamod.correa.ModoCorrea.values();
                    if (payload.parte() < 0 || payload.parte() >= partes.length
                            || payload.caraDesde() < 0 || payload.caraDesde() >= caras.length
                            || payload.caraHasta() < 0 || payload.caraHasta() >= caras.length
                            || payload.modo() < 0 || payload.modo() >= modos.length
                            || payload.superficie() < 0 || payload.superficie() >= com.modamod.correa.Correa.Superficie.values().length) return;
                    if (EditorEstilo.en(context.player().getWorld(), payload.pos()) instanceof EditorEstilo be
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
                    if (EditorEstilo.en(context.player().getWorld(), payload.pos()) instanceof EditorEstilo be
                            && be.canPlayerUse(context.player())) {
                        be.ajustar(payload.indice(), payload.colocacion(), payload.oscilacion(), payload.blandura());
                    }
                }));

        // Crear molde con nombre (2026-10-04).
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
                .register(CrearMoldePayload.ID, CrearMoldePayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(CrearMoldePayload.ID,
                (payload, context) -> context.server().execute(() -> {
                    if (EditorEstilo.en(context.player().getWorld(), payload.pos()) instanceof EditorEstilo be
                            && be.canPlayerUse(context.player())) {
                        be.crearMolde(context.player(), payload.nombre());
                    }
                }));
    }

    public static final DisenoEstiloItem DISENO_ESTILO = new DisenoEstiloItem(new net.minecraft.item.Item.Settings().maxCount(1));

    private EstilistaMod() {}
}
