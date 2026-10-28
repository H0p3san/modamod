package com.modamod.render;

import com.modamod.item.DelantalTirantes;
import com.modamod.item.SombreroPatron;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tirantes y cinta del delantal (2026-10-11, "dale con tirantes y sublimacion"): cintas de caja, lisas del color del delantal,
 * en el marco del torso. Suben por el frente hasta el borde de arriba del torso, pasan por encima y bajan por la
 * espalda; cruzados, los de atrás se cruzan en escalones. {@code dil} es la dilatación de la malla del delantal.
 */
final class TirantesDelantal {
    private TirantesDelantal() {}

    private static final float ANCHO = 1.2F, GROSOR = 0.5F, X = 2.6F, BAJADA_ESPALDA = 7F;
    private static final int PASOS = 5;
    /** La cinta de la cintura: donde terminan los tirantes por la espalda. */
    private static final float Y_CINTA = BAJADA_ESPALDA - 0.6F, ALTO_CINTA = 1.2F;

    static void dibujar(DelantalTirantes tipo, int rgb, float inicio, float dil, float bustoExtra, ModelPart torso,
                        MatrixStack matrices, VertexConsumerProvider vcp, int luz) {
        List<ModelPart.Cuboid> cajas = new ArrayList<>();
        float zf = -(2F + dil), zb = 2F + dil, top = -dil - GROSOR;
        // La cinta de la cintura (2026-10-11, "el delantal tiene que tener una cinta alrededor del torso que pase por
        // donde conectarian las tiras por detras"): un aro de 4 losas a la altura donde terminan los tirantes.
        float ox = 4F + dil + GROSOR, oz = 2F + dil + GROSOR;
        float saleCinta = bustoExtra * 0.3F;
        cajas.add(SombreroRenderer.caja(-ox, Y_CINTA, -oz - saleCinta, 2 * ox, ALTO_CINTA, GROSOR + saleCinta));   // frente
        cajas.add(SombreroRenderer.caja(-ox, Y_CINTA, zb, 2 * ox, ALTO_CINTA, GROSOR));                  // espalda
        cajas.add(SombreroRenderer.caja(-ox, Y_CINTA, zf, GROSOR, ALTO_CINTA, zb - zf));                 // costado izq.
        cajas.add(SombreroRenderer.caja(ox - GROSOR, Y_CINTA, zf, GROSOR, ALTO_CINTA, zb - zf));         // costado der.
        boolean conTirantes = tipo != null && tipo != DelantalTirantes.SIN;
        for (int lado = -1; lado <= 1 && conTirantes; lado += 2) {
            float x0 = lado * X - ANCHO / 2F;
            // sobre el hombro, de adelante a atrás
            cajas.add(SombreroRenderer.caja(x0, top, zf - GROSOR, ANCHO, GROSOR, zb - zf + 2 * GROSOR));
            // frente: de arriba al borde de arriba del delantal; sobre las cúpulas del busto sale un poco más
            float yF = inicio + 0.4F;
            float y = top;
            while (y < yF) {
                float sig = Math.min(yF, y < 1.5F ? 1.5F : y < 7.5F ? Math.min(7.5F, yF) : yF);
                boolean enBusto = y >= 1.5F && y < 7.5F;
                float sale = enBusto ? bustoExtra : 0F;
                cajas.add(SombreroRenderer.caja(x0, y, zf - GROSOR - sale, ANCHO, sig - y, GROSOR + sale));
                y = sig;
            }
            // espalda
            if (tipo == DelantalTirantes.CRUZADOS) {
                float paso = (BAJADA_ESPALDA - top) / PASOS;
                for (int k = 0; k < PASOS; k++) {
                    float t = (k + 0.5F) / PASOS;
                    float cx = lado * X * (1F - 2F * t);
                    float g = lado > 0 ? GROSOR + 0.1F : GROSOR;
                    cajas.add(SombreroRenderer.caja(cx - ANCHO / 2F, top + k * paso, zb, ANCHO, paso + 0.3F, g));
                }
            } else {
                cajas.add(SombreroRenderer.caja(x0, top, zb, ANCHO, BAJADA_ESPALDA - top, GROSOR));
            }
        }
        ModelPart parte = new ModelPart(cajas, Map.of());
        GarmentFeatureRenderer.dibujarModelPart(parte, CuerpoGeometria.Superficie.CUERPO,
                SombreroRenderer.textura(rgb, SombreroPatron.LISO), torso, matrices, vcp, luz);
    }
}
