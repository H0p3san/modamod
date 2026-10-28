package com.modamod.aplique;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

/**
 * Los modelos de aplique (2026-10-01, Mesa de estilado: "moños mariposas flores
 * etc"). Cada uno es un {@code .geo.json} de GeckoLib generado por
 * {@code tools/generar_apliques.py}, con 3 zonas de color (columnas del atlas
 * {@code textures/entity/aplique_atlas.png}). Se guarda por nombre: agregar
 * modelos nuevos en cualquier lugar no rompe lo guardado.
 */
public enum ModeloAplique implements StringIdentifiable {
    MONO("mono"),
    MARIPOSA("mariposa"),
    FLOR("flor"),
    /** Un ítem o bloque cualquiera dibujado por Minecraft (2026-10-04); ver {@link ObjetoAplique}. No tiene .geo.json. */
    OBJETO("objeto"),
    /** Bolsillo de canguro: parche plano de tela (2026-10-08). */
    CANGURO("canguro"),
    /** Corbata: nudo y hoja en tramos que cuelgan (2026-10-08). */
    CORBATA("corbata"),
    /** Cordones de capucha: dos cordones finos que cuelgan, con puntita (2026-10-14). */
    CORDONES("cordones"),
    /** Botón (2026-10-22, "nos faltaron botones y bolsillos"); la botonera es este con 4 en fila ({@link Fila}). */
    BOTON("boton"),
    /** Bolsillo parche, con tapa y trasero de jean (2026-10-22): parches pegados a la tela, no se mueven. */
    BOLSILLO("bolsillo"),
    BOLSILLO_TAPA("bolsillo_tapa"),
    BOLSILLO_JEAN("bolsillo_jean"),
    /** Tacha piramidal (2026-10-22); la tira de tachas es esta con 5 en fila. */
    TACHA("tacha"),
    /** Parches bordados (2026-10-22): relleno en la zona 1 y el borde bordado en la zona 2. */
    PARCHE_CORAZON("parche_corazon"),
    PARCHE_ESTRELLA("parche_estrella"),
    PARCHE_RAYO("parche_rayo");

    public final String clave;

    ModeloAplique(String clave) {
        this.clave = clave;
    }

    /** Clave con la que GeckoLib guarda el modelo horneado. */
    public Identifier geo() {
        return Identifier.of(Modamod.MOD_ID, "geo/aplique_" + clave + ".geo.json");
    }

    public String traduccion() {
        return "modamod.aplique." + clave;
    }

    @Override
    public String asString() {
        return clave;
    }
}
