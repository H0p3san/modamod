package com.modamod;

import net.minecraft.util.Identifier;

import java.util.Map;

/**
 * Ids de ítems que cambiaron de nombre (2026-11-05: los ids internos pasan a ser los de la categoría). Lo usa
 * {@link com.modamod.mixin.RegistroIdsViejosMixin} para que los ítems guardados con el id viejo se sigan cargando.
 * Para renombrar otro ítem: agregarlo acá y no volver a usar nunca el id viejo para otra cosa.
 */
public final class IdsViejos {
    private IdsViejos() {}

    private static final Map<String, String> ITEMS = Map.of(
            "socks_solid", "medias",
            "sombrero_bruja", "sombrero",
            "borcegos", "calzado",
            "molde_capa_con_capucha", "molde_capucha_malla" // molde de capucha común, sacado 2026-11-09
    );

    public static Identifier nuevo(Identifier id) {
        if (!Modamod.MOD_ID.equals(id.getNamespace())) return id;
        String n = ITEMS.get(id.getPath());
        return n == null ? id : Identifier.of(Modamod.MOD_ID, n);
    }
}
