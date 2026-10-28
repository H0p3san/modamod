package com.modamod;

import com.modamod.body.ComandoCuerpo;
import com.modamod.body.PerfilesDeCuerpo;
import com.modamod.garment.PrendasDelMod;
import com.modamod.item.ModamodComponents;
import com.modamod.item.ModamodItems;
import com.modamod.screen.ModamodScreenHandlers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class Modamod implements ModInitializer {

    public static final String MOD_ID = "modamod";

    @Override
    public void onInitialize() {
        ModamodItems.init();
        ModamodComponents.init();
        com.modamod.receta.ModamodRecetas.init();
        // Que items son prendas del sistema de capas. Va DESPUES de los
        // items: registra por instancia, no por id.
        PrendasDelMod.init();
        PerfilesDeCuerpo.init();
        // A la venta (2026-10-22, "va a estar a la venta"): el clérigo aprendiz vende Estrógenos y Testosterona.
        net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper.registerVillagerOffers(
                net.minecraft.village.VillagerProfession.CLERIC, 2, ofertas -> {
                    for (net.minecraft.item.Item hormona : new net.minecraft.item.Item[] { ModamodItems.ESTROGENOS, ModamodItems.TESTOSTERONA }) {
                        ofertas.add((entidad, azar) -> new net.minecraft.village.TradeOffer(
                                new net.minecraft.village.TradedItem(net.minecraft.item.Items.EMERALD, 6),
                                new net.minecraft.item.ItemStack(hormona), 12, 5, 0.05f));
                    }
                });
        ComandoCuerpo.init();
        com.modamod.body.RedCuerpo.init();
        com.modamod.util.RedTwirl.init();
        com.modamod.util.RedCapucha.init();
        com.modamod.util.RedFrente.init();
        com.modamod.ropa.Cosmeticos.init();
        com.modamod.ropa.RedRopa.init();
        ModamodScreenHandlers.init();
        com.modamod.modelado.ModeladoMod.register();
        com.modamod.estilista.EstilistaMod.register();
        com.modamod.telar.TelarMod.register();
        com.modamod.tinturas.TinturasMod.register();
        com.modamod.guardarropas.GuardarropasMod.register();
        com.modamod.maniqui.ManiquiMod.register();
        com.modamod.cinta.CintaMod.register();
        com.modamod.estilado.EstiladoMod.register();
        registrarPestanaCreativa();
    }

    /**
     * Sin esto, la ÚNICA forma de conseguir cualquiera de estos ítems era
     * ya saber la receta de memoria — ninguno aparecía en el buscador
     * creativo. Se notó recién con calientabrazos ("me faltan"), pero el
     * agujero es de TODO `ModamodItems` (pantalón, medias, los 16 moldes,
     * los 3 patrones): `SublimadoraMod` solo agrega los ítems de su propio
     * paquete (remera + sus 2 moldes cíclicos).
     *
     * <p>Pestaña PROPIA desde 2026-09-28 ("meteme todas las cosas del mod en
     * una pestaña"): antes todo se colaba en la vanilla de Bloques
     * funcionales, mezclado con lo de Minecraft. Incluye también lo de la
     * Sublimadora (que antes agregaba {@code SublimadoraMod} por su cuenta).
     * Las entradas se arman recién al abrir la pestaña, así que referenciar
     * ítems de otro ModInitializer acá no depende del orden de carga.
     */
    public static final ItemGroup PESTANA = FabricItemGroup.builder()
            .icon(() -> new ItemStack(com.modamod.sublimadora.ModItems.REMERA))
            .displayName(Text.translatable("itemGroup.modamod"))
            .entries((contexto, entries) -> {
            // Máquinas primero.
            entries.add(com.modamod.modelado.ModeladoMod.MODELADO_BLOCK_ITEM);
            entries.add(com.modamod.telar.TelarMod.TELAR_ITEM);
            entries.add(com.modamod.estilista.EstilistaMod.ESTILISTA_ITEM);
            entries.add(com.modamod.tinturas.TinturasMod.TINTURAS_BLOCK_ITEM);
            entries.add(com.modamod.sublimadora.ModBlocks.SUBLIMADORA_ITEM);
            entries.add(com.modamod.guardarropas.GuardarropasMod.GUARDARROPAS_BLOCK_ITEM);
            entries.add(com.modamod.item.ModamodItems.MALETIN);
            entries.add(com.modamod.maniqui.ManiquiMod.MANIQUI_BLOCK_ITEM);
            // Mesa de estilado jubilada (2026-10-08): la Estilista automática la reemplaza; sigue registrada.
            entries.add(com.modamod.cinta.CintaMod.CINTA_ITEM);
            entries.add(com.modamod.cinta.CintaMod.EMPALME_ITEM);
            entries.add(com.modamod.cinta.CintaMod.TUBO_ITEM);
            entries.add(com.modamod.cinta.CintaMod.EMPALME_TUBO_ITEM);
            entries.add(com.modamod.cinta.CintaMod.EXTRACTOR_ITEM);
            entries.add(com.modamod.cinta.CintaMod.VALVULA_ITEM);
            // Máquinas creativas (2026-10-01): sin espera ni insumos, cargadas al colocarlas.
            entries.add(com.modamod.modelado.ModeladoMod.MODELADO_CREATIVA_ITEM);
            entries.add(com.modamod.telar.TelarMod.TELAR_CREATIVA_ITEM);
            entries.add(com.modamod.estilista.EstilistaMod.ESTILISTA_CREATIVA_ITEM);
            entries.add(com.modamod.tinturas.TinturasMod.TINTURAS_CREATIVA_ITEM);
            entries.add(com.modamod.sublimadora.ModBlocks.SUBLIMADORA_CREATIVA_ITEM);
            // Kits (2026-10-01, antes /modamod debug patrones|moldes|insumos).
            for (ItemStack kit : com.modamod.util.KitsCreativos.todos()) entries.add(kit);
            // Prendas.
            entries.add(com.modamod.sublimadora.ModItems.REMERA);
            // Apliques (2026-10-01): los 3 moldes y retazos de prueba con colores
            // de fábrica, hasta que Tintes los tiña (fase 4).
            entries.add(ModamodItems.MOLDE_APLIQUE_MONO);
            entries.add(ModamodItems.MOLDE_APLIQUE_MARIPOSA);
            entries.add(ModamodItems.MOLDE_APLIQUE_FLOR);
            entries.add(ModamodItems.MOLDE_APLIQUE_CANGURO);
            entries.add(ModamodItems.MOLDE_APLIQUE_CORBATA);
            entries.add(ModamodItems.MOLDE_APLIQUE_CORDONES);
            entries.add(ModamodItems.MOLDE_APLIQUE_BOTON);
            entries.add(ModamodItems.MOLDE_APLIQUE_BOTONERA);
            entries.add(ModamodItems.MOLDE_APLIQUE_BOLSILLO);
            entries.add(ModamodItems.MOLDE_APLIQUE_BOLSILLO_TAPA);
            entries.add(ModamodItems.MOLDE_APLIQUE_BOLSILLO_JEAN);
            entries.add(ModamodItems.MOLDE_APLIQUE_TACHA);
            entries.add(ModamodItems.MOLDE_APLIQUE_TACHAS);
            entries.add(ModamodItems.MOLDE_APLIQUE_PARCHE_CORAZON);
            entries.add(ModamodItems.MOLDE_APLIQUE_PARCHE_ESTRELLA);
            entries.add(ModamodItems.MOLDE_APLIQUE_PARCHE_RAYO);
            entries.add(ModamodItems.MOLDE_TEXTURA_FRUNCIDO);
            entries.add(ModamodItems.MOLDE_TEXTURA_ACOLCHADO);
            entries.add(ModamodItems.ESTROGENOS);
            entries.add(ModamodItems.TESTOSTERONA);
            entries.add(com.modamod.aplique.RetazoApliqueItem.conColores(
                    new ItemStack(ModamodItems.RETAZO_APLIQUE), 0xE878A8, 0xF8D860, 0x58A868));
            entries.add(com.modamod.aplique.RetazoApliqueItem.conColores(
                    new ItemStack(ModamodItems.RETAZO_APLIQUE), 0x78B8E8, 0xF2F2F6, 0x2E4A80));
            entries.add(com.modamod.aplique.RetazoApliqueItem.conColores(
                    new ItemStack(ModamodItems.RETAZO_APLIQUE), 0xC82828, 0x1E1E22, 0xE8C050));
            // Medias 3/4 y de red de armadura jubiladas (2026-10-23, "vamos con tu recomendacion"): quedan registradas para no
            // romper mundos; las Medias con su largo y la trama de red de la Modeladora hacen lo mismo y llevan patrones.
            entries.add(ModamodItems.SOCKS_SOLID);
            entries.add(ModamodItems.PANTALON);
            entries.add(ModamodItems.POLLERA);
            entries.add(ModamodItems.DELANTAL);
            entries.add(ModamodItems.TINTE_MEZCLA);
            // Los moldes por-eje ESPECÍFICOS de pantalón-largo y medias se
            // sacaron (a pedido, "sintetizar todos en esos dos moldes
            // aunque cada prenda tenga su propia medida") — reemplazados
            // por el molde de RANGO unificado de abajo, que sirve para las
            // 4 categorías de extremidad a la vez. Siguen registrados (el
            // Telar viejo los sigue usando), solo dejan de listarse acá.
            // El de TIRO también se saca ahora (a pedido, "fusionar largo
            // de remera y tiro de pantalón en cobertura de torso") —
            // reemplazado por el molde de TORSO unificado de abajo.
            entries.add(ModamodItems.CALIENTABRAZOS);
            entries.add(ModamodItems.CAPA);
            // El sombrero base ya aparece en el primer preset (ANCHA + RECTA).
            // Banda (2026-10-05): un cinto y un choker de fábrica, hasta que lleguen los moldes de la Modeladora.
            entries.add(ModamodItems.BANDA);
            entries.add(ModamodItems.BORCEGOS);   // 2026-10-08
            // Los calzados armados del zip (2026-10-14): capellada, suela, punta, cierre y detalle de cada uno.
            for (Object[] c : new Object[][] {
                    {com.modamod.item.BorcegoCana.BAJA, com.modamod.item.BorcegoSuela.CHATA, com.modamod.item.CalzadoPunta.CUERO, com.modamod.item.CalzadoCierre.CORDONES, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.ALTA, com.modamod.item.BorcegoSuela.CHATA, com.modamod.item.CalzadoPunta.CUERO, com.modamod.item.CalzadoCierre.CORDONES, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.ALTA, com.modamod.item.BorcegoSuela.PLATAFORMA, com.modamod.item.CalzadoPunta.CUERO, com.modamod.item.CalzadoCierre.CORDONES, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.ZAPATO, com.modamod.item.BorcegoSuela.TACO_CHICO, com.modamod.item.CalzadoPunta.ALARGADA, com.modamod.item.CalzadoCierre.CORDONES, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.MOCASIN, com.modamod.item.BorcegoSuela.FINA, com.modamod.item.CalzadoPunta.REDONDA, com.modamod.item.CalzadoCierre.SIN, com.modamod.item.CalzadoDetalle.COSTURA_EMPEINE},
                    {com.modamod.item.BorcegoCana.BAILARINA, com.modamod.item.BorcegoSuela.FINA, com.modamod.item.CalzadoPunta.REDONDA, com.modamod.item.CalzadoCierre.SIN, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.TACO, com.modamod.item.BorcegoSuela.CUNA, com.modamod.item.CalzadoPunta.FINA, com.modamod.item.CalzadoCierre.SIN, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.BAILARINA, com.modamod.item.BorcegoSuela.FINA, com.modamod.item.CalzadoPunta.REDONDA, com.modamod.item.CalzadoCierre.TIRA_HEBILLA, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.TENIS_BAJA, com.modamod.item.BorcegoSuela.GOMA_TENIS, com.modamod.item.CalzadoPunta.GOMA, com.modamod.item.CalzadoCierre.CORDONES, com.modamod.item.CalzadoDetalle.LENGUETA},
                    {com.modamod.item.BorcegoCana.TENIS_ALTA, com.modamod.item.BorcegoSuela.GOMA_TENIS, com.modamod.item.CalzadoPunta.GOMA, com.modamod.item.CalzadoCierre.CORDONES, com.modamod.item.CalzadoDetalle.PARCHE},
                    {com.modamod.item.BorcegoCana.TENIS_BAJA, com.modamod.item.BorcegoSuela.PLATAFORMA_TENIS, com.modamod.item.CalzadoPunta.ANCHA, com.modamod.item.CalzadoCierre.CORDONES, com.modamod.item.CalzadoDetalle.LENGUETA},
                    {com.modamod.item.BorcegoCana.NINGUNA, com.modamod.item.BorcegoSuela.CHANCLA, com.modamod.item.CalzadoPunta.DESCUBIERTA, com.modamod.item.CalzadoCierre.TIRA_V, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.NINGUNA, com.modamod.item.BorcegoSuela.SANDALIA, com.modamod.item.CalzadoPunta.DESCUBIERTA, com.modamod.item.CalzadoCierre.TOBILLERA, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.NINGUNA, com.modamod.item.BorcegoSuela.SANDALIA, com.modamod.item.CalzadoPunta.DESCUBIERTA, com.modamod.item.CalzadoCierre.GLADIADORA, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.PANTUFLA, com.modamod.item.BorcegoSuela.TELA, com.modamod.item.CalzadoPunta.REDONDA, com.modamod.item.CalzadoCierre.SIN, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.BOTA_ALTA, com.modamod.item.BorcegoSuela.CHATA, com.modamod.item.CalzadoPunta.REDONDA, com.modamod.item.CalzadoCierre.COSTURA_LATERAL, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.TENIS_BAJA, com.modamod.item.BorcegoSuela.GOMA_TENIS, com.modamod.item.CalzadoPunta.GOMA, com.modamod.item.CalzadoCierre.VELCRO, com.modamod.item.CalzadoDetalle.NINGUNO},
                    {com.modamod.item.BorcegoCana.MEDIA, com.modamod.item.BorcegoSuela.CUNA, com.modamod.item.CalzadoPunta.FINA, com.modamod.item.CalzadoCierre.SIN, com.modamod.item.CalzadoDetalle.NINGUNO}}) {
                net.minecraft.item.ItemStack calzado = new net.minecraft.item.ItemStack(ModamodItems.BORCEGOS);
                com.modamod.item.BorcegosItem.setCana(calzado, (com.modamod.item.BorcegoCana) c[0]);
                com.modamod.item.BorcegosItem.setSuela(calzado, (com.modamod.item.BorcegoSuela) c[1]);
                com.modamod.item.BorcegosItem.setPunta(calzado, (com.modamod.item.CalzadoPunta) c[2], com.modamod.region.Lado.AMBAS);
                com.modamod.item.BorcegosItem.setCierre(calzado, (com.modamod.item.CalzadoCierre) c[3], com.modamod.region.Lado.AMBAS);
                com.modamod.item.BorcegosItem.setDetalle(calzado, (com.modamod.item.CalzadoDetalle) c[4], com.modamod.region.Lado.AMBAS);
                entries.add(calzado);
            }
            entries.add(ModamodItems.MOLDE_CORREA_LISA);
            entries.add(ModamodItems.MOLDE_CORREA_CADENA);
            entries.add(ModamodItems.MOLDE_CORREA_CADENA_FINA);
            entries.add(ModamodItems.MOLDE_CORREA_OJALILLOS);
            entries.add(ModamodItems.MOLDE_CORREA_CORDON);
            net.minecraft.item.ItemStack choker = new net.minecraft.item.ItemStack(ModamodItems.BANDA);
            com.modamod.item.BandaItem.setZona(choker, com.modamod.item.BandaZona.CUELLO);
            com.modamod.item.BandaItem.setAncho(choker, com.modamod.item.BandaAncho.FINO);
            entries.add(choker);
            // Los 20 sombreros (2026-10-14, mallas de Claude Design): copa + ala de cada preset.
            for (var par : new Object[][] {
                    {com.modamod.item.SombreroAla.ANCHA, com.modamod.item.SombreroPunta.RECTA},             // bruja
                    {com.modamod.item.SombreroAla.CORTA, com.modamod.item.SombreroPunta.RECTA},             // bruja corta
                    {com.modamod.item.SombreroAla.ANCHA, com.modamod.item.SombreroPunta.DOBLADA},           // bruja doblada
                    {com.modamod.item.SombreroAla.VAQUERA, com.modamod.item.SombreroPunta.HENDIDURA},       // vaquero
                    {com.modamod.item.SombreroAla.CORTA, com.modamod.item.SombreroPunta.CUPULA},            // redondo (bombín)
                    {com.modamod.item.SombreroAla.ANGOSTA, com.modamod.item.SombreroPunta.CILINDRO_ALTO},   // de copa
                    {com.modamod.item.SombreroAla.VISERA_CURVA, com.modamod.item.SombreroPunta.CUPULA},     // gorra
                    {com.modamod.item.SombreroAla.VISERA_CHATA, com.modamod.item.SombreroPunta.CUPULA_CUADRADA}, // gorra cuadrada
                    {com.modamod.item.SombreroAla.SIN_ALA, com.modamod.item.SombreroPunta.PLATO},           // boina
                    {com.modamod.item.SombreroAla.SIN_ALA, com.modamod.item.SombreroPunta.GORRO_CAIDO},     // gorro de lana
                    {com.modamod.item.SombreroAla.VINCHA, com.modamod.item.SombreroPunta.RECTA},            // vincha
                    {com.modamod.item.SombreroAla.COFIA, com.modamod.item.SombreroPunta.RECTA},             // cofia
                    {com.modamod.item.SombreroAla.MEDIA, com.modamod.item.SombreroPunta.HENDIDURA},         // fedora / panamá
                    {com.modamod.item.SombreroAla.CAIDA, com.modamod.item.SombreroPunta.CUPULA},            // piluso
                    {com.modamod.item.SombreroAla.PLANA, com.modamod.item.SombreroPunta.CUPULA},            // paja
                    {com.modamod.item.SombreroAla.PLANA, com.modamod.item.SombreroPunta.CHARRO},            // charro
                    {com.modamod.item.SombreroAla.SIN_ALA, com.modamod.item.SombreroPunta.TOQUE},           // gorro de cocinero
                    {com.modamod.item.SombreroAla.VISERA_CORTA, com.modamod.item.SombreroPunta.PANADERO},   // gorra de panadero
                    {com.modamod.item.SombreroAla.TRICORNIO, com.modamod.item.SombreroPunta.CUPULA},        // tricornio
                    {com.modamod.item.SombreroAla.SIN_ALA, com.modamod.item.SombreroPunta.MARINERO}}) {     // gorro de marinero
                net.minecraft.item.ItemStack variante = new net.minecraft.item.ItemStack(ModamodItems.SOMBRERO_BRUJA);
                com.modamod.item.SombreroBrujaItem.setAla(variante, (com.modamod.item.SombreroAla) par[0]);
                com.modamod.item.SombreroBrujaItem.setPunta(variante, (com.modamod.item.SombreroPunta) par[1]);
                entries.add(variante);
            }
            // La planta cuadrada de las alas planas (2026-10-14).
            net.minecraft.item.ItemStack paja = new net.minecraft.item.ItemStack(ModamodItems.SOMBRERO_BRUJA);
            com.modamod.item.SombreroBrujaItem.setAla(paja, com.modamod.item.SombreroAla.PLANA);
            com.modamod.item.SombreroBrujaItem.setPunta(paja, com.modamod.item.SombreroPunta.CUPULA);
            com.modamod.item.SombreroBrujaItem.setPlanta(paja, com.modamod.item.SombreroPlanta.CUADRADA);
            entries.add(paja);
            // El mismo sombrero en cuboides (2026-10-14).
            net.minecraft.item.ItemStack cajas = new net.minecraft.item.ItemStack(ModamodItems.SOMBRERO_BRUJA);
            com.modamod.item.SombreroBrujaItem.setAla(cajas, com.modamod.item.SombreroAla.VISERA_CURVA);
            com.modamod.item.SombreroBrujaItem.setPunta(cajas, com.modamod.item.SombreroPunta.CUPULA);
            com.modamod.item.SombreroBrujaItem.setEstilo(cajas, com.modamod.item.SombreroEstilo.CAJAS);
            entries.add(cajas);
            entries.add(ModamodItems.ANTIFAZ);
            entries.add(ModamodItems.MEDIA_MASCARA);
            entries.add(ModamodItems.MASCARA_COMPLETA);
            entries.add(ModamodItems.BARBIJO);
            // El Traje de Maid de armadura, jubilado igual (2026-10-23): no pasaba por las máquinas.
            // El Buzo Oversize viejo (armadura) se reemplazó por el hoodie
            // (2026-09-30): sigue registrado para no romper mundos, fuera de la pestaña.
            entries.add(ModamodItems.PATTERN_RAYAS);
            entries.add(ModamodItems.PATTERN_CORAZONES);
            entries.add(ModamodItems.PATTERN_ESTRELLAS);
            entries.add(ModamodItems.PATTERN_LUNARES);
            entries.add(ModamodItems.PATTERN_VICHY);
            entries.add(ModamodItems.PATTERN_LEOPARDO);
            entries.add(ModamodItems.PATTERN_CEBRA);
            entries.add(ModamodItems.PATTERN_TIGRE);
            entries.add(ModamodItems.PATTERN_VACA);
            entries.add(ModamodItems.PATTERN_JIRAFA);
            entries.add(ModamodItems.PATTERN_SERPIENTE);
            entries.add(ModamodItems.PATTERN_DALMATA);
            entries.add(ModamodItems.PATTERN_TARTAN);
            entries.add(ModamodItems.PATTERN_LENADOR);
            entries.add(ModamodItems.PATTERN_PATA_DE_GALLO);
            entries.add(ModamodItems.PATTERN_ARGYLE);
            entries.add(ModamodItems.PATTERN_CAMUFLAJE);
            entries.add(ModamodItems.PATTERN_CAMUFLAJE_DIGITAL);
            entries.add(ModamodItems.PATTERN_DIPLOMATICAS);
            entries.add(ModamodItems.PATTERN_VENTANA);
            entries.add(ModamodItems.PATTERN_ZIGZAG);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_EN_BLANCO);
            entries.add(com.modamod.modelado.ModeladoMod.TRAMA_EN_BLANCO);
            entries.add(com.modamod.modelado.ModeladoMod.PATRON_EN_BLANCO);
            entries.add(com.modamod.modelado.ModeladoMod.MASCARA_EN_BLANCO);
            entries.add(com.modamod.modelado.ModeladoMod.PLANTILLA_EN_BLANCO);
            entries.add(com.modamod.modelado.ModeladoMod.DISENO_EN_BLANCO);
            // Moldes de la Sublimadora (manga y cuello por valor).
            entries.add(com.modamod.sublimadora.ModItems.MOLDE_CUELLO_REDONDO);
            entries.add(com.modamod.sublimadora.ModItems.MOLDE_CUELLO_V);
            for (var m : com.modamod.sublimadora.ModItems.MOLDES_MASCARA) entries.add(m);
            entries.add(com.modamod.sublimadora.ModItems.MOLDE_CUELLO_POLERA);
            entries.add(com.modamod.sublimadora.ModItems.MOLDE_CUELLO_CUADRADO);
            entries.add(com.modamod.sublimadora.ModItems.MOLDE_CUELLO_CORAZON);
            entries.add(com.modamod.sublimadora.ModItems.MOLDE_CUELLO_CAMISA);
            // Los 8 presets de combo directo (cobertura de torso/extremidad)
            // se sacaron de la pestaña — traen los anclajes horneados de
            // fábrica e ignoran Anclaje/Lado por completo, lo que generaba
            // confusión al mezclarse con el flujo real de moldes por-eje.
            // Siguen registrados (no se borra nada), solo dejan de listarse.
            // Molde de rango unificado (a pedido): mismos 5, sirven para
            // pantalón/medias/calientabrazos/manga de remera, cada una
            // traduciéndolo a su propia escala real al fijar.
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RANGO_CERO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RANGO_MINIMO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RANGO_CORTO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RANGO_MEDIO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RANGO_MEDIOLARGO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RANGO_LARGO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RANGO_MAXIMO);
            // Molde de calce (a pedido): transversal a las 4 categorías,
            // controla la dilatación de la geometría 3D, no recorta tela.
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CALCE_PEGADO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CALCE_AJUSTADO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CALCE_SUELTO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CALCE_OVERSIZE);
            // Molde de red (a pedido): transversal a las 4 categorías,
            // perfora la tela ya compuesta, no recorta filas.
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RED_FINA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RED_GRUESA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RED_HEXAGONAL);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RED_PERFORADA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RED_ENCAJE);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RED_RAYAS);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RED_ESCOCESA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_MATERIAL_DENIM);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_MATERIAL_CUERO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_MATERIAL_PANA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RED_ARNES_X);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RED_ARNES_TIRANTES);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RED_ARNES_BANDAS);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_POLLERA_TABLEADA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_POLLERA_TUBO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_POLLERA_GLOBO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_POLLERA_CIRCULAR);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_DELANTAL_TRAPECIO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_DELANTAL_PETO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_DELANTAL_CORAZON);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_DELANTAL_FESTON);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_DELANTAL_TIRANTES_RECTOS);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_DELANTAL_TIRANTES_CRUZADOS);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_VOLADO_RECTO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_VOLADO_CIRCULAR);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RUEDO_REDONDEADO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RUEDO_COLA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CAPA_CON_CAPUCHA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CAPUCHA_MALLA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CAPUCHA_CAJAS);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CAPA_CUELLO_ALTO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CAPA_BORDE_EMBOZO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_DELANTAL_COBERTURA_FRENTE);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_DELANTAL_COBERTURA_ANGOSTO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_CORTA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_VINCHA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_COFIA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_PUNTA_DOBLADA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_VAQUERA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_ANGOSTA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_VISERA_CURVA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_VISERA_CHATA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_SIN_ALA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_CAIDA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_PLANA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_COPA_CILINDRO_ALTO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_COPA_HENDIDURA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_COPA_CUPULA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_COPA_CUPULA_CUADRADA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_COPA_PLATO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_COPA_GORRO_CAIDO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_MEDIA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_TRICORNIO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_VISERA_CORTA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_COPA_CHARRO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_COPA_TOQUE);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_COPA_PANADERO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_COPA_MARINERO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_PLANTA_CUADRADA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ESTILO_CAJAS);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_ANCHA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_ALA_LARGA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_PUNTA_RECTA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_PLANTA_REDONDA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_GROSOR_GRUESA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_SOMBRERO_BANDA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BANDA_ZONA_CINTURA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BANDA_ZONA_CUELLO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RUEDO_AJUSTADO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RUEDO_CAMPANA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RUEDO_ONDULADO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RUEDO_FESTONEADO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RUEDO_PICO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_RUEDO_FRUNCIDO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CHAQUETA_FRENTE_ABIERTA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CHAQUETA_SOLAPA_PICO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CHAQUETA_SOLAPA_REDONDA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CHAQUETA_SOLAPA_CHAL);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CHAQUETA_FRENTE_CRUZADA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_CHAQUETA_FRENTE_ABIERTA_PECHO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BANDA_ANCHO_FINO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BANDA_ANCHO_ANCHO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BANDA_HERRAJE_PLACA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BANDA_HERRAJE_ARO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BANDA_HERRAJE_CAMPANA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BANDA_HERRAJE_HUESO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BANDA_HERRAJE_CORAZON);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BANDA_HERRAJE_MEDALLA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_BAJA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_ALTA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_SUELA_PLATAFORMA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_BOTAMANGA_AFUERA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_BOTA_ALTA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_ZAPATO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_MOCASIN);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_BAILARINA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_TACO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_TENIS_BAJA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_TENIS_ALTA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_PANTUFLA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CANA_NINGUNA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_SUELA_FINA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_SUELA_TACO_CHICO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_SUELA_CUNA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_SUELA_GOMA_TENIS);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_SUELA_PLATAFORMA_TENIS);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_SUELA_CHANCLA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_SUELA_SANDALIA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_SUELA_TELA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_PUNTA_REDONDA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_PUNTA_ALARGADA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_PUNTA_CUADRADA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_PUNTA_FINA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_PUNTA_ANCHA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_PUNTA_GOMA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_PUNTA_DESCUBIERTA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CIERRE_VELCRO);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CIERRE_TIRA_HEBILLA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CIERRE_TIRA_V);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CIERRE_TOBILLERA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CIERRE_GLADIADORA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CIERRE_COSTURA_LATERAL);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_CIERRE_SIN);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_DETALLE_LENGUETA);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_DETALLE_PARCHE);
            entries.add(com.modamod.modelado.ModeladoMod.MOLDE_BORCEGO_DETALLE_COSTURA_EMPEINE);
            })
            .build();

    private static void registrarPestanaCreativa() {
        Registry.register(Registries.ITEM_GROUP, Identifier.of(MOD_ID, "modamod"), PESTANA);
    }
}
