package com.modamod.client;

import com.modamod.client.DisposicionEstilista.R;
import com.modamod.modelado.ZonasModelado.Enfoque;
import com.modamod.render.GarmentFeatureRenderer;
import com.modamod.sublimadora.Estampa;
import com.modamod.sublimadora.SublimadoraBlockEntity;
import com.modamod.sublimadora.SublimadoraBolsaPayload;
import com.modamod.sublimadora.SublimadoraScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pantalla de la Sublimadora en la ventana de 560×408 del plan de GUIs (2026-10-13, "vamos con la siguiente maquina",
 * recomendado en todo): visor grande del jugador con la prenda a la izquierda y una columna de controles contextuales a la
 * derecha, todos acomodados por {@link DisposicionSublimadora} (que se verifica sin abrir el juego).
 * <ul>
 *   <li>Arriba de la columna: selector de prenda con ‹ › y las zonas de cámara de esa prenda.</li>
 *   <li>Pestañas: Foto, Máscara, Capas, Bolsas y Diseños. Las fotos, las máscaras y los diseños son bolsas de
 *   referencias: elegir no gasta nada.</li>
 *   <li>Frente y Espalda juntas arriba (Foto y Máscara): click elige la cara que editan los controles y la chincheta la
 *   fija; solo se estampan las caras fijadas.</li>
 *   <li>Cabecera: Entrada → Prensar → barra → Salida; tanques de tinta y papel abajo a la derecha.</li>
 * </ul>
 */
public class SublimadoraScreen extends HandledScreen<SublimadoraScreenHandler> {

    private static final int ANCHO = 560, ALTO = 408;
    private static final int PX1 = 8, PY1 = 24, PX2 = 312, PY2 = 302;
    private static final int X = DisposicionSublimadora.X;
    private static final int COLS = 10;

    private int pestana = 0;
    private Map<String, R> disp = Map.of();
    private DisposicionSublimadora.Modo modo = DisposicionSublimadora.Modo.FOTO;
    private final Map<String, ButtonWidget> w = new HashMap<>();
    private final ButtonWidget[] btnPestana = new ButtonWidget[5];
    private final ButtonWidget[] btnZona = new ButtonWidget[Enfoque.values().length];
    private ButtonWidget btnCatAtras, btnCategoria, btnCatAdelante, btnPrensar;
    private net.minecraft.client.gui.widget.TextFieldWidget txtNombre;
    /** El diseño de la lista elegido para exportar (el último que se cargó con click izquierdo; 2026-10-25). */
    private int disenoElegido = -1;
    private final TinturasScreen.BotonChincheta[] btnChinchetas = new TinturasScreen.BotonChincheta[2];
    private int pagSel = 0, pagDis = 0;

    // visor
    private float anguloVista = 0f, inclinacionVista = 0f, zoomVista = 1f;
    private boolean arrastrando = false;
    private Enfoque enfoque = Enfoque.ENTERO;
    private float focoActual = Enfoque.ENTERO.focoY, tamActual = Enfoque.ENTERO.tamano;
    private long ultimoCuadroMs = -1;
    private String ultimoSolape = "";

    public SublimadoraScreen(SublimadoraScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = ANCHO;
        this.backgroundHeight = ALTO;
        this.titleX = PX1;
        this.titleY = 9;
        this.playerInventoryTitleX = SublimadoraScreenHandler.M_MEDIO;
        this.playerInventoryTitleY = 314;
    }

    private SublimadoraBlockEntity be() { return handler.be; }

    private ButtonWidget boton(String clave, int ancho, Text texto, String tooltip, Runnable accion) {
        ButtonWidget b = new EstiloPergamino.BotonPergamino(0, 0, ancho, 16, texto, btn -> accion.run());
        if (tooltip != null) b.setTooltip(Tooltip.of(Text.translatable(tooltip)));
        this.addDrawableChild(b);
        w.put(clave, b);
        return b;
    }

    private void control(String clave, String tooltip, int menos, int mas) {
        boton(clave + "Menos", 14, Text.literal("<"), tooltip, () -> clickBoton(menos));
        boton(clave, 82, Text.empty(), tooltip, () -> clickBoton(mas));
        boton(clave + "Mas", 14, Text.literal(">"), tooltip, () -> clickBoton(mas));
    }

    @Override
    protected void init() {
        EstiloPergamino.usarTema(EstiloPergamino.Tema.ORO);
        super.init();
        w.clear();
        // Selector de prenda y zonas de cámara de esa prenda.
        btnCatAtras = new EstiloPergamino.BotonPergamino(this.x + X, this.y + 24, 14, 14, Text.literal("<"),
                b -> clickBoton(SublimadoraBlockEntity.BTN_CATEGORIA_ATRAS));
        btnCategoria = new EstiloPergamino.BotonPergamino(this.x + X + 14, this.y + 24, 76, 14, Text.empty(), b -> { });
        btnCatAdelante = new EstiloPergamino.BotonPergamino(this.x + X + 90, this.y + 24, 14, 14, Text.literal(">"),
                b -> clickBoton(SublimadoraBlockEntity.BTN_CATEGORIA));
        btnCategoria.setTooltip(Tooltip.of(Text.translatable("modamod.sublimadora.tooltip.categoria")));
        this.addDrawableChild(btnCatAtras);
        this.addDrawableChild(btnCategoria);
        this.addDrawableChild(btnCatAdelante);
        var enfoques = Enfoque.values();
        for (int i = 0; i < enfoques.length; i++) {
            var e = enfoques[i];
            btnZona[i] = new ButtonWidget(0, -100, 41, 14,
                    Text.translatable("modamod.estilado.enfoque." + e.name().toLowerCase(java.util.Locale.ROOT)),
                    b -> enfoque = e, d -> Text.empty()) {
                @Override
                protected void renderWidget(DrawContext c, int mx, int my, float d) {
                    c.getMatrices().push();
                    c.getMatrices().translate(0, 0, 500);
                    EstiloPergamino.fondoBoton(c, getX(), getY(), getWidth(), getHeight(), isHovered() || enfoque == e, active);
                    c.drawCenteredTextWithShadow(textRenderer, getMessage(), getX() + getWidth() / 2, getY() + 3,
                            enfoque == e ? 0xFFFFE0A8 : EstiloPergamino.TEXTO_CLARO);
                    c.getMatrices().pop();
                }
            };
            this.addDrawableChild(btnZona[i]);
        }

        // Pestañas.
        String[] claves = {"foto", "mascara", "capas", "bolsas", "disenos"};
        for (int i = 0; i < 5; i++) {
            final int cual = i;
            btnPestana[i] = new EstiloPergamino.BotonPergamino(this.x + X + i * 46, this.y + 42, 46, 13,
                    Text.translatable("modamod.sublimadora.gui.tab." + claves[i]), b -> { pestana = cual; pagSel = 0; });
            btnPestana[i].setTooltip(Tooltip.of(Text.translatable("modamod.sublimadora.gui.tooltip.tab." + i)));
            this.addDrawableChild(btnPestana[i]);
        }

        // Línea de producción (2026-10-08): apagada de fábrica.
        this.addDrawableChild(new EstiloPergamino.BotonLinea(this.x + 452, this.y + 3, 96, 16,
                () -> be().linea(), b -> clickBoton(SublimadoraBlockEntity.BTN_LINEA)));

        // Cabecera: Prensar entre la Entrada y la barra.
        btnPrensar = new EstiloPergamino.BotonPergamino(this.x + SublimadoraScreenHandler.X_ENTRADA + 36, this.y + SublimadoraScreenHandler.Y_IO, 60, 16,
                Text.translatable("modamod.sublimadora.boton.prensar"), b -> clickBoton(SublimadoraBlockEntity.BTN_PRENSAR));
        btnPrensar.setTooltip(Tooltip.of(Text.translatable("modamod.sublimadora.tooltip.prensar")));
        this.addDrawableChild(btnPrensar);

        // Controles de la cara elegida.
        control("escala", "modamod.sublimadora.tooltip.escala", SublimadoraBlockEntity.BTN_ESCALA_MENOS, SublimadoraBlockEntity.BTN_ESCALA_MAS);
        control("x", "modamod.sublimadora.tooltip.x", SublimadoraBlockEntity.BTN_X_MENOS, SublimadoraBlockEntity.BTN_X_MAS);
        control("y", "modamod.sublimadora.tooltip.y", SublimadoraBlockEntity.BTN_Y_MENOS, SublimadoraBlockEntity.BTN_Y_MAS);
        control("angulo", "modamod.sublimadora.tooltip.angulo", SublimadoraBlockEntity.BTN_ANGULO_MENOS, SublimadoraBlockEntity.BTN_ANGULO_MAS);
        boton("editar", 112, Text.empty(), "modamod.sublimadora.tooltip.editar", () -> clickBoton(SublimadoraBlockEntity.BTN_EDITAR));
        boton("simetria", 112, Text.empty(), "modamod.sublimadora.tooltip.simetria", () -> clickBoton(SublimadoraBlockEntity.BTN_SIMETRIA));
        boton("quitarFoto", 112, Text.translatable("modamod.sublimadora.gui.quitar_foto"), null,
                () -> enviar(SublimadoraBolsaPayload.QUITAR, 0, 0));
        boton("quitarMascara", 112, Text.translatable("modamod.sublimadora.gui.quitar_mascara"), null,
                () -> enviar(SublimadoraBolsaPayload.QUITAR, 1, 0));
        // Escanear estampa (2026-10-02): una imagen del disco como foto de Camerapture.
        ButtonWidget esc = boton("escanear", 112, Text.translatable("modamod.sublimadora.boton.escanear"), null,
                () -> EscanerEstampaCliente.escanear(be().getPos()));
        esc.setTooltip(Tooltip.of(Text.translatable(EscanerEstampaCliente.disponible()
                ? "modamod.sublimadora.tooltip.escanear" : "modamod.sublimadora.escanear.sin_camerapture")));
        esc.active = EscanerEstampaCliente.disponible();

        // Capas con máscara.
        for (int i = 0; i < com.modamod.sublimadora.CapaEstampa.MAXIMO; i++) {
            int idx = i;
            boton("cp" + i, 37, Text.empty(), null, () -> clickBoton(SublimadoraBlockEntity.BTN_CAPA_BASE + idx));
        }
        boton("cpSubir", 36, Text.literal("▲"), "modamod.sublimadora.tooltip.capa_subir", () -> clickBoton(SublimadoraBlockEntity.BTN_CAPA_SUBIR));
        boton("cpBajar", 36, Text.literal("▼"), "modamod.sublimadora.tooltip.capa_bajar", () -> clickBoton(SublimadoraBlockEntity.BTN_CAPA_BAJAR));
        boton("cpBorrar", 36, Text.literal("✕"), "modamod.sublimadora.tooltip.capa_borrar", () -> clickBoton(SublimadoraBlockEntity.BTN_CAPA_BORRAR));

        // Una chincheta por cara (se dibujan a mano encima de todo).
        for (int i = 0; i < 2; i++) {
            int idx = i;
            btnChinchetas[i] = new TinturasScreen.BotonChincheta(b -> clickBoton(SublimadoraBlockEntity.BTN_CHINCHETA_BASE + idx));
            btnChinchetas[i].texturas = TinturasScreen.CHINCHETA_ORO;
            this.addSelectableChild(btnChinchetas[i]);
        }

        // Panel de la izquierda del inventario: nombre y "+" (crea un Diseño de sublimado).
        int dx = this.x + SublimadoraScreenHandler.X_DISENOS, dy = this.y + SublimadoraScreenHandler.Y_DISENOS;
        txtNombre = new net.minecraft.client.gui.widget.TextFieldWidget(this.textRenderer, dx, dy + 56, 83, 14,
                Text.translatable("modamod.tinturas.nombre_diseno"));
        txtNombre.setMaxLength(24);
        txtNombre.setPlaceholder(Text.translatable("modamod.tinturas.nombre_diseno"));
        this.addDrawableChild(txtNombre);
        ButtonWidget guardar = new EstiloPergamino.BotonPergamino(dx + 85, dy + 56, 20, 14, Text.literal("+"), b -> {
            ClientPlayNetworking.send(new com.modamod.sublimadora.GuardarDisenoSublimadoraPayload(be().getPos(), txtNombre.getText()));
            txtNombre.setText("");
        });
        guardar.setTooltip(Tooltip.of(Text.translatable("modamod.sublimadora.tooltip.guardar_diseno")));
        this.addDrawableChild(guardar);
        // Exportar el diseño elegido de la lista con el diseño en blanco del casillero de al lado (2026-10-25, "un botoncito de
        // exportar para exportar un diseñito que lleve el nombre del diseño guardado seleccionado").
        ButtonWidget btnExportar = new EstiloPergamino.BotonPergamino(this.x + 28, this.y + 346, 85, 14,
                Text.translatable("modamod.disenos.exportar"), b -> { enviar(SublimadoraBolsaPayload.EXPORTAR_DISENO, 0, disenoElegido); });
        btnExportar.setTooltip(Tooltip.of(Text.translatable("modamod.disenos.tooltip.exportar")));
        this.addDrawableChild(btnExportar);
        // Borrar lo fijado (2026-10-14, "un boton de delete"): suelta las caras y quita las capas.
ButtonWidget borrar = new EstiloPergamino.BotonPergamino(dx, dy + 74, 36, 14, Text.translatable("modamod.sublimadora.borrar"),
                b -> clickBoton(SublimadoraBlockEntity.BTN_BORRAR));
        borrar.setTooltip(Tooltip.of(Text.translatable("modamod.sublimadora.tooltip.borrar")));
        this.addDrawableChild(borrar);
        ButtonWidget borrarTodas = new EstiloPergamino.BotonPergamino(dx + 38, dy + 74, 67, 14, Text.translatable("modamod.sublimadora.borrar_todo"),
                b -> clickBoton(SublimadoraBlockEntity.BTN_BORRAR_TODAS));
        borrarTodas.setTooltip(Tooltip.of(Text.translatable("modamod.sublimadora.tooltip.borrar_todo")));
        this.addDrawableChild(borrarTodas);
    }

    private void clickBoton(int id) {
        this.client.interactionManager.clickButton(this.handler.syncId, id);
    }

    private void enviar(int accion, int tipo, int indice) {
        ClientPlayNetworking.send(new SublimadoraBolsaPayload(be().getPos(), accion, tipo, indice));
    }

    // ── cámara ──
    private List<Enfoque> zonas() {
        net.minecraft.item.Item c = be().categoria();
        if (c == com.modamod.sublimadora.ModItems.REMERA) return List.of(Enfoque.TORSO, Enfoque.ENTERO);
        if (c == com.modamod.item.ModamodItems.SOCKS_SOLID || c == com.modamod.item.ModamodItems.PANTALON) return List.of(Enfoque.PIERNAS, Enfoque.ENTERO);
        if (c == com.modamod.item.ModamodItems.CALIENTABRAZOS) return List.of(Enfoque.BRAZOS, Enfoque.ENTERO);
        return List.of(Enfoque.ENTERO);
    }

    private boolean dentroVisor(double mx, double my) {
        return mx >= this.x + PX1 && mx < this.x + PX2 && my >= this.y + PY1 && my < this.y + PY2;
    }

    private void dibujarVisor(DrawContext context) {
        int x1 = this.x + PX1 + 2, y1 = this.y + PY1 + 2, x2 = this.x + PX2 - 2, y2 = this.y + PY2 - 2;
        context.fill(this.x + PX1 - 1, this.y + PY1 - 1, this.x + PX2 + 1, this.y + PY2 + 1, 0xFF2A180C);
        context.fill(this.x + PX1, this.y + PY1, this.x + PX2, this.y + PY2, 0xFF4A3828);
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity jugador = client.player;
        if (jugador == null) return;
        ItemStack vista = be().prendaDeVistaPrevia(true);
        if (vista.getItem() instanceof net.minecraft.item.BannerItem) {
            // El banner no se viste: se ve la tela con la foto, grande, en el centro del visor.
            context.getMatrices().push();
            context.getMatrices().translate((x1 + x2) / 2f - 8 * 12f, (y1 + y2) / 2f - 8 * 12f, 0);
            context.getMatrices().scale(12f, 12f, 1f);
            context.drawItem(vista, 0, 0);
            context.getMatrices().pop();
            return;
        }
        List<ItemStack> prendas = new ArrayList<>(GarmentFeatureRenderer.equipadas(jugador));
        boolean esCara = vista.getItem() instanceof com.modamod.item.CaraItem;   // la dibuja CaraRenderer (2026-10-24)
        boolean esGorra = vista.getItem() instanceof com.modamod.item.SombreroBrujaItem;   // la dibuja SombreroRenderer (2026-10-25)
        if (!vista.isEmpty() && !esCara && !esGorra) {
            prendas.removeIf(s -> s.getItem().getClass() == vista.getItem().getClass());
            prendas.add(vista);
        }
        long ahora = net.minecraft.util.Util.getMeasuringTimeMs();
        float dt = ultimoCuadroMs < 0 ? 1000f : Math.min(200f, ahora - ultimoCuadroMs);
        ultimoCuadroMs = ahora;
        float k = 1f - (float) Math.exp(-dt / 90f);
        focoActual += (enfoque.focoY - focoActual) * k;
        tamActual += (enfoque.tamano - tamActual) * k;
        GarmentFeatureRenderer.previewOverride = prendas;
        com.modamod.render.CaraRenderer.override = esCara ? vista : null;
        GarmentFeatureRenderer.sombreroOverride = esGorra ? vista : null;
        try {
            PreviewJugador.dibujar(context, jugador, x1, y1, x2, y2, Math.round(tamActual * zoomVista), anguloVista,
                    (y1 + y2) / 2f, inclinacionVista, focoActual);
        } finally {
            GarmentFeatureRenderer.previewOverride = null;
            com.modamod.render.CaraRenderer.override = null;
            GarmentFeatureRenderer.sombreroOverride = null;
        }
    }

    // ── disposición ──
    private void poner(String clave) {
        ButtonWidget b = w.get(clave);
        if (b == null) return;
        R r = disp.get(clave);
        if (r == null) {
            b.visible = false;
            return;
        }
        b.visible = true;
        b.setX(this.x + r.x());
        b.setY(this.y + r.y());
        b.setWidth(r.w());
    }

    private void reubicar() {
        SublimadoraBlockEntity be = be();
        modo = DisposicionSublimadora.Modo.values()[Math.min(pestana, 4)];
        disp = DisposicionSublimadora.calcular(new DisposicionSublimadora.Estado(modo, be.hayMascara(),
                SublimadoraBlockEntity.admiteSimetria(be.categoria()), be.admiteEspalda()));
        for (String k : new ArrayList<>(w.keySet())) poner(k);
        // Chinchetas: arriba a la derecha de cada tarjeta de cara (solo en Foto y Máscara).
        for (int c = 0; c < 2; c++) {
            R r = disp.get("cara" + c);
            btnChinchetas[c].visible = r != null;
            if (r != null) btnChinchetas[c].setPosition(this.x + r.x() + r.w() - 18, this.y + r.y() + 4);
        }
        // Zonas de cámara de la prenda, junto al selector de prenda.
        var zs = zonas();
        if (!zs.contains(enfoque)) enfoque = Enfoque.ENTERO;
        for (int i = 0; i < btnZona.length; i++) {
            int k = zs.indexOf(Enfoque.values()[i]);
            btnZona[i].visible = k >= 0;
            if (k >= 0) { btnZona[i].setX(this.x + 424 + k * 42); btnZona[i].setY(this.y + 24); }
        }
        handler.verDeposito = pestana >= 3;
    }

    private void refrescar() {
        SublimadoraBlockEntity be = be();
        boolean reposo = be.getEstado() == SublimadoraBlockEntity.Estado.REPOSO;
        btnCategoria.setMessage(Text.literal(textRenderer.trimToWidth(Text.translatable("modamod.modelado.categoria."
                + claveCategoria(be.categoria())).getString(), 70)));
        w.get("escala").setMessage(Text.translatable("modamod.sublimadora.escala", Math.round(be.getEscalaBorrador() * 100)));
        w.get("x").setMessage(Text.translatable("modamod.sublimadora.posicion.x", Math.round(be.getXBorrador() * 100)));
        w.get("y").setMessage(Text.translatable("modamod.sublimadora.posicion.y", Math.round(be.getYBorrador() * 100)));
        w.get("angulo").setMessage(Text.translatable("modamod.sublimadora.angulo", Math.round(be.getAnguloBorrador())));
        for (String k : new String[] {"escala", "x", "y", "angulo"}) {
            w.get(k + "Menos").active = w.get(k).active = w.get(k + "Mas").active = reposo;
        }
        w.get("editar").setMessage(Text.translatable(be.editarMascaraElegido()
                ? "modamod.sublimadora.boton.editar_mascara" : "modamod.sublimadora.boton.editar_imagen"));
        w.get("editar").active = reposo && be.hayMascara();
        w.get("simetria").setMessage(Text.translatable("modamod.modelado.simetria",
                Text.translatable(be.simetria() ? "modamod.si" : "modamod.no")));
        w.get("quitarFoto").active = reposo && !be.getStack(be.getSeleccion().ordinal()).isEmpty();
        w.get("quitarMascara").active = reposo && be.hayMascara();
        btnPrensar.active = reposo && !be.getRemera().isEmpty() && be.getSalida().isEmpty();

        for (int c = 0; c < 2; c++) {
            Estampa.Cara cara = Estampa.Cara.values()[c];
            btnChinchetas[c].actualizar(be.caraFijada(cara));
            boolean comoCapa = be.hayMascara() && be.getFotoCargada(cara) != null;
            btnChinchetas[c].setTooltip(Tooltip.of(Text.translatable(comoCapa ? "modamod.sublimadora.tooltip.chincheta_capa"
                    : be.caraFijada(cara) ? "modamod.sublimadora.tooltip.chincheta_quitar" : "modamod.sublimadora.tooltip.chincheta_fijar",
                    Text.translatable("modamod.sublimadora.rotulo." + cara.clave))));
        }
        var capas = be.capas();
        for (int i = 0; i < com.modamod.sublimadora.CapaEstampa.MAXIMO; i++) {
            ButtonWidget b = w.get("cp" + i);
            if (i >= capas.size()) {
                b.active = false;
                b.setMessage(Text.literal("·"));
                b.setTooltip(null);
                continue;
            }
            var capa = capas.get(i);
            b.active = reposo;
            b.setMessage(Text.literal((i + 1) + (capa.cara() == Estampa.Cara.FRENTE ? "F" : "E")));
            b.setTooltip(Tooltip.of(Text.translatable("modamod.sublimadora.tooltip.capa", i + 1,
                    Text.translatable("modamod.sublimadora.forma." + capa.mascara().forma().asString()),
                    Text.translatable("modamod.sublimadora.rotulo." + capa.cara().clave))));
        }
        int elegida = be.capaElegida();
        boolean hay = reposo && elegida >= 0 && elegida < capas.size();
        w.get("cpSubir").active = hay && elegida < capas.size() - 1;
        w.get("cpBajar").active = hay && elegida > 0;
        w.get("cpBorrar").active = hay;
    }

    private static String claveCategoria(net.minecraft.item.Item item) {
        if (item == com.modamod.sublimadora.ModItems.REMERA) return "remera";
        if (item == com.modamod.item.ModamodItems.SOCKS_SOLID) return "medias";
        if (item == com.modamod.item.ModamodItems.PANTALON) return "pantalon";
        if (item == com.modamod.item.ModamodItems.POLLERA) return "pollera";
        if (item == com.modamod.item.ModamodItems.CAPA) return "capa";
        if (item == com.modamod.item.ModamodItems.DELANTAL) return "delantal";
        if (item instanceof net.minecraft.item.BannerItem) return "banner";
        if (item instanceof com.modamod.item.CaraItem c) return c.modelo;
        if (item instanceof com.modamod.item.SombreroBrujaItem) return "sombrero";
        return "calientabrazos";
    }

    // ── selector de fotos / máscaras ──
    private List<ItemStack> listaSel() {
        return pestana == 0 ? be().fotosBolsa() : be().mascarasBolsa();
    }

    private void dibujarSelector(DrawContext c, int mx, int my) {
        R r = disp.get("sel0");
        if (r == null) return;
        var l = listaSel();
        int y0 = this.y + r.y(), x0 = this.x + r.x();
        c.drawText(textRenderer, Text.translatable(pestana == 0 ? "modamod.sublimadora.gui.sel.fotos" : "modamod.sublimadora.gui.sel.mascaras"),
                x0, y0, EstiloPergamino.TEXTO, false);
        pagSel = Math.max(0, Math.min(pagSel, Math.max(0, (l.size() - 1) / COLS)));
        c.drawText(textRenderer, "<", x0 + 6, y0 + 15, 0xFF4B2E83, false);
        c.drawText(textRenderer, ">", x0 + 24 + COLS * 18, y0 + 15, 0xFF4B2E83, false);
        if (l.isEmpty()) {
            c.drawText(textRenderer, Text.translatable(pestana == 0 ? "modamod.sublimadora.gui.sel.vacio" : "modamod.sublimadora.gui.sel.vacio_mascaras"),
                    x0 + 20, y0 + 15, 0xFF7A6A52, false);
            return;
        }
        ItemStack puesto = pestana == 0 ? be().getStack(be().getSeleccion().ordinal()) : be().getStack(SublimadoraBlockEntity.SLOT_MASCARA);
        for (int k = 0; k < COLS && pagSel * COLS + k < l.size(); k++) {
            ItemStack e = l.get(pagSel * COLS + k);
            int ix = x0 + 20 + k * 18, iy = y0 + 10;
            boolean sel = !puesto.isEmpty() && (pestana == 0 ? ItemStack.areItemsAndComponentsEqual(puesto, e) : puesto.getItem() == e.getItem());
            c.fill(ix, iy, ix + 18, iy + 18, sel ? 0xFF7B4FA8 : 0xFF2A180C);
            c.fill(ix + 1, iy + 1, ix + 17, iy + 17, mx >= ix && mx < ix + 18 && my >= iy && my < iy + 18 ? 0xFFA38968 : 0xFF8B7355);
            c.drawItem(e, ix + 1, iy + 1);
        }
    }

    /** {fila (0), columna}: -1 = flecha izquierda, COLS = derecha; o null. */
    private int[] selectorBajo(double mx, double my) {
        R r = disp.get("sel0");
        if (r == null) return null;
        int y0 = this.y + r.y() + 10;
        if (my < y0 || my >= y0 + 18) return null;
        double rx = mx - (this.x + r.x());
        if (rx >= 0 && rx < 20) return new int[] {0, -1};
        if (rx >= 20 + COLS * 18 && rx < 40 + COLS * 18) return new int[] {0, COLS};
        if (rx >= 20 && rx < 20 + COLS * 18) return new int[] {0, (int) ((rx - 20) / 18)};
        return null;
    }

    private boolean clickSelector(double mx, double my, int boton) {
        int[] h = selectorBajo(mx, my);
        if (h == null) return false;
        var l = listaSel();
        if (h[1] < 0) { pagSel--; return true; }
        if (h[1] == COLS) { pagSel++; return true; }
        int i = pagSel * COLS + h[1];
        if (i >= l.size()) return true;
        enviar(boton == 1 ? SublimadoraBolsaPayload.SACAR : SublimadoraBolsaPayload.ELEGIR, pestana == 0 ? 0 : 1, i);
        return true;
    }

    // ── lista de diseños ──
    private List<Integer> disenosDeEstaPrenda() {
        List<Integer> l = new ArrayList<>();
        var todos = be().disenosItem();
        for (int i = 0; i < todos.size(); i++) {
            if (todos.get(i).get(com.modamod.item.ModamodComponents.DISENO_SUBLIMADO) != null) l.add(i);
        }
        return l;
    }

    /** La prenda madre del diseño (a pedido, 2026-10-14: "agregaria en los diseños la prenda madre del diseño"). */
    private static net.minecraft.item.Item madreDe(ItemStack d) {
        var c = d.get(com.modamod.item.ModamodComponents.DISENO_SUBLIMADO);
        var id = c == null ? null : net.minecraft.util.Identifier.tryParse(c.getString("Item"));
        return id == null ? net.minecraft.item.Items.AIR : net.minecraft.registry.Registries.ITEM.get(id);
    }

    private void dibujarDisenos(DrawContext c, int mx, int my) {
        R ay = disp.get("depositoAyuda");
        int ty = this.y + ay.y();
        for (var linea : textRenderer.wrapLines(Text.translatable("modamod.sublimadora.gui.deposito.disenos"), ay.w())) {
            c.drawText(textRenderer, linea, this.x + ay.x(), ty, 0xFF6B5A78, false);
            ty += 10;
        }
        R ur = disp.get("dUltimo");
        var ultimo = be().ultimo();
        c.fill(this.x + ur.x(), this.y + ur.y(), this.x + ur.x() + ur.w(), this.y + ur.y() + ur.h(), 0xFFD2B47F);
        c.fill(this.x + ur.x(), this.y + ur.y(), this.x + ur.x() + 2, this.y + ur.y() + ur.h(), 0xFF7B4FA8);
        String res = ultimo == null ? Text.translatable("modamod.estilista.disenos.ninguno").getString() : resumen(ultimo);
        c.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("modamod.estilista.disenos.ultimo").getString() + " " + res, ur.w() - 8),
                this.x + ur.x() + 5, this.y + ur.y() + 4, ultimo == null ? 0xFF7A6A52 : 0xFF4B2E83, false);
        var idx = disenosDeEstaPrenda();
        int paginas = Math.max(1, (idx.size() + 5) / 6);
        pagDis = Math.max(0, Math.min(pagDis, paginas - 1));
        var todos = be().disenosItem();
        if (idx.isEmpty()) {
            R d0 = disp.get("d0");
            c.drawText(textRenderer, Text.translatable("modamod.sublimadora.gui.diseno.vacio"), this.x + d0.x(), this.y + d0.y() + 4, 0xFF7A6A52, false);
        }
        for (int k = 0; k < 6 && pagDis * 6 + k < idx.size(); k++) {
            R r = disp.get("d" + k);
            ItemStack d = todos.get(idx.get(pagDis * 6 + k));
            int rx = this.x + r.x(), ry = this.y + r.y();
            boolean sobre = mx >= rx && mx < rx + r.w() && my >= ry && my < ry + r.h();
            IconoDiseno.fila(c, textRenderer, rx, ry, r.w(), r.h(), sobre || idx.get(pagDis * 6 + k) == disenoElegido, d, null);
        }
        R pg = disp.get("dPag");
        c.drawText(textRenderer, "<", this.x + pg.x() + 4, this.y + pg.y() + 4, 0xFF4B2E83, false);
        c.drawText(textRenderer, ">", this.x + pg.x() + pg.w() - 10, this.y + pg.y() + 4, 0xFF4B2E83, false);
        c.drawCenteredTextWithShadow(textRenderer, (pagDis + 1) + "/" + paginas, this.x + pg.x() + pg.w() / 2, this.y + pg.y() + 4, EstiloPergamino.TEXTO_CLARO);
    }

    private static String resumen(SublimadoraBlockEntity.DisenoEstampa d) {
        return (d.fijadaFrente() ? "F" : "") + (d.fijadaEspalda() ? "E" : "") + " " + Math.round(d.ajuste().escalaFrente() * 100) + "%";
    }

    private boolean clickDisenos(double mx, double my, int boton) {
        var idx = disenosDeEstaPrenda();
        int paginas = Math.max(1, (idx.size() + 5) / 6);
        R ur = disp.get("dUltimo");
        if (ur != null && mx >= this.x + ur.x() && mx < this.x + ur.x() + ur.w() && my >= this.y + ur.y() && my < this.y + ur.y() + ur.h()) {
            clickBoton(SublimadoraBlockEntity.BTN_CARGAR_ULTIMO);
            return true;
        }
        R pg = disp.get("dPag");
        if (pg != null && my >= this.y + pg.y() && my < this.y + pg.y() + pg.h()) {
            if (mx >= this.x + pg.x() && mx < this.x + pg.x() + 20) { pagDis = Math.max(0, pagDis - 1); return true; }
            if (mx >= this.x + pg.x() + pg.w() - 20 && mx < this.x + pg.x() + pg.w()) { pagDis = Math.min(paginas - 1, pagDis + 1); return true; }
        }
        for (int k = 0; k < 6 && pagDis * 6 + k < idx.size(); k++) {
            R r = disp.get("d" + k);
            if (r != null && mx >= this.x + r.x() && mx < this.x + r.x() + r.w() && my >= this.y + r.y() && my < this.y + r.y() + r.h()) {
                if (boton != 1) disenoElegido = idx.get(pagDis * 6 + k);
                enviar(boton == 1 ? SublimadoraBolsaPayload.SACAR_DISENO : SublimadoraBolsaPayload.CARGAR_DISENO, 0, idx.get(pagDis * 6 + k));
                return true;
            }
        }
        return false;
    }

    // ── mouse ──
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Los clicks propios van ANTES de super (HandledScreen devuelve true casi siempre).
        for (TinturasScreen.BotonChincheta b : btnChinchetas) if (b.visible && b.isMouseOver(mx, my)) return super.mouseClicked(mx, my, button);
        for (ButtonWidget b : btnZona) if (b.visible && b.isMouseOver(mx, my)) return super.mouseClicked(mx, my, button);
        if (button == 0 || button == 1) {
            for (int c = 0; c < 2; c++) {
                R r = disp.get("cara" + c);
                if (r != null && mx >= this.x + r.x() && mx < this.x + r.x() + r.w() && my >= this.y + r.y() && my < this.y + r.y() + r.h()) {
                    if (c != be().getSeleccion().ordinal()) clickBoton(SublimadoraBlockEntity.BTN_ELEGIR_CARA_BASE + c);
                    anguloVista = c == 0 ? 0f : 180f;
                    return true;
                }
            }
            if (pestana <= 1 && clickSelector(mx, my, button)) return true;
            if (pestana == 4 && clickDisenos(mx, my, button)) return true;
            // El ★ Último del panel de la izquierda.
            int px = this.x + SublimadoraScreenHandler.X_DISENOS, py = this.y + SublimadoraScreenHandler.Y_DISENOS;
            if (mx >= px && mx < px + SublimadoraScreenHandler.ANCHO_DISENOS && my >= py && my < py + 13) {
                clickBoton(SublimadoraBlockEntity.BTN_CARGAR_ULTIMO);
                return true;
            }
        }
        if (dentroVisor(mx, my)) {
            arrastrando = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        arrastrando = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (arrastrando) {
            anguloVista = Math.floorMod(Math.round(anguloVista + (float) dx * 1.15f), 360);
            if (button == 1) inclinacionVista = net.minecraft.util.math.MathHelper.clamp(inclinacionVista + (float) dy * 0.8f, -85f, 85f);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (dentroVisor(mx, my)) {
            zoomVista = net.minecraft.util.math.MathHelper.clamp(zoomVista + (float) v * 0.1f, 0.5f, 2.5f);
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    /** Sin esto, escribir una "e" en el nombre del diseño cierra la pantalla. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (txtNombre != null && (txtNombre.keyPressed(keyCode, scanCode, modifiers) || txtNombre.isActive())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── dibujo ──
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        reubicar();
        refrescar();
        super.render(context, mouseX, mouseY, delta);
        for (TinturasScreen.BotonChincheta b : btnChinchetas) if (b.visible) b.render(context, mouseX, mouseY, delta);
        verificarSolapes();
        // Tooltip del ítem de la lista de fotos / máscaras bajo el mouse.
        int[] h = pestana <= 1 ? selectorBajo(mouseX, mouseY) : null;
        if (h != null && h[1] >= 0 && h[1] < COLS) {
            var l = listaSel();
            int i = pagSel * COLS + h[1];
            if (i < l.size()) context.drawItemTooltip(textRenderer, l.get(i), mouseX, mouseY);
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private void verificarSolapes() {
        List<net.minecraft.client.gui.widget.ClickableWidget> v = new ArrayList<>();
        for (var c : this.children()) {
            if (c instanceof net.minecraft.client.gui.widget.ClickableWidget cw && cw.visible
                    && cw.getX() >= this.x + X && cw.getY() >= this.y + 20 && cw.getY() <= this.y + 300) v.add(cw);
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
            if (sb.length() > 0) org.slf4j.LoggerFactory.getLogger("modamod").warn("Sublimadora: controles superpuestos en la pestaña {}: {}", pestana, sb);
        }
    }

    private void marcoGrande(DrawContext c, int sx, int sy) {
        c.fill(sx - 13, sy - 13, sx + 29, sy + 29, 0xFF2A180C);
        c.fill(sx - 12, sy - 12, sx + 28, sy + 28, 0xFF6B5A78);
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int x0 = this.x, y0 = this.y, wd = backgroundWidth, h = backgroundHeight;
        var tema = EstiloPergamino.tema();
        c.fill(x0 - 2, y0 - 2, x0 + wd + 2, y0 + h + 2, 0xFF2A180C);
        c.fill(x0 - 1, y0 - 1, x0 + wd + 1, y0 + h + 1, tema.claro);
        c.fill(x0, y0, x0 + wd, y0 + h, 0xFFE9D8B4);
        for (int i = 0; i < 9; i++) {
            c.fill(x0, y0 + i, x0 + 9 - i, y0 + i + 1, tema.claro);
            c.fill(x0 + wd - 9 + i, y0, x0 + wd, y0 + i + 1, tema.claro);
            c.fill(x0, y0 + h - 1 - i, x0 + 9 - i, y0 + h - i, tema.claro);
            c.fill(x0 + wd - 9 + i, y0 + h - 1 - i, x0 + wd, y0 + h - i, tema.claro);
        }
        for (int px = x0 + 8; px < x0 + wd - 8; px += 4) c.fill(px, y0 + 306, px + 2, y0 + 307, tema.oscuro);
        for (int py = y0 + 24; py < y0 + 302; py += 4) c.fill(x0 + X - 3, py, x0 + X - 2, py + 2, tema.oscuro);
        for (int py = y0 + 312; py < y0 + 400; py += 4) {
            c.fill(x0 + 116, py, x0 + 117, py + 2, tema.oscuro);
            c.fill(x0 + 284, py, x0 + 285, py + 2, tema.oscuro);
        }
        dibujarVisor(c);
        // Cabecera: Entrada y Salida grandes con la barra de progreso en el medio.
        marcoGrande(c, x0 + SublimadoraScreenHandler.X_ENTRADA, y0 + SublimadoraScreenHandler.Y_IO);
        marcoGrande(c, x0 + SublimadoraScreenHandler.X_SALIDA, y0 + SublimadoraScreenHandler.Y_IO);
        int bx = x0 + SublimadoraScreenHandler.X_ENTRADA + 104, by = y0 + SublimadoraScreenHandler.Y_IO + 5, bw = 92;
        c.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF2A180C);
        c.fill(bx, by, bx + bw, by + 6, 0xFF111111);
        float nivel = be().getEstado() == SublimadoraBlockEntity.Estado.LISTO ? 1f : be().getProgreso();
        c.fill(bx, by, bx + (int) (bw * nivel), by + 6, tema.claro);
        dibujarTanques(c);
        // Marco de cada casillero visible chico (el depósito y el inventario).
        for (var s : handler.slots) {
            if (s.x < -1000 || !s.isEnabled() || SublimadoraScreenHandler.esSlotGrande(s.x, s.y)) continue;
            c.fill(x0 + s.x - 1, y0 + s.y - 1, x0 + s.x + 17, y0 + s.y + 17, 0xFF2A180C);
            c.fill(x0 + s.x, y0 + s.y, x0 + s.x + 16, y0 + s.y + 16, 0xFF8B7355);
        }
        dibujarCaras(c, mouseX, mouseY);
        if (pestana <= 1) {
            dibujarSelector(c, mouseX, mouseY);
        } else if (pestana == 3) {
            dibujarBolsas(c);
        } else if (pestana == 4 && disp.get("dUltimo") != null) {
            dibujarDisenos(c, mouseX, mouseY);
        }
        dibujarPanelIzquierdo(c);
    }

    private void dibujarCaras(DrawContext c, int mx, int my) {
        SublimadoraBlockEntity be = be();
        for (int cara = 0; cara < 2; cara++) {
            R r = disp.get("cara" + cara);
            if (r == null) continue;
            boolean sel = be.getSeleccion().ordinal() == cara;
            int rx = this.x + r.x(), ry = this.y + r.y();
            c.fill(rx - 1, ry - 1, rx + r.w() + 1, ry + r.h() + 1, sel ? 0xFFFFD24C : 0xFF2A180C);
            c.fill(rx, ry, rx + r.w(), ry + r.h(), sel ? 0xFF6B5A78 : 0xFF8B7355);
            ItemStack foto = be.getStack(cara);
            if (!foto.isEmpty()) c.drawItem(foto, rx + 3, ry + 4);
            Estampa.Cara ec = Estampa.Cara.values()[cara];
            c.drawText(textRenderer, Text.translatable("modamod.sublimadora.rotulo." + ec.clave), rx + 22, ry + 3, 0xFFFFFFFF, false);
            c.drawText(textRenderer, Text.translatable(foto.isEmpty() ? "modamod.sublimadora.gui.cara.sin_foto" : "modamod.sublimadora.gui.cara.con_foto"),
                    rx + 22, ry + 13, 0xFFD6C9AE, false);
        }
    }

    private void dibujarBolsas(DrawContext c) {
        R ay = disp.get("depositoAyuda");
        int ty = this.y + ay.y();
        for (var linea : textRenderer.wrapLines(Text.translatable("modamod.sublimadora.gui.deposito"), ay.w())) {
            c.drawText(textRenderer, linea, this.x + ay.x(), ty, 0xFF6B5A78, false);
            ty += 10;
        }
        R t = disp.get("bolsaTexto");
        c.drawText(textRenderer, Text.translatable("modamod.sublimadora.gui.bolsa.fotos").append(": " + be().fotosBolsa().size() + "/" + SublimadoraBlockEntity.FOTOS_MAX),
                this.x + t.x(), this.y + t.y(), EstiloPergamino.TEXTO, false);
        c.drawText(textRenderer, Text.translatable("modamod.sublimadora.gui.bolsa.mascaras").append(": " + be().mascarasBolsa().size() + "/" + SublimadoraBlockEntity.MASCARAS_MAX),
                this.x + t.x(), this.y + t.y() + 11, EstiloPergamino.TEXTO, false);
    }

    private void dibujarPanelIzquierdo(DrawContext c) {
        IconoDiseno.marcoBlanco(c, this.x + 9, this.y + 345, this.handler.slots.get(this.handler.slots.size() - 1).hasStack());
        int px = this.x + SublimadoraScreenHandler.X_DISENOS, py = this.y + SublimadoraScreenHandler.Y_DISENOS;
        int pw = SublimadoraScreenHandler.ANCHO_DISENOS;
        var ultimo = be().ultimo();
        c.fill(px, py, px + pw, py + 13, 0xFFD2B47F);
        c.fill(px, py, px + 2, py + 13, 0xFF7B4FA8);
        String res = ultimo == null ? Text.translatable("modamod.estilista.disenos.ninguno").getString() : resumen(ultimo);
        c.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("modamod.estilista.disenos.ultimo").getString() + " " + res, pw - 6),
                px + 4, py + 3, ultimo == null ? 0xFF7A6A52 : 0xFF4B2E83, false);
        c.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("modamod.sublimadora.gui.diseno.hint").getString(), pw), px, py + 17, 0xFF7A6A52, false);
    }

    /** Colores de las barras: C, M, Y, K y el papel. */
    private static final int[] COLOR_TANQUE = { 0xFF1FB3D6, 0xFFD6287F, 0xFFE8C21E, 0xFF2A2A2A, 0xFFF3EEDF };
    private static final String[] LETRA_TANQUE = { "C", "M", "Y", "K" };

    /** Abajo a la derecha: un tanque por tinta y uno de papel (letra, barra y "n/64"). */
    private void dibujarTanques(DrawContext c) {
        SublimadoraBlockEntity be = be();
        for (int i = 0; i < 5; i++) {
            int tx = this.x + 296 + (i % 3) * 86, ty = this.y + 364 + (i / 3) * 16;
            int cantidad = i < 4 ? Math.round(be.getTinta(i) * SublimadoraBlockEntity.CARGA_MAXIMA) : be.getPapel();
            Text letra = i < 4 ? Text.literal(LETRA_TANQUE[i]) : Text.translatable("modamod.sublimadora.tanque.papel");
            c.drawText(textRenderer, letra, tx, ty, EstiloPergamino.TEXTO, false);
            int bx0 = tx + 14, bx1 = tx + 56;
            c.fill(bx0 - 1, ty - 1, bx1 + 1, ty + 9, 0xFF2A180C);
            c.fill(bx0, ty, bx1, ty + 8, 0xFF8A7556);
            int lleno = (bx1 - bx0) * cantidad / SublimadoraBlockEntity.CARGA_MAXIMA;
            if (lleno > 0) c.fill(bx0, ty, bx0 + lleno, ty + 8, COLOR_TANQUE[i]);
            c.drawText(textRenderer, Integer.toString(cantidad), bx1 + 4, ty, cantidad == 0 ? 0xFFB02A1A : EstiloPergamino.TEXTO, false);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        EstiloPergamino.titulo(context, this.textRenderer, this.title, this.titleX, this.titleY);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX,
                this.playerInventoryTitleY, EstiloPergamino.TEXTO, false);
        SublimadoraBlockEntity be = be();
        if (disp.get("capasTitulo") != null) {
            R r = disp.get("capasTitulo");
            context.drawText(textRenderer, Text.translatable("modamod.sublimadora.gui.capas_titulo", be.capas().size()), r.x(), r.y(), EstiloPergamino.TEXTO, false);
            int elegida = be.capaElegida();
            if (elegida >= 0 && elegida < com.modamod.sublimadora.CapaEstampa.MAXIMO && disp.get("cp" + elegida) != null) {
                R b = disp.get("cp" + elegida);
                context.drawBorder(b.x() - 1, b.y() - 1, b.w() + 2, b.h() + 2, 0xFFFFD24C);
            }
        }
        if (disp.get("estadoTexto") != null) {
            R r = disp.get("estadoTexto");
            Text estado = Text.translatable(be.caraFijada(be.getSeleccion()) ? "modamod.tinturas.estado.fijada" : "modamod.tinturas.estado.borrador");
            context.drawText(textRenderer, Text.translatable("modamod.tinturas.editando",
                    Text.translatable("modamod.sublimadora.rotulo." + be.getSeleccion().clave), estado), r.x(), r.y(), EstiloPergamino.TEXTO, false);
        }
        // Qué sigue, abajo del visor.
        for (var linea : textRenderer.wrapLines(hint(), PX2 - PX1 - 12)) {
            context.drawText(textRenderer, linea, PX1 + 6, PY2 - 20, 0xFFE8DCC8, true);
            break;
        }
    }

    private Text hint() {
        SublimadoraBlockEntity be = be();
        if (be.getEstado() == SublimadoraBlockEntity.Estado.PRENSANDO) return Text.translatable("modamod.sublimadora.hint.prensando");
        if (!be.getSalida().isEmpty()) return Text.translatable("modamod.sublimadora.hint.listo");
        if (be.hayMascara() && be.getFotoCargada(be.getSeleccion()) != null) return Text.translatable("modamod.sublimadora.hint.mascara");
        if (be.getFotoCargada(Estampa.Cara.FRENTE) == null && be.getFotoCargada(Estampa.Cara.ESPALDA) == null && be.capas().isEmpty()) {
            return Text.translatable("modamod.sublimadora.gui.ayuda.foto");
        }
        if (!be.hayFijadas() && be.capas().isEmpty()) return Text.translatable("modamod.sublimadora.hint.ajustar");
        if (be.getRemera().isEmpty()) return Text.translatable("modamod.sublimadora.hint.sin_prenda");
        return Text.translatable("modamod.sublimadora.hint.fijado");
    }

    // ── Entrada/Salida grandes ──
    @Override
    protected void drawSlot(DrawContext context, net.minecraft.screen.slot.Slot slot) {
        if (!SublimadoraScreenHandler.esSlotGrande(slot.x, slot.y)) {
            super.drawSlot(context, slot);
            return;
        }
        float e = SublimadoraScreenHandler.ESCALA_IO;
        context.getMatrices().push();
        context.getMatrices().translate(slot.x + 8, slot.y + 8, 0);
        context.getMatrices().scale(e, e, 1f);
        context.getMatrices().translate(-(slot.x + 8), -(slot.y + 8), 0);
        super.drawSlot(context, slot);
        context.getMatrices().pop();
    }

    @Override
    protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        if (width == 16 && height == 16 && SublimadoraScreenHandler.esSlotGrande(x, y)) {
            return super.isPointWithinBounds(x - 12, y - 12, 40, 40, pointX, pointY);
        }
        return super.isPointWithinBounds(x, y, width, height, pointX, pointY);
    }
}
