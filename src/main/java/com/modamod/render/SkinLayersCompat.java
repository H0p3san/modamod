package com.modamod.render;

import net.minecraft.client.texture.NativeImage;

/**
 * Puente con 3D Skin Layers, aislado en su propia clase para que la JVM no la
 * resuelva si el mod no esta.
 *
 * <p><b>2026-09-20, AbstractMethodError resuelto</b>: esta clase llegó a
 * implementar {@code HttpTextureAccessor} (la interfaz de tr7zw para exponer
 * {@code getImage()}), primero heredando el método y después declarándolo
 * a mano — ninguna de las dos formas sobrevivía a un jar empaquetado de
 * verdad: Loom remapea cualquier método nombrado igual que uno heredado de
 * una clase vanilla (acá, {@code NativeImageBackedTexture.getImage()}) al
 * nombre intermediary ({@code method_4525}), aunque ese mismo método
 * también tenga que cumplir una interfaz de OTRO mod que espera el nombre
 * literal {@code getImage}. El resultado: nuestra clase queda con
 * {@code method_4525} pero ninguna con el nombre que la interfaz busca por
 * {@code invokeinterface}, y explota con AbstractMethodError.
 *
 * <p>Revisando el bytecode de {@code SkinUtil.getTexture} en 3DSL se ve que
 * ni hacía falta esa interfaz: antes de mirar {@code HttpTextureAccessor}
 * ya tiene una rama que, si la textura es {@code instanceof
 * NativeImageBackedTexture}, le llama {@code getImage()} directo por
 * despacho virtual normal — sin pasar por ninguna interfaz de terceros.
 * Como {@code Puente} ya es una {@code NativeImageBackedTexture} por
 * herencia, alcanza con NO implementar la interfaz para caer en esa rama
 * (que sí sobrevive el remapeo, porque ahí el método sigue siendo una
 * llamada virtual normal entre dos clases que Loom conoce y remapea
 * consistentemente en ambos mods).
 */
final class SkinLayersCompat {

    private SkinLayersCompat() {}

    static ClothingSkinTexture create(NativeImage image) {
        return new Puente(image);
    }

    private static final class Puente extends ClothingSkinTexture {
        Puente(NativeImage image) {
            super(image);
        }
    }
}
