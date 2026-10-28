package com.modamod.region;

import net.minecraft.util.StringIdentifiable;

/**
 * Cómo se funde una capa de color de Tinturas con lo que ya hay debajo
 * (2026-09-27, "aplicar un patron a toda la prenda pudiendo controlar
 * como se mezclan las capas") — mismos nombres que cualquier editor de
 * imágenes, más la opacidad aparte en {@code RegionResolver.CapaPatron}.
 */
public enum ModoMezcla implements StringIdentifiable {
    /** Tapa lo de abajo con el color de la capa (tiñendo la tela, no pintando plano). */
    NORMAL("normal"),
    /** Oscurece: lo de abajo multiplicado por el color — un patrón oscuro sobre cualquier color de base. */
    MULTIPLICAR("multiplicar"),
    /** Contraste: aclara lo claro y oscurece lo oscuro de abajo según el color de la capa. */
    SUPERPONER("superponer");

    public final String clave;

    ModoMezcla(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.modo_mezcla." + clave;
    }

    public ModoMezcla siguiente() {
        ModoMezcla[] v = values();
        return v[(ordinal() + 1) % v.length];
    }
}
