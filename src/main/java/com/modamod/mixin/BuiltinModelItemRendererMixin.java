package com.modamod.mixin;

import com.modamod.sublimadora.BannerHD;
import com.modamod.sublimadora.ModItems;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Escudo con la foto del banner (2026-10-06): fija la foto mientras se dibuja el escudo; los banners la fijan por su block entity. */
@Mixin(BuiltinModelItemRenderer.class)
public abstract class BuiltinModelItemRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void modamod$entra(ItemStack stack, ModelTransformationMode modo, MatrixStack m, VertexConsumerProvider v, int luz, int overlay, CallbackInfo ci) {
        BannerHD.contexto = stack.isOf(Items.SHIELD) ? BannerHD.Contexto.de(stack.get(ModItems.BANNER_ESTAMPA),
                stack.get(com.modamod.item.ModamodComponents.CAPAS_TINTE)) : null;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void modamod$sale(ItemStack stack, ModelTransformationMode modo, MatrixStack m, VertexConsumerProvider v, int luz, int overlay, CallbackInfo ci) {
        BannerHD.contexto = null;
    }
}
