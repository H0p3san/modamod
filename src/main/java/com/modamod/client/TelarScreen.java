package com.modamod.client;

import com.modamod.telar.TelarBlockEntity;
import com.modamod.telar.TelarPrenda;
import com.modamod.telar.TelarScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * El Telar automático (2026-10-07, "produzca prendas basicas a base de lana e hilo" + "se pueda seleccionar varias
 * prendas y vaya alternando, y se le pueda configurar que cantidad de prenda se quiere que saque"): fondo, marcos y
 * barra de progreso dibujados por código (sin texturas), con el acento granate del Telar (2026-10-07, "el color del telar es granate"). A la izquierda
 * lana e hilo, en el medio las seis prendas (se tildan varias) y el lote, a la derecha la prenda terminada; abajo el
 * inventario.
 */
public class TelarScreen extends net.minecraft.client.gui.screen.ingame.HandledScreen<TelarScreenHandler> {

    // Layout de las máquinas hermanas (2026-10-12): izquierda la prenda grande, centro las prendas a tejer y la franja
    // insumos → barra → Salida, derecha Línea y lote.
    private static final int LADO_ICONO = 36, PASO_ICONO = 46, POR_FILA = 5, Y_BOTONES = 40;   // íconos grandes (2026-10-14)
    private static final int X_BOTONES = TelarScreenHandler.M_MEDIO + (TelarScreenHandler.M_MEDIO_ANCHO - (POR_FILA * PASO_ICONO - 6)) / 2;
    private static final int X_TEJER = 372, X_BARRA = 440, Y_BARRA = TelarScreenHandler.Y_IO + 5, ANCHO_BARRA = 80;
    private static final int X_VISTA1 = 8, Y_VISTA1 = 18, X_VISTA2 = 94, Y_VISTA2 = 298;
    private static final int X_DER = TelarScreenHandler.M_DERECHA, ANCHO_DER = 173, Y_LOTE = 76;
    private static final int Y_SIGUE = 132;
    private final List<ButtonWidget> botones = new ArrayList<>();
    /** Giro del visor (grados), se arrastra con el mouse. */
    private float anguloVista = 0f;

    /** La prenda que se teje, puesta en la figura del jugador y girable (2026-10-14, "el visor puede mostrar un modelo 3d de la prenda"). */
    private void dibujarPrenda3d(DrawContext c, ItemStack prenda, int x1, int y1, int x2, int y2) {
        var jugador = net.minecraft.client.MinecraftClient.getInstance().player;
        if (jugador == null) return;
        boolean calzado = prenda.getItem() instanceof com.modamod.item.BorcegosItem;
        com.modamod.render.GarmentFeatureRenderer.previewOverride = calzado ? List.of() : List.of(prenda);
        com.modamod.render.GarmentFeatureRenderer.calzadoOverride = calzado ? prenda : null;
        try {
            PreviewJugador.dibujar(c, jugador, x1, y1, x2, y2, 100, anguloVista, (y1 + y2) / 2f);
        } finally {
            com.modamod.render.GarmentFeatureRenderer.previewOverride = null;
            com.modamod.render.GarmentFeatureRenderer.calzadoOverride = null;
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (mx >= this.x + X_VISTA1 && mx < this.x + X_VISTA2 && my >= this.y + Y_VISTA1 && my < this.y + Y_VISTA2) {
            anguloVista += (float) dx * 1.5f;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    public TelarScreen(TelarScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = TelarScreenHandler.ANCHO;
        this.backgroundHeight = TelarScreenHandler.ALTO;
        this.titleX = TelarScreenHandler.M_MEDIO;
        this.titleY = 9;
        this.playerInventoryTitleX = TelarScreenHandler.M_MEDIO;
        this.playerInventoryTitleY = 314;
    }

    @Override
    protected void init() {
        super.init();
        EstiloPergamino.usarTema(EstiloPergamino.Tema.GRANATE);
        botones.clear();
        TelarPrenda[] prendas = TelarPrenda.values();
        int pos = 0;
        for (int i = 0; i < prendas.length; i++) {
            if (!prendas[i].enTelar()) continue;
            int bx = this.x + X_BOTONES + (pos % POR_FILA) * PASO_ICONO, by = this.y + Y_BOTONES + (pos / POR_FILA) * PASO_ICONO;
            pos++;
            int idx = i;
            BotonPrenda b = new BotonPrenda(bx, by, prendas[i],
                    () -> handler.host.elegida(idx),
                    btn -> this.client.interactionManager.clickButton(this.handler.syncId, TelarBlockEntity.BTN_PRENDA_BASE + idx));
            this.addDrawableChild(b);
        }
        // El lote: −10 − + +10 y reiniciar, en la columna derecha.
        int ly = this.y + Y_LOTE;
        boton("-10", this.x + X_DER, ly, 28, 16, TelarBlockEntity.BTN_LOTE_MENOS_10);
        boton("-", this.x + X_DER + 30, ly, 18, 16, TelarBlockEntity.BTN_LOTE_MENOS);
        boton("+", this.x + X_DER + 50, ly, 18, 16, TelarBlockEntity.BTN_LOTE_MAS);
        boton("+10", this.x + X_DER + 70, ly, 28, 16, TelarBlockEntity.BTN_LOTE_MAS_10);
        this.addDrawableChild(new EstiloPergamino.BotonLinea(this.x + 452, this.y + 3, 96, 16,
                () -> handler.host.linea(),
                b -> this.client.interactionManager.clickButton(this.handler.syncId, TelarBlockEntity.BTN_LINEA)));
        ButtonWidget reiniciar = boton("↻", this.x + X_DER + 100, ly, 18, 16, TelarBlockEntity.BTN_LOTE_REINICIAR);
        reiniciar.setTooltip(Tooltip.of(Text.translatable("modamod.telar.tooltip.reiniciar")));
        // Tejer a mano (2026-10-17, "falta un boton para arrancar el tejido a mano" → "como en las otras maquinas, prioricemos
        // identidad de gui"): en la franja de la cabecera, entre los insumos y la barra, igual que Teñir/Aplicar/Modelar (60x16).
        ButtonWidget tejer = new EstiloPergamino.BotonPergamino(this.x + X_TEJER, this.y + TelarScreenHandler.Y_IO, 60, 16,
                Text.translatable("modamod.telar.boton.tejer"),
                btn -> this.client.interactionManager.clickButton(this.handler.syncId, TelarBlockEntity.BTN_TEJER));
        tejer.setTooltip(Tooltip.of(Text.translatable("modamod.telar.tooltip.tejer")));
        this.addDrawableChild(tejer);
    }

    private ButtonWidget boton(String texto, int bx, int by, int w, int h, int id) {
        ButtonWidget b = new EstiloPergamino.BotonPergamino(bx, by, w, h, Text.literal(texto),
                btn -> this.client.interactionManager.clickButton(this.handler.syncId, id));
        this.addDrawableChild(b);
        return b;
    }

    /** Un ícono de prenda que se tilda: marco claro si está elegida, tooltip con el nombre y el costo. */
    private static final class BotonPrenda extends ButtonWidget {
        private final TelarPrenda prenda;
        private final java.util.function.BooleanSupplier elegida;
        private ItemStack icono;

        BotonPrenda(int x, int y, TelarPrenda prenda, java.util.function.BooleanSupplier elegida, PressAction accion) {
            super(x, y, LADO_ICONO, LADO_ICONO, Text.empty(), accion, DEFAULT_NARRATION_SUPPLIER);
            this.prenda = prenda;
            this.elegida = elegida;
            Text nombre = Text.translatable("modamod.telar.prenda." + prenda.clave());
            setTooltip(Tooltip.of(Text.empty().append(nombre).append("\n")
                    .append(Text.translatable("modamod.telar.tooltip.prenda", prenda.lana, prenda.hilo, prenda.cuero))));
        }

        @Override
        protected void renderWidget(DrawContext c, int mx, int my, float delta) {
            if (icono == null) icono = prenda.crear(0xFFFFFF);
            boolean on = elegida.getAsBoolean();
            int x0 = getX(), y0 = getY();
            c.fill(x0 - 1, y0 - 1, x0 + width + 1, y0 + height + 1, on ? EstiloPergamino.tema().claro : 0xFF2A180C);
            c.fill(x0, y0, x0 + width, y0 + height, isHovered() ? 0xFFB59B6A : on ? 0xFFD9C08C : 0xFF8B7355);
            c.getMatrices().push();
            c.getMatrices().translate(x0 + (width - 32) / 2f, y0 + (height - 32) / 2f, 0);
            c.getMatrices().scale(2f, 2f, 1f);
            c.drawItem(icono, 0, 0);
            c.getMatrices().pop();
            TinturasScreen.dibujarChincheta(c, x0 + width - 11, y0 - 1, on, TinturasScreen.CHINCHETA_GRANATE);   // chincheta clavada = tildada
        }
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mx, int my) {
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
        // Separadores de puntos: entre las tres columnas y sobre el inventario.
        for (int py = y0 + 18; py < y0 + 300; py += 4) {
            c.fill(x0 + 114, py, x0 + 115, py + 2, tema.oscuro);
            c.fill(x0 + TelarScreenHandler.M_DERECHA - 8, py, x0 + TelarScreenHandler.M_DERECHA - 7, py + 2, tema.oscuro);
        }
        for (int px = x0 + 8; px < x0 + w - 8; px += 4) c.fill(px, y0 + 304, px + 2, y0 + 305, tema.oscuro);
        // Columna izquierda: la prenda grande (la tejida, o la que sigue) sobre un fondo oscuro.
        TelarBlockEntity be = handler.host;
        c.fill(x0 + X_VISTA1 - 1, y0 + Y_VISTA1 - 1, x0 + X_VISTA2 + 1, y0 + Y_VISTA2 + 1, 0xFF2A180C);
        c.fill(x0 + X_VISTA1, y0 + Y_VISTA1, x0 + X_VISTA2, y0 + Y_VISTA2, 0xFF4A3828);
        ItemStack vista = be.vistaPrevia();
        if (!vista.isEmpty()) {
            dibujarPrenda3d(c, vista, x0 + X_VISTA1, y0 + Y_VISTA1, x0 + X_VISTA2, y0 + Y_VISTA2);
            c.drawCenteredTextWithShadow(textRenderer, vista.getName(), x0 + (X_VISTA1 + X_VISTA2) / 2, y0 + Y_VISTA2 - 14, 0xFFFFFFFF);
        }
        // Marcos de los slots.
        marco(c, x0 + TelarScreenHandler.X_LANA, y0 + TelarScreenHandler.Y_LANA);
        marco(c, x0 + TelarScreenHandler.X_HILO, y0 + TelarScreenHandler.Y_HILO);
        marco(c, x0 + TelarScreenHandler.X_CUERO, y0 + TelarScreenHandler.Y_CUERO);
        marco(c, x0 + TelarScreenHandler.X_SALIDA, y0 + TelarScreenHandler.Y_SALIDA);
        int yi = y0 + TelarScreenHandler.Y_INV, xi = x0 + TelarScreenHandler.X_INV;
        for (int f = 0; f < 3; f++) for (int col = 0; col < 9; col++) marco(c, xi + col * 18, yi + f * 18);
        for (int col = 0; col < 9; col++) marco(c, xi + col * 18, yi + 58);
        // Rótulos de los insumos (arriba de cada slot, abreviados al ancho de 22 px).
        rotulo(c, "modamod.telar.lana", x0 + TelarScreenHandler.X_LANA + 8, y0 + TelarScreenHandler.Y_LANA - 10);
        rotulo(c, "modamod.telar.hilo", x0 + TelarScreenHandler.X_HILO + 8, y0 + TelarScreenHandler.Y_HILO - 10);
        rotulo(c, "modamod.telar.cuero", x0 + TelarScreenHandler.X_CUERO + 8, y0 + TelarScreenHandler.Y_CUERO - 10);
        // Barra de progreso de la prenda en curso.
        float nivel = switch (be.estado()) {
            case PROCESANDO -> be.progreso() / (float) be.duracion();
            case LISTO -> 1f;
            default -> 0f;
        };
        int bx = x0 + X_BARRA, by = y0 + Y_BARRA, bw = ANCHO_BARRA;
        c.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF2A180C);
        c.fill(bx, by, bx + bw, by + 6, 0xFF111111);
        c.fill(bx, by, bx + (int) (bw * nivel), by + 6, 0xFFB3283F);
        // Columna derecha: el lote.
        c.drawText(textRenderer, Text.translatable("modamod.telar.lote"), x0 + X_DER, y0 + Y_LOTE - 12, EstiloPergamino.TEXTO, false);
        Text lote = be.lote() == 0 ? Text.translatable("modamod.telar.lote_sin_limite")
                : Text.translatable("modamod.telar.lote_n", be.lote(), be.restantes());
        linea(c, lote, x0 + X_DER, y0 + Y_LOTE + 24, ANCHO_DER, EstiloPergamino.TEXTO);
        // Centro, bajo las prendas: la que sigue y si alcanzan los insumos.
        int v = be.prendaVista();
        Text sigue = v < 0 ? Text.translatable("modamod.telar.nada_tildado")
                : Text.translatable("modamod.telar.sigue", Text.translatable("modamod.telar.prenda." + TelarPrenda.values()[v].clave()),
                        TelarPrenda.values()[v].lana, TelarPrenda.values()[v].hilo, TelarPrenda.values()[v].cuero);
        int xc = x0 + TelarScreenHandler.M_MEDIO;
        linea(c, sigue, xc, y0 + Y_SIGUE, TelarScreenHandler.M_MEDIO_ANCHO, EstiloPergamino.TEXTO);
        if (be.estado() == TelarBlockEntity.Estado.REPOSO && v >= 0) {
            TelarPrenda p = TelarPrenda.values()[v];
            int faltaLana = Math.max(0, p.lana - be.lana());
            int faltaHilo = Math.max(0, p.hilo - be.hilo());
            int faltaCuero = Math.max(0, p.cuero - be.cuero());
            boolean lista = be.creativa() || (faltaLana == 0 && faltaHilo == 0 && faltaCuero == 0);
            Text estado = lista
                    ? Text.translatable("modamod.telar.estado.listo")
                    : Text.translatable("modamod.telar.estado.falta", faltaLana, faltaHilo, faltaCuero);
            linea(c, estado, xc, y0 + Y_SIGUE + 12, TelarScreenHandler.M_MEDIO_ANCHO, lista ? 0xFF3F7A38 : 0xFF8A2E2E);
        }
    }

    @Override
    protected void drawForeground(DrawContext c, int mx, int my) {
        EstiloPergamino.titulo(c, this.textRenderer, this.title, this.titleX, this.titleY);
        c.drawText(textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, EstiloPergamino.TEXTO, false);
    }

    private void rotulo(DrawContext c, String clave, int cx, int ty) {
        c.drawCenteredTextWithShadow(textRenderer, net.minecraft.util.Language.getInstance().reorder(textRenderer.trimToWidth(Text.translatable(clave), 21)), cx, ty, 0xFF3A2A18);
    }

    /** Una línea de texto recortada al ancho de su columna. */
    private void linea(DrawContext c, Text t, int tx, int ty, int ancho, int color) {
        c.drawText(textRenderer, net.minecraft.util.Language.getInstance().reorder(textRenderer.trimToWidth(t, ancho)),
                tx, ty, color, false);
    }

    private void marco(DrawContext c, int sx, int sy) {
        c.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF2A180C);
        c.fill(sx, sy, sx + 16, sy + 16, 0xFF8B7355);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c, mx, my, delta);
        super.render(c, mx, my, delta);
        drawMouseoverTooltip(c, mx, my);
    }
}
