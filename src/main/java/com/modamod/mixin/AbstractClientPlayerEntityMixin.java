package com.modamod.mixin;

import com.modamod.client.DebugApariencia;
import com.modamod.render.ComposedSkin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Devuelve una skin preparada para la ropa del mod.
 *
 * El enganche es getSkinTextures() y NO PlayerEntityRenderer.getTexture():
 * 3D Skin Layers lee de aca (SkinUtil.setup3dLayers -> PlayerUtil.getPlayerSkin
 * -> getSkinTextures().texture()), asi que enganchar en el renderer lo dejaria
 * construyendo su geometria 3D desde la skin original y la prenda seguiria
 * tapada.
 *
 * Si el jugador no lleva prendas que toquen la skin, ComposedSkin devuelve el
 * mismo objeto y esto no hace nada.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void modamod$vestirSkin(CallbackInfoReturnable<SkinTextures> cir) {
        SkinTextures real = cir.getReturnValue();
        AbstractClientPlayerEntity jugador = (AbstractClientPlayerEntity) (Object) this;
        // El override de debug (skin de prueba / slim-ancho forzado) es
        // SOLO para el jugador local — ver DebugApariencia.
        SkinTextures base = jugador == MinecraftClient.getInstance().player
                ? DebugApariencia.aplicar(real) : real;
        SkinTextures compuesta = ComposedSkin.forPlayer(jugador, base);
        if (compuesta != real) {
            cir.setReturnValue(compuesta);
        }
    }
}
