package com.modamod.sublimadora;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.item.PictureItem;
import net.minecraft.item.ItemStack;

import java.util.UUID;

/**
 * Puente con Camerapture, aislado para que la JVM solo lo cargue si el mod
 * esta presente.
 *
 * De la foto solo se saca el UUID: la imagen ya vive en su almacenamiento, y
 * copiarla al ItemStack de la remera lo haria pesar de mas por cada prenda.
 */
final class CameraptureCompat {

    private CameraptureCompat() {}

    /** Tope de bytes de una foto, el mismo que pone el servidor de Camerapture. */
    static int maximoBytes() {
        return Camerapture.CONFIG_MANAGER.getConfig().server.maxImageBytes;
    }

    /**
     * Una foto nueva de Camerapture con {@code imagen} (WebP ya comprimido):
     * la guarda en su almacenamiento, como al terminar de subir una foto de
     * la cámara, y devuelve el ítem de foto firmado por {@code jugador}.
     */
    static ItemStack crearFoto(net.minecraft.server.network.ServerPlayerEntity jugador, byte[] imagen)
            throws java.io.IOException {
        me.chrr.camerapture.picture.ServerPictureStore almacen = me.chrr.camerapture.picture.ServerPictureStore.getInstance();
        UUID id = almacen.reserveId();
        try {
            almacen.put(jugador.getServer(), id, new me.chrr.camerapture.picture.StoredPicture(imagen));
        } finally {
            almacen.unreserveId(id);
        }
        return PictureItem.create(jugador.getName().getString(), id);
    }

    static UUID uuidDe(ItemStack stack) {
        PictureItem.PictureData data = stack.get(Camerapture.PICTURE_DATA);
        return data == null ? null : data.id();
    }
}
