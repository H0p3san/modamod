package com.modamod.render;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * La skin compuesta, como textura de runtime.
 *
 * Existe como clase propia por 3D Skin Layers: su SkinUtil.getTexture solo
 * sabe leer dos cosas, recursos del ResourceManager o texturas que
 * implementen su interfaz HttpTextureAccessor. Una NativeImageBackedTexture
 * comun no es ninguna de las dos, asi que 3DSL devolveria null y le apagaria
 * las capas 3D al jugador — en TODO el cuerpo, por ponerse unas medias.
 */
public class ClothingSkinTexture extends NativeImageBackedTexture {

    public ClothingSkinTexture(NativeImage image) {
        super(image);
    }

    /**
     * Devuelve la variante que 3DSL sabe leer, si el mod esta presente.
     *
     * La clase que implementa su interfaz vive aparte a proposito: asi la JVM
     * solo la resuelve cuando se la usa, y sin skinlayers3d instalado nunca se
     * carga ni tira NoClassDefFoundError.
     */
    public static ClothingSkinTexture create(NativeImage image) {
        if (FabricLoader.getInstance().isModLoaded("skinlayers3d")) {
            try {
                return SkinLayersCompat.create(image);
            } catch (Throwable ignored) {
                // Cambio de version de 3DSL: mejor perder las capas 3D que crashear.
            }
        }
        return new ClothingSkinTexture(image);
    }
}
