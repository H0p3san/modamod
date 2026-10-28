package com.modamod.modelado;

import com.modamod.Modamod;
import com.modamod.item.Botamanga;
import com.modamod.item.Calce;
import com.modamod.item.ModamodComponents;
import com.modamod.item.PantalonTiro;
import com.modamod.item.PatronRed;
import com.modamod.sublimadora.Variante;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** Registro de la Mesa de Modelado: bloque, item, block entity type, molde de corte. */
public final class ModeladoMod {

    public static final ModeladoBlock MODELADO_BLOCK = new ModeladoBlock(
            AbstractBlock.Settings.create()
                    .strength(2.5f, 4.0f)
                    .sounds(BlockSoundGroup.WOOD)
                    // El bloque es INVISIBLE (GeckoLib lo dibuja desde el
                    // block entity) — sin esto vanilla lo trata como cubo
                    // opaco sólido y cullea la cara del bloque de abajo,
                    // que se ve como si el modelo se "comiera" esa cara.
                    .nonOpaque()
                    .luminance(com.modamod.util.LuzMaquina::luminancia));

    public static final ModeladoBlockItem MODELADO_BLOCK_ITEM = new ModeladoBlockItem(MODELADO_BLOCK, new Item.Settings());

    /** Versión creativa (2026-10-01): sin espera, viene con todos los moldes — ver {@code util.MaquinaCreativa}. */
    public static final ModeladoBlock MODELADO_CREATIVA = com.modamod.util.MaquinaCreativa.creativa(new ModeladoBlock(
            AbstractBlock.Settings.create().strength(2.5f, 4.0f).sounds(BlockSoundGroup.WOOD).nonOpaque()
                    .luminance(com.modamod.util.LuzMaquina::luminancia)));
    public static final ModeladoBlockItem MODELADO_CREATIVA_ITEM = new ModeladoBlockItem(MODELADO_CREATIVA, new Item.Settings());

    public static final BlockEntityType<ModeladoBlockEntity> MODELADO_BLOCK_ENTITY =
            BlockEntityType.Builder.create(ModeladoBlockEntity::new, MODELADO_BLOCK, MODELADO_CREATIVA).build();


    // ── molde de rango unificado (a pedido) ─────────────────────────────
    // Un solo set de 5, para las 4 categorías de extremidad — ver
    // MoldeRangoItem y ModeladoBlockEntity#fijar (traduce el rango a la
    // escala real de cada prenda). Anclaje/Lado aplican normal.

    public static final MoldeRangoItem MOLDE_RANGO_CERO = new MoldeRangoItem(new Item.Settings().maxCount(1), MoldeRangoItem.Rango.CERO);
    public static final MoldeRangoItem MOLDE_RANGO_MINIMO = new MoldeRangoItem(new Item.Settings().maxCount(1), MoldeRangoItem.Rango.MINIMO);
    public static final MoldeRangoItem MOLDE_RANGO_CORTO = new MoldeRangoItem(new Item.Settings().maxCount(1), MoldeRangoItem.Rango.CORTO);
    public static final MoldeRangoItem MOLDE_RANGO_MEDIO = new MoldeRangoItem(new Item.Settings().maxCount(1), MoldeRangoItem.Rango.MEDIO);
    public static final MoldeRangoItem MOLDE_RANGO_MEDIOLARGO = new MoldeRangoItem(new Item.Settings().maxCount(1), MoldeRangoItem.Rango.MEDIOLARGO);
    public static final MoldeRangoItem MOLDE_RANGO_LARGO = new MoldeRangoItem(new Item.Settings().maxCount(1), MoldeRangoItem.Rango.LARGO);
    public static final MoldeRangoItem MOLDE_RANGO_MAXIMO = new MoldeRangoItem(new Item.Settings().maxCount(1), MoldeRangoItem.Rango.MAXIMO);

    // ── molde de calce (a pedido 2026-09-15) ────────────────────────────
    // Transversal a las 4 categorías (remera/pantalón/medias/
    // calientabrazos) — no recorta filas, cambia la dilatación de la
    // geometría 3D (ver Calce, CuerpoGeometria).

    public static final MoldeCalceItem MOLDE_CALCE_PEGADO = new MoldeCalceItem(new Item.Settings().maxCount(1), Calce.PEGADO);
    public static final MoldeCalceItem MOLDE_CALCE_AJUSTADO = new MoldeCalceItem(new Item.Settings().maxCount(1), Calce.AJUSTADO);
    public static final MoldeCalceItem MOLDE_CALCE_SUELTO = new MoldeCalceItem(new Item.Settings().maxCount(1), Calce.SUELTO);
    public static final MoldeCalceItem MOLDE_CALCE_OVERSIZE = new MoldeCalceItem(new Item.Settings().maxCount(1), Calce.OVERSIZE);

    // ── molde de red (a pedido 2026-09-20) ──────────────────────────────
    // Transversal a las 4 categorías, mismo criterio que el molde de calce
    // — no recorta filas ni cambia geometría, perfora la textura ya
    // compuesta (ver PatronRed, ClothingTextureCache#perforarRed).

    public static final MoldeRedItem MOLDE_RED_FINA = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.FINA);
    public static final MoldeRedItem MOLDE_RED_GRUESA = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.GRUESA);
    /** Revierte la red: deja la textura lisa (2026-09-26, "un molde de textura lisa para revertir el media red"). */
    /** Panal de abejas (2026-09-28, "media red hexagonal"). */
    public static final MoldeRedItem MOLDE_RED_HEXAGONAL = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.HEXAGONAL);
    /** Agujeritos redondos en tresbolillo, tipo broderie (2026-09-28). */
    public static final MoldeRedItem MOLDE_RED_PERFORADA = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.PERFORADA);
    /** Encaje de rombos con punto central (2026-09-28). */
    public static final MoldeRedItem MOLDE_RED_ENCAJE = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.ENCAJE);
    /** Rayas caladas horizontales (2026-09-28). */
    public static final MoldeRedItem MOLDE_RED_RAYAS = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.RAYAS);
    /** Cuadrícula recta estilo escocés (2026-09-28). */
    public static final MoldeRedItem MOLDE_MATERIAL_DENIM = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.DENIM);
    public static final MoldeRedItem MOLDE_MATERIAL_CUERO = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.CUERO);
    public static final MoldeRedItem MOLDE_MATERIAL_PANA = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.PANA);
    public static final MoldeRedItem MOLDE_RED_ESCOCESA = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.ESCOCESA);
    /** Arnés cruzado en X (2026-09-28). */
    public static final MoldeRedItem MOLDE_RED_ARNES_X = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.ARNES_X);
    /** Arnés de tirantes (2026-09-28). */
    public static final MoldeRedItem MOLDE_RED_ARNES_TIRANTES = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.ARNES_TIRANTES);
    /** Arnés de bandas (2026-09-28). */
    public static final MoldeRedItem MOLDE_RED_ARNES_BANDAS = new MoldeRedItem(new Item.Settings().maxCount(1), PatronRed.ARNES_BANDAS);

    // ── molde de pollera (a pedido 2026-09-29) ─────────────────────────
    // Forma de la pollera: campana o tableada. Exclusivo de la pollera.

    public static final MoldePolleraItem MOLDE_POLLERA_TABLEADA = new MoldePolleraItem(new Item.Settings().maxCount(1),
            com.modamod.item.PolleraForma.TABLEADA);

    // ── moldes de capa (a pedido 2026-09-29, "podemos agregar todo eso como patrones de corte?") ──
    // Ruedo, capucha y cuello alto de la capa, cada uno para su pin. Exclusivos de la capa.
    public static final MoldeCapaItem MOLDE_CAPA_CON_CAPUCHA = new MoldeCapaItem(new Item.Settings().maxCount(1),
            MoldeCapaItem.Tipo.CON_CAPUCHA);
    /** Capucha de malla o de cajas (2026-10-23), en el top o en la capa. */
    public static final MoldeCapaItem MOLDE_CAPUCHA_MALLA = new MoldeCapaItem(new Item.Settings().maxCount(1), MoldeCapaItem.Tipo.CAPUCHA_MALLA);
    public static final MoldeCapaItem MOLDE_CAPUCHA_CAJAS = new MoldeCapaItem(new Item.Settings().maxCount(1), MoldeCapaItem.Tipo.CAPUCHA_CAJAS);
    public static final MoldeCapaItem MOLDE_CAPA_CUELLO_ALTO = new MoldeCapaItem(new Item.Settings().maxCount(1),
            MoldeCapaItem.Tipo.CUELLO_ALTO);

    // ── moldes del sombrero de bruja (2026-10-05, "segunda tanda del sombrero") ──
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_CORTA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_CORTA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_VINCHA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_VINCHA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_COFIA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_COFIA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_PUNTA_DOBLADA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.PUNTA_DOBLADA);

    // Sombreros nuevos (2026-10-14, "sombrero vaquero, redondo, de copa, gorra, boina"): copas y alas.
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_VAQUERA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_VAQUERA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_ANGOSTA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_ANGOSTA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_VISERA_CURVA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_VISERA_CURVA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_VISERA_CHATA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_VISERA_CHATA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_SIN_ALA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_SIN_ALA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_CAIDA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_CAIDA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_PLANA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_PLANA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_COPA_CILINDRO_ALTO = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.COPA_CILINDRO_ALTO);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_COPA_HENDIDURA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.COPA_HENDIDURA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_COPA_CUPULA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.COPA_CUPULA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_COPA_CUPULA_CUADRADA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.COPA_CUPULA_CUADRADA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_COPA_PLATO = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.COPA_PLATO);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_COPA_GORRO_CAIDO = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.COPA_GORRO_CAIDO);

    // Tanda de las mallas (2026-10-14): tres alas, cuatro copas y la planta cuadrada.
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_MEDIA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_MEDIA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_TRICORNIO = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_TRICORNIO);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_VISERA_CORTA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_VISERA_CORTA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_COPA_CHARRO = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.COPA_CHARRO);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_COPA_TOQUE = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.COPA_TOQUE);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_COPA_PANADERO = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.COPA_PANADERO);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_COPA_MARINERO = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.COPA_MARINERO);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_PLANTA_CUADRADA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.PLANTA_CUADRADA);

    public static final MoldeSombreroItem MOLDE_SOMBRERO_ESTILO_CAJAS = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ESTILO_CAJAS);

    // Segundo lote (2026-10-14): ala ancha y larga, punta recta, planta redonda, ala gruesa y banda.
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_ANCHA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_ANCHA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_ALA_LARGA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.ALA_LARGA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_PUNTA_RECTA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.PUNTA_RECTA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_PLANTA_REDONDA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.PLANTA_REDONDA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_GROSOR_GRUESA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.GROSOR_GRUESA);
    public static final MoldeSombreroItem MOLDE_SOMBRERO_BANDA = new MoldeSombreroItem(new Item.Settings().maxCount(1),
            MoldeSombreroItem.Tipo.BANDA_PONER);

    // ── moldes de volado de la pollera (2026-10-05) ──
    public static final MoldeVoladoItem MOLDE_VOLADO_RECTO = new MoldeVoladoItem(new Item.Settings().maxCount(1),
            com.modamod.item.PolleraVolado.RECTO);
    public static final MoldeVoladoItem MOLDE_VOLADO_CIRCULAR = new MoldeVoladoItem(new Item.Settings().maxCount(1),
            com.modamod.item.PolleraVolado.CIRCULAR);
    // Moldes de las formas nuevas de la pollera (2026-10-05).
    public static final MoldePolleraItem MOLDE_POLLERA_TUBO = new MoldePolleraItem(new Item.Settings().maxCount(1),
            com.modamod.item.PolleraForma.TUBO);
    public static final MoldePolleraItem MOLDE_POLLERA_GLOBO = new MoldePolleraItem(new Item.Settings().maxCount(1),
            com.modamod.item.PolleraForma.GLOBO);
    public static final MoldePolleraItem MOLDE_POLLERA_CIRCULAR = new MoldePolleraItem(new Item.Settings().maxCount(1),
            com.modamod.item.PolleraForma.CIRCULAR);

    public static final MoldeFormaDelantalItem MOLDE_DELANTAL_TRAPECIO = new MoldeFormaDelantalItem(new Item.Settings().maxCount(1),
            com.modamod.item.DelantalForma.TRAPECIO);
    public static final MoldeFormaDelantalItem MOLDE_DELANTAL_PETO = new MoldeFormaDelantalItem(new Item.Settings().maxCount(1),
            com.modamod.item.DelantalForma.PETO);
    public static final MoldeFormaDelantalItem MOLDE_DELANTAL_CORAZON = new MoldeFormaDelantalItem(new Item.Settings().maxCount(1),
            com.modamod.item.DelantalForma.CORAZON);
    public static final MoldeFormaDelantalItem MOLDE_DELANTAL_FESTON = new MoldeFormaDelantalItem(new Item.Settings().maxCount(1),
            com.modamod.item.DelantalForma.FESTON);

    public static final MoldeTirantesDelantalItem MOLDE_DELANTAL_TIRANTES_RECTOS = new MoldeTirantesDelantalItem(new Item.Settings().maxCount(1),
            com.modamod.item.DelantalTirantes.RECTOS);
    public static final MoldeTirantesDelantalItem MOLDE_DELANTAL_TIRANTES_CRUZADOS = new MoldeTirantesDelantalItem(new Item.Settings().maxCount(1),
            com.modamod.item.DelantalTirantes.CRUZADOS);

    public static final MoldeCoberturaDelantalItem MOLDE_DELANTAL_COBERTURA_FRENTE = new MoldeCoberturaDelantalItem(new Item.Settings().maxCount(1),
            com.modamod.item.DelantalCobertura.FRENTE);
    public static final MoldeCoberturaDelantalItem MOLDE_DELANTAL_COBERTURA_ANGOSTO = new MoldeCoberturaDelantalItem(new Item.Settings().maxCount(1),
            com.modamod.item.DelantalCobertura.ANGOSTO);
    public static final MoldeCapaItem MOLDE_CAPA_BORDE_EMBOZO = new MoldeCapaItem(new Item.Settings().maxCount(1),
            MoldeCapaItem.Tipo.BORDE_EMBOZO);

    public static final MoldeRuedoItem MOLDE_RUEDO_AJUSTADO = new MoldeRuedoItem(new Item.Settings().maxCount(1),
            com.modamod.item.Ruedo.AJUSTADO);
    public static final MoldeRuedoItem MOLDE_RUEDO_CAMPANA = new MoldeRuedoItem(new Item.Settings().maxCount(1),
            com.modamod.item.Ruedo.CAMPANA);
    public static final MoldeRuedoItem MOLDE_RUEDO_ONDULADO = new MoldeRuedoItem(new Item.Settings().maxCount(1),
            com.modamod.item.Ruedo.ONDULADO);
    public static final MoldeRuedoItem MOLDE_RUEDO_FESTONEADO = new MoldeRuedoItem(new Item.Settings().maxCount(1),
            com.modamod.item.Ruedo.FESTONEADO);
    public static final MoldeRuedoItem MOLDE_RUEDO_PICO = new MoldeRuedoItem(new Item.Settings().maxCount(1),
            com.modamod.item.Ruedo.PICO);
    /** Tela fruncida en medias y calientabrazos (2026-10-17, "quiero que se bunchee asi la tela de las medias y los armwarmers"). */
    public static final MoldeRuedoItem MOLDE_RUEDO_FRUNCIDO = new MoldeRuedoItem(new Item.Settings().maxCount(1),
            com.modamod.item.Ruedo.FRUNCIDO);

    // ── moldes de la banda (2026-10-05, "correas y cintos") ──
    public static final MoldeBandaItem MOLDE_BANDA_ZONA_CINTURA = new MoldeBandaItem(new Item.Settings().maxCount(1),
            MoldeBandaItem.Tipo.ZONA_CINTURA);
    public static final MoldeBandaItem MOLDE_BANDA_ZONA_CUELLO = new MoldeBandaItem(new Item.Settings().maxCount(1),
            MoldeBandaItem.Tipo.ZONA_CUELLO);
    public static final MoldeBandaItem MOLDE_BANDA_ANCHO_FINO = new MoldeBandaItem(new Item.Settings().maxCount(1),
            MoldeBandaItem.Tipo.ANCHO_FINO);
    public static final MoldeBandaItem MOLDE_BANDA_ANCHO_ANCHO = new MoldeBandaItem(new Item.Settings().maxCount(1),
            MoldeBandaItem.Tipo.ANCHO_ANCHO);
    public static final MoldeBandaItem MOLDE_BANDA_HERRAJE_PLACA = new MoldeBandaItem(new Item.Settings().maxCount(1),
            MoldeBandaItem.Tipo.HERRAJE_PLACA);
    public static final MoldeBandaItem MOLDE_BANDA_HERRAJE_ARO = new MoldeBandaItem(new Item.Settings().maxCount(1),
            MoldeBandaItem.Tipo.HERRAJE_ARO);
    public static final MoldeBandaItem MOLDE_BANDA_HERRAJE_CAMPANA = new MoldeBandaItem(new Item.Settings().maxCount(1),
            MoldeBandaItem.Tipo.HERRAJE_CAMPANA);
    public static final MoldeBandaItem MOLDE_BANDA_HERRAJE_HUESO = new MoldeBandaItem(new Item.Settings().maxCount(1),
            MoldeBandaItem.Tipo.HERRAJE_HUESO);
    public static final MoldeBandaItem MOLDE_BANDA_HERRAJE_CORAZON = new MoldeBandaItem(new Item.Settings().maxCount(1),
            MoldeBandaItem.Tipo.HERRAJE_CORAZON);
    public static final MoldeBandaItem MOLDE_BANDA_HERRAJE_MEDALLA = new MoldeBandaItem(new Item.Settings().maxCount(1),
            MoldeBandaItem.Tipo.HERRAJE_MEDALLA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_BAJA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_BAJA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_ALTA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_ALTA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_SUELA_PLATAFORMA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.SUELA_PLATAFORMA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_BOTAMANGA_AFUERA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.BOTAMANGA_AFUERA);
    // Calzado del zip de Claude Design (2026-10-14): capelladas, suelas, puntas, cierres y detalles.
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_BOTA_ALTA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_BOTA_ALTA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_ZAPATO = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_ZAPATO);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_MOCASIN = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_MOCASIN);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_BAILARINA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_BAILARINA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_TACO = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_TACO);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_TENIS_BAJA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_TENIS_BAJA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_TENIS_ALTA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_TENIS_ALTA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_PANTUFLA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_PANTUFLA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CANA_NINGUNA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CANA_NINGUNA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_SUELA_FINA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.SUELA_FINA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_SUELA_TACO_CHICO = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.SUELA_TACO_CHICO);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_SUELA_CUNA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.SUELA_CUNA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_SUELA_GOMA_TENIS = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.SUELA_GOMA_TENIS);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_SUELA_PLATAFORMA_TENIS = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.SUELA_PLATAFORMA_TENIS);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_SUELA_CHANCLA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.SUELA_CHANCLA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_SUELA_SANDALIA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.SUELA_SANDALIA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_SUELA_TELA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.SUELA_TELA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_PUNTA_REDONDA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.PUNTA_REDONDA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_PUNTA_ALARGADA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.PUNTA_ALARGADA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_PUNTA_CUADRADA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.PUNTA_CUADRADA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_PUNTA_FINA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.PUNTA_FINA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_PUNTA_ANCHA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.PUNTA_ANCHA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_PUNTA_GOMA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.PUNTA_GOMA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_PUNTA_DESCUBIERTA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.PUNTA_DESCUBIERTA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CIERRE_VELCRO = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CIERRE_VELCRO);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CIERRE_TIRA_HEBILLA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CIERRE_TIRA_HEBILLA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CIERRE_TIRA_V = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CIERRE_TIRA_V);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CIERRE_TOBILLERA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CIERRE_TOBILLERA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CIERRE_GLADIADORA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CIERRE_GLADIADORA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CIERRE_COSTURA_LATERAL = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CIERRE_COSTURA_LATERAL);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_CIERRE_SIN = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.CIERRE_SIN);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_DETALLE_LENGUETA = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.DETALLE_LENGUETA);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_DETALLE_PARCHE = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.DETALLE_PARCHE);
    public static final MoldeBorcegoItem MOLDE_BORCEGO_DETALLE_COSTURA_EMPEINE = new MoldeBorcegoItem(new Item.Settings().maxCount(1),
            MoldeBorcegoItem.Tipo.DETALLE_COSTURA_EMPEINE);


    // ── moldes de la chaqueta (2026-10-07) ──
    public static final MoldeChaquetaItem MOLDE_CHAQUETA_FRENTE_ABIERTA = new MoldeChaquetaItem(new Item.Settings().maxCount(1),
            MoldeChaquetaItem.Tipo.FRENTE_ABIERTA);

    public static final MoldeChaquetaItem MOLDE_CHAQUETA_SOLAPA_PICO = new MoldeChaquetaItem(new Item.Settings().maxCount(1),
            MoldeChaquetaItem.Tipo.SOLAPA_PICO);
    public static final MoldeChaquetaItem MOLDE_CHAQUETA_SOLAPA_REDONDA = new MoldeChaquetaItem(new Item.Settings().maxCount(1),
            MoldeChaquetaItem.Tipo.SOLAPA_REDONDA);
    public static final MoldeChaquetaItem MOLDE_CHAQUETA_FRENTE_CRUZADA = new MoldeChaquetaItem(new Item.Settings().maxCount(1),
            MoldeChaquetaItem.Tipo.FRENTE_CRUZADA);
    public static final MoldeChaquetaItem MOLDE_CHAQUETA_FRENTE_ABIERTA_PECHO = new MoldeChaquetaItem(new Item.Settings().maxCount(1),
            MoldeChaquetaItem.Tipo.FRENTE_ABIERTA_PECHO);
    public static final MoldeChaquetaItem MOLDE_CHAQUETA_SOLAPA_CHAL = new MoldeChaquetaItem(new Item.Settings().maxCount(1),
            MoldeChaquetaItem.Tipo.SOLAPA_CHAL);

    public static final MoldeEnBlancoItem MOLDE_EN_BLANCO = new MoldeEnBlancoItem(new Item.Settings().maxCount(64), MoldeEnBlancoItem.Tipo.MOLDE);
    public static final MoldeEnBlancoItem TRAMA_EN_BLANCO = new MoldeEnBlancoItem(new Item.Settings().maxCount(64), MoldeEnBlancoItem.Tipo.TRAMA);
    public static final MoldeEnBlancoItem PATRON_EN_BLANCO = new MoldeEnBlancoItem(new Item.Settings().maxCount(64), MoldeEnBlancoItem.Tipo.PATRON);
    public static final MoldeEnBlancoItem MASCARA_EN_BLANCO = new MoldeEnBlancoItem(new Item.Settings().maxCount(64), MoldeEnBlancoItem.Tipo.MASCARA);
    /** Diseño en blanco (8 papeles y un carbón) y Diseño de corte (2026-10-12, bolsa de diseños de la Modeladora). */
    public static final Item DISENO_EN_BLANCO = new Item(new Item.Settings().maxCount(64));
    public static final DisenoCorteItem DISENO_CORTE = new DisenoCorteItem(new Item.Settings().maxCount(1));
    public static final MoldeEnBlancoItem PLANTILLA_EN_BLANCO = new MoldeEnBlancoItem(new Item.Settings().maxCount(64), MoldeEnBlancoItem.Tipo.PLANTILLA);

    public static final MoldeRuedoItem MOLDE_RUEDO_REDONDEADO = new MoldeRuedoItem(new Item.Settings().maxCount(1),
            com.modamod.item.Ruedo.REDONDEADO);
    public static final MoldeRuedoItem MOLDE_RUEDO_COLA = new MoldeRuedoItem(new Item.Settings().maxCount(1),
            com.modamod.item.Ruedo.COLA);

    public static void register() {
        Identifier bloqueId = Identifier.of(Modamod.MOD_ID, "modelado");
        Registry.register(Registries.BLOCK, bloqueId, MODELADO_BLOCK);
        Registry.register(Registries.ITEM, bloqueId, MODELADO_BLOCK_ITEM);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, bloqueId, MODELADO_BLOCK_ENTITY);
        Identifier creativaId = Identifier.of(Modamod.MOD_ID, "modelado_creativa");
        Registry.register(Registries.BLOCK, creativaId, MODELADO_CREATIVA);
        Registry.register(Registries.ITEM, creativaId, MODELADO_CREATIVA_ITEM);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_en_blanco"), MOLDE_EN_BLANCO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "trama_en_blanco"), TRAMA_EN_BLANCO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "patron_en_blanco"), PATRON_EN_BLANCO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "mascara_en_blanco"), MASCARA_EN_BLANCO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "plantilla_en_blanco"), PLANTILLA_EN_BLANCO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "diseno_en_blanco"), DISENO_EN_BLANCO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "diseno_corte"), DISENO_CORTE);


        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_rango_cero"), MOLDE_RANGO_CERO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_rango_minimo"), MOLDE_RANGO_MINIMO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_rango_corto"), MOLDE_RANGO_CORTO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_rango_medio"), MOLDE_RANGO_MEDIO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_rango_mediolargo"), MOLDE_RANGO_MEDIOLARGO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_rango_largo"), MOLDE_RANGO_LARGO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_rango_maximo"), MOLDE_RANGO_MAXIMO);


        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_calce_pegado"), MOLDE_CALCE_PEGADO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_calce_ajustado"), MOLDE_CALCE_AJUSTADO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_calce_suelto"), MOLDE_CALCE_SUELTO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_calce_oversize"), MOLDE_CALCE_OVERSIZE);

        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_red_fina"), MOLDE_RED_FINA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_red_gruesa"), MOLDE_RED_GRUESA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_red_hexagonal"), MOLDE_RED_HEXAGONAL);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_red_perforada"), MOLDE_RED_PERFORADA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_red_encaje"), MOLDE_RED_ENCAJE);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_red_rayas"), MOLDE_RED_RAYAS);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_red_escocesa"), MOLDE_RED_ESCOCESA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_material_denim"), MOLDE_MATERIAL_DENIM);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_material_cuero"), MOLDE_MATERIAL_CUERO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_material_pana"), MOLDE_MATERIAL_PANA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_red_arnes_x"), MOLDE_RED_ARNES_X);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_red_arnes_tirantes"), MOLDE_RED_ARNES_TIRANTES);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_red_arnes_bandas"), MOLDE_RED_ARNES_BANDAS);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_pollera_tableada"), MOLDE_POLLERA_TABLEADA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_pollera_tubo"), MOLDE_POLLERA_TUBO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_pollera_globo"), MOLDE_POLLERA_GLOBO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_pollera_circular"), MOLDE_POLLERA_CIRCULAR);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_delantal_trapecio"), MOLDE_DELANTAL_TRAPECIO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_delantal_peto"), MOLDE_DELANTAL_PETO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_delantal_corazon"), MOLDE_DELANTAL_CORAZON);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_delantal_feston"), MOLDE_DELANTAL_FESTON);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_delantal_tirantes_rectos"), MOLDE_DELANTAL_TIRANTES_RECTOS);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_delantal_tirantes_cruzados"), MOLDE_DELANTAL_TIRANTES_CRUZADOS);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_volado_recto"), MOLDE_VOLADO_RECTO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_volado_circular"), MOLDE_VOLADO_CIRCULAR);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_ruedo_redondeado"), MOLDE_RUEDO_REDONDEADO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_ruedo_cola"), MOLDE_RUEDO_COLA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_capa_con_capucha"), MOLDE_CAPA_CON_CAPUCHA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_capucha_malla"), MOLDE_CAPUCHA_MALLA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_capucha_cajas"), MOLDE_CAPUCHA_CAJAS);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_capa_cuello_alto"), MOLDE_CAPA_CUELLO_ALTO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_delantal_cobertura_frente"), MOLDE_DELANTAL_COBERTURA_FRENTE);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_delantal_cobertura_angosto"), MOLDE_DELANTAL_COBERTURA_ANGOSTO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_capa_borde_embozo"), MOLDE_CAPA_BORDE_EMBOZO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_corta"), MOLDE_SOMBRERO_ALA_CORTA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_vincha"), MOLDE_SOMBRERO_ALA_VINCHA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_cofia"), MOLDE_SOMBRERO_ALA_COFIA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_punta_doblada"), MOLDE_SOMBRERO_PUNTA_DOBLADA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_vaquera"), MOLDE_SOMBRERO_ALA_VAQUERA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_angosta"), MOLDE_SOMBRERO_ALA_ANGOSTA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_visera_curva"), MOLDE_SOMBRERO_ALA_VISERA_CURVA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_visera_chata"), MOLDE_SOMBRERO_ALA_VISERA_CHATA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_sin_ala"), MOLDE_SOMBRERO_ALA_SIN_ALA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_caida"), MOLDE_SOMBRERO_ALA_CAIDA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_plana"), MOLDE_SOMBRERO_ALA_PLANA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_copa_cilindro_alto"), MOLDE_SOMBRERO_COPA_CILINDRO_ALTO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_copa_hendidura"), MOLDE_SOMBRERO_COPA_HENDIDURA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_copa_cupula"), MOLDE_SOMBRERO_COPA_CUPULA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_copa_cupula_cuadrada"), MOLDE_SOMBRERO_COPA_CUPULA_CUADRADA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_copa_plato"), MOLDE_SOMBRERO_COPA_PLATO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_copa_gorro_caido"), MOLDE_SOMBRERO_COPA_GORRO_CAIDO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_media"), MOLDE_SOMBRERO_ALA_MEDIA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_tricornio"), MOLDE_SOMBRERO_ALA_TRICORNIO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_visera_corta"), MOLDE_SOMBRERO_ALA_VISERA_CORTA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_copa_charro"), MOLDE_SOMBRERO_COPA_CHARRO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_copa_toque"), MOLDE_SOMBRERO_COPA_TOQUE);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_copa_panadero"), MOLDE_SOMBRERO_COPA_PANADERO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_copa_marinero"), MOLDE_SOMBRERO_COPA_MARINERO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_planta_cuadrada"), MOLDE_SOMBRERO_PLANTA_CUADRADA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_estilo_cajas"), MOLDE_SOMBRERO_ESTILO_CAJAS);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_ancha"), MOLDE_SOMBRERO_ALA_ANCHA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_ala_larga"), MOLDE_SOMBRERO_ALA_LARGA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_punta_recta"), MOLDE_SOMBRERO_PUNTA_RECTA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_planta_redonda"), MOLDE_SOMBRERO_PLANTA_REDONDA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_grosor_gruesa"), MOLDE_SOMBRERO_GROSOR_GRUESA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_sombrero_banda"), MOLDE_SOMBRERO_BANDA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_banda_zona_cintura"), MOLDE_BANDA_ZONA_CINTURA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_banda_zona_cuello"), MOLDE_BANDA_ZONA_CUELLO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_banda_ancho_fino"), MOLDE_BANDA_ANCHO_FINO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_banda_ancho_ancho"), MOLDE_BANDA_ANCHO_ANCHO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_banda_herraje_placa"), MOLDE_BANDA_HERRAJE_PLACA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_banda_herraje_aro"), MOLDE_BANDA_HERRAJE_ARO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_banda_herraje_campana"), MOLDE_BANDA_HERRAJE_CAMPANA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_banda_herraje_hueso"), MOLDE_BANDA_HERRAJE_HUESO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_banda_herraje_corazon"), MOLDE_BANDA_HERRAJE_CORAZON);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_banda_herraje_medalla"), MOLDE_BANDA_HERRAJE_MEDALLA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_baja"), MOLDE_BORCEGO_CANA_BAJA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_alta"), MOLDE_BORCEGO_CANA_ALTA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_suela_plataforma"), MOLDE_BORCEGO_SUELA_PLATAFORMA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_botamanga_afuera"), MOLDE_BORCEGO_BOTAMANGA_AFUERA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_bota_alta"), MOLDE_BORCEGO_CANA_BOTA_ALTA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_zapato"), MOLDE_BORCEGO_CANA_ZAPATO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_mocasin"), MOLDE_BORCEGO_CANA_MOCASIN);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_bailarina"), MOLDE_BORCEGO_CANA_BAILARINA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_taco"), MOLDE_BORCEGO_CANA_TACO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_tenis_baja"), MOLDE_BORCEGO_CANA_TENIS_BAJA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_tenis_alta"), MOLDE_BORCEGO_CANA_TENIS_ALTA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_pantufla"), MOLDE_BORCEGO_CANA_PANTUFLA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cana_ninguna"), MOLDE_BORCEGO_CANA_NINGUNA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_suela_fina"), MOLDE_BORCEGO_SUELA_FINA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_suela_taco_chico"), MOLDE_BORCEGO_SUELA_TACO_CHICO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_suela_cuna"), MOLDE_BORCEGO_SUELA_CUNA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_suela_goma_tenis"), MOLDE_BORCEGO_SUELA_GOMA_TENIS);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_suela_plataforma_tenis"), MOLDE_BORCEGO_SUELA_PLATAFORMA_TENIS);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_suela_chancla"), MOLDE_BORCEGO_SUELA_CHANCLA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_suela_sandalia"), MOLDE_BORCEGO_SUELA_SANDALIA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_suela_tela"), MOLDE_BORCEGO_SUELA_TELA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_punta_redonda"), MOLDE_BORCEGO_PUNTA_REDONDA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_punta_alargada"), MOLDE_BORCEGO_PUNTA_ALARGADA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_punta_cuadrada"), MOLDE_BORCEGO_PUNTA_CUADRADA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_punta_fina"), MOLDE_BORCEGO_PUNTA_FINA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_punta_ancha"), MOLDE_BORCEGO_PUNTA_ANCHA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_punta_goma"), MOLDE_BORCEGO_PUNTA_GOMA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_punta_descubierta"), MOLDE_BORCEGO_PUNTA_DESCUBIERTA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cierre_velcro"), MOLDE_BORCEGO_CIERRE_VELCRO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cierre_tira_hebilla"), MOLDE_BORCEGO_CIERRE_TIRA_HEBILLA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cierre_tira_v"), MOLDE_BORCEGO_CIERRE_TIRA_V);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cierre_tobillera"), MOLDE_BORCEGO_CIERRE_TOBILLERA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cierre_gladiadora"), MOLDE_BORCEGO_CIERRE_GLADIADORA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cierre_costura_lateral"), MOLDE_BORCEGO_CIERRE_COSTURA_LATERAL);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_cierre_sin"), MOLDE_BORCEGO_CIERRE_SIN);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_detalle_lengueta"), MOLDE_BORCEGO_DETALLE_LENGUETA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_detalle_parche"), MOLDE_BORCEGO_DETALLE_PARCHE);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_borcego_detalle_costura_empeine"), MOLDE_BORCEGO_DETALLE_COSTURA_EMPEINE);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_ruedo_ajustado"), MOLDE_RUEDO_AJUSTADO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_ruedo_campana"), MOLDE_RUEDO_CAMPANA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_ruedo_ondulado"), MOLDE_RUEDO_ONDULADO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_ruedo_festoneado"), MOLDE_RUEDO_FESTONEADO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_ruedo_pico"), MOLDE_RUEDO_PICO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_ruedo_fruncido"), MOLDE_RUEDO_FRUNCIDO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_chaqueta_frente_abierta"), MOLDE_CHAQUETA_FRENTE_ABIERTA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_chaqueta_solapa_pico"), MOLDE_CHAQUETA_SOLAPA_PICO);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_chaqueta_solapa_redonda"), MOLDE_CHAQUETA_SOLAPA_REDONDA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_chaqueta_solapa_chal"), MOLDE_CHAQUETA_SOLAPA_CHAL);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_chaqueta_frente_cruzada"), MOLDE_CHAQUETA_FRENTE_CRUZADA);
        Registry.register(Registries.ITEM, Identifier.of(Modamod.MOD_ID, "molde_chaqueta_frente_abierta_pecho"), MOLDE_CHAQUETA_FRENTE_ABIERTA_PECHO);

        // "Guardar diseño" con nombre (2026-09-27): el nombre viaja como paquete propio, ver GuardarDisenoPayload.
        PayloadTypeRegistry.playC2S().register(GuardarDisenoPayload.ID, GuardarDisenoPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(GuardarDisenoPayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    if (context.player().getWorld().getBlockEntity(payload.pos()) instanceof ModeladoBlockEntity be
                            && be.canPlayerUse(context.player())) {
                        be.crearDisenoItem(context.player(), payload.nombre());
                    }
                }));

        // Elegir un molde de la bolsa para un casillero / sacarlo de la bolsa (2026-10-12): ver ElegirMoldePayload.
        PayloadTypeRegistry.playC2S().register(ElegirMoldePayload.ID, ElegirMoldePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ElegirMoldePayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    if (context.player().getWorld().getBlockEntity(payload.pos()) instanceof ModeladoBlockEntity be
                            && be.canPlayerUse(context.player()) && !be.trabajando()) {
                        switch (payload.modo()) {
                            case ElegirMoldePayload.SACAR -> be.sacarDeBolsa(context.player(), payload.item());
                            case ElegirMoldePayload.CARGAR_DISENO -> be.cargarDisenoItem(payload.pin());
                            case ElegirMoldePayload.SACAR_DISENO -> be.sacarDiseno(context.player(), payload.pin());
                            case ElegirMoldePayload.EXPORTAR_DISENO -> be.exportarDiseno(context.player(), payload.pin());
                            default -> be.elegirDeBolsa(payload.pin(), payload.item());
                        }
                    }
                }));
    }

    private ModeladoMod() {}
}
