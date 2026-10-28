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

    static void dibujar(LivingEntity entidad, BipedEntityModel<?> biped, MatrixStack matrices,
                        VertexConsumerProvider vcp, int luz) {
        if (!biped.head.visible) return;
        List<ItemStack> puestas = new ArrayList<>();
        TrinketsApi.getTrinketComponent(entidad).ifPresent(c -> {
            for (var par : c.getAllEquipped()) if (par.getRight().getItem() instanceof CaraItem) puestas.add(par.getRight());
        });
        if (override != null && override.getItem() instanceof CaraItem o) {
            puestas.removeIf(s -> ((CaraItem) s.getItem()).barbijo == o.barbijo);
            puestas.add(override);
        }
        for (ItemStack s : puestas) dibujarUna(s, biped.head, matrices, vcp, luz);
    }

    /** Una máscara o un barbijo sobre la cabeza de {@code biped} (el Maniquí, 2026-10-25). */
    public static void dibujar(ItemStack stack, BipedEntityModel<?> biped, MatrixStack matrices, VertexConsumerProvider vcp, int luz) {
        if (biped.head.visible && stack.getItem() instanceof CaraItem) dibujarUna(stack, biped.head, matrices, vcp, luz);
    }

    private static void dibujarUna(ItemStack stack, ModelPart cabeza, MatrixStack matrices, VertexConsumerProvider vcp, int luz) {
        CaraItem item = (CaraItem) stack.getItem();
        Identifier tela = ZonaTextura.plana(BLANCO,
                stack.getOrDefault(com.modamod.item.ModamodComponents.CAPAS_TINTE, List.of()), false);
        CapuchaArchivo.cara(item.modelo, tela, cabeza, matrices, vcp, luz);
        Identifier foto = com.modamod.sublimadora.BannerHD.foto(com.modamod.sublimadora.BannerHD.Forma.CARA,
                stack.get(com.modamod.sublimadora.ModItems.ESTAMPA_FRENTE));
        if (foto != null) CapuchaArchivo.cara(item.modelo, foto, cabeza, matrices, vcp, luz);
    }
}
