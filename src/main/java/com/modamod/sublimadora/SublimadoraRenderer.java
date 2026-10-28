package com.modamod.sublimadora;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.UUID;

/**
 * Renderer del bloque. GeckoLib ya lo orienta con la propiedad
 * HORIZONTAL_FACING del estado, no hace falta rotar a mano.
 *
 * Registro (Fabric, en el ClientModInitializer):
 *   BlockEntityRendererFactories.register(ModBlocks.SUBLIMADORA_ENTITY,
 *       ctx -> new SublimadoraRenderer());
 */
public class SublimadoraRenderer extends GeoBlockRenderer<SublimadoraBlockEntity> {

    /**
     * Altura de la foto apoyada. La remera va de 10.98 a 11.40, y 11.55 la
     * dejaba a 0.15 de pixel de su cara: suficiente para que el z-buffer las
     * confundiera de lejos y parpadearan. Medio pixel entero las separa.
     */
    private static final float Y_FOTO = 11.9f / 16f;
    /** Lado del papel sobre la plancha, en bloques. */
    private static final float LADO = 5.5f / 16f;
    /** Cuanto se corre cada foto de su lado de la plancha. */
    private static final float SEPARACION = 2.9f / 16f;

    public SublimadoraRenderer() {
        super(new SublimadoraGeoModel());
        // Capa emisiva de los LEDs (2026-09-21, "que brillen de verdad") —
        // NO es la AutoGlowingGeoLayer que se había sacado el 2026-09-19
        // (esa esperaba un sublimator_atlas_glowmask.png real de recurso,
        // incompatible con un color de LED que cambia en runtime); ver
        // com.modamod.render.PantallaLed y LedGlowLayer.
        addRenderLayer(new com.modamod.render.LedGlowLayer<>(this, SublimadoraGeoModel.ANCHO_ATLAS,
                SublimadoraGeoModel.ALTO_ATLAS, SublimadoraGeoModel.LEDS, SublimadoraGeoModel::coloresLed));
        // La prenda cargada con su ícono real (2026-09-29, "reemplazar esos
        // huesos por el item nuevo"), debajo de las fotos (Y_FOTO 11.9).
        // Pantallita siempre iluminada (2026-09-29).
        addRenderLayer(new com.modamod.render.PantallaGlowLayer<>(this,
                be -> com.modamod.render.PantallaMaquina.glow(SublimadoraGeoModel.TEX,
                        com.modamod.render.PantallaMaquina.PANEL_128, be.getPos())));
        addRenderLayer(new com.modamod.render.PrendaEnMaquinaLayer<>(this, "REMERA",
                be -> !be.getRemera().isEmpty() ? be.getRemera() : be.getSalida(), 0f, 11.85f, -0.6f, 10f,
                com.modamod.render.PrendaEnMaquinaLayer.Apoyo.ACOSTADA_FRENTE_MENOS_Z));
        // La prenda terminada sobre la bandeja de salida, que se desliza hacia la
        // cinta con el hueso "cargo" (2026-10-04, "el cargo tiene una prenda random
        // habria q ponerle el icono de la prenda y empalmarla con la animacion de la
        // cinta"): reemplaza al cubito de color que traía el modelo.
        addRenderLayer(new com.modamod.render.PrendaEnMaquinaLayer<>(this, "cargo",
                be -> be.getSalida(), -5.6f, 5.08f, 0f, 2.4f,
                com.modamod.render.PrendaEnMaquinaLayer.Apoyo.ACOSTADA_FRENTE_MENOS_Z));
    }

    @Override
    public void render(SublimadoraBlockEntity be, float parcial, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int luz, int overlay) {
        super.render(be, parcial, matrices, vertexConsumers, luz, overlay);
        dibujarFotoCargada(be, matrices, vertexConsumers, luz, overlay);
    }

    /**
     * La foto esperando ser prensada, apoyada sobre la remera.
     *
     * Es el unico indicio de que hay una foto puesta: la maquina se traga el
     * papel y hasta que no termina el ciclo no hay forma de saber si estaba
     * cargada. No es un hueso del modelo porque la textura cambia con cada
     * foto, y un hueso tiene una sola textura, la del atlas.
     */
    private void dibujarFotoCargada(SublimadoraBlockEntity be, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int luz, int overlay) {
        // getFotoVisible y no getFotoCargada: la de dibujar sigue un rato
        // mas mientras la tapa baja, y ya devuelve null con la tapa cerrada.
        java.util.EnumMap<Estampa.Cara, Identifier> aDibujar =
                new java.util.EnumMap<>(Estampa.Cara.class);
        for (Estampa.Cara cara : Estampa.Cara.values()) {
            UUID id = be.getFotoVisible(cara);
            if (id == null) continue;
            Identifier textura = texturaDe(id);
            if (textura != null) aDibujar.put(cara, textura);
        }
        if (aDibujar.isEmpty()) return;

        matrices.push();
        // Mismo centrado y misma rotacion que le aplica GeckoLib al modelo,
        // asi el papel acompana al bloque cuando esta girado.
        matrices.translate(0.5f, 0f, 0.5f);
        rotateBlock(getFacing(be), matrices);

        MatrixStack.Entry entrada = matrices.peek();
        float m = LADO / 2f;
        for (var e : aDibujar.entrySet()) {
            // La del frente hacia el lado del panel, que en el modelo es -Z.
            float cz = e.getKey() == Estampa.Cara.FRENTE ? -SEPARACION : SEPARACION;
            VertexConsumer buffer = vertexConsumers.getBuffer(
                    RenderLayer.getEntityTranslucent(e.getValue()));
            vertice(buffer, entrada, -m, Y_FOTO, cz + m, 0f, 1f, luz, overlay);
            vertice(buffer, entrada, m, Y_FOTO, cz + m, 1f, 1f, luz, overlay);
            vertice(buffer, entrada, m, Y_FOTO, cz - m, 1f, 0f, luz, overlay);
            vertice(buffer, entrada, -m, Y_FOTO, cz - m, 0f, 0f, luz, overlay);
        }

        matrices.pop();
    }

    private static void vertice(VertexConsumer buffer, MatrixStack.Entry entrada,
                                float x, float y, float z, float u, float v, int luz, int overlay) {
        buffer.vertex(entrada.getPositionMatrix(), x, y, z)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(overlay)
                .light(luz)
                .normal(entrada, 0f, 1f, 0f);
    }

    private static Identifier texturaDe(UUID id) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("camerapture")) return null;
        try {
            CameraptureClientCompat.Foto foto = CameraptureClientCompat.foto(id);
            return foto == null ? null : foto.textura();
        } catch (Throwable ignorado) {
            return null;
        }
    }
}
