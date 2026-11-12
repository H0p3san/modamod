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
 *
 * <p>No es un enum (2026-11-12, apliques custom): una clave que no se conoce igual se lee (queda un modelo sin
 * geometría, que no se dibuja) en vez de romper la prenda o desconectar al cliente que todavía no lo recibió.
 */
public final class ModeloAplique implements StringIdentifiable {
    /** Todos por clave; los custom (clave {@code custom_*}, ver {@link ApliquesCustom}) se suman con el server andando. */
    private static final java.util.Map<String, ModeloAplique> POR_CLAVE = new java.util.concurrent.ConcurrentHashMap<>();

    public static final ModeloAplique MONO = de("mono");
    public static final ModeloAplique MARIPOSA = de("mariposa");
    public static final ModeloAplique FLOR = de("flor");
    /** Un ítem o bloque cualquiera dibujado por Minecraft (2026-10-04); ver {@link ObjetoAplique}. No tiene .geo.json. */
    public static final ModeloAplique OBJETO = de("objeto");
    /** Bolsillo de canguro: parche plano de tela (2026-10-08). */
    public static final ModeloAplique CANGURO = de("canguro");
    /** Corbata: nudo y hoja en tramos que cuelgan (2026-10-08). */
    public static final ModeloAplique CORBATA = de("corbata");
    /** Cordones de capucha: dos cordones finos que cuelgan, con puntita (2026-10-14). */
    public static final ModeloAplique CORDONES = de("cordones");
    /** Botón (2026-10-22, "nos faltaron botones y bolsillos"); la botonera es este con 4 en fila ({@link Fila}). */
    public static final ModeloAplique BOTON = de("boton");
    /** Bolsillo parche, con tapa y trasero de jean (2026-10-22): parches pegados a la tela, no se mueven. */
    public static final ModeloAplique BOLSILLO = de("bolsillo");
    public static final ModeloAplique BOLSILLO_TAPA = de("bolsillo_tapa");
    public static final ModeloAplique BOLSILLO_JEAN = de("bolsillo_jean");
    /** Tacha piramidal (2026-10-22); la tira de tachas es esta con 5 en fila. */
    public static final ModeloAplique TACHA = de("tacha");
    /** Parches bordados (2026-10-22): relleno en la zona 1 y el borde bordado en la zona 2. */
    public static final ModeloAplique PARCHE_CORAZON = de("parche_corazon");
    public static final ModeloAplique PARCHE_ESTRELLA = de("parche_estrella");
    public static final ModeloAplique PARCHE_RAYO = de("parche_rayo");
    public static final ModeloAplique POMPON = de("pompon");
    public static final ModeloAplique HEBILLA = de("hebilla");
    public static final ModeloAplique BARBA = de("barba");
    public static final ModeloAplique BIGOTE = de("bigote");

    public final String clave;

    private ModeloAplique(String clave) {
        this.clave = clave;
    }

    /** El modelo de esa clave, siempre el mismo objeto (se compara con ==). */
    public static ModeloAplique de(String clave) {
        return POR_CLAVE.computeIfAbsent(clave, ModeloAplique::new);
    }

    /** La clave va a parar a un {@link Identifier}: solo lo que ahí entra. */
    public static final com.mojang.serialization.Codec<ModeloAplique> CODEC = com.mojang.serialization.Codec.STRING.comapFlatMap(
            c -> c.matches("[a-z0-9_]{1,48}") ? com.mojang.serialization.DataResult.success(de(c))
                    : com.mojang.serialization.DataResult.error(() -> "modelo de aplique invalido: " + c),
            m -> m.clave);

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
