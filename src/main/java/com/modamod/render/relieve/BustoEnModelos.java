package com.modamod.render.relieve;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/**
 * El busto en los modelos de cualquier mod (2026-10-02, "armar un algo que
 * adapte las prendas y armaduras de cualquier mod (quizas con armaduras
 * pueden mantenerse rigidas"): mientras se dibuja un jugador con busto, cada
 * modelo humanoide que NO es el suyo (pecheras, armaduras de otros mods,
 * cosméticos, ropa de otros mods) recibe las cúpulas en su mismo dibujo —
 * misma textura, mismo tinte, y también sus pasadas de ajustes y de brillo,
 * porque se agregan dentro de {@code AnimalModel.render}
 * ({@code mixin/BustoEnModelosMixin}).
 *
 * <p>No hace falta conocer al mod: las UV salen de la cara del frente del
 * torso de ese modelo y el inflado de dónde está esa cara (una pechera vanilla
 * va 1 px por fuera del cuerpo). Las armaduras quedan rígidas; lo demás
 * rebota como la ropa del mod. Los modelos que no son humanoides (armaduras
 * GeckoLib con modelo propio) no se tocan.
 */
public final class BustoEnModelos {

    private BustoEnModelos() {}

    /** El busto y el modelo del jugador que se está dibujando (lo pone {@code LivingEntityRendererMixin}). */
    @Nullable
    public static BustoRender.Busto enEntidad;
    @Nullable
    public static EntityModel<?> modeloPrincipal;
    /** El slot de armadura que está dibujando {@code ArmorFeatureRenderer}, o null. */
    @Nullable
    public static EquipmentSlot slotArmadura;

    /** Al final de {@code AnimalModel.render}: si es un humanoide ajeno sobre un jugador con busto, las cúpulas. */
    public static void alDibujar(Object modelo, MatrixStack matrices, VertexConsumer vc, int luz, int ov, int color) {
        BustoRender.Busto busto = enEntidad;
        if (busto == null || !(modelo instanceof BipedEntityModel<?> biped) || modelo == modeloPrincipal) return;
        // Las piernas de la armadura también dibujan el torso (la cintura): el
        // busto solo la pechera; la cola, la pechera y las piernas (2026-10-02).
        boolean conBusto = slotArmadura == null || slotArmadura == EquipmentSlot.CHEST;
        boolean conCola = conBusto || slotArmadura == EquipmentSlot.LEGS;
        if (!conCola) return;
        ModelPart torso = biped.body;
        if (!torso.visible || torso.hidden || torso.cuboids.isEmpty()) return;
        boolean rigido = slotArmadura != null;
        matrices.push();
        torso.rotate(matrices);
        Frente f = conBusto ? cara(torso.cuboids.get(0), true) : null;
        if (f != null) BustoRender.dibujar(busto, matrices, vc, luz, ov, color, f.uv(), f.inflado(), 0f, rigido);
        // La cola con las UV de la cara de atrás: lo transparente de la textura
        // (unas piernas de armadura que solo pintan la cintura) no se ve.
        Frente atras = cara(torso.cuboids.get(0), false);
        if (atras != null) BustoRender.dibujarCola(busto, matrices, vc, luz, ov, color, atras.uv(), atras.inflado(), 0f,
                rigido, false);
        matrices.pop();
    }

    /** La cara del frente del torso de un modelo: sus UV y cuánto sale del cuerpo. */
    private record Frente(BustoRender.Uv uv, float inflado) {}

    /** La cara del frente ({@code frente}) o de atrás del torso de un modelo: sus UV y cuánto sale del cuerpo. */
    @Nullable
    private static Frente cara(ModelPart.Cuboid cuboide, boolean frente) {
        for (ModelPart.Quad q : cuboide.sides) {
            if (frente ? q.direction.z() > -0.5f : q.direction.z() < 0.5f) continue;
            float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z = 0f;
            float uX0 = 0, uX1 = 0, vY0 = 0, vY1 = 0;
            for (ModelPart.Vertex v : q.vertices) {
                if (v.pos.x() < x0) { x0 = v.pos.x(); uX0 = v.u; }
                if (v.pos.x() > x1) { x1 = v.pos.x(); uX1 = v.u; }
                if (v.pos.y() < y0) { y0 = v.pos.y(); vY0 = v.v; }
                if (v.pos.y() > y1) { y1 = v.pos.y(); vY1 = v.v; }
                z = v.pos.z();
            }
            if (!frente) z = -z;
            // Solo torsos de humanoide (8 px de ancho, más lo que se infle).
            if (x1 - x0 < 7.5f || x1 - x0 > 12f || y1 - y0 < 10f) return null;
            float inflado = Math.max(0f, -z - 2f);
            // Los bordes de la cara están inflados: se llevan a las coordenadas del cuerpo (−4..4, 0..12).
            return new Frente(BustoRender.Uv.deCaraDelTorso(x0 + inflado, x1 - inflado, uX0, uX1,
                    y0 + inflado, y1 - inflado, vY0, vY1), inflado);
        }
        return null;
    }
}
