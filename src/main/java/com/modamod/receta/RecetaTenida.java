package com.modamod.receta;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.modamod.telar.TelarPrenda;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;

/** Receta con forma cuya prenda sale del color de las lanas (ver {@link ModamodRecetas}). */
public class RecetaTenida extends ShapedRecipe {
    final RawShapedRecipe cruda;
    final TelarPrenda prenda;
    final int cantidad;

    public RecetaTenida(RawShapedRecipe cruda, TelarPrenda prenda, int cantidad) {
        super("", CraftingRecipeCategory.EQUIPMENT, cruda, vista(prenda, cantidad));
        this.cruda = cruda;
        this.prenda = prenda;
        this.cantidad = cantidad;
    }

    private static ItemStack vista(TelarPrenda p, int n) {
        ItemStack s = p.crear(0xFFFFFF);
        s.setCount(n);
        return s;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput in, RegistryWrapper.WrapperLookup registros) {
        ItemStack s = prenda.crear(ModamodRecetas.colorDeLanas(in, -1));
        s.setCount(cantidad);
        return s;
    }

    @Override
    public RecipeSerializer<?> getSerializer() { return ModamodRecetas.TENIDA; }

    public static class Serializer implements RecipeSerializer<RecetaTenida> {
        private static final MapCodec<RecetaTenida> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                RawShapedRecipe.CODEC.forGetter(r -> r.cruda),
                Codec.STRING.xmap(n -> TelarPrenda.valueOf(n.toUpperCase()), p -> p.name().toLowerCase())
                        .fieldOf("prenda").forGetter(r -> r.prenda),
                Codec.intRange(1, 64).optionalFieldOf("count", 1).forGetter(r -> r.cantidad)
        ).apply(i, RecetaTenida::new));
        private static final PacketCodec<RegistryByteBuf, RecetaTenida> PAQUETE = PacketCodec.tuple(
                RawShapedRecipe.PACKET_CODEC, r -> r.cruda,
                PacketCodecs.VAR_INT, r -> r.prenda.ordinal(),
                PacketCodecs.VAR_INT, r -> r.cantidad,
                (c, p, n) -> new RecetaTenida(c, TelarPrenda.values()[p], n));

        @Override public MapCodec<RecetaTenida> codec() { return CODEC; }
        @Override public PacketCodec<RegistryByteBuf, RecetaTenida> packetCodec() { return PAQUETE; }
    }
}
