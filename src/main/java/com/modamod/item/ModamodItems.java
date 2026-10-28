package com.modamod.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModamodItems {

    // --- Slots cosméticos (via Cosmetic Armor Updated) ---
    public static final ClothingArmorItem SOCKS_34 = registerArmor("socks_34",
            new ClothingArmorItem(ModamodArmorMaterials.CLOTH, ArmorItem.Type.BOOTS,
                    new Item.Settings().maxCount(1), true));

    // Migrada a Trinket: se dibuja pegada a la pierna real del jugador,
    // no con la geometría (más ancha) de bota de armadura.
    // maxCount(16): mismo criterio que remera -dos medias con distinto largo/
    // color/patrón tienen distintos componentes y nunca se apilan entre sí,
    // esto solo junta medias realmente iguales.
    public static final ClothingTrinketItem SOCKS_SOLID = register("socks_solid",
            new ClothingTrinketItem(new Item.Settings().maxCount(16), true));

    // socks_stripe_top / socks_stripe_alt SE FUERON: eran un item por
    // combinacion de colores. Ahora son patrones que se aplican sobre
    // socks_solid en el telar (PATTERN_STRIPE_TOP / PATTERN_STRIPE_ALT
    // mas abajo), asi que el aspecto lo definen los componentes del
    // ItemStack y no un Item distinto.

    public static final ClothingArmorItem FISHNET_SOCKS = registerArmor("fishnet_socks",
            new ClothingArmorItem(ModamodArmorMaterials.CLOTH, ArmorItem.Type.BOOTS,
                    new Item.Settings().maxCount(1), false));

    // Reemplaza a SHORTS. Primera prueba real del layering (se dibuja
    // ENCIMA de la media, sin borrarla — ver Capa.PIERNA_EXTERIOR), y ahora
    // ademas la prenda LARGA de pierna: se craftea pantalon completo y se
    // recorta con moldes en el telar. shorts.json se fue: era el antiguo
    // item fijo, sin eje.
    // maxCount(16): mismo criterio que remera/medias.
    public static final PantalonItem PANTALON = register("pantalon", new PantalonItem(new Item.Settings().maxCount(16)));

    /**
     * Pollera — primera pasada (2026-09-16), solo forma, sin eje todavía.
     * Comparte slot con PANTALON (piernas/exterior): alternativa, no
     * simultánea. Ver {@link PolleraItem}.
     */
    public static final PolleraItem POLLERA = register("pollera", new PolleraItem(new Item.Settings().maxCount(16)));

    /** Delantal de malla (2026-10-11, "un apron para complementar un maid outfit"). Ver {@link DelantalItem}. */
    public static final DelantalItem DELANTAL = register("delantal", new DelantalItem(new Item.Settings().maxCount(16)));

    // El croptop de ModaMod se retiro: era un chestplate del pipeline
    // viejo, nunca se le dibujo el arte -salia en damero- y desde que la
    // remera tiene el eje de largo, el corte crop hace lo mismo pero teñible,
    // estampable y sobre la geometria del cuerpo y no la de armadura.

    public static final ClothingArmorItem MAID_OUTFIT = registerArmor("maid_outfit",
            new ClothingArmorItem(ModamodArmorMaterials.CLOTH, ArmorItem.Type.CHESTPLATE,
                    new Item.Settings().maxCount(1), false));

    // --- Slot custom "arms" (via Trinkets) ---
    // Reemplaza a ARMWARMERS: aquel nunca se enganchó al sistema de capas
    // (sin color por lado, sin Garment, sin Pieza — no dibujaba nada). El
    // item viejo no se migra, se retira sin más (mismo criterio que shorts
    // al nacer PantalonItem): un stack viejo en un mundo existente queda
    // como ítem desconocido, no rompe nada.
    // maxCount(16): mismo criterio que remera/medias/pantalón.
    /** Capa personalizable (2026-09-29, "seria una nueva categoria de ropa"). */
    public static final CapaItem CAPA = register("capa", new CapaItem(new Item.Settings().maxCount(16)));

    /** Sombrero de bruja (2026-10-04), slot {@code head/sombrero}. */
    public static final SombreroBrujaItem SOMBRERO_BRUJA = register("sombrero_bruja",
            new SombreroBrujaItem(new Item.Settings().maxCount(16)));   // 2026-10-08: apilan 16 si son idénticas

    /** Banda: cinto o choker según su zona (2026-10-05), slots {@code torso/cinto} y {@code head/choker}. */
    public static final BandaItem BANDA = register("banda", new BandaItem(new Item.Settings().maxCount(16)));   // 2026-10-08: idem

    /** Maletín de outfits (2026-10-21): un Guardarropas portátil, con los outfits en el propio ítem. */
    public static final com.modamod.guardarropas.MaletinItem MALETIN = register("maletin",
            new com.modamod.guardarropas.MaletinItem(new Item.Settings().maxCount(1)));

    /** Máscaras y barbijo (2026-10-24): slots {@code head/mascara} y {@code head/barbijo}, ver {@link CaraItem}. */
    public static final CaraItem ANTIFAZ = register("antifaz", new CaraItem(new Item.Settings().maxCount(16), "antifaz", false));
    public static final CaraItem MEDIA_MASCARA = register("media_mascara", new CaraItem(new Item.Settings().maxCount(16), "media_mascara", false));
    public static final CaraItem MASCARA_COMPLETA = register("mascara_completa", new CaraItem(new Item.Settings().maxCount(16), "mascara_completa", false));
    public static final CaraItem BARBIJO = register("barbijo", new CaraItem(new Item.Settings().maxCount(16), "barbijo", true));

    /** Borcegos (2026-10-08, "un slot de calzado"), slot {@code socks/calzado}. */
    public static final BorcegosItem BORCEGOS = register("borcegos", new BorcegosItem(new Item.Settings().maxCount(16)));

    // Moldes de correa (2026-10-05, Mesa de estilado): el estilo de la correa que se pone; no se gastan.
    public static final com.modamod.correa.MoldeCorreaItem MOLDE_CORREA_LISA = register("molde_correa_lisa",
            new com.modamod.correa.MoldeCorreaItem(new Item.Settings().maxCount(1), com.modamod.correa.EstiloCorrea.LISA));
    public static final com.modamod.correa.MoldeCorreaItem MOLDE_CORREA_CADENA = register("molde_correa_cadena",
            new com.modamod.correa.MoldeCorreaItem(new Item.Settings().maxCount(1), com.modamod.correa.EstiloCorrea.CADENA));
    public static final com.modamod.correa.MoldeCorreaItem MOLDE_CORREA_CADENA_FINA = register("molde_correa_cadena_fina",
            new com.modamod.correa.MoldeCorreaItem(new Item.Settings().maxCount(1), com.modamod.correa.EstiloCorrea.CADENA_FINA));
    public static final com.modamod.correa.MoldeCorreaItem MOLDE_CORREA_OJALILLOS = register("molde_correa_ojalillos",
            new com.modamod.correa.MoldeCorreaItem(new Item.Settings().maxCount(1), com.modamod.correa.EstiloCorrea.OJALILLOS));
    public static final com.modamod.correa.MoldeCorreaItem MOLDE_CORREA_CORDON = register("molde_correa_cordon",
            new com.modamod.correa.MoldeCorreaItem(new Item.Settings().maxCount(1), com.modamod.correa.EstiloCorrea.CORDON));

    // Apliques (2026-10-01, Mesa de estilado): moldes que no se gastan y el retazo con los colores.
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_MONO = register("molde_aplique_mono",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.MONO));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_MARIPOSA = register("molde_aplique_mariposa",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.MARIPOSA));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_FLOR = register("molde_aplique_flor",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.FLOR));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_CANGURO = register("molde_aplique_canguro",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.CANGURO));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_CORBATA = register("molde_aplique_corbata",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.CORBATA));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_CORDONES = register("molde_aplique_cordones",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.CORDONES));
    // Botones y bolsillos (2026-10-22, "de apliques considero que nos faltaron botones y bolsillos").
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_BOTON = register("molde_aplique_boton",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.BOTON));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_BOTONERA = register("molde_aplique_botonera",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.BOTON, new com.modamod.aplique.Fila(4, 1.5f)));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_BOLSILLO = register("molde_aplique_bolsillo",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.BOLSILLO));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_BOLSILLO_TAPA = register("molde_aplique_bolsillo_tapa",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.BOLSILLO_TAPA));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_BOLSILLO_JEAN = register("molde_aplique_bolsillo_jean",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.BOLSILLO_JEAN));
    // Tachas y parches bordados (2026-10-22, "segui con todo").
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_TACHA = register("molde_aplique_tacha",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.TACHA));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_TACHAS = register("molde_aplique_tachas",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.TACHA, new com.modamod.aplique.Fila(5, 1.0f)));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_PARCHE_CORAZON = register("molde_aplique_parche_corazon",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.PARCHE_CORAZON));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_PARCHE_ESTRELLA = register("molde_aplique_parche_estrella",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.PARCHE_ESTRELLA));
    public static final com.modamod.aplique.MoldeApliqueItem MOLDE_APLIQUE_PARCHE_RAYO = register("molde_aplique_parche_rayo",
            new com.modamod.aplique.MoldeApliqueItem(new Item.Settings().maxCount(1), com.modamod.aplique.ModeloAplique.PARCHE_RAYO));
    /** Lo fabrica la Mesa de estilado creativa (2026-10-04); no está en la pestaña creativa. */
    public static final com.modamod.aplique.MoldeApliquePersonalizadoItem MOLDE_APLIQUE_PERSONALIZADO = register(
            "molde_aplique_personalizado",
            new com.modamod.aplique.MoldeApliquePersonalizadoItem(new Item.Settings().maxCount(1)));
    public static final com.modamod.aplique.RetazoApliqueItem RETAZO_APLIQUE = register("retazo_aplique",
            new com.modamod.aplique.RetazoApliqueItem(new Item.Settings().maxCount(64)));

    /** Muestra de color de la Estación de Tintes (2026-09-30), ver {@link MuestraColorItem}. */
    /** Estrógenos (2026-10-01): cada dosis sube un talle el busto del relieve. */
    public static final EstrogenosItem ESTROGENOS = register("estrogenos",
            new EstrogenosItem(new Item.Settings().maxCount(16)));
    /** Testosterona (2026-10-22): la contraparte, baja un talle por dosis; se compra al clérigo como los Estrógenos. */
    public static final EstrogenosItem TESTOSTERONA = register("testosterona",
            new EstrogenosItem(new Item.Settings().maxCount(16), false));
    /** Moldes de textura de tela (2026-10-01, relieve), se usan en la Mesa de estilado. */
    public static final MoldeTexturaItem MOLDE_TEXTURA_FRUNCIDO = register("molde_textura_fruncido",
            new MoldeTexturaItem(TexturaTela.FRUNCIDO, new Item.Settings().maxCount(1)));
    public static final MoldeTexturaItem MOLDE_TEXTURA_ACOLCHADO = register("molde_textura_acolchado",
            new MoldeTexturaItem(TexturaTela.ACOLCHADO, new Item.Settings().maxCount(1)));
    public static final MuestraColorItem TINTE_MEZCLA = register("tinte_mezcla",
            new MuestraColorItem(new Item.Settings().maxCount(16)));
    public static final CalientabrazosItem CALIENTABRAZOS = register("calientabrazos",
            new CalientabrazosItem(new Item.Settings().maxCount(16)));

    // --- Patrones reusables para la estación de personalización ---
    // Grosor base (escala 8x, tamaño GRANDE) de cada uno — Mediano/Chico
    // lo escalan en tiempo real (ver PatronGenerador/TamanoPatron), no
    // hay archivo por tamaño.
    // SUPERIOR y no ALTERNADO (2026-09-18, "top stripe es una linea en la
    // parte superior de la prenda"): una sola banda pegada arriba, no
    // repetida — coherente con el nombre, a diferencia de los otros dos
    // que sí son un ritmo de rayas de punta a punta.
    public static final ClothingPatternItem PATTERN_RAYAS = register("pattern_rayas",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "rayas"),
                    com.modamod.render.PatronGenerador.Forma.ALTERNADO, 16));

    public static final ClothingPatternItem PATTERN_STRIPE_TOP = register("pattern_stripe_top",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "stripe_top"),
                    com.modamod.render.PatronGenerador.Forma.ARRIBA, 8, false));

    public static final ClothingPatternItem PATTERN_STRIPE_ALT = register("pattern_stripe_alt",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "stripe_alt"),
                    com.modamod.render.PatronGenerador.Forma.ALTERNADO, 16, false));

    // TRES_RAYAS y no ALTERNADO (2026-09-19, "las tres rayitas son solo
    // tres, no se repiten"): antes tapizaba toda la prenda como stripe_alt;
    // ahora es un bloque fijo de 3 rayas que además se puede reposicionar
    // (eje "posicion" en RegionResolver.CapaPatron).
    public static final ClothingPatternItem PATTERN_TRIPLE_STRIPE = register("pattern_triple_stripe",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "triple_stripe"),
                    com.modamod.render.PatronGenerador.Forma.TRES_RAYAS, 12, false));

    // Moldes de MOTIVO (2026-09-28, Fase 1 de la propuesta de patrones):
    // el último número es la escala del sprite en tamaño GRANDE (px del
    // atlas por px del dibujo), ver PatronGenerador#mascaraMotivo.
    public static final ClothingPatternItem PATTERN_CORAZONES = register("pattern_corazones",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "corazones"), com.modamod.render.Motivo.CORAZONES, 2));
    public static final ClothingPatternItem PATTERN_ESTRELLAS = register("pattern_estrellas",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "estrellas"), com.modamod.render.Motivo.ESTRELLAS, 2));
    public static final ClothingPatternItem PATTERN_LUNARES = register("pattern_lunares",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "lunares"), com.modamod.render.Motivo.LUNARES, 2));
    public static final ClothingPatternItem PATTERN_VICHY = register("pattern_vichy",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "vichy"), com.modamod.render.Motivo.VICHY, 1));

    public static final ClothingPatternItem PATTERN_LEOPARDO = register("pattern_leopardo",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "leopardo"), com.modamod.render.Motivo.LEOPARDO, 1));
    public static final ClothingPatternItem PATTERN_CEBRA = register("pattern_cebra",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "cebra"), com.modamod.render.Motivo.CEBRA, 1));
    public static final ClothingPatternItem PATTERN_TIGRE = register("pattern_tigre",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "tigre"), com.modamod.render.Motivo.TIGRE, 1));
    public static final ClothingPatternItem PATTERN_VACA = register("pattern_vaca",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "vaca"), com.modamod.render.Motivo.VACA, 1));

    public static final ClothingPatternItem PATTERN_JIRAFA = register("pattern_jirafa",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "jirafa"), com.modamod.render.Motivo.JIRAFA, 1));
    public static final ClothingPatternItem PATTERN_SERPIENTE = register("pattern_serpiente",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "serpiente"), com.modamod.render.Motivo.SERPIENTE, 1));

    public static final ClothingPatternItem PATTERN_DALMATA = register("pattern_dalmata",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "dalmata"), com.modamod.render.Motivo.DALMATA, 1));

    // Cuadros (2026-10-22, "agregar dos patrones mas, escocesa onda kilt y leñador" + pata de gallo).
    public static final ClothingPatternItem PATTERN_TARTAN = register("pattern_tartan",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "tartan"), com.modamod.render.Motivo.TARTAN, 1));
    public static final ClothingPatternItem PATTERN_LENADOR = register("pattern_lenador",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "lenador"), com.modamod.render.Motivo.LENADOR, 1));
    public static final ClothingPatternItem PATTERN_PATA_DE_GALLO = register("pattern_pata_de_gallo",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "pata_de_gallo"), com.modamod.render.Motivo.PATA_DE_GALLO, 1));
    public static final ClothingPatternItem PATTERN_ARGYLE = register("pattern_argyle",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "argyle"), com.modamod.render.Motivo.ARGYLE, 1));
    public static final ClothingPatternItem PATTERN_CAMUFLAJE = register("pattern_camuflaje",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "camuflaje"), com.modamod.render.Motivo.CAMUFLAJE, 1));
    public static final ClothingPatternItem PATTERN_CAMUFLAJE_DIGITAL = register("pattern_camuflaje_digital",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "camuflaje_digital"), com.modamod.render.Motivo.CAMUFLAJE_DIGITAL, 1));
    public static final ClothingPatternItem PATTERN_DIPLOMATICAS = register("pattern_diplomaticas",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "diplomaticas"), com.modamod.render.Motivo.DIPLOMATICAS, 1));
    public static final ClothingPatternItem PATTERN_VENTANA = register("pattern_ventana",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "ventana"), com.modamod.render.Motivo.VENTANA, 1));
    public static final ClothingPatternItem PATTERN_ZIGZAG = register("pattern_zigzag",
            new ClothingPatternItem(new Item.Settings().maxCount(1),
                    Identifier.of("modamod", "zigzag"), com.modamod.render.Motivo.ZIGZAG, 1));

    private static ClothingArmorItem registerArmor(String path, ClothingArmorItem item) {
        return Registry.register(Registries.ITEM, Identifier.of("modamod", path), item);
    }

    private static <T extends Item> T register(String path, T item) {
        return Registry.register(Registries.ITEM, Identifier.of("modamod", path), item);
    }

    public static void init() {
        // fuerza class-loading al llamarse desde Modamod#onInitialize
    }
}
