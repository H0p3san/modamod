package com.modamod.render;

import com.modamod.guardarropas.MaletinItem;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * El maletín como accesorio de cajas (2026-10-23, "queria que el maletin tambien sea colocable en el mundo y editable en la
 * estacion de tintes y de apliques"): las mismas cajas del modelo de siempre, con 3 zonas que se tiñen y llevan patrones como
 * el sombrero o la banda (0 cuero, 1 costuras y manija, 2 herrajes), colgando de la mano derecha. Al ser un accesorio, Tintes,
 * la Estilista y el ícono lo dibujan con el mismo código que al resto; los apliques van sobre sus cajas.
 */
public final class MaletinRenderer {

    private MaletinRenderer() {}

    /** Las cajas del modelo del ítem (unidades de 1/16 de bloque, Y hacia arriba) y su zona. */
    private static final float[][] MODELO = {
            {1.5f, 2.0f, 5.5f, 14.5f, 7.0f, 10.5f, 0},
            {1.5f, 7.3f, 5.5f, 14.5f, 11.0f, 10.5f, 0},
            {1.4f, 6.9f, 5.4f, 14.6f, 7.4f, 10.6f, 1},
            {1.0f, 2.0f, 5.1f, 1.5f, 11.0f, 10.9f, 1},
            {14.5f, 2.0f, 5.1f, 15.0f, 11.0f, 10.9f, 1},
            {6.0f, 11.0f, 7.4f, 6.8f, 12.6f, 8.6f, 2},
            {9.2f, 11.0f, 7.4f, 10.0f, 12.6f, 8.6f, 2},
            {6.0f, 12.6f, 7.4f, 10.0f, 13.6f, 8.6f, 1},
            {4.0f, 6.2f, 10.5f, 5.6f, 8.2f, 11.0f, 2},
            {10.4f, 6.2f, 10.5f, 12.0f, 8.2f, 11.0f, 2},
            {4.0f, 6.2f, 5.0f, 5.6f, 8.2f, 5.5f, 2},
            {10.4f, 6.2f, 5.0f, 12.0f, 8.2f, 5.5f, 2},
            {1.0f, 10.2f, 10.5f, 2.6f, 11.2f, 11.0f, 2},
            {13.4f, 10.2f, 10.5f, 15.0f, 11.2f, 11.0f, 2},
            {1.0f, 1.8f, 10.5f, 2.6f, 2.8f, 11.0f, 2},
            {13.4f, 1.8f, 10.5f, 15.0f, 2.8f, 11.0f, 2},
            {2.5f, 1.2f, 6.0f, 3.7f, 2.0f, 7.2f, 2},
            {12.3f, 1.2f, 6.0f, 13.5f, 2.0f, 7.2f, 2},
            {2.5f, 1.2f, 8.8f, 3.7f, 2.0f, 10.0f, 2},
            {12.3f, 1.2f, 8.8f, 13.5f, 2.0f, 10.0f, 2},
    };

    /** Escala del modelo a px del brazo, y dónde queda la manija: en la mano (y 9,5 del brazo, centrada en el brazo). */
    private static final float ESCALA = 0.6f, MANIJA_ARRIBA = 13.6f, MANO_Y = 9.5f, BRAZO_X = -1f;

    /**
     * Las cajas en el marco del brazo derecho (px, Y hacia abajo): el largo del maletín va hacia adelante (Z del brazo) y el
     * ancho hacia el costado, como cuando se lleva colgando de la mano.
     */
    private static final List<SombreroRenderer.Caja> CAJAS = new ArrayList<>();
    static {
        for (float[] e : MODELO) {
            float x0 = BRAZO_X + (e[2] - 8f) * ESCALA, x1 = BRAZO_X + (e[5] - 8f) * ESCALA;
            float y0 = MANO_Y + (MANIJA_ARRIBA - e[4]) * ESCALA, y1 = MANO_Y + (MANIJA_ARRIBA - e[1]) * ESCALA;
            float z0 = (e[0] - 8f) * ESCALA, z1 = (e[3] - 8f) * ESCALA;
            CAJAS.add(new SombreroRenderer.Caja((int) e[6], new float[] {x0, y0, z0}, new float[] {x1, y1, z1}));
        }
    }

    private static final ModelPart[] PARTES = new ModelPart[3];

    public static List<SombreroRenderer.Caja> cajas() {
        return CAJAS;
    }

    private static ModelPart parte(int zona) {
        if (PARTES[zona] == null) {
            List<ModelPart.Cuboid> cubos = new ArrayList<>();
            for (SombreroRenderer.Caja c : CAJAS) {
                if (c.zona() != zona) continue;
                float[] a = c.min(), b = c.max();
                cubos.add(SombreroRenderer.caja(a[0], a[1], a[2], b[0] - a[0], b[1] - a[1], b[2] - a[2]));
            }
            PARTES[zona] = new ModelPart(cubos, Map.of());
        }
        return PARTES[zona];
    }

    /** En la mano derecha de {@code biped}, con sus zonas teñidas y los apliques encima. */
    public static void dibujar(ItemStack maletin, BipedEntityModel<?> biped, MatrixStack matrices,
                               VertexConsumerProvider vertexConsumers, int luz) {
        if (!(maletin.getItem() instanceof MaletinItem)) return;
        vertexConsumers = EfectoTrim.envolver(maletin, vertexConsumers);
        for (int z = 0; z < 3; z++) {
            GarmentFeatureRenderer.dibujarModelPart(parte(z), CuerpoGeometria.Superficie.CUERPO,
                    ZonaTextura.de(maletin, z, false), biped.rightArm, matrices, vertexConsumers, luz);
        }
        EfectoTrim.volcar();
        ApliqueRenderer.dibujar(maletin, 0f, biped, matrices, vertexConsumers, luz);
    }
}
