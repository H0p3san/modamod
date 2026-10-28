package com.modamod.mixin;

import com.modamod.render.GarmentFeatureRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vista previa de elegir cuerpo sin la segunda capa de la skin (2026-09-29,
 * "tiene que aparecer sin ropa y sin la capa de 3dsl en el cuerpo"):
 * mientras se dibuja esa vista previa ({@link GarmentFeatureRenderer#perfilOverride}
 * puesto), el jugador dice que tiene apagadas la chaqueta, las mangas y los
 * pantalones de su skin. Vanilla las esconde en {@code setModelPose} y 3D
 * Skin Layers pregunta lo mismo antes de extruir las suyas, así que ninguno
 * las dibuja. El sombrero (la capa de la cabeza) queda, igual que la cara.
 * Solo afecta ese dibujo: el override se limpia al terminar.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityCapasSkinMixin {

    @Inject(method = "isPartVisible", at = @At("HEAD"), cancellable = true)
    private void modamod$sinCapaEnVistaPrevia(PlayerModelPart parte, CallbackInfoReturnable<Boolean> cir) {
        // También con el cuerpo usado como skin (2026-09-29): la segunda capa
        // de la skin real taparía el cuerpo elegido.
        boolean cuerpoComoSkin = GarmentFeatureRenderer.perfilOverride != null
                || com.modamod.body.PerfilesDeCuerpo.de((PlayerEntity) (Object) this).siempre();
        // La capa del mod manda (2026-09-29, "la del mod manda"): con una
        // puesta, la capa vanilla no se dibuja. Con élitros la vanilla sigue
        // (va pintada en los élitros) y la del mod no se dibuja.
        if (parte == PlayerModelPart.CAPE) {
            PlayerEntity yo = (PlayerEntity) (Object) this;
            if (yo.getWorld().isClient()
                    && !yo.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).isOf(net.minecraft.item.Items.ELYTRA)
                    && GarmentFeatureRenderer.capaDe(GarmentFeatureRenderer.equipadas(yo)) != null) {
                cir.setReturnValue(false);
            }
            return;
        }
        if (cuerpoComoSkin && parte != PlayerModelPart.HAT
                && parte != PlayerModelPart.CAPE) {
            cir.setReturnValue(false);
        }
    }
}
