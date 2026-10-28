package com.modamod.screen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public final class ModamodScreenHandlers {

    /**
     * Extended, no un {@code ScreenHandlerType} común: la Mesa de Modelado
     * necesita que el handler del CLIENTE apunte al block entity REAL de esa
     * posición (no uno de mentira) para que la lista de fijadas —que no cabe
     * en un {@code PropertyDelegate}, viaja por NBT del block entity— llegue
     * actualizada. El payload es el {@code BlockPos} del bloque abierto.
     */
    public static final net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<
            com.modamod.modelado.ModeladoScreenHandler, net.minecraft.util.math.BlockPos> MODELADO = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("modamod", "modelado"),
            new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                    (syncId, inv, pos) -> com.modamod.modelado.ModeladoScreenHandler.deCliente(syncId, inv, pos),
                    net.minecraft.util.math.BlockPos.PACKET_CODEC));

    /** Mismo motivo que MODELADO: los patrones aprendidos viajan por NBT del block entity, no por PropertyDelegate. */
    public static final net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<
            com.modamod.tinturas.TinturasScreenHandler, net.minecraft.util.math.BlockPos> TINTURAS = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("modamod", "tinturas"),
            new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                    (syncId, inv, pos) -> com.modamod.tinturas.TinturasScreenHandler.deCliente(syncId, inv, pos),
                    net.minecraft.util.math.BlockPos.PACKET_CODEC));

    /** Mismo motivo que TINTURAS: nada que no quepa en un PropertyDelegate, pero igual mantiene el patrón. */
    public static final net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<
            com.modamod.sublimadora.SublimadoraScreenHandler, net.minecraft.util.math.BlockPos> SUBLIMADORA = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("modamod", "sublimadora"),
            new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                    (syncId, inv, pos) -> com.modamod.sublimadora.SublimadoraScreenHandler.deCliente(syncId, inv, pos),
                    net.minecraft.util.math.BlockPos.PACKET_CODEC));

    /** Mismo motivo que TINTURAS/SUBLIMADORA: la lista de outfits guardados viaja por NBT del block entity, no por PropertyDelegate. */
    public static final net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<
            com.modamod.guardarropas.GuardarropasScreenHandler, net.minecraft.util.math.BlockPos> GUARDARROPAS = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("modamod", "guardarropas"),
            new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                    (syncId, inv, pos) -> com.modamod.guardarropas.GuardarropasScreenHandler.deCliente(syncId, inv, pos),
                    net.minecraft.util.math.BlockPos.PACKET_CODEC));

    /** Maletín de outfits (2026-10-21): la lista para vestirte con los outfits de su Guardarropas (el dato es la mano). */
    public static final net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<
            com.modamod.guardarropas.MaletinScreenHandler, Integer> MALETIN = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("modamod", "maletin"),
            new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                    (syncId, inv, dato) -> com.modamod.guardarropas.MaletinScreenHandler.delCliente(syncId, inv, dato),
                    net.minecraft.network.codec.PacketCodecs.VAR_INT));

    /** Maniquí (2026-09-30): mismo patrón extended que GUARDARROPAS, para que el cliente apunte al block entity real. */
    public static final net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<
            com.modamod.maniqui.ManiquiScreenHandler, net.minecraft.util.math.BlockPos> MANIQUI = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("modamod", "maniqui"),
            new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                    (syncId, inv, pos) -> com.modamod.maniqui.ManiquiScreenHandler.deCliente(syncId, inv, pos),
                    net.minecraft.util.math.BlockPos.PACKET_CODEC));

    /** Mesa de estilado (2026-10-01): la prenda con sus apliques viaja en el block entity real. */
    public static final net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<
            com.modamod.estilado.EstiladoScreenHandler, net.minecraft.util.math.BlockPos> ESTILADO = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("modamod", "estilado"),
            new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                    (syncId, inv, pos) -> com.modamod.estilado.EstiladoScreenHandler.deCliente(syncId, inv, pos),
                    net.minecraft.util.math.BlockPos.PACKET_CODEC));

    public static void init() {
        // fuerza class-loading
    }

    private ModamodScreenHandlers() {}

    /** Pantalla de Ropa (2026-10-05): sin datos extra, todo sale del jugador. */
    public static final ScreenHandlerType<com.modamod.ropa.RopaScreenHandler> ROPA = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of("modamod", "ropa"),
            new ScreenHandlerType<>(com.modamod.ropa.RopaScreenHandler::new, FeatureSet.empty()));

    /** Pantalla de la Estilista automática (2026-10-05). */
    public static final net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<
            com.modamod.estilista.EstilistaScreenHandler, net.minecraft.util.math.BlockPos> ESTILISTA = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("modamod", "estilista"),
            new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                    (syncId, inv, pos) -> com.modamod.estilista.EstilistaScreenHandler.deCliente(syncId, inv, pos),
                    net.minecraft.util.math.BlockPos.PACKET_CODEC));

    /** Pantalla del Telar automático (2026-10-07). */
    public static final net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<
            com.modamod.telar.TelarScreenHandler, net.minecraft.util.math.BlockPos> TELAR = Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("modamod", "telar"),
            new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType<>(
                    (syncId, inv, pos) -> com.modamod.telar.TelarScreenHandler.deCliente(syncId, inv, pos),
                    net.minecraft.util.math.BlockPos.PACKET_CODEC));
}
