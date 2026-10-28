package com.modamod.client;

import com.modamod.item.ClothingPatternItem;
import com.modamod.modelado.ZonasModelado;
import com.modamod.render.GarmentFeatureRenderer;
import com.modamod.tinturas.ElegirPatronPayload;
import com.modamod.tinturas.TinturasBlockEntity;
import com.modamod.tinturas.TinturasScreenHandler;
import com.modamod.tinturas.ZonasTintes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pantalla de la Estación de Tintes, versión "visor enfocado" (2026-10-12, plan de GUIs, "vamos con todo": mismo esquema que
 * la Modeladora). Ventana de 560x408 con el fondo dibujado por código (los {@code esquema_tintes_*.png} y {@code
 * tinturas.png} ya no se leen):
 * <ul>
 *   <li>A. Visor (8,24, 304x278): el jugador con la prenda teñida en borrador; la cámara se acerca al enfoque de la zona
 *   activa. Sin seguir al mouse; click derecho arrastrando inclina.</li>
 *   <li>C. Columna derecha: prenda (‹ ›), tira de zonas, los cuadraditos de la zona (una fila cada uno: muestra del color,
 *   patrón, estado y chincheta) y debajo cinco pestañas de controles del cuadradito elegido — Color (CMYK, 3 colores),
 *   Patrón (tamaño, ángulo, giro, distancias, espejos...), Mezcla (modo, opacidad, orden), Capas (el panel de capas) y
 *   Bolsa (los patrones guardados y el depósito).</li>
 *   <li>Cabecera a la derecha del inventario: Entrada → Teñir → barra → Salida; diseños a la izquierda.</li>
 * </ul>
 * Mouse encima de un cuadradito (o de su fila en el panel de capas): el visor apaga todo lo que no es su zona.
 */
public class TinturasScreen extends HandledScreen<TinturasScreenHandler> {

    private static final int X_COL = TinturasScreenHandler.X_COL, ANCHO_COL = TinturasScreenHandler.ANCHO_COL;
    /** Claves de traducción de los 5 canales. */
    private static final String[] NOMBRE_CANAL = { "cian", "magenta", "amarillo", "negro", "transparencia" };
    /** Color del líquido de cada tanque C/M/Y/K — los mismos que la Sublimadora. */
    private static final int[] COLOR_TANQUE = { 0xFF1FB3D6, 0xFFD6287F, 0xFFE8C21E, 0xFF2A2A2A };

    private static final Identifier[] TEXTURE_CHINCHETA = {
            Identifier.of("modamod", "textures/gui/container/chincheta_verdin_0.png"),
            Identifier.of("modamod", "textures/gui/container/chincheta_verdin_1.png"),
            Identifier.of("modamod", "textures/gui/container/chincheta_verdin_2.png"),
    };

    // Sectores (coordenadas locales de la ventana).
    private static final int VISOR_X1 = 8, VISOR_Y1 = 24, VISOR_X2 = 312, VISOR_Y2 = 302;
    private static final int TIRA_X = X_COL, TIRA_Y = 43, TIRA_ANCHO = ANCHO_COL, TIRA_ALTO = 18;
    private static final int MAX_ZONAS = 4;
    /** Pestañas de controles: y de la franja, y de los controles y x de arranque. */
    private static final int PESTANAS_Y = 136, CY = 156, CX = X_COL + 2;
    private static final int N_PESTANAS = 5;
    private static final String[] NOMBRE_PESTANA = {"color", "patron", "capas", "bolsa", "disenos"};

    // Bolsa de patrones: grilla de 8x3.
    private static final int COLS_BOLSA = 8, FILAS_BOLSA = 3, POR_HOJA = COLS_BOLSA * FILAS_BOLSA, CELDA = 24;
    private static final int X_GRILLA = CX + 4, Y_GRILLA = CY;

    private ButtonWidget btnCategoria, btnCategoriaAtras, btnCategoriaAdelante;
    private ButtonWidget btnModo, btnOpacidad, btnOrden, btnTamano, btnAngulo;
    private ButtonWidget btnGiro, btnGiroAtras, btnGiroAdelante;
    private DistanciaSlider sliderDistH, sliderDistV;
    private ButtonWidget btnEspejo, btnAlternancia, btnSimetria, btnGiroAzar, btnPosX;
    private ButtonWidget btnPosicion, btnForma, btnSemilla, btnColores, btnContorno, btnVariacion, btnFondo;
    private final BotonMuestra[] btnMuestras = new BotonMuestra[3];
    private ButtonWidget btnSimetriaCalzado, btnInvertir, btnVista, btnTenir, btnGuardarDiseno, btnUltimo, btnFiltro;
    private ButtonWidget btnZonaPrev, btnZonaNext, btnPagPrev, btnPagNext;
    private final ButtonWidget[] btnZona = new ButtonWidget[MAX_ZONAS];
    private final ButtonWidget[] btnPestana = new ButtonWidget[N_PESTANAS];
    private final BotonChincheta[] btnChinchetas = new BotonChincheta[TinturasBlockEntity.CASILLAS];
    private net.minecraft.client.gui.widget.TextFieldWidget txtNombreDiseno;
    /** El diseño de la lista elegido para exportar (el último que se cargó con click izquierdo; 2026-10-25). */
    private int disenoElegido = -1;
    /** Filtro de la bolsa de diseños: -2 = la prenda actual, -1 = todas, 0.. = esa categoría. */
    private int filtroDisenos = -2;
    private int ratonX, ratonY;
    private static final int DIS_POR_HOJA = 3, DIS_ALTO = 16, DIS_ANCHO = 216;
    private final CanalSlider[] sliders = new CanalSlider[TinturasBlockEntity.CANALES];
    @SuppressWarnings("unchecked")
    private final List<ClickableWidget>[] porPestana = new List[N_PESTANAS];

    /** Qué cuadradito (y de qué categoría) mostraban los sliders el frame pasado — si cambió, se reposicionan. */
    private int casillaMostradaEnSliders = -1;
    /** Hasta cuándo releer los sliders del servidor (después de "Usar muestra"). */
    private long releerSlidersHasta = 0;
    private TinturasBlockEntity.Categoria categoriaMostradaEnSliders = null;
    /** Qué color (1..3) editaban los sliders el frame pasado. */
    private int editandoMostrado = -1;

    private int pagina = 0;
    private TinturasBlockEntity.Categoria ultimaCategoria;

    // Cámara del visor: lo actual se acerca a lo pedido por la zona (transición suave).
    private float focoActual = ZonasModelado.Enfoque.ENTERO.focoY, tamActual = ZonasModelado.Enfoque.ENTERO.tamano;
    private long ultimoCuadroMs = -1;
    private float anguloVista = 0f, zoomVista = 1f, inclinacion = 0f;
    private boolean arrastrandoPreview = false, inclinandoPreview = false;
    private static final float ZOOM_MIN = 0.5f, ZOOM_MAX = 2.5f;

    public TinturasScreen(TinturasScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = TinturasScreenHandler.ANCHO;
        this.backgroundHeight = TinturasScreenHandler.ALTO;
        this.titleX = 8;
        this.titleY = 9;
        this.playerInventoryTitleX = TinturasScreenHandler.X_INV;
        this.playerInventoryTitleY = TinturasScreenHandler.Y_INV - 10;
    }

    /** Botón pergamino chico con tooltip, agregado a la pantalla y a la lista de su pestaña (-1 = siempre visible). */
    private ButtonWidget boton(int pestana, int dx, int dy, int w, Text texto, String tooltip, int id) {
        ButtonWidget b = new EstiloPergamino.BotonPergamino(this.x + CX + dx, this.y + CY + dy, w, 16, texto, btn -> clickBoton(id));
        b.setTooltip(Tooltip.of(Text.translatable(tooltip)));
        agregar(pestana, b);
        return b;
    }

    private <T extends ClickableWidget> T agregar(int pestana, T w) {
        this.addDrawableChild(w);
        if (pestana >= 0) porPestana[pestana].add(w);
        return w;
    }

    @Override
    protected void init() {
        EstiloPergamino.usarTema(EstiloPergamino.Tema.VERDIN);
        super.init();
        for (int i = 0; i < N_PESTANAS; i++) porPestana[i] = new ArrayList<>();

        // Línea de producción (2026-10-08): apagada de fábrica; arriba a la derecha.
        this.addDrawableChild(new EstiloPergamino.BotonLinea(this.x + 452, this.y + 3, 96, 16,
                () -> this.handler.be.linea(), b -> clickBoton(TinturasBlockEntity.BTN_LINEA)));

        // Categoría con flechas a los costados.
        btnCategoriaAtras = new EstiloPergamino.BotonPergamino(this.x + X_COL, this.y + 24, 18, 16, Text.literal("‹"),
                b -> clickBoton(TinturasBlockEntity.BTN_CATEGORIA_ATRAS));
        btnCategoria = new EstiloPergamino.BotonPergamino(this.x + X_COL + 20, this.y + 24, ANCHO_COL - 40, 16, Text.literal(""),
                b -> clickBoton(TinturasBlockEntity.BTN_CATEGORIA));
        btnCategoria.setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.categoria")));
        btnCategoriaAdelante = new EstiloPergamino.BotonPergamino(this.x + X_COL + ANCHO_COL - 18, this.y + 24, 18, 16, Text.literal("›"),
                b -> clickBoton(TinturasBlockEntity.BTN_CATEGORIA));
        this.addDrawableChild(btnCategoriaAtras);
        this.addDrawableChild(btnCategoria);
        this.addDrawableChild(btnCategoriaAdelante);

        // Tira de zonas: ‹ ›, y un número por zona.
        btnZonaPrev = new EstiloPergamino.BotonPergamino(this.x + TIRA_X, this.y + TIRA_Y, 18, TIRA_ALTO, Text.literal("‹"), b -> cambiarZona(-1));
        btnZonaNext = new EstiloPergamino.BotonPergamino(this.x + TIRA_X + TIRA_ANCHO - 18, this.y + TIRA_Y, 18, TIRA_ALTO, Text.literal("›"), b -> cambiarZona(1));
        // Calzado (2026-10-15): simetría de los dos pies, prendida de fábrica; apagada se pintan por separado.
        btnSimetriaCalzado = new EstiloPergamino.BotonPergamino(this.x + TIRA_X + 100, this.y + TIRA_Y + 1, 78, TIRA_ALTO - 2, Text.empty(), b -> {
            TinturasBlockEntity be = this.handler.be;
            // Al prenderla el pie izquierdo desaparece: si estaba elegido, vuelve al primer cuadradito del derecho.
            clickBoton(TinturasBlockEntity.BTN_SIMETRIA_CALZADO);
            if (!be.simetriaCalzado() && be.seleccionada() >= 3) clickBoton(TinturasBlockEntity.BTN_SELECCIONAR_BASE);
        });
        btnSimetriaCalzado.setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.simetria_calzado")));
        this.addDrawableChild(btnSimetriaCalzado);
        this.addDrawableChild(btnZonaPrev);
        this.addDrawableChild(btnZonaNext);
        for (int i = 0; i < MAX_ZONAS; i++) {
            int idx = i;
            btnZona[i] = new ButtonWidget(0, 0, 16, TIRA_ALTO, Text.literal(String.valueOf(i + 1)), b -> irAZona(idx), d -> Text.empty()) {
                @Override
                protected void renderWidget(DrawContext c, int mx, int my, float d) {
                    boolean actual = idx == handler.zonaActiva;
                    EstiloPergamino.fondoBoton(c, getX(), getY(), getWidth(), getHeight(), isHovered() || actual, active);
                    c.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + 8, getY() + 5, actual ? 0xFFFFE0A8 : EstiloPergamino.TEXTO_CLARO);
                    if (zonaConAlgo(idx)) c.fill(getX() + 12, getY() + 2, getX() + 14, getY() + 4, 0xFF6EE06E);
                }
            };
            this.addDrawableChild(btnZona[i]);
        }

        // Una chincheta por cuadradito — se dibujan a mano en render().
        for (int i = 0; i < btnChinchetas.length; i++) {
            int idx = i;
            btnChinchetas[i] = new BotonChincheta(b -> clickBoton(TinturasBlockEntity.BTN_CHINCHETA_BASE + idx));
            btnChinchetas[i].tinte = EstiloPergamino.Tema.VERDIN.claro;
            this.addSelectableChild(btnChinchetas[i]);
        }

        // Pestañas de controles.
        for (int k = 0; k < N_PESTANAS; k++) {
            int valor = k;
            btnPestana[k] = new ButtonWidget(this.x + X_COL + k * 47, this.y + PESTANAS_Y, 46, 14,
                    Text.translatable("modamod.tinturas.pestana." + NOMBRE_PESTANA[k]), b -> { handler.pestana = valor; pagina = 0; }, d -> Text.empty()) {
                @Override
                protected void renderWidget(DrawContext c, int mx, int my, float d) {
                    EstiloPergamino.fondoBoton(c, getX(), getY(), getWidth(), getHeight(), isHovered() || handler.pestana == valor, active);
                    c.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2, getY() + 3,
                            handler.pestana == valor ? 0xFFFFE0A8 : EstiloPergamino.TEXTO_CLARO);
                }
            };
            this.addDrawableChild(btnPestana[k]);
        }

        // ── Pestaña Color: CMYK + transparencia (tanques), 3 colores, colores/contorno/variación, muestras ──
        for (int canal = 0; canal < TinturasBlockEntity.CANALES; canal++) {
            sliders[canal] = agregar(0, new CanalSlider(this.x + CX, this.y + CY + canal * 15, 150, 13,
                    canal, handler.be.nivelBorrador(canal)));
        }
        casillaMostradaEnSliders = handler.be.seleccionada();
        categoriaMostradaEnSliders = handler.be.categoria();
        editandoMostrado = handler.be.casilla(handler.be.seleccionada()).editando;
        for (int i = 0; i < btnMuestras.length; i++) {
            int idx = i;
            btnMuestras[i] = agregar(0, new BotonMuestra(idx, this.x + CX + 156, this.y + CY + i * 20, 74, 18,
                    b -> clickBoton(TinturasBlockEntity.BTN_EDITAR_COLOR_BASE + idx)));
            btnMuestras[i].setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.muestra", idx + 1)));
        }
        btnColores = boton(0, 0, 80, 74, Text.empty(), "modamod.tinturas.tooltip.colores", TinturasBlockEntity.BTN_COLORES);
        btnContorno = boton(0, 76, 80, 74, Text.empty(), "modamod.tinturas.tooltip.contorno", TinturasBlockEntity.BTN_CONTORNO);
        btnVariacion = boton(0, 152, 80, 78, Text.empty(), "modamod.tinturas.tooltip.variacion", TinturasBlockEntity.BTN_VARIACION);
        btnFondo = boton(0, 152, 100, 78, Text.empty(), "modamod.tinturas.tooltip.fondo", TinturasBlockEntity.BTN_FONDO);
        boton(0, 0, 100, 74, Text.translatable("modamod.tinturas.boton.envasar"),
                "modamod.tinturas.tooltip.envasar", TinturasBlockEntity.BTN_ENVASAR);
        ButtonWidget usar = new EstiloPergamino.BotonPergamino(this.x + CX + 76, this.y + CY + 100, 74, 16,
                Text.translatable("modamod.tinturas.boton.usar_muestra"), btn -> {
            clickBoton(TinturasBlockEntity.BTN_USAR_MUESTRA);
            // La mezcla nueva llega por la sincronización: los sliders se releen un rato.
            releerSlidersHasta = System.currentTimeMillis() + 800;
        });
        usar.setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.usar_muestra")));
        agregar(0, usar);

        // ── Pestaña Patrón ──
        btnTamano = boton(1, 0, 0, 52, Text.empty(), "modamod.tinturas.tooltip.tamano", TinturasBlockEntity.BTN_TAMANO);
        boton(1, 54, 0, 14, Text.literal("<"), "modamod.tinturas.tooltip.angulo", TinturasBlockEntity.BTN_ANGULO_ATRAS);
        btnAngulo = boton(1, 69, 0, 44, Text.empty(), "modamod.tinturas.tooltip.angulo", TinturasBlockEntity.BTN_ANGULO);
        boton(1, 114, 0, 14, Text.literal(">"), "modamod.tinturas.tooltip.angulo", TinturasBlockEntity.BTN_ANGULO);
        boton(1, 132, 0, 14, Text.literal("<"), "modamod.tinturas.tooltip.posicion", TinturasBlockEntity.BTN_POSICION_ATRAS);
        btnPosicion = boton(1, 147, 0, 68, Text.empty(), "modamod.tinturas.tooltip.posicion", TinturasBlockEntity.BTN_POSICION);
        boton(1, 216, 0, 14, Text.literal(">"), "modamod.tinturas.tooltip.posicion", TinturasBlockEntity.BTN_POSICION);

        btnForma = boton(1, 0, 19, 80, Text.empty(), "modamod.tinturas.tooltip.forma", TinturasBlockEntity.BTN_FORMA);
        btnSemilla = boton(1, 82, 19, 32, Text.translatable("modamod.tinturas.boton.semilla"),
                "modamod.tinturas.tooltip.semilla", TinturasBlockEntity.BTN_SEMILLA);
        btnInvertir = boton(1, 116, 19, 114, Text.empty(), "modamod.tinturas.tooltip.invertir", TinturasBlockEntity.BTN_INVERTIR);

        btnGiroAtras = boton(1, 0, 38, 14, Text.literal("<"), "modamod.tinturas.tooltip.giro_motivo", TinturasBlockEntity.BTN_GIRO_MOTIVO_ATRAS);
        btnGiro = boton(1, 15, 38, 44, Text.empty(), "modamod.tinturas.tooltip.giro_motivo", TinturasBlockEntity.BTN_GIRO_MOTIVO);
        btnGiroAdelante = boton(1, 60, 38, 14, Text.literal(">"), "modamod.tinturas.tooltip.giro_motivo", TinturasBlockEntity.BTN_GIRO_MOTIVO);
        TinturasBlockEntity.Casilla inicial = handler.be.casilla(handler.be.seleccionada());
        sliderDistH = agregar(1, new DistanciaSlider(this.x + CX + 76, this.y + CY + 38, 76, 16, true, inicial.distanciaH));
        sliderDistV = agregar(1, new DistanciaSlider(this.x + CX + 154, this.y + CY + 38, 76, 16, false, inicial.distanciaV));

        btnGiroAzar = boton(1, 0, 57, 76, Text.empty(), "modamod.tinturas.tooltip.giro_azar", TinturasBlockEntity.BTN_GIRO_AZAR);
        btnEspejo = boton(1, 77, 57, 76, Text.empty(), "modamod.tinturas.tooltip.espejo", TinturasBlockEntity.BTN_ESPEJO);
        btnAlternancia = boton(1, 154, 57, 76, Text.empty(), "modamod.tinturas.tooltip.alternancia", TinturasBlockEntity.BTN_ALTERNANCIA);
        btnSimetria = boton(1, 0, 76, 76, Text.empty(), "modamod.tinturas.tooltip.simetria", TinturasBlockEntity.BTN_SIMETRIA);
        boton(1, 77, 76, 14, Text.literal("<"), "modamod.tinturas.tooltip.posicion_x", TinturasBlockEntity.BTN_POSICION_X_ATRAS);
        btnPosX = boton(1, 92, 76, 48, Text.empty(), "modamod.tinturas.tooltip.posicion_x", TinturasBlockEntity.BTN_POSICION_X);
        boton(1, 141, 76, 14, Text.literal(">"), "modamod.tinturas.tooltip.posicion_x", TinturasBlockEntity.BTN_POSICION_X);

        // ── Pestaña Mezcla: modo, opacidad y orden ──
        // Una sola fila arriba del panel de capas: modo, opacidad y orden.
        btnModo = boton(2, 0, 0, 72, Text.empty(), "modamod.tinturas.tooltip.modo", TinturasBlockEntity.BTN_MODO);
        boton(2, 74, 0, 14, Text.literal("<"), "modamod.tinturas.tooltip.opacidad", TinturasBlockEntity.BTN_OPACIDAD_ATRAS);
        btnOpacidad = boton(2, 89, 0, 40, Text.empty(), "modamod.tinturas.tooltip.opacidad", TinturasBlockEntity.BTN_OPACIDAD);
        boton(2, 130, 0, 14, Text.literal(">"), "modamod.tinturas.tooltip.opacidad", TinturasBlockEntity.BTN_OPACIDAD);
        boton(2, 148, 0, 14, Text.literal("▼"), "modamod.tinturas.tooltip.bajar", TinturasBlockEntity.BTN_BAJAR);
        btnOrden = boton(2, 163, 0, 48, Text.empty(), "modamod.tinturas.tooltip.orden", TinturasBlockEntity.BTN_SUBIR);
        boton(2, 212, 0, 14, Text.literal("▲"), "modamod.tinturas.tooltip.subir", TinturasBlockEntity.BTN_SUBIR);

        // ── Pestaña Bolsa: páginas ──
        btnPagPrev = new EstiloPergamino.BotonPergamino(this.x + X_GRILLA, this.y + Y_GRILLA + FILAS_BOLSA * CELDA + 3, 18, 14, Text.literal("‹"), b -> pagina = Math.max(0, pagina - 1));
        btnPagNext = new EstiloPergamino.BotonPergamino(this.x + X_GRILLA + 22, this.y + Y_GRILLA + FILAS_BOLSA * CELDA + 3, 18, 14, Text.literal("›"), b -> pagina++);
        this.addDrawableChild(btnPagPrev);
        this.addDrawableChild(btnPagNext);

        // ── Cabecera: Teñir entre la Entrada y la barra ──
        btnTenir = new EstiloPergamino.BotonPergamino(this.x + TinturasScreenHandler.X_ENTRADA + 36, this.y + TinturasScreenHandler.Y_IO, 60, 16,
                Text.translatable("modamod.tinturas.boton.tenir"), b -> clickBoton(TinturasBlockEntity.BTN_TENIR));
        btnTenir.setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.tenir")));
        this.addDrawableChild(btnTenir);

        // ── Visor: Vista arriba de todo ──
        btnVista = new EstiloPergamino.BotonPergamino(this.x + VISOR_X1 + 4, this.y + VISOR_Y2 - 20, 90, 16, Text.literal(""),
                b -> anguloVista = Math.floorMod(Math.round(anguloVista) + 90, 360)) {
            @Override
            protected void renderWidget(DrawContext c, int mx, int my, float d) {
                c.getMatrices().push();
                c.getMatrices().translate(0, 0, 500);   // la prenda tiene profundidad hasta ~250
                super.renderWidget(c, mx, my, d);
                c.getMatrices().pop();
            }
        };
        btnVista.setTooltip(Tooltip.of(Text.translatable("modamod.preview.tooltip.vista")));
        this.addDrawableChild(btnVista);

        // ── Diseños (izquierda del inventario): "★ Último" y el "+" que fabrica un ítem de diseño ──
        btnUltimo = new EstiloPergamino.BotonPergamino(this.x + 8, this.y + 312, 105, 16, Text.translatable("modamod.tinturas.ultimo"),
                b -> clickBoton(TinturasBlockEntity.BTN_CARGAR_ULTIMO));
        btnUltimo.setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.ultimo")));
        this.addDrawableChild(btnUltimo);
        txtNombreDiseno = new net.minecraft.client.gui.widget.TextFieldWidget(
                this.textRenderer, this.x + 8, this.y + 336, 83, 14, Text.translatable("modamod.tinturas.nombre_diseno"));
        txtNombreDiseno.setMaxLength(24);
        txtNombreDiseno.setPlaceholder(Text.translatable("modamod.tinturas.nombre_diseno"));
        this.addDrawableChild(txtNombreDiseno);
        btnGuardarDiseno = new EstiloPergamino.BotonPergamino(this.x + 93, this.y + 336, 20, 14, Text.literal("+"), b -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.modamod.tinturas.GuardarDisenoTinturasPayload(this.handler.be.getPos(), txtNombreDiseno.getText()));
            txtNombreDiseno.setText("");
        });
        btnGuardarDiseno.setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.mas")));
        this.addDrawableChild(btnGuardarDiseno);
        // Borrar lo fijado (2026-10-14, "un boton de delete... y un delete all"): abajo a la izquierda, bajo el nombre del diseño.
        ButtonWidget btnBorrar = new EstiloPergamino.BotonPergamino(this.x + 8, this.y + 356, 36, 14, Text.translatable("modamod.tinturas.borrar"),
                b -> clickBoton(TinturasBlockEntity.BTN_BORRAR));
        btnBorrar.setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.borrar")));
        this.addDrawableChild(btnBorrar);
        ButtonWidget btnBorrarTodas = new EstiloPergamino.BotonPergamino(this.x + 46, this.y + 356, 67, 14, Text.translatable("modamod.tinturas.borrar_todo"),
                b -> clickBoton(TinturasBlockEntity.BTN_BORRAR_TODAS));
        btnBorrarTodas.setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.borrar_todo")));
        this.addDrawableChild(btnBorrarTodas);
        // Exportar el diseño elegido de la lista con el diseño en blanco del casillero de al lado (2026-10-25, "un botoncito de
        // exportar para exportar un diseñito que lleve el nombre del diseño guardado seleccionado").
        ButtonWidget btnExportar = new EstiloPergamino.BotonPergamino(this.x + 28, this.y + 377, 85, 14,
                Text.translatable("modamod.disenos.exportar"), b -> { net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ElegirPatronPayload(this.handler.be.getPos(), disenoElegido, net.minecraft.registry.Registries.ITEM.getId(com.modamod.tinturas.TinturasMod.DISENO_TINTA), ElegirPatronPayload.EXPORTAR_DISENO)); });
        btnExportar.setTooltip(Tooltip.of(Text.translatable("modamod.disenos.tooltip.exportar")));
        this.addDrawableChild(btnExportar);

        // Filtro por categoría de la bolsa de diseños (solo en esa pestaña).
        btnFiltro = new EstiloPergamino.BotonPergamino(this.x + X_GRILLA + 110, this.y + Y_GRILLA + 75, 106, 14, Text.literal(""), b -> {
            int n = TinturasBlockEntity.Categoria.values().length;
            filtroDisenos = filtroDisenos == n - 1 ? -2 : filtroDisenos + 1;
            pagina = 0;
        });
        btnFiltro.setTooltip(Tooltip.of(Text.translatable("modamod.modelado.disenos.filtro")));
        this.addDrawableChild(btnFiltro);
    }

    private void clickBoton(int id) {
        this.client.interactionManager.clickButton(this.handler.syncId, id);
    }

    // ── zonas ──

    private void irAZona(int zona) {
        int n = this.handler.be.zonasVisibles();
        this.handler.zonaActiva = Math.floorMod(zona, n);
        // Elegir otra zona deja elegido su primer cuadradito.
        int c = ZonasTintes.casillaDe(this.handler.be.categoria(), this.handler.zonaActiva, 0);
        if (c >= 0 && c != this.handler.be.seleccionada()) clickBoton(TinturasBlockEntity.BTN_SELECCIONAR_BASE + c);
    }

    private void cambiarZona(int delta) { irAZona(this.handler.zonaActiva + delta); }

    /** ¿Algún cuadradito de la zona está fijado? (puntito verde en la tira). */
    private boolean zonaConAlgo(int zona) {
        TinturasBlockEntity be = this.handler.be;
        TinturasBlockEntity.Categoria cat = be.categoria();
        for (int f = 0; f < 4; f++) {
            int c = ZonasTintes.casillaDe(cat, zona, f);
            if (c >= 0 && be.casilla(c).fijada) return true;
        }
        return false;
    }

    // ── bolsa de patrones ──

    /** Patrones de la bolsa, en el orden del registro. */
    private List<Identifier> candidatos() {
        List<Identifier> r = new ArrayList<>(this.handler.be.bolsaPatrones().keySet());
        r.sort(java.util.Comparator.comparingInt(id -> net.minecraft.registry.Registries.ITEM.getRawId(net.minecraft.registry.Registries.ITEM.get(id))));
        return r;
    }

    /** Celda de la grilla (índice absoluto en la lista) bajo el mouse, o -1. */
    private int celdaBajo(double mx, double my, int cantidad) {
        double lx = mx - this.x - X_GRILLA, ly = my - this.y - Y_GRILLA;
        if (lx < 0 || ly < 0 || lx >= COLS_BOLSA * CELDA || ly >= FILAS_BOLSA * CELDA) return -1;
        int idx = pagina * POR_HOJA + (int) (ly / CELDA) * COLS_BOLSA + (int) (lx / CELDA);
        return idx < cantidad ? idx : -1;
    }

    // ── bolsa de diseños ──

    /** Índices (en la bolsa) de los diseños que pasan el filtro de categoría. */
    private List<Integer> disenosFiltrados() {
        List<Integer> res = new ArrayList<>();
        List<ItemStack> ds = this.handler.be.disenosItem();
        String cat = filtroDisenos == -1 ? null
                : (filtroDisenos == -2 ? this.handler.be.categoria() : TinturasBlockEntity.Categoria.values()[filtroDisenos]).name();
        for (int i = 0; i < ds.size(); i++) {
            var d = ds.get(i).get(com.modamod.item.ModamodComponents.DISENO_TINTE);
            if (cat == null || (d != null && d.getString("Categoria").equals(cat))) res.add(i);
        }
        return res;
    }

    private Text textoFiltro() {
        if (filtroDisenos == -1) return Text.translatable("modamod.modelado.disenos.todas");
        TinturasBlockEntity.Categoria c = filtroDisenos == -2 ? this.handler.be.categoria() : TinturasBlockEntity.Categoria.values()[filtroDisenos];
        return Text.translatable("modamod.modelado.categoria." + c.name().toLowerCase(Locale.ROOT));
    }

    /** Diseño (índice real en la bolsa) bajo el mouse en la pestaña de diseños, o -1. */
    private int filaDiseno(double mx, double my) {
        double lx = mx - this.x - X_GRILLA, ly = my - this.y - Y_GRILLA;
        if (lx < 0 || ly < 0 || lx >= DIS_ANCHO || ly >= (DIS_POR_HOJA + 1) * DIS_ALTO) return -1;
        List<Integer> f = disenosFiltrados();
        int fila = (int) (ly / DIS_ALTO);
        if (fila == 0) return -2;   // la tarjeta "★ Último"
        int idx = pagina * DIS_POR_HOJA + fila - 1;
        return idx < f.size() ? f.get(idx) : -1;
    }

    /** Fila de la zona activa (0..) bajo el mouse, sin la columna de la chincheta, o -1. */
    private int filaBajo(double mx, double my) {
        double lx = mx - this.x - X_COL, ly = my - this.y - TinturasScreenHandler.Y_FILAS;
        if (lx < 0 || lx >= ANCHO_COL - 26 || ly < 0 || ly >= 4 * TinturasScreenHandler.ALTO_FILA) return -1;
        return (int) (ly / TinturasScreenHandler.ALTO_FILA);
    }

    // ── visor 3D: arrastrar para girar, click derecho para inclinar, rueda para zoom ──

    private boolean dentroDePreview(double mouseX, double mouseY) {
        return mouseX >= this.x + VISOR_X1 && mouseX < this.x + VISOR_X2 && mouseY >= this.y + VISOR_Y1 && mouseY < this.y + VISOR_Y2;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // OJO: HandledScreen#mouseClicked devuelve true casi siempre (también al clickear en vacío), así que lo propio se
        // resuelve ANTES de llamarlo; los botones y las chinchetas quedan fuera de estas áreas.
        TinturasBlockEntity be = this.handler.be;
        if (button == 0) {
            int f = filaBajo(mouseX, mouseY);
            if (f >= 0) {
                int c = ZonasTintes.casillaDe(be.categoria(), this.handler.zonaActiva, f);
                if (c >= 0 && c != be.seleccionada()) clickBoton(TinturasBlockEntity.BTN_SELECCIONAR_BASE + c);
                if (c >= 0) return true;
            }
            if (this.handler.pestana == TinturasScreenHandler.PESTANA_CAPAS && clickEnPanel(mouseX, mouseY)) return true;
        }
        // Click en un patrón de la bolsa: izquierdo lo fija en el cuadradito elegido, derecho saca uno de la bolsa.
        if (this.handler.pestana == TinturasScreenHandler.PESTANA_BOLSA && !be.trabajando() && (button == 0 || button == 1)) {
            List<Identifier> lista = candidatos();
            int c = celdaBajo(mouseX, mouseY, lista.size());
            if (c >= 0) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ElegirPatronPayload(
                        be.getPos(), be.seleccionada(), lista.get(c), button == 0 ? ElegirPatronPayload.ELEGIR : ElegirPatronPayload.SACAR));
                return true;
            }
        }
        // Bolsa de diseños: izquierdo carga el diseño, derecho lo saca como ítem.
        if (this.handler.pestana == TinturasScreenHandler.PESTANA_DISENOS && !be.trabajando() && (button == 0 || button == 1)) {
            int d = filaDiseno(mouseX, mouseY);
            if (d == -2 && button == 0) { clickBoton(TinturasBlockEntity.BTN_CARGAR_ULTIMO); return true; }
            if (d >= 0) {
                if (button == 0) disenoElegido = d;
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ElegirPatronPayload(
                        be.getPos(), d, net.minecraft.registry.Registries.ITEM.getId(com.modamod.tinturas.TinturasMod.DISENO_TINTA),
                        button == 0 ? ElegirPatronPayload.CARGAR_DISENO : ElegirPatronPayload.SACAR_DISENO));
                return true;
            }
        }
        if (dentroDePreview(mouseX, mouseY) && !btnVista.isMouseOver(mouseX, mouseY)) {
            if (button == 0) arrastrandoPreview = true;
            if (button == 1) inclinandoPreview = true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) arrastrandoPreview = false;
        if (button == 1) inclinandoPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (arrastrandoPreview) {
            anguloVista = Math.floorMod(Math.round(anguloVista + (float) deltaX * 1.15f), 360);
            return true;
        }
        if (inclinandoPreview) {
            inclinacion = MathHelper.clamp(inclinacion + (float) deltaY * 0.6f, -70f, 70f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dentroDePreview(mouseX, mouseY)) {
            zoomVista = MathHelper.clamp(zoomVista + (float) verticalAmount * 0.1f, ZOOM_MIN, ZOOM_MAX);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** Mismo motivo que {@code ModeladoScreen#keyPressed}: sin esto, escribir una "e" en el nombre del diseño cierra la pantalla. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return (!txtNombreDiseno.keyPressed(keyCode, scanCode, modifiers) && !txtNombreDiseno.isActive())
                ? super.keyPressed(keyCode, scanCode, modifiers) : true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ratonX = mouseX;
        ratonY = mouseY;
        refrescar();
        super.render(context, mouseX, mouseY, delta);
        // Chinchetas a mano, DESPUÉS de todo (z=400 adentro de su renderWidget).
        for (BotonChincheta b : btnChinchetas) {
            if (b.visible) b.render(context, mouseX, mouseY, delta);
        }
        if (handler.pestana == TinturasScreenHandler.PESTANA_BOLSA) {
            List<Identifier> lista = candidatos();
            int c = celdaBajo(mouseX, mouseY, lista.size());
            if (c >= 0) {
                context.drawItemTooltip(textRenderer, new ItemStack(net.minecraft.registry.Registries.ITEM.get(lista.get(c))), mouseX, mouseY);
            }
        }
        if (handler.pestana == TinturasScreenHandler.PESTANA_DISENOS) {
            int d = filaDiseno(mouseX, mouseY);
            if (d >= 0) context.drawItemTooltip(textRenderer, handler.be.disenosItem().get(d), mouseX, mouseY);
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
        tooltipPanel(context, mouseX, mouseY);
    }

    /**
     * Qué cuadradito resaltar en 3D: el de la fila del panel de capas con el mouse encima, el de la fila de la zona o el de
     * su chincheta. -1 = ninguno.
     */
    private int casillaResaltada(int mouseX, int mouseY) {
        int[] fila = filaPanelBajoMouse(mouseX, mouseY);
        if (fila != null) return fila[0];
        int f = filaBajo(mouseX, mouseY);
        if (f >= 0) return ZonasTintes.casillaDe(handler.be.categoria(), handler.zonaActiva, f);
        for (int i = 0; i < btnChinchetas.length; i++) {
            if (btnChinchetas[i].visible && btnChinchetas[i].isMouseOver(mouseX, mouseY)) return i;
        }
        return -1;
    }

    /** Visor 3D: la cámara va al enfoque de la zona activa con una transición suave (constante de ~90 ms). */
    private void dibujarPreview(DrawContext context, int mouseX, int mouseY, int resaltada) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity jugador = client.player;
        if (jugador == null) return;

        if (handler.be.categoria() == TinturasBlockEntity.Categoria.APLIQUE) {
            dibujarMuestrasRetazo(context, handler.be.prendaDeVistaPrevia(resaltada));
            return;
        }
        if (handler.be.categoria() == TinturasBlockEntity.Categoria.BANNER) {
            dibujarBanner(context, handler.be.prendaDeVistaPrevia(resaltada));
            return;
        }
        ItemStack prenda = handler.be.prendaDeVistaPrevia(resaltada);
        List<ItemStack> prendas = new ArrayList<>(GarmentFeatureRenderer.equipadas(jugador));
        // El sombrero (2026-10-14) no es ropa de cuerpo: se pone de mentira en la cabeza (como en la Mesa de estilado).
        // Lo mismo la banda y el calzado (2026-10-15): accesorios de cajas, se dibujan sobre el jugador.
        boolean esSombrero = prenda.getItem() instanceof com.modamod.item.ZonasTenibles;
        boolean esCalzado = prenda.getItem() instanceof com.modamod.item.BorcegosItem;
        // Máscara y barbijo (2026-10-24): los dibuja CaraRenderer, no son ropa de cuerpo.
        boolean esCara = prenda.getItem() instanceof com.modamod.item.CaraItem;
        if (!prenda.isEmpty() && !esSombrero && !esCara) {
            prendas.removeIf(s -> s.getItem().getClass() == prenda.getItem().getClass());
            prendas.add(prenda);
        }

        TinturasBlockEntity.Categoria cat = handler.be.categoria();
        ZonasTintes.Zona[] zonas = ZonasTintes.de(cat);
        ZonasModelado.Enfoque e = zonas[Math.min(handler.zonaActiva, zonas.length - 1)].enfoque();
        long ahora = net.minecraft.util.Util.getMeasuringTimeMs();
        float dt = ultimoCuadroMs < 0 ? 1000f : Math.min(200f, ahora - ultimoCuadroMs);
        ultimoCuadroMs = ahora;
        float k = 1f - (float) Math.exp(-dt / 90f);
        focoActual += (e.focoY - focoActual) * k;
        tamActual += (e.tamano - tamActual) * k;

        GarmentFeatureRenderer.previewOverride = prendas;
        GarmentFeatureRenderer.sombreroOverride = esSombrero && !esCalzado ? prenda : null;
        GarmentFeatureRenderer.calzadoOverride = esCalzado ? prenda : null;
        com.modamod.render.CaraRenderer.override = esCara ? prenda : null;
        try {
            PreviewJugador.dibujar(context, jugador, this.x + VISOR_X1, this.y + VISOR_Y1, this.x + VISOR_X2, this.y + VISOR_Y2,
                    Math.round(tamActual * zoomVista), anguloVista,
                    this.y + (VISOR_Y1 + VISOR_Y2) / 2f, inclinacion, focoActual);
        } finally {
            GarmentFeatureRenderer.previewOverride = null;
            GarmentFeatureRenderer.sombreroOverride = null;
            GarmentFeatureRenderer.calzadoOverride = null;
            com.modamod.render.CaraRenderer.override = null;
        }
    }

    /** Vista previa del banner (2026-10-24): el ítem grande, centrado en el visor (lo dibuja BannerHD con el tinte). */
    private void dibujarBanner(DrawContext context, ItemStack banner) {
        int x1 = this.x + VISOR_X1, y1 = this.y + VISOR_Y1, w = VISOR_X2 - VISOR_X1, h = VISOR_Y2 - VISOR_Y1;
        float esc = Math.min(w, h) * 0.8f / 16f;
        var m = context.getMatrices();
        m.push();
        m.translate(x1 + w / 2f - 8 * esc, y1 + h / 2f - 8 * esc, 0);
        m.scale(esc, esc, 1f);
        context.drawItem(banner, 0, 0);
        m.pop();
    }

    /** Vista previa del retazo de aplique (2026-10-04, fase 4): el ítem grande y las 3 zonas con su color. */
    private void dibujarMuestrasRetazo(DrawContext context, ItemStack retazo) {
        int x1 = this.x + VISOR_X1, y1 = this.y + VISOR_Y1;
        int x2 = this.x + VISOR_X2, y2 = this.y + VISOR_Y2;
        int w = x2 - x1, h = y2 - y1;
        var m = context.getMatrices();
        m.push();
        m.translate(x1 + w / 2f - 24, y1 + 10, 0);
        m.scale(3f, 3f, 1f);
        context.drawItem(retazo, 0, 0);
        m.pop();
        List<Integer> colores = com.modamod.aplique.RetazoApliqueItem.colores(retazo);
        int alto = Math.min(30, (h - 90) / 3);
        for (int i = 0; i < 3; i++) {
            int ty = y1 + 70 + i * (alto + 6);
            context.fill(x1 + 20, ty - 1, x2 - 20, ty + alto + 1, 0xFF2A180C);
            context.fill(x1 + 21, ty, x2 - 21, ty + alto, 0xFF000000 | colores.get(i));
            context.drawText(this.textRenderer, Text.translatable("modamod.aplique.zona", i + 1),
                    x1 + 26, ty + alto / 2 - 4, 0xFFFFFFFF, true);
        }
    }

    private void refrescar() {
        TinturasBlockEntity be = handler.be;
        TinturasBlockEntity.Categoria cat = be.categoria();
        int sel = be.seleccionada();
        TinturasBlockEntity.Casilla casilla = be.casilla(sel);
        int zonas = be.zonasVisibles();

        if (cat != ultimaCategoria) {
            ultimaCategoria = cat;
            handler.zonaActiva = 0;
            ultimaZonaDeSel = -1;
        }
        if (handler.zonaActiva >= zonas) handler.zonaActiva = 0;
        // Si lo elegido no es de la zona activa (cambió desde otro lado), la zona sigue al cuadradito elegido.
        int zonaDeSel = ZonasTintes.zonaDe(cat, sel);
        if (zonaDeSel >= 0 && zonaDeSel != ultimaZonaDeSel) {
            ultimaZonaDeSel = zonaDeSel;
            handler.zonaActiva = zonaDeSel;
        }

        btnCategoria.setMessage(Text.translatable("modamod.modelado.categoria." + cat.name().toLowerCase(Locale.ROOT)));
        btnModo.setMessage(Text.translatable(casilla.modo.traduccion()));
        btnOpacidad.setMessage(Text.literal(casilla.opacidad + "%"));
        int pos = be.posicionEnOrden(sel);
        btnOrden.setMessage(Text.translatable("modamod.tinturas.orden", pos, be.capasEnBorrador()));
        btnTamano.setMessage(Text.translatable(casilla.tamano.traduccion()));
        btnAngulo.setMessage(Text.translatable("modamod.tinturas.angulo", Math.round(casilla.angulo)));
        btnPosicion.setMessage(Text.translatable("modamod.tinturas.posicion", Math.round(casilla.posicion * 100)));
        btnGiro.setMessage(Text.translatable("modamod.tinturas.giro_motivo", Math.round(casilla.giroMotivo)));
        // Molde de motivo: el botón muestra/cicla la Repetición; de rayas, la Forma.
        com.modamod.render.Motivo motivo = be.motivoSeleccionado();
        if (motivo != null) {
            btnForma.setMessage(Text.translatable(casilla.repeticion.traduccion()));
            btnForma.setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.repeticion")));
        } else {
            btnForma.setMessage(Text.translatable(be.formaBorrador().traduccion()));
            btnForma.setTooltip(Tooltip.of(Text.translatable("modamod.tinturas.tooltip.forma")));
        }
        boolean giraMotivo = motivo != null && !motivo.esProcedural();
        btnGiro.active = btnGiroAtras.active = btnGiroAdelante.active = giraMotivo;
        btnEspejo.active = btnAlternancia.active = btnGiroAzar.active = btnPosX.active = giraMotivo;
        btnPosX.setMessage(Text.literal("X " + Math.round(casilla.posX * 100)));
        btnGiroAzar.setMessage(Text.translatable("modamod.tinturas.giro_azar", Text.translatable(casilla.giroAzar.traduccion())));
        btnEspejo.setMessage(Text.translatable(casilla.espejo.traduccion()));
        btnAlternancia.setMessage(Text.translatable(casilla.alternancia.traduccion()));
        btnSimetria.setMessage(Text.translatable("modamod.tinturas.simetria",
                Text.translatable(casilla.simetria ? "modamod.si" : "modamod.no")));
        btnSemilla.active = (motivo != null && casilla.repeticion == com.modamod.render.Repeticion.DISPERSO)
                || (casilla.variacion == com.modamod.render.Variacion.ALEATORIO && casilla.colores > 1);

        for (int i = 0; i < btnMuestras.length; i++) {
            btnMuestras[i].color = casilla.colorDe(i);
            btnMuestras[i].prendida = i < casilla.colores;
            btnMuestras[i].editando = i == casilla.editando;
            btnMuestras[i].contorno = casilla.contorno && casilla.colores > 1 && i == casilla.colores - 1;
        }
        btnColores.setMessage(Text.translatable("modamod.tinturas.colores", casilla.colores));
        btnContorno.setMessage(Text.translatable("modamod.tinturas.contorno",
                Text.translatable(casilla.contorno ? "modamod.si" : "modamod.no")));
        btnContorno.active = casilla.colores > 1;
        btnFondo.setMessage(Text.translatable("modamod.tinturas.fondo",
                Text.translatable(casilla.fondo ? "modamod.si" : "modamod.no")));
        btnVariacion.setMessage(Text.translatable(casilla.variacion.traduccion()));
        btnInvertir.setMessage(Text.translatable("modamod.tinturas.invertir",
                Text.translatable(casilla.invertido ? "modamod.si" : "modamod.no")));
        btnVista.setMessage(Text.translatable("modamod.preview.vista", Text.translatable(PreviewJugador.nombreVista(anguloVista))));

        // Los sliders muestran la mezcla del cuadradito SELECCIONADO — si cambió, se reposicionan a mano.
        if (sel != casillaMostradaEnSliders || cat != categoriaMostradaEnSliders || casilla.editando != editandoMostrado
                || System.currentTimeMillis() < releerSlidersHasta) {
            casillaMostradaEnSliders = sel;
            categoriaMostradaEnSliders = cat;
            editandoMostrado = casilla.editando;
            for (int canal = 0; canal < TinturasBlockEntity.CANALES; canal++) {
                sliders[canal].sincronizarDesdeServidor(be.nivelBorrador(canal));
            }
            sliderDistH.sincronizar(casilla.distanciaH);
            sliderDistV.sincronizar(casilla.distanciaV);
        }

        // Pestañas: solo se ven los controles de la pestaña activa.
        for (int k = 0; k < N_PESTANAS; k++) {
            for (ClickableWidget w : porPestana[k]) w.visible = handler.pestana == k;
        }
        boolean enBolsa = handler.pestana == TinturasScreenHandler.PESTANA_BOLSA;
        boolean enDisenos = handler.pestana == TinturasScreenHandler.PESTANA_DISENOS;
        int hojas = enDisenos ? Math.max(1, (disenosFiltrados().size() + DIS_POR_HOJA - 1) / DIS_POR_HOJA)
                : Math.max(1, (candidatos().size() + POR_HOJA - 1) / POR_HOJA);
        pagina = MathHelper.clamp(pagina, 0, hojas - 1);
        btnPagPrev.visible = btnPagNext.visible = (enBolsa || enDisenos) && hojas > 1;
        btnFiltro.visible = enDisenos;
        btnFiltro.setMessage(textoFiltro());
        btnPagPrev.active = pagina > 0;
        btnPagNext.active = pagina < hojas - 1;

        btnSimetriaCalzado.visible = cat == TinturasBlockEntity.Categoria.CALZADO;
        btnSimetriaCalzado.setMessage(Text.translatable(be.simetriaCalzado() ? "modamod.tinturas.simetria_calzado.si" : "modamod.tinturas.simetria_calzado.no"));
        btnZonaPrev.active = btnZonaNext.active = zonas > 1;
        for (int i = 0; i < MAX_ZONAS; i++) {
            btnZona[i].visible = i < zonas;
            btnZona[i].active = i < zonas;
            btnZona[i].setPosition(this.x + TIRA_X + TIRA_ANCHO - 20 - (zonas - i) * 18 + 2, this.y + TIRA_Y);
        }

        for (int i = 0; i < btnChinchetas.length; i++) {
            BotonChincheta b = btnChinchetas[i];
            com.modamod.region.RegionPintura region = TinturasBlockEntity.regionDe(cat, i);
            int fila = ZonasTintes.filaDe(cat, i);
            boolean usable = region != null && fila >= 0 && ZonasTintes.zonaDe(cat, i) == handler.zonaActiva;
            b.visible = usable;
            b.active = usable;
            if (!usable) continue;
            b.setPosition(this.x + X_COL + ANCHO_COL - 22, this.y + TinturasScreenHandler.Y_FILAS + fila * TinturasScreenHandler.ALTO_FILA + 3);
            TinturasBlockEntity.Casilla cas = be.casilla(i);
            b.actualizar(cas.fijada);
            String clave = cas.fijada ? "modamod.tinturas.tooltip.chincheta_quitar" : "modamod.tinturas.tooltip.chincheta_liso";
            b.setTooltip(Tooltip.of(Text.translatable(clave, Text.translatable(region.traduccion()))));
        }

        btnTenir.active = be.puedeReintentar();

        btnGuardarDiseno.active = be.hayFijadas();
        btnUltimo.active = be.hayUltimo();
    }

    private int ultimaZonaDeSel = -1;

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        EstiloPergamino.titulo(context, this.textRenderer, this.title, this.titleX, this.titleY);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX,
                this.playerInventoryTitleY, EstiloPergamino.TEXTO, false);

        dibujarFilas(context);

        TinturasBlockEntity be = handler.be;
        int pest = handler.pestana;
        if (pest == TinturasScreenHandler.PESTANA_COLOR) {
            // Qué se está editando: región del cuadradito seleccionado y su estado.
            com.modamod.region.RegionPintura region = TinturasBlockEntity.regionDe(be.categoria(), be.seleccionada());
            if (region != null) {
                TinturasBlockEntity.Casilla sel = be.casilla(be.seleccionada());
                Text estado = Text.translatable(sel.oculta ? "modamod.tinturas.estado.oculta"
                        : sel.fijada ? "modamod.tinturas.estado.fijada" : "modamod.tinturas.estado.borrador");
                context.drawText(this.textRenderer, Text.translatable("modamod.tinturas.editando",
                        Text.translatable(region.traduccion()), estado), CX, CY + 122, EstiloPergamino.TEXTO, false);
            }
        } else if (pest == TinturasScreenHandler.PESTANA_CAPAS) {
            dibujarPanelCapas(context, mouseX, mouseY);
        } else if (pest == TinturasScreenHandler.PESTANA_BOLSA) {
            dibujarBolsa(context);
        } else if (pest == TinturasScreenHandler.PESTANA_DISENOS) {
            dibujarDisenos(context);
        }
    }

    /** Las filas de la zona activa: muestra del color con el patrón encima, nombre, patrón y estado. Coordenadas locales. */
    private void dibujarFilas(DrawContext context) {
        TinturasBlockEntity be = handler.be;
        TinturasBlockEntity.Categoria cat = be.categoria();
        for (int f = 0; f < 4; f++) {
            int i = ZonasTintes.casillaDe(cat, handler.zonaActiva, f);
            if (i < 0) continue;
            com.modamod.region.RegionPintura region = TinturasBlockEntity.regionDe(cat, i);
            if (region == null) continue;
            TinturasBlockEntity.Casilla cas = be.casilla(i);
            int top = TinturasScreenHandler.Y_FILAS + f * TinturasScreenHandler.ALTO_FILA;
            int sx = X_COL + 3, sy = top + 3;
            if (cas.fijada && cas.patronFijado != null) {
                ClothingPatternItem item = ClothingPatternItem.porId(cas.patronFijado);
                if (item != null) context.drawItem(new ItemStack(item), sx, sy);
            }
            // Nombre: la zona (las de prenda entera llevan su número) y debajo patrón · estado.
            Text nombre = Text.translatable(region.traduccion());
            if (region == com.modamod.region.RegionPintura.TODO) {
                int n = 1;
                for (int j = 0; j < i; j++) if (TinturasBlockEntity.regionDe(cat, j) == com.modamod.region.RegionPintura.TODO) n++;
                nombre = Text.empty().append(nombre).append(" " + n);
            }
            context.drawText(textRenderer, nombre, X_COL + 24, top + 2, EstiloPergamino.TEXTO, false);
            Identifier patron = cas.patronFijado;
            ClothingPatternItem pi = patron == null ? null : ClothingPatternItem.porId(patron);
            Text detalle = pi != null ? pi.getName() : Text.translatable("modamod.tinturas.capas.liso");
            Text estado = Text.translatable(cas.oculta ? "modamod.tinturas.estado.oculta"
                    : cas.fijada ? "modamod.tinturas.estado.fijada" : "modamod.tinturas.estado.borrador");
            Text valor = Text.empty().append(detalle).append(" · ").append(estado);
            context.drawText(textRenderer, net.minecraft.util.Language.getInstance().reorder(
                    textRenderer.trimToWidth(valor, ANCHO_COL - 24 - 26)), X_COL + 24, top + 11, 0xFF7A5A36, false);
        }
    }

    /** Lista de diseños de la bolsa (coordenadas locales). */
    private void dibujarDisenos(DrawContext c) {
        List<ItemStack> ds = handler.be.disenosItem();
        List<Integer> filtrados = disenosFiltrados();
        c.drawText(textRenderer, Text.translatable("modamod.tinturas.disenos.ayuda"), CX + 4, Y_GRILLA + 92, 0xFF7A5A36, false);
        if (filtrados.isEmpty()) {
            c.drawText(textRenderer, Text.translatable("modamod.tinturas.disenos.vacio"), X_GRILLA + 3, Y_GRILLA + 4, EstiloPergamino.TEXTO, false);
        }
        IconoDiseno.tarjetaUltimo(c, textRenderer, X_GRILLA, Y_GRILLA, DIS_ANCHO, DIS_ALTO - 1, filaDiseno(ratonX, ratonY) == -2);
        for (int k = 0; k < DIS_POR_HOJA; k++) {
            int idx = pagina * DIS_POR_HOJA + k;
            int ry = Y_GRILLA + (k + 1) * DIS_ALTO;
            if (idx >= filtrados.size()) continue;
            int real = filtrados.get(idx);
            var dt = ds.get(real).get(com.modamod.item.ModamodComponents.DISENO_TINTE);
            String cat = dt == null ? null : Text.translatable("modamod.modelado.categoria." + dt.getString("Categoria").toLowerCase(Locale.ROOT)).getString();
            IconoDiseno.fila(c, textRenderer, X_GRILLA, ry, DIS_ANCHO, DIS_ALTO - 1, (filaDiseno(ratonX, ratonY) == real || real == disenoElegido), ds.get(real), cat);
        }
        if (btnPagPrev.visible) {
            int hojas = Math.max(1, (filtrados.size() + DIS_POR_HOJA - 1) / DIS_POR_HOJA);
            c.drawText(textRenderer, (pagina + 1) + "/" + hojas, X_GRILLA + 48, Y_GRILLA + 78, EstiloPergamino.TEXTO, false);
        }
        int bx = TinturasScreenHandler.X_BOLSA, by = TinturasScreenHandler.Y_BOLSA;
        c.drawText(textRenderer, Text.translatable("modamod.tinturas.bolsa.depositar_disenos"), bx + 24, by, EstiloPergamino.TEXTO, false);
        c.drawText(textRenderer, Text.translatable("modamod.tinturas.bolsa.ayuda_disenos"), bx + 24, by + 10, 0xFF7A5A36, false);
    }

    private void dibujarBolsa(DrawContext c) {
        TinturasBlockEntity be = handler.be;
        List<Identifier> lista = candidatos();
        int sel = be.seleccionada();
        Identifier fijado = be.casilla(sel).patronFijado;
        for (int k = 0; k < POR_HOJA; k++) {
            int idx = pagina * POR_HOJA + k;
            int cx = X_GRILLA + (k % COLS_BOLSA) * CELDA, cy = Y_GRILLA + (k / COLS_BOLSA) * CELDA;
            c.fill(cx, cy, cx + CELDA - 2, cy + CELDA - 2, 0xFF2A180C);
            boolean hay = idx < lista.size();
            var item = hay ? net.minecraft.registry.Registries.ITEM.get(lista.get(idx)) : null;
            boolean marcado = hay && item instanceof ClothingPatternItem p && p.patternId.equals(fijado);
            c.fill(cx + 1, cy + 1, cx + CELDA - 3, cy + CELDA - 3, marcado ? EstiloPergamino.tema().claro : 0xFF8B7355);
            if (!hay) continue;
            ItemStack st = new ItemStack(item, Math.min(99, be.bolsaPatrones().getOrDefault(lista.get(idx), 1)));
            c.drawItem(st, cx + 3, cy + 3);
            c.drawItemInSlot(textRenderer, st, cx + 3, cy + 3);
        }
        if (lista.isEmpty()) {
            c.drawText(textRenderer, Text.translatable("modamod.tinturas.bolsa.vacia"), CX + 4, CY + 4, EstiloPergamino.TEXTO, false);
        }
        if (btnPagPrev.visible) {
            int hojas = Math.max(1, (lista.size() + POR_HOJA - 1) / POR_HOJA);
            c.drawText(textRenderer, (pagina + 1) + "/" + hojas, X_GRILLA + 48, Y_GRILLA + FILAS_BOLSA * CELDA + 6, EstiloPergamino.TEXTO, false);
        }
        int bx = TinturasScreenHandler.X_BOLSA, by = TinturasScreenHandler.Y_BOLSA;
        c.drawText(textRenderer, Text.translatable("modamod.tinturas.bolsa.depositar"), bx + 24, by, EstiloPergamino.TEXTO, false);
        c.drawText(textRenderer, Text.translatable("modamod.tinturas.bolsa.ayuda"), bx + 24, by + 10, 0xFF7A5A36, false);
    }

    // ── panel de capas, dentro de la pestaña Capas ──
    private static final int PANEL_X = CX, PANEL_Y = CY + 20, PANEL_ANCHO = 228;
    private static final int FILA_Y0 = PANEL_Y + 11, FILA_ALTO = 9;
    /** Zonas de una fila, en x local relativa a PANEL_X. */
    private static final int OJO_X1 = 11, SUBIR_X0 = PANEL_ANCHO - 20, BAJAR_X0 = PANEL_ANCHO - 10;
    private static final int ZONA_OJO = 0, ZONA_NOMBRE = 1, ZONA_SUBIR = 2, ZONA_BAJAR = 3;

    /** {cuadradito, zona} de la fila del panel con el mouse encima (solo con la pestaña Capas), o null. */
    @org.jetbrains.annotations.Nullable
    private int[] filaPanelBajoMouse(double mouseX, double mouseY) {
        if (handler.pestana != TinturasScreenHandler.PESTANA_CAPAS) return null;
        double lx = mouseX - this.x - PANEL_X, ly = mouseY - this.y - FILA_Y0;
        if (lx < 0 || lx >= PANEL_ANCHO || ly < 0) return null;
        List<Integer> filas = handler.be.capasDelPanel();
        int f = (int) (ly / FILA_ALTO);
        if (f >= filas.size()) return null;
        int zona = lx < OJO_X1 ? ZONA_OJO : lx >= BAJAR_X0 ? ZONA_BAJAR : lx >= SUBIR_X0 ? ZONA_SUBIR : ZONA_NOMBRE;
        return new int[]{filas.get(f), zona};
    }

    private boolean clickEnPanel(double mouseX, double mouseY) {
        int[] fila = filaPanelBajoMouse(mouseX, mouseY);
        if (fila == null) return false;
        int i = fila[0];
        int id = switch (fila[1]) {
            case ZONA_OJO -> TinturasBlockEntity.BTN_OJO_BASE + i;
            case ZONA_SUBIR -> TinturasBlockEntity.BTN_SUBIR_BASE + i;
            case ZONA_BAJAR -> TinturasBlockEntity.BTN_BAJAR_BASE + i;
            default -> i == handler.be.seleccionada() ? -1 : TinturasBlockEntity.BTN_SELECCIONAR_BASE + i;
        };
        if (id >= 0) {
            clickBoton(id);
            this.client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                    net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
        return true;
    }

    /** Coordenadas LOCALES (drawForeground ya está corrido a this.x/this.y). */
    private void dibujarPanelCapas(DrawContext c, int mouseX, int mouseY) {
        TinturasBlockEntity be = handler.be;
        TinturasBlockEntity.Categoria cat = be.categoria();
        c.drawText(this.textRenderer, Text.translatable("modamod.tinturas.capas"), PANEL_X, PANEL_Y, EstiloPergamino.TEXTO, false);
        c.fill(PANEL_X, PANEL_Y + 9, PANEL_X + PANEL_ANCHO, PANEL_Y + 10, 0xFF6B4E2A);

        List<Integer> filas = be.capasDelPanel();
        if (filas.isEmpty()) {
            c.drawText(this.textRenderer, Text.translatable("modamod.tinturas.capas.vacio"),
                    PANEL_X, FILA_Y0 + 1, EstiloPergamino.TEXTO_APAGADO, false);
            return;
        }
        int[] bajo = filaPanelBajoMouse(mouseX, mouseY);
        for (int f = 0; f < filas.size(); f++) {
            int i = filas.get(f);
            TinturasBlockEntity.Casilla cas = be.casilla(i);
            int y = FILA_Y0 + f * FILA_ALTO;
            int x0 = PANEL_X, x1 = PANEL_X + PANEL_ANCHO;
            boolean hover = bajo != null && bajo[0] == i;
            if (i == be.seleccionada()) {
                c.fill(x0, y, x1, y + FILA_ALTO, 0x40FFD24C);
                c.drawBorder(x0, y, PANEL_ANCHO, FILA_ALTO, 0xFFC79A4B);
            } else if (hover) {
                c.fill(x0, y, x1, y + FILA_ALTO, 0x30FFFFFF);
            }

            dibujarOjo(c, x0 + 1, y + 1, !cas.oculta, hover && bajo[1] == ZONA_OJO);

            // Muestra: una franja por color de la capa.
            int mx0 = x0 + 12, mx1 = x0 + 21;
            int n = Math.max(1, cas.colores);
            for (int k = 0; k < n; k++) {
                int a = mx0 + (mx1 - mx0) * k / n, b = mx0 + (mx1 - mx0) * (k + 1) / n;
                dibujarMuestra(c, a, y + 1, b, y + 8, cas.colorDe(k));
            }
            c.drawBorder(mx0 - 1, y, mx1 - mx0 + 2, 9, 0xFF2A180C);
            if (cas.oculta) c.fill(mx0, y + 1, mx1, y + 8, 0xA0D9B98A);

            // Nombre: zona · patrón (o "liso"); sin fijar va en cursiva.
            com.modamod.region.RegionPintura region = TinturasBlockEntity.regionDe(cat, i);
            Identifier patron = be.patronEnBorrador(cat, i);
            ClothingPatternItem item = patron == null ? null : ClothingPatternItem.porId(patron);
            Text detalle = item != null ? item.getName() : Text.translatable("modamod.tinturas.capas.liso");
            net.minecraft.text.MutableText nombre = Text.empty()
                    .append(region == null ? Text.empty() : Text.translatable(region.traduccion()))
                    .append(" · ").append(detalle);
            if (!cas.fijada) nombre = nombre.formatted(net.minecraft.util.Formatting.ITALIC);
            int anchoNombre = SUBIR_X0 - 24;
            net.minecraft.text.StringVisitable recortado = this.textRenderer.trimToWidth(nombre, anchoNombre);
            c.drawText(this.textRenderer, net.minecraft.util.Language.getInstance().reorder(recortado),
                    x0 + 23, y + 1, cas.oculta ? EstiloPergamino.TEXTO_APAGADO : EstiloPergamino.TEXTO, false);

            boolean puedeSubir = f > 0, puedeBajar = f < filas.size() - 1;
            dibujarFlecha(c, "▲", x0 + SUBIR_X0, y, puedeSubir, hover && bajo[1] == ZONA_SUBIR);
            dibujarFlecha(c, "▼", x0 + BAJAR_X0, y, puedeBajar, hover && bajo[1] == ZONA_BAJAR);
        }
    }

    private void dibujarFlecha(DrawContext c, String flecha, int x, int y, boolean activa, boolean hover) {
        if (hover && activa) c.fill(x, y, x + 10, y + FILA_ALTO, 0x40FFFFFF);
        c.drawText(this.textRenderer, flecha, x + 2, y + 1,
                activa ? (hover ? 0xFF6B4E2A : EstiloPergamino.TEXTO) : EstiloPergamino.TEXTO_APAGADO, false);
    }

    /** Ojo de 9x7 a mano (la fuente no trae uno): abierto con pupila, o cerrado con pestañas. */
    private static void dibujarOjo(DrawContext c, int x, int y, boolean abierto, boolean hover) {
        int col = hover ? 0xFFC79A4B : 0xFF3B2410;
        if (abierto) {
            c.fill(x, y + 3, x + 1, y + 4, col);
            c.fill(x + 1, y + 2, x + 2, y + 3, col);
            c.fill(x + 1, y + 4, x + 2, y + 5, col);
            c.fill(x + 2, y + 1, x + 7, y + 2, col);
            c.fill(x + 2, y + 5, x + 7, y + 6, col);
            c.fill(x + 7, y + 2, x + 8, y + 3, col);
            c.fill(x + 7, y + 4, x + 8, y + 5, col);
            c.fill(x + 8, y + 3, x + 9, y + 4, col);
            c.fill(x + 3, y + 2, x + 6, y + 5, col);
        } else {
            c.fill(x, y + 3, x + 9, y + 4, col);
            c.fill(x + 1, y + 4, x + 2, y + 5, col);
            c.fill(x + 4, y + 4, x + 5, y + 6, col);
            c.fill(x + 7, y + 4, x + 8, y + 5, col);
        }
    }

    private void tooltipPanel(DrawContext context, int mouseX, int mouseY) {
        int[] fila = filaPanelBajoMouse(mouseX, mouseY);
        if (fila == null) return;
        String clave = switch (fila[1]) {
            case ZONA_OJO -> handler.be.casilla(fila[0]).oculta
                    ? "modamod.tinturas.tooltip.ojo_mostrar" : "modamod.tinturas.tooltip.ojo_ocultar";
            case ZONA_SUBIR -> "modamod.tinturas.tooltip.subir";
            case ZONA_BAJAR -> "modamod.tinturas.tooltip.bajar";
            default -> null;
        };
        if (clave != null) context.drawTooltip(this.textRenderer, Text.translatable(clave), mouseX, mouseY);
    }

    /**
     * Rellena una muestra con un color de la mezcla: si tiene Transparencia (byte alto, 2026-09-28), primero un fondo a
     * cuadros y encima el color con ese alfa — así se ve cuánto calará la tela.
     */
    static void dibujarMuestra(DrawContext c, int x0, int y0, int x1, int y1, int color) {
        int t = (color >>> 24) & 0xFF;
        if (t > 0) {
            for (int y = y0; y < y1; y += 4) {
                for (int x = x0; x < x1; x += 4) {
                    boolean claro = ((x - x0) / 4 + (y - y0) / 4) % 2 == 0;
                    c.fill(x, y, Math.min(x + 4, x1), Math.min(y + 4, y1), claro ? 0xFFE0E0E0 : 0xFF9A9A9A);
                }
            }
        }
        c.fill(x0, y0, x1, y1, ((255 - t) << 24) | (color & 0xFFFFFF));
    }

    private Text hint() {
        TinturasBlockEntity be = handler.be;
        if (be.estado() == TinturasBlockEntity.Estado.TINIENDO) return Text.translatable("modamod.tinturas.hint.tiniendo");
        if (!be.getSalida().isEmpty()) return Text.translatable("modamod.tinturas.hint.listo");
        if (!be.hayFijadas()) return Text.translatable("modamod.tinturas.hint.vacio");
        return Text.translatable("modamod.tinturas.hint.fijado");
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int x0 = this.x, y0 = this.y, w = backgroundWidth, h = backgroundHeight;
        var tema = EstiloPergamino.tema();
        TinturasBlockEntity be = handler.be;
        TinturasBlockEntity.Categoria cat = be.categoria();
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
        for (int py = y0 + 24; py < y0 + 302; py += 4) c.fill(x0 + X_COL - 3, py, x0 + X_COL - 2, py + 2, tema.oscuro);
        for (int py = y0 + 312; py < y0 + 400; py += 4) {
            c.fill(x0 + 116, py, x0 + 117, py + 2, tema.oscuro);
            c.fill(x0 + 284, py, x0 + 285, py + 2, tema.oscuro);
        }

        // A. Visor (el jugador se dibuja ahora, antes de los botones).
        c.fill(x0 + VISOR_X1 - 1, y0 + VISOR_Y1 - 1, x0 + VISOR_X2 + 1, y0 + VISOR_Y2 + 1, 0xFF2A180C);
        c.fill(x0 + VISOR_X1, y0 + VISOR_Y1, x0 + VISOR_X2, y0 + VISOR_Y2, 0xFF4A3828);
        dibujarPreview(c, mouseX, mouseY, casillaResaltada(mouseX, mouseY));

        // Tira: el nombre de la zona entre ‹ y los números.
        int zonas = be.zonasVisibles();
        int zi = Math.min(handler.zonaActiva, zonas - 1);
        c.fill(x0 + TIRA_X + 20, y0 + TIRA_Y, x0 + TIRA_X + TIRA_ANCHO - 20, y0 + TIRA_Y + TIRA_ALTO, 0x33000000);
        c.drawText(textRenderer, Text.translatable("modamod.tinturas.zona." + ZonasTintes.de(cat)[zi].clave()),
                x0 + TIRA_X + 26, y0 + TIRA_Y + 5, EstiloPergamino.TEXTO, false);

        // Filas: fondo, y marco dorado en el cuadradito elegido (más la tira del color de su capa).
        for (int f = 0; f < 4; f++) {
            int i = ZonasTintes.casillaDe(cat, zi, f);
            if (i < 0 || TinturasBlockEntity.regionDe(cat, i) == null) continue;
            int top = y0 + TinturasScreenHandler.Y_FILAS + f * TinturasScreenHandler.ALTO_FILA;
            boolean elegido = i == be.seleccionada();
            c.fill(x0 + X_COL, top, x0 + X_COL + ANCHO_COL, top + TinturasScreenHandler.ALTO_FILA - 1, elegido ? 0x77FFE0A8 : 0x33FFFFFF);
            if (elegido) c.drawBorder(x0 + X_COL, top, ANCHO_COL, TinturasScreenHandler.ALTO_FILA - 1, 0xFFFFD24C);
            int sx = x0 + X_COL + 3, sy = top + 3;
            c.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF2A180C);
            c.fill(sx, sy, sx + 16, sy + 16, 0xFF8B7355);
            if (be.enBorrador(cat, i)) {
                // Tira del color de la capa debajo del patrón: se lee de un vistazo qué color va en cada parte.
                c.fill(sx, sy + 13, sx + 16, sy + 16, 0xFF000000 | be.casilla(i).color());
            }
        }

        // Pestañas: fondo del contenido y lo que se pinta por código.
        c.fill(x0 + X_COL, y0 + PESTANAS_Y + 14, x0 + X_COL + ANCHO_COL, y0 + 302, 0x22000000);
        if (handler.pestana == TinturasScreenHandler.PESTANA_COLOR) {
            int mx0 = x0 + CX + 156, my0 = y0 + CY + 62;
            dibujarMuestra(c, mx0, my0, mx0 + 74, my0 + 30, be.colorBorrador());
            c.drawBorder(mx0 - 1, my0 - 1, 76, 32, 0xFF2A180C);
        } else if (handler.pestana == TinturasScreenHandler.PESTANA_BOLSA || handler.pestana == TinturasScreenHandler.PESTANA_DISENOS) {
            int bx = x0 + TinturasScreenHandler.X_BOLSA, by = y0 + TinturasScreenHandler.Y_BOLSA;
            marco(c, bx, by);
        }

        // Cabecera: Entrada → Teñir → barra → Salida, estado e insumos.
        marcoGrande(c, x0 + TinturasScreenHandler.X_ENTRADA, y0 + TinturasScreenHandler.Y_IO);
        marcoGrande(c, x0 + TinturasScreenHandler.X_SALIDA, y0 + TinturasScreenHandler.Y_IO);
        float nivel = be.estado() == TinturasBlockEntity.Estado.TINIENDO ? be.progresoFraccion()
                : !be.getSalida().isEmpty() ? 1f : 0f;
        int bx = x0 + TinturasScreenHandler.X_ENTRADA + 104, by = y0 + TinturasScreenHandler.Y_IO + 5, bw = 92;
        c.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF2A180C);
        c.fill(bx, by, bx + bw, by + 6, 0xFF111111);
        c.fill(bx, by, bx + (int) (bw * nivel), by + 6, tema.claro);
        c.drawText(textRenderer, net.minecraft.util.Language.getInstance().reorder(textRenderer.trimToWidth(hint(), 250)),
                x0 + 296, y0 + 358, EstiloPergamino.TEXTO, false);

        dibujarTanques(c, x0 + 296, y0 + 372);

        // Inventario del jugador.
        int xi = x0 + TinturasScreenHandler.X_INV, yi = y0 + TinturasScreenHandler.Y_INV;
        for (int f = 0; f < 3; f++) for (int col = 0; col < 9; col++) marco(c, xi + col * 18, yi + f * 18);
        for (int col = 0; col < 9; col++) marco(c, xi + col * 18, yi + 58);
        IconoDiseno.marcoBlanco(c, this.x + 9, this.y + 377, this.handler.slots.get(this.handler.slots.size() - 1).hasStack());
    }

    /**
     * Los 4 tanques de tinta abajo, junto a la cabecera (2026-10-12, "movéme los indicadores cmyk abajo como hicimos con
     * la tijera en la modeladora"): cada uno con su letra, una barra del color de la tinta y "n/64" (en rojo si está vacío).
     */
    private void dibujarTanques(DrawContext c, int x0, int y0) {
        String[] letra = {"C", "M", "Y", "K"};
        for (int canal = 0; canal < 4; canal++) {
            int tx = x0 + (canal % 2) * 132, ty = y0 + (canal / 2) * 12;
            int carga = handler.be.carga(canal);
            c.drawText(textRenderer, letra[canal], tx, ty, EstiloPergamino.TEXTO, false);
            int bx = tx + 10;
            c.fill(bx - 1, ty - 1, bx + 81, ty + 9, 0xFF2A180C);
            c.fill(bx, ty, bx + 80, ty + 8, 0xFF111111);
            int lleno = 80 * carga / TinturasBlockEntity.CARGA_MAXIMA;
            if (lleno > 0) {
                c.fill(bx, ty, bx + lleno, ty + 8, COLOR_TANQUE[canal]);
                c.fill(bx, ty, bx + lleno, ty + 1, 0x40FFFFFF);
            }
            c.drawText(textRenderer, carga + "/" + TinturasBlockEntity.CARGA_MAXIMA, bx + 84, ty, carga == 0 ? 0xFF8A2E2E : EstiloPergamino.TEXTO, false);
        }
    }

    private void marco(DrawContext c, int sx, int sy) {
        c.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF2A180C);
        c.fill(sx, sy, sx + 16, sy + 16, 0xFF8B7355);
    }

    /** Marco de 40x40 alrededor de un casillero de 16x16 dibujado a {@link TinturasScreenHandler#ESCALA_IO}. */
    private void marcoGrande(DrawContext c, int sx, int sy) {
        c.fill(sx - 13, sy - 13, sx + 29, sy + 29, 0xFF2A180C);
        c.fill(sx - 12, sy - 12, sx + 28, sy + 28, 0xFF8B7355);
    }

    @Override
    protected void drawSlot(DrawContext context, net.minecraft.screen.slot.Slot slot) {
        if (!TinturasScreenHandler.esSlotGrande(slot.x, slot.y)) {
            super.drawSlot(context, slot);
            return;
        }
        float e = TinturasScreenHandler.ESCALA_IO;
        context.getMatrices().push();
        context.getMatrices().translate(slot.x + 8, slot.y + 8, 0);
        context.getMatrices().scale(e, e, 1f);
        context.getMatrices().translate(-(slot.x + 8), -(slot.y + 8), 0);
        super.drawSlot(context, slot);
        context.getMatrices().pop();
    }

    @Override
    protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        if (width == 16 && height == 16 && TinturasScreenHandler.esSlotGrande(x, y)) {
            return super.isPointWithinBounds(x - 12, y - 12, 40, 40, pointX, pointY);
        }
        return super.isPointWithinBounds(x, y, width, height, pointX, pointY);
    }

    /**
     * Distancia horizontal o vertical del patrón (2026-09-30): de pegados (50 %) al triple (300 %), de a 5 %. Mismo
     * mecanismo que los CMYK: cuantiza y solo manda el click cuando cambia el escalón.
     */
    private class DistanciaSlider extends SliderWidget {
        private final boolean horizontal;
        private int ultimoNivelEnviado;

        DistanciaSlider(int x, int y, int width, int height, boolean horizontal, float distancia) {
            super(x, y, width, height, Text.empty(), 0);
            this.horizontal = horizontal;
            sincronizar(distancia);
            setTooltip(Tooltip.of(Text.translatable(horizontal
                    ? "modamod.tinturas.tooltip.distancia_h" : "modamod.tinturas.tooltip.distancia_v")));
        }

        private int nivel() {
            return Math.round((float) (this.value * (com.modamod.render.DistribucionPatron.NIVELES - 1)));
        }

        void sincronizar(float distancia) {
            int nivel = com.modamod.render.DistribucionPatron.nivelDeDistancia(distancia);
            this.value = nivel / (double) (com.modamod.render.DistribucionPatron.NIVELES - 1);
            this.ultimoNivelEnviado = nivel;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int porcentaje = Math.round(com.modamod.render.DistribucionPatron.distanciaDeNivel(nivel()) * 100);
            this.setMessage(Text.translatable(horizontal
                    ? "modamod.tinturas.distancia_h" : "modamod.tinturas.distancia_v", porcentaje));
        }

        @Override
        protected void applyValue() {
            int nivel = nivel();
            if (nivel == ultimoNivelEnviado) return;
            ultimoNivelEnviado = nivel;
            clickBoton((horizontal ? TinturasBlockEntity.BTN_DISTANCIA_H_BASE : TinturasBlockEntity.BTN_DISTANCIA_V_BASE) + nivel);
        }
    }

    /** Un slider C/M/Y/K — cuantiza a NIVELES_MEZCLA pasos y solo manda el click cuando ese nivel cambia. */
    private class CanalSlider extends SliderWidget {
        private final int canal;
        private int ultimoNivelEnviado;

        CanalSlider(int x, int y, int width, int height, int canal, int nivelInicial) {
            super(x, y, width, height, Text.empty(), nivelInicial / (double) (TinturasBlockEntity.NIVELES_MEZCLA - 1));
            this.canal = canal;
            this.ultimoNivelEnviado = nivelInicial;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int nivel = Math.round((float) (this.value * (TinturasBlockEntity.NIVELES_MEZCLA - 1)));
            int porcentaje = Math.round(nivel * 100f / (TinturasBlockEntity.NIVELES_MEZCLA - 1));
            this.setMessage(Text.translatable("modamod.tinturas.canal." + NOMBRE_CANAL[canal]).append(Text.literal(": " + porcentaje + "%")));
        }

        @Override
        protected void applyValue() {
            int nivel = Math.round((float) (this.value * (TinturasBlockEntity.NIVELES_MEZCLA - 1)));
            if (nivel == ultimoNivelEnviado) return;
            ultimoNivelEnviado = nivel;
            clickBoton(handler.be.botonMezcla(canal, nivel));
        }

        /**
         * Slider común: el indicador de tinta se mudó abajo, junto a la cabecera (2026-10-12), así que acá solo va el
         * mango de madera y el nombre del canal con su porcentaje.
         */
        @Override
        public void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            int x0 = getX(), y0 = getY(), w = getWidth(), h = getHeight();
            c.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF2A180C);
            c.fill(x0, y0, x0 + w, y0 + h, 0xFF8A7556);
            if (canal < 4) c.fill(x0, y0 + h - 2, x0 + w, y0 + h, COLOR_TANQUE[canal]);   // el color de la tinta, en el borde de abajo
            int mx = x0 + (int) (this.value * (w - 8));
            EstiloPergamino.fondoBoton(c, mx, y0, 8, h, isHovered() || isFocused(), this.active);
            var fuente = MinecraftClient.getInstance().textRenderer;
            int ty = y0 + (h - 8) / 2;
            Text m = getMessage();
            c.drawText(fuente, m, x0 + 5, ty + 1, 0xFF2A180C, false);
            c.drawText(fuente, m, x0 + 4, ty, EstiloPergamino.TEXTO_CLARO, false);
        }

        /** Refleja el nivel del cuadradito recién seleccionado SIN mandar un click. */
        void sincronizarDesdeServidor(int nivel) {
            this.value = nivel / (double) (TinturasBlockEntity.NIVELES_MEZCLA - 1);
            this.ultimoNivelEnviado = nivel;
            updateMessage();
        }
    }

    /**
     * Una de las 3 muestras de color de la capa (Fase 2, 2026-09-28): rellena con su color si está prendida (tachada si
     * no), marco dorado si es la que editan los sliders, y una "C" si es la del contorno.
     */
    private static class BotonMuestra extends ButtonWidget {
        int color;
        boolean prendida, editando, contorno;

        BotonMuestra(int indice, int x, int y, int w, int h, PressAction accion) {
            super(x, y, w, h, Text.literal(String.valueOf(indice + 1)), accion, DEFAULT_NARRATION_SUPPLIER);
        }

        @Override
        protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            if (prendida) {
                dibujarMuestra(c, x + 1, y + 1, x + w - 1, y + h - 1, color);
            } else {
                c.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFB9A27A);
                for (int i = 0; i < w - 2; i++) c.fill(x + 1 + i, y + 1 + i * (h - 2) / (w - 2), x + 2 + i, y + 2 + i * (h - 2) / (w - 2), 0xFF6B4E2A);
            }
            c.drawBorder(x, y, w, h, editando ? 0xFFFFD24C : (isHovered() ? 0xFFC79A4B : 0xFF2A180C));
            if (editando) c.drawBorder(x + 1, y + 1, w - 2, h - 2, 0xFFFFD24C);
            var fuente = MinecraftClient.getInstance().textRenderer;
            c.drawTextWithShadow(fuente, getMessage().getString() + (contorno ? " C" : ""), x + 4, y + (h - 8) / 2, 0xFFFFFFFF);
        }
    }

    /** Chincheta de un cuadradito: hueca = no fijado, llena = fijado (entra al diseño que se aplica). */
    /** Chincheta suelta (sin botón) con el color de destaque de la máquina; cuadro 2 = fijada, 0 = suelta. */
    private static Identifier[] chincheta(String color) {
        return new Identifier[] {
                Identifier.of("modamod", "textures/gui/container/chincheta_" + color + "_0.png"),
                Identifier.of("modamod", "textures/gui/container/chincheta_" + color + "_1.png"),
                Identifier.of("modamod", "textures/gui/container/chincheta_" + color + "_2.png")};
    }

    /** Chinchetas del color de cada máquina (2026-10-14, "las chinchetas de su propio color"), sin tinte. */
    static final Identifier[] CHINCHETA_LILA = chincheta("lila"), CHINCHETA_ORO = chincheta("oro"), CHINCHETA_GRANATE = chincheta("granate");

    static void dibujarChinchetaLila(DrawContext c, int x, int y, boolean fijado) {
        dibujarChincheta(c, x, y, fijado, CHINCHETA_LILA);
    }

    static void dibujarChincheta(DrawContext c, int x, int y, boolean fijado, Identifier[] texturas) {
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 400);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        c.drawTexture(texturas[fijado ? 2 : 0], x, y, 0, 0, 12, 12, 12, 12);
        c.getMatrices().pop();
    }

    static void dibujarChincheta(DrawContext c, int x, int y, boolean fijado, int tinte) {
        c.getMatrices().push();
        c.getMatrices().translate(0, 0, 400);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        if (fijado && tinte != 0) c.setShaderColor(((tinte >> 16) & 255) / 255f, ((tinte >> 8) & 255) / 255f, (tinte & 255) / 255f, 1f);
        c.drawTexture(TEXTURE_CHINCHETA[fijado ? 2 : 0], x, y, 0, 0, 12, 12, 12, 12);
        if (fijado && tinte != 0) c.setShaderColor(1f, 1f, 1f, 1f);
        c.getMatrices().pop();
    }

    static class BotonChincheta extends ButtonWidget {
        private boolean fijado;
        private long cambioMs = -1000;
        /** Color de destaque de la máquina (0 = el de la textura); tiñe la chincheta fijada. */
        int tinte = 0;
        /** Textura propia de la máquina (null = la verdín de Tintes, con el tinte de arriba). */
        Identifier[] texturas;

        BotonChincheta(PressAction accion) {
            super(0, 0, 16, 16, Text.empty(), accion, DEFAULT_NARRATION_SUPPLIER);
        }

        void actualizar(boolean nuevo) {
            if (nuevo != fijado) cambioMs = net.minecraft.util.Util.getMeasuringTimeMs();
            fijado = nuevo;
        }

        @Override
        protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            boolean animando = net.minecraft.util.Util.getMeasuringTimeMs() - cambioMs < 160;
            int cuadro = animando ? 1 : fijado ? 2 : 0;
            int x = getX(), y = getY();
            c.getMatrices().push();
            c.getMatrices().translate(0, 0, 400);
            if (isHovered() && active) c.fill(x + 2, y + 2, x + 14, y + 14, 0x33FFFFFF);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            boolean teñir = texturas == null && tinte != 0 && cuadro != 0;
            if (teñir) c.setShaderColor(((tinte >> 16) & 255) / 255f, ((tinte >> 8) & 255) / 255f, (tinte & 255) / 255f, 1f);
            c.drawTexture((texturas != null ? texturas : TEXTURE_CHINCHETA)[cuadro], x + 2, y + 2, 0, 0, 12, 12, 12, 12);
            if (teñir) c.setShaderColor(1f, 1f, 1f, 1f);
            c.getMatrices().pop();
        }
    }
}
