package com.modamod.client;

import com.modamod.maniqui.ManiquiBlockEntity;
import com.modamod.maniqui.ManiquiScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Maniquí (2026-09-30): la grilla de 16 slots en el mismo lugar que el
 * Guardarropas (reusa su fondo), más Girar/Detener e "Intercambiar conmigo"
 * (la ropa del maniquí pasa a Trinkets y la tuya al maniquí). El preview de
 * la izquierda te muestra con la ropa del maniquí puesta — la figura real
 * ya se ve en el mundo.
 */
public class ManiquiScreen extends HandledScreen<ManiquiScreenHandler> {

    private static final Identifier TEXTURE = Identifier.of("modamod", "textures/gui/container/maniqui.png");
    private static final int ANCHO = ManiquiScreenHandler.ANCHO;
    private static final int ALTO = ManiquiScreenHandler.ALTO;
    private static final int M_MEDIO = ManiquiScreenHandler.M_MEDIO;

    private static final int PREVIEW_X1_LOCAL = 8, PREVIEW_Y1_LOCAL = 18, PREVIEW_X2_LOCAL = 160, PREVIEW_Y2_LOCAL = 296;
    /** Columna derecha: sliders de la pose, skin, emote y botones. */
    private static final int X_DER = 398, ANCHO_DER = 154;
    private float anguloVista = 0f;
    private ButtonWidget btnVista;
    private ButtonWidget btnGirar;

    public ManiquiScreen(ManiquiScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = ANCHO;
        this.backgroundHeight = ALTO;
        this.titleX = 172;
        this.titleY = 6;
        this.playerInventoryTitleX = 172;
        this.playerInventoryTitleY = 311;
    }

    @Override
    protected void init() {
        // Estilo del mod con el metal del mueble (2026-09-30, "armame la gui...
        // con el estilo del mod y los colores de sus acentos metalicos").
        EstiloPergamino.usarTema(EstiloPergamino.Tema.AZUL);
        super.init();

        btnVista = new EstiloPergamino.BotonPergamino(this.x + PREVIEW_X1_LOCAL, this.y + 300, PREVIEW_X2_LOCAL - PREVIEW_X1_LOCAL, 16, Text.literal(""), b -> anguloVista = Math.floorMod(Math.round(anguloVista) + 90, 360));
        this.addDrawableChild(btnVista);

        // Columna derecha (2026-10-19): sliders de la pose, skin, emote y los botones, en pares.
        int dx = this.x + X_DER, mitad = (ANCHO_DER - 4) / 2;
        for (int i = 0; i < sliders.length; i++) {
            sliders[i] = new SliderPose(dx, this.y + 18 + i * 13, ANCHO_DER, 12, i);
            this.addDrawableChild(sliders[i]);
        }
        campoSkin = new net.minecraft.client.gui.widget.TextFieldWidget(this.textRenderer, dx, this.y + 152, ANCHO_DER - 32, 14,
                Text.translatable("modamod.maniqui.skin.campo"));
        campoSkin.setMaxLength(16);
        campoSkin.setPlaceholder(Text.translatable("modamod.maniqui.skin.campo"));
        this.addDrawableChild(campoSkin);
        btnSkin = new EstiloPergamino.BotonPergamino(dx + ANCHO_DER - 30, this.y + 152, 30, 14, Text.translatable("modamod.maniqui.skin.ir"), b -> {
            String nombre = campoSkin.getText().trim();
            if (!nombre.isEmpty()) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new com.modamod.maniqui.ElegirSkinManiquiPayload(handler.be.getPos(), nombre));
            }
        });
        this.addDrawableChild(btnSkin);
        // Emote de Emotecraft (2026-10-04): solo si el mod está instalado.
        if (EmotecraftCompat.disponible()) {
            btnEmote = new EstiloPergamino.BotonPergamino(dx, this.y + 168, ANCHO_DER, 14, Text.literal(""), b -> siguienteEmote());
            this.addDrawableChild(btnEmote);
        }
        int by = this.y + 190;
        btnGirar = par(dx, by, mitad, ManiquiBlockEntity.BTN_GIRAR);
        btnIntercambiar = new EstiloPergamino.BotonPergamino(dx + mitad + 4, by, mitad, 16, Text.translatable("modamod.maniqui.intercambiar"), b -> clickBoton(ManiquiBlockEntity.BTN_INTERCAMBIAR));
        this.addDrawableChild(btnIntercambiar);
        btnPose = par(dx, by + 18, ANCHO_DER, ManiquiBlockEntity.BTN_POSE);
        btnFigura = par(dx, by + 36, ANCHO_DER, ManiquiBlockEntity.BTN_FIGURA);
        btnBusto = par(dx, by + 54, mitad, ManiquiBlockEntity.BTN_BUSTO);
        btnCandado = par(dx + mitad + 4, by + 54, mitad, ManiquiBlockEntity.BTN_CANDADO);
        // Ropa (2026-10-19): abrir la chaqueta, subir la capucha y el twirl en loop.
        btnFrente = par(dx, by + 72, mitad, ManiquiBlockEntity.BTN_FRENTE);
        btnCapucha = par(dx + mitad + 4, by + 72, mitad, ManiquiBlockEntity.BTN_CAPUCHA);
        btnTwirl = par(dx, by + 90, ANCHO_DER, ManiquiBlockEntity.BTN_TWIRL);
    }

    private ButtonWidget par(int bx, int by, int ancho, int id) {
        ButtonWidget b = new EstiloPergamino.BotonPergamino(bx, by, ancho, 16, Text.literal(""), t -> clickBoton(id));
        this.addDrawableChild(b);
        return b;
    }

    private ButtonWidget btnEmote;
    private ButtonWidget btnFrente, btnCapucha, btnTwirl;

    /** Pasa por ninguno → cada emote cargado → ninguno. */
    private void siguienteEmote() {
        var lista = EmotecraftCompat.lista();
        java.util.UUID actual = handler.be.emote();
        int i = actual == null ? -1 : lista.indexOf(actual);
        String siguiente = i + 1 < lista.size() ? lista.get(i + 1).toString() : "";
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.modamod.maniqui.ElegirEmoteManiquiPayload(handler.be.getPos(), siguiente));
    }

    private net.minecraft.client.gui.widget.TextFieldWidget campoSkin;

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Con el cursor en el nombre del jugador, las teclas son del texto (la E no cierra la pantalla; 2026-10-08).
        if (campoSkin != null && campoSkin.visible && (campoSkin.keyPressed(keyCode, scanCode, modifiers) || campoSkin.isActive())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    private ButtonWidget btnSkin;
    private ButtonWidget btnCandado;

    private ButtonWidget btnIntercambiar;
    private ButtonWidget btnPose;
    private ButtonWidget btnBusto;
    private ButtonWidget btnFigura;
    private final SliderPose[] sliders = new SliderPose[com.modamod.maniqui.PoseManiqui.ANGULOS];
    /** Mientras se arrastra un slider no se pisa con lo que llega del servidor. */
    private boolean arrastrando = false;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) arrastrando = true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) arrastrando = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * Un ángulo de la pose libre (2026-09-30, "pose libre por partes"): manda
     * el valor en grados por {@code PoseManiquiPayload}; el maniquí pasa a
     * pose Libre. Rango por eje en {@code PoseManiqui.RANGO}.
     */
    private final class SliderPose extends net.minecraft.client.gui.widget.SliderWidget {
        private final int indice;

        SliderPose(int x, int y, int ancho, int alto, int indice) {
            super(x, y, ancho, alto, Text.empty(), 0.5);
            this.indice = indice;
            this.value = aValor(handler.be.angulo(indice));
            updateMessage();
        }

        private float[] rango() { return com.modamod.maniqui.PoseManiqui.RANGO[indice]; }

        private double aValor(float grados) {
            float[] r = rango();
            return (grados - r[0]) / (r[1] - r[0]);
        }

        private float grados() {
            float[] r = rango();
            return Math.round(r[0] + (float) value * (r[1] - r[0]));
        }

        void refrescar() {
            double v = aValor(handler.be.angulo(indice));
            if (Math.abs(v - value) > 0.001) {
                value = v;
                updateMessage();
            }
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("modamod.maniqui.eje." + indice, (int) grados()));
        }

        /** Riel de madera oscura con filo del metal del tema y mango de madera (estilo del mod, 2026-09-30). */
        @Override
        public void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            int x0 = getX(), y0 = getY(), w = getWidth(), h = getHeight();
            c.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0xFF2A180C);
            c.fill(x0, y0, x0 + w, y0 + h, 0xFF5A4028);
            c.fill(x0, y0 + h - 1, x0 + w, y0 + h, EstiloPergamino.tema().claro);
            int mango = x0 + (int) (value * (w - 8));
            EstiloPergamino.fondoBoton(c, mango, y0, 8, h, isHovered(), active);
            var fuente = MinecraftClient.getInstance().textRenderer;
            Text m = getMessage();
            int tx = x0 + (w - fuente.getWidth(m)) / 2, ty = y0 + (h - 8) / 2;
            c.drawText(fuente, m, tx + 1, ty + 1, 0xFF2A180C, false);
            c.drawText(fuente, m, tx, ty, EstiloPergamino.TEXTO_CLARO, false);
        }

        @Override
        protected void applyValue() {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.modamod.maniqui.PoseManiquiPayload(handler.be.getPos(), indice, grados()));
        }
    }

    private Text nombreFigura() {
        var be = handler.be;
        String pre = be.predefinida();
        if (pre != null) return Text.translatable("modamod.maniqui.figura.pre", Text.translatable("modamod.maniqui.skin." + pre));
        return switch (be.figura()) {
            case ManiquiBlockEntity.FIGURA_TU_SKIN -> Text.translatable("modamod.maniqui.figura.skin");
            case ManiquiBlockEntity.FIGURA_JUGADOR -> Text.translatable("modamod.maniqui.figura.jugador",
                    be.dueno() != null && be.dueno().name().isPresent() ? be.dueno().name().get() : "?");
            default -> Text.translatable("modamod.maniqui.figura.maniqui");
        };
    }

    private void clickBoton(int id) {
        this.client.interactionManager.clickButton(this.handler.syncId, id);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        btnVista.setMessage(Text.translatable("modamod.preview.vista",
                Text.translatable(PreviewJugador.nombreVista(anguloVista))));
        btnGirar.setMessage(Text.translatable(handler.be.girando()
                ? "modamod.maniqui.detener" : "modamod.maniqui.girar"));
        btnPose.setMessage(Text.translatable("modamod.maniqui.pose",
                Text.translatable(handler.be.pose().traduccion())));
        btnFigura.setMessage(nombreFigura());
        // Sin permiso (candado cerrado de otro) todo queda de solo lectura (2026-10-04).
        boolean toca = handler.puedeTocar();
        btnFrente.setMessage(Text.translatable(handler.be.frenteAbierto() ? "modamod.maniqui.frente.cerrar" : "modamod.maniqui.frente.abrir"));
        btnCapucha.setMessage(Text.translatable(handler.be.capuchaArriba() ? "modamod.maniqui.capucha.bajar" : "modamod.maniqui.capucha.subir"));
        btnCapucha.active = handler.puedeTocar() && handler.be.hayCapucha();
        btnTwirl.setMessage(Text.translatable(handler.be.twirl() ? "modamod.maniqui.twirl.si" : "modamod.maniqui.twirl.no"));
        for (ButtonWidget b : new ButtonWidget[] {btnGirar, btnPose, btnFigura, btnBusto, btnSkin, btnIntercambiar, btnFrente, btnTwirl}) {
            if (b != null) b.active = toca;
        }
        for (SliderPose sl : sliders) sl.active = toca;
        campoSkin.setEditable(toca);
        if (btnEmote != null) {
            btnEmote.active = toca;
            java.util.UUID e = handler.be.emote();
            btnEmote.setMessage(e == null ? Text.translatable("modamod.maniqui.emote.ninguno")
                    : Text.translatable("modamod.maniqui.emote", EmotecraftCompat.nombre(e)));
        }
        btnCandado.active = com.modamod.util.Candado.puedeAlternar(handler.estado());
        btnCandado.setMessage(Text.translatable(com.modamod.util.Candado.cerrado(handler.estado())
                ? "modamod.candado.cerrado" : "modamod.candado.abierto"));
        btnBusto.setMessage(handler.be.busto() == 0 ? Text.translatable("modamod.maniqui.busto.sin")
                : Text.translatable("modamod.maniqui.busto", handler.be.busto(), com.modamod.body.PerfilCuerpo.BUSTO_MAXIMO));
        if (!arrastrando) for (SliderPose s : sliders) s.refrescar();
        super.render(context, mouseX, mouseY, delta);
        dibujarPreview(context, mouseY);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /**
     * La vista previa es la figura del maniquí (2026-10-02, "la vista previa
     * q tiene q generar es la gui es del maniquí no del player"): la misma
     * que en el bloque — pose, figura (maniquí o skin), busto, armadura y
     * ropa — dibujada por {@code ManiquiRenderer#dibujarFigura}.
     */
    private void dibujarPreview(DrawContext context, int mouseY) {
        // Sin permiso (2026-10-04, "alguien que no es dueño... la preview de su personaje usando el outfit"): se ve
        // el propio personaje con la ropa y la armadura del maniquí puestas, sin tocar nada.
        if (!handler.puedeTocar() && MinecraftClient.getInstance().player != null) {
            GuardarropasScreen.dibujarConOutfit(context, MinecraftClient.getInstance().player, handler.be,
                    handler.be.prendasPuestas(), this.x + PREVIEW_X1_LOCAL, this.y + PREVIEW_Y1_LOCAL,
                    this.x + PREVIEW_X2_LOCAL, this.y + PREVIEW_Y2_LOCAL, anguloVista, mouseY);
            return;
        }
        var dispatcher = MinecraftClient.getInstance().getBlockEntityRenderDispatcher();
        if (!(dispatcher.get(handler.be) instanceof com.modamod.maniqui.ManiquiRenderer renderer)) return;
        int x1 = this.x + PREVIEW_X1_LOCAL, y1 = this.y + PREVIEW_Y1_LOCAL;
        int x2 = this.x + PREVIEW_X2_LOCAL, y2 = this.y + PREVIEW_Y2_LOCAL;
        context.enableScissor(x1, y1, x2, y2);
        // La figura mide ~2 bloques: que entre con un poco de aire.
        float escala = (y2 - y1) * 0.86f / 2.05f;
        var matrices = context.getMatrices();
        matrices.push();
        matrices.translate((x1 + x2) / 2f, y2 - (y2 - y1) * 0.06f, 150f);
        matrices.scale(escala, -escala, escala);
        // Inclinación leve con el mouse, como la vista del jugador.
        float h = (y1 + y2) / 2f;
        float inclina = (float) Math.atan((h - mouseY) / 40f) * 10f;
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-inclina));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(180f + anguloVista));
        net.minecraft.client.render.DiffuseLighting.method_34742();
        renderer.dibujarFigura(handler.be, matrices, context.getVertexConsumers(),
                net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
        context.draw();
        net.minecraft.client.render.DiffuseLighting.enableGuiDepthLighting();
        matrices.pop();
        context.disableScissor();
    }


    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        EstiloPergamino.titulo(context, this.textRenderer, this.title, this.titleX, this.titleY);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX,
                this.playerInventoryTitleY, EstiloPergamino.TEXTO, false);
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
        // Separadores de puntos: entre las tres columnas y sobre el inventario.
        for (int py = y0 + 18; py < y0 + 296; py += 4) {
            c.fill(x0 + 166, py, x0 + 167, py + 2, tema.oscuro);
            c.fill(x0 + 392, py, x0 + 393, py + 2, tema.oscuro);
        }
        for (int px = x0 + 8; px < x0 + w - 8; px += 4) c.fill(px, y0 + 304, px + 2, y0 + 305, tema.oscuro);
        // La figura sobre un fondo oscuro.
        c.fill(x0 + PREVIEW_X1_LOCAL - 1, y0 + PREVIEW_Y1_LOCAL - 1, x0 + PREVIEW_X2_LOCAL + 1, y0 + PREVIEW_Y2_LOCAL + 1, 0xFF2A180C);
        c.fill(x0 + PREVIEW_X1_LOCAL, y0 + PREVIEW_Y1_LOCAL, x0 + PREVIEW_X2_LOCAL, y0 + PREVIEW_Y2_LOCAL, 0xFF4A3828);
        // Como la pantalla de Ropa y el Guardarropas (2026-10-25, "el maniqui no tiene los iconos y tiene unos textos largos q se
        // cruzan hacelo con el modelo de los otros dos"): marco de slot de Ropa, el ícono del slot de Trinkets en los vacíos y
        // solo dos títulos; lo que va en cada lugar lo dice el hover.
        for (var slot : handler.slots) {
            int sx = x0 + slot.x - 1, sy = y0 + slot.y - 1;
            c.fill(sx, sy, sx + 18, sy + 18, 0xFF6B5136);
            c.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFC9A877);
            c.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0xFFA58558);
            if (slot instanceof com.modamod.ropa.SlotDeRopa ropa && !slot.hasStack()) {
                c.drawTexture(icono(ropa.slotDeRopa()), x0 + slot.x, y0 + slot.y, 0, 0, 16, 16, 16, 16);
            }
        }
        int tinta = EstiloPergamino.TEXTO;
        c.drawText(this.textRenderer, Text.translatable("modamod.ropa.prendas"), x0 + ManiquiScreenHandler.X_GRILLA - 1,
                y0 + ManiquiScreenHandler.Y_GRILLA - 10, tinta, false);
        c.drawText(this.textRenderer, Text.translatable("modamod.ropa.real"), x0 + ManiquiScreenHandler.X_ARMADURA - 1,
                y0 + ManiquiScreenHandler.Y_GRILLA - 10, tinta, false);
    }

    /** El ícono de slot vacío de cada lugar de ropa (los mismos de Trinkets, {@code textures/gui/slot/}). */
    private static Identifier icono(String slot) {
        String n = switch (slot) {
            case "prenda" -> "remera";
            case "exterior" -> "pantalon";
            case "pair" -> "socks";
            default -> slot;
        };
        return Identifier.of("modamod", "textures/gui/slot/" + n + ".png");
    }
}
