package com.modamod.client;

import me.chrr.camerapture.CameraptureClient;
import me.chrr.camerapture.config.SyncedConfig;
import me.chrr.camerapture.util.ImageUtil;
import net.minecraft.util.Formatting;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lo que "Escanear estampa" necesita de Camerapture, aislado para que la JVM solo cargue sus clases si el mod está
 * (hallazgo H05, 2026-10-08: sin Camerapture la ruta directa daba NoClassDefFoundError). Solo se llama después de
 * comprobar {@link EscanerEstampaCliente#disponible()}.
 */
final class CameraptureEscanerCompat {

    private CameraptureEscanerCompat() {}

    /** La imagen elegida, achicada a los límites del servidor y comprimida a WebP; null si no se pudo (ya avisó). */
    static byte[] comprimir(Path archivo) throws Exception {
        BufferedImage imagen = ImageIO.read(archivo.toFile());
        if (imagen == null && archivo.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".webp")) {
            imagen = ImageUtil.decodeImageFromWebP(Files.readAllBytes(archivo));
        }
        if (imagen == null) {
            EscanerEstampaCliente.avisar("modamod.sublimadora.escanear.no_imagen", Formatting.RED);
            return null;
        }
        // Los mismos límites que la cámara (los manda el servidor de Camerapture).
        SyncedConfig config = CameraptureClient.syncedConfig;
        int resolucion = config != null ? config.maxImageResolution() : 1280;
        int maximo = config != null ? config.maxImageBytes() : 500_000;
        imagen = ImageUtil.normalize(ImageUtil.clampSize(imagen, resolucion));
        float calidad = 0.95f;
        byte[] bytes = ImageUtil.compressIntoWebP(imagen, calidad);
        while (bytes.length > maximo) {
            calidad -= 0.05f;
            if (calidad < 0.1f) {
                EscanerEstampaCliente.avisar("modamod.sublimadora.escanear.grande", Formatting.RED);
                return null;
            }
            bytes = ImageUtil.compressIntoWebP(imagen, calidad);
        }
        return bytes;
    }
}
