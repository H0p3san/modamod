package com.modamod.prenda;

import com.modamod.prenda.Parte;
import net.minecraft.item.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Todo lo que el mod sabe de un tipo de prenda, en un solo lugar: su categoría, cómo se dibuja y qué partes del cuerpo
 * tapa. Lo registra {@link Prendas} por ítem; las máquinas y el render le preguntan a esto en lugar de a la clase del
 * ítem.
 *
 * <p>Vive del lado común (servidor y cliente): las máquinas corren en el servidor y lo necesitan. Cómo se ve de verdad
 * (texturas, mallas) es del cliente.
 *
 * @param partes qué partes del cuerpo tapa: ahí se apaga la piel de vanilla y se dibuja el cuerpo base. Depende del
 *               stack porque el corte manda.
 */
public record DefinicionDePrenda(CategoriaDePrenda categoria, FormaDeDibujo forma,
                                 Function<ItemStack, Set<Parte>> partes) {

    public DefinicionDePrenda(CategoriaDePrenda categoria, FormaDeDibujo forma) {
        this(categoria, forma, s -> Set.of());
    }

    public DefinicionDePrenda(CategoriaDePrenda categoria, FormaDeDibujo forma, Parte primera, Parte... resto) {
        this(categoria, forma, fijas(EnumSet.of(primera, resto)));
    }

    private static Function<ItemStack, Set<Parte>> fijas(Set<Parte> partes) {
        Set<Parte> copia = Set.copyOf(partes);
        return s -> copia;
    }

    public Set<Parte> partesDe(ItemStack stack) {
        return partes.apply(stack);
    }
}
