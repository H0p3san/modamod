package com.modamod.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

/**
 * Tiñe todo lo que se dibuje a través de este proveedor (2026-10-04, aplique de objeto con
 * muestra de color): multiplica el color de cada vértice por un RGB, así el ítem o bloque que
 * dibuja Minecraft sale de ese tono sin tocar sus texturas.
 */
public final class TintadoVertex implements VertexConsumerProvider {

    private final VertexConsumerProvider destino;
    private final int r, g, b;

    public TintadoVertex(VertexConsumerProvider destino, int rgb) {
        this.destino = destino;
        this.r = (rgb >> 16) & 0xFF;
        this.g = (rgb >> 8) & 0xFF;
        this.b = rgb & 0xFF;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer) {
        return new Tinte(destino.getBuffer(layer));
    }

    private final class Tinte implements VertexConsumer {
        private final VertexConsumer vc;

        Tinte(VertexConsumer vc) {
            this.vc = vc;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            vc.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            vc.color(red * r / 255, green * g / 255, blue * b / 255, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            vc.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            vc.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            vc.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            vc.normal(x, y, z);
            return this;
        }
    }
}
