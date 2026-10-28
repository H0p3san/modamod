package com.modamod.pruebas;

import com.modamod.client.ModeladoScreen;
import com.modamod.client.TelarScreen;
import com.modamod.item.Calce;
import com.modamod.item.ModamodComponents;
import com.modamod.item.ModamodDye;
import com.modamod.item.ModamodItems;
import com.modamod.item.Ruedo;
import com.modamod.modelado.ElegirMoldePayload;
import com.modamod.modelado.ModeladoBlockEntity;
import com.modamod.modelado.ModeladoBlockEntity.Categoria;
import com.modamod.modelado.ModeladoMod;
import com.modamod.telar.TelarBlockEntity;
import com.modamod.telar.TelarMod;
import com.modamod.telar.TelarPrenda;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import static com.modamod.pruebas.Corrida.enServidor;
import static com.modamod.pruebas.Corrida.jugador;
import static com.modamod.pruebas.Informe.verificar;

/**
 * Recorrido por las pantallas reales (./gradlew.bat runSmoke): Modeladora (pines, molde en blanco de calce, ruedo
 * Fruncido: elegir, deshacer, cerrar, reabrir, modelar y revisar la prenda que sale) y Telar (boton Tejer + persistencia
 * de la seleccion). Las acciones pasan por los mismos caminos que el jugador: botones del handler y paquetes.
 */
public final class EscenarioPantallas {
    private static BlockPos posModeladora, posTelar;

    private EscenarioPantallas() {}

    // ── acceso a los block entities del servidor ─────────────────────────────────────────

    private static <T> T modeladora(MinecraftClient c, java.util.function.Function<ModeladoBlockEntity, T> f) {
        return enServidor(c, () -> f.apply((ModeladoBlockEntity) jugador(c).getServerWorld().getBlockEntity(posModeladora)));
    }

    private static <T> T telar(MinecraftClient c, java.util.function.Function<TelarBlockEntity, T> f) {
        return enServidor(c, () -> f.apply((TelarBlockEntity) jugador(c).getServerWorld().getBlockEntity(posTelar)));
    }

    private static void abrirModeladora(MinecraftClient c) {
        enServidor(c, () -> { jugador(c).openHandledScreen((ModeladoBlockEntity) jugador(c).getServerWorld().getBlockEntity(posModeladora)); return null; });
    }

    private static void abrirTelar(MinecraftClient c) {
        enServidor(c, () -> { jugador(c).openHandledScreen((TelarBlockEntity) jugador(c).getServerWorld().getBlockEntity(posTelar)); return null; });
    }

    private static void boton(MinecraftClient c, int id) { c.interactionManager.clickButton(c.player.currentScreenHandler.syncId, id); }

    /** Encuentra el casillero de la categoria que acepta ese molde (el mismo criterio con el que lo muestra la pantalla). */
    private static int pinPara(Categoria cat, Item molde) {
        for (int i = 0; i < ModeladoBlockEntity.PINES_POR_CATEGORIA; i++) {
            if (ModeladoBlockEntity.ROLES[cat.ordinal()][i] != ModeladoBlockEntity.Rol.NINGUNO && ModeladoBlockEntity.pinAcepta(cat, i, new ItemStack(molde))) return i;
        }
        return -1;
    }

    /** Paso: llega a la categoria apretando el boton de categoria como el jugador (reintenta mientras el servidor responde). */
    private static Corrida irACategoria(Corrida r, Categoria objetivo) {
        return r.paso("categoria " + objetivo, c -> {
            if (modeladora(c, be -> be.categoria()) == objetivo) return true;
            boton(c, ModeladoBlockEntity.BTN_CATEGORIA);
            return false;
        });
    }

    /** Paso: pone el molde en su casillero por el paquete de la pantalla y comprueba que quedo aplicado y fijado. */
    private static Corrida elegirMolde(Corrida r, String nombre, Categoria cat, Item molde) {
        return r.hacer("elegir " + nombre, 10, c -> {
            int pin = pinPara(cat, molde);
            verificar(pin >= 0, "ningun casillero de " + cat + " acepta " + nombre);
            ClientPlayNetworking.send(new ElegirMoldePayload(posModeladora, pin, Registries.ITEM.getId(molde), ElegirMoldePayload.ELEGIR));
        }).paso("pin aplicado " + nombre, c -> {
            int idx = cat.ordinal() * ModeladoBlockEntity.PINES_POR_CATEGORIA + pinPara(cat, molde);
            return modeladora(c, be -> be.pinCombo(idx) != null && be.pinFijado(idx));
        });
    }

    /** Paso: enciende la maquina y espera la prenda en la salida (la creativa tarda pocos ticks). */
    private static Corrida modelar(Corrida r, String nombre) {
        return r.hacer("encender " + nombre, 0, c -> boton(c, ModeladoBlockEntity.BTN_ENCENDER))
                .paso("salida lista " + nombre, c -> modeladora(c, be -> be.estado() == ModeladoBlockEntity.Estado.LISTO && !be.getStack(ModeladoBlockEntity.SALIDA).isEmpty()));
    }

    public static Corrida armar() {
        Corrida r = new Corrida("pantallas (Modeladora y Telar)");
        r.mundoPlano();
        r.hacer("limpiar corrida anterior", 0, Corrida::limpiar);
        r.hacer("GUI a escala 2x", 5, Corrida::guiDos);
        r.hacer("colocar Modeladora y Telar creativas", 10, c -> {
            BlockPos base = jugador(c).getBlockPos();
            posModeladora = base.add(2, 0, 0);
            posTelar = base.add(-2, 0, 0);
            enServidor(c, () -> {
                var w = jugador(c).getServerWorld();
                w.setBlockState(posModeladora, ModeladoMod.MODELADO_CREATIVA.getDefaultState());
                w.setBlockState(posTelar, TelarMod.TELAR_CREATIVA.getDefaultState());
                return null;
            });
        });

        // ── Modeladora: molde en blanco de calce sobre un pantalon ───────────────────────
        r.hacer("cargar pantalon y moldes", 5, c -> modeladora(c, be -> {
            be.setStack(ModeladoBlockEntity.PRENDA, new ItemStack(ModamodItems.PANTALON));
            be.absorber(new ItemStack(ModeladoMod.MOLDE_CALCE_OVERSIZE));
            be.absorber(new ItemStack(ModeladoMod.MOLDE_RUEDO_FRUNCIDO));
            return null;
        }));
        r.hacer("abrir Modeladora", 0, EscenarioPantallas::abrirModeladora);
        r.paso("pantalla Modeladora abierta", c -> c.currentScreen instanceof ModeladoScreen);
        r.dejarPasar(20).captura("modeladora-abierta");
        irACategoria(r, Categoria.PANTALON);
        elegirMolde(r, "calce oversize", Categoria.PANTALON, ModeladoMod.MOLDE_CALCE_OVERSIZE);
        r.dejarPasar(15).captura("modeladora-calce-fijado");
        // deshacer: desfijar la primera fijada y comprobar que la lista queda vacia
        r.hacer("deshacer corte (desfijar)", 5, c -> boton(c, ModeladoBlockEntity.BTN_DESFIJAR_BASE));
        r.paso("fijadas vacias tras deshacer", c -> modeladora(c, be -> be.fijadas().isEmpty()));
        elegirMolde(r, "calce oversize (de nuevo)", Categoria.PANTALON, ModeladoMod.MOLDE_CALCE_OVERSIZE);
        // guardar/reabrir: cerrar la pantalla, la maquina conserva el pin, y al reabrir sigue ahi
        r.hacer("cerrar pantalla", 10, c -> c.player.closeHandledScreen());
        r.hacer("estado persiste con la pantalla cerrada", 0, c -> verificar(modeladora(c, be -> !be.fijadas().isEmpty()), "al cerrar se perdio el corte fijado"));
        r.hacer("reabrir Modeladora", 0, EscenarioPantallas::abrirModeladora);
        r.paso("pantalla reabierta", c -> c.currentScreen instanceof ModeladoScreen);
        r.hacer("corte fijado sigue al reabrir", 20, c -> verificar(modeladora(c, be -> be.categoria() == Categoria.PANTALON && !be.fijadas().isEmpty()), "al reabrir no esta el corte"));
        r.captura("modeladora-reabierta");
        modelar(r, "pantalon oversize");
        r.hacer("la prenda salio con Calce OVERSIZE", 5, c -> {
            ItemStack out = modeladora(c, be -> be.getStack(ModeladoBlockEntity.SALIDA).copy());
            verificar(out.get(ModamodComponents.CALCE) == Calce.OVERSIZE, "calce de la salida: " + out.get(ModamodComponents.CALCE));
        });

        // ── Modeladora: ruedo Fruncido en medias ─────────────────────────────────────────
        // Se retira la salida como el jugador y se espera a que la maquina vuelva a REPOSO antes de cargar otra prenda.
        r.hacer("retirar la salida", 0, c -> modeladora(c, be -> { be.setStack(ModeladoBlockEntity.SALIDA, ItemStack.EMPTY); return null; }));
        r.paso("la maquina vuelve a reposo", c -> modeladora(c, be -> be.estado() == ModeladoBlockEntity.Estado.REPOSO));
        // La maquina cierra la GUI al trabajar: se reabre ANTES de cargar la prenda, si no arranca sola con los cortes de la categoria anterior.
        r.hacer("reabrir Modeladora para las medias", 0, c -> { if (!(c.currentScreen instanceof ModeladoScreen)) abrirModeladora(c); });
        r.paso("pantalla lista para las medias", c -> c.currentScreen instanceof ModeladoScreen);
        r.hacer("cargar medias", 5, c -> modeladora(c, be -> {
            be.setStack(ModeladoBlockEntity.PRENDA, new ItemStack(ModamodItems.SOCKS_SOLID));
            return null;
        }));
        irACategoria(r, Categoria.MEDIAS);
        elegirMolde(r, "ruedo fruncido", Categoria.MEDIAS, ModeladoMod.MOLDE_RUEDO_FRUNCIDO);
        r.dejarPasar(15).captura("modeladora-ruedo-fruncido");
        modelar(r, "medias fruncidas");
        r.hacer("las medias salieron con ruedo Fruncido", 5, c -> {
            ItemStack out = modeladora(c, be -> be.getStack(ModeladoBlockEntity.SALIDA).copy());
            var ruedos = out.get(ModamodComponents.RUEDOS);
            int pin = pinPara(Categoria.MEDIAS, ModeladoMod.MOLDE_RUEDO_FRUNCIDO);
            verificar(ruedos != null && ruedos.containsValue(Ruedo.FRUNCIDO), "ruedos de la salida: " + ruedos + " | salida=" + out + " componentes=" + out.getComponents()
                    + " | pin=" + pin + " rol=" + ModeladoBlockEntity.ROLES[Categoria.MEDIAS.ordinal()][pin]
                    + " | aplicar directo=" + modeladora(c, be -> com.modamod.modelado.PrendaModelado.aplicar(new ItemStack(ModamodItems.SOCKS_SOLID), be.fijadas().get(0)).get(ModamodComponents.RUEDOS))
                    + " | estado=" + modeladora(c, be -> be.estado() + " tijera=" + be.tijeraOk())
                    + " | procesar directo=" + modeladora(c, be -> { try { var m = ModeladoBlockEntity.class.getDeclaredMethod("procesar", ItemStack.class); m.setAccessible(true); return ((ItemStack) m.invoke(be, new ItemStack(ModamodItems.SOCKS_SOLID))).get(ModamodComponents.RUEDOS); } catch (Exception e) { return e.toString(); } }));
        });
        // ── Modeladora: pin "Pant leg" (botamanga) del calzado con el molde en blanco (reporte 2026-10-19) ─────────────
        r.hacer("retirar las medias", 0, c -> modeladora(c, be -> { be.setStack(ModeladoBlockEntity.SALIDA, ItemStack.EMPTY); return null; }));
        r.paso("la maquina vuelve a reposo (calzado)", c -> modeladora(c, be -> be.estado() == ModeladoBlockEntity.Estado.REPOSO));
        r.hacer("reabrir Modeladora para el calzado", 0, c -> { if (!(c.currentScreen instanceof ModeladoScreen)) abrirModeladora(c); });
        r.paso("pantalla lista para el calzado", c -> c.currentScreen instanceof ModeladoScreen);
        r.hacer("cargar borcegos y moldes de botamanga", 5, c -> modeladora(c, be -> {
            be.setStack(ModeladoBlockEntity.PRENDA, new ItemStack(ModamodItems.BORCEGOS));
            be.absorber(new ItemStack(ModeladoMod.MOLDE_EN_BLANCO));
            be.absorber(new ItemStack(ModeladoMod.MOLDE_BORCEGO_BOTAMANGA_AFUERA));
            return null;
        }));
        irACategoria(r, Categoria.CALZADO);
        int[] pantLeg = {-1};
        r.hacer("buscar el pin Pant leg izquierdo", 0, c -> {
            for (int i = 0; i < ModeladoBlockEntity.PINES_POR_CATEGORIA; i++) {
                if (ModeladoBlockEntity.ROLES[Categoria.CALZADO.ordinal()][i] == ModeladoBlockEntity.Rol.BOTAMANGA_BORCEGO_IZQ) pantLeg[0] = i;
            }
            verificar(pantLeg[0] >= 0, "Calzado no tiene el pin de botamanga izquierda");
            verificar(ModeladoBlockEntity.pinAcepta(Categoria.CALZADO, pantLeg[0], new ItemStack(ModeladoMod.MOLDE_BORCEGO_BOTAMANGA_AFUERA)),
                    "el pin Pant leg no acepta el molde de botamanga afuera");
        });
        r.hacer("el pin Pant leg acepta el molde en blanco", 0, c -> verificar(
                ModeladoBlockEntity.pinAcepta(Categoria.CALZADO, pantLeg[0], new ItemStack(ModeladoMod.MOLDE_EN_BLANCO)),
                "el molde en blanco NO entra en el pin Pant leg (no se puede volver la botamanga a adentro)"));
        r.hacer("poner botamanga afuera", 10, c -> ClientPlayNetworking.send(new ElegirMoldePayload(posModeladora, pantLeg[0],
                Registries.ITEM.getId(ModeladoMod.MOLDE_BORCEGO_BOTAMANGA_AFUERA), ElegirMoldePayload.ELEGIR)));
        r.paso("pin Pant leg fijado con afuera", c -> modeladora(c, be -> be.pinCombo(Categoria.CALZADO.ordinal() * ModeladoBlockEntity.PINES_POR_CATEGORIA + pantLeg[0]) != null));
        r.hacer("poner el molde en blanco en el mismo pin", 10, c -> ClientPlayNetworking.send(new ElegirMoldePayload(posModeladora, pantLeg[0],
                Registries.ITEM.getId(ModeladoMod.MOLDE_EN_BLANCO), ElegirMoldePayload.ELEGIR)));
        r.dejarPasar(10).captura("modeladora-calzado-pant-leg");
        modelar(r, "borcegos con molde en blanco");
        r.hacer("la botamanga volvio a adentro", 5, c -> {
            ItemStack out = modeladora(c, be -> be.getStack(ModeladoBlockEntity.SALIDA).copy());
            var b = com.modamod.item.BorcegosItem.botamanga(out, true);
            verificar(b == com.modamod.item.BorcegoBotamanga.ADENTRO, "botamanga de la salida: " + b + " (esperaba ADENTRO tras el molde en blanco)");
        });
        r.hacer("cerrar Modeladora", 5, c -> c.player.closeHandledScreen());

        // ── Telar: boton Tejer ────────────────────────────────────────────────────────────
        r.hacer("abrir Telar", 0, EscenarioPantallas::abrirTelar);
        r.paso("pantalla Telar abierta", c -> c.currentScreen instanceof TelarScreen);
        r.dejarPasar(20).captura("telar-abierto");
        int pantalon = TelarPrenda.PANTALON.ordinal();
        r.hacer("tildar pantalon", 5, c -> boton(c, TelarBlockEntity.BTN_PRENDA_BASE + pantalon));
        r.hacer("la seleccion se guardo en el telar", 0, c -> verificar(telar(c, be -> be.elegida(pantalon)), "el pantalon no quedo tildado"));
        r.hacer("apretar el boton Tejer de la pantalla", 0, c -> {
            String texto = Text.translatable("modamod.telar.boton.tejer").getString();
            ButtonWidget tejer = c.currentScreen.children().stream().filter(w -> w instanceof ButtonWidget b && b.getMessage().getString().equals(texto))
                    .map(w -> (ButtonWidget) w).findFirst().orElseThrow(() -> new AssertionError("no hay boton '" + texto + "' en la pantalla del Telar"));
            verificar(tejer.mouseClicked(tejer.getX() + 5, tejer.getY() + 5, 0), "el boton Tejer no respondio al clic");
        });
        r.paso("el telar teje una prenda", c -> telar(c, be -> !be.salidaVisible().isEmpty()));
        r.hacer("la salida del telar es ropa", 5, c -> {
            ItemStack out = telar(c, be -> be.salidaVisible().copy());
            verificar(ModamodDye.isClothing(out), "salida del telar no es ropa: " + out);
        });
        r.hacer("cerrar y reabrir Telar", 10, c -> { if (c.currentScreen != null) c.player.closeHandledScreen(); abrirTelar(c); });
        r.paso("Telar reabierto", c -> c.currentScreen instanceof TelarScreen);
        r.hacer("la seleccion persiste al reabrir", 20, c -> verificar(telar(c, be -> be.elegida(pantalon)), "la seleccion se perdio al reabrir"));
        r.captura("telar-reabierto");

        // ── Empalme con filtros (2026-10-19): costados filtrados por item y por tag, y el filtro de la tolva ────────────
        BlockPos[] ep = new BlockPos[1];
        r.hacer("armar un empalme hacia el este con cofres en sus costados", 10, c -> {
            ep[0] = jugador(c).getBlockPos().add(8, 0, 8);
            enServidor(c, () -> {
                var w = jugador(c).getServerWorld();
                w.setBlockState(ep[0], com.modamod.cinta.CintaMod.EMPALME_BLOCK.getDefaultState()
                        .with(com.modamod.cinta.EmpalmeBlock.FACING, net.minecraft.util.math.Direction.EAST));
                for (var d : new net.minecraft.util.math.Direction[]{net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.SOUTH}) {
                    w.setBlockState(ep[0].offset(d), net.minecraft.block.Blocks.CHEST.getDefaultState());
                }
                return null;
            });
        });
        java.util.function.Function<MinecraftClient, com.modamod.cinta.EmpalmeBlockEntity> empalme =
                c -> enServidor(c, () -> (com.modamod.cinta.EmpalmeBlockEntity) jugador(c).getServerWorld().getBlockEntity(ep[0]));
        java.util.function.BiFunction<MinecraftClient, net.minecraft.util.math.Direction, net.minecraft.inventory.Inventory> cofre =
                (c, d) -> enServidor(c, () -> (net.minecraft.inventory.Inventory) jugador(c).getServerWorld().getBlockEntity(ep[0].offset(d)));
        // Los filtros se ponen con click derecho sobre la estructura de arriba, como el jugador.
        java.util.function.BiConsumer<MinecraftClient, Object[]> clic = (c, args) -> enServidor(c, () -> {
            var w = jugador(c).getServerWorld();
            var hit = new net.minecraft.util.hit.BlockHitResult(new net.minecraft.util.math.Vec3d(ep[0].getX() + 0.5 + (double) args[0], ep[0].getY() + 0.72, ep[0].getZ() + 0.5 + (double) args[1]),
                    net.minecraft.util.math.Direction.UP, ep[0], false);
            var estado = w.getBlockState(ep[0]);
            var jug = jugador(c);
            jug.setSneaking((boolean) args[3]);
            if (args[2] == null) estado.onUse(w, jug, hit);
            else estado.onUseWithItem(new ItemStack((net.minecraft.item.Item) args[2]), w, jug, net.minecraft.util.Hand.MAIN_HAND, hit);
            jug.setSneaking(false);
            return null;
        });
        // El frente apunta al este: izquierda = norte (dz < 0), derecha = sur (dz > 0), al medio = tolva.
        r.hacer("click con diamante en el costado izquierdo lo setea", 5, c -> clic.accept(c, new Object[]{0.0, -0.17, net.minecraft.item.Items.DIAMOND, false}));
        r.hacer("el filtro izquierdo quedo en diamante", 0, c -> {
            var be = empalme.apply(c);
            verificar(be.filtro(com.modamod.cinta.EmpalmeBlockEntity.IZQUIERDA).isOf(net.minecraft.item.Items.DIAMOND), "izquierda: " + be.filtro(com.modamod.cinta.EmpalmeBlockEntity.IZQUIERDA));
        });
        r.hacer("click con tablones de abeto en la derecha y shift con la mano vacia (por tag)", 5, c -> {
            clic.accept(c, new Object[]{0.0, 0.17, net.minecraft.item.Items.SPRUCE_PLANKS, false});
            clic.accept(c, new Object[]{0.0, 0.17, null, true});
        });
        r.hacer("la derecha filtra por tag", 0, c -> verificar(empalme.apply(c).filtroPorTag(com.modamod.cinta.EmpalmeBlockEntity.DERECHA), "shift + click no activo el tag"));
        r.hacer("el medio es el filtro de la tolva: hierro", 5, c -> clic.accept(c, new Object[]{0.0, 0.0, net.minecraft.item.Items.IRON_INGOT, false}));
        r.hacer("la tolva solo saca hierro y el diamante no", 0, c -> {
            var be = empalme.apply(c);
            verificar(be.canExtract(0, new ItemStack(net.minecraft.item.Items.IRON_INGOT), net.minecraft.util.math.Direction.DOWN), "la tolva no puede sacar hierro");
            verificar(!be.canExtract(0, new ItemStack(net.minecraft.item.Items.DIAMOND), net.minecraft.util.math.Direction.DOWN), "la tolva saco un diamante");
        });
        // Ruteo: cada item entra por atras y sale por donde le toca.
        Object[][] casos = {
                {net.minecraft.item.Items.DIAMOND, net.minecraft.util.math.Direction.NORTH, "el diamante sale por el costado izquierdo (norte)"},
                {net.minecraft.item.Items.OAK_PLANKS, net.minecraft.util.math.Direction.SOUTH, "los tablones de roble salen por la derecha (tag de planks)"},
                {net.minecraft.item.Items.COBBLESTONE, net.minecraft.util.math.Direction.EAST, "lo que no coincide sale por el frente"},
        };
        for (Object[] caso : casos) {
            r.hacer("entra " + caso[0], 0, c -> enServidor(c, () -> { ((com.modamod.cinta.EmpalmeBlockEntity) jugador(c).getServerWorld().getBlockEntity(ep[0])).setStack(0, new ItemStack((net.minecraft.item.Item) caso[0])); return null; }));
            r.paso((String) caso[2], c -> {
                var inv = cofre.apply(c, (net.minecraft.util.math.Direction) caso[1]);
                for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf((net.minecraft.item.Item) caso[0])) return true;
                return false;
            });
        }
        r.hacer("click con la mano vacia sin shift borra el filtro", 5, c -> clic.accept(c, new Object[]{0.0, -0.17, null, false}));
        r.hacer("el filtro izquierdo se borro", 0, c -> verificar(empalme.apply(c).filtro(com.modamod.cinta.EmpalmeBlockEntity.IZQUIERDA).isEmpty(), "no se borro"));

        // ── Empalme sin filtros: reparte lo no registrado entre sus salidas libres, y el filtro de la tolva manda hacia abajo ──
        BlockPos[] e2 = new BlockPos[1];
        r.hacer("armar un segundo empalme con cofres N, S, E y uno abajo", 10, c -> {
            e2[0] = jugador(c).getBlockPos().add(14, 0, 8);
            enServidor(c, () -> {
                var w = jugador(c).getServerWorld();
                w.setBlockState(e2[0], com.modamod.cinta.CintaMod.EMPALME_BLOCK.getDefaultState()
                        .with(com.modamod.cinta.EmpalmeBlock.FACING, net.minecraft.util.math.Direction.EAST));
                for (var d : new net.minecraft.util.math.Direction[]{net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.NORTH,
                        net.minecraft.util.math.Direction.SOUTH, net.minecraft.util.math.Direction.DOWN}) {
                    w.setBlockState(e2[0].offset(d), net.minecraft.block.Blocks.CHEST.getDefaultState());
                }
                return null;
            });
        });
        java.util.function.BiFunction<MinecraftClient, net.minecraft.util.math.Direction, Integer> cuenta = (c, d) -> enServidor(c, () -> {
            var inv = (net.minecraft.inventory.Inventory) jugador(c).getServerWorld().getBlockEntity(e2[0].offset(d));
            int n = 0;
            for (int i = 0; i < inv.size(); i++) n += inv.getStack(i).getCount();
            return n;
        });
        for (int k = 0; k < 6; k++) {
            r.hacer("entra una piedra (" + (k + 1) + "/6) por atras", 0, c -> enServidor(c, () -> {
                ((com.modamod.cinta.EmpalmeBlockEntity) jugador(c).getServerWorld().getBlockEntity(e2[0])).setStack(0, new ItemStack(net.minecraft.item.Items.COBBLESTONE));
                return null;
            }));
            r.paso("la piedra " + (k + 1) + " salio", c -> enServidor(c, () -> ((com.modamod.cinta.EmpalmeBlockEntity) jugador(c).getServerWorld().getBlockEntity(e2[0])).isEmpty()));
        }
        r.hacer("lo no registrado se reparte parejo entre las tres salidas libres", 0, c -> {
            int n = cuenta.apply(c, net.minecraft.util.math.Direction.NORTH), sur = cuenta.apply(c, net.minecraft.util.math.Direction.SOUTH), e = cuenta.apply(c, net.minecraft.util.math.Direction.EAST);
            verificar(n == 2 && sur == 2 && e == 2, "reparto N/S/E = " + n + "/" + sur + "/" + e + " (esperaba 2/2/2)");
        });
        r.hacer("filtro de la tolva: hierro; entra hierro y una piedra", 0, c -> enServidor(c, () -> {
            var be = (com.modamod.cinta.EmpalmeBlockEntity) jugador(c).getServerWorld().getBlockEntity(e2[0]);
            be.setFiltro(com.modamod.cinta.EmpalmeBlockEntity.TOLVA, new ItemStack(net.minecraft.item.Items.IRON_INGOT));
            be.setStack(0, new ItemStack(net.minecraft.item.Items.IRON_INGOT));
            return null;
        }));
        r.paso("el hierro baja al cofre de abajo", c -> cuenta.apply(c, net.minecraft.util.math.Direction.DOWN) == 1);
        r.hacer("y no se colo por ninguna salida libre", 0, c -> {
            int n = cuenta.apply(c, net.minecraft.util.math.Direction.NORTH), sur = cuenta.apply(c, net.minecraft.util.math.Direction.SOUTH), e = cuenta.apply(c, net.minecraft.util.math.Direction.EAST);
            verificar(n + sur + e == 6, "el hierro aparecio en una salida libre: N/S/E = " + n + "/" + sur + "/" + e);
        });
        // Tolva de abajo llena (2026-10-21, "si un hopper bajo una cinta esta lleno los items no deberian atorarse arriba"):
        // el hierro registrado sigue por una salida libre.
        r.hacer("llenar el cofre de abajo y entra otro hierro", 0, c -> enServidor(c, () -> {
            var w = jugador(c).getServerWorld();
            var abajo = (net.minecraft.inventory.Inventory) w.getBlockEntity(e2[0].down());
            for (int i = 0; i < abajo.size(); i++) abajo.setStack(i, new ItemStack(net.minecraft.item.Items.DIRT, 64));
            ((com.modamod.cinta.EmpalmeBlockEntity) w.getBlockEntity(e2[0])).setStack(0, new ItemStack(net.minecraft.item.Items.IRON_INGOT));
            return null;
        }));
        r.paso("el hierro no se atora: sale por una libre", c -> enServidor(c, () -> ((com.modamod.cinta.EmpalmeBlockEntity) jugador(c).getServerWorld().getBlockEntity(e2[0])).isEmpty()));
        r.hacer("y llego a un cofre libre", 0, c -> {
            int n = cuenta.apply(c, net.minecraft.util.math.Direction.NORTH), sur = cuenta.apply(c, net.minecraft.util.math.Direction.SOUTH), e = cuenta.apply(c, net.minecraft.util.math.Direction.EAST);
            verificar(n + sur + e == 7, "N/S/E = " + n + "/" + sur + "/" + e + " (esperaba 7)");
        });

        // ── Empalme con filtros: los items SOLTADOS (no colocados en la cinta) tambien siguen los filtros ────────────────────
        BlockPos[] e3 = new BlockPos[1];
        r.hacer("armar un tercer empalme con cintas saliendo al norte, sur y este", 10, c -> {
            e3[0] = jugador(c).getBlockPos().add(14, 0, 16);
            enServidor(c, () -> {
                var w = jugador(c).getServerWorld();
                w.setBlockState(e3[0], com.modamod.cinta.CintaMod.EMPALME_BLOCK.getDefaultState()
                        .with(com.modamod.cinta.EmpalmeBlock.FACING, net.minecraft.util.math.Direction.EAST));
                for (var d : new net.minecraft.util.math.Direction[]{net.minecraft.util.math.Direction.EAST, net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.SOUTH}) {
                    w.setBlockState(e3[0].offset(d), com.modamod.cinta.CintaMod.CINTA_BLOCK.getDefaultState().with(com.modamod.cinta.CintaBlock.FACING, d));
                }
                var be = (com.modamod.cinta.EmpalmeBlockEntity) w.getBlockEntity(e3[0]);
                be.setFiltro(com.modamod.cinta.EmpalmeBlockEntity.IZQUIERDA, new ItemStack(net.minecraft.item.Items.DIAMOND));
                be.setFiltro(com.modamod.cinta.EmpalmeBlockEntity.DERECHA, new ItemStack(net.minecraft.item.Items.SPRUCE_PLANKS));
                be.alternarTag(com.modamod.cinta.EmpalmeBlockEntity.DERECHA);
                return null;
            });
        });
        Object[][] soltados = {
                {net.minecraft.item.Items.DIAMOND, net.minecraft.util.math.Direction.NORTH, "el diamante soltado se va por la cinta de la izquierda (norte)"},
                {net.minecraft.item.Items.OAK_PLANKS, net.minecraft.util.math.Direction.SOUTH, "los tablones soltados se van por la derecha (tag)"},
                {net.minecraft.item.Items.COBBLESTONE, net.minecraft.util.math.Direction.EAST, "lo no registrado soltado sigue por el frente"},
        };
        for (Object[] caso : soltados) {
            int[] id = {-1};
            r.hacer("soltar " + caso[0] + " sobre el empalme", 0, c -> enServidor(c, () -> {
                var w = jugador(c).getServerWorld();
                var e = new net.minecraft.entity.ItemEntity(w, e3[0].getX() + 0.5, e3[0].getY() + 0.5, e3[0].getZ() + 0.5, new ItemStack((net.minecraft.item.Item) caso[0]), 0, 0, 0);
                w.spawnEntity(e);
                id[0] = e.getId();
                return null;
            }));
            r.dejarPasar(60);
            r.hacer((String) caso[2], 0, c -> enServidor(c, () -> {
                var e = jugador(c).getServerWorld().getEntityById(id[0]);
                var d = (net.minecraft.util.math.Direction) caso[1];
                if (e == null) {
                    // La cinta levanta los items soltados que la tocan: tiene que estar en la cinta del lado que corresponde (o mas alla).
                    var cinta = jugador(c).getServerWorld().getBlockEntity(e3[0].offset(d));
                    verificar(cinta instanceof com.modamod.cinta.CintaBlockEntity cb && cb.carga().isOf((net.minecraft.item.Item) caso[0]),
                            "el item se fue y no esta en la cinta de " + d + ": N=" + ((com.modamod.cinta.CintaBlockEntity) jugador(c).getServerWorld().getBlockEntity(e3[0].north())).carga()
                                    + " S=" + ((com.modamod.cinta.CintaBlockEntity) jugador(c).getServerWorld().getBlockEntity(e3[0].south())).carga()
                                    + " E=" + ((com.modamod.cinta.CintaBlockEntity) jugador(c).getServerWorld().getBlockEntity(e3[0].east())).carga()
                                    + " empalme=" + ((com.modamod.cinta.EmpalmeBlockEntity) jugador(c).getServerWorld().getBlockEntity(e3[0])).carga());
                    return null;
                }
                double desp = (e.getX() - (e3[0].getX() + 0.5)) * d.getOffsetX() + (e.getZ() - (e3[0].getZ() + 0.5)) * d.getOffsetZ();
                verificar(desp > 0.7, "el item avanzo " + desp + " hacia " + d + " (esperaba > 0,7); pos=" + e.getPos());
                e.discard();
                return null;
            }));
        }

        // ── Tubos de transporte (2026-10-20): dos tubos hacia arriba y un empalme de tubo con filtros y cofres ──────────
        BlockPos[] tb = new BlockPos[1];
        r.hacer("armar tubos verticales con un empalme de tubo y cofres N, E, S y O", 10, c -> {
            tb[0] = jugador(c).getBlockPos().add(20, 0, 8);
            enServidor(c, () -> {
                var w = jugador(c).getServerWorld();
                for (int i = 0; i < 2; i++) w.setBlockState(tb[0].up(i), com.modamod.cinta.CintaMod.TUBO_BLOCK.getDefaultState());
                com.modamod.cinta.TuboBlock.llenar(w, tb[0], true);   // con agua suben
                BlockPos nudo = tb[0].up(2);
                w.setBlockState(nudo, com.modamod.cinta.CintaMod.EMPALME_TUBO_BLOCK.getDefaultState());
                for (var d : new net.minecraft.util.math.Direction[]{net.minecraft.util.math.Direction.NORTH, net.minecraft.util.math.Direction.EAST,
                        net.minecraft.util.math.Direction.SOUTH, net.minecraft.util.math.Direction.WEST}) {
                    w.setBlockState(nudo.offset(d), net.minecraft.block.Blocks.CHEST.getDefaultState());
                }
                var be = (com.modamod.cinta.EmpalmeTuboBlockEntity) w.getBlockEntity(nudo);
                be.setFiltro(net.minecraft.util.math.Direction.NORTH, new ItemStack(net.minecraft.item.Items.DIAMOND));
                be.setFiltro(net.minecraft.util.math.Direction.EAST, new ItemStack(net.minecraft.item.Items.OAK_PLANKS));
                be.alternarTag(net.minecraft.util.math.Direction.EAST);
                return null;
            });
        });
        java.util.function.BiFunction<MinecraftClient, net.minecraft.util.math.Direction, Integer> cuentaTubo = (c, d) -> enServidor(c, () -> {
            var inv = (net.minecraft.inventory.Inventory) jugador(c).getServerWorld().getBlockEntity(tb[0].up(2).offset(d));
            int n = 0;
            for (int i = 0; i < inv.size(); i++) n += inv.getStack(i).getCount();
            return n;
        });
        Object[][] casosTubo = {
                {net.minecraft.item.Items.DIAMOND, net.minecraft.util.math.Direction.NORTH},
                {net.minecraft.item.Items.BIRCH_PLANKS, net.minecraft.util.math.Direction.EAST}};
        for (Object[] caso : casosTubo) {
            r.hacer("entra " + ((net.minecraft.item.Item) caso[0]).getName().getString() + " por el tubo de abajo", 0, c -> enServidor(c, () -> {
                ((com.modamod.cinta.TuboBlockEntity) jugador(c).getServerWorld().getBlockEntity(tb[0])).setStack(0, new ItemStack((net.minecraft.item.Item) caso[0]));
                return null;
            }));
            r.paso("sale por " + caso[1], c -> cuentaTubo.apply(c, (net.minecraft.util.math.Direction) caso[1]) == 1);
        }
        for (int k = 0; k < 2; k++) {
            r.hacer("entra una piedra (" + (k + 1) + "/2) por el tubo", 0, c -> enServidor(c, () -> {
                ((com.modamod.cinta.TuboBlockEntity) jugador(c).getServerWorld().getBlockEntity(tb[0])).setStack(0, new ItemStack(net.minecraft.item.Items.STONE));
                return null;
            }));
            final int n = k + 1;
            r.paso("la piedra " + n + " llego a un cofre libre", c -> cuentaTubo.apply(c, net.minecraft.util.math.Direction.SOUTH) + cuentaTubo.apply(c, net.minecraft.util.math.Direction.WEST) == n);
        }
        r.hacer("lo registrado no se colo por las salidas libres y las piedras se repartieron", 0, c -> {
            verificar(cuentaTubo.apply(c, net.minecraft.util.math.Direction.NORTH) == 1 && cuentaTubo.apply(c, net.minecraft.util.math.Direction.EAST) == 1,
                    "el diamante o los tablones salieron por otro lado");
            verificar(cuentaTubo.apply(c, net.minecraft.util.math.Direction.SOUTH) == 1 && cuentaTubo.apply(c, net.minecraft.util.math.Direction.WEST) == 1,
                    "las piedras no se repartieron: S=" + cuentaTubo.apply(c, net.minecraft.util.math.Direction.SOUTH) + " O=" + cuentaTubo.apply(c, net.minecraft.util.math.Direction.WEST));
        });

        // ── Extractor (2026-10-21): de costado con filtro de diamante, y hacia arriba ──────────────────────────────
        BlockPos[] ex = new BlockPos[1];
        r.hacer("armar un extractor de costado con filtro y otro hacia arriba", 10, c -> {
            ex[0] = jugador(c).getBlockPos().add(26, 0, 8);
            enServidor(c, () -> {
                var w = jugador(c).getServerWorld();
                var ext = com.modamod.cinta.CintaMod.EXTRACTOR_BLOCK.getDefaultState();
                w.setBlockState(ex[0], net.minecraft.block.Blocks.CHEST.getDefaultState());
                w.setBlockState(ex[0].east(), ext.with(com.modamod.cinta.ExtractorBlock.FACING, net.minecraft.util.math.Direction.EAST));
                w.setBlockState(ex[0].east(2), net.minecraft.block.Blocks.CHEST.getDefaultState());
                var a = (net.minecraft.inventory.Inventory) w.getBlockEntity(ex[0]);
                a.setStack(0, new ItemStack(net.minecraft.item.Items.COBBLESTONE, 3));
                a.setStack(1, new ItemStack(net.minecraft.item.Items.DIAMOND, 2));
                ((com.modamod.cinta.ExtractorBlockEntity) w.getBlockEntity(ex[0].east())).setFiltro(new ItemStack(net.minecraft.item.Items.DIAMOND));
                BlockPos q = ex[0].south(3);
                w.setBlockState(q, net.minecraft.block.Blocks.CHEST.getDefaultState());
                w.setBlockState(q.up(), ext.with(com.modamod.cinta.ExtractorBlock.FACING, net.minecraft.util.math.Direction.UP));
                w.setBlockState(q.up(2), net.minecraft.block.Blocks.CHEST.getDefaultState());
                ((net.minecraft.inventory.Inventory) w.getBlockEntity(q)).setStack(0, new ItemStack(net.minecraft.item.Items.IRON_INGOT, 2));
                return null;
            });
        });
        java.util.function.BiFunction<MinecraftClient, Object[], Integer> hay = (c, caso) -> enServidor(c, () -> {
            var inv = (net.minecraft.inventory.Inventory) jugador(c).getServerWorld().getBlockEntity((BlockPos) caso[0]);
            int n = 0;
            for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf((net.minecraft.item.Item) caso[1])) n += inv.getStack(i).getCount();
            return n;
        });
        r.paso("los 2 diamantes pasaron de costado", c -> hay.apply(c, new Object[]{ex[0].east(2), net.minecraft.item.Items.DIAMOND}) == 2);
        r.paso("el hierro subio al cofre de arriba", c -> hay.apply(c, new Object[]{ex[0].south(3).up(2), net.minecraft.item.Items.IRON_INGOT}) == 2);
        r.hacer("la piedra no paso el filtro", 0, c -> verificar(hay.apply(c, new Object[]{ex[0].east(2), net.minecraft.item.Items.COBBLESTONE}) == 0
                && hay.apply(c, new Object[]{ex[0], net.minecraft.item.Items.COBBLESTONE}) == 3, "la piedra se movio con filtro de diamante"));

        // ── Guardarropas (2026-10-21): guardar el outfit puesto con nombre, ponerlo entero y desarmarlo ───────────────────
        BlockPos[] gr = new BlockPos[1];
        java.util.function.Function<MinecraftClient, com.modamod.guardarropas.GuardarropasBlockEntity> mueble =
                c -> (com.modamod.guardarropas.GuardarropasBlockEntity) jugador(c).getServerWorld().getBlockEntity(gr[0]);   // sin enServidor: no se anida
        java.util.function.Function<MinecraftClient, Integer> puestas = c -> enServidor(c, () -> {
            int[] n = {0};
            dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(jugador(c)).ifPresent(comp -> {
                for (int cat = 0; cat < com.modamod.guardarropas.GuardarropasBlockEntity.CATEGORIAS; cat++) {
                    var inv = com.modamod.guardarropas.GuardarropasBlockEntity.inventarioTrinkets(comp, cat);
                    for (int i = 0; inv != null && i < inv.size(); i++) if (!inv.getStack(i).isEmpty()) n[0]++;
                }
            });
            for (var es : com.modamod.ropa.Cosmeticos.ZONAS) if (!jugador(c).getEquippedStack(es).isEmpty()) n[0]++;
            return n[0];
        });
        r.hacer("colocar un Guardarropas y vestirme con remera, pantalon y casco", 10, c -> {
            c.player.closeHandledScreen();
            gr[0] = jugador(c).getBlockPos().add(-6, 0, -4);
            enServidor(c, () -> {
                var w = jugador(c).getServerWorld();
                w.setBlockState(gr[0], com.modamod.guardarropas.GuardarropasMod.GUARDARROPAS_BLOCK.getDefaultState());
                dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(jugador(c)).ifPresent(comp -> {
                    var cam = com.modamod.guardarropas.GuardarropasBlockEntity.inventarioTrinkets(comp, com.modamod.guardarropas.GuardarropasBlockEntity.REMERA);
                    cam.setStack(0, new ItemStack(com.modamod.sublimadora.ModItems.REMERA));
                    var pan = com.modamod.guardarropas.GuardarropasBlockEntity.inventarioTrinkets(comp, com.modamod.guardarropas.GuardarropasBlockEntity.PANTALON);
                    pan.setStack(0, new ItemStack(ModamodItems.PANTALON));
                });
                jugador(c).equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.item.Items.IRON_HELMET));
                return null;
            });
        });
        r.hacer("guardar el outfit como Gala: queda desnudo y el mueble lo tiene", 0, c -> {
            verificar(puestas.apply(c) == 3, "esperaba 3 piezas puestas y hay " + puestas.apply(c));
            boolean ok = enServidor(c, () -> mueble.apply(c) == null ? false : mueble.apply(c).guardarOutfit(jugador(c), "Gala"));
        });
        r.hacer("verificar el guardado", 0, c -> {
            verificar(puestas.apply(c) == 0, "despues de guardar todavia lleva " + puestas.apply(c) + " piezas");
            var o = enServidor(c, () -> mueble.apply(c).outfits().size() + ":" + mueble.apply(c).outfits().get(0).nombre() + ":" + mueble.apply(c).outfits().get(0).piezas().size());
            verificar(o.equals("1:Gala:3"), "outfit guardado = " + o + " (esperaba 1:Gala:3)");
        });
        r.hacer("ponerme una remera suelta y despues el outfit Gala: la remera queda como Anterior", 0, c -> enServidor(c, () -> {
            dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(jugador(c)).ifPresent(comp ->
                    com.modamod.guardarropas.GuardarropasBlockEntity.inventarioTrinkets(comp, com.modamod.guardarropas.GuardarropasBlockEntity.REMERA).setStack(1, new ItemStack(com.modamod.sublimadora.ModItems.REMERA)));
            mueble.apply(c).ponerOutfit(jugador(c), 0);
            return null;
        }));
        r.hacer("llevo las 3 piezas de Gala y el mueble guarda el Anterior", 0, c -> {
            verificar(puestas.apply(c) == 3, "despues de ponerme el outfit llevo " + puestas.apply(c) + " piezas (esperaba 3)");
            var o = enServidor(c, () -> mueble.apply(c).outfits().size() + ":" + mueble.apply(c).outfits().get(0).nombre());
            verificar(o.equals("1:Anterior"), "outfits = " + o + " (esperaba 1:Anterior)");
        });
        r.hacer("desarmar el Anterior: vuelve al inventario", 0, c -> enServidor(c, () -> {
            mueble.apply(c).desarmar(jugador(c), 0);
            return null;
        }));
        r.hacer("el mueble queda vacio", 0, c -> verificar(enServidor(c, () -> mueble.apply(c).outfits().isEmpty()), "quedaron outfits"));
        r.hacer("me pongo Gala: el mueble se acuerda del nombre", 0, c -> enServidor(c, () -> {
            var m = mueble.apply(c);
            m.guardarOutfit(jugador(c), "Gala");
            m.ponerOutfit(jugador(c), 0);
            return null;
        }));
        r.hacer("guardar sin nombre usa el de Gala", 0, c -> {
            enServidor(c, () -> { mueble.apply(c).guardarOutfit(jugador(c), ""); return null; });
            var o = enServidor(c, () -> mueble.apply(c).outfits().get(0).nombre());
            verificar(o.equals("Gala"), "el outfit se llamo " + o + " (esperaba Gala)");
        });
        r.hacer("un nombre repetido recibe un numero", 0, c -> {
            enServidor(c, () -> {
                dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(jugador(c)).ifPresent(comp ->
                        com.modamod.guardarropas.GuardarropasBlockEntity.inventarioTrinkets(comp, com.modamod.guardarropas.GuardarropasBlockEntity.REMERA).setStack(0, new ItemStack(com.modamod.sublimadora.ModItems.REMERA)));
                mueble.apply(c).guardarOutfit(jugador(c), "Gala");
                return null;
            });
            var o = enServidor(c, () -> mueble.apply(c).outfits().get(1).nombre());
            verificar(o.equals("Gala 2"), "el segundo se llamo " + o + " (esperaba Gala 2)");
            enServidor(c, () -> { mueble.apply(c).desarmar(jugador(c), 1); mueble.apply(c).desarmar(jugador(c), 0); return null; });
            enServidor(c, () -> {
                dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(jugador(c)).ifPresent(comp ->
                        com.modamod.guardarropas.GuardarropasBlockEntity.inventarioTrinkets(comp, com.modamod.guardarropas.GuardarropasBlockEntity.REMERA).setStack(0, new ItemStack(com.modamod.sublimadora.ModItems.REMERA)));
                return null;
            });
        });
        r.hacer("guardar otra vez y abrir la pantalla", 0, c -> enServidor(c, () -> {
            mueble.apply(c).guardarOutfit(jugador(c), "Salida de noche");
            dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(jugador(c)).ifPresent(comp ->
                    com.modamod.guardarropas.GuardarropasBlockEntity.inventarioTrinkets(comp, com.modamod.guardarropas.GuardarropasBlockEntity.REMERA).setStack(0, new ItemStack(com.modamod.sublimadora.ModItems.REMERA)));
            jugador(c).openHandledScreen(mueble.apply(c));
            return null;
        }));
        r.paso("pantalla del Guardarropas abierta", c -> c.currentScreen instanceof com.modamod.client.GuardarropasScreen);
        r.dejarPasar(20).captura("guardarropas-gui");

        // ── Maletin de outfits (2026-10-21): vinculado a la coleccion del Guardarropas, solo viste ──────────────────────────
        java.util.function.Function<MinecraftClient, ItemStack> enMano = c -> jugador(c).getStackInHand(net.minecraft.util.Hand.MAIN_HAND);
        java.util.function.BiConsumer<MinecraftClient, ItemStack[]> vestir = (c, prendas) -> {
            dev.emi.trinkets.api.TrinketsApi.getTrinketComponent(jugador(c)).ifPresent(comp -> {
                int[] cat = {com.modamod.guardarropas.GuardarropasBlockEntity.REMERA, com.modamod.guardarropas.GuardarropasBlockEntity.PANTALON};
                for (int i = 0; i < prendas.length; i++) com.modamod.guardarropas.GuardarropasBlockEntity.inventarioTrinkets(comp, cat[i]).setStack(0, prendas[i]);
            });
        };
        r.hacer("cerrar la pantalla, vaciar el Guardarropas y guardar Gala y Casual en el MUEBLE", 0, c -> {
            c.player.closeHandledScreen();
            enServidor(c, () -> {
                var m0 = mueble.apply(c);
                m0.guardarOutfit(jugador(c), "limpiar");        // se saca todo lo puesto
                m0.almacen().outfits().clear();                  // y se vacia la coleccion
                vestir.accept(c, new ItemStack[]{new ItemStack(com.modamod.sublimadora.ModItems.REMERA), new ItemStack(ModamodItems.PANTALON)});
                m0.guardarOutfit(jugador(c), "Gala");
                vestir.accept(c, new ItemStack[]{new ItemStack(com.modamod.sublimadora.ModItems.REMERA), ItemStack.EMPTY});
                m0.guardarOutfit(jugador(c), "Casual");
                ItemStack maletin = new ItemStack(com.modamod.item.ModamodItems.MALETIN);
                maletin.set(com.modamod.item.ModamodComponents.MALETIN_COLECCION, m0.coleccion());
                jugador(c).setStackInHand(net.minecraft.util.Hand.MAIN_HAND, maletin);
                return null;
            });
        });
        r.hacer("el Maletin comparte la coleccion del mueble: ve Gala y Casual", 0, c -> {
            var o = enServidor(c, () -> {
                var al = com.modamod.guardarropas.Colecciones.almacen(c.getServer(), com.modamod.guardarropas.MaletinItem.coleccionDe(enMano.apply(c)));
                return al.outfits().size() + ":" + al.outfits().get(0).nombre();
            });
            verificar(o.equals("2:Gala"), "maletin = " + o + " (esperaba 2:Gala)");
            verificar(puestas.apply(c) == 0, "despues de guardar todavia lleva " + puestas.apply(c));
        });
        r.hacer("Shift + clic: me pone Gala (2 piezas) y queda Casual", 0, c -> {
            enServidor(c, () -> { com.modamod.guardarropas.MaletinItem.siguiente(jugador(c), enMano.apply(c)); return null; });
            verificar(puestas.apply(c) == 2, "con Gala puesta llevo " + puestas.apply(c) + " piezas (esperaba 2)");
        });
        // 2026-10-21, "con el anterior me parece que tendría que ser solo si el outfit puesto no fue guardado": un outfit guardado
        // que me saco vuelve a la lista con su nombre (al final, así el Shift + clic hace la ronda).
        r.hacer("otro Shift + clic: me pone Casual y Gala vuelve al MUEBLE con su nombre", 0, c -> {
            enServidor(c, () -> { com.modamod.guardarropas.MaletinItem.siguiente(jugador(c), enMano.apply(c)); return null; });
            verificar(puestas.apply(c) == 1, "con Casual puesta llevo " + puestas.apply(c) + " piezas (esperaba 1)");
            var o = enServidor(c, () -> mueble.apply(c).outfits().size() + ":" + mueble.apply(c).outfits().get(0).nombre() + ":" + mueble.apply(c).outfits().get(0).piezas().size());
            verificar(o.equals("1:Gala:2"), "el mueble tiene " + o + " (esperaba 1:Gala:2)");
        });
        r.hacer("tercer Shift + clic: me devuelve Gala y Casual vuelve con su nombre (la ronda)", 0, c -> {
            enServidor(c, () -> { com.modamod.guardarropas.MaletinItem.siguiente(jugador(c), enMano.apply(c)); return null; });
            verificar(puestas.apply(c) == 2, "con Gala otra vez llevo " + puestas.apply(c) + " piezas (esperaba 2)");
            var o = enServidor(c, () -> mueble.apply(c).outfits().size() + ":" + mueble.apply(c).outfits().get(0).nombre() + ":" + mueble.apply(c).outfits().get(0).piezas().size());
            verificar(o.equals("1:Casual:1"), "el mueble tiene " + o + " (esperaba 1:Casual:1)");
        });
        r.hacer("un Anterior sin usar no se pisa: pasa a Anterior 2", 0, c -> {
            enServidor(c, () -> {
                // Lo puesto sin guardar (se olvida de dónde vino) pasa a Anterior; con ese Anterior sin usar y otra vez algo sin
                // guardar puesto, me pongo otro outfit: el Anterior de antes se renombra.
                var m = mueble.apply(c);
                m.almacen().ultimos().remove(jugador(c).getUuid());
                m.ponerOutfit(jugador(c), 0);                     // me pongo Casual: Gala (sin guardar) queda como Anterior
                m.almacen().ultimos().remove(jugador(c).getUuid());
                m.almacen().outfits().add(new com.modamod.guardarropas.GuardarropasBlockEntity.Outfit("Fiesta",
                        new java.util.LinkedHashMap<>(java.util.Map.of(com.modamod.guardarropas.GuardarropasBlockEntity.clave("torso", "prenda", 0), new ItemStack(com.modamod.sublimadora.ModItems.REMERA)))));
                m.ponerOutfit(jugador(c), 1);
                return null;
            });
            var o = enServidor(c, () -> mueble.apply(c).outfits().get(0).nombre() + "," + mueble.apply(c).outfits().get(1).nombre());
            verificar(o.equals("Anterior,Anterior 2"), "lista = " + o + " (esperaba Anterior,Anterior 2)");
        });
        r.hacer("abrir el Maletin con clic derecho", 0, c -> enServidor(c, () -> {
            jugador(c).setSneaking(false);
            enMano.apply(c).use(jugador(c).getServerWorld(), jugador(c), net.minecraft.util.Hand.MAIN_HAND);
            return null;
        }));
        r.paso("pantalla del Maletin abierta", c -> c.currentScreen instanceof com.modamod.client.MaletinScreen);
        r.dejarPasar(20).captura("maletin-gui");

        // ── Maniqui: botones de chaqueta, capucha y twirl en loop (2026-10-19) ──────────────────────
        BlockPos[] posManiqui = new BlockPos[1];
        java.util.function.Function<MinecraftClient, com.modamod.maniqui.ManiquiBlockEntity> maniqui =
                c -> enServidor(c, () -> (com.modamod.maniqui.ManiquiBlockEntity) jugador(c).getServerWorld().getBlockEntity(posManiqui[0]));
        r.hacer("colocar un Maniqui con un top con capucha y una pollera", 10, c -> {
            c.player.closeHandledScreen();
            posManiqui[0] = jugador(c).getBlockPos().add(0, 0, -4);
            enServidor(c, () -> {
                var w = jugador(c).getServerWorld();
                w.setBlockState(posManiqui[0], com.modamod.maniqui.ManiquiMod.MANIQUI_BLOCK.getDefaultState());
                var be = (com.modamod.maniqui.ManiquiBlockEntity) w.getBlockEntity(posManiqui[0]);
                ItemStack top = new ItemStack(com.modamod.sublimadora.ModItems.REMERA);
                com.modamod.item.TopCorte.setConCapucha(top, true);
                be.setStack(com.modamod.guardarropas.GuardarropasBlockEntity.REMERA * com.modamod.guardarropas.GuardarropasBlockEntity.POR_CATEGORIA, top);
                be.setStack(com.modamod.guardarropas.GuardarropasBlockEntity.PANTALON * com.modamod.guardarropas.GuardarropasBlockEntity.POR_CATEGORIA + 1, new ItemStack(ModamodItems.POLLERA));
                return null;
            });
        });
        r.hacer("abrir la pantalla del Maniqui", 0, c -> enServidor(c, () -> {
            jugador(c).openHandledScreen((com.modamod.maniqui.ManiquiBlockEntity) jugador(c).getServerWorld().getBlockEntity(posManiqui[0]));
            return null;
        }));
        r.paso("pantalla del Maniqui abierta", c -> c.currentScreen instanceof com.modamod.client.ManiquiScreen);
        r.dejarPasar(20).captura("maniqui-gui");
        r.hacer("boton Chaqueta abre el frente", 10, c -> boton(c, com.modamod.maniqui.ManiquiBlockEntity.BTN_FRENTE));
        r.hacer("el frente quedo abierto", 0, c -> verificar(maniqui.apply(c).frenteAbierto(), "el boton Chaqueta no abrio el frente"));
        r.hacer("boton Capucha la sube", 10, c -> boton(c, com.modamod.maniqui.ManiquiBlockEntity.BTN_CAPUCHA));
        r.hacer("la capucha quedo arriba", 0, c -> verificar(maniqui.apply(c).capuchaArriba(), "el boton Capucha no la subio"));
        r.hacer("boton Twirl lo prende", 10, c -> boton(c, com.modamod.maniqui.ManiquiBlockEntity.BTN_TWIRL));
        r.hacer("el twirl quedo prendido", 0, c -> verificar(maniqui.apply(c).twirl(), "el boton Twirl no lo prendio"));
        r.dejarPasar(20).captura("maniqui-gui-twirl");
        r.hacer("los botones se desactivan al segundo clic", 10, c -> {
            boton(c, com.modamod.maniqui.ManiquiBlockEntity.BTN_FRENTE);
            boton(c, com.modamod.maniqui.ManiquiBlockEntity.BTN_CAPUCHA);
            boton(c, com.modamod.maniqui.ManiquiBlockEntity.BTN_TWIRL);
        });
        r.hacer("todo volvio a su estado", 0, c -> {
            var be = maniqui.apply(c);
            verificar(!be.frenteAbierto() && !be.capuchaArriba() && !be.twirl(), "los botones no alternan: frente=" + be.frenteAbierto() + " capucha=" + be.capuchaArriba() + " twirl=" + be.twirl());
        });
        r.hacer("cerrar el Maniqui", 5, c -> c.player.closeHandledScreen());
        return r;
    }
}
