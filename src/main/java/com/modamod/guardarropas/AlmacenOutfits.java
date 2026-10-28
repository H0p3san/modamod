package com.modamod.guardarropas;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Donde viven los outfits guardados (2026-10-21, "estilo maletín"): el bloque Guardarropas o el ítem Maletín. Las reglas (guardar lo
 * puesto, ponerse uno entero, desarmarlo) son las mismas y están en {@link Outfits}.
 */
public interface AlmacenOutfits {
    /** La lista viva: {@link Outfits} la modifica y después avisa con {@link #guardado}. */
    List<GuardarropasBlockEntity.Outfit> outfits();

    /** El nombre del último outfit que se puso cada jugador, para volver a guardarlo con el mismo. */
    Map<UUID, String> ultimos();

    /** Después de cambiar algo: guardar y sincronizar (el bloque marca el chunk; el maletín escribe su componente). */
    void guardado();
}
