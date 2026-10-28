package com.modamod.mixin;

import com.modamod.sublimadora.BannerConImagen;
import com.modamod.sublimadora.Estampa;
import com.modamod.sublimadora.ModItems;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.DyeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * El banner guarda la foto sublimada (2026-10-06, "solo tintes y sublimadoras a banners"): se copia del ítem al
 * colocarlo, vuelve al ítem al recogerlo, se guarda en el chunk y viaja al cliente con el resto del bloque.
 */
@Mixin(BannerBlockEntity.class)
public abstract class BannerBlockEntityMixin extends BlockEntity implements BannerConImagen {

    public BannerBlockEntityMixin(net.minecraft.block.entity.BlockEntityType<?> tipo, net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState estado) {
        super(tipo, pos, estado);
    }

    @Unique private Estampa modamod$imagen;

    @Override public Estampa modamod$imagen() { return modamod$imagen; }
    @Override public void modamod$ponerImagen(Estampa e) { modamod$imagen = e; }

    /** Las capas de Tintes (2026-10-24): del ítem al bloque y de vuelta, igual que la foto. */
    @Unique private java.util.List<com.modamod.region.RegionResolver.CapaPatron> modamod$capas = java.util.List.of();

    @Override public java.util.List<com.modamod.region.RegionResolver.CapaPatron> modamod$capas() { return modamod$capas; }

    /** El banner "de ítem" que dibuja el render del inventario y de la mano. */
    @Inject(method = "readFrom", at = @At("TAIL"))
    private void modamod$leerDelItem(ItemStack stack, DyeColor color, CallbackInfo ci) {
        modamod$imagen = stack.get(ModItems.BANNER_ESTAMPA);
        modamod$capas = stack.getOrDefault(com.modamod.item.ModamodComponents.CAPAS_TINTE, java.util.List.of());
    }

    @Inject(method = "readComponents", at = @At("TAIL"))
    private void modamod$leerComponentes(BlockEntity.ComponentsAccess componentes, CallbackInfo ci) {
        modamod$imagen = componentes.get(ModItems.BANNER_ESTAMPA);
        modamod$capas = componentes.getOrDefault(com.modamod.item.ModamodComponents.CAPAS_TINTE, java.util.List.of());
    }

    @Inject(method = "addComponents", at = @At("TAIL"))
    private void modamod$sumarComponentes(ComponentMap.Builder builder, CallbackInfo ci) {
        if (modamod$imagen != null) builder.add(ModItems.BANNER_ESTAMPA, modamod$imagen);
        if (!modamod$capas.isEmpty()) builder.add(com.modamod.item.ModamodComponents.CAPAS_TINTE, modamod$capas);
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void modamod$escribir(NbtCompound nbt, RegistryWrapper.WrapperLookup registros, CallbackInfo ci) {
        if (!modamod$capas.isEmpty()) {
            com.modamod.region.RegionResolver.CapaPatron.CODEC.listOf().encodeStart(NbtOps.INSTANCE, modamod$capas).result()
                    .ifPresent(n -> nbt.put("ModamodCapas", n));
        }
        if (modamod$imagen == null) return;
        Estampa.CODEC.encodeStart(NbtOps.INSTANCE, modamod$imagen).result().ifPresent(n -> nbt.put("ModamodImagen", n));
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void modamod$leer(NbtCompound nbt, RegistryWrapper.WrapperLookup registros, CallbackInfo ci) {
        modamod$imagen = nbt.contains("ModamodImagen")
                ? Estampa.CODEC.parse(NbtOps.INSTANCE, nbt.get("ModamodImagen")).result().orElse(null) : null;
        modamod$capas = nbt.contains("ModamodCapas")
                ? com.modamod.region.RegionResolver.CapaPatron.CODEC.listOf().parse(NbtOps.INSTANCE, nbt.get("ModamodCapas"))
                        .result().map(java.util.List::copyOf).orElse(java.util.List.of())
                : java.util.List.of();
    }
}
