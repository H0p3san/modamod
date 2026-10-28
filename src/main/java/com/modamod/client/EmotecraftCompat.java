package com.modamod.client;

import com.modamod.maniqui.ManiquiBlockEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.fabricmc.loader.api.FabricLoader;

import java.util.List;
import java.util.UUID;

/**
 * Puerta de entrada a Emotecraft desde el Maniquí (2026-10-04, "agregarle al maniqui una animacion del emotecraft
 * si esta instalado"). Esta clase NO nombra ninguna clase de Emotecraft: sin el mod no se carga {@link EmotecraftImpl}
 * y todo queda como siempre.
 */
public final class EmotecraftCompat {

    private static final boolean CARGADO = FabricLoader.getInstance().isModLoaded("emotecraft");

    public static boolean disponible() { return CARGADO; }

    /** Los emotes que este cliente tiene cargados, ordenados por nombre. */
    public static List<UUID> lista() { return CARGADO ? EmotecraftImpl.lista() : List.of(); }

    public static String nombre(UUID id) { return CARGADO ? EmotecraftImpl.nombre(id) : "?"; }

    /** Pone la pose del emote {@code id} (en este instante) sobre el modelo; false si no se pudo (no existe, sin mod). */
    public static boolean aplicar(ManiquiBlockEntity be, UUID id, PlayerEntityModel<LivingEntity> m) {
        return CARGADO && EmotecraftImpl.aplicar(be, id, m);
    }

    private EmotecraftCompat() {}
}
