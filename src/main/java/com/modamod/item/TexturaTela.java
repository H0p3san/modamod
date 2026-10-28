package com.modamod.item;

import net.minecraft.util.StringIdentifiable;

/**
 * Volumen propio de la tela (2026-10-01, relieve: "arrugas automaticas por
 * calce y pongamos un par de moldes de prueba"): se le pone a una prenda con
 * un Molde de textura en la Mesa de estilado y lo dibuja
 * {@code render.relieve.RelieveTela}. Viaja por red por ordinal: valores
 * nuevos, siempre al final.
 */
public enum TexturaTela implements StringIdentifiable {
    LISA("lisa"),
    /** Pliegues finos verticales, como una tela fruncida o arruchada. */
    FRUNCIDO("fruncido"),
    /** Almohadones en rombo, como una campera inflada. */
    ACOLCHADO("acolchado");

    public static final com.mojang.serialization.Codec<TexturaTela> CODEC = StringIdentifiable.createCodec(TexturaTela::values);

    public final String clave;

    TexturaTela(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.textura_tela." + clave;
    }
}
