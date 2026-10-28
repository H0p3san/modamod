package com.modamod.body;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

/**
 * La parte de arriba de la ropa interior del cuerpo (2026-09-30, "me
 * gustaria agregar un binder mas boxer? capaz se eligen las dos partes?").
 * Viaja por ordinal: valores nuevos, al final.
 */
public enum InteriorArriba implements StringIdentifiable {
    NINGUNA("ninguna"),
    BRALETTE("bralette"),
    DEPORTIVO("deportivo"),
    BINDER("binder");

    public final String clave;

    InteriorArriba(String clave) {
        this.clave = clave;
    }

    @Override
    public String asString() {
        return clave;
    }

    /** Textura en gris con el layout de la skin (se tiñe con el color elegido), o null si no hay nada que dibujar. */
    @Nullable
    public Identifier textura() {
        return this == NINGUNA ? null
                : Identifier.of(Modamod.MOD_ID, "textures/entity/cuerpo/interior_arriba_" + clave + ".png");
    }

    public String traduccion() {
        return "modamod.interior.arriba." + clave;
    }

    public InteriorArriba siguiente() {
        return values()[(ordinal() + 1) % values().length];
    }
}
