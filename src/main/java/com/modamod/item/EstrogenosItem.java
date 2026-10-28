package com.modamod.item;

import com.modamod.body.PerfilCuerpo;
import com.modamod.body.PerfilesDeCuerpo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/**
 * Estrógenos (2026-10-01, "quiero agregar un item llamado estrogenos que
 * generara pechos de distintos tamaños en el cuerpo" → "3 tamaños de mamas
 * con fisicas estilo resorte a traves de la acumulacion de tres ingestas y
 * que los efectos duren 24 hs"): se toma como una poción; cada dosis suma un
 * talle (chico, mediano, grande) hasta {@link PerfilCuerpo#BUSTO_MAXIMO}. Lo dibuja
 * el relieve, con el rebote de {@code render.relieve.FisicaBusto}.
 *
 * <p>Sin vencimiento y con contraparte (2026-10-22, "sacamos lo de las 24 horas. estrogeno aumenta testo baja
 * indefinidamente"): la Testosterona es este mismo ítem con {@code sube} falso; baja un talle por dosis y, del
 * talle 1 o del busto propio del cuerpo, deja el pecho plano y sin cola ({@link PerfilCuerpo#BUSTO_PLANO}).
 */
public class EstrogenosItem extends Item {

    /** true = Estrógenos (suben un talle), false = Testosterona (bajan). */
    private final boolean sube;

    public EstrogenosItem(Settings settings) {
        this(settings, true);
    }

    public EstrogenosItem(Settings settings, boolean sube) {
        super(settings);
        this.sube = sube;
    }

    /** El talle después de una dosis: de PLANO, los Estrógenos van al 1; la Testosterona del 1 (o del cuerpo, 0) va a PLANO. */
    public static int despuesDeDosis(int busto, boolean sube) {
        if (sube) return Math.min(PerfilCuerpo.BUSTO_MAXIMO, Math.max(0, busto) + 1);
        return busto > 1 ? busto - 1 : PerfilCuerpo.BUSTO_PLANO;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.DRINK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 32;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (world instanceof ServerWorld servidor && user instanceof PlayerEntity jugador) {
            PerfilCuerpo perfil = PerfilesDeCuerpo.de(jugador);
            int dosis = despuesDeDosis(perfil.busto(), sube);
            PerfilesDeCuerpo.poner(jugador, perfil.conBusto(dosis, 0L));
            String clave = dosis == PerfilCuerpo.BUSTO_PLANO ? "modamod.testosterona.plano"
                    : dosis == perfil.busto() ? "modamod.estrogenos.renovado" : "modamod.estrogenos.dosis";
            jugador.sendMessage(Text.translatable(clave, dosis, PerfilCuerpo.BUSTO_MAXIMO), true);
            world.playSound(null, jugador.getX(), jugador.getY(), jugador.getZ(),
                    SoundEvents.ITEM_HONEY_BOTTLE_DRINK, SoundCategory.PLAYERS, 0.8f, 1.2f);
            stack.decrementUnlessCreative(1, jugador);
        }
        return stack;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(sube ? "modamod.estrogenos.tooltip" : "modamod.testosterona.tooltip").formatted(Formatting.GRAY));
    }
}
