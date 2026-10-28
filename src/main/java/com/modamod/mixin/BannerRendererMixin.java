package com.modamod.mixin;

import com.modamod.sublimadora.BannerConImagen;
import com.modamod.sublimadora.BannerHD;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BannerBlockEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.BannerPatternsComponent;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Banner con foto a 16x (2026-10-06): el bloque (puesto, de pared, en la mano y en el inventario) y el escudo pasan por
 * {@code renderCanvas}; si hay una foto en juego se dibuja el mismo {@code ModelPart} con la textura de 1024×1024.
 */
@Mixin(BannerBlockEntityRenderer.class)
public abstract class BannerRendererMixin {

    @Inject(method = "render(Lnet/minecraft/block/entity/BannerBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at = @At("HEAD"))
    private void modamod$entra(BannerBlockEntity be, float delta, MatrixStack m, VertexConsumerProvider v, int luz, int overlay, CallbackInfo ci) {
        BannerHD.contexto = be instanceof BannerConImagen c ? BannerHD.Contexto.de(c.modamod$imagen(), c.modamod$capas()) : null;
    }

    @Inject(method = "render(Lnet/minecraft/block/entity/BannerBlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;II)V",
            at = @At("RETURN"))
    private void modamod$sale(BannerBlockEntity be, float delta, MatrixStack m, VertexConsumerProvider v, int luz, int overlay, CallbackInfo ci) {
        BannerHD.contexto = null;
    }

    @Inject(method = "renderCanvas(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/model/ModelPart;Lnet/minecraft/client/util/SpriteIdentifier;ZLnet/minecraft/util/DyeColor;Lnet/minecraft/component/type/BannerPatternsComponent;Z)V",
            at = @At("HEAD"), cancellable = true)
    private static void modamod$hd(MatrixStack matrices, VertexConsumerProvider vcp, int luz, int overlay, ModelPart lienzo,
                                      SpriteIdentifier base, boolean esBanner, DyeColor color, BannerPatternsComponent patrones,
                                      boolean brillo, CallbackInfo ci) {
        if (BannerHD.contexto == null) return;
        // El paño: teñido (2026-10-24) o el de vanilla con su color.
        Identifier tinte = BannerHD.tinte(color);
        if (tinte != null) {
            RenderLayer capa = RenderLayer.getEntityCutoutNoCull(tinte);
            lienzo.render(matrices, brillo ? ItemRenderer.getDirectItemGlintConsumer(vcp, capa, true, true) : vcp.getBuffer(capa), luz, overlay);
        } else {
            lienzo.render(matrices, base.getVertexConsumer(vcp, RenderLayer::getEntitySolid, brillo), luz, overlay);
            capaVanilla(matrices, vcp, luz, overlay, lienzo, esBanner ? TexturedRenderLayers.BANNER_BASE : TexturedRenderLayers.SHIELD_BASE, color);
        }
        // Los diseños del telar, como vanilla (antes la foto los tapaba).
        for (int i = 0; i < 16 && i < patrones.layers().size(); i++) {
            BannerPatternsComponent.Layer l = patrones.layers().get(i);
            capaVanilla(matrices, vcp, luz, overlay, lienzo, esBanner ? TexturedRenderLayers.getBannerPatternTextureId(l.pattern())
                    : TexturedRenderLayers.getShieldPatternTextureId(l.pattern()), l.color());
        }
        // La foto arriba de todo; mientras no bajó, el banner queda sin ella.
        Identifier foto = BannerHD.foto(esBanner ? BannerHD.Forma.BANNER : BannerHD.Forma.ESCUDO);
        // El color y los diseños de vanilla van en un buffer fijo que se dibuja al final del cuadro: sin descargarlo
        // antes tapaban la foto (2026-10-24, "solo estampa despues de tintar").
        if (foto != null && vcp instanceof VertexConsumerProvider.Immediate inmediato) {
            inmediato.draw(esBanner ? TexturedRenderLayers.getBannerPatterns() : TexturedRenderLayers.getShieldPatterns());
        }
        if (foto != null) lienzo.render(matrices, vcp.getBuffer(RenderLayer.getEntityTranslucent(foto)), luz, overlay);
        ci.cancel();
    }

    /** Lo mismo que {@code BannerBlockEntityRenderer#renderLayer} (privado): una capa del telar con su color. */
    private static void capaVanilla(MatrixStack matrices, VertexConsumerProvider vcp, int luz, int overlay, ModelPart lienzo,
                                    SpriteIdentifier id, DyeColor color) {
        lienzo.render(matrices, id.getVertexConsumer(vcp, RenderLayer::getEntityNoOutline), luz, overlay, color.getEntityColor());
    }
}
