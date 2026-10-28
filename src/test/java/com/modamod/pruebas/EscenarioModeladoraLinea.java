package com.modamod.pruebas;

import com.modamod.item.Calce;
import com.modamod.item.ModamodComponents;
import com.modamod.item.ModamodItems;
import com.modamod.modelado.ModeladoBlockEntity;
import com.modamod.modelado.ModeladoMod;
import com.modamod.util.InventarioUtil;
import net.minecraft.block.Blocks;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import static com.modamod.pruebas.Informe.verificar;

public final class EscenarioModeladoraLinea {
    private EscenarioModeladoraLinea() {}

    public static Corrida armar() {
        Corrida corrida = new Corrida("modeladora: reinicio en linea");
        corrida.mundoPlano();
        corrida.hacer("tres prendas consecutivas con salida bloqueada", 0, cliente -> Corrida.enServidor(cliente, () -> {
            ServerWorld mundo = Corrida.jugador(cliente).getServerWorld();
            BlockPos posicion = Corrida.jugador(cliente).getBlockPos().add(3, 0, 0);
            mundo.setBlockState(posicion, ModeladoMod.MODELADO_BLOCK.getDefaultState());
            ModeladoBlockEntity maquina = (ModeladoBlockEntity) mundo.getBlockEntity(posicion);
            maquina.guiAbierta();
            maquina.setStack(ModeladoBlockEntity.PRENDA, new ItemStack(ModamodItems.PANTALON));
            maquina.absorber(new ItemStack(ModeladoMod.MOLDE_CALCE_OVERSIZE));
            int pin = -1;
            for (int candidato = 0; candidato < ModeladoBlockEntity.PINES_POR_CATEGORIA; candidato++) {
                if (ModeladoBlockEntity.pinAcepta(ModeladoBlockEntity.Categoria.PANTALON, candidato,
                        new ItemStack(ModeladoMod.MOLDE_CALCE_OVERSIZE))) { pin = candidato; break; }
            }
            verificar(maquina.elegirDeBolsa(pin, Registries.ITEM.getId(ModeladoMod.MOLDE_CALCE_OVERSIZE)), "no se fijo el molde");
            maquina.setStack(ModeladoBlockEntity.SLOT_TIJERA, new ItemStack(Items.SHEARS));
            maquina.alternarLinea();
            maquina.guiCerrada();
            maquina.encenderAlCerrar();
            BlockPos destino = posicion.offset(maquina.ladoSalida());
            mundo.setBlockState(destino, Blocks.STONE.getDefaultState());
            terminar(mundo, posicion, maquina);
            for (int ciclo = 1; ciclo <= 2; ciclo++) {
                verificar(InventarioUtil.empujarA(mundo, posicion, Direction.UP, new ItemStack(ModamodItems.PANTALON)).isEmpty(), "no entro la siguiente prenda");
                tick(mundo, posicion, maquina);
                verificar(maquina.estado() == ModeladoBlockEntity.Estado.LISTO && !maquina.encendida(), "proceso con la salida bloqueada");
                verificar(maquina.tijera().getDamage() == ciclo, "gasto tijera mientras esperaba");
                mundo.setBlockState(destino, Blocks.BARREL.getDefaultState());
                tick(mundo, posicion, maquina);
                Inventory barril = (Inventory) mundo.getBlockEntity(destino);
                verificar(barril.getStack(0).getCount() == 1 && barril.getStack(0).get(ModamodComponents.CALCE) == Calce.OVERSIZE,
                        "no entrego exactamente una prenda modelada");
                tick(mundo, posicion, maquina);
                verificar(maquina.estado() == ModeladoBlockEntity.Estado.PROCESANDO, "la siguiente prenda quedo detenida tras liberar la salida");
                verificar(maquina.tijera().getDamage() == ciclo + 1, "no gasto exactamente un uso al reiniciar");
                mundo.setBlockState(destino, Blocks.STONE.getDefaultState());
                terminar(mundo, posicion, maquina);
            }
            maquina.removeStack(ModeladoBlockEntity.SALIDA);
            tick(mundo, posicion, maquina);
            maquina.guiAbierta();
            maquina.setStack(ModeladoBlockEntity.PRENDA, new ItemStack(ModamodItems.PANTALON));
            tick(mundo, posicion, maquina);
            verificar(!maquina.encendida(), "arranco con la GUI abierta");
            maquina.guiCerrada();
            maquina.alternarLinea();
            tick(mundo, posicion, maquina);
            verificar(!maquina.encendida(), "arranco con la linea desactivada");
            maquina.alternarLinea();
            ItemStack tijera = maquina.sacarTijera();
            tick(mundo, posicion, maquina);
            verificar(!maquina.encendida(), "arranco sin tijera");
            maquina.setStack(ModeladoBlockEntity.SLOT_TIJERA, tijera);
            BlockPos senal = posicion.up();
            mundo.setBlockState(senal, Blocks.REDSTONE_BLOCK.getDefaultState());
            tick(mundo, posicion, maquina);
            verificar(!maquina.encendida(), "ignoro la pausa de redstone");
            mundo.removeBlock(senal, false);
            tick(mundo, posicion, maquina);
            verificar(maquina.estado() == ModeladoBlockEntity.Estado.PROCESANDO && maquina.tijera().getDamage() == 4,
                    "no retomo al quitar la pausa");
            terminar(mundo, posicion, maquina);
            maquina.removeStack(ModeladoBlockEntity.SALIDA);
            tick(mundo, posicion, maquina);
            tick(mundo, posicion, maquina);
            verificar(!maquina.encendida() && maquina.tijera().getDamage() == 4, "trabajo sin prenda o duplico el desgaste");
            return null;
        }));
        return corrida;
    }

    private static void tick(ServerWorld mundo, BlockPos posicion, ModeladoBlockEntity maquina) {
        ModeladoBlockEntity.tick(mundo, posicion, mundo.getBlockState(posicion), maquina);
    }

    private static void terminar(ServerWorld mundo, BlockPos posicion, ModeladoBlockEntity maquina) {
        for (int paso = 0; paso <= ModeladoBlockEntity.TICKS_PROCESO; paso++) {
            if (maquina.estado() == ModeladoBlockEntity.Estado.LISTO) break;
            tick(mundo, posicion, maquina);
        }
        verificar(maquina.estado() == ModeladoBlockEntity.Estado.LISTO, "no termino la prenda");
    }
}
