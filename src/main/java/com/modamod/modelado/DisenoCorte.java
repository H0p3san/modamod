package com.modamod.modelado;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Lo que guarda un ítem "Diseño de corte" (2026-10-12, "no podemos hacer items custom que se puedan sacar e
 * intercambiar y hacemos una bolsa para ellos?"): la categoría de la Modeladora a la que pertenece (nombre del enum
 * {@code ModeladoBlockEntity.Categoria}) y los cortes fijados de ese diseño. El nombre va en el componente de nombre
 * personalizado del ítem.
 */
public record DisenoCorte(String categoria, List<ComboCorte> combos) {
    public static final Codec<DisenoCorte> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("categoria").forGetter(DisenoCorte::categoria),
            ComboCorte.CODEC.listOf().fieldOf("combos").forGetter(DisenoCorte::combos)
    ).apply(i, DisenoCorte::new));
}
