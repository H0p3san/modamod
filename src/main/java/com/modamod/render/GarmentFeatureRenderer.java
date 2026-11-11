package com.modamod.render;

import com.modamod.body.PerfilCuerpo;
import com.modamod.body.PerfilesDeCuerpo;
import com.modamod.prenda.Capa;
import com.modamod.prenda.Parte;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Dibuja TODA la ropa del mod, en orden, desde un solo lugar.
 *
 * Antes cada prenda se registraba como TrinketRenderer y se dibujaba sola.
 * Eso alcanzaba mientras hubiera una prenda por parte del cuerpo, pero el
 * orden lo decidia Trinkets segun el order de los slots, no nosotros: no
 * habia forma de decir "el short va arriba de la media". El ordinal de capa
 * necesita un unico punto de dibujo, y este es.
 *
 * Se engancha con LivingEntityFeatureRendererRegistrationCallback, que es un
 * hook publico de Fabric API — sin Mixins, igual que el resto del mod.
 *
 * <h2>El orden</h2>
 * <ol>
 *   <li>el CUERPO BASE, una sola vez por parte del cuerpo, dilatado 0.30;</li>
 *   <li>las piezas de esa parte en el orden de la lista de ropa ({@link com.modamod.ropa.ListaDeRopa}), de adentro
 *       hacia afuera: cada una se infla por fuera de lo que ya hay en las filas que comparten (la holgura), sea de la
 *       categoría que sea.</li>
 * </ol>
 * La fila de la armadura cosmética parte la lista: lo de las filas de arriba se infla por fuera de la armadura que se ve.
 * Donde una prenda tiene tela es opaca y gana; donde no, se ve la capa de
 * abajo o el cuerpo. De yapa esto arregla el midriff: una musculosa deja ver
 * la panza sin que la prenda tenga que rellenarla ella misma.
 *
 * <h2>El cuerpo base va donde la prenda MANDA, no donde tiene tela</h2>
 * Las partes del sustrato salen de {@link com.modamod.prenda.DefinicionDePrenda#partes} y no de las piezas
 * dibujadas. La diferencia importa para una musculosa: no dibuja nada en los
 * brazos, pero igual tiene que taparle las mangas pintadas de la skin, asi
 * que declara los brazos como suyos y ahi va el cuerpo.
 */
public class GarmentFeatureRenderer<T extends LivingEntity, M extends EntityModel<T>>
        extends FeatureRenderer<T, M> {

    /**
     * Hook del visor 3D de la Mesa de Modelado: cuando no es null, se usa
     * ESTA lista en vez de leer Trinkets real — permite previsualizar una
     * prenda en construcción (fijadas aplicadas sobre el slot PRENDA, ver
     * {@code ModeladoBlockEntity#previsualizar}) sin que el jugador se la
     * tenga puesta encima. Se setea y limpia en el mismo frame, alrededor
     * de un único {@code InventoryScreen.drawEntity}, así que nunca afecta
     * el render normal del jugador en el mundo.
     */
    @Nullable
    public static List<ItemStack> previewOverride;
    /** El sombrero de bruja de la vista previa de la Mesa de estilado (2026-10-05), que no está en los Trinkets del jugador. */
    @Nullable
    public static ItemStack sombreroOverride;

    /**
     * Los borcegos de este dibujo (2026-10-08, slot de calzado), o null: el pantalón se adapta a ellos
     * ({@link #dibujarPiezas}). {@code render0} lo fija con lo puesto; el Maniquí, que dibuja la tela por su cuenta, deja
     * el suyo en {@link #calzadoDeTela} justo antes de llamar a {@link #dibujarTela}.
     */
    private static ItemStack calzado;
    public static ItemStack calzadoDeTela;
    /** Los borcegos que muestra el Guardarropas en su vista previa (junto al sombrero de {@link #sombreroOverride}). */
    public static ItemStack calzadoOverride;

    /**
     * El pie de esta pierna lo dibuja el calzado abierto (sandalias, chanclas: capellada "ninguna"): las medias con volumen no
     * suman su punta de tela ni de piel, el pie generado ya lleva la tela de la media (2026-10-18).
     */
    private static boolean pieDelCalzado(Parte parte) {
        return calzado != null
                && com.modamod.item.CalzadoItem.cana(calzado, parte == Parte.PIERNA_IZQ) == com.modamod.item.BorcegoCana.NINGUNA;
    }

    /**
     * ¿Lleva la media con volumen el cubo del pie de adelante? (2026-10-21, "skin tight y fitted: z-fighting y cuboide separado"): con
     * calce Pegado o Ajustado la media es una segunda piel que copia la pierna, que no tiene pie: el cubo quedaba suelto delante de la
     * pierna (con su planta aparte) y peleaba los planos con la piel. Solo lo llevan las medias con volumen (Normal, Suelto, Oversize),
     * ninguna si el calzado abierto dibuja el pie, y ninguna si la media no llega al pie.
     */
    private static boolean conPuntaDeMedia(Pieza p, Parte parte) {
        if (pieDelCalzado(parte)) return false;
        // Rediseño ("el cubo de pie se empieza a pintar antes de que el largo llegue hasta el cubo"): la punta es del pie, así
        // que solo va cuando la media llega hasta la última fila; una media cortada más arriba no la tiene.
        if (p.filaHasta() < 12) return false;
        com.modamod.item.Calce c = com.modamod.item.Calce.de(p.dilatacion());
        return c != com.modamod.item.Calce.PEGADO && c != com.modamod.item.Calce.AJUSTADO;
    }

    /**
     * ¿El pantalón va por fuera del calzado? Lo dice el orden de la lista de ropa (2026-11-04, "ahora que se resuelve con capas"):
     * el que está más a la izquierda/arriba (índice menor, más de afuera) tapa al otro. Sin lista o sin alguno de los dos, por dentro.
     */
    private static boolean pantalonSobreElCalzado(@Nullable LivingEntity entidad) {
        if (entidad == null) return false;
        var inv = com.modamod.ropa.ListaDeRopa.de(entidad);
        if (inv == null) return false;
        int iCalzado = -1, iPantalon = -1;
        var lugares = com.modamod.ropa.ListaDeRopa.lugares(inv);
        for (int i = 0; i < lugares.size(); i++) {
            ItemStack s = lugares.get(i);
            if (s.isEmpty()) continue;
            if (iCalzado < 0 && com.modamod.prenda.Prendas.es(s, com.modamod.prenda.CategoriaDePrenda.CALZADO)) iCalzado = i;
            if (iPantalon < 0 && com.modamod.prenda.Prendas.categoria(s) == com.modamod.prenda.CategoriaDePrenda.PANTALON) iPantalon = i;
        }
        return iCalzado >= 0 && iPantalon >= 0 && iPantalon < iCalzado;
    }

    /** Lo calcula {@link #render1} una vez por cuadro. */
    private static boolean pantalonSobreCalzado;

    /**
     * El cinto puesto que aprieta la piel del torso: {filaDesde, filaHasta, dilatación} (ver
     * {@link BandaRenderer#compresionDeCinto}), o null. Se calcula una vez por cuadro, como {@link #pantalonSobreCalzado}.
     */
    @Nullable
    private static float[] cintoCompresion;

    private static void anotarCinto(List<ItemStack> prendas) {
        cintoCompresion = compresionDeCintos(prendas);
    }

    @Nullable
    static float[] compresionDeCintos(List<ItemStack> prendas) {
        float[] out = null;
        for (ItemStack s : prendas) {
            if (!com.modamod.prenda.Prendas.es(s, com.modamod.prenda.CategoriaDePrenda.BANDA)) continue;
            float[] c = BandaRenderer.compresionDeCinto(s);
            // El que más aprieta manda (dos cintos puestos).
            if (c != null && (out == null || c[2] < out[2])) out = c;
        }
        return out;
    }

    /**
     * Los cintos puestos. Van en su propio slot de Trinkets, no en la lista de ropa: {@link #equipadas} solo trae ropa de
     * cuerpo y el cinto no llegaba nunca (2026-11-08, "el cinto ajustado no se ve": quedaba metido dentro de la piel).
     */
    static List<ItemStack> cintosDe(@Nullable LivingEntity entidad) {
        // Primero el accesorio que muestran las máquinas: su lista de vista previa no lo trae (2026-11-08, "en la vista previa del
        // cinto se ve la skin en partes").
        if (sombreroOverride != null) return List.of(sombreroOverride);
        if (previewOverride != null) return previewOverride;
        List<ItemStack> out = new ArrayList<>();
        if (entidad != null) TrinketsApi.getTrinketComponent(entidad).ifPresent(c -> {
            for (var par : c.getAllEquipped()) {
                if (com.modamod.prenda.Prendas.es(par.getRight(), com.modamod.prenda.CategoriaDePrenda.BANDA)) out.add(par.getRight());
            }
        });
        return out;
    }

    /** Las partes con cuerpo base: las de la ropa, más el torso si un cinto lo aprieta. */
    private static List<Parte> partesConCuerpo(List<ItemStack> prendas, boolean cuerpoEntero, boolean cintoAprieta) {
        if (cuerpoEntero) return List.of(Parte.values());
        List<Parte> out = com.modamod.prenda.Prendas.partesCubiertas(prendas);
        if (cintoAprieta && !out.contains(Parte.TORSO)) out.add(Parte.TORSO);
        return out;
    }

    private static ItemStack calzadoDe(@Nullable LivingEntity entidad) {
        if (calzadoOverride != null) return calzadoOverride;
        if (sombreroOverride != null) {
            return com.modamod.prenda.Prendas.es(sombreroOverride, com.modamod.prenda.CategoriaDePrenda.CALZADO) ? sombreroOverride : null;
        }
        if (entidad == null) return null;
        ItemStack[] hallado = {null};
        TrinketsApi.getTrinketComponent(entidad).ifPresent(c -> {
            for (var par : c.getAllEquipped()) {
                if (com.modamod.prenda.Prendas.es(par.getRight(), com.modamod.prenda.CategoriaDePrenda.CALZADO)) hallado[0] = par.getRight();
            }
        });
        return hallado[0];
    }

    /**
     * Click en la vista 3D de la Mesa de estilado (2026-10-01): si no es null,
     * el próximo {@link #render} guarda acá, por parte, la matriz que lleva del
     * espacio local de esa parte (en bloques, como los cuboides de ModelPart)
     * a coordenadas de pantalla de la GUI. Se setea y se lee en el mismo frame.
     */
    @Nullable
    public static Map<Parte, org.joml.Matrix4f> capturaPoses;

    /**
     * Para el click de la Mesa de estilado en la pollera y la capa (2026-10-02,
     * "Pollera y Capa no registran click on garment"): si no es null, el
     * próximo dibujo guarda acá sus mallas ({@link MallaCapturada}, claves
     * {@code "pollera"} y {@code "capa"}) en coordenadas de pantalla.
     */
    @Nullable
    public static Map<String, MallaCapturada> capturaMallas;

    /** Graba la malla que dibuja {@code dibujo} si hace falta (apliques o el click de la Mesa) y devuelve la grabación. */
    @Nullable
    static MallaCapturada grabar(String clave, boolean conApliques, Runnable dibujo) {
        MallaCapturada malla = conApliques || capturaMallas != null ? new MallaCapturada() : null;
        MallaCapturada anterior = MallaCapturada.grabando;
        MallaCapturada.grabando = malla;
        try {
            dibujo.run();
        } finally {
            MallaCapturada.grabando = anterior;
        }
        if (malla != null && capturaMallas != null) capturaMallas.put(clave, malla);
        return malla;
    }

    public GarmentFeatureRenderer(FeatureRendererContext<T, M> contexto) {
        super(contexto);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz,
                       T entidad, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {
        long t0 = com.modamod.render.Perf.ini();
        try {
            render0(matrices, vertexConsumers, luz, entidad, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
        } finally {
            com.modamod.render.Perf.fin(com.modamod.render.Perf.Seccion.ROPA, t0);
        }
    }

    private static boolean MAPAS_EN_USO = false, REUSA_MAPAS = false;
    private static final Map<Parte, List<Pieza>> MAPA_POR_PARTE = new EnumMap<>(Parte.class);
    private static final Map<Pieza, ItemStack> MAPA_ORIGEN = new java.util.IdentityHashMap<>();
    private static final Map<Pieza, Integer> MAPA_PROFUNDIDAD = new java.util.IdentityHashMap<>();

    /**
     * Las piezas de {@code prendas} (de adentro hacia afuera) agrupadas por parte, en ese orden; las de una misma prenda,
     * por su {@link Pieza#capa}. Anota de qué prenda sale cada una y su profundidad (su lugar en {@code prendas}).
     */
    private static void agrupar(List<ItemStack> prendas, @Nullable LivingEntity entidad, Map<Parte, List<Pieza>> porParte,
                                Map<Pieza, ItemStack> origen, Map<Pieza, Integer> profundidad) {
        porParte.clear();
        origen.clear();
        profundidad.clear();
        for (int k = 0; k < prendas.size(); k++) {
            ItemStack stack = prendas.get(k);
            long t0 = Perf.ini();
            List<Pieza> propias = new ArrayList<>(PiezasDePrenda.de(stack, entidad));
            Perf.fin(Perf.Seccion.PIEZAS, t0);
            propias.sort(Comparator.comparingInt(Pieza::capa));
            for (Pieza pieza : propias) {
                porParte.computeIfAbsent(pieza.parte(), x -> new ArrayList<>()).add(pieza);
                origen.put(pieza, stack);
                profundidad.put(pieza, k);
            }
        }
    }

    /**
     * La vista previa de una máquina: lo puesto con {@code vista} en el lugar de la prenda de su clase (la más de adentro,
     * si hay varias; las otras se sacan), o encima de todo si no lleva ninguna.
     */
    public static List<ItemStack> conVista(List<ItemStack> puestas, ItemStack vista) {
        List<ItemStack> out = new ArrayList<>(puestas);
        int donde = -1;
        for (int i = out.size() - 1; i >= 0; i--) {
            if (out.get(i).getItem().getClass() == vista.getItem().getClass()) {
                out.remove(i);
                donde = i;
            }
        }
        if (donde < 0) {
            // Sin ninguna de su clase puesta (2026-11-04): va arriba de lo que por naturaleza va debajo de ella y debajo de lo que va encima.
            int rango = rangoDeCapa(vista);
            donde = 0;
            while (donde < out.size() && rangoDeCapa(out.get(donde)) <= rango) donde++;
        }
        out.add(Math.min(donde, out.size()), vista);
        return out;
    }

    /** Qué tan por fuera va una prenda de cuerpo por naturaleza: medias y pantalón por dentro, remera, calientabrazos, pollera, capa y delantal por fuera. */
    private static int rangoDeCapa(ItemStack s) {
        var cat = com.modamod.prenda.Prendas.categoria(s);
        if (cat == null) return 3;
        return switch (cat) {
            case MEDIAS -> 0;
            case PANTALON -> 1;
            case POLLERA -> 2;
            case REMERA -> 3;
            case CALIENTABRAZOS -> 4;
            case CAPA -> 5;
            case DELANTAL -> 6;
            default -> 3;
        };
    }

    public void render0(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz,
                       T entidad, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {
        boolean anterior = REUSA_MAPAS, esteReusa = !MAPAS_EN_USO;
        REUSA_MAPAS = esteReusa;
        if (esteReusa) MAPAS_EN_USO = true;
        try {
            render1(matrices, vertexConsumers, luz, entidad, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
        } finally {
            EfectoTrim.volcar();   // el brillo de la última pieza con acabado (2026-10-22)
            if (esteReusa) MAPAS_EN_USO = false;
            REUSA_MAPAS = anterior;
        }
    }

    /** La holgura de cada fila de una media con volumen; el ribete de piel engrosa las 2 filas del borde de arriba o de abajo. */
    private static float[] filasDeMedia(Pieza pieza, float base, int desde, int hasta) {
        float[] dil = new float[12];
        for (int f = 0; f < 12; f++) dil[f] = CuerpoGeometria.dilTelaFila(base, f);
        // Escalonado suave (2026-11-04, "rompio la continuidad, son cuadraditos flotando"): cada fila del ribete un poco más que la vecina.
        float[] sube = {0.5F, 0.3F, 0.12F};
        if (hasta - desde > 3) {
            if (pieza.piel()) for (int k = 0; k < 3; k++) dil[hasta - 1 - k] += sube[k];
            if (pieza.pielSup()) for (int k = 0; k < 3; k++) dil[desde + k] += sube[k];
        }
        return dil;
    }

    private void render1(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz,
                       T entidad, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {
        long tPrepara = Perf.ini();

        if (!(getContextModel() instanceof BipedEntityModel<?> biped)) return;

        List<ItemStack> prendas = previewOverride != null ? previewOverride : equipadas(entidad);
        calzado = calzadoDe(entidad);
        pantalonSobreCalzado = pantalonSobreElCalzado(entidad);
        anotarCinto(cintosDe(entidad));
        // La armadura que se ve infla capa, embozo y capucha (2026-10-28, "que la capa embozo y capucha se inflen mas
        // cuando use armadura"); en la vista previa de las máquinas no cuenta.
        // (2026-11-04, "la capa y la capucha se ven mas chicas en la vista previa"): la vista usa lo que infla la armadura del que
        // mira, la dibuje o no, para que capa, embozo y capucha tengan el tamaño que van a tener en el mundo; sin prendas "encima"
        // de la armadura, la ropa de cuerpo no se infla por ella.
        usarArmadura(previewOverride != null ? ArmaduraVisible.de(slot -> piezaVisible(entidad, slot), 0)
                : ArmaduraVisible.de(slot -> piezaVisible(entidad, slot), com.modamod.ropa.ListaDeRopa.prendasSobreLaArmadura(entidad)),
                prendas.size());
        // Tela blanda de los apliques (2026-10-04): la inercia de esta entidad, o la de la vista previa.
        if (previewOverride != null) {
            FisicaApliques.preparar(FisicaApliques.CLAVE_VISTA_PREVIA, null, tickDelta, FisicaApliques.modoVistaPrevia);
        } else {
            FisicaApliques.preparar(entidad, entidad, tickDelta, FisicaApliques.Modo.REAL);
        }
        if (capturaPoses != null) {
        for (Parte parte : Parte.values()) {
                ModelPart p = CuerpoGeometria.delJugador(biped, parte);
                org.joml.Matrix4f m = new org.joml.Matrix4f(matrices.peek().getPositionMatrix());
                m.translate(p.pivotX / 16f, p.pivotY / 16f, p.pivotZ / 16f);
                m.rotate(new org.joml.Quaternionf().rotationZYX(p.roll, p.yaw, p.pitch));
                capturaPoses.put(parte, m);
            }
        }
        // En la vista previa de elegir cuerpo, el cuerpo va entero aunque no haya ropa.
        // Cuerpo entero: la vista previa de elegir cuerpo, o un perfil que usa
        // el cuerpo como skin aunque no haya ropa (2026-09-29, "un selector que
        // directamente te deje esa skin de default").
        com.modamod.render.relieve.RelieveRender.fijarVolumen(perfilDe(entidad));
        boolean cuerpoEntero = perfilOverride != null || perfilDe(entidad).siempre();
        // Busto (2026-10-01, estilo Only Jugs): uno por dibujo, con su rebote.
        prepararBusto(entidad, tickDelta);
        // Apliques de armaduras y wearables de cualquier mod (2026-10-02).
        dibujarApliquesDeVestibles(entidad, biped, matrices, vertexConsumers, luz);
        // Sombrero de bruja (2026-10-04): no es una prenda de cuerpo, se dibuja siempre que esté puesto.
        dibujarSombrero(entidad, biped, matrices, vertexConsumers, luz, holguraDeCinto(prendas), prendas);
        // Máscara y barbijo (2026-10-24): igual, en la cabeza.
        CaraRenderer.dibujar(entidad, biped, matrices, vertexConsumers, luz);
        if (prendas.isEmpty() && !cuerpoEntero && cintoCompresion == null) {
            dibujarBustoEnLaSkin(entidad, biped, matrices, vertexConsumers, luz);
            com.modamod.render.relieve.BustoRender.actual = null;
            return;
        }

        boolean slim = esSlim(entidad);
        anotarPollera(prendas);

        // Las partes que alguna prenda gobierna: ahi va el cuerpo base.
        List<Parte> conCuerpo = partesConCuerpo(prendas, cuerpoEntero, cintoCompresion != null);

        // Las piezas de tela, agrupadas por parte. EnumMap para que el
        // recorrido sea estable: dos frames no pueden dibujar en distinto
        // orden dos piezas de la misma capa.
        // Los dos mapas se reusan entre cuadros (2026-10-12, "vamos por las seguras"); si este render quedara anidado en otro
        // (otro mod dibujando un jugador adentro de un jugador) cada uno usa los suyos.
        boolean reusar = REUSA_MAPAS;
        Map<Parte, List<Pieza>> porParte = reusar ? MAPA_POR_PARTE : new EnumMap<>(Parte.class);
        // De qué prenda sale cada pieza: el relieve mira su calce, su textura y su archivo de extrusión.
        Map<Pieza, ItemStack> origen = reusar ? MAPA_ORIGEN : new java.util.IdentityHashMap<>();
        Map<Pieza, Integer> profundidad = reusar ? MAPA_PROFUNDIDAD : new java.util.IdentityHashMap<>();
        agrupar(prendas, entidad, porParte, origen, profundidad);
        // La capa no gobierna ninguna parte: se dibuja antes del corte de abajo. Sola (o con la pollera, que tampoco es una
        // Pieza) sigue el camino de siempre: su capucha y sus apliques también se dibujan.
        dibujarCapa(prendas, entidad, biped, matrices, vertexConsumers, luz, tickDelta, false);

        Identifier cuerpo = texturaDelCuerpo(entidad, slim);
        // Relieve del cuerpo (2026-10-01): músculos, curvas y busto del perfil.
        com.modamod.render.relieve.MapaRelieve mapaCuerpo =
                com.modamod.render.relieve.RelieveRender.estilo == com.modamod.render.relieve.RelieveRender.Estilo.APAGADO
                        ? com.modamod.render.relieve.MapaRelieve.PLANO
                        : com.modamod.render.relieve.RelieveCuerpo.de(perfilDe(entidad), slim);
        // El torso que solo toma el cinto (2026-11-08, "solo en sus filas"): la skin real en todas las filas, apretada a la
        // altura del cinto, con su segunda capa (ComposedSkin la borra solo ahí). Nada del cuerpo elegido.
        boolean torsoDelCinto = cintoCompresion != null && !cuerpoEntero
                && !com.modamod.prenda.Prendas.partesCubiertas(prendas).contains(Parte.TORSO);
        // El torso lo dibuja vanilla (ninguna prenda lo gobierna): el busto va con la piel de la skin.
        if (!conCuerpo.contains(Parte.TORSO) || torsoDelCinto) dibujarBustoEnLaSkin(entidad, biped, matrices, vertexConsumers, luz);

        Perf.fin(Perf.Seccion.PREPARA, tPrepara);
        for (Parte parte : Parte.values()) {
            boolean llevaCuerpo = cuerpo != null && conCuerpo.contains(parte)
                    // La cabeza nunca lleva cuerpo base: es la cara del
                    // jugador, y taparla con un tono plano le borra los ojos.
                    && parte != Parte.CABEZA;
            List<Pieza> piezas = porParte.get(parte);
            if (!llevaCuerpo && piezas == null) continue;

            ModelPart delJugador = CuerpoGeometria.delJugador(biped, parte);
            if (!delJugador.visible) continue;

            // Medias con volumen: solo si son la tela de más arriba de esa pierna (si hay un
            // pantalón encima, el volumen asomaría a través de él).
            Pieza conVolumen = null;
            if ((parte == Parte.PIERNA_DER || parte == Parte.PIERNA_IZQ) && piezas != null && !piezas.isEmpty()
                    && piezas.get(piezas.size() - 1).volumenPierna()) {
                conVolumen = piezas.get(piezas.size() - 1);
            }

            long tCuerpo = Perf.ini();
            if (llevaCuerpo && parte == Parte.TORSO && !torsoDelCinto) {
                // Debajo de una tela holgada el busto lo dibuja la tela (su manto):
                // el del cuerpo atravesaba el hoodie (2026-10-02, "el hoodie es
                // atravesado por los pechos, deberia apagarlos y reemplazarlos por
                // su propia geometria").
                if ((indiceManto(piezas) < 0 || mantoAbierto(piezas, origen))
                        && !com.modamod.render.relieve.BustoRender.debugSinBustoCuerpo) {
                    dibujarBusto(cuerpo, delJugador, infladoBustoCuerpo(piezas), 0f, 20f, true, null,
                            fondoSosten(perfilDe(entidad)), matrices, vertexConsumers, luz);
                }
                // La cola es de la cadera: con la misma piel que el torso de la cintura para abajo (dibujarCuerpoTorso). Sin
                // el cuerpo entero es la skin real, con lo que tenga pintado ahí (2026-11-08, "mapeada la skin base o la
                // prenda que hubiere en las caderas sobre el volumen del trasero": salía la bombacha del cuerpo elegido
                // sobre el short de la skin).
                boolean colaDelCuerpo = perfilOverride != null || perfilDe(entidad).siempre();
                if (entidad instanceof AbstractClientPlayerEntity conSkin && !colaDelCuerpo) {
                    dibujarCola(conSkin.getSkinTextures().texture(), delJugador, infladoColaCuerpo(piezas), 0f, 20f, true,
                            matrices, vertexConsumers, luz);
                } else {
                    dibujarCola(cuerpo, delJugador, infladoColaCuerpo(piezas), 0f, 20f, true,
                            techoBombacha(perfilDe(entidad)), matrices, vertexConsumers, luz);
                }
            }
            if (llevaCuerpo) {
                com.modamod.render.relieve.RelieveRender.actual =
                        new com.modamod.render.relieve.RelieveRender.Contexto(parte,
                                com.modamod.render.relieve.RelieveRender.cuerpoPlano ? com.modamod.render.relieve.MapaRelieve.PLANO
                                        : relieveDelCuerpoBajoTela(mapaCuerpo, parte, piezas));
                List<CuerpoGeometria.SegmentoCuerpo> segmentos = segmentosCuerpo(parte, piezas);
                if (conVolumen != null) {
                    dibujarModelPart(CuerpoGeometria.cuerpoConVolumenDePierna(parte, conVolumen.dilatacion(),
                                    conVolumen.filaDesde(), conVolumen.filaHasta(), conPuntaDeMedia(conVolumen, parte)),
                            CuerpoGeometria.Superficie.CUERPO, cuerpo, delJugador, matrices, vertexConsumers, luz);
                } else if (parte == Parte.TORSO) {
                    dibujarCuerpoTorso(entidad, slim, cuerpo, segmentos, torsoDelCinto, delJugador, matrices, vertexConsumers, luz);
                } else if (segmentos == null) {
                    dibujar(CuerpoGeometria.Superficie.CUERPO, parte, slim, cuerpo,
                            CuerpoGeometria.Superficie.CUERPO.dilatacion, delJugador, matrices, vertexConsumers, luz);
                } else {
                    ModelPart nuestra = CuerpoGeometria.cuerpoSegmentado(parte, slim, segmentos);
                    dibujarModelPart(nuestra, CuerpoGeometria.Superficie.CUERPO, cuerpo,
                            delJugador, matrices, vertexConsumers, luz);
                }
            }
            com.modamod.render.relieve.RelieveRender.actual = null;
            Perf.fin(Perf.Seccion.CUERPO, tCuerpo);
            if (piezas == null) continue;
            long tTela = Perf.ini();
            dibujarPiezas(parte, slim, piezas, conVolumen, delJugador, matrices, vertexConsumers, luz,
                    llevaCuerpo ? mapaCuerpo : com.modamod.render.relieve.MapaRelieve.PLANO, origen, profundidad);
            Perf.fin(Perf.Seccion.TELA, tTela);
        }

        // Cuello de polera, capucha y cordones del hoodie (2026-09-30).
        CuelloYCapucha.dibujar(prendas, biped, matrices, vertexConsumers, luz);
        // Apliques de la Mesa de estilado (2026-10-01).
        ApliqueRenderer.dibujar(prendas, biped, matrices, vertexConsumers, luz);
        dibujarTranslucidas();
        // La pollera pasa por fuera de la cola: el busto se apaga después.
        dibujarPollera(prendas, entidad, biped, matrices, vertexConsumers, luz, tickDelta);
        com.modamod.render.relieve.BustoRender.actual = null;
        dibujarCapa(prendas, entidad, biped, matrices, vertexConsumers, luz, tickDelta, true);
    }

    private static final Identifier POLLERA_BASE = Identifier.of("modamod",
            "textures/models/armor/pollera_tela.png");

    /**
     * La pollera se dibuja APARTE del loop de {@link Pieza} de arriba —
     * su geometría no es una caja por {@link Parte} como el resto (ver
     * {@link PolleraGeometria}, paneles en abanico), así que no encaja en
     * {@code CuerpoGeometria.parte(...)}. Ancla en el pivote del TORSO —
     * mismo lugar de donde cuelga la banda de cintura del pantalón — y
     * usa la dilatación de {@code Superficie.CUERPO} (escala 1) para el
     * factor de escala final: la geometría de la pollera ya está en
     * unidades naturales, no hace falta el truco de 1/8 que usa
     * {@code TELA} para prendas estampadas.
     *
     * <p>Sin balanceo por ahora (2026-09-16): se probó un balanceo tipo
     * capa sobre la versión anterior (tubo de aritos) y "no gustaba" —
     * "forma primero, balanceo después" otra vez, hasta confirmar que esta
     * forma de paneles en abanico se ve bien parada quieta.
     */
    private static void dibujarPollera(List<ItemStack> prendas, LivingEntity entidad, BipedEntityModel<?> biped,
                                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz,
                                       float tickDelta) {
        // Varias polleras (2026-10-07, "que las polleras rendericen abajo de
        // otras polleras", "por orden de slot"): la de slot más bajo queda
        // abajo y cada siguiente se infla SEPARACION_POLLERAS más.
        int nivel = 0;
        for (ItemStack s : prendas) {
            if (!(com.modamod.prenda.Prendas.es(s, com.modamod.prenda.CategoriaDePrenda.POLLERA))) continue;
            dibujarUnaPollera(s, nivel++, entidad, biped, matrices, vertexConsumers, luz, tickDelta);
        }
        // Faldón de malla de los Tops (2026-10-11, "las chaquetas de malla"): por fuera de las polleras.
        for (ItemStack s : prendas) {
            if (com.modamod.prenda.Prendas.es(s, com.modamod.prenda.CategoriaDePrenda.REMERA) && com.modamod.item.TopCorte.faldon(s) >= 0) {
                dibujarFaldon(s, nivel, entidad, biped, matrices, vertexConsumers, luz, tickDelta);
            }
        }
        // Delantal (2026-10-11): por fuera de todo lo demás.
        for (ItemStack s : prendas) {
            if (com.modamod.prenda.Prendas.es(s, com.modamod.prenda.CategoriaDePrenda.DELANTAL)) {
                dibujarDelantal(s, prendas, nivel, entidad, biped, matrices, vertexConsumers, luz, tickDelta);
            }
        }
    }

    /**
     * El faldón de un Top: una falda de malla que cuelga de la cintura (más arriba que la de la pollera), con el frente
     * abierto o cerrado como la chaqueta ({@code TopCorte.frente}, la tecla J). Usa la tela del torso del Top —así lleva
     * sus patrones, estampas y teñidos— tomando las filas de abajo de esa tela.
     */
    private static void dibujarFaldon(ItemStack stack, int polleras, LivingEntity entidad, BipedEntityModel<?> biped,
                                      MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz, float tickDelta) {
        vertexConsumers = EfectoTrim.envolver(stack, vertexConsumers);   // acabado de trim en toda la prenda (2026-10-22, "vamos con todo")
        Identifier tela = CuelloYCapucha.telaDelTorso(stack);
        if (tela == null) return;
        ModelPart delJugador = CuerpoGeometria.delJugador(biped, Parte.TORSO);
        if (!delJugador.visible) return;
        int filas = com.modamod.sublimadora.RemeraItem.variante(stack).largo().filas;
        int filasAbrigo = filas;
        // Su propia tela (2026-11-09): la del Top que sigue debajo de su última fila, con los patrones al mismo tamaño. Mientras no
        // está (una foto sin bajar) se estiran las filas de abajo del torso, como antes.
        Identifier propia = com.modamod.sublimadora.EstampaTextures.faldon(stack);
        float v0, v1;
        if (propia != null) {
            tela = propia;
            v0 = 20F;
            v1 = 20F + com.modamod.sublimadora.EstampaTextures.faldonPx(stack);
        } else {
            filas = Math.max(3, filas);
            v1 = 20F + filas - 0.25F;
            v0 = 20F + Math.max(0, Math.min(FALDON_CINTURA, filas - 3)) + 0.25F;
        }
        com.modamod.item.Calce calce = com.modamod.item.Calce.leer(stack);
        float dilatacion = Math.max(0F, com.modamod.item.Calce.dilatacionEfectiva(stack)) + calce.caida
                + 0.3F + polleras * SEPARACION_POLLERAS;
        // Arranca en el ruedo del abrigo con su ancho (2026-10-18, "el faldon de los abrigos no coincide muy bien con el abrigo
        // tanto en la coneccion del borde como en el ancho"); cada pollera de abajo suma su separación.
        // El borde que de verdad dibujó el torso en este cuadro (2026-11-09, "la joint es inconsistente en los distintos calces"):
        // los costados que dan a los brazos casi no se inflan; el Oversize de malla sigue saliendo de su malla.
        float[] borde = BORDE_TORSO.remove(stack);
        float[] ruedo = borde != null ? new float[]{borde[0] + 0.05F, borde[1] + 0.05F, borde[2]}
                : ruedoDelAbrigo(stack, calce, Math.max(1, filasAbrigo));
        float mitadX = ruedo[0] + polleras * SEPARACION_POLLERAS, mitadZ = ruedo[1] + polleras * SEPARACION_POLLERAS;
        float cinturaFaldon = ruedo[2];
        float holguraFaldon = mitadX - 4F;
        matrices.push();
        delJugador.rotate(matrices);
        VertexConsumer buffer = vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(tela));
        CapaMalla.Movimiento mov = entidad instanceof AbstractClientPlayerEntity jugador && previewOverride == null
                ? CapaMalla.movimiento(jugador, tickDelta) : CapaMalla.Movimiento.QUIETO;
        float twirl = previewOverride == null ? (entidad != null
                ? com.modamod.client.TwirlCliente.progreso(entidad, tickDelta) : twirlFigura) : -1f;
        float cola = com.modamod.render.relieve.BustoRender.actual == null ? 0f
                : com.modamod.render.relieve.BustoRender.actual.cola();
        // Por fuera de las polleras (2026-10-11, "el faldon tiene que colisionar con la pollera"): cada fila se aleja
        // hasta pasar la pollera de esa altura, con un margen para que no se toquen cuando la pollera se mueve.
        float[] extra = null;
        if (!GarmentFeatureRenderer.polleras.isEmpty()) {
            com.modamod.item.PolleraLargo largoFaldon = com.modamod.item.TopCorte.largoFaldon(stack);
            extra = new float[PolleraMalla.FILAS + 1];
            for (int f = 0; f <= PolleraMalla.FILAS; f++) {
                float y = cinturaFaldon + largoFaldon.pixeles * f / (float) PolleraMalla.FILAS;
                float hol = holguraPollera(y);
                extra[f] = hol < 0F ? 0F : Math.max(0F, hol + 0.5F - (holguraFaldon + 0.1F));
            }
        }
        faldones.add(new FaldonDibujado(com.modamod.item.TopCorte.largoFaldon(stack),
                com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.TORSO).volado(), mitadX, mitadZ, extra, cinturaFaldon));
        PolleraMalla.dibujarFaldon(matrices, buffer, luz, com.modamod.item.PolleraForma.TUBO,
                com.modamod.item.TopCorte.largoFaldon(stack),
                com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.TORSO), dilatacion,
                new PolleraMalla.Piernas(biped.body, biped.rightLeg, biped.leftLeg), mov, twirl, cola, delJugador.pitch,
                cinturaFaldon, v0, v1,
                // Con su tela, la apertura ya viene en la textura con el ancho del torso; sin ella, el hueco de siempre.
                propia == null && com.modamod.item.TopCorte.frente(stack) == com.modamod.item.ChaquetaFrente.ABIERTA ? 2 : 0,
                extra, mitadX, mitadZ, borde == null && calce == com.modamod.item.Calce.OVERSIZE);
        matrices.pop();
    }

    /**
     * Dónde termina el abrigo y cuánto mide ahí: {mitad en x, mitad en z, y bajo el hombro} de su ruedo, 0,05 por fuera para que no
     * se empaten. Con calce Oversize sale de la malla de Blockbench (el anillo de su última fila: con faldón el torso no cuelga ni
     * remata); si no, de la dilatación de la última fila. Los otros calces lo anota el torso al dibujarse ({@link #BORDE_TORSO}).
     */
    private static float[] ruedoDelAbrigo(ItemStack stack, com.modamod.item.Calce calce, int hasta) {
        float dil = com.modamod.item.Calce.dilatacionEfectiva(stack) + calce.caida;
        if (calce == com.modamod.item.Calce.OVERSIZE) {
            MallaOversize.Datos d = MallaOversize.de("torso");
            if (d != null) {
                int anillo = Math.min(hasta, d.bandas());
                float mx = 0F, mz = 0F, yBb = 24F - hasta;
                for (int i = 0; i < d.v().length; i++) {
                    if (d.anillo()[i] != anillo) continue;
                    mx = Math.max(mx, Math.abs(d.v()[i][0]));
                    mz = Math.max(mz, Math.abs(d.v()[i][2]));
                    yBb = d.v()[i][1];
                }
                return new float[]{mx + 0.05F, mz + 0.05F, 24F - yBb};
            }
        }
        return new float[]{4F + dil + 0.05F, 2F + dil + 0.05F, hasta};
    }

    /** Dónde terminó el torso de cada Top con faldón en este cuadro: {mitad en x, mitad en z, fila}. Lo anota el torso, lo usa el faldón. */
    private static final Map<ItemStack, float[]> BORDE_TORSO = new java.util.IdentityHashMap<>();

    /** Altura de la cintura del faldón (px bajo el hombro): más arriba que la cadera (9) de la pollera. */
    private static final int FALDON_CINTURA = 6;

    /** Cuánto más se infla cada pollera respecto de la de abajo (px). */
    private static final float SEPARACION_POLLERAS = 0.3F;

    private static void dibujarUnaPollera(ItemStack stack, int nivel, LivingEntity entidad, BipedEntityModel<?> biped,
                                          MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz,
                                          float tickDelta) {
        long t0 = com.modamod.render.Perf.ini();
        try {
            dibujarUnaPollera0(stack, nivel, entidad, biped, matrices, vertexConsumers, luz, tickDelta);
        } finally {
            com.modamod.render.Perf.fin(com.modamod.render.Perf.Seccion.POLLERA, t0);
        }
    }

    private static void dibujarUnaPollera0(ItemStack stack, int nivel, LivingEntity entidad, BipedEntityModel<?> biped,
                                          MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz,
                                          float tickDelta) {
        vertexConsumers = EfectoTrim.envolver(stack, vertexConsumers);   // acabado de trim en toda la prenda (2026-10-22, "vamos con todo")
        // La cintura de la pollera no puede apretar por dentro del cuerpo
        // (no es una Pieza: el cuerpo de abajo no se achica por ella) —
        // Ajustado pasó a -0.25 el 2026-09-30.
        float dilatacion = Math.max(0F, com.modamod.item.Calce.dilatacionEfectiva(stack)) + nivel * SEPARACION_POLLERAS;
        // La cola del que la lleva entra en las UV de la tela (2026-10-07, "minimizar la deformacion... atras").
        float colaTela = com.modamod.render.relieve.BustoRender.actual == null ? 0f
                : com.modamod.render.relieve.BustoRender.actual.cola();
        Identifier textura = texturaPollera(stack, PolleraMalla.pasoDeCola(colaTela));

        ModelPart delJugador = CuerpoGeometria.delJugador(biped, Parte.TORSO);
        if (!delJugador.visible) return;
        // Malla propia (2026-09-29, "resolveme la pollera que se ve horrible a
        // veces"): reemplaza a los gajos de PolleraGeometria. Se dibuja en el
        // marco del torso; las piernas van relativas al torso para que el
        // ruedo se abra donde pasan (ver PolleraMalla#chocarConPiernas).
        matrices.push();
        delJugador.rotate(matrices);
        VertexConsumer buffer = vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura));
        // Movimiento (2026-09-30, "habria que animarlas segun el movimiento"):
        // la misma inercia que usa la capa vanilla (ver CapaMalla#movimiento).
        CapaMalla.Movimiento mov = entidad instanceof AbstractClientPlayerEntity jugador && previewOverride == null
                ? CapaMalla.movimiento(jugador, tickDelta) : CapaMalla.Movimiento.QUIETO;
        float twirl = previewOverride == null ? (entidad != null
                ? com.modamod.client.TwirlCliente.progreso(entidad, tickDelta) : twirlFigura) : -1f;   // entidad null = Maniquí: sin twirl
        float cola = com.modamod.render.relieve.BustoRender.actual == null ? 0f
                : com.modamod.render.relieve.BustoRender.actual.cola();
        // Apliques de la pollera (2026-10-02): anclados por UV a la malla de este cuadro.
        List<com.modamod.aplique.Aplique> apliques = ApliqueRenderer.apliquesEn(stack, com.modamod.aplique.Aplique.Superficie.POLLERA);
        MallaCapturada malla = grabar("pollera", !apliques.isEmpty() || CorreaRenderer.hayEn(stack, com.modamod.correa.Correa.Superficie.POLLERA), () -> PolleraMalla.dibujar(matrices, buffer, luz,
                com.modamod.item.PolleraItem.forma(stack), com.modamod.item.PolleraItem.largo(stack),
                com.modamod.item.PolleraItem.voladoRuedo(stack), com.modamod.item.PolleraItem.voladoTodo(stack),
                com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.POLLERA), dilatacion, new PolleraMalla.Piernas(biped.body, biped.rightLeg, biped.leftLeg), mov,
                twirl, cola, delJugador.pitch, com.modamod.item.PolleraItem.cintura(stack)));
        matrices.pop();
        if (malla != null) { ApliqueRenderer.dibujarEnMalla(stack, com.modamod.aplique.Aplique.Superficie.POLLERA, malla, vertexConsumers, luz, matrices.peek().getNormalMatrix());
        CorreaRenderer.dibujarEnMalla(stack, com.modamod.correa.Correa.Superficie.POLLERA, malla, vertexConsumers, luz, matrices.peek().getNormalMatrix()); }
    }

    /**
     * La tela de la pollera ya teñida — separada de {@link #dibujarPollera}
     * (2026-09-29) para que el ícono del ítem ({@code IconoPrenda}) saque el
     * color del mismo lugar que la prenda puesta.
     */
    public static Identifier texturaPollera(ItemStack stack) {
        return texturaPollera(stack, 0);
    }

    /** Con la cola del que la lleva en pasos de media unidad ({@link PolleraMalla#pasoDeCola}). */
    public static Identifier texturaPollera(ItemStack stack, int pasoCola) {
        int colorBase = com.modamod.region.RegionResolver.colorBase(stack);
        // Tela nueva (2026-09-29, "teñirla y sublimarla"): pollera_tela.png
        // tiene el layout de la caja del TORSO (ver PolleraMalla), así que
        // los patrones salen de PatronGenerador("pollera") = caja de torso,
        // las zonas de Tintes son las del torso, y las fotos y la red se
        // pintan encima como en las demás prendas.
        java.util.List<com.modamod.region.RegionResolver.CapaPatron> capasPollera =
                com.modamod.region.RegionResolver.capas(stack);
        java.util.List<ClothingTextureCache.CapaMascara> capasMascaraPollera = new java.util.ArrayList<>(capasPollera.size());
        // Patrones desplegados según la forma y el largo (2026-10-01, ver PatronGenerador#desplegar).
        String clavePollera = com.modamod.render.PatronGenerador.clavePollera(
                com.modamod.item.PolleraItem.forma(stack), com.modamod.item.PolleraItem.largo(stack), pasoCola);
        for (com.modamod.region.RegionResolver.CapaPatron capa : capasPollera) {
            net.minecraft.client.texture.NativeImage mascara = com.modamod.render.PatronGenerador.mascaraDeCapa(clavePollera, capa);
            if (!capa.lisa() && mascara == null) continue;
            java.util.List<CajaSkin.Rect> region = capa.region() == com.modamod.region.RegionPintura.TODO ? null
                    : capa.region().rects(com.modamod.tinturas.TinturasBlockEntity.Categoria.POLLERA, CuerpoGeometria.ESCALA_TELA);
            capasMascaraPollera.add(ClothingTextureCache.CapaMascara.de(capa, mascara, region));
        }
        com.modamod.item.PatronRed red = com.modamod.item.PatronRed.leer(stack);
        boolean estampada = com.modamod.sublimadora.EstampaTextures.tieneEstampa(stack);
        ClothingTextureCache.Encima encima = !estampada && red == null ? null : new ClothingTextureCache.Encima() {
            @Override
            public String clave() {
                return "pollera_" + red + (estampada ? "_" + com.modamod.sublimadora.EstampaTextures.claveEstampas(stack) : "");
            }

            @Override
            public boolean aplicar(net.minecraft.client.texture.NativeImage destino) {
                boolean listo = !estampada || com.modamod.sublimadora.EstampaTextures.estampar(destino, stack);
                if (red != null) ClothingTextureCache.perforarRed(destino, red, Parte.TORSO, 0, 12);
                return listo;
            }
        };
        return ClothingTextureCache.composeGarmentCapas(POLLERA_BASE, colorBase, capasMascaraPollera,
                ClothingTextureCache.Shading.NONE, encima, true);
    }

    private static final Identifier DELANTAL_BASE = Identifier.of("modamod", "textures/models/armor/delantal_tela.png");

    /**
     * El delantal (2026-10-11, "un delantal que sea de malla"): el arco del frente de la malla de la pollera, del pecho
     * hacia abajo. Pasa por fuera de lo que lleve puesto: el top más holgado, las polleras (fila por fila) y el busto.
     */
    private static void dibujarDelantal(ItemStack stack, List<ItemStack> prendas, int nivel, LivingEntity entidad,
                                        BipedEntityModel<?> biped, MatrixStack matrices,
                                        VertexConsumerProvider vertexConsumers, int luz, float tickDelta) {
        vertexConsumers = EfectoTrim.envolver(stack, vertexConsumers);   // acabado de trim en toda la prenda (2026-10-22, "vamos con todo")
        ModelPart delJugador = CuerpoGeometria.delJugador(biped, Parte.TORSO);
        if (!delJugador.visible) return;
        float inicio = com.modamod.item.DelantalItem.inicio(stack), largo = com.modamod.item.DelantalItem.largo(stack);
        float otras = 0F;
        for (ItemStack p : prendas) {
            if (!(com.modamod.prenda.Prendas.es(p, com.modamod.prenda.CategoriaDePrenda.DELANTAL))) {
                otras = Math.max(otras, com.modamod.item.Calce.dilatacionEfectiva(p));
            }
        }
        float propia = Math.max(0F, com.modamod.item.Calce.dilatacionEfectiva(stack));
        float dilatacion = Math.max(propia, otras) + 0.1F + nivel * SEPARACION_POLLERAS + 0.1F;
        Identifier textura = texturaDelantal(stack);
        // Mientras gira, el delantal se apoya sobre las polleras y faldones abiertos por el giro (2026-11-11).
        float giroFaldas = previewOverride == null ? (entidad != null
                ? com.modamod.client.TwirlCliente.progreso(entidad, tickDelta) : twirlFigura) : -1f;
        float[] extra = new float[PolleraMalla.FILAS + 1];
        com.modamod.render.relieve.BustoRender.Busto busto = com.modamod.render.relieve.BustoRender.actual;
        for (int f = 0; f <= PolleraMalla.FILAS; f++) {
            float y = inicio + largo * f / (float) PolleraMalla.FILAS;
            float e = 0F;
            // Por fuera de las polleras y de los faldones (2026-11-11, el faldón del buzo atravesaba el delantal).
            float hol = holguraDeFaldas(y, giroFaldas);
            if (hol >= 0F) e = Math.max(0F, hol + 0.12F - (dilatacion + 0.1F));
            // El busto ya no aleja la fila entera (2026-11-09, "los delantales quedan muy gruesos con tetas"): el frente lo
            // esquiva punto por punto con PolleraMalla.frenteDelantal.
            extra[f] = e;
        }
        matrices.push();
        delJugador.rotate(matrices);
        VertexConsumer buffer = vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura));
        CapaMalla.Movimiento mov = entidad instanceof AbstractClientPlayerEntity jugador && previewOverride == null
                ? CapaMalla.movimiento(jugador, tickDelta) : CapaMalla.Movimiento.QUIETO;
        float twirl = previewOverride == null ? (entidad != null
                ? com.modamod.client.TwirlCliente.progreso(entidad, tickDelta) : twirlFigura) : -1f;
        float cola = busto == null ? 0f : busto.cola();
        List<com.modamod.aplique.Aplique> apliques = ApliqueRenderer.apliquesEn(stack, com.modamod.aplique.Aplique.Superficie.DELANTAL);
        float finalDilatacion = dilatacion;
        // Lo que sale el busto por delante de la tela del delantal, para la cinta y los tirantes de cajas.
        float bustoExtra = 0F;
        if (busto != null && busto.talle() * busto.sujecion() > 0.05F) {
            float infl = 0F;
            for (ItemStack p : prendas) {
                if (com.modamod.prenda.Prendas.es(p, com.modamod.prenda.CategoriaDePrenda.REMERA)) infl = Math.max(infl, com.modamod.item.Calce.dilatacionEfectiva(p));
            }
            com.modamod.render.relieve.BustoRender.Campo cb = campoDelBusto(busto, infl);
            for (float yy = 1F; yy <= 8F; yy += 0.5F) bustoExtra = Math.max(bustoExtra, cb.en(-2.6F, yy) + 0.15F - dilatacion);
            bustoExtra = Math.max(0F, bustoExtra);
        }
        boolean pieza = DelantalPieza.activa;
        boolean sinVolados = com.modamod.item.DelantalItem.voladoLateral(stack) == null
                && com.modamod.item.DelantalItem.voladoSuperior(stack) == null;
        PolleraMalla.AdornoDelantal adorno = sinVolados && !pieza ? null
                : new PolleraMalla.AdornoDelantal(com.modamod.item.DelantalItem.forma(stack), largo,
                        com.modamod.item.DelantalItem.voladoLateral(stack), com.modamod.item.DelantalItem.voladoSuperior(stack),
                        com.modamod.item.DelantalItem.cobertura(stack).ancho, pieza,
                        com.modamod.item.DelantalItem.tirantes(stack), finalDilatacion, bustoExtra);
        int columnas = com.modamod.item.DelantalItem.cobertura(stack).columnas();
        // El busto de la capa de más afuera (la remera más holgada; sin remera, la piel) que el delantal esquiva.
        if (busto != null && busto.talle() * busto.sujecion() > 0.05F) {
            float infladoRemera = 0F;
            for (ItemStack p : prendas) {
                if (com.modamod.prenda.Prendas.es(p, com.modamod.prenda.CategoriaDePrenda.REMERA)) {
                    infladoRemera = Math.max(infladoRemera, com.modamod.item.Calce.dilatacionEfectiva(p));
                }
            }
            PolleraMalla.frenteDelantal = campoDelBusto(busto, infladoRemera);
        }
        MallaCapturada malla;
        try {
            malla = grabar("delantal", !apliques.isEmpty()
                    || CorreaRenderer.hayEn(stack, com.modamod.correa.Correa.Superficie.DELANTAL), () ->
                    PolleraMalla.dibujarDelantal(matrices, buffer, luz, finalDilatacion,
                    new PolleraMalla.Piernas(biped.body, biped.rightLeg, biped.leftLeg), mov, twirl, cola, delJugador.pitch,
                    inicio, largo, com.modamod.item.DelantalItem.volado(stack),
                    com.modamod.item.Ruedos.get(stack, com.modamod.item.ZonaRuedo.DELANTAL), extra,
                    adorno, columnas));
        } finally {
            PolleraMalla.frenteDelantal = null;
        }
        matrices.pop();
        if (!pieza) TirantesDelantal.dibujar(com.modamod.item.DelantalItem.tirantes(stack),
                com.modamod.region.RegionResolver.colorBase(stack), inicio, finalDilatacion,
                bustoExtra, delJugador, matrices, vertexConsumers, luz);
        if (malla != null) {
            ApliqueRenderer.dibujarEnMalla(stack, com.modamod.aplique.Aplique.Superficie.DELANTAL, malla, vertexConsumers, luz, matrices.peek().getNormalMatrix());
            CorreaRenderer.dibujarEnMalla(stack, com.modamod.correa.Correa.Superficie.DELANTAL, malla, vertexConsumers, luz, matrices.peek().getNormalMatrix());
        }
    }

    /** La tela del delantal ya teñida y recortada a su silueta (también la usa el ícono). */
    public static Identifier texturaDelantal(ItemStack stack) {
        int colorBase = com.modamod.region.RegionResolver.colorBase(stack);
        java.util.List<com.modamod.region.RegionResolver.CapaPatron> capas =
                com.modamod.region.RegionResolver.capas(stack);
        java.util.List<ClothingTextureCache.CapaMascara> capasMascara = new java.util.ArrayList<>(capas.size());
        for (com.modamod.region.RegionResolver.CapaPatron capa : capas) {
            net.minecraft.client.texture.NativeImage mascara = com.modamod.render.PatronGenerador.mascaraDeCapa("delantal", capa);
            if (!capa.lisa() && mascara == null) continue;
            java.util.List<CajaSkin.Rect> region = capa.region() == com.modamod.region.RegionPintura.TODO ? null
                    : capa.region().rects(com.modamod.tinturas.TinturasBlockEntity.Categoria.DELANTAL, CuerpoGeometria.ESCALA_TELA);
            capasMascara.add(ClothingTextureCache.CapaMascara.de(capa, mascara, region));
        }
        com.modamod.item.DelantalForma forma = com.modamod.item.DelantalItem.forma(stack);
        float largo = com.modamod.item.DelantalItem.largo(stack);
        boolean redondas = com.modamod.item.DelantalItem.esquinasRedondas(stack);
        com.modamod.item.DelantalCobertura cobertura = com.modamod.item.DelantalItem.cobertura(stack);
        com.modamod.item.PatronRed red = com.modamod.item.PatronRed.leer(stack);
        boolean estampado = com.modamod.sublimadora.EstampaTextures.tieneEstampa(stack);
        ClothingTextureCache.Encima encima = new ClothingTextureCache.Encima() {
            @Override
            public String clave() {
                return "delantal_" + (estampado ? com.modamod.sublimadora.EstampaTextures.claveEstampas(stack) + "_" : "") + forma + "_" + Math.round(largo * 2) + (redondas ? "r" : "") + "_" + cobertura + "_" + red;
            }

            @Override
            public boolean aplicar(net.minecraft.client.texture.NativeImage destino) {
                if (estampado) com.modamod.sublimadora.EstampaTextures.estampar(destino, stack);
                if (red != null) ClothingTextureCache.perforarRed(destino, red, Parte.TORSO, 0, 12);
                recortarDelantal(destino, forma, largo, redondas, cobertura.ancho);
                return true;
            }
        };
        return ClothingTextureCache.composeGarmentCapas(DELANTAL_BASE, colorBase, capasMascara,
                ClothingTextureCache.Shading.NONE, encima, true);
    }

    /**
     * Borra los texeles que quedan fuera de la silueta (la tira del torso: u 16..40, v 20 hacia abajo; el centro del
     * frente está en u 24 y el borde de arriba del paño en v 20). Funciona a cualquier escala de la textura.
     */
    private static void recortarDelantal(net.minecraft.client.texture.NativeImage img, com.modamod.item.DelantalForma forma,
                                         float largo, boolean redondas, float cap) {
        float escala = img.getWidth() / 64f;
        for (int y = 0; y < img.getHeight(); y++) {
            float yy = (y + 0.5f) / escala - 20f;
            for (int x = 0; x < img.getWidth(); x++) {
                float s = (x + 0.5f) / escala - 24f;
                if (!forma.contiene(s, yy, largo, redondas, cap)) img.setColor(x, y, 0);
            }
        }
    }

    private static final Identifier CAPA_BASE = Identifier.of("modamod",
            "textures/models/armor/capa_tela.png");

    /** La capa del mod puesta en el slot espalda/capa, o null. */
    @Nullable
    public static ItemStack capaDe(List<ItemStack> prendas) {
        for (ItemStack s : prendas) if (com.modamod.prenda.Prendas.es(s, com.modamod.prenda.CategoriaDePrenda.CAPA)) return s;
        return null;
    }

    /**
     * La capa (2026-09-29, "capas... como las capas vanilla (misma dinamica
     * de tela) pero usable y personalizable"): mismo marco y mismos giros que
     * {@code CapeFeatureRenderer} (ver {@link CapaMalla#giros}), así que
     * flamea igual que la vanilla. Con élitros puestos se dibuja igual y los
     * tapa (2026-11-08, {@code ElytraFeatureRendererMixin}); la capa vanilla se oculta mientras esté esta
     * ({@code CapeFeatureRendererMixin}).
     */
    private static void dibujarCapa(List<ItemStack> prendas, LivingEntity entidad, BipedEntityModel<?> biped,
                                    MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int luz, float tickDelta,
                                    boolean translucidas) {
        long t0 = com.modamod.render.Perf.ini();
        try {
            dibujarCapa0(prendas, entidad, biped, matrices, vertexConsumers, luz, tickDelta, translucidas);
        } finally {
            com.modamod.render.Perf.fin(com.modamod.render.Perf.Seccion.CAPA, t0);
        }
    }

    private static void dibujarCapa0(List<ItemStack> prendas, LivingEntity entidad, BipedEntityModel<?> biped,
                                    MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int luz, float tickDelta,
                                    boolean translucidas) {
        // Sin entidad es la figura del maniqui (2026-10-19): la capa cuelga quieta, sin inercia ni agacharse.
        AbstractClientPlayerEntity jugador = entidad instanceof AbstractClientPlayerEntity j ? j : null;
        if (jugador == null && entidad != null) return;
        ItemStack stack = capaDe(prendas);
        if (stack != null) vertexConsumers = EfectoTrim.envolver(stack, vertexConsumers);   // acabado de trim (2026-10-22, "vamos con todo")
        if (stack == null || (jugador != null && jugador.isInvisible())) return;

        Identifier textura = texturaCapa(stack);
        // Translúcida (2026-10-01): va al final, después del cuerpo — si se
        // dibuja antes, su profundidad tapa al cuerpo que queda detrás.
        if (ClothingTextureCache.esTranslucida(textura) != translucidas) return;
        VertexConsumer buffer = vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura));
        boolean agachado = jugador != null && jugador.isInSneakingPose();
        // Tela (2026-09-30, "es una placa tiesa"): sin giros de matriz; CapaMalla
        // dobla el paño tramo por tramo y lo hace chocar con las piernas.
        float sy = agachado ? 1.85f : 0f, sz = 2f + (agachado ? 1.4f : 0f);
        float alejar = separacionCapa(prendas, true);
        float mangas = separacionBrazos(prendas);
        // El busto, para que el embozo pase por encima; en la pasada translúcida ya se apagó: se pide sin física.
        com.modamod.render.relieve.BustoRender.Busto bustoAhora = com.modamod.render.relieve.BustoRender.actual != null
                ? com.modamod.render.relieve.BustoRender.actual : bustoDe(entidad, tickDelta, false);
        java.util.function.BiFunction<Float, Float, com.modamod.render.relieve.BustoRender.Punto> bustoEmbozo =
                bustoAhora == null || com.modamod.item.CapaItem.embozo(stack) < 0 ? null
                        : com.modamod.render.relieve.BustoRender.superficie(bustoAhora, separacionCapa(prendas, false));
        org.joml.Matrix4f marcoPano = new org.joml.Matrix4f().translation(0f, sy, sz + alejar);
        PolleraMalla.Piernas piernas = new PolleraMalla.Piernas(marcoPano, biped.rightLeg, biped.leftLeg)
                // Brazos a ras de la manga (la caja de la pierna ya es 0,55 más ancha que el brazo, y el paño se aleja lo
                // que infla el torso): quietos no tocan el paño y no lo abren; balanceándose, lo empujan.
                .conBrazos(PolleraMalla.Piernas.brazos(marcoPano, biped.rightArm, biped.leftArm, mangas - 0.7f));
        // Apliques de la capa (2026-10-02): anclados por UV a la malla de este cuadro.
        List<com.modamod.aplique.Aplique> apliques = ApliqueRenderer.apliquesEn(stack, com.modamod.aplique.Aplique.Superficie.CAPA);
        boolean hoodie = conHoodie(prendas);
        // Embozo sobre los hombros (2026-10-11): en el marco del torso.
        Runnable embozo = () -> {
            ModelPart torso = CuerpoGeometria.delJugador(biped, Parte.TORSO);
            matrices.push();
            torso.rotate(matrices);
            // Choca con piernas y brazos como la pollera (2026-10-23).
            CapaMalla.dibujarEmbozo(matrices, buffer, luz, com.modamod.item.CapaItem.profundidadEmbozo(stack),
                    com.modamod.item.CapaItem.bordeEmbozo(stack), com.modamod.item.CapaItem.embozoAbierto(stack),
                    separacionCapa(prendas, false), bustoEmbozo,
                    new PolleraMalla.Piernas(torso, biped.rightLeg, biped.leftLeg),
                    PolleraMalla.Piernas.brazos(torso, biped.rightArm, biped.leftArm,
                            Math.max(0.3f, mangas - 0.25f)));
            matrices.pop();
        };
        MallaCapturada malla = grabar("capa", !apliques.isEmpty() || CorreaRenderer.hayEn(stack, com.modamod.correa.Correa.Superficie.CAPA), () -> {
            // Con el embozo largo, el embozo es la capa: el paño no se dibuja (2026-10-23, "cuando este el emboso largo sacamos la capa"),
            // y los apliques y correas de la capa van sobre el embozo, que usa la misma UV del exterior (2026-10-24, "apliques en las capas?").
            if (com.modamod.item.CapaItem.embozoLargo(stack)) embozo.run();
            else {
                matrices.push();
                matrices.translate(0f, sy / 16f, (sz + alejar) / 16f);
                CapaMalla.dibujarPano(matrices, buffer, luz, stack,
                        jugador != null ? CapaMalla.movimiento(jugador, tickDelta) : CapaMalla.Movimiento.QUIETO, agachado,
                        jugador != null ? jugador.age + tickDelta : (float) (System.nanoTime() / 50_000_000.0), piernas, hoodie,
                        jugador == null || (!jugador.hasVehicle() && !jugador.isSwimming() && !jugador.isSleeping() && !jugador.isCrawling()));
                matrices.pop();
            }

            // Con hoodie, la capucha del hoodie manda: la capa no lleva cuello alto (2026-10-02).
            if (com.modamod.item.CapaItem.cuelloAlto(stack) && !hoodie) {
                matrices.push();
                matrices.translate(0f, 0f, 0.125f);
                if (agachado) matrices.translate(0f, 1.85f / 16f, 1.4f / 16f);
                // Sin el giro de 180° (2026-10-23, "el cuello largo que esta la comba dibujada al reves"): con el giro la
                // media vuelta quedaba centrada en la nuca y se curvaba hacia la cara; así rodea la nuca por atrás.
                CapaMalla.dibujarCuello(matrices, buffer, luz);
                matrices.pop();
            }
        });
        // Embozo corto: fuera de la malla de apliques (comparte la UV del exterior con el paño).
        if (com.modamod.item.CapaItem.embozo(stack) >= 0 && !com.modamod.item.CapaItem.embozoLargo(stack)) embozo.run();
        if (malla != null) { ApliqueRenderer.dibujarEnMalla(stack, com.modamod.aplique.Aplique.Superficie.CAPA, malla, vertexConsumers, luz, matrices.peek().getNormalMatrix());
        CorreaRenderer.dibujarEnMalla(stack, com.modamod.correa.Correa.Superficie.CAPA, malla, vertexConsumers, luz, matrices.peek().getNormalMatrix()); }
    }

    /**
     * Los apliques de lo que {@code entidad} tiene puesto y no es ropa del mod
     * (2026-10-02, "extender apliques para toda armadura o wearable vanilla o
     * de mods"): los 4 slots de armadura y lo que lleve en Trinkets. Van sobre
     * la parte del cuerpo donde se pusieron, por fuera lo que sale ese slot.
     */
    private static void dibujarApliquesDeVestibles(LivingEntity entidad, BipedEntityModel<?> biped, MatrixStack matrices,
                                                   VertexConsumerProvider vertexConsumers, int luz) {
        for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
            if (slot.getType() != net.minecraft.entity.EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack puesto = entidad.getEquippedStack(slot);
            if (puesto.isEmpty() || com.modamod.prenda.Prendas.esRopaDeCuerpo(puesto)) continue;
            ApliqueRenderer.dibujar(puesto, ApliqueRenderer.dilatacionDeSlot(slot), biped, matrices, vertexConsumers, luz);
        }
        TrinketsApi.getTrinketComponent(entidad).ifPresent(c -> {
            for (var par : c.getAllEquipped()) {
                ItemStack puesto = par.getRight();
                // Los del sombrero los dibuja SombreroRenderer (también en la vista previa y el Maniquí).
                if (puesto.isEmpty() || com.modamod.prenda.Prendas.esRopaDeCuerpo(puesto) || AccesorioRenderer.es(puesto)) continue;
                ApliqueRenderer.dibujar(puesto, ApliqueRenderer.dilatacionDeSlot(null), biped, matrices, vertexConsumers, luz);
            }
        });
    }

    /**
     * Los accesorios de cajas puestos en sus slots de Trinkets (2026-10-04 el sombrero de bruja; 2026-10-05 la banda:
     * cinto y choker). {@code sombreroOverride} (el nombre quedó del sombrero) es el que muestra la Mesa de estilado,
     * el Guardarropas y la Modeladora aunque no esté puesto.
     */
    /** ¿Hay alguna capucha (de Top o de capa) puesta en la cabeza? */
    public static boolean capuchaPuesta(List<ItemStack> prendas) {
        for (ItemStack s : prendas) {
            boolean capucha = com.modamod.prenda.Prendas.es(s, com.modamod.prenda.CategoriaDePrenda.CAPA)
                    ? com.modamod.item.CapaItem.capucha(s) : com.modamod.item.TopCorte.tieneCapucha(s);
            if (capucha && com.modamod.item.TopCorte.capuchaArriba(s)) return true;
        }
        return false;
    }

    private static void dibujarSombrero(LivingEntity entidad, BipedEntityModel<?> biped, MatrixStack matrices,
                                        VertexConsumerProvider vertexConsumers, int luz, float dil, List<ItemStack> prendas) {
        // La piel de quien lleva el calzado, para el pie de las sandalias y ojotas (2026-10-14).
        BorcegosRenderer.pielActual = entidad instanceof net.minecraft.client.network.AbstractClientPlayerEntity jugador
                ? jugador.getSkinTextures().texture() : null;
        // Las medias que llegan hasta el pie lo envuelven (2026-10-18): el pie generado usa su tela.
        BorcegosRenderer.mediaIzq = BorcegosRenderer.mediaDer = null;
        for (ItemStack s : prendas) {
            if (s.getItem() != com.modamod.item.ModamodItems.MEDIAS || com.modamod.item.PatronRed.leer(s) != null) continue;   // la red tiene agujeros
            for (Pieza pz : PiezasDePrenda.de(s, entidad)) {
                if (pz.capa() != Capa.MEDIA || pz.filaHasta() < 12) continue;
                if (pz.parte() == Parte.PIERNA_IZQ) BorcegosRenderer.mediaIzq = pz.textura();
                else if (pz.parte() == Parte.PIERNA_DER) BorcegosRenderer.mediaDer = pz.textura();
            }
        }
        // Con la capucha puesta el sombrero va por fuera de ella (2026-11-11, "la capucha con sombrero tiene atravesamientos").
        SombreroRenderer.sobreCapucha = capuchaPuesta(prendas);
        try {
            dibujarAccesorios(entidad, biped, matrices, vertexConsumers, luz, dil);
        } finally {
            BorcegosRenderer.mediaIzq = BorcegosRenderer.mediaDer = null;
            SombreroRenderer.sobreCapucha = false;
        }
    }

    private static void dibujarAccesorios(LivingEntity entidad, BipedEntityModel<?> biped, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int luz, float dil) {
        if (sombreroOverride != null || calzadoOverride != null) {
            if (sombreroOverride != null) AccesorioRenderer.dibujar(sombreroOverride, biped, matrices, vertexConsumers, luz, dil);
            if (calzadoOverride != null) AccesorioRenderer.dibujar(calzadoOverride, biped, matrices, vertexConsumers, luz, dil);
            return;
        }
        TrinketsApi.getTrinketComponent(entidad).ifPresent(c -> {
            for (var par : c.getAllEquipped()) {
                ItemStack puesto = par.getRight();
                // La máscara y el barbijo los dibuja CaraRenderer (con sus apliques).
                if (AccesorioRenderer.es(puesto) && !(puesto.getItem() instanceof com.modamod.item.CaraItem)) AccesorioRenderer.dibujar(puesto, biped, matrices, vertexConsumers, luz, dil);
            }
        });
    }

    /** Cuánto sale del torso la ropa en la cintura (2026-10-05): el cinto va por encima, sin hundirse en un hoodie oversize. */
    public static float holguraDeCinto(List<ItemStack> prendas) {
        float m = 0f;
        int polleras = 0;
        boolean delantal = false;
        for (ItemStack p : prendas) {
            m = Math.max(m, com.modamod.item.Calce.dilatacionEfectiva(p));
            if (com.modamod.prenda.Prendas.es(p, com.modamod.prenda.CategoriaDePrenda.POLLERA)) polleras++;
            if (com.modamod.prenda.Prendas.es(p, com.modamod.prenda.CategoriaDePrenda.DELANTAL)) delantal = true;
        }
        float holgura = Math.max(BandaRenderer.DIL_MESA, m * 1.4f + 0.25f);
        // También por encima del delantal (2026-11-11, "el cinto tiene atravesamientos"): el delantal va a la holgura de la
        // ropa más 0,2 y la separación de cada pollera, y además pasa por fuera de polleras y faldones (dibujarDelantal).
        if (delantal) {
            float base = Math.max(0f, m) + 0.2f + polleras * SEPARACION_POLLERAS + 0.1f;
            for (float y = 7.6f; y <= 8.7f; y += 0.5f) {
                float faldas = -1f;
                int i = 0;
                for (ItemStack s : prendas) {
                    if (com.modamod.prenda.Prendas.es(s, com.modamod.prenda.CategoriaDePrenda.POLLERA)) {
                        faldas = Math.max(faldas, PolleraMalla.holguraEn(com.modamod.item.PolleraItem.forma(s), com.modamod.item.PolleraItem.largo(s),
                                com.modamod.item.PolleraItem.voladoRuedo(s), com.modamod.item.PolleraItem.voladoTodo(s),
                                Math.max(0F, com.modamod.item.Calce.dilatacionEfectiva(s)) + i++ * SEPARACION_POLLERAS, y, com.modamod.item.PolleraItem.cintura(s)));
                    } else if (com.modamod.prenda.Prendas.es(s, com.modamod.prenda.CategoriaDePrenda.REMERA) && com.modamod.item.TopCorte.faldon(s) >= 0) {
                        // El faldón, por su ruedo de fábrica (el que de verdad dibuja el torso se sabe recién al dibujarlo).
                        float[] r = ruedoDelAbrigo(s, com.modamod.item.Calce.leer(s),
                                Math.max(1, com.modamod.sublimadora.RemeraItem.variante(s).largo().filas));
                        faldas = Math.max(faldas, PolleraMalla.holguraEnFaldon(com.modamod.item.TopCorte.largoFaldon(s),
                                com.modamod.item.Ruedos.get(s, com.modamod.item.ZonaRuedo.TORSO).volado(),
                                r[0] + polleras * SEPARACION_POLLERAS, r[1] + polleras * SEPARACION_POLLERAS, null, y, r[2], -1f) + 0.5f);
                    }
                }
                holgura = Math.max(holgura, Math.max(base, faldas + 0.12f) + 0.25f);
            }
        }
        return holgura;
    }

    /** ¿Hay un hoodie (chaqueta con capucha) entre las prendas? */
    private static boolean conHoodie(List<ItemStack> prendas) {
        for (ItemStack s : prendas) if (com.modamod.item.TopCorte.tieneCapucha(s)) return true;
        return false;
    }

    /**
     * Cuánto más atrás va la capa (px) para pasar por fuera de lo que hay en
     * la espalda (2026-10-02, "capa se solapa", → "capa por fuera"): la tela
     * más holgada del torso (con su caída hacia el ruedo) y, con un hoodie de
     * capucha caída, la bolsa de la capucha.
     */
    /**
     * @param conCapuchaCaida sumar la bolsa de la capucha caída de la chaqueta; el embozo no la suma (2026-10-23, "cuando
     *                        se pliega la capucha con el embozo el embozo se pone raro"): la capucha va por fuera de él.
     */
    /**
     * Cuánto sale la manga más inflada del brazo (px), como {@link #separacionCapa} con el torso (2026-10-25, en las
     * capturas las mangas oversize del buzo atravesaban el embozo: el choque usaba el brazo pelado).
     */
    static float separacionBrazos(List<ItemStack> prendas) {
        float extra = armaduraPecho;
        for (ItemStack s : prendas) {
            for (Pieza p : PiezasDePrenda.de(s, null)) {
                if (p.parte() != Parte.BRAZO_DER && p.parte() != Parte.BRAZO_IZQ) continue;
                com.modamod.item.Calce calce = com.modamod.item.Calce.de(p.dilatacion());
                float caida = calce == null ? 0f : calce.caida;
                extra = Math.max(extra, Math.max(0f, p.dilatacion()) + caida * 0.6f + 0.1f);
            }
        }
        return extra;
    }

    /** Lo que infla la pechera y el casco que se ven en la entidad que se está dibujando (px; 0 sin armadura). */
    static float armaduraPecho, armaduraCasco, armaduraPiernas, armaduraPies;

    /** Desde qué profundidad las prendas van por encima de la armadura (la fila de la armadura); MAX_VALUE: ninguna. */
    private static int profundidadArmadura = Integer.MAX_VALUE;

    /** Lo que sale del cuerpo la armadura que se ve en la fila {@code f} (0..11) de {@code parte}, o 0. */
    private static float armaduraEn(Parte parte, int f) {
        return switch (parte) {
            case CABEZA -> armaduraCasco;
            case BRAZO_DER, BRAZO_IZQ -> armaduraPecho;
            case TORSO -> Math.max(armaduraPecho, f >= FILA_CINTURON ? armaduraPiernas : 0f);
            case PIERNA_DER, PIERNA_IZQ -> Math.max(armaduraPiernas, f >= FILA_BOTAS ? armaduraPies : 0f);
        };
    }

    /** Desde qué fila del torso llega el pantalón de la armadura y desde cuál de la pierna, las botas. */
    private static final int FILA_CINTURON = 9, FILA_BOTAS = 7;

    /**
     * La armadura que se ve en un dibujo: lo que infla cada pieza (px; solo las armaduras, {@code ArmorItem}: la élitra y lo
     * demás no inflan) y cuántas de las prendas de dibujo, las últimas, van por encima de ella (la fila de la armadura).
     */
    public record ArmaduraVisible(float casco, float pecho, float piernas, float pies, int prendasEncima) {
        public static final ArmaduraVisible NINGUNA = new ArmaduraVisible(0f, 0f, 0f, 0f, 0);

        public static ArmaduraVisible de(java.util.function.Function<net.minecraft.entity.EquipmentSlot, ItemStack> pieza, int prendasEncima) {
            return new ArmaduraVisible(infla(pieza, net.minecraft.entity.EquipmentSlot.HEAD), infla(pieza, net.minecraft.entity.EquipmentSlot.CHEST),
                    infla(pieza, net.minecraft.entity.EquipmentSlot.LEGS), infla(pieza, net.minecraft.entity.EquipmentSlot.FEET), prendasEncima);
        }

        private static float infla(java.util.function.Function<net.minecraft.entity.EquipmentSlot, ItemStack> pieza,
                                   net.minecraft.entity.EquipmentSlot slot) {
            return pieza.apply(slot).getItem() instanceof net.minecraft.item.ArmorItem ? ApliqueRenderer.dilatacionDeSlot(slot) : 0f;
        }
    }

    private static void usarArmadura(ArmaduraVisible a, int prendas) {
        armaduraCasco = a.casco();
        armaduraPecho = a.pecho();
        armaduraPiernas = a.piernas();
        armaduraPies = a.pies();
        profundidadArmadura = a.prendasEncima() > 0 ? prendas - a.prendasEncima() : Integer.MAX_VALUE;
    }

    /** La pieza que SE VE en {@code slot}: la cosmética o nada si la cambia u oculta, si no la puesta. */
    private static ItemStack piezaVisible(@Nullable LivingEntity entidad, net.minecraft.entity.EquipmentSlot slot) {
        if (entidad == null) return ItemStack.EMPTY;
        ItemStack s = entidad instanceof net.minecraft.entity.player.PlayerEntity j ? com.modamod.ropa.Cosmeticos.visual(j, slot) : null;
        return s != null ? s : entidad.getEquippedStack(slot);
    }

    static float separacionCapa(List<ItemStack> prendas, boolean conCapuchaCaida) {
        float extra = 0f;
        for (ItemStack s : prendas) {
            for (Pieza p : PiezasDePrenda.de(s, null)) {
                if (p.parte() != Parte.TORSO) continue;
                com.modamod.item.Calce calce = com.modamod.item.Calce.de(p.dilatacion());
                float caida = calce == null ? 0f : calce.caida;
                extra = Math.max(extra, Math.max(0f, p.dilatacion()) + caida * 0.6f + 0.1f);
            }
            if (conCapuchaCaida && com.modamod.item.TopCorte.tieneCapucha(s) && !com.modamod.item.TopCorte.capuchaArriba(s)) {
                // La bolsa de la capucha caída (CuelloYCapucha.capuchaCaida): 2.4 de grosor desde 2 + d + 0.2.
                extra = Math.max(extra, Math.max(0f, com.modamod.item.Calce.dilatacionEfectiva(s)) + 4.0f);
            }
        }
        return extra + armaduraPecho;
    }

    /** La tela de la capa ya teñida y estampada (también la usa el ícono). */
    public static Identifier texturaCapa(ItemStack stack) {
        int colorBase = com.modamod.region.RegionResolver.colorBase(stack);
        java.util.List<com.modamod.region.RegionResolver.CapaPatron> capas =
                com.modamod.region.RegionResolver.capas(stack);
        java.util.List<ClothingTextureCache.CapaMascara> mascaras = new java.util.ArrayList<>(capas.size());
        for (com.modamod.region.RegionResolver.CapaPatron capa : capas) {
            net.minecraft.client.texture.NativeImage mascara = PatronGenerador.mascaraDeCapa("capa", capa);
            if (!capa.lisa() && mascara == null) continue;
            java.util.List<CajaSkin.Rect> region = capa.region() == com.modamod.region.RegionPintura.TODO ? null
                    : capa.region().rects(com.modamod.tinturas.TinturasBlockEntity.Categoria.CAPA, CuerpoGeometria.ESCALA_TELA);
            mascaras.add(ClothingTextureCache.CapaMascara.de(capa, mascara, region));
        }
        boolean estampada = com.modamod.sublimadora.EstampaTextures.tieneEstampa(stack);
        ClothingTextureCache.Encima encima = !estampada ? null : new ClothingTextureCache.Encima() {
            @Override
            public String clave() {
                return "capa_" + com.modamod.sublimadora.EstampaTextures.claveEstampas(stack);
            }

            @Override
            public boolean aplicar(net.minecraft.client.texture.NativeImage destino) {
                return com.modamod.sublimadora.EstampaTextures.estampar(destino, stack);
            }
        };
        return ClothingTextureCache.composeGarmentCapas(CAPA_BASE, colorBase, mascaras,
                ClothingTextureCache.Shading.NONE, encima);
    }

    /**
     * Un anillo de tela en la boca de una pieza (2026-10-02, "manga inferior
     * abierta sin planos"): en la fila {@code y} (px de la parte), entre el
     * contorno de la tela (holgura {@code dil}, con el lado interno en
     * {@code interno}) y el del cuerpo. Cada lado lleva el color de la última
     * fila con tela ({@code filaTextura}) de su cara.
     */
    private static void dibujarBoca(Parte parte, boolean slim, Identifier textura, float dil, float interno,
                                    int filaTextura, float y, ModelPart delModelo, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int luz) {
        dibujarBoca(parte, slim, textura, dil, 0F, interno, filaTextura, y, delModelo, matrices, vertexConsumers, luz);
    }

    /** Con el contorno de adentro en {@code dilInterior} (un escalón entre dos filas) y no pegado al cuerpo. */
    private static void dibujarBoca(Parte parte, boolean slim, Identifier textura, float dil, float dilInterior, float interno,
                                    int filaTextura, float y, ModelPart delModelo, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int luz) {
        dibujarBoca(parte, slim, textura, dil, dilInterior, interno, filaTextura, y, 0F, delModelo, matrices, vertexConsumers, luz);
    }

    /**
     * La ley del escalonado (2026-11-04, "debería haber alguna ley en todas las prendas sobre completar el escalonado"):
     * con {@code hacia} distinto de 0 esto es el anillo de un ESCALÓN entre dos filas, no la boca de la prenda contra el
     * cuerpo. Dos diferencias: se dibuja por chica que sea la diferencia (una boca de menos de 0,02 px no se ve, pero un
     * escalón de 0,03 px deja pasar la luz igual — "cortes muy pequeños incluso en partes no escalonadas"), y la normal
     * apunta hacia donde mira el anillo ({@code +1} abajo, {@code -1} arriba; en el modelo +y es hacia abajo), así no
     * queda iluminado al revés. El contorno de adentro vale negativo (una fila Ajustado está por dentro de la piel).
     */
    private static void dibujarBoca(Parte parte, boolean slim, Identifier textura, float dil, float dilInterior, float interno,
                                    int filaTextura, float y, float hacia, ModelPart delModelo, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int luz) {
        boolean escalon = hacia != 0F;
        if (escalon ? dil - dilInterior <= 0.002F : (dil <= 0.02F || dil - dilInterior <= 0.02F)) return;
        float[] c = ApliqueRenderer.caja(parte, slim);
        float x0 = c[0], y0 = c[1], z0 = c[2], w = c[3], d = c[5];
        float[] cx = CuerpoGeometria.costadosX(parte, dil, interno);
        float ox0 = x0 - cx[0], ox1 = x0 + w + cx[1], oz0 = z0 - dil, oz1 = z0 + d + dil;
        float ix0 = x0, ix1 = x0 + w, iz0 = z0, iz1 = z0 + d;
        if (escalon || dilInterior > 0F) {
            float[] ci = CuerpoGeometria.costadosX(parte, dilInterior, interno);
            ix0 = x0 - ci[0];
            ix1 = x0 + w + ci[1];
            iz0 = z0 - dilInterior;
            iz1 = z0 + d + dilInterior;
        }
        float yy = (y0 + y) / 16F;
        // Origen de la caja en la skin (64x64) y la v de la fila de la textura.
        int[] uv = switch (parte) {
            case TORSO -> new int[]{16, 16};
            case BRAZO_DER -> new int[]{40, 16};
            case BRAZO_IZQ -> new int[]{32, 48};
            case PIERNA_DER -> new int[]{0, 16};
            case PIERNA_IZQ -> new int[]{16, 48};
            default -> new int[]{0, 0};
        };
        int ancho = Math.round(w), hondo = Math.round(d);
        float v = (uv[1] + hondo + Math.max(0, Math.min(11, filaTextura)) + 0.5F) / 64F;
        // Medio pixel para adentro de cada cara (2026-11-04, "la media se abre en la arista de atrás a la derecha"): el
        // anillo llegaba justo al borde de la franja de la parte, y el píxel de al lado ya es de otra prenda — en la
        // pierna derecha, la costura donde la textura da la vuelta cae justo en esa arista, así que el anillo leía un
        // texel transparente y dejaba una rayita abierta en cada fila.
        final float EPS_U = 0.5F;
        float uFrente = uv[0] + hondo, uDer = uv[0] + hondo + ancho, uAtras = uv[0] + 2 * hondo + ancho, uIzq = uv[0];
        matrices.push();
        delModelo.rotate(matrices);
        MatrixStack.Entry e = matrices.peek();
        VertexConsumer vc = vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura));
        float ny = hacia < 0F ? -1F : 1F;
        // Frente (z mínimo), espalda (z máximo) y los dos costados: trapecios entre los dos contornos.
        bocaQuad(vc, e, luz, v, ox0, oz0, ox1, oz0, ix1, iz0, ix0, iz0, yy, uFrente + EPS_U, uFrente + ancho - EPS_U, ny);
        bocaQuad(vc, e, luz, v, ox1, oz1, ox0, oz1, ix0, iz1, ix1, iz1, yy, uAtras + EPS_U, uAtras + ancho - EPS_U, ny);
        bocaQuad(vc, e, luz, v, ox1, oz0, ox1, oz1, ix1, iz1, ix1, iz0, yy, uDer + EPS_U, uDer + hondo - EPS_U, ny);
        bocaQuad(vc, e, luz, v, ox0, oz1, ox0, oz0, ix0, iz0, ix0, iz1, yy, uIzq + EPS_U, uIzq + hondo - EPS_U, ny);
        matrices.pop();
    }

    private static void bocaQuad(VertexConsumer vc, MatrixStack.Entry e, int luz, float v,
                                 float ax, float az, float bx, float bz, float cx, float cz, float dx, float dz,
                                 float y, float u0, float u1) {
        bocaQuad(vc, e, luz, v, ax, az, bx, bz, cx, cz, dx, dz, y, u0, u1, 1F);
    }

    private static void bocaQuad(VertexConsumer vc, MatrixStack.Entry e, int luz, float v,
                                 float ax, float az, float bx, float bz, float cx, float cz, float dx, float dz,
                                 float y, float u0, float u1, float ny) {
        float[][] p = {{ax, az, u0}, {bx, bz, u1}, {cx, cz, u1}, {dx, dz, u0}};
        for (float[] q : p) {
            vc.vertex(e.getPositionMatrix(), q[0] / 16F, y, q[1] / 16F)
                    .color(0xFFFFFFFF)
                    .texture(q[2] / 64F, v)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(luz)
                    .normal(e, 0F, ny, 0F);
        }
    }

    /** El anillo de volado de {@link VoladoMalla} en el borde {@code y} (fila) de {@code parte}. */
    private static void dibujarVolado(@Nullable com.modamod.item.PolleraVolado volado,
                                      @Nullable com.modamod.item.PolleraBorde borde, Parte parte, boolean slim, Identifier textura,
                                      int y, float dil, int filaTextura, float hacia, ModelPart delModelo,
                                      MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz) {
        float[] c = ApliqueRenderer.caja(parte, slim);
        int[] uv = switch (parte) {
            case TORSO -> new int[]{16, 16};
            case BRAZO_DER -> new int[]{40, 16};
            case BRAZO_IZQ -> new int[]{32, 48};
            case PIERNA_DER -> new int[]{0, 16};
            case PIERNA_IZQ -> new int[]{16, 48};
            default -> new int[]{0, 0};
        };
        int hondo = Math.round(c[5]);
        float v = (uv[1] + hondo + Math.max(0, Math.min(11, filaTextura)) + 0.5F) / 64F;
        matrices.push();
        delModelo.rotate(matrices);
        VertexConsumer vc = vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura));
        VoladoMalla.dibujar(matrices.peek(), vc, luz, volado, borde, hacia, c[0], c[2], c[3], c[5], c[1] + y, dil,
                uv[0], v);
        matrices.pop();
    }

    /** La pollera del dibujo en curso (la tela de encima pasa por fuera), o null. */
    private static final List<ItemStack> polleras = new java.util.ArrayList<>();

    /** La profundidad de la pollera más de adentro, o −1: lo de más afuera se abre por fuera de ella. */
    private static int profundidadPollera = -1;

    /** Los faldones ya dibujados en este cuadro: lo de encima (el delantal) pasa por fuera, como de las polleras. */
    private record FaldonDibujado(com.modamod.item.PolleraLargo largo, @Nullable com.modamod.item.PolleraVolado volado,
                                  float mitadX, float mitadZ, @Nullable float[] extra, float cintura) {}

    private static final List<FaldonDibujado> faldones = new java.util.ArrayList<>();

    /** Cuánto sale del torso lo que cuelga de la cintura (polleras y faldones) a la altura {@code y}, o −1. */
    private static float holguraDeFaldas(float y, float twirl) {
        float mejor = holguraPollera(y, twirl);
        for (FaldonDibujado f : faldones) {
            mejor = Math.max(mejor, PolleraMalla.holguraEnFaldon(f.largo, f.volado, f.mitadX, f.mitadZ, f.extra, y, f.cintura, twirl));
        }
        return mejor;
    }

    /** El manto del busto de la tela de más afuera dibujada en este cuadro: el delantal lo esquiva tal cual es. */
    private record Manto(float inflado, float carpa, com.modamod.render.relieve.BustoRender.Tela tela, float fondo) {}

    @Nullable
    private static Manto mantoDeAfuera;

    private static com.modamod.render.relieve.BustoRender.Campo campoDelBusto(
            com.modamod.render.relieve.BustoRender.Busto busto, float inflado) {
        Manto m = mantoDeAfuera;
        return m == null ? com.modamod.render.relieve.BustoRender.campoDelantal(busto, inflado)
                : com.modamod.render.relieve.BustoRender.campoDelantal(busto, m.inflado, m.carpa, m.tela, m.fondo);
    }

    private static void anotarPollera(List<ItemStack> prendas) {
        polleras.clear();
        faldones.clear();
        mantoDeAfuera = null;
        profundidadPollera = -1;
        for (int k = 0; k < prendas.size(); k++) {
            ItemStack s = prendas.get(k);
            if (!com.modamod.prenda.Prendas.es(s, com.modamod.prenda.CategoriaDePrenda.POLLERA)) continue;
            if (polleras.isEmpty()) profundidadPollera = k;
            polleras.add(s);
        }
    }

    /** Cuánto sale la pollera puesta del torso a la altura {@code y}, o −1 (ver {@link PolleraMalla#holguraEn}). */
    private static float holguraPollera(float y) {
        return holguraPollera(y, -1F);
    }

    /** Con las polleras abiertas por el twirl ({@code twirl} 0..1, −1 quietas). */
    private static float holguraPollera(float y, float twirl) {
        float mejor = -1F;
        for (int i = 0; i < polleras.size(); i++) {
            ItemStack s = polleras.get(i);
            mejor = Math.max(mejor, PolleraMalla.holguraEn(com.modamod.item.PolleraItem.forma(s), com.modamod.item.PolleraItem.largo(s),
                    com.modamod.item.PolleraItem.voladoRuedo(s), com.modamod.item.PolleraItem.voladoTodo(s),
                    Math.max(0F, com.modamod.item.Calce.dilatacionEfectiva(s)) + i * SEPARACION_POLLERAS, y, com.modamod.item.PolleraItem.cintura(s), twirl));
        }
        return mejor;
    }

    /** Cuánto queda cada capa por fuera de la de abajo, en las filas donde se superponen (px de skin). */
    private static final float SEPARACION_CAPAS = 0.03F;

    /** Cuánto se separa cada malla oversize de la que tiene debajo (px): son siluetas distintas, no se pueden empatar por fila. */
    private static final float SEPARACION_MALLAS = 0.3F;

    /** Ancho en px de la apertura del frente de esta prenda (0 si no tiene): el manto del busto se corta ahí. */
    private static int aperturaDe(@Nullable ItemStack prenda) {
        if (prenda == null || !(com.modamod.prenda.Prendas.es(prenda, com.modamod.prenda.CategoriaDePrenda.REMERA))) return 0;
        if (!com.modamod.item.TopCorte.frente(prenda).abre()) return 0;
        return com.modamod.render.DetallesHoodie.anchoApertura(com.modamod.item.Calce.leer(prenda));
    }

    /**
     * Dibuja la tela de una parte, ya ordenada por {@link Capa}, con el
     * calce de cada pieza (2026-09-30, "quiero que resolvamos el calce para
     * que quede diferenciado"):
     * <ul>
     *   <li>Suelto/Oversize se abren hacia el ruedo ({@code Calce.caida},
     *       creciendo de a poco fila a fila) y cuelgan por debajo de donde
     *       corta la prenda ({@code Calce.colgado});</li>
     *   <li>a pedido ("respetar siempre las capas"), una pieza nunca queda
     *       por dentro de una de capa más baja: en cada fila donde se
     *       superponen se la empuja {@link #SEPARACION_CAPAS} por fuera de la
     *       de abajo — solo en esas filas, así un croptop ajustado sobre un
     *       pantalón suelto sigue apretando donde no hay pantalón.</li>
     * </ul>
     * Una pieza que queda con todas sus filas iguales y sin colgar va por la
     * caja entera de siempre ({@link #dibujar}); si no, fila por fila
     * ({@link CuerpoGeometria#telaPorFilas}).
     */
    private static void dibujarPiezas(Parte parte, boolean slim, List<Pieza> piezas, @Nullable Pieza conVolumen,
                                      ModelPart delModelo, MatrixStack matrices,
                                      VertexConsumerProvider vcpBase, int luz,
                                      com.modamod.render.relieve.MapaRelieve debajo, Map<Pieza, ItemStack> origen,
                                      Map<Pieza, Integer> profundidad) {
        // Relieve (2026-10-01): cada tela envuelve a lo de abajo (el cuerpo o
        // la pieza anterior) según su calce, y suma arrugas, textura y extrusión.
        com.modamod.render.relieve.MapaRelieve previo = debajo;
        // Busto (2026-10-01): cada tela que tapa el pecho lo envuelve, por fuera de la de abajo.
        float infladoBusto = parte == Parte.TORSO ? infladoBustoCuerpo(piezas) : 0f;
        float infladoCola = parte == Parte.TORSO ? infladoColaCuerpo(piezas) : 0f;
        // Las telas por dentro de la última holgada no dibujan su busto: lo tapa su manto.
        int manto = parte == Parte.TORSO ? indiceManto(piezas) : -1;
        int indice = -1;
        boolean conRelieve = com.modamod.render.relieve.RelieveRender.estilo
                != com.modamod.render.relieve.RelieveRender.Estilo.APAGADO;
        try {
        boolean pierna = parte == Parte.PIERNA_DER || parte == Parte.PIERNA_IZQ;
        // Lo más afuera que ya hay dibujado en cada fila (12 + lo que puede colgar).
        float[] exterior = new float[12 + 4];
        java.util.Arrays.fill(exterior, Float.NEGATIVE_INFINITY);
        int capasMalla = 0;   // cuántas telas oversize de malla ya hay en esta parte

        for (Pieza pieza : piezas) {
            indice++;
            // Acabado con trim (2026-10-06): la tela de esta prenda se dibuja además con su efecto animado.
            VertexConsumerProvider vertexConsumers = EfectoTrim.envolver(origen.get(pieza), vcpBase);
            int prof = profundidad.getOrDefault(pieza, 0);
            // Lo de más afuera que la pollera se abre por fuera de ella; lo de las filas de arriba de la armadura, por fuera de ella.
            boolean sobrePollera = parte == Parte.TORSO && profundidadPollera >= 0 && prof > profundidadPollera;
            boolean sobreArmadura = prof >= profundidadArmadura;
            int desde = Math.max(0, Math.min(12, pieza.filaDesde()));
            int hasta = Math.max(desde, Math.min(12, pieza.filaHasta()));
            // Calzado (2026-10-08, "quiero las dos opciones"): el pantalón con la botamanga por dentro se corta donde
            // empieza la caña; por fuera baja entero y se infla sobre ella (más abajo, fila por fila).
            int cana = 0;
            boolean botamangaAfuera = false;
            if (pierna && calzado != null && pieza != conVolumen && pieza.capa() >= Capa.PIERNA_EXTERIOR) {
                boolean izqPie = parte == Parte.PIERNA_IZQ;   // cada pie con su caña, suela y botamanga
                cana = com.modamod.item.CalzadoItem.alturaCana(calzado, izqPie);   // 2026-10-14: la capellada y la suela elegidas
                botamangaAfuera = pantalonSobreCalzado;   // lo decide el orden de la lista de ropa
                // Metida en la bota entra una fila por debajo del borde de la caña (2026-11-11, "con botas hay z fighting"):
                // cortada justo en el borde quedaba una rendija por la que se veía la media, dentada en voxel.
                if (!botamangaAfuera) hasta = Math.max(desde, Math.min(hasta, 12 - cana + 1));
            }
            // Lo que queda dentro de la caña (todo menos el pantalón que va por fuera): plano y más fino que ella.
            int canaPie = pierna && calzado != null ? com.modamod.item.CalzadoItem.alturaCana(calzado, parte == Parte.PIERNA_IZQ) : 0;
            boolean dentroDeLaBota = canaPie > 0 && !(botamangaAfuera && pieza.capa() >= Capa.PIERNA_EXTERIOR);
            float base = pieza.dilatacion();
            // Medias tapadas por un pantalon (2026-10-19, "veo con rotura escalonada"): sin volumen (lo apaga el pantalon de encima) la
            // media Suelto/Oversize se dibujaba por filas con toda su holgura y sus escalones asomaban por debajo de la malla del
            // pantalon. Tapada, queda pegada a la pierna.
            if (pierna && pieza.volumenPierna() && pieza != conVolumen) base = Math.min(base, CuerpoGeometria.Superficie.CUERPO.dilatacion + 0.01F);
            if (conRelieve && parte != Parte.CABEZA) {
                ItemStack prenda = origen.get(pieza);
                previo = com.modamod.render.relieve.RelieveTela.de(previo, com.modamod.item.Calce.de(base),
                        prenda == null ? com.modamod.item.TexturaTela.LISA
                                : com.modamod.prenda.Acabado.textura(prenda),
                        prenda == null ? null : com.modamod.render.relieve.RelieveTela.extrusionDe(
                                net.minecraft.registry.Registries.ITEM.getId(prenda.getItem())));
                // Lo que queda debajo de otra prenda va plano donde esa prenda lo tapa, como el cuerpo (2026-11-11, "las medias que
                // estan debajo de un pantalon se dibujan por encima alli donde hay volumen"): la de arriba se dibuja lisa, o con
                // sus escalones corridos, y el relieve de la de abajo la atravesaba. La de arriba sigue armando su relieve
                // sobre el de abajo entero ({@code previo} no se toca).
                com.modamod.render.relieve.MapaRelieve propio = previo;
                for (int k = indice + 1; k < piezas.size(); k++) {
                    propio = bajo(propio, parte, piezas.get(k));
                }
                if (dentroDeLaBota) propio = com.modamod.render.relieve.MapaRelieve.sinFilas(propio, parte, 12 - canaPie, 12);
                com.modamod.render.relieve.RelieveRender.actual =
                        new com.modamod.render.relieve.RelieveRender.Contexto(parte, propio);
            }

            if (pieza == conVolumen) {
                // Tela fruncida en malla (2026-10-17, "q las medias tambien pasen por el fruncido"): el perfil de muslo y
                // pantorrilla es la base de la malla.
                ModelPart fruncida = pieza.fruncido() || pieza.fruncidoSup()
                        ? CuerpoGeometria.telaFruncidaVolumen(parte, base, desde, hasta, pieza.fruncido(), pieza.fruncidoSup(),
                                conPuntaDeMedia(pieza, parte)) : null;
                float[] dilMedia = filasDeMedia(pieza, base, desde, hasta);
                dibujarModelPart(fruncida != null ? fruncida
                                : CuerpoGeometria.telaConVolumenDePierna(parte, dilMedia, conPuntaDeMedia(pieza, parte), desde, hasta),
                        CuerpoGeometria.Superficie.TELA, pieza.textura(), delModelo, matrices, vertexConsumers, luz);
                // Sin anillos entre filas (2026-11-05, "caída lisa"): la malla continua de telaConVolumenDePierna ya las une.
                for (int f = desde; f < hasta; f++) exterior[f] = Math.max(exterior[f], base);
                continue;
            }

            com.modamod.item.Calce calce = com.modamod.item.Calce.de(base);
            // La caída es del RUEDO: la parte de torso del pantalón (tiro) y el
            // cinto de la pollera terminan en la cintura, no tienen ruedo libre.
            // La cabeza (banda de la polera) tampoco.
            boolean ruedoLibre = calce != null && parte != Parte.CABEZA
                    && !(parte == Parte.TORSO && (pieza.capa() == Capa.PIERNA_EXTERIOR || pieza.capa() == Capa.POLLERA));
            float caida = ruedoLibre ? calce.caida : 0F;
            int colgado = ruedoLibre ? calce.colgado : 0;
            // Una pierna entera no cuelga por debajo del pie (quedaría enterrada).
            if (pierna && hasta >= 12) colgado = 0;
            // La punta se cierra SOLO con el molde de ruedo CERRADO (2026-11-04, "podemos resolver lo del guante con un molde de
            // ruedo que diga cerrado"): la mano o el pie, donde el cuerpo no tiene tapa propia. Llegar a la muñeca (fila 12) no es
            // cerrar el puño (2026-11-05, "la confusión de la muñeca con el puño"): un calientabrazos largo es un tubo abierto.
            // Antes todo MANGA_INTERIOR hasta la fila 12 se tomaba por guante y dibujaba una tapa de textura vacía: el píxel
            // suelto en la mano. Un borde cerrado tampoco cuelga.
            boolean tapaPunta = pieza.ruedo() == com.modamod.item.Ruedo.CERRADO && hasta >= 12;
            if (tapaPunta) colgado = 0;
            if (cana > 0 && !botamangaAfuera) colgado = 0;     // la botamanga cortada no cuelga sobre el calzado
            if (hasta == desde) colgado = 0;
            // Elástico (puños y ruedo del hoodie): la banda sostiene la tela,
            // no cuelga — la caída queda como globo arriba de la banda.
            if (pieza.elastico()) colgado = 0;
            // Con faldón (2026-11-09) el torso no cuelga: el faldón lo sigue desde su última fila.
            ItemStack conFaldon = parte == Parte.TORSO && origen.get(pieza) != null
                    && com.modamod.prenda.Prendas.es(origen.get(pieza), com.modamod.prenda.CategoriaDePrenda.REMERA)
                    && com.modamod.item.TopCorte.faldon(origen.get(pieza)) >= 0 ? origen.get(pieza) : null;
            if (conFaldon != null) colgado = 0;
            // Oversize en malla (2026-10-15, "podemos hacer la ropa oversize de malla?"): el torso y las mangas con ruedo libre
            // van como una superficie continua con las esquinas redondeadas y la holgura interpolada, no como cajas escalonadas.
            boolean esBrazo = parte == Parte.BRAZO_DER || parte == Parte.BRAZO_IZQ;
            // El pantalón oversize (2026-10-16, "ahora al pantalon oversize"): la banda de cintura (torso) y las piernas.
            // Suelto también va en malla, a mitad de la holgura (2026-10-19, "los pantalones oversize y loose deberian ser de malla").
            boolean pantalonOversize = (calce == com.modamod.item.Calce.OVERSIZE || calce == com.modamod.item.Calce.SUELTO)
                    && pieza.capa() == Capa.PIERNA_EXTERIOR && (pierna || parte == Parte.TORSO);
            float factorMalla = calce == com.modamod.item.Calce.SUELTO ? 0.5F : 1F;
            boolean suave = pantalonOversize
                    || ruedoLibre && calce == com.modamod.item.Calce.OVERSIZE && (parte == Parte.TORSO || esBrazo);

            float[] fila = new float[12];
            // La parte continua de la holgura (calce, silueta, caída, campana): entre filas se alisa; lo que se suma después
            // (puño elástico, ribete de piel, botamanga, otra capa) es un escalón a propósito (2026-11-05, "caída lisa").
            float[] continua = new float[12];
            boolean uniforme = true;
            for (int f = 0; f < 12; f++) {
                float d = base;
                if (f >= desde && f < hasta) {
                    // Silueta en A del hoodie (2026-10-04, "que arriba sea mas pegado al cuerpo y se ensanche hacia
                    // abajo"): el torso arranca al 35 % de la holgura en la primera fila y llega al 100 % en la última.
                    if (pieza.elastico() && parte == Parte.TORSO && pieza.capa() == Capa.CHAQUETA && hasta - desde > 1) {
                        float tt = (f - desde + 1) / (float) (hasta - desde);
                        d = base * (0.35F + 0.65F * tt);
                    }
                    if (caida > 0F) {
                        float t = (f - desde + 1) / (float) (hasta - desde);
                        d += caida * t * (float) Math.sqrt(t);
                        // La manga oversize se abre hacia la boca (sin cerrar el puño: eso lo hace el molde de puño).
                        if (suave && esBrazo && !pieza.elastico()) d += 0.4F * t * t;
                    }
                    // Ruedo en campana (2026-10-07): las últimas 3 filas se abren hacia afuera, 0,6 px más cada una.
                    if (pieza.campana() && hasta - desde > 3 && f >= hasta - 3) d += 0.6F * (f - (hasta - 3) + 1);
                    continua[f] = d;
                    // Ribete de piel (2026-11-04): las 2 últimas filas del borde de abajo (o las 2 primeras del de arriba) más gruesas.
                    if (pieza.piel() && hasta - desde > 2 && f >= hasta - 2) d += 0.5F;
                    if (pieza.pielSup() && hasta - desde > 2 && f < desde + 2) d += 0.5F;
                    // La banda elástica aprieta: 40 % de la holgura, nunca por
                    // dentro del cuerpo (0.05 de aire).
                    if (pieza.elastico() && f == hasta - 1) d = Math.max(0.05F, base * 0.4F);
                    if (pieza.elasticoSup() && f == desde) d = Math.max(0.05F, base * 0.4F);
                    // Botamanga por fuera: sobre la caña, más ancha que ella (las filas de la suela quedan adentro de la suela).
                    if (botamangaAfuera && f >= 12 - cana
                            && f < 12 - com.modamod.item.CalzadoItem.suela(calzado, parte == Parte.PIERNA_IZQ).filas) {
                        d = Math.max(d, com.modamod.render.BorcegosRenderer.T + 0.12F);
                    }
                    if (dentroDeLaBota && f >= 12 - canaPie) d = Math.min(d, com.modamod.render.BorcegosRenderer.T - 0.15F);
                    if (exterior[f] != Float.NEGATIVE_INFINITY) d = Math.max(d, exterior[f] + SEPARACION_CAPAS);
                    if (sobreArmadura && armaduraEn(parte, f) > 0f) d = Math.max(d, armaduraEn(parte, f) + SEPARACION_CAPAS);
                    // Lo que va encima de la pollera (el hoodie) se abre por fuera de
                    // ella (2026-10-02, "el hoodie se abre por fuera"): la cintura de
                    // la pollera atravesaba el ruedo; el elástico tampoco la aprieta.
                    if (sobrePollera) {
                        float h = holguraPollera(f + 1);
                        if (h >= 0F) d = Math.max(d, h + 0.15F);
                    }
                }
                fila[f] = d;
                if (f < desde || f >= hasta) continua[f] = d;
                if (d != base) uniforme = false;
            }
            // Bordes de fila 0..12 que quedan en escalón: los cortes de la prenda y donde cambia algo más que la parte continua.
            boolean[] juntaDura = new boolean[13];
            juntaDura[0] = juntaDura[12] = true;
            if (desde > 0 && desde < 12) juntaDura[desde] = true;
            if (hasta > 0 && hasta < 12) juntaDura[hasta] = true;
            for (int b = 1; b < 12; b++) {
                if (Math.abs((fila[b] - fila[b - 1]) - (continua[b] - continua[b - 1])) > 0.01F) juntaDura[b] = true;
            }
            float dilColgado = hasta > 0 ? fila[hasta - 1] : base;
            for (int k = 0; k < colgado; k++) {
                int f = hasta + k;
                if (exterior[f] != Float.NEGATIVE_INFINITY) dilColgado = Math.max(dilColgado, exterior[f] + SEPARACION_CAPAS);
                if (sobreArmadura && f < 12 && armaduraEn(parte, f) > 0f) dilColgado = Math.max(dilColgado, armaduraEn(parte, f) + SEPARACION_CAPAS);
                if (sobrePollera) {
                    float h = holguraPollera(f + 1);
                    if (h >= 0F) dilColgado = Math.max(dilColgado, h + 0.15F);
                }
            }

            // Lo que de verdad se infló cada fila (calce, caída, ruedos, lo de abajo que empuja): los apliques se apoyan ahí
            // (2026-11-04, "los apliques en partes ensanchadas quedan debajo del modelo").
            ApliqueRenderer.registrarFilas(origen.get(pieza), parte, fila, dilColgado);
            // Los costados que dan al cuerpo (torso ↔ brazos) casi no se inflan
            // (2026-10-02, "probar no ensanchar los lados internos que coexisten
            // en torso y brazos"): una manga holgada y el torso holgado se metían
            // uno adentro del otro. Cada capa apenas por fuera de la de abajo.
            boolean conBrazo = parte == Parte.TORSO || parte == Parte.BRAZO_DER || parte == Parte.BRAZO_IZQ;
            float interno = conBrazo ? 0.05F + 0.04F * indice : -1F;
            boolean costadoInterno = interno >= 0F && base > interno;
            if (conFaldon != null && !suave && hasta > 0) {
                float d = Math.max(0F, fila[hasta - 1]);   // el Ajustado (holgura negativa) igual va por fuera del cuerpo
                BORDE_TORSO.put(conFaldon, new float[]{4F + CuerpoGeometria.costadosX(parte, d, costadoInterno ? interno : -1F)[0],
                        2F + d, hasta});
            }
            // Malla de Blockbench (2026-10-16): cada malla oversize de la misma parte queda 0,3 px por fuera de la de abajo.
            ModelPart mallaOversize = suave
                    ? CuerpoGeometria.telaOversize(parte, pantalonOversize && parte == Parte.TORSO, slim, desde, hasta, colgado,
                            capasMalla * SEPARACION_MALLAS, pieza.elastico() ? Math.max(0.05F, base * 0.4F) : -1F, factorMalla)
                    : null;
            // Tela fruncida en malla (2026-10-17, "quiero que se bunchee asi la tela de las medias y los armwarmers" → "capaz que en
            // malla quedaria mejor"): los pliegues no salen de las filas sino de la malla, así que van aparte de {@code fila}.
            boolean fruncida = pieza.fruncido() || pieza.fruncidoSup();
            ModelPart mallaFruncida = fruncida && mallaOversize == null
                    ? CuerpoGeometria.telaFruncida(parte, slim, fila, desde, hasta, pieza.fruncido(), pieza.fruncidoSup(), colgado,
                            dilColgado, costadoInterno ? interno : -1F, 0)
                    : null;
            if (mallaFruncida != null) {
                dibujarModelPart(mallaFruncida, CuerpoGeometria.Superficie.TELA, pieza.textura(), delModelo, matrices,
                        vertexConsumers, luz);
            } else if (mallaOversize != null) {
                capasMalla++;
                dibujarModelPart(mallaOversize, CuerpoGeometria.Superficie.TELA, pieza.textura(), delModelo, matrices,
                        vertexConsumers, luz);
            } else if (((uniforme && colgado == 0) || parte == Parte.CABEZA) && !costadoInterno) {
                // La cabeza va de una pieza: con la holgura (o el casco) de su fila más inflada.
                float entera = base;
                if (parte == Parte.CABEZA) for (int f = desde; f < hasta; f++) entera = Math.max(entera, fila[f]);
                dibujar(CuerpoGeometria.Superficie.TELA, parte, slim, pieza.textura(), entera,
                        delModelo, matrices, vertexConsumers, luz);
            } else {
                dibujarModelPart(suave
                                ? CuerpoGeometria.telaSuavePorFilas(parte, slim, fila, hasta, colgado, dilColgado,
                                        costadoInterno ? interno : -1F, tapaPunta)
                                : CuerpoGeometria.telaSuavePorFilas(parte, slim, fila, hasta, colgado, dilColgado,
                                        costadoInterno ? interno : -1F, tapaPunta, juntaDura),
                        CuerpoGeometria.Superficie.TELA, pieza.textura(), delModelo, matrices, vertexConsumers, luz);
            }
            // Los escalones hacia adentro (el puño o el ruedo elástico aprietan de golpe): el borde de la fila de arriba
            // queda con la cara de abajo abierta y se ve el interior (2026-10-04, "la parte de abajo no tiene tapas, la
            // manga queda abierta"). Un anillo entre los dos contornos lo cierra.
            if (parte != Parte.CABEZA && !suave && mallaFruncida == null) {
                for (int f = desde; f < hasta - 1; f++) {
                    // Cualquier escalón, hacia adentro o hacia afuera (2026-10-13, "arreglar las grietas en los modelos
                    // escalonados por el ensanchamiento de la prenda... procedural"): la cara que une dos filas con
                    // distinta holgura se genera sola entre los dos contornos, sin mirar capturas caso por caso.
                    float sup = fila[f], inf = fila[f + 1];
                    // Cualquier diferencia, no solo las que se ven de lejos (2026-11-04, "cortes muy pequeños pero
                    // incluso en partes no escalonadas"): el escalonado del calce mueve las filas por centésimas y
                    // esas rendijas también dejan ver el interior. Solo en las juntas duras: las lisas las une la pared
                    // inclinada de la malla (2026-11-05, "caída lisa").
                    if (juntaDura[f + 1] && Math.abs(sup - inf) > 0.002F) {
                        dibujarBoca(parte, slim, pieza.textura(), Math.max(sup, inf), Math.min(sup, inf),
                                costadoInterno ? interno : -1F, sup > inf ? f : f + 1, f + 1, sup > inf ? 1F : -1F,
                                delModelo, matrices, vertexConsumers, luz);
                    }
                }
            }
            // La boca de la manga (o de la botamanga, o el ruedo cortado) cerrada
            // con un anillo de tela entre la prenda y el cuerpo (2026-10-02, "manga
            // inferior abierta sin planos"): antes se veía el hueco de adentro.
            boolean brazo = parte == Parte.BRAZO_DER || parte == Parte.BRAZO_IZQ;
            // (la malla suave deja la boca abierta: el anillo plano sacaría esquinas rectas por fuera de las redondeadas)
            if (parte != Parte.CABEZA && !suave && hasta > desde && (brazo || hasta < 12 || colgado > 0)) {
                float dBoca = colgado > 0 ? dilColgado : fila[hasta - 1];
                dibujarBoca(parte, slim, pieza.textura(), dBoca, costadoInterno ? interno : -1F, hasta - 1,
                        hasta + colgado, delModelo, matrices, vertexConsumers, luz);
            }

            // Volado de malla en el borde de abajo o de arriba (2026-10-11, puños de maid): un anillo ondulado.
            if (parte != Parte.CABEZA && hasta > desde) {
                if (pieza.ruedo().volado() != null || pieza.ruedo().borde() != null) {
                    dibujarVolado(pieza.ruedo().volado(), pieza.ruedo().borde(), parte, slim, pieza.textura(), hasta + colgado,
                            (colgado > 0 ? dilColgado : fila[hasta - 1]) + 0.05F, Math.min(11, hasta - 1 + colgado), 1F,
                            delModelo, matrices, vertexConsumers, luz);
                }
                if (pieza.ruedoSup().volado() != null || pieza.ruedoSup().borde() != null) {
                    dibujarVolado(pieza.ruedoSup().volado(), pieza.ruedoSup().borde(), parte, slim, pieza.textura(), desde,
                            fila[desde] + 0.05F, desde, -1F, delModelo, matrices, vertexConsumers, luz);
                }
            }

            // La malla oversize de arriba ya pasa holgada por la cola: la cuña de tela, casi chata con tanta holgura, salía
            // como una tabla por los costados de la espalda (2026-11-08, "tiene una tira rara atras el chaleco"). La del
            // pantalón (la cintura) no la envuelve: esa sigue dibujando su cola.
            boolean caderaPieza = pieza.capa() == Capa.PIERNA_EXTERIOR || pieza.capa() == Capa.POLLERA;
            if (parte == Parte.TORSO && com.modamod.render.relieve.BustoRender.actual != null
                    && com.modamod.render.relieve.BustoRender.cubreCola(desde, hasta) && (mallaOversize == null || caderaPieza)) {
                // La cola: cada tela que pasa por ahí la envuelve; lo que la pieza no
                // cubre es transparente en su textura y deja ver lo de abajo.
                infladoCola = Math.max(fila[Math.max(desde, Math.min(hasta - 1, 10))], infladoCola + SEPARACION_CAPAS);
                // La prenda de las caderas (pantalón, pollera) estira su textura desde su primera fila hasta arriba de la
                // cola, como la bombacha (2026-11-08, "mapeada la skin base o la prenda que hubiere en las caderas sobre el
                // volumen del trasero"): si no, la cola de arriba de la cintura quedaba transparente y asomaba la bombacha.
                boolean cadera = pieza.capa() == Capa.PIERNA_EXTERIOR || pieza.capa() == Capa.POLLERA;
                dibujarCola(pieza.textura(), delModelo, infladoCola,
                        com.modamod.render.relieve.BustoRender.carpaDe(calce), 20f, false, cadera ? desde : 0f,
                        matrices, vertexConsumers, luz);
            }
            if (parte == Parte.TORSO && com.modamod.render.relieve.BustoRender.actual != null
                    && com.modamod.render.relieve.BustoRender.cubre(desde, hasta) && indice >= manto
                    && !com.modamod.render.relieve.BustoRender.debugSinBustoTela) {
                infladoBusto = Math.max(fila[3], infladoBusto + SEPARACION_CAPAS);
                if (com.modamod.render.relieve.BustoRender.debugMedir) org.slf4j.LoggerFactory.getLogger("modamod").info(
                        "[medir] pieza fila=" + java.util.Arrays.toString(fila) + " base=" + base + " infladoBusto=" + infladoBusto);
                float carpa = com.modamod.render.relieve.BustoRender.carpaDe(calce);
                // Suelto y Oversize: un manto apoyado en la tela de cada fila (2026-10-02,
                // "se marca demasiado en el hoodie no se deberian ver asi como dos tetas").
                com.modamod.render.relieve.BustoRender.Tela tela =
                        new com.modamod.render.relieve.BustoRender.Tela(fila.clone(), desde, hasta, aperturaDe(origen.get(pieza)),
                                dejaVer(origen.get(pieza), pieza));
                dibujarBusto(pieza.textura(), delModelo, infladoBusto, carpa, 20f, false, tela, hasta,
                        matrices, vertexConsumers, luz);
                mantoDeAfuera = new Manto(infladoBusto, carpa, tela, hasta);
            }

            for (int f = desde; f < hasta; f++) exterior[f] = Math.max(exterior[f], fila[f]);
            for (int k = 0; k < colgado; k++) exterior[hasta + k] = Math.max(exterior[hasta + k], dilColgado);
        }
        } finally {
            com.modamod.render.relieve.RelieveRender.actual = null;
        }
    }

    /** Copia la pose ya calculada de la parte del jugador y dibuja la nuestra encima. */
    private static void dibujar(CuerpoGeometria.Superficie superficie, Parte parte, boolean slim,
                                Identifier textura, float dilatacion, ModelPart delJugador, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int luz) {
        ModelPart nuestra = CuerpoGeometria.parte(superficie, parte, slim, dilatacion);
        dibujarModelPart(nuestra, superficie, textura, delJugador, matrices, vertexConsumers, luz);
    }

    /** Como {@link #dibujar}, para cuando la ModelPart ya viene resuelta (CUERPO segmentado). */
    /**
     * {@code ModelPart.render} escrito a mano (2026-11-10, "sodium y iris"): nuestras telas arman cada cara como un cuboide de
     * 1×1×1 al que se le cambia la cara (CuerpoGeometria.cuadro). Sodium no dibuja las caras del cuboide sino una copia que
     * guarda al crearlo, así que con Sodium o Iris la tela salía como un cubito escondido dentro del cuerpo: invisible.
     */
    static void renderAMano(ModelPart parte, MatrixStack matrices, VertexConsumer vc, int luz, int overlay) {
        if (!parte.visible || (parte.cuboids.isEmpty() && parte.children.isEmpty())) return;
        matrices.push();
        parte.rotate(matrices);
        if (!parte.hidden) {
            MatrixStack.Entry e = matrices.peek();
            org.joml.Vector3f n = new org.joml.Vector3f(), w = new org.joml.Vector3f();
            for (ModelPart.Cuboid c : parte.cuboids) {
                for (ModelPart.Quad q : c.sides) {
                    e.transformNormal(q.direction, n);
                    for (ModelPart.Vertex v : q.vertices) {
                        e.getPositionMatrix().transformPosition(v.pos.x() / 16F, v.pos.y() / 16F, v.pos.z() / 16F, w);
                        vc.vertex(w.x, w.y, w.z, 0xFFFFFFFF, v.u, v.v, overlay, luz, n.x, n.y, n.z);
                    }
                }
            }
        }
        for (ModelPart hijo : parte.children.values()) renderAMano(hijo, matrices, vc, luz, overlay);
        matrices.pop();
    }

    static void dibujarModelPart(ModelPart nuestra, CuerpoGeometria.Superficie superficie,
                                Identifier textura, ModelPart delJugador, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int luz) {
        nuestra.copyTransform(delJugador);
        // copyTransform tambien copia xScale/yScale/zScale, asi que la nuestra
        // va DESPUES o se pierde.
        float escala = superficie.escalaDeParte();
        nuestra.xScale = nuestra.yScale = nuestra.zScale = escala;
        com.modamod.render.relieve.RelieveRender.Contexto relieve =
                com.modamod.render.relieve.RelieveRender.aplica(com.modamod.render.relieve.RelieveRender.actual)
                        && !CuerpoGeometria.esSuave(nuestra)
                        ? com.modamod.render.relieve.RelieveRender.actual : null;
        if (ClothingTextureCache.esTranslucida(textura)) {
            // Transparencia real (2026-10-01): segunda pasada, después de todo
            // lo opaco — si no, la tela translúcida del torso escribe su
            // profundidad antes que el brazo de atrás y lo tapa. La ModelPart
            // puede venir de un caché compartido: se guarda su pose acá.
            net.minecraft.client.model.ModelTransform pose = nuestra.getTransform();
            MatrixStack copia = new MatrixStack();
            copia.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
            copia.peek().getNormalMatrix().set(matrices.peek().getNormalMatrix());
            TRANSLUCIDAS_PENDIENTES.add(() -> {
                nuestra.setTransform(pose);
                nuestra.xScale = nuestra.yScale = nuestra.zScale = escala;
                VertexConsumer vc = vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura));
                if (relieve != null) {
                    com.modamod.render.relieve.RelieveRender.dibujar(nuestra, relieve, superficie.escala,
                            copia, vc, luz, OverlayTexture.DEFAULT_UV);
                } else {
                    renderAMano(nuestra, copia, vc, luz, OverlayTexture.DEFAULT_UV);
                }
            });
            return;
        }
        VertexConsumer buffer = vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura));
        long tDibujo = Perf.ini();
        if (relieve != null) {
            com.modamod.render.relieve.RelieveRender.dibujar(nuestra, relieve, superficie.escala,
                    matrices, buffer, luz, OverlayTexture.DEFAULT_UV);
            Perf.fin(Perf.Seccion.RELIEVE, tDibujo);
        } else {
            renderAMano(nuestra, matrices, buffer, luz, OverlayTexture.DEFAULT_UV);
            Perf.fin(Perf.Seccion.MALLA, tDibujo);
        }
    }

    /**
     * El busto de este dibujo (2026-10-01, "buscando mas la estrategia del mod
     * only jugs"): talle del cuerpo + Estrógenos, la sujeción de la ropa
     * interior (binder aplana, deportivo sujeta) y el rebote de resorte.
     */
    private static void prepararBusto(LivingEntity entidad, float tickDelta) {
        com.modamod.render.relieve.BustoRender.actual = bustoDe(entidad, tickDelta, true);
    }

    /**
     * El busto de {@code entidad} (null si no tiene): talle, sujeción de la
     * ropa interior y, con {@code fisica}, el rebote de este cuadro.
     */
    @Nullable
    public static com.modamod.render.relieve.BustoRender.Busto bustoDe(@Nullable LivingEntity entidad,
                                                                          float tickDelta, boolean fisica) {
        if (entidad == null) return null;
        PerfilCuerpo perfil = perfilDe(entidad);
        float talle = com.modamod.render.relieve.RelieveCuerpo.bustoDe(perfil);
        if (talle <= 0f) return null;
        com.modamod.body.InteriorArriba arriba = perfil.interior().arriba();
        float sujecion = arriba == com.modamod.body.InteriorArriba.BINDER ? 0.3f
                : arriba == com.modamod.body.InteriorArriba.DEPORTIVO ? 0.75f : 1f;
        com.modamod.render.relieve.RelieveRender.Rebote rebote =
                fisica ? com.modamod.render.relieve.FisicaBusto.de(entidad, tickDelta, talle) : null;
        // La cola crece con el busto y tiene su propio resorte (2026-10-02,
        // "el trasero deberia crecer con el busto y tener tambien fisica").
        float cola = com.modamod.render.relieve.RelieveCuerpo.colaDe(perfil);
        return new com.modamod.render.relieve.BustoRender.Busto(talle, sujecion, rebote, perfil.bustoCuadrado(),
                cola, fisica ? com.modamod.render.relieve.FisicaBusto.cola(entidad, cola) : null);
    }

    /** {@link #infladoBustoCuerpo} para un conjunto de prendas (las piezas de su torso). */
    public static float infladoBustoDe(List<ItemStack> prendas) {
        List<Pieza> torso = new ArrayList<>();
        for (ItemStack s : prendas) {
            for (Pieza p : PiezasDePrenda.de(s, null)) if (p.parte() == Parte.TORSO) torso.add(p);
        }
        return infladoBustoCuerpo(torso);
    }

    /**
     * Cuánto se infla el busto de la piel: un poco por dentro de la tela más
     * pegada que lo tape (Ajustado aprieta por dentro del cuerpo).
     */
    private static float infladoBustoCuerpo(@Nullable List<Pieza> piezas) {
        float minimo = 0f;
        if (piezas != null) {
            for (Pieza p : piezas) {
                if (com.modamod.render.relieve.BustoRender.cubre(p.filaDesde(), p.filaHasta())) {
                    minimo = Math.min(minimo, p.dilatacion() - 0.06f);
                }
            }
        }
        return minimo;
    }

    /** Como {@link #infladoBustoCuerpo}, para la cola: por dentro de la tela más pegada que pase por ahí. */
    private static float infladoColaCuerpo(@Nullable List<Pieza> piezas) {
        float minimo = 0f;
        if (piezas != null) {
            for (Pieza p : piezas) {
                if (com.modamod.render.relieve.BustoRender.cubreCola(p.filaDesde(), p.filaHasta())) {
                    minimo = Math.min(minimo, p.dilatacion() - 0.06f);
                }
            }
        }
        return minimo;
    }

    /**
     * La última pieza (la de más afuera) que tapa el busto con una tela
     * holgada (Suelto/Oversize: un manto), o −1. Las de adentro y el cuerpo
     * no dibujan su busto: lo reemplaza el manto.
     */
    private static int indiceManto(@Nullable List<Pieza> piezas) {
        int ultimo = -1;
        if (piezas == null) return ultimo;
        for (int i = 0; i < piezas.size(); i++) {
            Pieza p = piezas.get(i);
            if (p.parte() == Parte.TORSO && com.modamod.render.relieve.BustoRender.cubre(p.filaDesde(), p.filaHasta())
                    && com.modamod.render.relieve.BustoRender.esManto(
                            com.modamod.render.relieve.BustoRender.carpaDe(com.modamod.item.Calce.de(p.dilatacion())))) {
                ultimo = i;
            }
        }
        return ultimo;
    }

    /**
     * ¿El manto de más afuera tiene el frente abierto? Entonces el busto del cuerpo se dibuja igual (2026-10-20, "en una chaqueta
     * con frente abierto las tetas salen partidas": la franja cortada del manto dejaba un hueco en el medio sin nada abajo).
     */
    private static boolean mantoAbierto(@Nullable List<Pieza> piezas, Map<Pieza, ItemStack> origen) {
        int i = indiceManto(piezas);
        return i >= 0 && (aperturaDe(origen.get(piezas.get(i))) > 0 || dejaVer(origen.get(piezas.get(i)), piezas.get(i)));
    }

    /**
     * ¿La tela deja ver lo de abajo? Red, encaje, perforada, arnés o una textura translúcida (2026-11-08, "los pechos
     * desaparecen" con una remera oversize de red fina): el manto la reemplazaba y por los agujeros no había busto.
     */
    private static boolean dejaVer(@Nullable ItemStack prenda, Pieza pieza) {
        com.modamod.item.PatronRed red = prenda == null ? null : com.modamod.item.PatronRed.leer(prenda);
        return red != null && red != com.modamod.item.PatronRed.LISA && !red.esMaterial()
                || ClothingTextureCache.esTranslucida(pieza.textura());
    }

    /** ¿Alguna prenda tapa el busto con un manto? (el Maniquí no dibuja entonces el busto de la figura). */
    public static boolean bustoTapadoPorManto(List<ItemStack> prendas) {
        List<Pieza> torso = new ArrayList<>();
        Map<Pieza, ItemStack> origen = new java.util.IdentityHashMap<>();
        for (ItemStack s : prendas) {
            for (Pieza p : PiezasDePrenda.de(s, null)) if (p.parte() == Parte.TORSO) { torso.add(p); origen.put(p, s); }
        }
        return indiceManto(torso) >= 0 && !mantoAbierto(torso, origen);
    }

    /**
     * Hasta qué fila del torso llega el corpiño de la ropa interior (12 sin
     * corpiño): la textura del busto se aprieta para que lo cubra entero
     * (2026-10-02, "la parte de abajo de los senos no esta cubierta por el corpiño").
     * Sale de las texturas de {@code tools/generar_ropa_interior.py}.
     */
    private static float fondoSosten(PerfilCuerpo perfil) {
        return switch (perfil.interior().arriba()) {
            case BRALETTE, DEPORTIVO -> 5f;
            case BINDER -> 8f;
            // Sin nada arriba (2026-10-05, "rompio las tetas sin top"): el busto grande baja hasta la fila donde
            // empieza el slip y su textura leía ese blanco (una media luna blanca abajo de cada teta); se aprieta
            // para terminar justo antes de la ropa interior de abajo.
            default -> techoBombacha(perfil) > 0f ? techoBombacha(perfil) - 0.1f : 12f;
        };
    }

    /**
     * Primera fila de la ropa interior de abajo en la espalda (2026-10-02,
     * "acomoda la ropa interior a las nalgas"): sale de las texturas de
     * {@code tools/generar_ropa_interior.py} (slip y culotte desde la 9, boxer
     * desde la 8). La cola aprieta su textura desde ahí para quedar cubierta.
     */
    private static float techoBombacha(PerfilCuerpo perfil) {
        return switch (perfil.interior().abajo()) {
            case BOXER -> 8f;
            case NINGUNA -> 0f;          // sin bombacha no hay nada que cubrir: la cola no aprieta su textura
            default -> 9f;
        };
    }

    private static void dibujarCola(Identifier textura, ModelPart torso, float inflado, float carpa, float filaSkin,
                                    boolean piel, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz) {
        dibujarCola(textura, torso, inflado, carpa, filaSkin, piel, 0f, matrices, vertexConsumers, luz);
    }

    /** La cola con {@code textura} (piel o tela) en el marco del torso; las translúcidas, a la segunda pasada. */
    private static void dibujarCola(Identifier textura, ModelPart torso, float inflado, float carpa, float filaSkin,
                                    boolean piel, float techo, MatrixStack matrices,
                                    VertexConsumerProvider vertexConsumers, int luz) {
        com.modamod.render.relieve.BustoRender.Busto busto = com.modamod.render.relieve.BustoRender.actual;
        if (busto == null || busto.cola() <= 0f) return;
        matrices.push();
        torso.rotate(matrices);
        if (ClothingTextureCache.esTranslucida(textura)) {
            MatrixStack copia = new MatrixStack();
            copia.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
            copia.peek().getNormalMatrix().set(matrices.peek().getNormalMatrix());
            TRANSLUCIDAS_PENDIENTES.add(() -> com.modamod.render.relieve.BustoRender.dibujarCola(busto, copia,
                    vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura)), luz,
                    OverlayTexture.DEFAULT_UV, inflado, carpa, filaSkin, piel, techo));
        } else {
            com.modamod.render.relieve.BustoRender.dibujarCola(busto, matrices,
                    vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura)), luz,
                    OverlayTexture.DEFAULT_UV, inflado, carpa, filaSkin, piel, techo);
        }
        matrices.pop();
    }

    /** El busto con la textura de la skin (y su segunda capa), cuando el torso lo dibuja vanilla. */
    private static void dibujarBustoEnLaSkin(LivingEntity entidad, BipedEntityModel<?> biped, MatrixStack matrices,
                                             VertexConsumerProvider vertexConsumers, int luz) {
        if (com.modamod.render.relieve.BustoRender.actual == null
                || !(entidad instanceof AbstractClientPlayerEntity jugador) || !biped.body.visible) return;
        Identifier skin = jugador.getSkinTextures().texture();
        boolean chaqueta = jugador.isPartVisible(net.minecraft.entity.player.PlayerModelPart.JACKET);
        dibujarBustoDeSkin(skin, biped.body, chaqueta, matrices, vertexConsumers, luz);
    }

    /**
     * El busto con una skin (la del jugador o la de la figura del maniquí).
     * La piel sale apenas por delante de la segunda capa plana (2026-10-02,
     * "la skin del pecho donde surgen las tetas muestra la remera en plano":
     * la chaqueta plana de la skin asomaba en la base) y la segunda capa va
     * en su propia cúpula, por fuera.
     */
    public static void dibujarBustoDeSkin(Identifier skin, ModelPart torso, boolean chaqueta, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int luz) {
        dibujarBustoDeSkin(skin, torso, chaqueta, chaqueta ? 0.27f : 0f, matrices, vertexConsumers, luz);
    }

    /** Con el inflado de la piel a mano (por dentro de la ropa que la tape). */
    public static void dibujarBustoDeSkin(Identifier skin, ModelPart torso, boolean chaqueta, float inflado,
                                          MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz) {
        dibujarBustoDeSkin(skin, torso, chaqueta, inflado, true, matrices, vertexConsumers, luz);
    }

    /** {@code conBusto} false: solo la cola (el busto lo tapa el manto de una tela holgada). */
    public static void dibujarBustoDeSkin(Identifier skin, ModelPart torso, boolean chaqueta, float inflado,
                                          boolean conBusto, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int luz) {
        if (conBusto) {
            dibujarBusto(skin, torso, inflado, 0f, 20f, true, matrices, vertexConsumers, luz);
            if (chaqueta) dibujarBusto(skin, torso, 0.5f, 0f, 36f, matrices, vertexConsumers, luz);
        }
        dibujarCola(skin, torso, inflado, 0f, 20f, true, matrices, vertexConsumers, luz);
        if (chaqueta) dibujarCola(skin, torso, 0.5f, 0f, 36f, false, matrices, vertexConsumers, luz);
    }

    /** Las dos cúpulas del busto de una tela con {@code textura}, en el marco del torso. */
    private static void dibujarBusto(Identifier textura, ModelPart torso, float inflado, float carpa, float filaSkin,
                                     MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz) {
        dibujarBusto(textura, torso, inflado, carpa, filaSkin, false, matrices, vertexConsumers, luz);
    }

    /** Las dos cúpulas del busto con {@code textura} (piel o tela); las translúcidas, a la segunda pasada. */
    private static void dibujarBusto(Identifier textura, ModelPart torso, float inflado, float carpa, float filaSkin,
                                     boolean piel, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz) {
        dibujarBusto(textura, torso, inflado, carpa, filaSkin, piel, null, 12f, matrices, vertexConsumers, luz);
    }

    /** Con la dilatación de la pieza por fila ({@code tela}: el manto de las telas holgadas se apoya en ella). */
    private static void dibujarBusto(Identifier textura, ModelPart torso, float inflado, float carpa, float filaSkin,
                                     boolean piel, @Nullable com.modamod.render.relieve.BustoRender.Tela tela,
                                     float fondo, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz) {
        com.modamod.render.relieve.BustoRender.Busto busto = com.modamod.render.relieve.BustoRender.actual;
        if (busto == null) return;
        matrices.push();
        torso.rotate(matrices);
        if (ClothingTextureCache.esTranslucida(textura)) {
            MatrixStack copia = new MatrixStack();
            copia.peek().getPositionMatrix().set(matrices.peek().getPositionMatrix());
            copia.peek().getNormalMatrix().set(matrices.peek().getNormalMatrix());
            TRANSLUCIDAS_PENDIENTES.add(() -> com.modamod.render.relieve.BustoRender.dibujar(busto, copia,
                    vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura)), luz,
                    OverlayTexture.DEFAULT_UV, inflado, carpa, filaSkin, piel, tela, fondo));
        } else {
            com.modamod.render.relieve.BustoRender.dibujar(busto, matrices,
                    vertexConsumers.getBuffer(ClothingTextureCache.capaDeRender(textura)), luz,
                    OverlayTexture.DEFAULT_UV, inflado, carpa, filaSkin, piel, tela, fondo);
        }
        matrices.pop();
    }

    /** Telas translúcidas de este dibujo, para la segunda pasada ({@link #dibujarTranslucidas}). */
    private static final List<Runnable> TRANSLUCIDAS_PENDIENTES = new ArrayList<>();

    /** Dibuja (en el orden en que llegaron: de adentro hacia afuera) las telas translúcidas pendientes. */
    private static void dibujarTranslucidas() {
        for (Runnable r : TRANSLUCIDAS_PENDIENTES) r.run();
        TRANSLUCIDAS_PENDIENTES.clear();
    }

    /**
     * Fila del torso donde está la cintura natural — coincide con
     * {@code Variante.Largo.NORMAL.filas} (la remera "normal" baja justo
     * hasta ahí). Por debajo de esta fila NO se reconstruye piel: se
     * dibuja la piel REAL del jugador — a pedido (2026-09-19), "la piel
     * reconstruida hasta la cintura, de ahí la real". Antes CUERPO
     * rellenaba las 12 filas enteras siempre, así que una remera crop o
     * normal (que no llega a la cintura) mostraba ahí piel RECONSTRUIDA
     * en vez de la piel de verdad del jugador — el detalle que se pidió
     * corregir. Cuando la prenda sí cubre esas filas (remerón/LARGO), la
     * {@link Pieza} de tela se dibuja arriba de las dos capas igual y las
     * tapa por completo, así que el corte no cambia nada en ese caso.
     */
    private static final int FILA_CINTURA_TORSO = 9;

    /**
     * El CUERPO del torso, partido en la cintura ({@link #FILA_CINTURA_TORSO}):
     * arriba de la cintura, la piel reconstruida de siempre; de la
     * cintura para abajo, la piel REAL del jugador (nunca reconstruida
     * ahí). Si la entidad no es un jugador (mob con una prenda del mod,
     * caso raro) no hay piel "real" de la que tirar, así que se cae al
     * camino de siempre sin cortar nada.
     */
    private static void dibujarCuerpoTorso(LivingEntity entidad, boolean slim, Identifier cuerpoReconstruido,
                                            @Nullable List<CuerpoGeometria.SegmentoCuerpo> segmentos, boolean todoPiel,
                                            ModelPart delJugador, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int luz) {
        // De la cintura para abajo, la piel real también con un cuerpo elegido (2026-11-05, "vamos con el 2"): las piernas
        // las dibuja vanilla con la skin, y con la pelvis del cuerpo quedaba su ropa interior pegada al short de la skin.
        // Solo con el cuerpo entero (siempre, o la vista previa de elegir cuerpo) la pelvis es del cuerpo: ahí las piernas también.
        boolean cuerpoEntero = perfilOverride != null || perfilDe(entidad).siempre();
        Identifier pielReal = entidad instanceof AbstractClientPlayerEntity jugador && !cuerpoEntero
                ? jugador.getSkinTextures().texture() : null;
        if (pielReal == null) {
            ModelPart nuestra = segmentos == null
                    ? CuerpoGeometria.parte(CuerpoGeometria.Superficie.CUERPO, Parte.TORSO, slim)
                    : CuerpoGeometria.cuerpoSegmentado(Parte.TORSO, slim, segmentos);
            dibujarModelPart(nuestra, CuerpoGeometria.Superficie.CUERPO, cuerpoReconstruido,
                    delJugador, matrices, vertexConsumers, luz);
            return;
        }

        List<CuerpoGeometria.SegmentoCuerpo> completos = segmentos != null ? segmentos
                : List.of(new CuerpoGeometria.SegmentoCuerpo(0, 12, CuerpoGeometria.Superficie.CUERPO.dilatacion));

        if (todoPiel) {
            dibujarModelPart(CuerpoGeometria.cuerpoSegmentado(Parte.TORSO, slim, completos),
                    CuerpoGeometria.Superficie.CUERPO, pielReal, delJugador, matrices, vertexConsumers, luz);
            return;
        }
        List<CuerpoGeometria.SegmentoCuerpo> arriba = recortarSegmentos(completos, 0, FILA_CINTURA_TORSO);
        List<CuerpoGeometria.SegmentoCuerpo> abajo = recortarSegmentos(completos, FILA_CINTURA_TORSO, 12);

        if (!arriba.isEmpty()) {
            dibujarModelPart(CuerpoGeometria.cuerpoSegmentado(Parte.TORSO, slim, arriba),
                    CuerpoGeometria.Superficie.CUERPO, cuerpoReconstruido, delJugador, matrices, vertexConsumers, luz);
        }
        if (!abajo.isEmpty()) {
            dibujarModelPart(CuerpoGeometria.cuerpoSegmentado(Parte.TORSO, slim, abajo),
                    CuerpoGeometria.Superficie.CUERPO, pielReal, delJugador, matrices, vertexConsumers, luz);
        }
    }

    /**
     * El relieve del cuerpo de esa parte, plano en las filas que tapa alguna prenda (2026-11-05, "reemplazar la skin por la
     * prenda donde haya conflicto"). La tela envuelve el relieve completo del cuerpo (mapaCuerpo), así que la forma se sigue
     * viendo; el cuerpo de abajo no compite con ella y no la atraviesa (en voxel las grillas de cuerpo y tela no coinciden).
     */
    private static com.modamod.render.relieve.MapaRelieve relieveDelCuerpoBajoTela(
            com.modamod.render.relieve.MapaRelieve mapaCuerpo, Parte parte, @Nullable List<Pieza> piezas) {
        if (piezas == null) return bajoLaCana(mapaCuerpo, parte);
        com.modamod.render.relieve.MapaRelieve m = mapaCuerpo;
        for (Pieza p : piezas) m = bajo(m, parte, p);
        return bajoLaCana(m, parte);
    }

    /** {@code m} plano en las filas que tapa la caña del calzado puesto: es opaca, como una tela. */
    private static com.modamod.render.relieve.MapaRelieve bajoLaCana(com.modamod.render.relieve.MapaRelieve m, Parte parte) {
        if (calzado == null || (parte != Parte.PIERNA_DER && parte != Parte.PIERNA_IZQ)) return m;
        int cana = com.modamod.item.CalzadoItem.alturaCana(calzado, parte == Parte.PIERNA_IZQ);
        return cana <= 0 ? m : com.modamod.render.relieve.MapaRelieve.sinFilas(m, parte, 12 - cana, 12);
    }

    /**
     * {@code m} como queda debajo de {@code arriba}: plano donde esa tela es opaca (su textura, px por px), con su relieve
     * donde deja ver (abertura, escote, trama, translúcida). Sin la imagen de la textura a mano, plano en todas sus filas.
     */
    private static com.modamod.render.relieve.MapaRelieve bajo(com.modamod.render.relieve.MapaRelieve m, Parte parte, Pieza arriba) {
        boolean[][] opaco = com.modamod.render.relieve.MascaraOpaca.de(arriba.textura(), parte, m.slim);
        return opaco == null ? com.modamod.render.relieve.MapaRelieve.sinFilas(m, parte, arriba.filaDesde(), arriba.filaHasta())
                : com.modamod.render.relieve.MapaRelieve.sinOpacos(m, parte,
                        com.modamod.render.relieve.MascaraOpaca.clave(arriba.textura(), m.slim), opaco);
    }

    /** El relieve del cuerpo de una figura (el maniquí) debajo de {@code prendas}: la misma regla que para el jugador. */
    public static com.modamod.render.relieve.MapaRelieve relieveBajoRopa(com.modamod.render.relieve.MapaRelieve mapaCuerpo,
                                                                          Parte parte, List<ItemStack> prendas) {
        com.modamod.render.relieve.MapaRelieve m = mapaCuerpo;
        for (ItemStack stack : prendas) {
            for (Pieza p : PiezasDePrenda.de(stack, null)) if (p.parte() == parte) m = bajo(m, parte, p);
        }
        return m;
    }

    /** Recorta una lista de tramos contiguos [0,12) a la ventana [desde,hasta), partiendo el que cruce el borde. */
    private static List<CuerpoGeometria.SegmentoCuerpo> recortarSegmentos(
            List<CuerpoGeometria.SegmentoCuerpo> segmentos, int desde, int hasta) {
        List<CuerpoGeometria.SegmentoCuerpo> out = new ArrayList<>();
        for (CuerpoGeometria.SegmentoCuerpo s : segmentos) {
            int d = Math.max(s.filaDesde(), desde), h = Math.min(s.filaHasta(), hasta);
            if (d < h) out.add(new CuerpoGeometria.SegmentoCuerpo(d, h, s.dilatacion()));
        }
        return out;
    }

    /**
     * Qué partes hay que ocultarle al modelo BASE de vanilla porque acá se
     * dibuja encima — a pedido (2026-09-16), "quiero que nuestro modelo
     * reemplace el original": la capa base de la skin no se puede borrar
     * con textura (a diferencia de la segunda capa/overlay, que sí —
     * ver {@code SkinRegions}), así que la única forma de que no compita
     * en profundidad con {@code CUERPO} es que vanilla directamente no la
     * dibuje. {@code LivingEntityRendererMixin} llama esto para apagar
     * ({@code ModelPart.visible = false}) esas partes justo ANTES de que
     * vanilla dibuje su capa base, y las prende de vuelta justo DESPUÉS
     * (nunca antes de que {@link #render} corra — si quedaran apagadas,
     * {@code delJugador.visible} en el loop de abajo las saltearía a
     * ELLAS también, y no se dibujaría ni lo de vanilla ni lo nuestro).
     *
     * <p>Misma condición EXACTA que decide {@code llevaCuerpo} en
     * {@link #render}, factorizada para que las dos nunca se
     * desincronicen: si esto ocultara una parte que {@link #render} no
     * llega a dibujar (ej. {@code cuerpo == null}, la textura del cuerpo
     * base falló ese frame), quedaría un agujero real — invisible de
     * vanilla Y sin nada nuestro reemplazándolo.
     */
    public static List<Parte> partesAOcultarDeVanilla(LivingEntity entidad) {
        List<ItemStack> prendas = previewOverride != null ? previewOverride : equipadas(entidad);
        // Cuerpo entero: la vista previa de elegir cuerpo, o un perfil que usa
        // el cuerpo como skin aunque no haya ropa (2026-09-29, "un selector que
        // directamente te deje esa skin de default").
        boolean cuerpoEntero = perfilOverride != null || perfilDe(entidad).siempre();
        boolean cinto = compresionDeCintos(cintosDe(entidad)) != null;
        if (prendas.isEmpty() && !cuerpoEntero && !cinto) return List.of();
        Identifier cuerpo = texturaDelCuerpo(entidad, esSlim(entidad));
        if (cuerpo == null) return List.of();
        List<Parte> out = new ArrayList<>(partesConCuerpo(prendas, cuerpoEntero, cinto));
        // La cabeza nunca lleva cuerpo base (ver render) — ocultar la de
        // vanilla ahí dejaría al jugador sin cara.
        out.remove(Parte.CABEZA);
        return out;
    }

    private static final float EPSILON_COMPRESION = 0.02F;

    /**
     * Si alguna pieza de esta parte tiene un calce más chico que el cuerpo
     * (Ajustado) arma los tramos de fila para
     * {@link CuerpoGeometria#cuerpoSegmentado} — la piel se ve más flaca
     * SOLO donde esa tela realmente tapa (unión de {@code filaDesde}/
     * {@code filaHasta} de cada pieza comprimida; si dos se superponen con
     * calces distintos, gana la más chica, para que ninguna quede tapada
     * por el cuerpo). Devuelve {@code null} cuando ninguna pieza comprime
     * — el caso normal, sigue el camino simple de siempre.
     */
    @Nullable
    /** Cuántas filas más allá del borde de la tela llega el volado o el borde decorativo de un puño ({@link VoladoMalla}). */
    private static int filasDeVuelo(com.modamod.item.Ruedo r) {
        if (r.volado() == com.modamod.item.PolleraVolado.CIRCULAR) return 4;
        return r.volado() != null || r.borde() != null ? 3 : 0;
    }

    private static List<CuerpoGeometria.SegmentoCuerpo> segmentosCuerpo(Parte parte, @Nullable List<Pieza> piezas) {
        float base = CuerpoGeometria.Superficie.CUERPO.dilatacion;
        float[] porFila = new float[12];
        java.util.Arrays.fill(porFila, base);
        boolean hayCompresion = false;
        // El cinto vacía la piel de sus filas igual que una prenda que aprieta (ver BandaRenderer#compresionDeCinto).
        if (parte == Parte.TORSO && cintoCompresion != null) {
            hayCompresion = true;
            float propia = cintoCompresion[2] - EPSILON_COMPRESION;
            for (int f = (int) cintoCompresion[0]; f < (int) cintoCompresion[1]; f++) {
                if (propia < porFila[f]) porFila[f] = propia;
            }
        }
        if (piezas == null) {
            if (!hayCompresion) return null;
            piezas = List.of();
        }
        for (Pieza pieza : piezas) {
            // "< base + 0.01": también Pegado (base + 0.001), que "reemplaza la
            // skin" (2026-09-30) — el cuerpo se mete apenas por dentro de él en
            // vez de quedar a una milésima, así no hay empate de profundidad.
            if (pieza.dilatacion() >= base + 0.01F) continue;
            hayCompresion = true;
            float propia = pieza.dilatacion() - EPSILON_COMPRESION;
            // Con volado o borde decorativo (2026-10-15, "a la manga de ropa ajustada se le pone algun borde o vuelo y queda
            // bajo la piel"): el anillo de malla cuelga de la tela y arranca casi a ras del cuerpo, así que la piel también se
            // aprieta unas filas más allá del borde (lo que mide el volado, redondeado para arriba).
            int desde = Math.max(0, pieza.filaDesde() - filasDeVuelo(pieza.ruedoSup()));
            int hasta = Math.min(12, pieza.filaHasta() + filasDeVuelo(pieza.ruedo()));
            for (int f = desde; f < hasta; f++) {
                if (propia < porFila[f]) porFila[f] = propia;
            }
        }
        if (!hayCompresion) return null;
        List<CuerpoGeometria.SegmentoCuerpo> segmentos = new ArrayList<>();
        int inicio = 0;
        for (int f = 1; f <= 12; f++) {
            if (f == 12 || porFila[f] != porFila[inicio]) {
                segmentos.add(new CuerpoGeometria.SegmentoCuerpo(inicio, f, porFila[inicio]));
                inicio = f;
            }
        }
        return segmentos;
    }

    /**
     * Perfil a mostrar en vez del guardado mientras se dibuja la vista previa
     * de la GUI de elegir cuerpo (2026-09-29) — mismo mecanismo que
     * {@link #previewOverride}: se pone antes de dibujar y se limpia después.
     */
    @Nullable
    public static PerfilCuerpo perfilOverride = null;

    /** El perfil que se está dibujando: el de la vista previa, el del jugador o el de siempre. */
    public static PerfilCuerpo perfilDe(LivingEntity entidad) {
        return perfilOverride != null ? perfilOverride
                : entidad instanceof PlayerEntity jugador
                ? PerfilesDeCuerpo.de(jugador)
                : PerfilCuerpo.DEFECTO;
    }

    @Nullable
    private static Identifier texturaDelCuerpo(LivingEntity entidad, boolean slim) {
        return CuerpoBaseTextures.de(entidad, perfilDe(entidad), slim);
    }

    /**
     * Dibuja SOLO la tela ({@link Pieza}) de un brazo sobre el
     * {@link ModelPart} de PRIMERA PERSONA (2026-09-24, "la mano en
     * primera persona nunca muestra las prendas custom") — {@link #render}
     * de acá arriba nunca corre para esa vista: vanilla dibuja el brazo/
     * manga de primera persona por su cuenta
     * ({@code PlayerEntityRenderer#renderArm}), sin pasar por el modelo
     * completo del jugador ni sus {@code FeatureRenderer}. Llamado desde
     * {@code PlayerEntityRendererFirstPersonArmMixin}.
     *
     * <p>A propósito NO dibuja {@code Superficie.CUERPO} (el cuerpo
     * reconstruido): en primera persona vanilla ya muestra la piel REAL
     * del jugador tal cual (correcta), a diferencia de tercera persona
     * donde se reconstruye para tapar mangas pintadas en la textura de la
     * skin — acá lo único que falta es la PRENDA en sí.
     */
    public static void dibujarBrazoPrimeraPersona(Parte parte, ModelPart brazoDelJugador,
                                                   AbstractClientPlayerEntity jugador, MatrixStack matrices,
                                                   VertexConsumerProvider vertexConsumers, int luz) {
        List<ItemStack> prendas = equipadas(jugador);
        if (prendas.isEmpty()) return;

        Map<Parte, List<Pieza>> porParte = new EnumMap<>(Parte.class);
        agrupar(prendas, jugador, porParte, new java.util.IdentityHashMap<>(), new java.util.IdentityHashMap<>());
        List<Pieza> piezas = porParte.get(parte);
        if (piezas == null) return;

        boolean slim = esSlim(jugador);
        for (Pieza pieza : piezas) {
            dibujar(CuerpoGeometria.Superficie.TELA, parte, slim, pieza.textura(), pieza.dilatacion(),
                    brazoDelJugador, matrices, vertexConsumers, luz);
        }
    }

    /**
     * Solo la TELA de {@code prendas} (sin cuerpo base) sobre un modelo
     * bípedo ya posado que no es de ninguna entidad — para el Maniquí
     * (2026-09-30, "guarda y muestra la ropa... 16 slots, 4 por prenda"):
     * la figura del modelo {@code mannequin} hace de cuerpo, así que acá no
     * hay piel que reconstruir. Mismo orden (el de {@code prendas}) y misma
     * pollera que {@link #render}; las medias con volumen también (sin inflar
     * la pierna de la figura).
     */
    /**
     * Con el busto de la figura (2026-10-02, maniquí "agregale la opcion de ponerle tetas"), o null, y su armadura: la
     * capucha y lo de las filas de arriba de la armadura pasan por fuera de ella, como en el jugador.
     */
    public static void dibujarTela(BipedEntityModel<?> biped, boolean slim, List<ItemStack> prendas,
                                   MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz,
                                   @Nullable com.modamod.render.relieve.BustoRender.Busto busto, ArmaduraVisible armadura) {
        com.modamod.render.relieve.BustoRender.actual = busto;
        usarArmadura(armadura, prendas.size());
        calzado = calzadoDeTela;
        calzadoDeTela = null;
        pantalonSobreCalzado = false;   // el maniquí no tiene lista: nada de valores viejos del último jugador dibujado
        anotarCinto(prendas);
        try {
            dibujarTelaConBusto(biped, slim, prendas, matrices, vertexConsumers, luz);
            EfectoTrim.volcar();   // el brillo de la última pieza con acabado (2026-10-22)
        } finally {
            com.modamod.render.relieve.BustoRender.actual = null;
            calzado = null;
        }
    }

    private static void dibujarTelaConBusto(BipedEntityModel<?> biped, boolean slim, List<ItemStack> prendas,
                                            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int luz) {
        anotarPollera(prendas);
        // La capa va antes del corte de abajo, como en el jugador (2026-10-19: mismo orden que el jugador).
        dibujarCapa(prendas, null, biped, matrices, vertexConsumers, luz, 0f, false);
        Map<Parte, List<Pieza>> porParte = new EnumMap<>(Parte.class);
        Map<Pieza, ItemStack> origen = new java.util.IdentityHashMap<>();
        Map<Pieza, Integer> profundidad = new java.util.IdentityHashMap<>();
        agrupar(prendas, null, porParte, origen, profundidad);
        for (Map.Entry<Parte, List<Pieza>> entrada : porParte.entrySet()) {
            Parte parte = entrada.getKey();
            List<Pieza> piezas = entrada.getValue();
            ModelPart delModelo = CuerpoGeometria.delJugador(biped, parte);
            // Medias con volumen también en la figura (2026-10-22, el Maniquí no mostraba el volumen de las medias), con la misma regla
            // que el jugador: solo si son la tela de más arriba de esa pierna. La figura no se infla por dentro: la tela es un tubo cerrado.
            Pieza conVolumen = (parte == Parte.PIERNA_DER || parte == Parte.PIERNA_IZQ) && piezas.get(piezas.size() - 1).volumenPierna()
                    ? piezas.get(piezas.size() - 1) : null;
            // Con el relieve de la figura (2026-10-19, "tela con relieve como el jugador"): el mismo mapa con el que se dibuja su cuerpo.
            dibujarPiezas(parte, slim, piezas, conVolumen, delModelo, matrices, vertexConsumers, luz,
                    mapaFigura != null ? mapaFigura : com.modamod.render.relieve.MapaRelieve.PLANO, origen, profundidad);
        }
        CuelloYCapucha.dibujar(prendas, biped, matrices, vertexConsumers, luz);
        ApliqueRenderer.dibujar(prendas, biped, matrices, vertexConsumers, luz);
        dibujarTranslucidas();
        // Sin entidad: la pollera queda quieta (sin inercia ni twirl).
        dibujarPollera(prendas, null, biped, matrices, vertexConsumers, luz, 0f);
        dibujarCapa(prendas, null, biped, matrices, vertexConsumers, luz, 0f, true);
    }

    /** Progreso (0..1) del twirl de la figura del maniqui, o -1 si no gira; lo fija {@code ManiquiRenderer} mientras la dibuja. */
    public static float twirlFigura = -1f;

    /** Mapa de relieve del cuerpo de la figura del maniqui mientras se dibuja su tela, o null (tela plana). */
    public static com.modamod.render.relieve.MapaRelieve mapaFigura = null;

    /** La ropa de cuerpo puesta, en el orden de dibujo (de adentro hacia afuera; ver {@link com.modamod.ropa.ListaDeRopa#paraDibujar}). */
    public static List<ItemStack> equipadas(LivingEntity entidad) {
        var lugares = com.modamod.ropa.ListaDeRopa.de(entidad);
        return lugares == null ? new ArrayList<>() : com.modamod.ropa.ListaDeRopa.paraDibujar(com.modamod.ropa.ListaDeRopa.lugares(lugares));
    }

    /**
     * Si el jugador usa el modelo de brazos finos.
     *
     * Se lee de la skin y no del modelo porque PlayerEntityModel guarda su
     * thinArms privado y no lo expone.
     */
    private static boolean esSlim(LivingEntity entidad) {
        return entidad instanceof AbstractClientPlayerEntity jugador
                && jugador.getSkinTextures().model() == SkinTextures.Model.SLIM;
    }
}
