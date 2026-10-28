package com.modamod.sublimadora;

import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.RemotePicture;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Lado cliente del puente con Camerapture: conseguir la textura de una foto
 * a partir de su UUID.
 *
 * Va en una clase aparte del CameraptureCompat comun por la misma razon que
 * aquel esta aislado: asi la JVM no intenta resolver clases de Camerapture
 * -ni de cliente- cuando el mod no esta instalado o cuando esto corre en un
 * servidor dedicado.
 */
final class CameraptureClientCompat {

    private CameraptureClientCompat() {}

    /**
     * Textura de la foto, o null si todavia no llego.
     *
     * ensureRemotePicture la pide al servidor la primera vez y despues la
     * sirve de su cache, asi que se puede llamar en cada frame: la remera
     * aparece en blanco un instante y se estampa sola cuando termina la
     * descarga.
     */
    static Foto foto(UUID id) {
        RemotePicture remota = ClientPictureStore.getInstance().ensureRemotePicture(id);
        if (remota == null || remota.getStatus() != RemotePicture.Status.SUCCESS) return null;
        Identifier textura = remota.getTextureIdentifier();
        if (textura == null) return null;
        return new Foto(textura, Math.max(1, remota.getWidth()), Math.max(1, remota.getHeight()));
    }

    record Foto(Identifier textura, int ancho, int alto) {}
}
