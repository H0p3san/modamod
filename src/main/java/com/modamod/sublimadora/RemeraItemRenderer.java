package com.modamod.sublimadora;

import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Dibuja la remera con la foto estampada encima.
 *
 * La remera en si la sigue dibujando vanilla: no reimplementamos su aspecto,
 * se le pide al ItemRenderer el modelo item/corte_<corte> -un item/generated
 * normal- y despues se le pega la foto adelante. Reimplementarla a mano
 * habria costado el relieve y el sombreado que vanilla le da gratis a
 * cualquier item plano.
 */
public class RemeraItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {

    // El modelo con el aspecto real de cada corte sale de Variante. Tiene que
    // ser OTRO archivo que item/remera: ese hereda de builtin/entity para
    // llegar hasta aca, y pedirlo de nuevo seria recursion infinita.

    /**
     * Area imprimible para las estampas que van adentro de la tela: la remera
     * dibujada va de x=2 a x=14 de los 16. Todo lo que guarda la Estampa se
     * multiplica por esto.
     */
    private static final float AREA = 12f / 16f;

    /**
     * Caja del full print: el rectangulo mas grande que entra ENTERO adentro
     * de la silueta de la remera, columnas 4 a 11 y filas 3 a 13 del sprite.
     *
     * No es el cuadrado completo de 12x12 a proposito. La remera tiene forma
     * de T: en las filas de arriba solo ocupa el cuello y en las de abajo se
     * angosta, asi que un cuadrado dejaria la foto sobresaliendo por las
     * cuatro esquinas, fuera del contorno de la prenda. Cubrir tambien las
     * mangas pide enmascarar la foto contra la silueta, que es otro trabajo.
     */
    private static final float LLENO_ANCHO = 8f / 16f;
    private static final float LLENO_ALTO = 11f / 16f;
    /**
     * Donde se apoya la estampa. El modelo item/generated tiene 1/16 de
     * espesor centrado en el origen, asi que sus caras estan en 0.03125
     * exacto.
     *
     * Los 0.0022 de mas NO son decorativos: con la estampa a 0.0313 quedaba
     * a cinco cienmilesimas de la cara, o sea coplanar para cualquier efecto
     * practico, y de lejos el z-buffer perdia precision y las dos superficies
     * se peleaban. Eso era el parpadeo de la remera tirada en el piso.
     */
    private static final float Z_TELA = 0.03345f;

    @Override
    public void render(ItemStack stack, ModelTransformationMode modo, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int luz, int overlay) {
        MinecraftClient cliente = MinecraftClient.getInstance();
        BakedModel base = ((FabricBakedModelManager) cliente.getBakedModelManager())
                .getModel(RemeraItem.variante(stack).modeloItem());

        if (base != null) {
            matrices.push();
            // renderItem vuelve a hacer translate(-0.5,-0.5,-0.5) por su
            // cuenta, y vanilla ya lo hizo antes de llamarnos. Sin esto la
            // remera sale corrida un bloque entero.
            matrices.translate(0.5f, 0.5f, 0.5f);
            // Con NONE no vuelve a aplicar la transformacion de perspectiva:
            // esa ya la aplico vanilla con el modelo builtin.
            cliente.getItemRenderer().renderItem(stack, ModelTransformationMode.NONE, false,
                    matrices, vertexConsumers, luz, overlay, base);
            matrices.pop();
        }

        matrices.push();
        matrices.translate(0.5f, 0.5f, 0.5f);
        for (Estampa.Cara cara : Estampa.Cara.values()) {
            Estampa estampa = RemeraItem.estampaDe(stack, cara);
            if (estampa == null) continue;
            CameraptureClientCompat.Foto foto = fotoDe(estampa.foto());
            if (foto == null) continue;   // todavia descargando, o sin Camerapture

            if (estampa.cubrir()) {
                // Full print: la foto va enmascarada contra la silueta, asi
                // llega tambien a las mangas. Si la composicion no salio -no
                // se pudo leer la textura todavia, o fallo- se cae al
                // rectangulo del torso, que es mas chico pero nunca se sale
                // del contorno.
                Identifier compuesta = EstampaTextures.fullPrint(
                        RemeraItem.variante(stack), estampa.foto(),
                        foto.textura(), foto.ancho(), foto.alto());
                if (compuesta != null) {
                    dibujarSilueta(matrices, vertexConsumers, compuesta, cara, luz, overlay);
                    continue;
                }
            }
            dibujar(matrices, vertexConsumers, foto, estampa, cara, luz, overlay);
        }
        matrices.pop();
    }

    /**
     * Se llama por frame, pero el acceso a Camerapture esta aislado detras de
     * isModLoaded para que la clase puente no se cargue si el mod no esta.
     */
    private static CameraptureClientCompat.Foto fotoDe(UUID id) {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("camerapture")) return null;
        try {
            return CameraptureClientCompat.foto(id);
        } catch (Throwable ignorado) {
            return null;   // cambio de version del mod: mejor sin estampa que crashear
        }
    }

    /**
     * Un cuadrado con la foto, en una sola cara de la remera.
     *
     * Va con getEntityTranslucent y no getEntityCutout: cutout hace alfa
     * BINARIO -o solido o invisible- y se come cualquier degrade de un PNG
     * con transparencia parcial, que es justo lo que uno sube para un logo.
     */
    private static void dibujar(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                CameraptureClientCompat.Foto foto, Estampa estampa,
                                Estampa.Cara cara, int luz, int overlay) {
        float ancho = (estampa.cubrir() ? LLENO_ANCHO : AREA) * estampa.escala();
        float alto = (estampa.cubrir() ? LLENO_ALTO : AREA) * estampa.escala();

        // Por defecto la foto entra ENTERA en su caja: la apaisada queda mas
        // baja, la vertical mas angosta, y sobra tela alrededor.
        //
        // Con cubrir, al reves: la caja queda del tamano completo y lo que se
        // achica es el pedazo de foto que se muestra, recortando el eje que
        // sobra desde el centro. Es la diferencia entre "que se vea toda la
        // imagen" y "que no quede tela sin estampar".
        float u0 = 0f, u1 = 1f, v0 = 0f, v1 = 1f;
        float relacionFoto = (float) foto.ancho() / foto.alto();
        float relacionCaja = ancho / alto;
        if (estampa.cubrir()) {
            if (relacionFoto > relacionCaja) {
                float visible = relacionCaja / relacionFoto;
                u0 = (1f - visible) / 2f;
                u1 = u0 + visible;
            } else if (relacionFoto < relacionCaja) {
                float visible = relacionFoto / relacionCaja;
                v0 = (1f - visible) / 2f;
                v1 = v0 + visible;
            }
        } else if (relacionFoto >= relacionCaja) {
            alto = ancho / relacionFoto;
        } else {
            ancho = alto * relacionFoto;
        }

        float cx = estampa.x() * AREA;
        float cy = estampa.y() * AREA;
        float x0 = cx - ancho / 2f, x1 = cx + ancho / 2f;
        float y0 = cy - alto / 2f, y1 = cy + alto / 2f;

        VertexConsumer buffer = vertexConsumers.getBuffer(
                RenderLayer.getEntityTranslucent(foto.textura()));
        MatrixStack.Entry entrada = matrices.peek();

        if (cara == Estampa.Cara.FRENTE) {
            // La v va invertida porque el eje Y del item crece hacia arriba y
            // el de la textura hacia abajo.
            vertice(buffer, entrada, x0, y0, Z_TELA, u0, v1, 0f, 0f, 1f, luz, overlay);
            vertice(buffer, entrada, x1, y0, Z_TELA, u1, v1, 0f, 0f, 1f, luz, overlay);
            vertice(buffer, entrada, x1, y1, Z_TELA, u1, v0, 0f, 0f, 1f, luz, overlay);
            vertice(buffer, entrada, x0, y1, Z_TELA, u0, v0, 0f, 0f, 1f, luz, overlay);
        } else {
            // Orden de vertices al reves para que la cara mire para atras y
            // no la descarte el culling, y la u espejada porque la espalda se
            // ve desde el otro lado.
            vertice(buffer, entrada, x0, y1, -Z_TELA, u1, v0, 0f, 0f, -1f, luz, overlay);
            vertice(buffer, entrada, x1, y1, -Z_TELA, u0, v0, 0f, 0f, -1f, luz, overlay);
            vertice(buffer, entrada, x1, y0, -Z_TELA, u0, v1, 0f, 0f, -1f, luz, overlay);
            vertice(buffer, entrada, x0, y0, -Z_TELA, u1, v1, 0f, 0f, -1f, luz, overlay);
        }
    }

    /**
     * La textura ya compuesta, estirada sobre TODO el cuadro del item.
     *
     * No hace falta calcular ninguna caja: la imagen trae la silueta de la
     * remera en su alfa, asi que apoyada sobre el sprite completo cae
     * exactamente donde hay tela y en ningun otro lado.
     */
    private static void dibujarSilueta(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                       Identifier textura, Estampa.Cara cara, int luz, int overlay) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(textura));
        MatrixStack.Entry entrada = matrices.peek();
        float m = 0.5f;
        if (cara == Estampa.Cara.FRENTE) {
            vertice(buffer, entrada, -m, -m, Z_TELA, 0f, 1f, 0f, 0f, 1f, luz, overlay);
            vertice(buffer, entrada, m, -m, Z_TELA, 1f, 1f, 0f, 0f, 1f, luz, overlay);
            vertice(buffer, entrada, m, m, Z_TELA, 1f, 0f, 0f, 0f, 1f, luz, overlay);
            vertice(buffer, entrada, -m, m, Z_TELA, 0f, 0f, 0f, 0f, 1f, luz, overlay);
        } else {
            vertice(buffer, entrada, -m, m, -Z_TELA, 1f, 0f, 0f, 0f, -1f, luz, overlay);
            vertice(buffer, entrada, m, m, -Z_TELA, 0f, 0f, 0f, 0f, -1f, luz, overlay);
            vertice(buffer, entrada, m, -m, -Z_TELA, 0f, 1f, 0f, 0f, -1f, luz, overlay);
            vertice(buffer, entrada, -m, -m, -Z_TELA, 1f, 1f, 0f, 0f, -1f, luz, overlay);
        }
    }

    private static void vertice(VertexConsumer buffer, MatrixStack.Entry entrada,
                                float x, float y, float z, float u, float v,
                                float nx, float ny, float nz, int luz, int overlay) {
        buffer.vertex(entrada.getPositionMatrix(), x, y, z)
                .color(0xFFFFFFFF)
                .texture(u, v)
                .overlay(overlay)
                .light(luz)
                .normal(entrada, nx, ny, nz);
    }
}
