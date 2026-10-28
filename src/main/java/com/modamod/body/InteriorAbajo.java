package com.modamod.body;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

/**
 * La parte de abajo de la ropa interior del cuerpo (2026-09-30). "Ninguna" se
 * agregó el 2026-10-04 ("alguna forma de optar sin ropa interior de los pantalones asi pueda hacer la mia
 * propia"). Viaja por ordinal: valores nuevos, al final.
 */
public enum InteriorAbajo implements StringIdentifiable {
    SLIP("slip"),
    CULOTTE("culotte"),
    BOXER("boxer"),
    NINGUNA("ninguna");

    public final String clave;

    InteriorAbajo(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() {
        return clave;
    }

    /** Textura en gris con el layout de la skin (se tiñe con el color elegido), o null si no hay nada que dibujar. */
    @org.jetbrains.annotations.Nullable
    public Identifier textura() {
        return this == NINGUNA ? null : Identifier.of(Modamod.MOD_ID, "textures/entity/cuerpo/interior_abajo_" + clave + ".png");
    }

    public String traduccion() {
        return "modamod.interior.abajo." + clave;
    }

    public InteriorAbajo siguiente() {
        return values()[(ordinal() + 1) % values().length];
    }
}
