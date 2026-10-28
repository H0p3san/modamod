package com.modamod.bloque;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeoModel genérico para los bloques que no necesitan lógica propia en el
 * modelo: geo/textura/animación salen del nombre del asset
 * ({@code geo/<nombre>.geo.json}, {@code textures/block/<nombre>_atlas.png},
 * {@code animations/<nombre>.animation.json}). Lo usan el Guardarropas y la
 * Mesa de estilado (bloque e ítem), traídos del zip "Bloque de sublimadora
 * Minecraft" (2026-09-30, "hay 3 bloques nuevos que hay que hacer funcionar")
 * — antes cada máquina tenía su trío GeoModel/ItemGeoModel/ItemRenderer
 * copiado a mano con solo el nombre cambiado.
 */
public class ModeloGeo<T extends GeoAnimatable> extends GeoModel<T> {

    private final Identifier geo;
    private final Identifier textura;
    private final Identifier animacion;

    public ModeloGeo(String nombre) {
        this(nombre, nombre);
    }

    /**
     * Con un {@code .geo.json} propio y el atlas/animación de {@code nombre}
     * — para el ícono del Maniquí, que usa una copia del geo sin partir
     * (GeckoLib comparte los huesos entre todos los que usan el mismo geo, y
     * el bloque esconde la figura; ver {@code ManiquiGeoModel}).
     */
    public ModeloGeo(String geo, String nombre) {
        this.geo = Identifier.of(Modamod.MOD_ID, "geo/" + geo + ".geo.json");
        this.textura = Identifier.of(Modamod.MOD_ID, "textures/block/" + nombre + "_atlas.png");
        this.animacion = Identifier.of(Modamod.MOD_ID, "animations/" + nombre + ".animation.json");
    }

    @Override public Identifier getModelResource(T animatable) { return geo; }
    /** La versión creativa de una máquina (2026-10-01) usa la textura recoloreada — ver {@code util.MaquinaCreativa}. */
    @Override
    public Identifier getTextureResource(T animatable) {
        boolean creativa = animatable instanceof net.minecraft.block.entity.BlockEntity be
                ? com.modamod.util.MaquinaCreativa.es(be)
                : animatable instanceof net.minecraft.item.Item item && com.modamod.util.MaquinaCreativa.es(item);
        return com.modamod.util.MaquinaCreativa.textura(textura, creativa);
    }
    @Override public Identifier getAnimationResource(T animatable) { return animacion; }
}
