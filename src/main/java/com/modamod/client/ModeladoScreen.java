package com.modamod.client;

import com.modamod.modelado.ComboCorte;
import com.modamod.modelado.ModeladoBlockEntity;
import com.modamod.modelado.ModeladoScreenHandler;
import com.modamod.modelado.ZonasModelado;
import com.modamod.render.GarmentFeatureRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
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
 * Pantalla de la Mesa de Modelado, versión "visor enfocado" (2026-10-12, "el visor sea más grande y cambie de enfoque
 * en vez de tener un esquema de pines con casilleros por todos lados"). Ventana de 560x408 con el fondo dibujado por
 * código (como {@link TelarScreen}; los {@code esquema_*.png} y {@code modelado.png} ya no se usan):
 * <ul>
 *   <li>A. Visor (8,24, 304x256): el jugador con la prenda en construcción; la cámara se acerca al enfoque de la zona
 *   activa ({@link ZonasModelado.Enfoque}) con una transición suave.</li>
 *   <li>D. Tira de zonas (8,284, 304x18): ‹ ›, el nombre de la zona y los números para saltar.</li>
 *   <li>C. Columna derecha (318,24, 234x278): botón de prenda y Simetría arriba, los casilleros de la zona activa
 *   (a lo sumo {@link ZonasModelado#FILAS_MAX}, de 22 px) y, hasta la tanda 2, el almacén y los moldes por prenda.</li>
 *   <li>Diseños a la izquierda del inventario; cabecera Entrada → Modelar → barra → Salida a la derecha.</li>
 * </ul>
 * Los casilleros son los mismos pines de siempre ({@code ModeladoBlockEntity#ROLES}); la zona activa es un campo del
 * handler y solo cambia cuáles están {@code isEnabled}. Apagada = editable; prendida la GUI ni siquiera se abre.
 * Sin botón de Encender: la máquina se prende al cerrar la pantalla (ver {@code ModeladoScreenHandler#onClosed}).
 */
public class ModeladoScreen extends HandledScreen<ModeladoScreenHandler> {

    /** Cuadros de la chincheta: 0 sin fijar (aguja a la vista), 1 a mitad de clavarse, 2 fijada (sin aguja). */
    private static final Identifier[] TEXTURE_CHINCHETA = {
            Identifier.of("modamod", "textures/gui/container/chincheta_0.png"),
            Identifier.of("modamod", "textures/gui/container/chincheta_1.png"),
            Identifier.of("modamod", "textures/gui/container/chincheta_2.png"),
    };

    // Sectores (coordenadas locales de la ventana).
    private static final int VISOR_X1 = 8, VISOR_Y1 = 24, VISOR_X2 = 312, VISOR_Y2 = 302;
    private static final int TIRA_X = 318, TIRA_Y = 43, TIRA_ANCHO = 234, TIRA_ALTO = 18;
    private static final int COL_X = ModeladoScreenHandler.X_COL, COL_ANCHO = ModeladoScreenHandler.ANCHO_COL;
    private static final int MAX_ZONAS = 6;

    private ButtonWidget btnCategoria, btnSimetria, btnModelar, btnVista, btnGuardarDiseno, btnUltimo;
    private ButtonWidget btnZonaPrev, btnZonaNext, btnPagPrev, btnPagNext;
    private final ButtonWidget[] btnZona = new ButtonWidget[MAX_ZONAS];
    private net.minecraft.client.gui.widget.TextFieldWidget txtNombreDiseno;
    /** El diseño de la lista elegido para exportar (el último que se cargó con click izquierdo; 2026-10-25). */
    private int disenoElegido = -1;
    private final BotonChincheta[] btnPines = new BotonChincheta[ModeladoBlockEntity.PINES_POR_CATEGORIA];
    /** Pestaña de la bolsa: 0 = moldes del casillero elegido, 1 = bolsa de diseños (2026-10-12). */
    private int pestana = 0;
    private ButtonWidget btnTabMoldes, btnTabDisenos, btnFiltro;
    /** Filtro de la bolsa de diseños: -2 = la prenda actual, -1 = todas, 0.. = esa categoría (2026-10-12). */
    private int filtroDisenos = -2;
    private int ratonX, ratonY;
    private static final int DIS_POR_HOJA = 3, DIS_ALTO = 16, DIS_ANCHO = 216;

    // Desplegable de moldes (2026-10-12, bolsas): casillero elegido (índice de pin de la categoría, -1 ninguno) y hoja.
    private int pinSel = -1;
    private int pagina = 0;
    private static final int COLS_BOLSA = 8, FILAS_BOLSA = 3, POR_HOJA = COLS_BOLSA * FILAS_BOLSA, CELDA = 24;
    private static final int X_GRILLA = ModeladoScreenHandler.X_COL + 6, Y_GRILLA = 176;

    // Cámara del visor: lo actual se acerca a lo pedido por la zona (transición suave).
    private float focoActual = ZonasModelado.Enfoque.ENTERO.focoY, tamActual = ZonasModelado.Enfoque.ENTERO.tamano;
    private long ultimoCuadroMs = -1;
    private ModeladoBlockEntity.Categoria ultimaCategoria, ultimaCategoriaBolsa;

    /** En GRADOS, no radianes — ver el javadoc de {@link PreviewJugador}. */
    private float anguloVista = 0f;
    private boolean arrastrandoPreview = false, inclinandoPreview = false;
    /** Inclinación del visor en grados (arrastre vertical con click derecho): para ver las tapas de arriba y de abajo. */
    private float inclinacion = 0f;
    private float zoomVista = 1f;
    private static final float ZOOM_MIN = 0.5f, ZOOM_MAX = 2.5f;

    public ModeladoScreen(ModeladoScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = ModeladoScreenHandler.ANCHO;
        this.backgroundHeight = ModeladoScreenHandler.ALTO;
        this.titleX = 8;
        this.titleY = 9;
        this.playerInventoryTitleX = ModeladoScreenHandler.X_INV;
        this.playerInventoryTitleY = ModeladoScreenHandler.Y_INV - 10;
    }

    @Override
    protected void init() {
        EstiloPergamino.usarTema(EstiloPergamino.Tema.COBRE);
        super.init();

        // Categoría más corta con flechas a los costados (2026-10-12, "achicarlo en largo para agregarle flechitas").
        this.addDrawableChild(new EstiloPergamino.BotonPergamino(this.x + COL_X, this.y + 24, 18, 16, Text.literal("‹"),
                b -> clickBoton(ModeladoBlockEntity.BTN_CATEGORIA_ATRAS)));
        this.addDrawableChild(new EstiloPergamino.BotonPergamino(this.x + COL_X + 128, this.y + 24, 18, 16, Text.literal("›"),
                b -> clickBoton(ModeladoBlockEntity.BTN_CATEGORIA)));
        btnCategoria = new EstiloPergamino.BotonPergamino(this.x + COL_X + 20, this.y + 24, 106, 16, Text.literal(""), b -> clickBoton(ModeladoBlockEntity.BTN_CATEGORIA));
        btnCategoria.setTooltip(Tooltip.of(Text.translatable("modamod.modelado.tooltip.categoria")));
        this.addDrawableChild(btnCategoria);

        btnSimetria = new EstiloPergamino.BotonPergamino(this.x + COL_X + 150, this.y + 24, COL_ANCHO - 150, 16, Text.literal(""), b -> clickBoton(ModeladoBlockEntity.BTN_SIMETRIA));
        btnSimetria.setTooltip(Tooltip.of(Text.translatable("modamod.modelado.tooltip.simetria")));
        this.addDrawableChild(btnSimetria);

        // Línea de producción (2026-10-08): apagada de fábrica; arriba a la derecha (2026-10-12, plan de GUIs).
        this.addDrawableChild(new EstiloPergamino.BotonLinea(this.x + 452, this.y + 3, 96, 16,
                () -> this.handler.be.linea(), b -> clickBoton(ModeladoBlockEntity.BTN_LINEA)));

        // Tira de zonas: ‹ ›, y un número por zona.
        btnZonaPrev = new EstiloPergamino.BotonPergamino(this.x + TIRA_X, this.y + TIRA_Y, 18, TIRA_ALTO, Text.literal("‹"), b -> cambiarZona(-1));
        btnZonaNext = new EstiloPergamino.BotonPergamino(this.x + TIRA_X + TIRA_ANCHO - 18, this.y + TIRA_Y, 18, TIRA_ALTO, Text.literal("›"), b -> cambiarZona(1));
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

        // Páginas del desplegable de moldes.
        btnPagPrev = new EstiloPergamino.BotonPergamino(this.x + X_GRILLA, this.y + Y_GRILLA + FILAS_BOLSA * CELDA + 3, 18, 14, Text.literal("‹"), b -> pagina = Math.max(0, pagina - 1));
        btnPagNext = new EstiloPergamino.BotonPergamino(this.x + X_GRILLA + 22, this.y + Y_GRILLA + FILAS_BOLSA * CELDA + 3, 18, 14, Text.literal("›"), b -> pagina++);
        this.addDrawableChild(btnPagPrev);
        this.addDrawableChild(btnPagNext);

        // Modelar, entre la Entrada y la barra (2026-09-28, "a modeladora le agreguemos el boton modelar").
        btnModelar = new EstiloPergamino.BotonPergamino(this.x + ModeladoScreenHandler.X_ENTRADA + 36, this.y + ModeladoScreenHandler.Y_IO, 60, 16,
                Text.translatable("modamod.modelado.boton.modelar"), b -> clickBoton(ModeladoBlockEntity.BTN_MODELAR));
        btnModelar.setTooltip(Tooltip.of(Text.translatable("modamod.modelado.tooltip.modelar")));
        this.addDrawableChild(btnModelar);

        // Diseños (tanda 3, 2026-10-12): "★ Último" (lo que se usó en la última prenda) y 3 con nombre; click carga, derecho borra.
        btnUltimo = new EstiloPergamino.BotonPergamino(this.x + 8, this.y + 312, 105, 16, Text.translatable("modamod.modelado.ultimo"),
                b -> clickBoton(ModeladoBlockEntity.BTN_CARGAR_ULTIMO));
        btnUltimo.setTooltip(Tooltip.of(Text.translatable("modamod.modelado.tooltip.ultimo")));
        this.addDrawableChild(btnUltimo);
        // Pestañas de la bolsa: moldes del casillero / diseños guardados como ítems.
        btnTabMoldes = pestanaBoton(150, 40, "modamod.modelado.pestana.moldes", 0);
        btnTabDisenos = pestanaBoton(192, 42, "modamod.modelado.pestana.disenos", 1);
        // Filtro por categoría de la bolsa de diseños (2026-10-12, "boton de categoria para los diseños?"): cicla actual → todas → cada una.
        btnFiltro = new EstiloPergamino.BotonPergamino(this.x + X_GRILLA + 110, this.y + Y_GRILLA + FILAS_BOLSA * CELDA + 3, 106, 14, Text.literal(""), b -> {
            int n = ModeladoBlockEntity.Categoria.values().length;
            filtroDisenos = filtroDisenos == n - 1 ? -2 : filtroDisenos + 1;
            pagina = 0;
        });
        btnFiltro.setTooltip(Tooltip.of(Text.translatable("modamod.modelado.disenos.filtro")));
        this.addDrawableChild(btnFiltro);

        // Una chincheta por casillero: fija el corte (el molde vuelve al almacén). Se dibujan a mano en render().
        for (int i = 0; i < btnPines.length; i++) {
            btnPines[i] = new BotonChincheta(0, 0, b -> { });
            this.addSelectableChild(btnPines[i]);
        }

        // El jugador se dibuja con profundidad (z hasta ~250): el botón va a z 500 para quedar siempre encima de la prenda
        // (2026-10-12, "al boton de vista lo sigue tapando la pollera").
        btnVista = new EstiloPergamino.BotonPergamino(this.x + VISOR_X1 + 4, this.y + VISOR_Y2 - 20, 90, 16, Text.literal(""),
                b -> anguloVista = Math.floorMod(Math.round(anguloVista) + 90, 360)) {
            @Override
            protected void renderWidget(DrawContext c, int mx, int my, float d) {
                c.getMatrices().push();
                c.getMatrices().translate(0, 0, 500);
                super.renderWidget(c, mx, my, d);
                c.getMatrices().pop();
            }
        };
        btnVista.setTooltip(Tooltip.of(Text.translatable("modamod.preview.tooltip.vista")));
        this.addDrawableChild(btnVista);

        txtNombreDiseno = new net.minecraft.client.gui.widget.TextFieldWidget(
                this.textRenderer, this.x + 8, this.y + 336, 83, 14, Text.translatable("modamod.modelado.nombre_diseno"));
        txtNombreDiseno.setMaxLength(24);
        txtNombreDiseno.setPlaceholder(Text.translatable("modamod.modelado.nombre_diseno"));
        this.addDrawableChild(txtNombreDiseno);

        btnGuardarDiseno = new EstiloPergamino.BotonPergamino(this.x + 93, this.y + 336, 20, 14, Text.literal("+"), b -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.modamod.modelado.GuardarDisenoPayload(this.handler.be.getPos(), txtNombreDiseno.getText()));
            txtNombreDiseno.setText("");
        });
        btnGuardarDiseno.setTooltip(Tooltip.of(Text.translatable("modamod.modelado.tooltip.mas")));
        this.addDrawableChild(btnGuardarDiseno);
        // Borrar lo fijado (2026-10-14, "un boton de delete... y un delete all"): abajo a la izquierda, bajo el nombre del diseño.
        ButtonWidget btnBorrar = new EstiloPergamino.BotonPergamino(this.x + 8, this.y + 356, 36, 14, Text.translatable("modamod.modelado.borrar"),
                b -> clickBoton(ModeladoBlockEntity.BTN_BORRAR));
        btnBorrar.setTooltip(Tooltip.of(Text.translatable("modamod.modelado.tooltip.borrar")));
        this.addDrawableChild(btnBorrar);
        ButtonWidget btnBorrarTodas = new EstiloPergamino.BotonPergamino(this.x + 46, this.y + 356, 67, 14, Text.translatable("modamod.modelado.borrar_todo"),
                b -> clickBoton(ModeladoBlockEntity.BTN_BORRAR_TODAS));
        btnBorrarTodas.setTooltip(Tooltip.of(Text.translatable("modamod.modelado.tooltip.borrar_todo")));
        this.addDrawableChild(btnBorrarTodas);
        // Exportar el diseño elegido de la lista con el diseño en blanco del casillero de al lado (2026-10-25, "un botoncito de
        // exportar para exportar un diseñito que lleve el nombre del diseño guardado seleccionado").
        ButtonWidget btnExportar = new EstiloPergamino.BotonPergamino(this.x + 28, this.y + 377, 85, 14,
                Text.translatable("modamod.disenos.exportar"), b -> { mandarMolde(disenoElegido, net.minecraft.registry.Registries.ITEM.getId(com.modamod.modelado.ModeladoMod.DISENO_CORTE), com.modamod.modelado.ElegirMoldePayload.EXPORTAR_DISENO); });
        btnExportar.setTooltip(Tooltip.of(Text.translatable("modamod.disenos.tooltip.exportar")));
        this.addDrawableChild(btnExportar);

        refrescar();
    }

    private ButtonWidget pestanaBoton(int dx, int ancho, String clave, int valor) {
        ButtonWidget b = new ButtonWidget(this.x + COL_X + dx, this.y + 156, ancho, 14, Text.translatable(clave), bt -> { pestana = valor; pagina = 0; }, d -> Text.empty()) {
            @Override
            protected void renderWidget(DrawContext c, int mx, int my, float d) {
                EstiloPergamino.fondoBoton(c, getX(), getY(), getWidth(), getHeight(), isHovered() || pestana == valor, active);
                c.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2, getY() + 3,
                        pestana == valor ? 0xFFFFE0A8 : EstiloPergamino.TEXTO_CLARO);
            }
        };
        this.addDrawableChild(b);
        return b;
    }

    /** Click al SERVIDOR nada más (ver historial: el doble camino cliente+servidor tenía una carrera real). */
    void clickBoton(int id) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.interactionManager == null) return;
        client.interactionManager.clickButton(this.handler.syncId, id);
    }

    // ── zonas ──

    private void irAZona(int zona) {
        int n = ZonasModelado.cantidad(this.handler.be.categoria());
        this.handler.zonaActiva = Math.floorMod(zona, n);
    }

    private void cambiarZona(int delta) { irAZona(this.handler.zonaActiva + delta); }

    /** ¿Algún casillero de la zona tiene molde puesto o corte fijado? (puntito verde en la tira). */
    private boolean zonaConAlgo(int zona) {
        ModeladoBlockEntity be = this.handler.be;
        ModeladoBlockEntity.Categoria cat = be.categoria();
        for (int f = 0; f < ZonasModelado.FILAS_MAX; f++) {
            int i = ZonasModelado.pinDe(cat, zona, f);
            if (i < 0) continue;
            int p = cat.ordinal() * ModeladoBlockEntity.PINES_POR_CATEGORIA + i;
            if (be.pinFijado(p) || !be.getStack(ModeladoBlockEntity.pinSlot(p)).isEmpty()) return true;
        }
        return false;
    }

    // ── desplegable de moldes de la bolsa ──

    /** Moldes de la bolsa que acepta el casillero elegido (la "bolsa por tipo"), en el orden del registro. */
    private List<net.minecraft.util.Identifier> candidatos() {
        List<net.minecraft.util.Identifier> r = new ArrayList<>();
        if (pinSel < 0) return r;
        ModeladoBlockEntity.Categoria cat = this.handler.be.categoria();
        for (var id : this.handler.be.bolsa().keySet()) {
            var item = net.minecraft.registry.Registries.ITEM.get(id);
            if (ModeladoBlockEntity.pinAcepta(cat, pinSel, new ItemStack(item))) r.add(id);
        }
        r.sort(java.util.Comparator.comparingInt(id -> net.minecraft.registry.Registries.ITEM.getRawId(net.minecraft.registry.Registries.ITEM.get(id))));
        return r;
    }

    /** Molde con el que está fijado el casillero {@code i} (el ítem que produjo su corte), o null. */
    private net.minecraft.util.Identifier moldeFijado(int i) {
        ModeladoBlockEntity be = this.handler.be;
        int p = be.categoria().ordinal() * ModeladoBlockEntity.PINES_POR_CATEGORIA + i;
        ComboCorte combo = be.pinFijado(p) ? be.pinCombo(p) : null;
        return combo == null ? null : combo.iconoOrigen().orElse(null);
    }

    /** Celda de la grilla (índice absoluto en la lista de candidatos) bajo el mouse, o -1. */
    private int celdaBajo(double mx, double my, int cantidad) {
        double lx = mx - this.x - X_GRILLA, ly = my - this.y - Y_GRILLA;
        if (lx < 0 || ly < 0 || lx >= COLS_BOLSA * CELDA || ly >= FILAS_BOLSA * CELDA) return -1;
        int idx = pagina * POR_HOJA + (int) (ly / CELDA) * COLS_BOLSA + (int) (lx / CELDA);
        return idx < cantidad ? idx : -1;
    }

    /** Índices (en la bolsa) de los diseños que pasan el filtro de categoría. */
    private List<Integer> disenosFiltrados() {
        List<Integer> r = new ArrayList<>();
        List<ItemStack> ds = this.handler.be.disenosItem();
        String cat = filtroDisenos == -1 ? null
                : (filtroDisenos == -2 ? this.handler.be.categoria() : ModeladoBlockEntity.Categoria.values()[filtroDisenos]).name();
        for (int i = 0; i < ds.size(); i++) {
            var dc = ds.get(i).get(com.modamod.item.ModamodComponents.DISENO_CORTE);
            if (cat == null || (dc != null && dc.categoria().equals(cat))) r.add(i);
        }
        return r;
    }

    private Text textoFiltro() {
        if (filtroDisenos == -1) return Text.translatable("modamod.modelado.disenos.todas");
        ModeladoBlockEntity.Categoria c = filtroDisenos == -2 ? this.handler.be.categoria() : ModeladoBlockEntity.Categoria.values()[filtroDisenos];
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

    /** Fila de la zona activa (0..) bajo el mouse, o -1. Excluye la columna de la chincheta. */
    private int filaBajo(double mx, double my) {
        double lx = mx - this.x - COL_X, ly = my - this.y - ModeladoScreenHandler.Y_FILAS;
        if (lx < 0 || lx >= COL_ANCHO - 26 || ly < 0 || ly >= ZonasModelado.FILAS_MAX * ModeladoScreenHandler.ALTO_FILA) return -1;
        return (int) (ly / ModeladoScreenHandler.ALTO_FILA);
    }

    private void mandarMolde(int pin, net.minecraft.util.Identifier id, int modo) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.modamod.modelado.ElegirMoldePayload(this.handler.be.getPos(), pin, id, modo));
    }

    // ── visor: arrastre para girar, rueda para el zoom ──

    private boolean dentroDePreview(double mouseX, double mouseY) {
        return mouseX >= this.x + VISOR_X1 && mouseX < this.x + VISOR_X2 && mouseY >= this.y + VISOR_Y1 && mouseY < this.y + VISOR_Y2;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // OJO: HandledScreen#mouseClicked devuelve true casi siempre (también al clickear en vacío), así que lo propio
        // se resuelve ANTES de llamarlo (2026-10-12, "nunca me aparecieron los moldes"); los botones quedan fuera de estas áreas.
        ModeladoBlockEntity be = this.handler.be;
        // Click en una fila de la zona: elige ese casillero (abre su lista de moldes).
        if (button == 0) {
            int f = filaBajo(mouseX, mouseY);
            if (f >= 0) {
                int i = ZonasModelado.pinDe(be.categoria(), this.handler.zonaActiva, f);
                if (i >= 0) { pinSel = pinSel == i ? -1 : i; pagina = 0; pestana = 0; return true; }
            }
        }
        // Bolsa de diseños: izquierdo carga el diseño, derecho lo saca como ítem.
        if (pestana == 1 && !be.encendida() && (button == 0 || button == 1)) {
            int d = filaDiseno(mouseX, mouseY);
            if (d == -2 && button == 0) { clickBoton(ModeladoBlockEntity.BTN_CARGAR_ULTIMO); return true; }
            if (d >= 0) {
                if (button == 0) disenoElegido = d;
                mandarMolde(d, net.minecraft.registry.Registries.ITEM.getId(com.modamod.modelado.ModeladoMod.DISENO_CORTE),
                        button == 0 ? com.modamod.modelado.ElegirMoldePayload.CARGAR_DISENO : com.modamod.modelado.ElegirMoldePayload.SACAR_DISENO);
                return true;
            }
        }
        // Click en un molde del desplegable: izquierdo lo elige para el casillero, derecho saca uno de la bolsa.
        if (pestana == 0 && pinSel >= 0 && !be.encendida()) {
            List<net.minecraft.util.Identifier> lista = candidatos();
            int c = celdaBajo(mouseX, mouseY, lista.size());
            if (c >= 0 && (button == 0 || button == 1)) {
                mandarMolde(pinSel, lista.get(c), button == 0 ? com.modamod.modelado.ElegirMoldePayload.ELEGIR : com.modamod.modelado.ElegirMoldePayload.SACAR);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dentroDePreview(mouseX, mouseY)) {
            zoomVista = MathHelper.clamp(zoomVista + (float) verticalAmount * 0.1f, ZOOM_MIN, ZOOM_MAX);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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

    /** Sin esto, escribir una "e" en el nombre del diseño cierra la pantalla (mismo mecanismo que el yunque). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return (!txtNombreDiseno.keyPressed(keyCode, scanCode, modifiers) && !txtNombreDiseno.isActive())
                ? super.keyPressed(keyCode, scanCode, modifiers) : true;
    }

    // ── estado de los controles ──

    private void refrescar() {
        ModeladoBlockEntity be = this.handler.be;
        boolean prendida = be.encendida();
        ModeladoBlockEntity.Categoria categoria = be.categoria();
        int zonas = ZonasModelado.cantidad(categoria);

        if (categoria != ultimaCategoria) {
            ultimaCategoria = categoria;
            this.handler.zonaActiva = 0;
        }
        if (this.handler.zonaActiva >= zonas) this.handler.zonaActiva = 0;

        // Solo el nombre: el botón es más corto ahora que tiene flechas.
        btnCategoria.setMessage(Text.translatable("modamod.modelado.categoria." + categoria.name().toLowerCase(Locale.ROOT)));
        btnCategoria.active = !prendida;
        btnSimetria.setMessage(Text.translatable("modamod.modelado.simetria",
                Text.translatable(be.remeraSimetria() ? "modamod.si" : "modamod.no")));
        btnSimetria.active = !prendida;
        btnVista.setMessage(Text.translatable("modamod.preview.vista", Text.translatable(PreviewJugador.nombreVista(anguloVista))));
        btnModelar.active = be.puedeModelar();

        btnZonaPrev.active = btnZonaNext.active = zonas > 1;
        if (pinSel >= 0 && ZonasModelado.zonaDe(categoria, pinSel) != this.handler.zonaActiva) pinSel = -1;
        if (categoria != ultimaCategoriaBolsa) { ultimaCategoriaBolsa = categoria; pinSel = -1; pagina = 0; }
        int hojas = pestana == 1
                ? Math.max(1, (disenosFiltrados().size() + DIS_POR_HOJA - 1) / DIS_POR_HOJA)
                : Math.max(1, (candidatos().size() + POR_HOJA - 1) / POR_HOJA);
        pagina = MathHelper.clamp(pagina, 0, hojas - 1);
        btnPagPrev.visible = btnPagNext.visible = (pestana == 1 || pinSel >= 0) && hojas > 1;
        btnFiltro.visible = pestana == 1;
        btnFiltro.setMessage(textoFiltro());
        btnPagPrev.active = pagina > 0;
        btnPagNext.active = pagina < hojas - 1;
        for (int i = 0; i < MAX_ZONAS; i++) {
            btnZona[i].visible = i < zonas;
            btnZona[i].active = i < zonas;
            btnZona[i].setPosition(this.x + TIRA_X + TIRA_ANCHO - 20 - (zonas - i) * 18 + 2, this.y + TIRA_Y);
        }

        btnGuardarDiseno.active = !prendida && !be.fijadas().isEmpty();
        txtNombreDiseno.active = !prendida;
        btnUltimo.active = !prendida && be.hayUltimo();

        int cat = categoria.ordinal();
        for (int i = 0; i < btnPines.length; i++) {
            int p = cat * ModeladoBlockEntity.PINES_POR_CATEGORIA + i;
            int fila = ZonasModelado.filaDe(categoria, i);
            boolean usable = fila >= 0 && ZonasModelado.zonaDe(categoria, i) == this.handler.zonaActiva;
            boolean fijado = be.pinFijado(p);
            boolean conMolde = !be.getStack(ModeladoBlockEntity.pinSlot(p)).isEmpty();
            btnPines[i].setPosition(this.x + COL_X + COL_ANCHO - 22, this.y + ModeladoScreenHandler.yFila(Math.max(fila, 0)));
            btnPines[i].pin = p;
            btnPines[i].visible = usable;
            btnPines[i].active = !prendida && (conMolde || fijado);
            btnPines[i].actualizar(fijado);
            ComboCorte combo = be.pinCombo(p);
            btnPines[i].setTooltip(Tooltip.of(fijado && combo != null
                    ? Text.translatable("modamod.modelado.tooltip.chincheta_quitar", descripcion(combo))
                    : Text.translatable("modamod.modelado.tooltip.chincheta")));
        }
    }

    /** El ítem que produjo esta fijada (ver {@code ComboCorte#iconoOrigen}). */
    private static ItemStack iconoDe(ComboCorte combo) {
        return combo.iconoOrigen()
                .map(net.minecraft.registry.Registries.ITEM::get)
                .filter(item -> item != null)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }

    private static Text descripcion(ComboCorte combo) {
        StringBuilder sb = new StringBuilder();
        combo.remeraLargo().ifPresent(v -> sb.append("Remera largo: ").append(v.clave).append(' '));
        combo.remeraManga().ifPresent(v -> sb.append("Remera manga: ").append(v.clave).append(' '));
        combo.remeraCuello().ifPresent(v -> sb.append("Remera cuello: ").append(v.clave).append(' '));
        combo.pantalonTiro().ifPresent(v -> sb.append("Tiro: ").append(v.clave).append(' '));
        combo.pantalonLargoSuperior().ifPresent(v -> sb.append("Pantalón sup: ").append(v.clave).append(' '));
        combo.pantalonLargoInferior().ifPresent(v -> sb.append("Pantalón inf: ").append(v.clave).append(' '));
        combo.mediasLargoSuperior().ifPresent(v -> sb.append("Medias sup: ").append(v.clave).append(' '));
        combo.mediasLargoInferior().ifPresent(v -> sb.append("Medias inf: ").append(v.clave).append(' '));
        combo.calientabrazosCoberturaSuperior().ifPresent(v -> sb.append("Calient. sup: ").append(v.clave).append(' '));
        combo.calientabrazosCoberturaInferior().ifPresent(v -> sb.append("Calient. inf: ").append(v.clave).append(' '));
        combo.calce().ifPresent(v -> sb.append("Calce: ").append(v.clave).append(' '));
        combo.pollera().ifPresent(p -> {
            p.largo().ifPresent(v -> sb.append("Pollera largo: ").append(v.clave).append(' '));
            p.forma().ifPresent(v -> sb.append("Pollera forma: ").append(v.clave).append(' '));
            p.capaLargo().ifPresent(v -> sb.append("Capa largo: ").append(v.asString()).append(' '));
            p.capaRuedo().ifPresent(v -> sb.append("Capa ruedo: ").append(v.asString()).append(' '));
            p.capaCapucha().ifPresent(v -> sb.append(v ? "Con capucha " : "Sin capucha "));
            p.capaCuello().ifPresent(v -> sb.append(v ? "Cuello alto " : "Sin cuello "));
        });
        if (combo.lado() != com.modamod.region.Lado.AMBAS) sb.append('(').append(combo.lado().clave()).append(')');
        return Text.literal(sb.toString().trim());
    }

    // ── dibujo ──

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
        // Separadores de puntos: sobre el sector de abajo y entre el visor y la columna.
        for (int px = x0 + 8; px < x0 + w - 8; px += 4) c.fill(px, y0 + 306, px + 2, y0 + 307, tema.oscuro);
        for (int py = y0 + 24; py < y0 + 302; py += 4) c.fill(x0 + COL_X - 3, py, x0 + COL_X - 2, py + 2, tema.oscuro);
        for (int py = y0 + 312; py < y0 + 400; py += 4) {
            c.fill(x0 + 116, py, x0 + 117, py + 2, tema.oscuro);
            c.fill(x0 + 284, py, x0 + 285, py + 2, tema.oscuro);
        }

        // A. Visor (el jugador se dibuja encima en render()).
        c.fill(x0 + VISOR_X1 - 1, y0 + VISOR_Y1 - 1, x0 + VISOR_X2 + 1, y0 + VISOR_Y2 + 1, 0xFF2A180C);
        c.fill(x0 + VISOR_X1, y0 + VISOR_Y1, x0 + VISOR_X2, y0 + VISOR_Y2, 0xFF4A3828);
        // El jugador se dibuja en el fondo (2026-10-12, "el boton para girar tapado por la prenda"): los botones van encima.
        dibujarPreview(c, mouseX, mouseY);

        // D. Tira: el nombre de la zona entre ‹ y los números.
        ModeladoBlockEntity be = this.handler.be;
        ModeladoBlockEntity.Categoria cat = be.categoria();
        int zonas = ZonasModelado.cantidad(cat);
        int zi = Math.min(this.handler.zonaActiva, zonas - 1);
        c.fill(x0 + TIRA_X + 20, y0 + TIRA_Y, x0 + TIRA_X + TIRA_ANCHO - 20, y0 + TIRA_Y + TIRA_ALTO, 0x33000000);
        Text nombreZona = Text.translatable("modamod.modelado.zona." + ZonasModelado.de(cat)[zi].clave());
        c.drawText(textRenderer, nombreZona, x0 + TIRA_X + 26, y0 + TIRA_Y + 5, EstiloPergamino.TEXTO, false);

        // C. Filas de la zona activa: marco del casillero, nombre del rol y valor.
        ZonasModelado.Zona zona = ZonasModelado.de(cat)[zi];
        for (int f = 0; f < zona.roles().length; f++) {
            int i = ZonasModelado.pinDe(cat, zi, f);
            if (i < 0) continue;
            int p = cat.ordinal() * ModeladoBlockEntity.PINES_POR_CATEGORIA + i;
            int top = y0 + ModeladoScreenHandler.Y_FILAS + f * ModeladoScreenHandler.ALTO_FILA;
            c.fill(x0 + COL_X, top, x0 + COL_X + COL_ANCHO, top + ModeladoScreenHandler.ALTO_FILA - 1, i == pinSel ? 0x77FFE0A8 : 0x33FFFFFF);
            marco(c, x0 + ModeladoScreenHandler.X_SLOT, top + 3);
            c.drawText(textRenderer, Text.translatable("modamod.modelado.rol." + claveDeRol(zona.roles()[f])),
                    x0 + COL_X + 24, top + 2, EstiloPergamino.TEXTO, false);
            Text valor;
            if (be.pinFijado(p) && be.pinCombo(p) != null) valor = descripcion(be.pinCombo(p));
            else if (!be.getStack(ModeladoBlockEntity.pinSlot(p)).isEmpty()) valor = be.getStack(ModeladoBlockEntity.pinSlot(p)).getName();
            else valor = Text.literal("—");
            c.drawText(textRenderer, net.minecraft.util.Language.getInstance().reorder(textRenderer.trimToWidth(valor, COL_ANCHO - 24 - 26)), x0 + COL_X + 24, top + 11, 0xFF7A5A36, false);
        }

        // Desplegable de moldes (bolsas, 2026-10-12): los moldes de la bolsa que acepta el casillero elegido.
        dibujarBolsa(c, zona, cat, be, tema, mouseX, mouseY);

        // Cabecera: Entrada → Modelar → barra → Salida, estado e insumo.
        marcoGrande(c, x0 + ModeladoScreenHandler.X_ENTRADA, y0 + ModeladoScreenHandler.Y_IO);
        marcoGrande(c, x0 + ModeladoScreenHandler.X_SALIDA, y0 + ModeladoScreenHandler.Y_IO);
        float nivel = switch (be.estado()) {
            case PROCESANDO -> Math.min(1f, be.progreso() / (float) ModeladoBlockEntity.TICKS_PROCESO);
            case LISTO -> 1f;
            default -> 0f;
        };
        int bx = x0 + ModeladoScreenHandler.X_ENTRADA + 104, by = y0 + ModeladoScreenHandler.Y_IO + 5, bw = 92;
        c.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF2A180C);
        c.fill(bx, by, bx + bw, by + 6, 0xFF111111);
        c.fill(bx, by, bx + (int) (bw * nivel), by + 6, tema.claro);
        c.drawText(textRenderer, net.minecraft.util.Language.getInstance().reorder(textRenderer.trimToWidth(hint(), 250)), x0 + 296, y0 + 358, EstiloPergamino.TEXTO, false);
        // Contador del único insumo: la tijera y su durabilidad.
        boolean creativa = com.modamod.util.MaquinaCreativa.es(be);
        float dur = be.durabilidadTijera();
        int tx = x0 + 296, ty = y0 + 374;
        if (creativa) {
            c.drawText(textRenderer, Text.translatable("modamod.modelado.tijera_creativa"), tx, ty, 0xFF3F7A38, false);
        } else if (!be.tijeraOk()) {
            c.drawText(textRenderer, Text.translatable("modamod.modelado.sin_tijera"), tx, ty, 0xFF8A2E2E, false);
        } else {
            c.drawText(textRenderer, Text.translatable("modamod.modelado.tijera"), tx, ty, EstiloPergamino.TEXTO, false);
            int bxt = tx + 48;
            c.fill(bxt - 1, ty, bxt + 61, ty + 8, 0xFF2A180C);
            c.fill(bxt, ty + 1, bxt + 60, ty + 7, 0xFF111111);
            c.fill(bxt, ty + 1, bxt + (int) (60 * dur), ty + 7, 0xFF3F9A38);
        }

        c.drawText(textRenderer, Text.translatable("modamod.modelado.diseno.costo"), x0 + 8, y0 + 354, 0xFF7A5A36, false);
        // Inventario del jugador.
        int xi = x0 + ModeladoScreenHandler.X_INV, yi = y0 + ModeladoScreenHandler.Y_INV;
        for (int f = 0; f < 3; f++) for (int col = 0; col < 9; col++) marco(c, xi + col * 18, yi + f * 18);
        for (int col = 0; col < 9; col++) marco(c, xi + col * 18, yi + 58);
        IconoDiseno.marcoBlanco(c, this.x + 9, this.y + 377, this.handler.slots.get(this.handler.slots.size() - 1).hasStack());
    }

    /** Marco de 40x40 alrededor de un casillero de 16x16 dibujado a {@link ModeladoScreenHandler#ESCALA_IO}. */
    private void marcoGrande(DrawContext c, int sx, int sy) {
        c.fill(sx - 13, sy - 13, sx + 29, sy + 29, 0xFF2A180C);
        c.fill(sx - 12, sy - 12, sx + 28, sy + 28, 0xFF8B7355);
    }

    @Override
    protected void drawSlot(DrawContext context, net.minecraft.screen.slot.Slot slot) {
        if (!ModeladoScreenHandler.esSlotGrande(slot.x, slot.y)) {
            super.drawSlot(context, slot);
            return;
        }
        float e = ModeladoScreenHandler.ESCALA_IO;
        context.getMatrices().push();
        context.getMatrices().translate(slot.x + 8, slot.y + 8, 0);
        context.getMatrices().scale(e, e, 1f);
        context.getMatrices().translate(-(slot.x + 8), -(slot.y + 8), 0);
        super.drawSlot(context, slot);
        context.getMatrices().pop();
    }

    @Override
    protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        if (width == 16 && height == 16 && ModeladoScreenHandler.esSlotGrande(x, y)) {
            return super.isPointWithinBounds(x - 12, y - 12, 40, 40, pointX, pointY);
        }
        return super.isPointWithinBounds(x, y, width, height, pointX, pointY);
    }

    private void dibujarBolsa(DrawContext c, ZonasModelado.Zona zona, ModeladoBlockEntity.Categoria cat, ModeladoBlockEntity be,
                              EstiloPergamino.Tema tema, int mouseX, int mouseY) {
        int x0 = this.x, y0 = this.y;
        int yt = y0 + 156;
        c.fill(x0 + COL_X, yt - 2, x0 + COL_X + COL_ANCHO, y0 + 302, 0x22000000);
        if (pestana == 1) {
            List<net.minecraft.item.ItemStack> ds = be.disenosItem();
            List<Integer> filtrados = disenosFiltrados();
            c.drawText(textRenderer, Text.translatable("modamod.modelado.disenos.ayuda"), x0 + COL_X + 6, yt + 3, 0xFF7A5A36, false);
            if (filtrados.isEmpty()) {
                c.drawText(textRenderer, Text.translatable("modamod.modelado.disenos.vacio"), x0 + COL_X + 6, y0 + Y_GRILLA + 4, EstiloPergamino.TEXTO, false);
            }
            IconoDiseno.tarjetaUltimo(c, textRenderer, x0 + X_GRILLA, y0 + Y_GRILLA, DIS_ANCHO, DIS_ALTO - 1, filaDiseno(ratonX, ratonY) == -2);
            for (int k = 0; k < DIS_POR_HOJA; k++) {
                int idx = pagina * DIS_POR_HOJA + k;
                int ry = y0 + Y_GRILLA + (k + 1) * DIS_ALTO;
                if (idx >= filtrados.size()) continue;
                int real = filtrados.get(idx);
                var dc = ds.get(real).get(com.modamod.item.ModamodComponents.DISENO_CORTE);
                String cat2 = dc == null ? null : Text.translatable("modamod.modelado.categoria." + dc.categoria().toLowerCase(Locale.ROOT)).getString();
                IconoDiseno.fila(c, textRenderer, x0 + X_GRILLA, ry, DIS_ANCHO, DIS_ALTO - 1, (filaDiseno(ratonX, ratonY) == real || real == disenoElegido), ds.get(real), cat2);
            }
        } else if (pinSel < 0) {
            c.drawText(textRenderer, Text.translatable("modamod.modelado.bolsa.elegi"), x0 + COL_X + 6, yt + 2, EstiloPergamino.TEXTO, false);
            c.drawText(textRenderer, Text.translatable("modamod.modelado.bolsa.cuenta", be.bolsa().size()), x0 + COL_X + 6, yt + 14, 0xFF7A5A36, false);
        } else {
            ModeladoBlockEntity.Rol rol = ModeladoBlockEntity.ROLES[cat.ordinal()][pinSel];
            c.drawText(textRenderer, Text.translatable("modamod.modelado.bolsa.para", Text.translatable("modamod.modelado.rol." + claveDeRol(rol))),
                    x0 + COL_X + 6, yt + 3, EstiloPergamino.TEXTO, false);
            List<net.minecraft.util.Identifier> lista = candidatos();
            if (lista.isEmpty()) {
                c.drawText(textRenderer, Text.translatable("modamod.modelado.bolsa.vacia"), x0 + COL_X + 6, yt + 16, 0xFF7A5A36, false);
            }
            var fijado = moldeFijado(pinSel);
            for (int k = 0; k < POR_HOJA; k++) {
                int idx = pagina * POR_HOJA + k;
                int cx = x0 + X_GRILLA + (k % COLS_BOLSA) * CELDA, cy = y0 + Y_GRILLA + (k / COLS_BOLSA) * CELDA;
                c.fill(cx, cy, cx + CELDA - 2, cy + CELDA - 2, 0xFF2A180C);
                boolean hay = idx < lista.size();
                boolean sel = hay && lista.get(idx).equals(fijado);
                c.fill(cx + 1, cy + 1, cx + CELDA - 3, cy + CELDA - 3, sel ? tema.claro : 0xFF8B7355);
                if (!hay) continue;
                var item = net.minecraft.registry.Registries.ITEM.get(lista.get(idx));
                int n = be.bolsa().getOrDefault(lista.get(idx), 1);
                ItemStack st = new ItemStack(item, Math.min(99, n));
                c.drawItem(st, cx + 3, cy + 3);
                c.drawItemInSlot(textRenderer, st, cx + 3, cy + 3);
            }
            if (btnPagPrev.visible) {
                int hojas = Math.max(1, (lista.size() + POR_HOJA - 1) / POR_HOJA);
                c.drawText(textRenderer, (pagina + 1) + "/" + hojas, x0 + X_GRILLA + 48, y0 + Y_GRILLA + FILAS_BOLSA * CELDA + 6, EstiloPergamino.TEXTO, false);
            }
        }
        // Depósito: soltar moldes acá los suma a la bolsa.
        marco(c, x0 + ModeladoScreenHandler.X_BOLSA, y0 + ModeladoScreenHandler.Y_BOLSA);
        c.drawText(textRenderer, Text.translatable("modamod.modelado.bolsa.depositar"), x0 + ModeladoScreenHandler.X_BOLSA + 24, y0 + ModeladoScreenHandler.Y_BOLSA, EstiloPergamino.TEXTO, false);
        c.drawText(textRenderer, Text.translatable("modamod.modelado.bolsa.ayuda"), x0 + ModeladoScreenHandler.X_BOLSA + 24, y0 + ModeladoScreenHandler.Y_BOLSA + 10, 0xFF7A5A36, false);
    }

    private void marco(DrawContext c, int sx, int sy) {
        c.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF2A180C);
        c.fill(sx, sy, sx + 16, sy + 16, 0xFF8B7355);
    }

    private static String claveDeRol(ModeladoBlockEntity.Rol rol) {
        return switch (rol) {
            case PERS_IZQ1 -> "pers_izq";
            case PERS_DER1 -> "pers_der";
            default -> rol.name().toLowerCase(Locale.ROOT);
        };
    }

    /**
     * Visor 3D: el jugador vistiendo la prenda en construcción (no la real) mediante
     * {@link GarmentFeatureRenderer#previewOverride}; se arma justo antes del único dibujo y se limpia enseguida.
     * La cámara va hacia el enfoque de la zona activa con una transición suave (constante de tiempo ~90 ms).
     */
    private void dibujarPreview(DrawContext context, int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity jugador = client.player;
        if (jugador == null) return;

        ItemStack enConstruccion = this.handler.be.previsualizar();
        boolean esAccesorio = com.modamod.render.AccesorioRenderer.es(enConstruccion);
        // Con una prenda en la Entrada solo se ve ella (2026-10-12, "cuando haya prenda puesta en entrada el resto de la
        // ropa no se vea"); sin prenda, el jugador con lo que lleva puesto.
        boolean hayEntrada = !this.handler.be.getStack(ModeladoBlockEntity.PRENDA).isEmpty();
        List<ItemStack> prendas = hayEntrada ? new ArrayList<>() : new ArrayList<>(GarmentFeatureRenderer.equipadas(jugador));
        if (!enConstruccion.isEmpty() && !esAccesorio) {
            prendas.removeIf(s -> s.getItem().getClass() == enConstruccion.getItem().getClass());
            prendas.add(enConstruccion);
        }

        ModeladoBlockEntity.Categoria cat = this.handler.be.categoria();
        ZonasModelado.Zona[] zonas = ZonasModelado.de(cat);
        ZonasModelado.Enfoque e = zonas[Math.min(this.handler.zonaActiva, zonas.length - 1)].enfoque();
        long ahora = net.minecraft.util.Util.getMeasuringTimeMs();
        float dt = ultimoCuadroMs < 0 ? 1000f : Math.min(200f, ahora - ultimoCuadroMs);
        ultimoCuadroMs = ahora;
        float k = 1f - (float) Math.exp(-dt / 90f);
        focoActual += (e.focoY - focoActual) * k;
        tamActual += (e.tamano - tamActual) * k;

        GarmentFeatureRenderer.previewOverride = prendas;
        GarmentFeatureRenderer.sombreroOverride = esAccesorio ? enConstruccion : null;
        try {
            PreviewJugador.dibujar(context, jugador, this.x + VISOR_X1, this.y + VISOR_Y1, this.x + VISOR_X2, this.y + VISOR_Y2,
                    Math.round(tamActual * zoomVista), anguloVista,
                    // Sin seguir al mouse (2026-10-12, "la figura sigue el mouse... impedira poner apliques en superficies
                    // superiores e inferiores"): se pasa el centro del visor, así el cabeceo es 0; se inclina con click derecho.
                    this.y + (VISOR_Y1 + VISOR_Y2) / 2f, inclinacion, focoActual);
        } finally {
            GarmentFeatureRenderer.previewOverride = null;
            GarmentFeatureRenderer.sombreroOverride = null;
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        EstiloPergamino.titulo(context, this.textRenderer, this.title, this.titleX, this.titleY);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX,
                this.playerInventoryTitleY, EstiloPergamino.TEXTO, false);
        dibujarFantasmasDePines(context);
    }

    /**
     * Ícono fantasma en cada casillero de la zona con el corte ya fijado (2026-09-25, "quiero que quede visible el
     * patron cuando esta pineado"): el molde volvió al almacén, así que se dibuja el ítem que lo produjo, desvaído.
     * Coordenadas locales (drawForeground ya está trasladado).
     */
    private void dibujarFantasmasDePines(DrawContext context) {
        ModeladoBlockEntity be = this.handler.be;
        ModeladoBlockEntity.Categoria cat = be.categoria();
        for (int i = 0; i < ModeladoBlockEntity.PINES_POR_CATEGORIA; i++) {
            if (ZonasModelado.zonaDe(cat, i) != this.handler.zonaActiva) continue;
            int p = cat.ordinal() * ModeladoBlockEntity.PINES_POR_CATEGORIA + i;
            if (!be.pinFijado(p) || !be.getStack(ModeladoBlockEntity.pinSlot(p)).isEmpty()) continue;
            ComboCorte combo = be.pinCombo(p);
            if (combo == null) continue;
            ItemStack icono = iconoDe(combo);
            if (icono.isEmpty()) continue;
            int x = ModeladoScreenHandler.PIN_POS[cat.ordinal()][i][0], y = ModeladoScreenHandler.PIN_POS[cat.ordinal()][i][1];
            context.drawItem(icono, x, y);
            // drawItem dibuja en z=150: el velo tiene que ir más arriba o queda debajo del ícono
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            context.fill(x, y, x + 16, y + 16, 0x5CD9B98A);
            context.getMatrices().pop();
        }
    }

    private Text hint() {
        ModeladoBlockEntity be = this.handler.be;
        if (be.estado() == ModeladoBlockEntity.Estado.PROCESANDO) return Text.translatable("modamod.modelado.estado.procesando");
        if (!be.getStack(ModeladoBlockEntity.SALIDA).isEmpty()) return Text.translatable("modamod.modelado.hint.listo");
        if (be.encendida()) return Text.translatable("modamod.modelado.hint.esperando");
        if (be.fijadas().isEmpty()) return Text.translatable("modamod.modelado.hint.ajustar");
        return Text.translatable("modamod.modelado.hint.fijado");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        ratonX = mouseX;
        ratonY = mouseY;
        refrescar();
        super.render(context, mouseX, mouseY, delta);
        if (pestana == 1) {
            int d = filaDiseno(mouseX, mouseY);
            if (d >= 0) context.drawItemTooltip(textRenderer, this.handler.be.disenosItem().get(d), mouseX, mouseY);
        } else if (pinSel >= 0) {
            List<net.minecraft.util.Identifier> lista = candidatos();
            int c = celdaBajo(mouseX, mouseY, lista.size());
            if (c >= 0) {
                context.drawItemTooltip(textRenderer, new ItemStack(net.minecraft.registry.Registries.ITEM.get(lista.get(c))), mouseX, mouseY);
            }
        }
        // Las chinchetas van ENCIMA de todo lo demás (incluido el ícono fantasma de drawForeground).
        for (BotonChincheta b : btnPines) {
            if (b.visible) b.render(context, mouseX, mouseY, delta);
        }
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /**
     * Chincheta de un casillero: dibujada a mano. Hueca = hay molde puesto, todavía sin fijar; rosa llena = corte ya
     * fijado (el molde volvió al almacén).
     */
    private static class BotonChincheta extends ButtonWidget {
        boolean fijado;
        int pin;
        private long cambioMs = -1000;

        BotonChincheta(int x, int y, PressAction onPress) {
            super(x, y, 16, 16, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        }

        /** Registra el cambio de estado para animar el clavado (cuadro intermedio unos ms). */
        void actualizar(boolean nuevo) {
            if (nuevo != fijado) cambioMs = net.minecraft.util.Util.getMeasuringTimeMs();
            fijado = nuevo;
        }

        @Override
        public void onPress() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.interactionManager == null || client.currentScreen == null) return;
            if (client.currentScreen instanceof ModeladoScreen pantalla) {
                pantalla.clickBoton(ModeladoBlockEntity.BTN_PIN_BASE + pin);
            }
        }

        @Override
        protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            boolean animando = net.minecraft.util.Util.getMeasuringTimeMs() - cambioMs < 160;
            int cuadro = animando ? 1 : fijado ? 2 : 0;
            int x = getX(), y = getY();
            if (isHovered() && active) c.fill(x + 2, y + 2, x + 14, y + 14, 0x33FFFFFF);
            c.getMatrices().push();
            c.getMatrices().translate(0, 0, 400);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            c.setShaderColor(1f, 1f, 1f, active ? 1f : 0.55f);
            c.drawTexture(TEXTURE_CHINCHETA[cuadro], x + 2, y + 2, 0, 0, 12, 12, 12, 12);
            c.setShaderColor(1f, 1f, 1f, 1f);
            c.getMatrices().pop();
        }
    }

}
