package com.modamod.render;

import com.modamod.item.CaraItem;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja máscara y barbijo puestos (2026-10-24, "quiero una mascara y un barbijo tinteables y sublimables"): el modelo
 * de Blockbench en el marco de la cabeza con la tela teñida, y encima, en la misma malla, la foto de la Sublimadora
 * (transparente fuera del panel del frente).
 */
public final class CaraRenderer {
    private CaraRenderer() {}

    /** Blanco de fábrica, como el sombrero y el retazo. */
    private static final int BLANCO = 0xF2F2F6;

    /** La que muestra la vista previa de Tintes o de la Sublimadora: reemplaza a la puesta del mismo tipo. */
    @Nullable public static ItemStack override;

    /**
     * La de la vista previa: la que puso Tintes o la Sublimadora, o el accesorio que muestran la Estilista y la Modeladora
     * ({@code sombreroOverride}) cuando es una máscara o un barbijo: el camino de los accesorios no las dibuja, así que sin
     * esto no se veían ahí.
     */
    @Nullable
    static ItemStack vista() {
        ItemStack v = override != null ? override : GarmentFeatureRenderer.sombreroOverride;
        return v != null && v.getItem() instanceof CaraItem ? v : null;
    }

    static void dibujar(LivingEntity entidad, BipedEntityModel<?> biped, MatrixStack matrices,
                        VertexConsumerProvider vcp, int luz) {
        if (!biped.head.visible) return;
        List<ItemStack> puestas = new ArrayList<>();
        TrinketsApi.getTrinketComponent(entidad).ifPresent(c -> {
            for (var par : c.getAllEquipped()) if (par.getRight().getItem() instanceof CaraItem) puestas.add(par.getRight());
        });
        ItemStack vista = vista();
        if (vista != null) {
            boolean barbijo = ((CaraItem) vista.getItem()).barbijo;
            puestas.removeIf(s -> ((CaraItem) s.getItem()).barbijo == barbijo);
            puestas.add(vista);
        }
        for (ItemStack s : puestas) dibujarUna(s, biped, matrices, vcp, luz);
    }

    /** Una máscara o un barbijo sobre la cabeza de {@code biped} (el Maniquí, 2026-10-25). */
    public static void dibujar(ItemStack stack, BipedEntityModel<?> biped, MatrixStack matrices, VertexConsumerProvider vcp, int luz) {
        if (biped.head.visible && stack.getItem() instanceof CaraItem) dibujarUna(stack, biped, matrices, vcp, luz);
    }

    /** La caja que abarca el modelo de la máscara o el barbijo, para apuntarle apliques en la Mesa de estilado (2026-11-04). */
    public static List<SombreroRenderer.Caja> cajas(ItemStack stack) {
        if (!(stack.getItem() instanceof CaraItem item)) return List.of();
        float[][] l = CapuchaArchivo.limites("cara/" + item.modelo);
        return l == null ? List.of() : List.of(new SombreroRenderer.Caja(0, l[0], l[1]));
    }

    private static void dibujarUna(ItemStack stack, BipedEntityModel<?> biped, MatrixStack matrices, VertexConsumerProvider vcp, int luz) {
        ModelPart cabeza = biped.head;
        CaraItem item = (CaraItem) stack.getItem();
        var capas = stack.getOrDefault(com.modamod.item.ModamodComponents.CAPAS_TINTE, List.<com.modamod.region.RegionResolver.CapaPatron>of());
        // El casco para apliques sin teñir es solo un soporte: no dibuja tela, se ve la cabeza de abajo.
        if (!(CaraItem.CASCO.equals(item.modelo) && capas.isEmpty())) {
            CapuchaArchivo.cara(item.modelo, ZonaTextura.plana(BLANCO, capas, false), cabeza, matrices, vcp, luz);
        }
        Identifier foto = com.modamod.sublimadora.BannerHD.foto(com.modamod.sublimadora.BannerHD.Forma.CARA,
                com.modamod.sublimadora.Estampas.frente(stack));
        if (foto != null) CapuchaArchivo.cara(item.modelo, foto, cabeza, matrices, vcp, luz);
        // Apliques (2026-11-04): sobre la cabeza, a ras del modelo.
        ApliqueRenderer.dibujar(stack, 0f, biped, matrices, vcp, luz);
    }
}
