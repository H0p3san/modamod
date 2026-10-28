package com.modamod.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.function.Function;

/**
 * La prenda cargada en la máquina, dibujada con su ícono de 64x64 real
 * ({@link IconoPrenda}) en vez de los cubos de colores de los huesos de
 * categoría — a pedido (2026-09-29, "ahora podriamos reemplazar esos huesos
 * por el item nuevo?"). Los huesos de prenda siguen en el modelo pero
 * ocultos: uno hace de ancla ({@code hueso}) y su transformación se hereda
 * (en Tintes, el giro del rodillo). GeckoLib llama a las capas por cada hueso
 * aunque esté oculto (solo saltea sus cubos).
 *
 * <p>Coordenadas en píxeles del .geo.json (Bedrock): GeckoLib espeja la X al
 * cargar, así que acá va {@code -x}.
 */
public class PrendaEnMaquinaLayer<T extends GeoAnimatable> extends GeoRenderLayer<T> {

    /** Cómo se apoya el dibujo en la máquina. */
    public enum Apoyo {
        /** Acostada sobre la mesa, el frente de la máquina en -Z: el cuello hacia el fondo. */
        ACOSTADA_FRENTE_MENOS_Z,
        /** Colgando de frente a +Z. */
        PARADA_FRENTE_MAS_Z,
        /**
         * Enrollada alrededor de un rodillo con eje en X (2026-09-29, "alguna
         * forma de enredar el icono alrededor del rodillo y que quede bien?"):
         * un cilindro de {@link #TRAMOS} caras; el ancho del ícono va a lo largo
         * del eje y su alto da la vuelta entera. Derecho visto de frente (+Z).
         */
        ENROLLADA_EJE_X,
        /** Lo mismo que {@link #ENROLLADA_EJE_X} pero derecho visto de frente en -Z (el modelo de Tintes, girado 180°, 2026-10-15). */
        ENROLLADA_EJE_X_FRENTE_MENOS_Z,
        /** Como {@link #ACOSTADA_FRENTE_MENOS_Z} girada 180° en Y: el cuello hacia -Z (la bandeja de Tintes, 2026-10-15). */
        ACOSTADA_CUELLO_MENOS_Z
    }

    private static final int TRAMOS = 32;

    private final String hueso;
    private final Function<T, ItemStack> prenda;
    private final float cx, cy, cz, lado, radio;
    private final Apoyo apoyo;

    /**
     * @param hueso  nombre del hueso ancla (se dibuja una sola vez, al pasar por él)
     * @param prenda qué prenda mostrar ahora (vacío = nada)
     * @param cx,cy,cz centro del dibujo en píxeles del .geo.json
     * @param lado   lado del cuadrado del ícono, en píxeles
     */
    public PrendaEnMaquinaLayer(GeoRenderer<T> renderer, String hueso, Function<T, ItemStack> prenda,
                                float cx, float cy, float cz, float lado, Apoyo apoyo) {
        this(renderer, hueso, prenda, cx, cy, cz, lado, 0f, apoyo);
    }

    /** @param radio solo para {@link Apoyo#ENROLLADA_EJE_X}: radio del cilindro en píxeles, centro = (cy, cz). */
    public PrendaEnMaquinaLayer(GeoRenderer<T> renderer, String hueso, Function<T, ItemStack> prenda,
                                float cx, float cy, float cz, float lado, float radio, Apoyo apoyo) {
        super(renderer);
        this.radio = radio;
        this.hueso = hueso;
        this.prenda = prenda;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.lado = lado;
        this.apoyo = apoyo;
    }

    @Override
    public void renderForBone(MatrixStack matrices, T animatable, GeoBone bone, RenderLayer renderType,
                              VertexConsumerProvider buffers, VertexConsumer buffer, float partialTick,
                              int luz, int overlay) {
        if (!bone.getName().equals(hueso)) return;
        ItemStack stack = prenda.apply(animatable);
        if (stack == null || stack.isEmpty()) return;
        Identifier icono = IconoPrenda.de(stack);
        if (icono == null) return;

        VertexConsumer vc = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(icono));
        MatrixStack.Entry e = matrices.peek();
        float x = -cx / 16f, y = cy / 16f, z = cz / 16f, m = lado / 32f;
        int ov = OverlayTexture.DEFAULT_UV;
        if (apoyo == Apoyo.ACOSTADA_FRENTE_MENOS_Z) {
            // Mirando desde el frente (-Z) hacia la mesa: la derecha es -X y el fondo +Z.
            v(vc, e, x + m, y, z + m, 0, 0, luz, ov, 0, 1, 0);
            v(vc, e, x + m, y, z - m, 0, 1, luz, ov, 0, 1, 0);
            v(vc, e, x - m, y, z - m, 1, 1, luz, ov, 0, 1, 0);
            v(vc, e, x - m, y, z + m, 1, 0, luz, ov, 0, 1, 0);
        } else if (apoyo == Apoyo.ACOSTADA_CUELLO_MENOS_Z) {
            v(vc, e, x - m, y, z - m, 0, 0, luz, ov, 0, 1, 0);
            v(vc, e, x - m, y, z + m, 0, 1, luz, ov, 0, 1, 0);
            v(vc, e, x + m, y, z + m, 1, 1, luz, ov, 0, 1, 0);
            v(vc, e, x + m, y, z - m, 1, 0, luz, ov, 0, 1, 0);
        } else if (apoyo == Apoyo.ENROLLADA_EJE_X_FRENTE_MENOS_Z) {
            float r = radio / 16f;
            for (int i = 0; i < TRAMOS; i++) {
                float v0 = i / (float) TRAMOS, v1 = (i + 1) / (float) TRAMOS;
                float a0 = (0.5f - v0) * 2f * (float) Math.PI, a1 = (0.5f - v1) * 2f * (float) Math.PI;
                float s0 = (float) Math.sin(a0), c0 = (float) Math.cos(a0);
                float s1 = (float) Math.sin(a1), c1 = (float) Math.cos(a1);
                v(vc, e, x + m, y + r * s0, z - r * c0, 0, v0, luz, ov, 0, s0, -c0);
                v(vc, e, x - m, y + r * s0, z - r * c0, 1, v0, luz, ov, 0, s0, -c0);
                v(vc, e, x - m, y + r * s1, z - r * c1, 1, v1, luz, ov, 0, s1, -c1);
                v(vc, e, x + m, y + r * s1, z - r * c1, 0, v1, luz, ov, 0, s1, -c1);
            }
        } else if (apoyo == Apoyo.ENROLLADA_EJE_X) {
            // v = 0.5 queda en el frente (+Z); v < 0.5 sube por arriba, así se
            // lee derecha de frente con el rodillo quieto. u a lo largo de X
            // (de frente la derecha es +X).
            float r = radio / 16f;
            for (int i = 0; i < TRAMOS; i++) {
                float v0 = i / (float) TRAMOS, v1 = (i + 1) / (float) TRAMOS;
                float a0 = (0.5f - v0) * 2f * (float) Math.PI, a1 = (0.5f - v1) * 2f * (float) Math.PI;
                float s0 = (float) Math.sin(a0), c0 = (float) Math.cos(a0);
                float s1 = (float) Math.sin(a1), c1 = (float) Math.cos(a1);
                v(vc, e, x - m, y + r * s0, z + r * c0, 0, v0, luz, ov, 0, s0, c0);
                v(vc, e, x + m, y + r * s0, z + r * c0, 1, v0, luz, ov, 0, s0, c0);
                v(vc, e, x + m, y + r * s1, z + r * c1, 1, v1, luz, ov, 0, s1, c1);
                v(vc, e, x - m, y + r * s1, z + r * c1, 0, v1, luz, ov, 0, s1, c1);
            }
        } else {
            // De frente a +Z: la derecha es +X, arriba +Y.
            v(vc, e, x - m, y - m, z, 0, 1, luz, ov, 0, 0, 1);
            v(vc, e, x + m, y - m, z, 1, 1, luz, ov, 0, 0, 1);
            v(vc, e, x + m, y + m, z, 1, 0, luz, ov, 0, 0, 1);
            v(vc, e, x - m, y + m, z, 0, 0, luz, ov, 0, 0, 1);
        }
    }

    private static void v(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, float u, float v,
                          int luz, int overlay, float nx, float ny, float nz) {
        vc.vertex(e.getPositionMatrix(), x, y, z)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(overlay)
                .light(luz)
                .normal(e, nx, ny, nz);
    }
}
