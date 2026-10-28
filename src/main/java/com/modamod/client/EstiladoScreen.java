package com.modamod.client;

import com.modamod.aplique.Aplique;
import com.modamod.estilado.EstiladoBlockEntity;
import com.modamod.estilado.EstiladoScreenHandler;
import com.modamod.estilado.PonerApliquePayload;
import com.modamod.garment.Parte;
import com.modamod.render.ApliqueRenderer;
import com.modamod.render.GarmentFeatureRenderer;
import com.modamod.render.Pieza;
import com.modamod.render.PiezasDePrenda;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Mesa de estilado (2026-10-01, "agregarles modelos 3d anclados en la
 * geometria de la prenda" + "libre con click en la vista 3D"): a la izquierda
 * el jugador con la prenda del slot (y sus apliques), a la derecha los slots,
 * la lista de apliques puestos y los botones de giro/tamaño/quitar.
 *
 * <h2>El click</h2>
 * Mientras se dibuja la vista previa, {@code GarmentFeatureRenderer} guarda por
 * parte la matriz espacio-de-la-parte → pantalla ({@code capturaPoses}). Para
 * el click se invierte esa matriz, el mouse pasa a ser un rayo en el espacio de
 * la parte (la GUI es ortogonal: el rayo va en z) y se corta contra la caja de
 * tela de cada pieza de la prenda (inflada por su calce, solo las filas con
 * tela). Gana el punto más cercano a la cámara (el z de pantalla más grande).
 */
public class EstiladoScreen extends HandledScreen<EstiladoScreenHandler> {

    private static final Identifier TEXTURE = Identifier.of("modamod", "textures/gui/container/estilado.png");
    // Ventana de 560x408 (2026-10-12, plan de GUIs): visor 8,24 304x278 y columna de controles en x 318.
    static final int ANCHO = 560, ALTO = 408;
    private static final int PX1 = 8, PY1 = 24, PX2 = 312, PY2 = 302;
    /** Alto del panel de ajustes de la Mesa creativa (afuera de la ventana, a la derecha). */
    private static final int ALTO_PANEL = 350;   // con el casillero de objeto abajo (2026-10-14)
    private static final int X_DER = EstiladoScreenHandler.X_DERECHA;

    private float anguloVista = 0f;
    /** Inclinación de la vista (grados, 2026-10-04): + = se ve más desde arriba, − desde abajo. */
    private float inclinacionVista = 0f;
    private float zoomVista = 1f;
    private static final float INCLINACION_MAX = 85f;
    /** Mueve el muñeco para ver cómo se agita la tela de los apliques (2026-10-04). */
    private boolean sacudiendo = false;
    private boolean arrastrando = false;
    /** Dónde quedó cada aplique en la última vista previa (2026-10-04, para poner apliques sobre apliques). */
    private final Map<Integer, ApliqueRenderer.Captura> cajasApliques = new java.util.HashMap<>();
    /** Matrices de la última vista previa (por parte). */
    private final Map<Parte, Matrix4f> poses = new EnumMap<>(Parte.class);
    /** Las mallas de la pollera y la capa del último dibujo de la vista, en pantalla (2026-10-02). */
    private final Map<String, com.modamod.render.MallaCapturada> mallas = new java.util.HashMap<>();
    @Nullable private Text aviso;

    private final ButtonWidget[] btnApliques = new ButtonWidget[Aplique.MAXIMO_POR_PRENDA];
    private ButtonWidget btnGiro, btnEscala, btnQuitar, btnSacudir, btnVistaIzq, btnVistaDer;
    private ButtonWidget btnGiroAtras, btnGiroAdelante, btnEscalaAtras, btnEscalaAdelante;
    /** Fila del aplique (2026-10-22, botonera): cantidad en la fila del giro, separación en la de la escala. */
    private ButtonWidget btnFilaAtras, btnFila, btnFilaAdelante, btnSepAtras, btnSep, btnSepAdelante;
    /** Dónde quedó cada control de la columna (solo la Estilista; 2026-10-13, ver {@link DisposicionEstilista}). */
    java.util.Map<String, DisposicionEstilista.R> disp = java.util.Map.of();
    DisposicionEstilista.Modo modoDisp = DisposicionEstilista.Modo.APLIQUE;
    private String ultimoSolape = "";
    /** Controles del aplique de objeto (2026-10-04): arriba de la vista, solo con uno elegido. */
    private ButtonWidget btnObjModo, btnObjVariante;
    /**
     * Borcegos (2026-10-11, "no se puede independizar derecho e izquierdo?"): cada pie lleva lo suyo. {@code espejarPie}
     * (de fábrica prendido) repite lo que hacés en un pie, espejado, en el otro; {@code pieActivoIzq} es el pie al que
     * van los botones de color y dibujo (se cambia con el botón o clickeando un borcego en la vista).
     */
    private boolean espejarPie = true, pieActivoIzq = false;
    private net.minecraft.item.Item ultimaPrendaEspejo;
    private ButtonWidget btnEspejo, btnPie;
    /**
     * Colorear el sombrero de bruja (2026-10-05, "que se le apliquen los colores en la mesa de estilado sobre todo si
     * tiene tres areas"): con un sombrero en la prenda hay un botón que cambia los controles de apliques por 3 filas
     * (ala, cono, cinta) con un botón por cada color del retazo, y "retazo entero".
     */
    private boolean modoColor = false;
    /**
     * Correas libres (2026-10-05, "correas libres"): modo con dos clicks en la vista (inicio y fin), modo pegada o
     * colgante, ancho en px y "Quitar última" (que antes cancela el punto de inicio si ya hay uno).
     */
    private boolean modoCorrea = false;
    private com.modamod.correa.ModoCorrea correaModo = com.modamod.correa.ModoCorrea.PEGADA;
    private int correaAncho = 2;
    /** Largo de la cadena que cuelga (px, 2..32 de a 2): 2026-10-06, "ademas de ancho deberia poder agregarsele largo". */
    private int correaLargo = (int) com.modamod.correa.Correa.LARGO_INICIAL;
    private ButtonWidget btnCorreaLargo, btnCorreaLargoMenos, btnCorreaLargoMas;
    @Nullable
    private Toque correaInicio = null;
    private ButtonWidget btnCorreaModo, btnCorreaAncho, btnCorreaQuitar, btnCorreaColor;
    /** La lista de correas de la prenda (una por correa) y cuál está elegida (-1 = ninguna). */
    private final ButtonWidget[] btnCorreaLista = new ButtonWidget[com.modamod.correa.Correa.MAXIMO_POR_PRENDA];
    private int correaSel = -1;
    /** Dónde (pantalla) se hizo el primer click: la guía de las correas sobre malla, que no tienen un punto 3D. */
    private float[] correaInicioPantalla = null;
    /** El color del retazo (0..2) con el que pinta el click en la vista (2026-10-05): el último botón ■ apretado. */
    private int colorActivo = 0;
    private ButtonWidget btnColorEntero;
    /**
     * Las tres pestañas (2026-10-06, "conceptualmente son tres cosas aplique correa acabado"): 0 = Aplique, 1 = Correa,
     * 2 = Acabado (textura de tela, trim con material y, en el sombrero y las bandas, el color y dibujo de cada zona).
     */
    int pestana = 0;
    private final ButtonWidget[] btnPestana = new ButtonWidget[5];

    /** La Estilista suma una cuarta pestaña, las Bolsas (2026-10-12). */
    boolean conBolsas() { return false; }

    /** Gancho: la Estilista guarda lo elegido en cada pestaña por separado. */
    void alCambiarPestana(int antes, int ahora) { }
    private final ButtonWidget[][] btnColorZona = new ButtonWidget[3][3];
    private final ButtonWidget[] btnPatronZona = new ButtonWidget[3];
    /** Los controles de apliques que se esconden mientras se colorea. */
    private final List<ButtonWidget> grupoAplique = new java.util.ArrayList<>();


    public EstiladoScreen(EstiladoScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = ANCHO;
        this.backgroundHeight = ALTO;
        this.titleX = PX1;
        this.titleY = 9;
        this.playerInventoryTitleX = EstiladoScreenHandler.X_INVENTARIO;
        this.playerInventoryTitleY = EstiladoScreenHandler.Y_INVENTARIO - 10;
    }

    private ButtonWidget boton(int x, int y, int w, Text texto, String tooltip, Runnable accion) {
        ButtonWidget b = new EstiloPergamino.BotonPergamino(this.x + x, this.y + y + dyEstilista(), w, 16, texto, btn -> accion.run());
        if (tooltip != null) b.setTooltip(Tooltip.of(Text.translatable(tooltip)));
        this.addDrawableChild(b);
        return b;
    }

    /** La Estilista baja los controles para dejar arriba el selector de zona y las pestañas (2026-10-13). */
    private int dyEstilista() { return 0; }

    private void poner(ButtonWidget b, String clave) {
        if (b == null) return;
        DisposicionEstilista.R r = disp.get(clave);
        if (r == null) {
            b.visible = false;
            return;
        }
        b.setX(this.x + r.x());
        b.setY(this.y + r.y());
        b.setWidth(r.w());
    }

    /**
     * Acomoda TODOS los controles de la columna con la disposición de la pestaña (2026-10-13, "ordenalo bien chequea dos
     * veces"): lo que no figura en la disposición se esconde, así no puede haber dos controles en el mismo lugar.
     */
    private void reubicar(boolean zonas, boolean pares, boolean objeto) {
        if (!conBolsas()) return;
        modoDisp = pestana == 0 ? DisposicionEstilista.Modo.APLIQUE : pestana == 1 ? DisposicionEstilista.Modo.CORREA
                : pestana == 2 ? (zonas ? DisposicionEstilista.Modo.COLOR : DisposicionEstilista.Modo.ACABADO)
                : pestana == 3 ? DisposicionEstilista.Modo.BOLSAS : DisposicionEstilista.Modo.DISENOS;
        disp = DisposicionEstilista.calcular(new DisposicionEstilista.Estado(modoDisp, pares, objeto));
        for (int i = 0; i < btnApliques.length; i++) poner(btnApliques[i], "ap" + i);
        poner(btnGiroAtras, "giroAtras");
        poner(btnGiro, "giro");
        poner(btnGiroAdelante, "giroAdelante");
        poner(btnQuitar, "quitar");
        poner(btnEscalaAtras, "escalaAtras");
        poner(btnEscala, "escala");
        poner(btnEscalaAdelante, "escalaAdelante");
        poner(btnSacudir, "sacudir");
        poner(btnFilaAtras, "filaAtras");
        poner(btnFila, "fila");
        poner(btnFilaAdelante, "filaAdelante");
        poner(btnSepAtras, "sepAtras");
        poner(btnSep, "sep");
        poner(btnSepAdelante, "sepAdelante");
        // El slider de blandura vive en la pestaña Aplique, también con la Estilista creativa (2026-10-14, "el boton de
        // softness desaparecio"); el panel de la creativa la repite como Intensidad.
        DisposicionEstilista.R rb = disp.get("blandura");
        if (sliderNormal != null) {
            sliderNormal.visible = rb != null;
            if (rb != null) { sliderNormal.setX(this.x + rb.x()); sliderNormal.setY(this.y + rb.y()); sliderNormal.setWidth(rb.w()); }
        }
        poner(btnObjModo, "objModo");
        poner(btnObjVariante, "objVariante");
        poner(btnEspejo, "espejo");
        poner(btnPie, "pie");
        for (int i = 0; i < btnCorreaLista.length; i++) poner(btnCorreaLista[i], "cl" + i);
        poner(btnCorreaModo, "cModo");
        poner(btnCorreaAncho, "cAncho");
        poner(btnCorreaQuitar, "cQuitar");
        poner(btnCorreaColor, "cColor");
        poner(btnCorreaLargoMenos, "cLargoMenos");
        poner(btnCorreaLargo, "cLargo");
        poner(btnCorreaLargoMas, "cLargoMas");
        poner(btnTextura, "textura");
        for (int z = 0; z < 3; z++) {
            for (int c = 0; c < 3; c++) poner(btnColorZona[z][c], "zc" + z + c);
            poner(btnPatronZona[z], "zp" + z);
        }
        poner(btnColorEntero, "entero");
    }

    /** Revisa con los widgets reales que nada visible de la columna se pise; avisa una sola vez por situación. */
    private void verificarSolapes() {
        if (!conBolsas()) return;
        java.util.List<net.minecraft.client.gui.widget.ClickableWidget> v = new java.util.ArrayList<>();
        for (var c : this.children()) {
            if (c instanceof net.minecraft.client.gui.widget.ClickableWidget w && w.visible
                    && w.getX() >= this.x + DisposicionEstilista.X && w.getY() >= this.y + 20 && w.getY() <= this.y + 300) v.add(w);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.size(); i++) {
            for (int j = i + 1; j < v.size(); j++) {
                var a = v.get(i);
                var b = v.get(j);
                if (a.getX() < b.getX() + b.getWidth() && b.getX() < a.getX() + a.getWidth()
                        && a.getY() < b.getY() + b.getHeight() && b.getY() < a.getY() + a.getHeight()) {
                    sb.append('[').append(a.getMessage().getString()).append(" x ").append(b.getMessage().getString()).append(']');
                }
            }
        }
        String firma = pestana + ":" + sb;
        if (!firma.equals(ultimoSolape)) {
            ultimoSolape = firma;
            if (sb.length() > 0) org.slf4j.LoggerFactory.getLogger("modamod").warn("Estilista: controles superpuestos en la pestaña {}: {}", pestana, sb);
        }
    }

    private void clickBoton(int id) {
        this.client.interactionManager.clickButton(this.handler.syncId, id);
    }

    /** ¿El accesorio de la prenda tiene dos pies que se pintan por separado (los borcegos)? */
    private boolean conPares() {
        ItemStack p = handler.be.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        return p.getItem() instanceof com.modamod.item.ZonasTenibles zt && zt.tienePares();
    }

    /** Color o dibujo de una zona: en los borcegos va al pie activo y, con "Espejar", también al otro. */
    private void clickZona(int id) {
        if (!conPares()) {
            clickBoton(id);
            return;
        }
        clickBoton(pieActivoIzq ? id + EstiladoBlockEntity.BTN_PIE_IZQ : id);
        if (espejarPie) clickBoton(pieActivoIzq ? id : id + EstiladoBlockEntity.BTN_PIE_IZQ);
    }

    /**
     * El mismo toque del otro lado (2026-10-14, "que el boton mirror funcione para todas las prendas... en las de una
     * pieza tome el centro como referencia"): brazos y piernas pasan al de enfrente; el torso y la cabeza se espejan
     * contra su propio centro; en la pollera, la capa y el delantal (por u de la tela) contra el centro del frente.
     * La X se invierte y las caras este y oeste se cambian.
     */
    private static Toque espejoDe(Toque t) {
        // shortcut: la capucha no se espeja (no se sabe dónde está el centro de su tela de Blockbench); si hace falta, medirlo en el .bbmodel.
        if (t.superficie() == Aplique.Superficie.CAPUCHA) return t;
        if (t.superficie() != Aplique.Superficie.CAJA) {
            float u = t.x(), u2;
            if (t.superficie() == Aplique.Superficie.CAPA) {
                // El paño (u 1..11, centro 6) y el forro (u 12..22, centro 17) de la capa.
                u2 = u >= 1 && u < 11 ? 12f - u : u >= 12 && u < 22 ? 34f - u : u;
            } else {
                // Pollera y delantal: la franja del torso (u 16..40, vuelta de 24) con el centro del frente en 24.
                u2 = 48f - u;
                if (u2 < 16f) u2 += 24f;
                else if (u2 >= 40f) u2 -= 24f;
            }
            return new Toque(t.parte(), u2, t.y(), t.z(), t.cara(), t.profundidad(), t.superficie(), t.padre());
        }
        Parte otra = switch (t.parte()) {
            case PIERNA_DER -> Parte.PIERNA_IZQ;
            case PIERNA_IZQ -> Parte.PIERNA_DER;
            case BRAZO_DER -> Parte.BRAZO_IZQ;
            case BRAZO_IZQ -> Parte.BRAZO_DER;
            default -> t.parte();
        };
        Direction c = t.cara() == Direction.EAST ? Direction.WEST : t.cara() == Direction.WEST ? Direction.EAST : t.cara();
        return new Toque(otra, -t.x(), t.y(), t.z(), c, t.profundidad(), t.superficie(), t.padre());
    }

    /** ¿El espejo cae casi encima del toque (un aplique justo en el centro)? Entonces no se duplica. */
    private static boolean sobreSiMismo(Toque t, Toque e) {
        return e.parte() == t.parte() && e.cara() == t.cara() && Math.abs(e.x() - t.x()) < 0.75f && Math.abs(e.y() - t.y()) < 0.75f
                && Math.abs(e.z() - t.z()) < 0.75f;
    }

    @Override
    protected void init() {
        EstiloPergamino.usarTema(EstiloPergamino.Tema.LILA);
        super.init();
        for (int i = 0; i < btnApliques.length; i++) {
            int id = EstiladoBlockEntity.BTN_SELECCIONAR_BASE + i;
            btnApliques[i] = boton(X_DER + Math.round(i * 13.5f), 66, 13, Text.literal(Integer.toString(i + 1)),
                    "modamod.estilado.tooltip.aplique", () -> clickBoton(id));
            grupoAplique.add(btnApliques[i]);
        }
        btnGiroAtras = boton(X_DER, 88, 14, Text.literal("<"), "modamod.estilado.tooltip.giro", () -> clickBoton(EstiladoBlockEntity.BTN_GIRO_ATRAS));
        grupoAplique.add(btnGiroAtras);
        btnGiro = boton(X_DER + 15, 88, 64, Text.empty(), "modamod.estilado.tooltip.giro", () -> clickBoton(EstiladoBlockEntity.BTN_GIRO));
        grupoAplique.add(btnGiro);
        btnGiroAdelante = boton(X_DER + 80, 88, 14, Text.literal(">"), "modamod.estilado.tooltip.giro", () -> clickBoton(EstiladoBlockEntity.BTN_GIRO));
        grupoAplique.add(btnGiroAdelante);
        btnEscalaAtras = boton(X_DER, 108, 14, Text.literal("<"), "modamod.estilado.tooltip.escala", () -> clickBoton(EstiladoBlockEntity.BTN_ESCALA_ATRAS));
        grupoAplique.add(btnEscalaAtras);
        btnEscala = boton(X_DER + 15, 108, 64, Text.empty(), "modamod.estilado.tooltip.escala", () -> clickBoton(EstiladoBlockEntity.BTN_ESCALA));
        grupoAplique.add(btnEscala);
        btnEscalaAdelante = boton(X_DER + 80, 108, 14, Text.literal(">"), "modamod.estilado.tooltip.escala", () -> clickBoton(EstiladoBlockEntity.BTN_ESCALA));
        grupoAplique.add(btnEscalaAdelante);
        btnFilaAtras = boton(X_DER + 166, 88, 14, Text.literal("<"), "modamod.estilado.tooltip.fila", () -> clickBoton(EstiladoBlockEntity.BTN_FILA_MENOS));
        btnFila = boton(X_DER + 181, 88, 36, Text.empty(), "modamod.estilado.tooltip.fila", () -> clickBoton(EstiladoBlockEntity.BTN_FILA_MAS));
        btnFilaAdelante = boton(X_DER + 218, 88, 14, Text.literal(">"), "modamod.estilado.tooltip.fila", () -> clickBoton(EstiladoBlockEntity.BTN_FILA_MAS));
        btnSepAtras = boton(X_DER + 166, 108, 14, Text.literal("<"), "modamod.estilado.tooltip.separacion", () -> clickBoton(EstiladoBlockEntity.BTN_SEPARACION_MENOS));
        btnSep = boton(X_DER + 181, 108, 36, Text.empty(), "modamod.estilado.tooltip.separacion", () -> clickBoton(EstiladoBlockEntity.BTN_SEPARACION_MAS));
        btnSepAdelante = boton(X_DER + 218, 108, 14, Text.literal(">"), "modamod.estilado.tooltip.separacion", () -> clickBoton(EstiladoBlockEntity.BTN_SEPARACION_MAS));
        for (ButtonWidget b : new ButtonWidget[] { btnFilaAtras, btnFila, btnFilaAdelante, btnSepAtras, btnSep, btnSepAdelante }) grupoAplique.add(b);
        btnQuitar = boton(X_DER + 98, 88, 64, Text.translatable("modamod.estilado.quitar"),
                "modamod.estilado.tooltip.quitar", () -> clickBoton(EstiladoBlockEntity.BTN_QUITAR));
        grupoAplique.add(btnQuitar);
        if (!conBolsas()) {
        btnVistaIzq = boton(X_DER + 98, 108, 31, Text.literal("⟲"), "modamod.estilado.tooltip.vista",
                () -> anguloVista = Math.floorMod(Math.round(anguloVista) - 45, 360));
        btnVistaDer = boton(X_DER + 131, 108, 31, Text.literal("⟳"), "modamod.estilado.tooltip.vista",
                () -> anguloVista = Math.floorMod(Math.round(anguloVista) + 45, 360));
        }
        // Textura de tela (2026-10-01, relieve): con un Molde de textura en el slot del molde.
        btnTextura = boton(X_DER, 66, 162, Text.empty(), "modamod.estilado.tooltip.textura",
                () -> clickBoton(EstiladoBlockEntity.BTN_TEXTURA));
        // Pestañas (2026-10-06): abajo de los controles, arriba del título del inventario.
        int nPest = conBolsas() ? 5 : 3, wPest = conBolsas() ? 46 : 54;
        for (int i = 0; i < nPest; i++) {
            final int cual = i;
            btnPestana[i] = new EstiloPergamino.BotonPergamino(this.x + X_DER + i * wPest, this.y + (conBolsas() ? 42 : 147), wPest, 13, Text.empty(), b -> {
                int antes = pestana;
                pestana = cual;
                correaInicio = null;
                alCambiarPestana(antes, cual);
            });
            btnPestana[i].setTooltip(Tooltip.of(Text.translatable("modamod.estilado.tooltip.pestana." + i)));
            this.addDrawableChild(btnPestana[i]);
        }
        // Colorear el sombrero (2026-10-05): 3 filas (ala, cono, cinta) x 3 colores del retazo, y retazo entero.
        // Correas libres (2026-10-05): modo, pegada/colgante, ancho y quitar.
        for (int i = 0; i < btnCorreaLista.length; i++) {
            final int idx = i;
            btnCorreaLista[i] = boton(X_DER + i * 20, 66, 19, Text.literal(Integer.toString(i + 1)),
                    "modamod.correa.tooltip.lista", () -> {
                        correaSel = correaSel == idx ? -1 : idx;
                        var l = handler.be.correas();
                        if (correaSel >= 0 && correaSel < l.size()) {
                            correaModo = l.get(correaSel).modo();
                            correaAncho = Math.round(l.get(correaSel).ancho());
                            correaLargo = Math.round(l.get(correaSel).largo());
                        }
                    });
        }
        btnCorreaModo = boton(X_DER, 88, 80, Text.empty(), "modamod.correa.tooltip.modo", () -> {
            correaModo = correaModo.siguiente();
            if (correaSel >= 0) clickBoton(EstiladoBlockEntity.BTN_CORREA_MODO_BASE + correaSel * 4 + correaModo.ordinal());
        });
        btnCorreaAncho = boton(X_DER + 82, 88, 80, Text.empty(), "modamod.correa.tooltip.ancho", () -> {
            correaAncho = correaAncho % 4 + 1;
            if (correaSel >= 0) clickBoton(EstiladoBlockEntity.BTN_CORREA_ANCHO_BASE + correaSel * 4 + (correaAncho - 1));
        });
        btnCorreaLargoMenos = boton(X_DER, 128, 14, Text.literal("−"), "modamod.correa.tooltip.largo", () -> cambiarLargo(-1));
        btnCorreaLargo = boton(X_DER + 15, 128, 64, Text.empty(), "modamod.correa.tooltip.largo", () -> cambiarLargo(1));
        btnCorreaLargoMas = boton(X_DER + 80, 128, 14, Text.literal("+"), "modamod.correa.tooltip.largo", () -> cambiarLargo(1));
        btnCorreaQuitar = boton(X_DER, 110, 80, Text.empty(), "modamod.correa.tooltip.quitar", () -> {
            if (correaInicio != null) {
                correaInicio = null;
            } else if (correaSel >= 0) {
                clickBoton(EstiladoBlockEntity.BTN_CORREA_QUITAR_BASE + correaSel);
                correaSel = -1;
            } else {
                clickBoton(EstiladoBlockEntity.BTN_CORREA_QUITAR);
            }
        });
        btnCorreaColor = boton(X_DER + 82, 110, 80, Text.translatable("modamod.correa.boton.recolorear"),
                "modamod.correa.tooltip.recolorear", () -> {
                    if (correaSel >= 0) clickBoton(EstiladoBlockEntity.BTN_CORREA_COLOREAR_BASE + correaSel);
                });
        for (int z = 0; z < 3; z++) {
            for (int c = 0; c < 3; c++) {
                int id = EstiladoBlockEntity.BTN_COLOR_BASE + z * 4 + c;
                final int color = c;
                btnColorZona[z][c] = boton(X_DER + 46 + c * 17, 66 + z * 22, 15, Text.literal("■"),
                        "modamod.estilado.tooltip.color_zona", () -> {
                            colorActivo = color;
                            clickZona(id);
                        });
            }
        }
        for (int z = 0; z < 3; z++) {
            final int id = EstiladoBlockEntity.BTN_PATRON_BASE + z;
            btnPatronZona[z] = boton(X_DER + 98, 66 + z * 22, 64, Text.empty(), "modamod.estilado.tooltip.patron_zona",
                    () -> clickZona(id));
        }
        btnColorEntero = boton(X_DER, 132, 96, Text.translatable("modamod.estilado.color.entero"),
                "modamod.estilado.tooltip.color_entero", () -> clickZona(EstiladoBlockEntity.BTN_COLOR_ENTERO));
        // Sacudir (2026-10-04, "boton de sacudir... alternar mover el muñeco"): prende y apaga el vaivén.
        btnSacudir = boton(X_DER + 98, 132, 64, Text.empty(), "modamod.estilado.tooltip.sacudir",
                () -> sacudiendo = !sacudiendo);
        // Aplique de objeto (2026-10-04, "seleccionar si item o bloque"): en la franja de arriba de la vista; solo
        // se ven con un aplique de objeto elegido. La colocación y el movimiento están en el panel lateral.
        int by = conBolsas() ? 150 - 0 : PY1 + 3;   // la Estilista los lleva a la columna de controles
        int bx0 = conBolsas() ? X_DER : PX1 + 4, bpaso = conBolsas() ? 54 : 60;
        // Con la Estilista creativa, Item y Variante van en su panel, junto al casillero de objeto (2026-10-14).
        boolean enPanel = conBolsas() && handler.be.creativa();
        if (enPanel) { bx0 = ANCHO + 4 + panelExtra() + 54; by = 301; bpaso = 20; }
        btnObjModo = boton(bx0, by, enPanel ? 100 : conBolsas() ? 53 : 58, Text.empty(), "modamod.estilado.tooltip.obj_modo",
                () -> clickBoton(EstiladoBlockEntity.BTN_OBJ_MODO));
        btnObjVariante = boton(bx0 + (enPanel ? 0 : bpaso), enPanel ? by + bpaso : by, enPanel ? 100 : conBolsas() ? 53 : 58, Text.empty(), "modamod.estilado.tooltip.obj_variante",
                () -> clickBoton(EstiladoBlockEntity.BTN_OBJ_VARIANTE));
        btnEspejo = conBolsas() ? boton(X_DER, 132, 96, Text.empty(), "modamod.estilado.tooltip.espejo", () -> espejarPie = !espejarPie)
                : boton(bx0 + bpaso * 2, by, 72, Text.empty(), "modamod.estilado.tooltip.espejo", () -> espejarPie = !espejarPie);
        btnPie = boton(X_DER, 44, 80, Text.empty(), "modamod.estilado.tooltip.pie", () -> pieActivoIzq = !pieActivoIzq);
        // Tira de enfoques abajo del visor (2026-10-12): acerca la cámara a la parte del cuerpo para poner apliques cómodo.
        var enfoques = com.modamod.modelado.ZonasModelado.Enfoque.values();
        for (int i = 0; i < enfoques.length; i++) {
            var e = enfoques[i];
            btnEnfoque[i] = new ButtonWidget(conBolsas() ? 0 : this.x + PX1 + 2 + i * 43, conBolsas() ? -100 : this.y + PY2 - 17, conBolsas() ? 29 : 42, 14,
                    Text.translatable("modamod.estilado.enfoque." + e.name().toLowerCase(java.util.Locale.ROOT)),
                    b -> enfoque = e, d -> Text.empty()) {
                @Override
                protected void renderWidget(DrawContext c, int mx, int my, float d) {
                    c.getMatrices().push();
                    c.getMatrices().translate(0, 0, 500);   // la prenda tiene profundidad hasta ~250
                    EstiloPergamino.fondoBoton(c, getX(), getY(), getWidth(), getHeight(), isHovered() || enfoque == e, active);
                    c.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2, getY() + 3,
                            enfoque == e ? 0xFFFFE0A8 : EstiloPergamino.TEXTO_CLARO);
                    c.getMatrices().pop();
                }
            };
            this.addDrawableChild(btnEnfoque[i]);
        }
        // Mesa creativa (2026-10-01): elegir cualquier molde sin tenerlo.
        ButtonWidget moldeCreativo = boton(X_DER + 110, 48, 52, Text.translatable("modamod.estilado.siguiente_molde"),
                "modamod.estilado.tooltip.siguiente_molde", () -> clickBoton(EstiladoBlockEntity.BTN_SIGUIENTE_MOLDE));
        moldeCreativo.visible = handler.be.creativa() && !conBolsas();
        crearPanel();
    }

    private ButtonWidget btnTextura;
    /** Enfoque de la cámara del visor (2026-10-12): punto del cuerpo y zoom; se llega con una transición suave. */
    com.modamod.modelado.ZonasModelado.Enfoque enfoque = com.modamod.modelado.ZonasModelado.Enfoque.ENTERO;
    private float focoActual = com.modamod.modelado.ZonasModelado.Enfoque.ENTERO.focoY,
            tamActual = com.modamod.modelado.ZonasModelado.Enfoque.ENTERO.tamano;
    private long ultimoCuadroMs = -1;
    final ButtonWidget[] btnEnfoque = new ButtonWidget[com.modamod.modelado.ZonasModelado.Enfoque.values().length];

    // ── panel lateral (2026-10-04, "posición, rotación, escala, cara de contacto, profundidad, pivote, oscilación") ──
    private static final int PW = 160;
    /** Una página del panel: 0 = Colocación, 1 = Movimiento, 2 = Moldes (la biblioteca de la Mesa creativa). */
    private int paginaPanel = 0;
    /** La biblioteca de moldes: 12 filas por vez (2026-10-04). */
    private static final int FILAS_BIBLIO = 12;
    private int paginaBiblio = 0;
    private final ButtonWidget[] btnBiblioDar = new ButtonWidget[FILAS_BIBLIO], btnBiblioBorrar = new ButtonWidget[FILAS_BIBLIO];
    private ButtonWidget btnPaginaMoldes, btnBiblioAnt, btnBiblioSig;
    private int panelX;

    /** Cuánto se corre a la derecha el panel de ajustes (la Estilista pone su panel de máquina primero). */
    int panelExtra() { return 0; }

    /** La Estilista no usa el slot de prenda de la Mesa (2026-10-08): se tapa el marco que trae el fondo. */
    boolean sinSlotPrenda() { return false; }
    private ButtonWidget btnPaginaColocacion, btnPaginaMovimiento, btnRestablecer, btnPivote, btnEje, btnCrearMolde;
    /** El slider de blandura de la Mesa normal (2026-10-04): como estaba antes del panel lateral. */
    private SliderAjuste sliderNormal;
    /** El nombre del molde que se va a fabricar (2026-10-04, "me falta un casillero para ponerle el nombre al aplique"). */
    private net.minecraft.client.gui.widget.TextFieldWidget txtNombreMolde;

    /** Si el molde es uno personalizado de un objeto (no pide retazo: la muestra de color es opcional). */
    private static boolean plantillaDeObjeto(ItemStack molde) {
        Aplique p = molde.getItem() instanceof com.modamod.aplique.MoldeApliquePersonalizadoItem
                ? com.modamod.aplique.MoldeApliquePersonalizadoItem.plantilla(molde) : null;
        return p != null && p.objeto() != null;
    }

    /** La Mesa creativa tiene el slot de objeto, el panel lateral y la fábrica de moldes; la normal, no. */
    private boolean creativa() {
        return handler.be.creativa();
    }
    private final ButtonWidget[] btnCaras = new ButtonWidget[6];
    private final List<SliderAjuste> slidersColocacion = new java.util.ArrayList<>();
    private final List<SliderAjuste> slidersMovimiento = new java.util.ArrayList<>();
    /** La cara de contacto de cada botón: arriba, abajo, izquierda, derecha, frente, atrás (del objeto). */
    private static final Direction[] CARAS = { Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST,
            Direction.NORTH, Direction.SOUTH };
    private static final String[] NOMBRE_CARA = { "arriba", "abajo", "izquierda", "derecha", "frente", "atras" };

    /** El aplique elegido, o null. */
    @Nullable
    private Aplique elegido() {
        List<Aplique> l = handler.be.apliques();
        int sel = handler.be.seleccionado();
        return sel >= 0 && sel < l.size() ? l.get(sel) : null;
    }

    /** Aplica un cambio al aplique elegido: se ve YA en la vista (copia local) y se manda al servidor. */
    private void ajustar(java.util.function.UnaryOperator<Aplique> cambio) {
        Aplique a = elegido();
        if (a == null) return;
        Aplique nuevo = cambio.apply(a);
        int sel = handler.be.seleccionado();
        handler.be.ajustar(sel, nuevo.colocacion(), nuevo.oscilacion(), nuevo.blandura());
        ClientPlayNetworking.send(new com.modamod.estilado.AjustarApliquePayload(handler.be.getPos(), sel,
                nuevo.colocacion(), nuevo.oscilacion(), nuevo.blandura()));
    }

    /** Un slider del panel: mapea su 0..1 a [min, max] en pasos, lee el valor del aplique elegido y manda el cambio. */
    private final class SliderAjuste extends net.minecraft.client.gui.widget.SliderWidget {
        private final String clave;
        private final float min, max, paso;
        private final java.util.function.Function<Aplique, Float> leer;
        private final java.util.function.BiFunction<Aplique, Float, Aplique> escribir;
        private final boolean porcentaje;
        private boolean editando = false;
        private float ultimo = Float.NaN;

        SliderAjuste(int x, int y, int ancho, String clave, float min, float max, float paso, boolean porcentaje,
                     java.util.function.Function<Aplique, Float> leer,
                     java.util.function.BiFunction<Aplique, Float, Aplique> escribir) {
            super(x, y, ancho, 12, Text.empty(), 0.5);
            this.clave = clave;
            this.min = min;
            this.max = max;
            this.paso = paso;
            this.porcentaje = porcentaje;
            this.leer = leer;
            this.escribir = escribir;
        }

        private float valor() {
            float v = min + (float) value * (max - min);
            return Math.round(v / paso) * paso;
        }

        /** Sigue al aplique elegido, salvo mientras se lo arrastra. */
        void refrescar(@Nullable Aplique a) {
            active = a != null;
            if (a != null && !editando) {
                float v = leer.apply(a);
                value = (v - min) / (max - min);
            }
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float v = valor();
            String t = porcentaje ? Integer.toString(Math.round(v * 100))
                    : (v == Math.rint(v) ? Integer.toString((int) v) : String.format(java.util.Locale.ROOT, "%.2f", v));
            setMessage(Text.translatable("modamod.estilado.panel." + clave, t));
        }

        @Override
        protected void applyValue() {
            float v = valor();
            if (v == ultimo) return;
            ultimo = v;
            ajustar(a -> escribir.apply(a, v));
        }

        @Override
        public void onClick(double mx, double my) {
            editando = true;
            ultimo = Float.NaN;
            super.onClick(mx, my);
        }

        @Override
        public void onRelease(double mx, double my) {
            super.onRelease(mx, my);
            editando = false;
        }

        @Override
        public void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            int x0 = getX(), y0 = getY(), w = getWidth(), h = getHeight();
            c.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF2A180C);
            c.fill(x0, y0, x0 + w, y0 + h, active ? 0xFF5A4028 : 0xFF3A2C1C);
            c.fill(x0, y0 + h - 1, x0 + w, y0 + h, EstiloPergamino.tema().claro);
            int mango = x0 + (int) (value * (w - 6));
            EstiloPergamino.fondoBoton(c, mango, y0, 6, h, isHovered(), active);
            var fuente = MinecraftClient.getInstance().textRenderer;
            Text m = getMessage();
            int tx = x0 + (w - fuente.getWidth(m)) / 2, ty = y0 + (h - 8) / 2 + 1;
            c.drawText(fuente, m, tx + 1, ty + 1, 0xFF2A180C, false);
            c.drawText(fuente, m, tx, ty, active ? EstiloPergamino.TEXTO_CLARO : EstiloPergamino.TEXTO_APAGADO, false);
        }
    }

    private SliderAjuste slider(List<SliderAjuste> pagina, int y, String clave, float min, float max, float paso,
                                boolean porcentaje, java.util.function.Function<Aplique, Float> leer,
                                java.util.function.BiFunction<Aplique, Float, Aplique> escribir, String tooltip) {
        SliderAjuste s = new SliderAjuste(panelX + 6, this.y + y, PW - 12, clave, min, max, paso, porcentaje, leer, escribir);
        if (tooltip != null) s.setTooltip(Tooltip.of(Text.translatable(tooltip)));
        this.addDrawableChild(s);
        pagina.add(s);
        return s;
    }

    private ButtonWidget botonPanel(int x, int y, int w, Text texto, String tooltip, Runnable accion) {
        ButtonWidget b = new EstiloPergamino.BotonPergamino(panelX + x, this.y + y, w, 14, texto, btn -> accion.run());
        if (tooltip != null) b.setTooltip(Tooltip.of(Text.translatable(tooltip)));
        this.addDrawableChild(b);
        return b;
    }

    private void crearPanel() {
        // A la derecha de la ventana; si no hay lugar, a la izquierda.
        panelX = this.x + ANCHO + 4 + panelExtra();
        if (panelX + PW > this.width && this.x - PW - 4 >= 0) panelX = this.x - PW - 4;
        slidersColocacion.clear();
        slidersMovimiento.clear();
        btnPaginaColocacion = botonPanel(6, 6, 48, Text.translatable("modamod.estilado.panel.pagina.colocacion"),
                null, () -> paginaPanel = 0);
        btnPaginaMovimiento = botonPanel(56, 6, 48, Text.translatable("modamod.estilado.panel.pagina.movimiento"),
                null, () -> paginaPanel = 1);
        btnPaginaMoldes = botonPanel(106, 6, 48, Text.translatable("modamod.estilado.panel.pagina.moldes"),
                "modamod.estilado.tooltip.biblioteca", () -> paginaPanel = 2);
        // Página Moldes: la biblioteca de lo que fabricó esta Mesa (click = una copia; la X lo borra de la lista).
        for (int i = 0; i < FILAS_BIBLIO; i++) {
            int fila = i;
            btnBiblioDar[i] = botonPanel(6, 28 + i * 15, 126, Text.empty(), "modamod.estilado.tooltip.biblioteca_dar",
                    () -> this.client.interactionManager.clickButton(this.handler.syncId,
                            EstiladoBlockEntity.BTN_BIBLIO_DAR_BASE + paginaBiblio * FILAS_BIBLIO + fila));
            btnBiblioBorrar[i] = botonPanel(134, 28 + i * 15, 20, Text.literal("x"), "modamod.estilado.tooltip.biblioteca_borrar",
                    () -> this.client.interactionManager.clickButton(this.handler.syncId,
                            EstiladoBlockEntity.BTN_BIBLIO_BORRAR_BASE + paginaBiblio * FILAS_BIBLIO + fila));
        }
        btnBiblioAnt = botonPanel(6, 232, 34, Text.literal("<"), null, () -> paginaBiblio = 0);
        btnBiblioSig = botonPanel(44, 232, 34, Text.literal(">"), null, () -> paginaBiblio = 1);

        // Página Colocación: cara de contacto (solo objetos), profundidad, desplazamiento, rotación, escala.
        for (int i = 0; i < 6; i++) {
            int idx = i;
            btnCaras[i] = botonPanel(6 + (i % 3) * 50, 40 + (i / 3) * 16, 48,
                    Text.translatable("modamod.estilado.cara." + NOMBRE_CARA[i]), "modamod.estilado.tooltip.cara_base",
                    () -> ajustar(a -> a.conColocacion(a.colocacion().conCara(CARAS[idx]))));
        }
        slider(slidersColocacion, 74, "profundidad", -8f, 8f, 0.25f, false, a -> a.colocacion().campo(0),
                (a, v) -> a.conColocacion(a.colocacion().conCampo(0, v)), "modamod.estilado.tooltip.profundidad");
        String[] ejes = { "x", "y", "z" };
        for (int i = 0; i < 3; i++) {
            int c = i;
            slider(slidersColocacion, 90 + i * 14, "d" + ejes[i], -16f, 16f, 0.25f, false, a -> a.colocacion().campo(1 + c),
                    (a, v) -> a.conColocacion(a.colocacion().conCampo(1 + c, v)), null);
            slider(slidersColocacion, 136 + i * 14, "r" + ejes[i], -180f, 180f, 5f, false, a -> a.colocacion().campo(4 + c),
                    (a, v) -> a.conColocacion(a.colocacion().conCampo(4 + c, v)), null);
            slider(slidersColocacion, 182 + i * 14, "s" + ejes[i], 0.1f, 4f, 0.05f, true, a -> a.colocacion().campo(7 + c),
                    (a, v) -> a.conColocacion(a.colocacion().conCampo(7 + c, v)), null);
        }

        // Página Movimiento: pivote, intensidad (= blandura), velocidad, amplitud y eje.
        btnPivote = botonPanel(6, 34, PW - 12, Text.empty(), "modamod.estilado.tooltip.pivote", () -> ajustar(a -> {
            var v = com.modamod.aplique.Oscilacion.Pivote.values();
            return a.conOscilacion(a.oscilacion().conPivote(v[(a.oscilacion().pivote().ordinal() + 1) % v.length]));
        }));
        for (int i = 0; i < 3; i++) {
            int c = i;
            slider(slidersMovimiento, 52 + i * 14, "po" + ejes[i], -16f, 16f, 0.25f, false, a -> a.oscilacion().campo(c),
                    (a, v) -> a.conOscilacion(a.oscilacion().conCampo(c, v)), "modamod.estilado.tooltip.pivote_offset");
        }
        slider(slidersMovimiento, 98, "intensidad", 0f, 1f, 0.1f, true, Aplique::blandura,
                (a, v) -> a.conBlandura(v), "modamod.estilado.tooltip.blandura");
        slider(slidersMovimiento, 112, "velocidad", 0f, 4f, 0.1f, false, a -> a.oscilacion().campo(3),
                (a, v) -> a.conOscilacion(a.oscilacion().conCampo(3, v)), "modamod.estilado.tooltip.velocidad");
        slider(slidersMovimiento, 126, "amplitud", 0f, 60f, 1f, false, a -> a.oscilacion().campo(4),
                (a, v) -> a.conOscilacion(a.oscilacion().conCampo(4, v)), "modamod.estilado.tooltip.amplitud");
        btnEje = botonPanel(6, 144, PW - 12, Text.empty(), "modamod.estilado.tooltip.eje", () -> ajustar(a -> {
            var v = com.modamod.aplique.Oscilacion.Eje.values();
            return a.conOscilacion(a.oscilacion().conEje(v[(a.oscilacion().eje().ordinal() + 1) % v.length]));
        }));
        sliderNormal = new SliderAjuste(this.x + X_DER, this.y + 150 + dyEstilista(), 162, "blandura", 0f, 1f, 0.1f, true, Aplique::blandura,
                (a, v) -> a.conBlandura(v));
        sliderNormal.setTooltip(Tooltip.of(Text.translatable("modamod.estilado.tooltip.blandura")));
        this.addDrawableChild(sliderNormal);
        btnCrearMolde = botonPanel(82, 268, 72, Text.translatable("modamod.estilado.panel.crear_molde"),
                "modamod.estilado.tooltip.crear_molde", () -> {
                    ClientPlayNetworking.send(new com.modamod.estilado.CrearMoldePayload(handler.be.getPos(), txtNombreMolde.getText()));
                    txtNombreMolde.setText("");
                });
        txtNombreMolde = new net.minecraft.client.gui.widget.TextFieldWidget(this.textRenderer, panelX + 6, this.y + 248, PW - 12, 14,
                Text.translatable("modamod.estilado.panel.nombre"));
        txtNombreMolde.setMaxLength(40);
        txtNombreMolde.setPlaceholder(Text.translatable("modamod.estilado.panel.nombre"));
        this.addDrawableChild(txtNombreMolde);
        btnRestablecer = botonPanel(6, 268, 72, Text.translatable("modamod.estilado.panel.restablecer"),
                "modamod.estilado.tooltip.restablecer", () -> ajustar(a -> a
                        .conColocacion(com.modamod.aplique.Colocacion.DEFECTO.conCara(a.colocacion().caraBase()))
                        .conOscilacion(com.modamod.aplique.Oscilacion.DEFECTO)));
    }

    /** Muestra la página elegida y pone en cada control el valor del aplique elegido. */
    private void actualizarPanel(@Nullable Aplique a) {
        boolean hay = a != null;
        boolean objeto = hay && a.objeto() != null;
        boolean cre = creativa();
        if (!conBolsas()) sliderNormal.visible = !cre;
        sliderNormal.refrescar(a);
        if (!cre) {
            // La Mesa normal no tiene panel lateral.
            for (var w : new ButtonWidget[] { btnPaginaColocacion, btnPaginaMovimiento, btnPaginaMoldes, btnRestablecer, btnPivote, btnEje,
                    btnCrearMolde, btnBiblioAnt, btnBiblioSig }) w.visible = false;
            for (int i = 0; i < FILAS_BIBLIO; i++) btnBiblioDar[i].visible = btnBiblioBorrar[i].visible = false;
            txtNombreMolde.visible = false;
            for (SliderAjuste sl : slidersColocacion) sl.visible = false;
            for (SliderAjuste sl : slidersMovimiento) sl.visible = false;
            for (ButtonWidget b : btnCaras) b.visible = false;
            return;
        }
        btnCrearMolde.visible = true;
        btnCrearMolde.active = hay;
        txtNombreMolde.visible = true;
        txtNombreMolde.setEditable(hay);
        btnPaginaColocacion.active = paginaPanel != 0;
        btnPaginaMovimiento.active = paginaPanel != 1;
        btnPaginaMoldes.visible = true;
        btnPaginaMoldes.active = paginaPanel != 2;
        // La biblioteca (página 2): una fila por molde guardado en esta Mesa.
        boolean biblio = paginaPanel == 2;
        btnBiblioAnt.visible = btnBiblioSig.visible = biblio;
        btnBiblioAnt.active = paginaBiblio != 0;
        btnBiblioSig.active = paginaBiblio != 1;
        for (int i = 0; i < FILAS_BIBLIO; i++) {
            ItemStack molde = handler.be.getStack(EstiladoBlockEntity.SLOT_BIBLIOTECA + paginaBiblio * FILAS_BIBLIO + i);
            boolean hayMolde = biblio && !molde.isEmpty();
            btnBiblioDar[i].visible = btnBiblioBorrar[i].visible = hayMolde;
            if (hayMolde) btnBiblioDar[i].setMessage(molde.getName());
        }
        for (SliderAjuste s : slidersColocacion) {
            s.visible = hay && paginaPanel == 0;   // sin aplique elegido solo se ve el aviso (2026-10-14, "texto atrapado bajo los botones")
            s.refrescar(a);
        }
        for (SliderAjuste s : slidersMovimiento) {
            s.visible = hay && paginaPanel == 1;
            s.refrescar(a);
        }
        for (int i = 0; i < 6; i++) {
            btnCaras[i].visible = hay && paginaPanel == 0;
            btnCaras[i].active = objeto && a.colocacion().caraBase() != CARAS[i];
        }
        btnPivote.visible = btnEje.visible = hay && paginaPanel == 1;
        btnRestablecer.visible = paginaPanel != 2;
        btnRestablecer.active = hay;
        if (hay) {
            btnPivote.setMessage(Text.translatable("modamod.estilado.panel.pivote",
                    Text.translatable("modamod.estilado.pivote." + a.oscilacion().pivote().asString())));
            btnEje.setMessage(Text.translatable("modamod.estilado.panel.eje",
                    Text.translatable("modamod.estilado.eje." + a.oscilacion().eje().asString())));
        }
    }

    private void dibujarPanel(DrawContext c) {
        if (!creativa()) return;
        int x0 = panelX, y0 = this.y;
        c.fill(x0 - 1, y0 - 1, x0 + PW + 1, y0 + ALTO_PANEL + 1, 0xFF2A180C);
        c.fill(x0, y0, x0 + PW, y0 + ALTO_PANEL, 0xFF4B3F5A);
        c.fill(x0 + 2, y0 + 2, x0 + PW - 2, y0 + ALTO_PANEL - 2, 0xFF5C4F6E);
        var fuente = MinecraftClient.getInstance().textRenderer;
        if (paginaPanel == 2) {
            if (handler.be.getStack(EstiladoBlockEntity.SLOT_BIBLIOTECA).isEmpty()) {
                c.drawTextWrapped(fuente, Text.translatable("modamod.estilado.panel.biblioteca_vacia"), x0 + 8, y0 + 32,
                        PW - 16, EstiloPergamino.TEXTO_CLARO);
            }
            return;
        }
        Aplique a = elegido();
        if (a == null) {
            c.drawTextWrapped(fuente, Text.translatable("modamod.estilado.panel.ninguno"), x0 + 8, y0 + 40, PW - 16,
                    EstiloPergamino.TEXTO_CLARO);
            return;
        }
        if (paginaPanel == 0) {
            Text t = a.objeto() != null ? Text.translatable("modamod.estilado.panel.cara_base")
                    : Text.translatable("modamod.estilado.panel.cara_modelo");
            c.drawText(fuente, t, x0 + 8, y0 + 28, EstiloPergamino.TEXTO_CLARO, false);
        }
    }

    @Override
    protected boolean isClickOutsideBounds(double mx, double my, int left, int top, int button) {
        // El panel está afuera de la ventana: tocarlo con un ítem en el cursor no lo tira.
        if (creativa() && mx >= panelX && mx < panelX + PW && my >= this.y && my < this.y + ALTO_PANEL) return false;
        return super.isClickOutsideBounds(mx, my, left, top, button);
    }

    // ── vista previa ───────────────────────────────────────────────────────
    private boolean dentroDeVista(double mx, double my) {
        return mx >= this.x + PX1 && mx < this.x + PX2 && my >= this.y + PY1 && my < this.y + PY2;
    }

    private void dibujarVista(DrawContext context) {
        PlayerEntity jugador = MinecraftClient.getInstance().player;
        if (jugador == null) return;
        ItemStack prenda = handler.be.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        int x1 = this.x + PX1 + 2, y1 = this.y + PY1 + 2, x2 = this.x + PX2 - 2, y2 = this.y + PY2 - 2;
        Map<Parte, Matrix4f> captura = new EnumMap<>(Parte.class);
        Map<String, com.modamod.render.MallaCapturada> capturaMallas = new java.util.HashMap<>();
        boolean delMod = com.modamod.garment.Garments.esPrenda(prenda);
        GarmentFeatureRenderer.previewOverride = delMod ? List.of(prenda) : List.of();
        GarmentFeatureRenderer.capturaPoses = captura;
        GarmentFeatureRenderer.capturaMallas = capturaMallas;
        Map<Integer, ApliqueRenderer.Captura> capturaApliques = new java.util.HashMap<>();
        ApliqueRenderer.capturaApliques = capturaApliques;
        // Una armadura (2026-10-02, "extender apliques para toda armadura o
        // wearable"): puesta de mentira en el inventario del jugador SOLO del
        // cliente durante este dibujo, como la vista previa del Guardarropas.
        var armadura = jugador.getInventory().armor;
        ItemStack[] antes = new ItemStack[armadura.size()];
        for (int i = 0; i < antes.length; i++) antes[i] = armadura.get(i);
        net.minecraft.entity.EquipmentSlot slot = delMod ? null : slotDe(prenda);
        // El sombrero de bruja no está en los Trinkets del jugador: se dibuja de mentira (2026-10-05).
        GarmentFeatureRenderer.sombreroOverride = com.modamod.render.AccesorioRenderer.es(prenda) ? prenda : null;
        try {
            if (slot != null && slot.getType() == net.minecraft.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
                armadura.set(slot.getEntitySlotId(), prenda);
            }
            // mouseY en el centro: la vista no se inclina con el mouse (el click necesita una pose quieta).
            actualizarModoDeTela();
            // La cámara va al enfoque elegido con una transición suave (2026-10-12, plan de GUIs: mismo esquema que la
            // Modeladora y Tintes); el click usa las poses capturadas, así que no depende de la cámara.
            long ahora = net.minecraft.util.Util.getMeasuringTimeMs();
            float dt = ultimoCuadroMs < 0 ? 1000f : Math.min(200f, ahora - ultimoCuadroMs);
            ultimoCuadroMs = ahora;
            float k = 1f - (float) Math.exp(-dt / 90f);
            focoActual += (enfoque.focoY - focoActual) * k;
            tamActual += (enfoque.tamano - tamActual) * k;
            PreviewJugador.dibujar(context, jugador, x1, y1, x2, y2, Math.round(tamActual * zoomVista), anguloVista,
                    (y1 + y2) / 2f, inclinacionVista, focoActual);
        } finally {
            com.modamod.render.FisicaApliques.modoVistaPrevia = com.modamod.render.FisicaApliques.Modo.QUIETO;
            GarmentFeatureRenderer.previewOverride = null;
            GarmentFeatureRenderer.sombreroOverride = null;
            GarmentFeatureRenderer.capturaPoses = null;
            ApliqueRenderer.capturaApliques = null;
            GarmentFeatureRenderer.capturaMallas = null;
            for (int i = 0; i < antes.length; i++) armadura.set(i, antes[i]);
        }
        cajasApliques.clear();
        cajasApliques.putAll(capturaApliques);
        poses.clear();
        poses.putAll(captura);
        mallas.clear();
        mallas.putAll(capturaMallas);
    }

    /** La tela de los apliques se agita en la vista previa solo mientras Sacudir está prendido. */
    private void actualizarModoDeTela() {
        com.modamod.render.FisicaApliques.modoVistaPrevia = sacudiendo
                ? com.modamod.render.FisicaApliques.Modo.SACUDIDA : com.modamod.render.FisicaApliques.Modo.QUIETO;
    }

    /** Resultado del click: parte, punto sobre la caja sin inflar (px) y cara. */
    private record Toque(Parte parte, float x, float y, float z, Direction cara, float profundidad,
                         Aplique.Superficie superficie, int padre) {
        Toque(Parte parte, float x, float y, float z, Direction cara, float profundidad) {
            this(parte, x, y, z, cara, profundidad, Aplique.Superficie.CAJA, -1);
        }

        Toque(Parte parte, float x, float y, float z, Direction cara, float profundidad, Aplique.Superficie superficie) {
            this(parte, x, y, z, cara, profundidad, superficie, -1);
        }

        /** La profundidad en el mismo eje para todos: z de pantalla (más alto = más cerca de quien mira). */
        float zPantalla() {
            // La malla de pollera/capa ya guarda el z de pantalla; las cajas guardan el parámetro t del rayo (de -10000 a 10000).
            return superficie == Aplique.Superficie.POLLERA || superficie == Aplique.Superficie.CAPA
                    || superficie == Aplique.Superficie.DELANTAL || superficie == Aplique.Superficie.CAPUCHA
                    ? profundidad : -10000f + 20000f * profundidad;
        }
    }

    /**
     * La zona del sombrero (0 ala, 1 cono, 2 cinta) bajo el mouse, o -1 (2026-10-05, "click en la vista 3d"): el
     * rayo del mouse contra las cajas del sombrero en el marco de la cabeza ({@link SombreroRenderer#cajas}); gana
     * la superficie más cercana a quien mira (por donde sale el rayo, como en {@link #tocarApliques}).
     */
    private int zonaDelSombrero(double mx, double my) {
        ItemStack prenda = handler.be.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        if (!(prenda.getItem() instanceof com.modamod.item.ZonasTenibles zt)) return -1;
        int mejor = -1;
        float mejorT = -Float.MAX_VALUE;
        for (Parte marco : marcosDe(prenda, zt)) {
            Matrix4f m = poses.get(marco);
            if (m == null || Math.abs(m.determinant()) < 1e-12f) continue;
            Matrix4f inversa = new Matrix4f(m).invert();
            final float Z = 10000f;
            Vector3f p0 = inversa.transformPosition(new Vector3f((float) mx, (float) my, -Z));
            Vector3f p1 = inversa.transformPosition(new Vector3f((float) mx, (float) my, Z));
            float[] o = { p0.x, p0.y, p0.z };
            float[] d = { p1.x - p0.x, p1.y - p0.y, p1.z - p0.z };
            for (com.modamod.render.SombreroRenderer.Caja c : com.modamod.render.AccesorioRenderer.cajas(prenda, marco)) {
                float tMin = -Float.MAX_VALUE, tMax = Float.MAX_VALUE;
                boolean fuera = false;
                for (int i = 0; i < 3 && !fuera; i++) {
                    float mn = c.min()[i] / 16f, mx2 = c.max()[i] / 16f;   // px del modelo a bloques
                    if (Math.abs(d[i]) < 1e-9f) {
                        if (o[i] < mn || o[i] > mx2) fuera = true;
                        continue;
                    }
                    float t1 = (mn - o[i]) / d[i], t2 = (mx2 - o[i]) / d[i];
                    tMin = Math.max(tMin, Math.min(t1, t2));
                    tMax = Math.min(tMax, Math.max(t1, t2));
                }
                if (fuera || tMin > tMax || tMax < 0 || tMax > 1) continue;
                if (tMax > mejorT) {
                    mejorT = tMax;
                    mejor = c.zona();
                    pieIzquierdo = marco == Parte.PIERNA_IZQ;
                }
            }
        }
        return mejor;
    }

    /** El pie en el que cayó el último click sobre una zona (borcegos): lo fija {@link #zonaDelSombrero}. */
    private boolean pieIzquierdo = false;

    /** Los marcos donde se dibuja un accesorio: uno solo, o las dos piernas en los borcegos (2026-10-11). */
    private static List<Parte> marcosDe(ItemStack prenda, com.modamod.item.ZonasTenibles zt) {
        return zt.tienePares() ? List.of(Parte.PIERNA_DER, Parte.PIERNA_IZQ) : List.of(zt.marco(prenda));
    }

    /** El slot de armadura (o de mano) de un ítem que se pone, o null. */
    @Nullable
    private static net.minecraft.entity.EquipmentSlot slotDe(ItemStack stack) {
        net.minecraft.item.Equipment e = net.minecraft.item.Equipment.fromStack(stack);
        return e == null ? null : e.getSlotType();
    }

    /**
     * Las cajas donde se puede poner un aplique en una armadura o wearable
     * que no es del mod (2026-10-02): las partes que cubre su slot, infladas
     * lo que sale del cuerpo ({@link ApliqueRenderer#dilatacionDeSlot}). Sin
     * slot de armadura (Trinkets, otros), todo el cuerpo.
     */
    private static List<Pieza> piezasDeVestible(ItemStack stack) {
        net.minecraft.entity.EquipmentSlot slot = slotDe(stack);
        float d = ApliqueRenderer.dilatacionDeSlot(slot);
        Identifier nada = Identifier.of("modamod", "vacio");
        java.util.List<Pieza> out = new java.util.ArrayList<>();
        if (slot == net.minecraft.entity.EquipmentSlot.HEAD) {
            out.add(new Pieza(Parte.CABEZA, 0, nada, d));
        } else if (slot == net.minecraft.entity.EquipmentSlot.CHEST) {
            for (Parte p : new Parte[]{Parte.TORSO, Parte.BRAZO_DER, Parte.BRAZO_IZQ}) out.add(new Pieza(p, 0, nada, d));
        } else if (slot == net.minecraft.entity.EquipmentSlot.LEGS) {
            out.add(new Pieza(Parte.TORSO, 0, nada, d, 8, 12));
            out.add(new Pieza(Parte.PIERNA_DER, 0, nada, d));
            out.add(new Pieza(Parte.PIERNA_IZQ, 0, nada, d));
        } else if (slot == net.minecraft.entity.EquipmentSlot.FEET) {
            out.add(new Pieza(Parte.PIERNA_DER, 0, nada, d, 6, 12));
            out.add(new Pieza(Parte.PIERNA_IZQ, 0, nada, d, 6, 12));
        } else {
            for (Parte p : Parte.values()) out.add(new Pieza(p, 0, nada, d));
        }
        return out;
    }

    /** Lo más cercano a quien mira entre la prenda y los apliques que ya tiene (2026-10-04, apliques sobre apliques). */
    @Nullable
    private Toque tocar(double mx, double my) {
        Toque prenda = tocarPrenda(mx, my);
        Toque aplique = tocarApliques((float) mx, (float) my);
        if (aplique == null) return prenda;
        return prenda == null || aplique.zPantalla() > prenda.zPantalla() ? aplique : prenda;
    }

    /** El rayo del mouse contra la caja de cada aplique (el espacio de objeto de su padre): el punto y la cara tocados. */
    @Nullable
    private Toque tocarApliques(float mx, float my) {
        Toque mejor = null;
        for (var e : cajasApliques.entrySet()) {
            ApliqueRenderer.Captura c = e.getValue();
            Matrix4f m = c.matriz();
            if (Math.abs(m.determinant()) < 1e-12f) continue;
            Matrix4f inversa = new Matrix4f(m).invert();
            final float Z = 10000f;
            Vector3f p0 = inversa.transformPosition(new Vector3f(mx, my, -Z));
            Vector3f p1 = inversa.transformPosition(new Vector3f(mx, my, Z));
            float[] o = { p0.x, p0.y, p0.z };
            float[] d = { p1.x - p0.x, p1.y - p0.y, p1.z - p0.z };
            float tMin = -Float.MAX_VALUE, tMax = Float.MAX_VALUE;
            int eje = -1;
            float signo = 0;
            boolean fuera = false;
            for (int i = 0; i < 3 && !fuera; i++) {
                if (Math.abs(d[i]) < 1e-9f) {
                    if (o[i] < c.min()[i] || o[i] > c.max()[i]) fuera = true;
                    continue;
                }
                float t1 = (c.min()[i] - o[i]) / d[i], t2 = (c.max()[i] - o[i]) / d[i];
                float cerca = Math.min(t1, t2), lejos = Math.max(t1, t2);
                if (cerca > tMin) tMin = cerca;
                if (lejos < tMax) {
                    tMax = lejos;
                    eje = i;
                    signo = d[i] > 0 ? 1 : -1;
                }
            }
            if (fuera || eje < 0 || tMin > tMax || tMax < 0 || tMax > 1) continue;
            // La cara que se ve es por donde sale el rayo (la más cercana a quien mira).
            float[] p = { o[0] + d[0] * tMax, o[1] + d[1] * tMax, o[2] + d[2] * tMax };
            for (int i = 0; i < 3; i++) {
                p[i] = i == eje ? (signo > 0 ? c.max()[i] : c.min()[i]) : Math.max(c.min()[i], Math.min(c.max()[i], p[i]));
            }
            Direction cara = Direction.getFacing(eje == 0 ? signo : 0, eje == 1 ? signo : 0, eje == 2 ? signo : 0);
            Toque t = new Toque(Parte.TORSO, p[0] * 16f, p[1] * 16f, p[2] * 16f, cara, tMax, Aplique.Superficie.APLIQUE, e.getKey());
            if (mejor == null || t.profundidad() > mejor.profundidad()) mejor = t;
        }
        return mejor;
    }

    /**
     * El rayo del mouse contra las cajas del sombrero (2026-10-05, "apliques apoyados en el cono"): el punto y la cara
     * tocados, en px del marco de la cabeza; gana la superficie más cercana a quien mira (por donde sale el rayo).
     */
    @Nullable
    /** Clave de traducción del nombre de la zona {@code i} del accesorio que hay en la mesa (sombrero, banda o borcegos). */
    private String claveDeZona(int i) {
        ItemStack prenda = handler.be.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        return prenda.getItem() instanceof com.modamod.item.ZonasTenibles zt ? zt.claveZona(prenda, i) : "modamod.sombrero.zona." + (i + 1);
    }

    private Toque tocarSombrero(double mx, double my, ItemStack prenda) {
        var zt = (com.modamod.item.ZonasTenibles) prenda.getItem();
        Toque mejor = null;
        for (Parte marco : marcosDe(prenda, zt)) {
            Matrix4f m = poses.get(marco);
            if (m == null || Math.abs(m.determinant()) < 1e-12f) continue;
            Matrix4f inversa = new Matrix4f(m).invert();
            final float Z = 10000f;
            Vector3f p0 = inversa.transformPosition(new Vector3f((float) mx, (float) my, -Z));
            Vector3f p1 = inversa.transformPosition(new Vector3f((float) mx, (float) my, Z));
            float[] o = { p0.x, p0.y, p0.z };
            float[] d = { p1.x - p0.x, p1.y - p0.y, p1.z - p0.z };
            for (com.modamod.render.SombreroRenderer.Caja c : com.modamod.render.AccesorioRenderer.cajas(prenda, marco)) {
                float tMin = -Float.MAX_VALUE, tMax = Float.MAX_VALUE;
                int eje = -1;
                float signo = 0;
                boolean fuera = false;
                for (int i = 0; i < 3 && !fuera; i++) {
                    float mn = c.min()[i] / 16f, mx2 = c.max()[i] / 16f;
                    if (Math.abs(d[i]) < 1e-9f) {
                        if (o[i] < mn || o[i] > mx2) fuera = true;
                        continue;
                    }
                    float t1 = (mn - o[i]) / d[i], t2 = (mx2 - o[i]) / d[i];
                    float cerca = Math.min(t1, t2), lejos = Math.max(t1, t2);
                    if (cerca > tMin) tMin = cerca;
                    if (lejos < tMax) {
                        tMax = lejos;
                        eje = i;
                        signo = d[i] > 0 ? 1 : -1;
                    }
                }
                if (fuera || eje < 0 || tMin > tMax || tMax < 0 || tMax > 1) continue;
                float[] p = { o[0] + d[0] * tMax, o[1] + d[1] * tMax, o[2] + d[2] * tMax };
                for (int i = 0; i < 3; i++) {
                    float mn = c.min()[i] / 16f, mx2 = c.max()[i] / 16f;
                    p[i] = i == eje ? (signo > 0 ? mx2 : mn) : Math.max(mn, Math.min(mx2, p[i]));
                }
                Direction cara = Direction.getFacing(eje == 0 ? signo : 0, eje == 1 ? signo : 0, eje == 2 ? signo : 0);
                Toque t = new Toque(marco, p[0] * 16f, p[1] * 16f, p[2] * 16f, cara, tMax);
                if (mejor == null || t.profundidad() > mejor.profundidad()) mejor = t;
            }
        }
        return mejor;
    }

    @Nullable
    private Toque tocarPrenda(double mx, double my) {
        ItemStack prenda = handler.be.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        PlayerEntity jugador = MinecraftClient.getInstance().player;
        if (prenda.isEmpty() || jugador == null || poses.isEmpty()) return null;
        if (prenda.getItem() instanceof com.modamod.item.ZonasTenibles) return tocarSombrero(mx, my, prenda);
        // La capucha (2026-10-24): su malla contra lo demás de la prenda; gana lo más cercano a quien mira.
        com.modamod.render.MallaCapturada mc = mallas.get("capucha");
        float[] hc = mc == null ? null : mc.tocar((float) mx, (float) my);
        Toque capucha = hc == null ? null : new Toque(Parte.TORSO, hc[0] * 64f, hc[1] * 64f, 0f, Direction.SOUTH, hc[2], Aplique.Superficie.CAPUCHA);
        Toque resto = tocarPrendaSinCapucha(prenda, jugador, mx, my);
        if (capucha == null) return resto;
        return resto == null || capucha.zPantalla() >= resto.zPantalla() ? capucha : resto;
    }

    @Nullable
    private Toque tocarPrendaSinCapucha(ItemStack prenda, PlayerEntity jugador, double mx, double my) {
        boolean slim = MinecraftClient.getInstance().player.getSkinTextures().model() == SkinTextures.Model.SLIM;
        // Pollera y capa (2026-10-02, "no registran click on garment"): contra su malla, por UV.
        Aplique.Superficie malla = prenda.getItem() instanceof com.modamod.item.PolleraItem ? Aplique.Superficie.POLLERA
                : prenda.getItem() instanceof com.modamod.item.CapaItem ? Aplique.Superficie.CAPA
                : prenda.getItem() instanceof com.modamod.item.DelantalItem ? Aplique.Superficie.DELANTAL : null;
        if (malla != null) {
            com.modamod.render.MallaCapturada m = mallas.get(malla == Aplique.Superficie.POLLERA ? "pollera"
                    : malla == Aplique.Superficie.DELANTAL ? "delantal" : "capa");
            float[] h = m == null ? null : m.tocar((float) mx, (float) my);
            if (h == null) return null;
            return new Toque(Parte.TORSO, h[0] * 64f, h[1] * 64f, 0f, Direction.SOUTH, h[2], malla);
        }
        Toque mejor = null;
        com.modamod.render.relieve.BustoRender.Busto busto =
                com.modamod.render.GarmentFeatureRenderer.bustoDe(jugador, 0f, false);
        List<Pieza> piezas = com.modamod.garment.Garments.esPrenda(prenda)
                ? PiezasDePrenda.de(prenda, jugador) : piezasDeVestible(prenda);
        for (Pieza pieza : piezas) {
            Matrix4f m = poses.get(pieza.parte());
            if (m == null) continue;
            Matrix4f inversa = new Matrix4f(m).invert();
            Toque t = cortar(pieza, inversa, slim, (float) mx, (float) my);
            if (t != null && (mejor == null || t.profundidad() > mejor.profundidad())) mejor = t;
            // El busto (2026-10-02, "se puede las dos? cosa que si armo la prenda sin
            // pechos despues siga sirviendo?"): el click le pega a la cúpula, pero se
            // guarda el punto del frente plano que tiene debajo.
            if (busto != null && pieza.parte() == Parte.TORSO
                    && com.modamod.render.relieve.BustoRender.cubre(pieza.filaDesde(), pieza.filaHasta())) {
                Toque tb = cortarBusto(busto, pieza, inversa, (float) mx, (float) my);
                if (tb != null && (mejor == null || tb.profundidad() > mejor.profundidad())) mejor = tb;
            }
            // La cola (2026-10-02): igual, en la espalda.
            if (busto != null && pieza.parte() == Parte.TORSO
                    && com.modamod.render.relieve.BustoRender.cubreCola(pieza.filaDesde(), pieza.filaHasta())) {
                Toque tc = cortarCola(busto, pieza, inversa, (float) mx, (float) my);
                if (tc != null && (mejor == null || tc.profundidad() > mejor.profundidad())) mejor = tc;
            }
        }
        return mejor;
    }

    /** El rayo del mouse contra la cola de esta pieza: guarda el punto de la espalda plana de debajo. */
    @Nullable
    private static Toque cortarCola(com.modamod.render.relieve.BustoRender.Busto busto, Pieza pieza,
                                    Matrix4f inversa, float mx, float my) {
        final float Z = 10000f;
        Vector3f lejos = inversa.transformPosition(new Vector3f(mx, my, -Z)).mul(16f);
        Vector3f cerca = inversa.transformPosition(new Vector3f(mx, my, Z)).mul(16f);
        Vector3f dir = new Vector3f(lejos).sub(cerca);
        float[] h = com.modamod.render.relieve.BustoRender.rayoCola(busto, cerca, dir,
                Math.max(0f, pieza.dilatacion()) + 0.02f,
                com.modamod.render.relieve.BustoRender.carpaDe(com.modamod.item.Calce.de(pieza.dilatacion())));
        if (h == null) return null;
        return new Toque(Parte.TORSO, h[1], h[2], 2f, Direction.SOUTH, 1f - h[0]);
    }

    /** El rayo del mouse contra las cúpulas del busto de esta pieza (desde el lado del que mira). */
    @Nullable
    private static Toque cortarBusto(com.modamod.render.relieve.BustoRender.Busto busto, Pieza pieza,
                                     Matrix4f inversa, float mx, float my) {
        final float Z = 10000f;
        Vector3f lejos = inversa.transformPosition(new Vector3f(mx, my, -Z)).mul(16f);
        Vector3f cerca = inversa.transformPosition(new Vector3f(mx, my, Z)).mul(16f);
        Vector3f dir = new Vector3f(lejos).sub(cerca);
        float[] h = com.modamod.render.relieve.BustoRender.rayo(busto, cerca, dir,
                Math.max(0f, pieza.dilatacion()) + 0.02f,
                com.modamod.render.relieve.BustoRender.carpaDe(com.modamod.item.Calce.de(pieza.dilatacion())));
        if (h == null) return null;
        // Profundidad en la misma escala que cortar(): 1 = del lado del que mira.
        return new Toque(Parte.TORSO, h[1], h[2], -2f, Direction.NORTH, 1f - h[0]);
    }

    @Nullable
    private static Toque cortar(Pieza pieza, Matrix4f inversa, boolean slim, float mx, float my) {
        float[] c = ApliqueRenderer.caja(pieza.parte(), slim);
        float dil = Math.max(0f, pieza.dilatacion());
        int desde = pieza.parte() == Parte.CABEZA ? 0 : Math.max(0, pieza.filaDesde());
        int hasta = pieza.parte() == Parte.CABEZA ? 8 : Math.min(12, pieza.filaHasta());
        if (hasta <= desde) return null;
        float[] min = { (c[0] - dil) / 16f, (c[1] + desde) / 16f, (c[2] - dil) / 16f };
        float[] max = { (c[0] + c[3] + dil) / 16f, (c[1] + hasta) / 16f, (c[2] + c[5] + dil) / 16f };

        final float Z = 10000f;
        Vector3f p0 = inversa.transformPosition(new Vector3f(mx, my, -Z));
        Vector3f p1 = inversa.transformPosition(new Vector3f(mx, my, Z));
        float[] o = { p0.x, p0.y, p0.z };
        float[] d = { p1.x - p0.x, p1.y - p0.y, p1.z - p0.z };
        float tMin = -Float.MAX_VALUE, tMax = Float.MAX_VALUE;
        int eje = -1;
        float signo = 0;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-9f) {
                if (o[i] < min[i] || o[i] > max[i]) return null;
                continue;
            }
            float t1 = (min[i] - o[i]) / d[i], t2 = (max[i] - o[i]) / d[i];
            float cerca = Math.min(t1, t2), lejos = Math.max(t1, t2);
            if (cerca > tMin) tMin = cerca;
            // La salida del rayo (z de pantalla más alto) es la cara que se ve.
            if (lejos < tMax) {
                tMax = lejos;
                eje = i;
                signo = d[i] > 0 ? 1 : -1;
            }
        }
        if (eje < 0 || tMin > tMax || tMax < 0 || tMax > 1) return null;
        float[] p = { (o[0] + d[0] * tMax) * 16, (o[1] + d[1] * tMax) * 16, (o[2] + d[2] * tMax) * 16 };
        // Sobre la caja SIN inflar: la cara tocada al ras y el resto acotado.
        float[] cMin = { c[0], c[1] + desde, c[2] }, cMax = { c[0] + c[3], c[1] + hasta, c[2] + c[5] };
        for (int i = 0; i < 3; i++) {
            p[i] = i == eje ? (signo > 0 ? cMax[i] : cMin[i]) : Math.max(cMin[i], Math.min(cMax[i], p[i]));
        }
        Direction cara = Direction.getFacing(eje == 0 ? signo : 0, eje == 1 ? signo : 0, eje == 2 ? signo : 0);
        return new Toque(pieza.parte(), p[0], p[1], p[2], cara, tMax);
    }

    /** Más o menos largo (de a 2 px, de 2 a 32); con una correa elegida se lo manda a esa. */
    private void cambiarLargo(int dir) {
        int min = (int) com.modamod.correa.Correa.LARGO_MIN, max = (int) com.modamod.correa.Correa.LARGO_MAX, paso = (int) com.modamod.correa.Correa.LARGO_PASO;
        correaLargo = Math.max(min, Math.min(max, correaLargo + dir * paso));
        if (correaSel >= 0) clickBoton(EstiladoBlockEntity.BTN_CORREA_LARGO_BASE + correaSel * 16 + (correaLargo / paso - 1));
    }

    /** Un click de correa (2026-10-05): el 1.º fija el inicio y el 2.º manda la correa al servidor (la anclada, solo uno). */
    private void clickCorrea(double mx, double my) {
        EstiladoBlockEntity be = handler.be;
        Toque t = tocarPrenda(mx, my);
        if (!(be.getStack(EstiladoBlockEntity.SLOT_MOLDE).getItem() instanceof com.modamod.correa.MoldeCorreaItem)) {
            aviso = Text.translatable("modamod.correa.aviso.molde");
        } else if (be.correas().size() >= com.modamod.correa.Correa.MAXIMO_POR_PRENDA) {
            aviso = Text.translatable("modamod.correa.aviso.lleno");
        } else if (t == null || t.superficie() == Aplique.Superficie.APLIQUE) {
            aviso = Text.translatable("modamod.estilado.aviso.fuera");
        } else if (!correaModo.dosPuntos()) {
            // Anclada: un solo click y cuelga hacia abajo.
            aviso = null;
            ClientPlayNetworking.send(new com.modamod.estilado.PonerCorreaPayload(be.getPos(), t.parte().ordinal(),
                    new float[] {t.x(), t.y(), t.z()}, t.cara().ordinal(), new float[] {t.x(), t.y(), t.z()}, t.cara().ordinal(),
                    correaModo.ordinal(), correaAncho, t.superficie().ordinal(), correaLargo));
            enviarCorreaEspejo(t, t);
        } else if (correaInicio == null) {
            aviso = null;
            correaInicio = t;
            correaInicioPantalla = new float[] {(float) mx, (float) my};
        } else if (t.parte() != correaInicio.parte() || t.superficie() != correaInicio.superficie()) {
            aviso = Text.translatable("modamod.correa.aviso.parte");
        } else {
            aviso = null;
            ClientPlayNetworking.send(new com.modamod.estilado.PonerCorreaPayload(be.getPos(), t.parte().ordinal(),
                    new float[] {correaInicio.x(), correaInicio.y(), correaInicio.z()}, correaInicio.cara().ordinal(),
                    new float[] {t.x(), t.y(), t.z()}, t.cara().ordinal(), correaModo.ordinal(), correaAncho,
                    t.superficie().ordinal(), correaLargo));
            enviarCorreaEspejo(correaInicio, t);
            correaInicio = null;
        }
    }

    /** Con borcegos y "Espejar", la misma correa en el otro pie (2026-10-11). */
    private void enviarCorreaEspejo(Toque a, Toque b) {
        EstiladoBlockEntity be = handler.be;
        if (!espejarPie || a.superficie() != Aplique.Superficie.CAJA
                || be.correas().size() + 1 >= com.modamod.correa.Correa.MAXIMO_POR_PRENDA) return;
        Toque ea = espejoDe(a), eb = espejoDe(b);
        if (sobreSiMismo(a, ea) && sobreSiMismo(b, eb)) return;   // una correa justo en el centro
        ClientPlayNetworking.send(new com.modamod.estilado.PonerCorreaPayload(be.getPos(), ea.parte().ordinal(),
                new float[] {ea.x(), ea.y(), ea.z()}, ea.cara().ordinal(), new float[] {eb.x(), eb.y(), eb.z()}, eb.cara().ordinal(),
                correaModo.ordinal(), correaAncho, eb.superficie().ordinal(), correaLargo));
    }

    /** Dónde cae en pantalla el punto de inicio de la correa (px de la GUI), o null. */
    @Nullable
    private float[] pantallaDe(Toque t) {
        Matrix4f m = poses.get(t.parte());
        if (m == null) return null;
        Vector3f p = m.transformPosition(new Vector3f(t.x() / 16f, t.y() / 16f, t.z() / 16f));
        return new float[] {p.x, p.y};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Los botones de objeto están dentro de la vista: tienen prioridad sobre poner un aplique.
        for (ButtonWidget b : objetoBotones()) {
            if (b.visible && b.isMouseOver(mx, my)) return super.mouseClicked(mx, my, button);
        }
        for (ButtonWidget b : btnEnfoque) {
            if (b != null && b.isMouseOver(mx, my)) return super.mouseClicked(mx, my, button);
        }
        if (dentroDeVista(mx, my)) {
            if (button == 1) {
                arrastrando = true;
                return true;
            }
            if (button == 0 && modoCorrea) {
                clickCorrea(mx, my);
                return true;
            }
            if (button == 0 && modoColor) {
                // Colorear el sombrero (2026-10-05, "click en la vista 3d"): la zona clickeada toma el color activo.
                int zona = zonaDelSombrero(mx, my);
                if (handler.be.coloresDeLaFuente() == null) aviso = Text.translatable("modamod.estilado.color.sin_retazo");
                else if (zona < 0) aviso = Text.translatable("modamod.estilado.aviso.fuera");
                else {
                    aviso = null;
                    if (conPares()) pieActivoIzq = pieIzquierdo;    // el pie que tocaste
                    clickZona(EstiladoBlockEntity.BTN_COLOR_BASE + zona * 4 + colorActivo);
                }
                return true;
            }
            if (button == 0) {
                Toque t = tocar(mx, my);
                EstiladoBlockEntity be = handler.be;
                boolean objeto = !be.getStack(EstiladoBlockEntity.SLOT_OBJETO).isEmpty();
                if (be.getStack(EstiladoBlockEntity.SLOT_PRENDA).isEmpty()) aviso = Text.translatable("modamod.estilado.aviso.prenda");
                else if (!objeto && be.getStack(EstiladoBlockEntity.SLOT_MOLDE).getItem() instanceof com.modamod.item.MoldeTexturaItem)
                    aviso = Text.translatable("modamod.estilado.aviso.textura");
                else if (!objeto && be.getStack(EstiladoBlockEntity.SLOT_MOLDE).isEmpty()) aviso = Text.translatable("modamod.estilado.aviso.molde");
                else if (!objeto && !(be.getStack(EstiladoBlockEntity.SLOT_RETAZO).getItem() instanceof com.modamod.aplique.RetazoApliqueItem)
                        && !(plantillaDeObjeto(be.getStack(EstiladoBlockEntity.SLOT_MOLDE)))
                        && !be.creativa()) aviso = Text.translatable("modamod.estilado.aviso.retazo");
                else if (be.apliques().size() >= Aplique.MAXIMO_POR_PRENDA) aviso = Text.translatable("modamod.estilado.aviso.lleno");
                else if (t == null) aviso = Text.translatable("modamod.estilado.aviso.fuera");
                else {
                    aviso = null;
                    ClientPlayNetworking.send(new PonerApliquePayload(be.getPos(), t.parte().ordinal(),
                            t.x(), t.y(), t.z(), t.cara().ordinal(), t.superficie().ordinal(), t.padre()));
                    if (espejarPie && t.superficie() != Aplique.Superficie.APLIQUE && t.padre() < 0
                            && be.apliques().size() + 1 < Aplique.MAXIMO_POR_PRENDA && !sobreSiMismo(t, espejoDe(t))) {
                        Toque e = espejoDe(t);
                        ClientPlayNetworking.send(new PonerApliquePayload(be.getPos(), e.parte().ordinal(),
                                e.x(), e.y(), e.z(), e.cara().ordinal(), e.superficie().ordinal(), e.padre()));
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private List<ButtonWidget> objetoBotones() {
        return new java.util.ArrayList<>(List.of(btnObjModo, btnObjVariante, btnEspejo));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 1) arrastrando = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (arrastrando) {
            anguloVista = Math.floorMod(Math.round(anguloVista + (float) dx * 1.15f), 360);
            inclinacionVista = net.minecraft.util.math.MathHelper.clamp(
                    inclinacionVista + (float) dy * 0.8f, -INCLINACION_MAX, INCLINACION_MAX);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (dentroDeVista(mx, my)) {
            zoomVista = net.minecraft.util.math.MathHelper.clamp(zoomVista + (float) vertical * 0.1f, 0.5f, 2.5f);
            return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    /** Sin esto, escribir una "e" en el nombre del molde cierra la pantalla (mismo motivo que la Sublimadora). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (txtNombreMolde != null && txtNombreMolde.visible
                && (txtNombreMolde.keyPressed(keyCode, scanCode, modifiers) || txtNombreMolde.isActive())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── dibujo ─────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        EstiladoBlockEntity be = handler.be;
        List<Aplique> apliques = be.apliques();
        int sel = be.seleccionado();
        for (int i = 0; i < btnApliques.length; i++) {
            btnApliques[i].active = i < apliques.size();
            btnApliques[i].setMessage(Text.literal(i == sel ? "[" + (i + 1) + "]" : Integer.toString(i + 1)));
        }
        boolean hay = sel >= 0 && sel < apliques.size();
        // Sombrero de bruja en la prenda (2026-10-05): colorear o poner apliques.
        ItemStack enPrenda = be.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        boolean sombrero = enPrenda.getItem() instanceof com.modamod.item.ZonasTenibles;
        // Correas libres: en toda prenda y wearable puesto.
        boolean admiteCorrea = !enPrenda.isEmpty();
        if (!admiteCorrea && pestana != 0 && pestana < 3) pestana = 0;
        modoCorrea = pestana == 1;
        modoColor = pestana == 2 && sombrero;
        if (!modoCorrea) correaInicio = null;
        String[] nombresPestana = {"aplique", "correa", "acabado"};
        for (int i = 0; i < btnPestana.length; i++) {
            if (btnPestana[i] == null) continue;
            btnPestana[i].active = i == 0 || admiteCorrea || i >= 3;
            Text base = i == 0 ? Text.translatable("modamod.estilado.pestana.aplique", apliques.size(), Aplique.MAXIMO_POR_PRENDA)
                    : i == 1 ? Text.translatable("modamod.estilado.pestana.correa", be.correas().size(), com.modamod.correa.Correa.MAXIMO_POR_PRENDA)
                    : i == 3 ? Text.translatable("modamod.estilado.pestana.bolsas")
                    : i == 4 ? Text.translatable("modamod.estilado.pestana.disenos")
                    : Text.translatable("modamod.estilado.pestana.acabado");
            if (conBolsas() && i < 3) base = Text.translatable("modamod.estilado.pestana.corta." + i);
            btnPestana[i].setMessage(i == pestana ? Text.literal("[").append(base).append("]") : base);
        }
        btnTextura.visible = pestana == 2 && !sombrero;
        btnCorreaModo.visible = btnCorreaAncho.visible = btnCorreaQuitar.visible = btnCorreaColor.visible = modoCorrea;
        btnCorreaLargo.visible = btnCorreaLargoMenos.visible = btnCorreaLargoMas.visible = modoCorrea;
        // El largo solo vale para las que cuelgan (de la elegida, si hay una).
        var modoDelLargo = correaSel >= 0 && correaSel < be.correas().size() ? be.correas().get(correaSel).modo() : correaModo;
        btnCorreaLargo.active = btnCorreaLargoMenos.active = btnCorreaLargoMas.active = modoDelLargo.usaLargo();
        btnCorreaLargo.setMessage(Text.translatable("modamod.correa.boton.largo", correaLargo));
        int nCorreas = be.correas().size();
        if (correaSel >= nCorreas) correaSel = -1;
        for (int i = 0; i < btnCorreaLista.length; i++) {
            btnCorreaLista[i].visible = modoCorrea;
            btnCorreaLista[i].active = i < nCorreas;
            btnCorreaLista[i].setMessage(Text.literal(i == correaSel ? "[" + (i + 1) + "]" : Integer.toString(i + 1)));
        }
        btnCorreaColor.active = correaSel >= 0 && be.coloresDeLaFuente() != null;
        btnCorreaModo.setMessage(Text.translatable("modamod.correa.boton.modo", Text.translatable(correaModo.traduccion())));
        btnCorreaAncho.setMessage(Text.translatable("modamod.correa.boton.ancho", correaAncho));
        btnCorreaQuitar.setMessage(Text.translatable(correaInicio != null ? "modamod.correa.boton.cancelar"
                : correaSel >= 0 ? "modamod.correa.boton.quitar_sel" : "modamod.correa.boton.quitar", correaSel >= 0 ? correaSel + 1 : be.correas().size()));
        btnCorreaQuitar.active = correaInicio != null || !be.correas().isEmpty();
        for (ButtonWidget b : grupoAplique) b.visible = pestana == 0;
        java.util.List<Integer> fuente = be.coloresDeLaFuente();
        for (int z = 0; z < 3; z++) {
            for (int c = 0; c < 3; c++) {
                ButtonWidget b = btnColorZona[z][c];
                b.visible = modoColor;
                b.active = fuente != null;
                int rgb = fuente == null ? 0x808080 : fuente.get(c);
                b.setMessage(Text.literal(c == colorActivo ? "[■]" : "■").styled(st -> st.withColor(rgb)));
            }
        }
        btnColorEntero.visible = modoColor;
        btnColorEntero.active = fuente != null;
        boolean pares = sombrero && ((com.modamod.item.ZonasTenibles) enPrenda.getItem()).tienePares();
        if (enPrenda.getItem() != ultimaPrendaEspejo) {
            // Cada prenda arranca con su espejo: prendido en las de a pares (borcegos), apagado en el resto.
            ultimaPrendaEspejo = enPrenda.getItem();
            espejarPie = conPares();
        }
        btnEspejo.visible = !enPrenda.isEmpty();
        btnEspejo.setMessage(Text.translatable(espejarPie ? "modamod.estilado.espejo.si" : "modamod.estilado.espejo.no"));
        btnPie.visible = pares && modoColor;
        btnPie.setMessage(Text.translatable(pieActivoIzq ? "modamod.estilado.pie.izq" : "modamod.estilado.pie.der"));
        for (int z = 0; z < 3; z++) {
            btnPatronZona[z].visible = modoColor;
            if (enPrenda.getItem() instanceof com.modamod.item.ZonasTenibles zt) btnPatronZona[z].setMessage(Text.translatable(
                    zt.patronesDe(enPrenda, pieActivoIzq).get(z).traduccion()));
        }
        btnGiro.active = btnEscala.active = btnQuitar.active = hay;
        // La fila es de los apliques de modelo; los de objeto la ignoran.
        boolean conFila = hay && apliques.get(sel).objeto() == null;
        for (ButtonWidget b : new ButtonWidget[] { btnFilaAtras, btnFila, btnFilaAdelante, btnSepAtras, btnSep, btnSepAdelante }) b.active = conFila;
        com.modamod.aplique.Fila fila = conFila ? apliques.get(sel).fila() : com.modamod.aplique.Fila.DEFECTO;
        btnFila.setMessage(Text.translatable("modamod.estilado.fila", fila.cantidad()));
        btnSep.setMessage(Text.translatable("modamod.estilado.separacion", String.format(java.util.Locale.ROOT, "%.2f", fila.separacion())));
        actualizarPanel(hay ? apliques.get(sel) : null);
        com.modamod.aplique.ObjetoAplique obj = hay ? apliques.get(sel).objeto() : null;
        for (ButtonWidget b : objetoBotones()) b.visible = obj != null && creativa() && (!conBolsas() || pestana == 0);
        if (conBolsas()) btnEspejo.visible = !enPrenda.isEmpty() && pestana <= 1;
        if (obj != null) {
            boolean esBloque = com.modamod.aplique.ObjetoAplique.bloqueDe(obj.item()) != null;
            btnObjModo.active = esBloque;
            btnObjModo.setMessage(Text.translatable(obj.bloque() ? "modamod.estilado.obj.bloque" : "modamod.estilado.obj.item"));
            btnObjVariante.active = obj.bloque() && obj.cantidadDeVariantes() > 1;
            btnObjVariante.setMessage(Text.translatable("modamod.estilado.obj.variante",
                    Math.floorMod(obj.variante(), obj.cantidadDeVariantes()) + 1, obj.cantidadDeVariantes()));
        }
        btnSacudir.setMessage(Text.translatable(sacudiendo ? "modamod.estilado.sacudir.parar" : "modamod.estilado.sacudir"));
        btnGiro.setMessage(Text.translatable("modamod.estilado.giro", hay ? Math.round(apliques.get(sel).giro()) : 0));
        btnEscala.setMessage(Text.translatable("modamod.estilado.escala",
                hay ? Math.round(apliques.get(sel).escala() * 100) : 100));

        ItemStack prendaPuesta = be.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        com.modamod.item.TexturaTela actual = prendaPuesta.getOrDefault(
                com.modamod.item.ModamodComponents.TEXTURA_TELA, com.modamod.item.TexturaTela.LISA);
        net.minecraft.util.Identifier trim = be.patronDeTrim(be.getStack(EstiladoBlockEntity.SLOT_MOLDE));
        if (trim != null) {
            // Molde de trim de vanilla: acabado animado de toda la prenda (2026-10-06).
            net.minecraft.util.Identifier matSlot = be.materialDe(be.getStack(EstiladoBlockEntity.SLOT_MATERIAL));
            boolean ya = trim.equals(prendaPuesta.get(com.modamod.item.ModamodComponents.ACABADO_TRIM))
                    && (matSlot == null || matSlot.equals(prendaPuesta.get(com.modamod.item.ModamodComponents.ACABADO_MATERIAL)));
            btnTextura.active = !prendaPuesta.isEmpty() && EstiladoBlockEntity.admiteAcabado(prendaPuesta);
            btnTextura.setMessage(Text.translatable(ya ? "modamod.estilado.acabado.quitar" : "modamod.estilado.acabado.poner",
                    Text.translatable("modamod.efecto." + com.modamod.render.EfectoTrim.tipoDe(trim).name().toLowerCase())));
        } else if (be.getStack(EstiladoBlockEntity.SLOT_MOLDE).getItem() instanceof com.modamod.item.MoldeTexturaItem mt
                && !prendaPuesta.isEmpty()) {
            btnTextura.active = true;
            btnTextura.setMessage(Text.translatable(actual == mt.textura ? "modamod.estilado.textura.quitar"
                    : "modamod.estilado.textura.poner", Text.translatable(mt.textura.traduccion())));
        } else if (be.materialDe(be.getStack(EstiladoBlockEntity.SLOT_MATERIAL)) != null
                && prendaPuesta.get(com.modamod.item.ModamodComponents.ACABADO_TRIM) != null
                && !be.materialDe(be.getStack(EstiladoBlockEntity.SLOT_MATERIAL))
                        .equals(prendaPuesta.get(com.modamod.item.ModamodComponents.ACABADO_MATERIAL))) {
            // Sin molde pero con material: solo cambia el material del acabado que ya está.
            btnTextura.active = true;
            net.minecraft.util.Identifier m = be.materialDe(be.getStack(EstiladoBlockEntity.SLOT_MATERIAL));
            btnTextura.setMessage(Text.translatable("modamod.estilado.acabado.material",
                    Text.translatable("trim_material." + m.getNamespace() + "." + m.getPath())));
        } else {
            btnTextura.active = false;
            btnTextura.setMessage(Text.translatable("modamod.estilado.textura.actual",
                    Text.translatable(actual.traduccion())));
        }

        reubicar(sombrero, pares, obj != null && creativa());
        super.render(context, mouseX, mouseY, delta);
        verificarSolapes();
        // Mira: dónde caería el aplique.
        if (modoCorrea && dentroDeVista(mouseX, mouseY)) {
            Toque t = tocarPrenda(mouseX, mouseY);
            if (t != null && t.superficie() != Aplique.Superficie.APLIQUE) {
                context.fill(mouseX - 3, mouseY, mouseX + 4, mouseY + 1, 0xFFFFFFFF);
                context.fill(mouseX, mouseY - 3, mouseX + 1, mouseY + 4, 0xFFFFFFFF);
            }
            // El punto de inicio y una guía hasta el mouse.
            float[] ini = correaInicio == null ? null
                    : correaInicio.superficie() == Aplique.Superficie.CAJA ? pantallaDe(correaInicio) : correaInicioPantalla;
            if (ini != null) {
                int x0 = Math.round(ini[0]), y0 = Math.round(ini[1]);
                context.fill(x0 - 2, y0 - 2, x0 + 3, y0 + 3, 0xFFFFD060);
                int pasos = Math.max(Math.abs(mouseX - x0), Math.abs(mouseY - y0));
                for (int i = 0; i < pasos; i += 2) {
                    int px = x0 + (mouseX - x0) * i / pasos, py = y0 + (mouseY - y0) * i / pasos;
                    context.fill(px, py, px + 1, py + 1, 0xFFFFD060);
                }
            }
        } else if (modoColor && dentroDeVista(mouseX, mouseY)) {
            int zona = zonaDelSombrero(mouseX, mouseY);
            if (zona >= 0) {
                context.fill(mouseX - 3, mouseY, mouseX + 4, mouseY + 1, 0xFFFFFFFF);
                context.fill(mouseX, mouseY - 3, mouseX + 1, mouseY + 4, 0xFFFFFFFF);
                context.drawTooltip(this.textRenderer, Text.translatable(claveDeZona(zona)), mouseX, mouseY);
            }
        } else if (!modoColor && !modoCorrea && dentroDeVista(mouseX, mouseY) && tocar(mouseX, mouseY) != null) {
            context.fill(mouseX - 3, mouseY, mouseX + 4, mouseY + 1, 0xFFFFFFFF);
            context.fill(mouseX, mouseY - 3, mouseX + 1, mouseY + 4, 0xFFFFFFFF);
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        EstiloPergamino.titulo(context, this.textRenderer, this.title, this.titleX, this.titleY);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX,
                this.playerInventoryTitleY, EstiloPergamino.TEXTO, false);
        context.getMatrices().push();
        context.getMatrices().translate(0, dyEstilista(), 0);
        int y = EstiladoScreenHandler.Y_SLOTS - 10;
        if (!conBolsas()) {
        context.drawText(this.textRenderer, Text.translatable("modamod.estilado.slot.prenda"), X_DER - 1, y, EstiloPergamino.TEXTO, false);
        context.drawText(this.textRenderer, Text.translatable("modamod.estilado.slot.molde"), X_DER + 25, y + 30, EstiloPergamino.TEXTO, false);
        context.drawText(this.textRenderer, Text.translatable("modamod.estilado.slot.retazo"), X_DER + 51, y, EstiloPergamino.TEXTO, false);
        if (creativa()) {
            context.drawText(this.textRenderer, Text.translatable("modamod.estilado.slot.objeto"), X_DER + 77, y + 30, EstiloPergamino.TEXTO, false);
        }
        context.drawText(this.textRenderer, Text.translatable("modamod.estilado.slot.material"), X_DER + 103, y, EstiloPergamino.TEXTO, false);
        }
        ItemStack enPrendaF = handler.be.getStack(EstiladoBlockEntity.SLOT_PRENDA);
        if (pestana == 2 && !enPrendaF.isEmpty() && !(enPrendaF.getItem() instanceof com.modamod.item.ZonasTenibles)) {
            // Qué lleva la prenda y cómo se pone (2026-10-06).
            net.minecraft.util.Identifier t = enPrendaF.get(com.modamod.item.ModamodComponents.ACABADO_TRIM);
            net.minecraft.util.Identifier m = enPrendaF.get(com.modamod.item.ModamodComponents.ACABADO_MATERIAL);
            Text actual = t == null ? Text.translatable("modamod.estilado.acabado.ninguno")
                    : Text.translatable("modamod.acabado.tooltip", Text.translatable(
                            "modamod.efecto." + com.modamod.render.EfectoTrim.tipoDe(t).name().toLowerCase()));
            if (t != null && m != null) actual = actual.copy().append(" · ").append(Text.translatable(
                    "trim_material." + m.getNamespace() + "." + m.getPath()));
            int yy = conBolsas() && disp.get("acabadoTexto") != null ? disp.get("acabadoTexto").y() : 90;
            for (var linea : this.textRenderer.wrapLines(actual, 162)) { context.drawText(this.textRenderer, linea, X_DER, yy, EstiloPergamino.TEXTO, false); yy += 10; }
            yy += 4;
            for (var linea : (conBolsas() ? java.util.List.<net.minecraft.text.OrderedText>of() : this.textRenderer.wrapLines(Text.translatable("modamod.estilado.acabado.ayuda"), 162))) {
                context.drawText(this.textRenderer, linea, X_DER, yy, 0xFF6B5A78, false);
                yy += 10;
            }
        }
        if (modoColor) {
            for (int z = 0; z < 3; z++) {
                context.drawText(this.textRenderer, Text.translatable(claveDeZona(z)),
                        X_DER, (conBolsas() && disp.get("zr" + z) != null ? disp.get("zr" + z).y() : 66 + z * 22) + 4, EstiloPergamino.TEXTO, false);
            }
            if (handler.be.coloresDeLaFuente() == null) {
                context.drawText(this.textRenderer, Text.translatable("modamod.estilado.color.sin_retazo"),
                        X_DER, conBolsas() && disp.get("avisoColor") != null ? disp.get("avisoColor").y() : 170, 0xFFFF9090, false);
            }
        }
        context.getMatrices().pop();
        java.util.List<Integer> fuenteAyuda = modoColor ? handler.be.coloresDeLaFuente() : null;
        Text ayuda = aviso != null ? aviso
                : modoCorrea ? Text.translatable(!correaModo.dosPuntos() ? "modamod.correa.ayuda.anclada"
                        : correaInicio == null ? "modamod.correa.ayuda.inicio" : "modamod.correa.ayuda.fin")
                : modoColor ? Text.translatable("modamod.estilado.ayuda_color",
                        Text.literal("■").styled(st -> st.withColor(fuenteAyuda == null ? 0x808080 : fuenteAyuda.get(colorActivo))))
                : pestana == 2 ? Text.translatable("modamod.estilado.ayuda_acabado")
                : Text.translatable("modamod.estilado.ayuda");
        int color = aviso != null ? 0xFFFF9090 : 0xFFE8DCC8;
        for (var linea : this.textRenderer.wrapLines(ayuda, PX2 - PX1 - 12)) {
            context.drawText(this.textRenderer, linea, PX1 + 6, PY2 - 32, color, true);
            break;
        }
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int x0 = this.x, y0 = this.y, w = backgroundWidth, h = backgroundHeight;
        var tema = EstiloPergamino.tema();
        // Pergamino con borde oscuro, filete del acento y esquinas en el acento (como las máquinas hermanas).
        c.fill(x0 - 2, y0 - 2, x0 + w + 2, y0 + h + 2, 0xFF2A180C);
        c.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, tema.claro);
        c.fill(x0, y0, x0 + w, y0 + h, 0xFFE9D8B4);
        for (int i = 0; i < 9; i++) {
            c.fill(x0, y0 + i, x0 + 9 - i, y0 + i + 1, tema.claro);
            c.fill(x0 + w - 9 + i, y0, x0 + w, y0 + i + 1, tema.claro);
            c.fill(x0, y0 + h - 1 - i, x0 + 9 - i, y0 + h - i, tema.claro);
            c.fill(x0 + w - 9 + i, y0 + h - 1 - i, x0 + w, y0 + h - i, tema.claro);
        }
        for (int px = x0 + 8; px < x0 + w - 8; px += 4) c.fill(px, y0 + 306, px + 2, y0 + 307, tema.oscuro);
        for (int py = y0 + 24; py < y0 + 302; py += 4) c.fill(x0 + X_DER - 3, py, x0 + X_DER - 2, py + 2, tema.oscuro);
        for (int py = y0 + 312; py < y0 + 400; py += 4) {
            c.fill(x0 + 116, py, x0 + 117, py + 2, tema.oscuro);
            c.fill(x0 + 284, py, x0 + 285, py + 2, tema.oscuro);
        }
        // Visor: el jugador se dibuja ahora, así los botones y los casilleros quedan encima.
        c.fill(x0 + PX1 - 1, y0 + PY1 - 1, x0 + PX2 + 1, y0 + PY2 + 1, 0xFF2A180C);
        c.fill(x0 + PX1, y0 + PY1, x0 + PX2, y0 + PY2, 0xFF4A3828);
        dibujarVista(c);
        // Marco de cada casillero visible (el fondo ya no es un PNG con los marcos horneados).
        for (var s : handler.slots) {
            if (s.x < -1000 || !s.isEnabled()) continue;
            c.fill(x0 + s.x - 1, y0 + s.y - 1, x0 + s.x + 17, y0 + s.y + 17, 0xFF2A180C);
            c.fill(x0 + s.x, y0 + s.y, x0 + s.x + 16, y0 + s.y + 16, 0xFF8B7355);
        }
        dibujarPanel(c);
    }
}
