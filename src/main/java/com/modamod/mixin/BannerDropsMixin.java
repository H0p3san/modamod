package com.modamod.mixin;

import com.modamod.sublimadora.BannerConImagen;
import com.modamod.sublimadora.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.BannerItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Al romper un banner con foto, lo que cae lleva la foto (la tabla de botín de vanilla solo copia patrones y nombre). */
@Mixin(Block.class)
public abstract class BannerDropsMixin {

    @Inject(method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"))
    private static void modamod$bannerConFoto(BlockState estado, ServerWorld mundo, BlockPos pos, BlockEntity be,
                                                 Entity entidad, ItemStack herramienta, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!(be instanceof BannerBlockEntity) || !(be instanceof BannerConImagen c)) return;
        if (c.modamod$imagen() == null && c.modamod$capas().isEmpty()) return;
        for (ItemStack s : cir.getReturnValue()) {
            if (!(s.getItem() instanceof BannerItem)) continue;
            if (c.modamod$imagen() != null) s.set(ModItems.BANNER_ESTAMPA, c.modamod$imagen());
            // El tinte (2026-10-24) también.
            if (!c.modamod$capas().isEmpty()) s.set(com.modamod.item.ModamodComponents.CAPAS_TINTE, c.modamod$capas());
        }
    }
}
