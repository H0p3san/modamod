package com.modamod.client;

import com.modamod.item.ModamodComponents;
import com.modamod.render.EfectoTrim;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Partículas que salen de la ropa con acabado de trim (2026-10-22, "los trims hay que hacerlo épico" + "vamos con todo"):
 * cada efecto suelta las suyas (gotas de lava del magma, burbujas del océano, almas del sculk, portal del ender, chispas del
 * rayo...) desde la parte del cuerpo que cubre la prenda. Pocas quieto, más caminando y una estela en el twirl. Solo de
 * cerca, y las del propio jugador en primera persona no (taparían la vista).
 */
public final class ParticulasTrim {

    private ParticulasTrim() {}

    /** Hasta qué distancia (bloques) sueltan partículas los demás jugadores. */
    private static final double DISTANCIA = 24;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(ParticulasTrim::tick);
    }

    private static void tick(MinecraftClient c) {
        if (c.world == null || c.player == null || c.isPaused()) return;
        for (AbstractClientPlayerEntity j : c.world.getPlayers()) {
            if (j.isInvisible() || j.squaredDistanceTo(c.player) > DISTANCIA * DISTANCIA) continue;
            if (j == c.player && c.options.getPerspective().isFirstPerson()) continue;
            List<ItemStack> conTrim = new ArrayList<>();
            dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(j).ifPresent(comp -> {
                for (var par : comp.getAllEquipped()) if (par.getRight().contains(ModamodComponents.ACABADO_TRIM)) conTrim.add(par.getRight());
            });
            if (conTrim.isEmpty()) continue;
            float twirl = TwirlCliente.progreso(j, 0f);
            double vel = j.getVelocity().horizontalLength();
            // Una cada ~10 ticks quieto; caminando y corriendo, más; en el twirl, una lluvia.
            float tasa = twirl >= 0f ? 1.2f : 0.1f + (float) Math.min(0.5, vel * 2.5);
            tasa = repartir(tasa, conTrim.size());
            for (ItemStack prenda : conTrim) {
                soltar(c.world, j.getRandom(), prenda, j.getX(), j.getY(), j.getZ(), j.isSneaking() ? 0.85f : 1f, tasa, twirl >= 0f);
            }
        }
    }

    /** El último tick en que soltó cada maniquí (su render corre varias veces por tick y también en la pantalla). */
    private static final java.util.Map<net.minecraft.util.math.BlockPos, Long> MANIQUIES = new java.util.HashMap<>();

    /**
     * Las del Maniquí (2026-10-22, "vamos con todo"): las mismas que un jugador quieto, o la lluvia del twirl si gira. Lo llama su
     * render con los pies de la figura en {@code (x, y, z)}.
     */
    public static void maniqui(net.minecraft.world.World mundo, net.minecraft.util.math.BlockPos pos, List<ItemStack> prendas,
                               double x, double y, double z, boolean twirl) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.isPaused() || c.player.squaredDistanceTo(x, y, z) > DISTANCIA * DISTANCIA) return;
        Long ultimo = MANIQUIES.get(pos);
        if (ultimo != null && ultimo == mundo.getTime()) return;
        MANIQUIES.put(pos, mundo.getTime());
        if (MANIQUIES.size() > 256) MANIQUIES.clear();
        int n = 0;
        for (ItemStack prenda : prendas) if (prenda.contains(ModamodComponents.ACABADO_TRIM)) n++;
        float tasa = repartir(twirl ? 1.2f : 0.1f, n);
        for (ItemStack prenda : prendas) {
            if (prenda.contains(ModamodComponents.ACABADO_TRIM)) soltar(mundo, mundo.getRandom(), prenda, x, y, z, 1f, tasa, twirl);
        }
    }

    /**
     * Con muchas prendas con trim la tasa de cada una baja (2026-10-22, modashow: seis prendas en el twirl tapaban la ropa): el
     * total crece como la raíz de cuántas son, no en proporción.
     */
    private static float repartir(float tasa, int prendas) {
        return prendas <= 1 ? tasa : tasa / (float) Math.sqrt(prendas);
    }

    private static void soltar(net.minecraft.world.World mundo, Random r, ItemStack prenda, double px, double py, double pz,
                               float agachado, float tasa, boolean twirl) {
        int n = (int) tasa + (r.nextFloat() < tasa - (int) tasa ? 1 : 0);
        if (n == 0) return;
        Identifier patron = prenda.get(ModamodComponents.ACABADO_TRIM);
        EfectoTrim.Tipo tipo = EfectoTrim.tipoDe(patron);
        // La altura de la prenda: abajo las piernas y el calzado, arriba el torso y la cabeza.
        float[] alto = altura(prenda);
        for (int k = 0; k < n; k++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double radio = 0.3 + r.nextDouble() * 0.05;
            double x = px + Math.cos(ang) * radio, z = pz + Math.sin(ang) * radio;
            double y = py + (alto[0] + r.nextFloat() * (alto[1] - alto[0])) * agachado;
            double vx = 0, vz = 0;
            if (twirl) {   // la estela sale tangente al giro
                vx = -Math.sin(ang) * 0.15;
                vz = Math.cos(ang) * 0.15;
            }
            mundo.addParticle(particula(tipo), x, y, z, vx + velocidadX(tipo, r), velocidadY(tipo, r), vz + velocidadX(tipo, r));
        }
    }

    /** [desde, hasta] en bloques sobre los pies de lo que cubre la prenda. */
    private static float[] altura(ItemStack prenda) {
        var partes = com.modamod.garment.Garments.partesCubiertas(List.of(prenda));
        if (prenda.getItem() instanceof com.modamod.item.SombreroBrujaItem) return new float[]{1.5f, 2.1f};
        if (prenda.getItem() instanceof com.modamod.item.BorcegosItem) return new float[]{0f, 0.3f};
        boolean arriba = partes.contains(com.modamod.garment.Parte.TORSO) || partes.contains(com.modamod.garment.Parte.BRAZO_DER);
        boolean abajo = partes.contains(com.modamod.garment.Parte.PIERNA_DER) || partes.contains(com.modamod.garment.Parte.PIERNA_IZQ);
        if (arriba && abajo) return new float[]{0.1f, 1.45f};
        if (abajo) return new float[]{0.05f, 0.8f};
        return new float[]{0.75f, 1.45f};
    }

    private static ParticleEffect particula(EfectoTrim.Tipo tipo) {
        return switch (tipo) {
            case SENTRY -> ParticleTypes.CRIT;
            case DUNE -> new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, net.minecraft.block.Blocks.SAND.getDefaultState());
            case COAST -> ParticleTypes.SPLASH;
            case WILD -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case WARD -> ParticleTypes.SCULK_CHARGE_POP;
            case EYE -> ParticleTypes.PORTAL;
            case VEX -> ParticleTypes.SOUL;
            case TIDE -> ParticleTypes.NAUTILUS;
            case SNOUT -> ParticleTypes.FALLING_LAVA;
            case RIB -> ParticleTypes.SMALL_FLAME;
            case SPIRE -> ParticleTypes.END_ROD;
            case WAYFINDER -> ParticleTypes.ENCHANT;
            case SHAPER -> ParticleTypes.WAX_ON;
            case SILENCE -> ParticleTypes.SCULK_SOUL;
            case RAISER -> ParticleTypes.SMOKE;
            case HOST -> ParticleTypes.GLOW;
            case FLOW -> ParticleTypes.CLOUD;
            case BOLT -> ParticleTypes.ELECTRIC_SPARK;
            default -> ParticleTypes.WAX_OFF;
        };
    }

    /** Las que suben (humo, almas, calor) y las que flotan quietas. */
    private static double velocidadY(EfectoTrim.Tipo tipo, Random r) {
        return switch (tipo) {
            case RAISER, VEX, SILENCE, SPIRE -> 0.02 + r.nextDouble() * 0.03;
            case RIB, WARD -> 0.01;
            default -> 0;
        };
    }

    private static double velocidadX(EfectoTrim.Tipo tipo, Random r) {
        return switch (tipo) {
            case FLOW, SPIRE, EYE -> (r.nextDouble() - 0.5) * 0.04;
            default -> 0;
        };
    }
}
