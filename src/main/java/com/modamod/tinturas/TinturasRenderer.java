package com.modamod.tinturas;

import com.modamod.render.LedGlowLayer;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Registro (ClientModInitializer): BlockEntityRendererFactories.register(TinturasMod.TINTURAS_BLOCK_ENTITY, ctx -> new TinturasRenderer()). */
public class TinturasRenderer extends GeoBlockRenderer<TinturasBlockEntity> {
    public TinturasRenderer() {
        super(new TinturasGeoModel());
        // Los tubos de nivel y su vidrio, translúcidos (alfa parcial en el atlas), en una pasada propia: el resto del modelo va en la capa
        // común de las máquinas, que es la que deja ver el brillo de LED y pantallita con Sodium + Iris (2026-11-14).
        addRenderLayer(new software.bernie.geckolib.renderer.layer.GeoRenderLayer<>(this) {
            @Override
            public void render(net.minecraft.client.util.math.MatrixStack matrices, TinturasBlockEntity be,
                               software.bernie.geckolib.cache.object.BakedGeoModel modelo,
                               @org.jetbrains.annotations.Nullable net.minecraft.client.render.RenderLayer tipo,
                               net.minecraft.client.render.VertexConsumerProvider buffers,
                               @org.jetbrains.annotations.Nullable net.minecraft.client.render.VertexConsumer buffer,
                               float parcial, int luz, int overlay) {
                net.minecraft.client.render.RenderLayer capa =
                        net.minecraft.client.render.RenderLayer.getEntityTranslucent(getTextureLocation(be));
                soloTubos = true;
                try {
                    reRender(modelo, matrices, buffers, be, capa, buffers.getBuffer(capa), parcial, luz, overlay, -1);
                } finally {
                    soloTubos = false;
                }
            }
        });
        addRenderLayer(new LedGlowLayer<>(this, TinturasGeoModel.ANCHO_ATLAS, TinturasGeoModel.ALTO_ATLAS,
                TinturasGeoModel.LEDS, TinturasGeoModel::coloresLed));
        // La prenda enrollada en el rodillo con su ícono real (2026-09-29,
        // "reemplazar esos huesos por el item nuevo" / "enredar el icono
        // alrededor del rodillo"): ancla en un hijo de "roller", así hereda
        // el giro. Solo mientras tiñe.
        // Pantallita siempre iluminada (2026-09-29).
        addRenderLayer(new com.modamod.render.PantallaGlowLayer<>(this,
                be -> com.modamod.render.PantallaMaquina.glow(
                        com.modamod.util.MaquinaCreativa.textura(TinturasGeoModel.TEX, com.modamod.util.MaquinaCreativa.es(be)),
                        com.modamod.render.PantallaMaquina.PANEL_TINTURAS, be.getPos())));
        addRenderLayer(new com.modamod.render.PrendaEnMaquinaLayer<>(this, "prenda_remera",
                // Sigue en el rodillo mientras espera en la Salida (2026-09-29,
                // "desaparece cuando se termina de procesar en vez de cuando se retira").
                be -> be.estado() == TinturasBlockEntity.Estado.TINIENDO ? be.getPrendaEntrada() : be.getSalida(),
                // Enrollada (2026-09-29; z 3.2 → -3.2 al girar el modelo 2026-10-15): eje del rodillo en (y 12.2, z -3.2), largo 12.4;
                // su sección (dos cuadrados de 2.9 cruzados) llega a ~2.05 del eje.
                0f, 12.2f, -3.2f, 12f, 2.2f, com.modamod.render.PrendaEnMaquinaLayer.Apoyo.ENROLLADA_EJE_X_FRENTE_MENOS_Z));
        // La prenda terminada sobre la bandeja de salida, que se desliza hacia la
        // cinta con el hueso "cargo" (2026-10-04, "el cargo tiene una prenda random
        // habria q ponerle el icono de la prenda y empalmarla con la animacion de la
        // cinta"): reemplaza al cubito de color que traía el modelo.
        addRenderLayer(new com.modamod.render.PrendaEnMaquinaLayer<>(this, "cargo",
                be -> be.getSalida(), -5.6f, 5.08f, 0f, 2.4f,
                com.modamod.render.PrendaEnMaquinaLayer.Apoyo.ACOSTADA_CUELLO_MENOS_Z));
    }

    /** La pasada de los tubos está en curso: solo se dibujan ellos. */
    private boolean soloTubos;
    /** La pasada en curso es una capa (tubos o brillo) y no la principal. */
    private boolean esCapa;

    @Override
    public void renderRecursively(net.minecraft.client.util.math.MatrixStack matrices, TinturasBlockEntity be,
                                  software.bernie.geckolib.cache.object.GeoBone hueso, net.minecraft.client.render.RenderLayer tipo,
                                  net.minecraft.client.render.VertexConsumerProvider buffers, net.minecraft.client.render.VertexConsumer buffer,
                                  boolean isReRender, float parcial, int luz, int overlay, int color) {
        esCapa = isReRender;
        super.renderRecursively(matrices, be, hueso, tipo, buffers, buffer, isReRender, parcial, luz, overlay, color);
    }

    /** Qué píxeles del atlas son translúcidos (alfa parcial): el líquido de los tubos y su vidrio. */
    private static boolean[] translucido;

    private static boolean esTranslucida(software.bernie.geckolib.cache.object.GeoQuad cara) {
        if (translucido == null) {
            net.minecraft.client.texture.NativeImage atlas = com.modamod.render.ClothingTextureCache.imagenBase(TinturasGeoModel.TEX);
            if (atlas == null) return false;
            boolean[] m = new boolean[TinturasGeoModel.ANCHO_ATLAS * TinturasGeoModel.ALTO_ATLAS];
            for (int k = 0; k < m.length; k++) {
                int alfa = atlas.getColor(k % TinturasGeoModel.ANCHO_ATLAS, k / TinturasGeoModel.ANCHO_ATLAS) >>> 24;
                m[k] = alfa > 0 && alfa < 255;
            }
            translucido = m;
        }
        float u = 0f, v = 0f;
        for (var vertice : cara.vertices()) { u += vertice.texU(); v += vertice.texV(); }
        int x = Math.min(TinturasGeoModel.ANCHO_ATLAS - 1, Math.max(0, (int) (u / cara.vertices().length * TinturasGeoModel.ANCHO_ATLAS)));
        int y = Math.min(TinturasGeoModel.ALTO_ATLAS - 1, Math.max(0, (int) (v / cara.vertices().length * TinturasGeoModel.ALTO_ATLAS)));
        return translucido[y * TinturasGeoModel.ANCHO_ATLAS + x];
    }

    @Override
    public void createVerticesOfQuad(software.bernie.geckolib.cache.object.GeoQuad cara, org.joml.Matrix4f pose, org.joml.Vector3f normal,
                                     net.minecraft.client.render.VertexConsumer buffer, int luz, int overlay, int color) {
        boolean tubo = esTranslucida(cara);
        // Lo translúcido sale solo en su pasada; en la principal se saltea y en la suya se saltea todo lo demás.
        if (soloTubos ? !tubo : tubo && !esCapa) return;
        super.createVerticesOfQuad(cara, pose, normal, buffer, luz, overlay, color);
    }
}
