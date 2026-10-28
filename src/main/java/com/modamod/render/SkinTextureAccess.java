package com.modamod.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Devuelve la NativeImage de la skin de un jugador, para poder samplear su
 * tono de piel.
 *
 * OJO — la ruta que sugería el comentario viejo de este archivo NO funciona:
 * las skins descargadas de Mojang son PlayerSkinTexture, que extiende
 * ResourceTexture, NO NativeImageBackedTexture. ResourceTexture sube los
 * pixeles a la GPU y suelta la NativeImage, así que no hay nada que castear:
 * el cast tira ClassCastException o devuelve null para toda skin real.
 *
 * Lo que sí funciona es leer los pixeles DE VUELTA de la GPU: bindear la
 * textura y usar NativeImage.loadFromTextureImage(). Requiere estar en el
 * hilo de render — que es donde corren los renderers de prendas, así que no
 * es una restricción en la práctica.
 *
 * El resultado se cachea por Identifier de skin: la lectura desde GPU es
 * cara y la skin de un jugador no cambia entre frames.
 */
public final class SkinTextureAccess {

    private static final int SKIN_SIZE = 64;

    private static final Map<Identifier, NativeImage> CACHE = new HashMap<>();
    private static final Set<Identifier> FAILED = new HashSet<>();

    private SkinTextureAccess() {}

    public static NativeImage tryGetImage(SkinTextures skin) {
        if (skin == null) return null;
        Identifier id = skin.texture();
        if (id == null || FAILED.contains(id)) return null;

        NativeImage cached = CACHE.get(id);
        if (cached != null) return cached;

        if (!RenderSystem.isOnRenderThread()) return null;

        try {
            AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(id);
            if (texture == null) {
                FAILED.add(id);
                return null;
            }

            // Caso raro pero gratis: si la textura ya conserva su NativeImage.
            if (texture instanceof NativeImageBackedTexture backed && backed.getImage() != null) {
                NativeImage img = backed.getImage();
                CACHE.put(id, img);
                return img;
            }

            // Caso real: leer de vuelta desde la GPU.
            NativeImage image = new NativeImage(SKIN_SIZE, SKIN_SIZE, false);
            RenderSystem.bindTexture(texture.getGlId());
            image.loadFromTextureImage(0, false);
            CACHE.put(id, image);
            return image;
        } catch (Exception e) {
            FAILED.add(id);
            return null;
        }
    }
}
