package com.modamod.client;

import com.modamod.body.CuerpoBase;
import com.modamod.body.PerfilCuerpo;
import com.modamod.body.PerfilesDeCuerpo;
import com.modamod.body.RedCuerpo;
import com.modamod.render.CuerpoBaseTextures;
import com.modamod.render.GarmentFeatureRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Elegir el cuerpo base (2026-09-29, "la primera vez que uno se pone una
 * prenda del mod te lance una gui con el color de skin calculado y te de la
 * alternativa de elegir cualquiera de esas texturas base y agregar un
 * comando para overridearla").
 *
 * <ul>
 *   <li>Izquierda: vista previa 3D del jugador SIN ropa y sin la segunda capa
 *   de la skin (ni la de 3D Skin Layers) — "tiene que aparecer sin ropa y
 *   sin la capa de 3dsl en el cuerpo" — con el cuerpo y los colores que se
 *   están eligiendo; arrastrar para girar.</li>
 *   <li>Centro: "Tu skin" + los 19 cuerpos del zip, con miniatura teñida.</li>
 *   <li>Derecha: los colores. Tres zonas (Base, Clara, Oscura — "tiene un
 *   tono de pelaje y un tono en la pancita"; Clara y Oscura solo en los
 *   animales), la paleta de 5 colores sacados de la skin ("poder
 *   seleccionar hasta 5 colores directamente de la skin"), sliders R/G/B de
 *   la zona elegida y un botón para volver a lo automático.</li>
 * </ul>
 * Confirmar lo guarda en el perfil (y ya no salta sola). "Ahora no" la
 * cierra sin guardar: vuelve a saltar la próxima sesión. {@code /modamod
 * elegir} la abre cuando quieras.
 */
public class ElegirCuerpoScreen extends Screen {

    private static final int ANCHO = 480, ALTO = 250;
    // La vista previa se achicó (2026-09-30) para que entren abajo los dos botones de ropa interior.
    private static final int PREVIEW_X1 = 8, PREVIEW_Y1 = 22, PREVIEW_X2 = 108, PREVIEW_Y2 = 204;
    private static final int GRILLA_X = 116, GRILLA_Y = 22, CELDA_W = 44, CELDA_H = 46, COLUMNAS = 5;
    private static final int DERECHA_X = 344, DERECHA_ANCHO = 128;
    private static final int ZONAS_Y = 34, PALETA_Y = 90, SLIDERS_Y = 110;
    /** Tono de reserva mientras la skin no bajó (el mismo beige que usa el resto del mod). */
    private static final int TONO_RESERVA = 0xC89F7E;
    private static final String[] CLAVE_ZONA = {"base", "clara", "oscura", "rubor"};
    private static final int ZONAS = 4;
    /** "Zona" extra de los sliders: el color de la ropa interior (2026-09-30), con su muestra al lado de sus botones. */
    private static final int ZONA_INTERIOR = 4;

    private int x0, y0;
    private CuerpoBase elegido;
    /**
     * Color elegido a mano de cada zona (Base, Clara, Oscura); null =
     * automático — la Base sale de la skin, Clara y Oscura del color Base.
     */
    private final Integer[] tonos = new Integer[ZONAS + 1];
    /** Fuerza del rubor en % (2026-09-29, "poneme un selector de fuerza de rubor"). */
    private int fuerzaRubor = PerfilCuerpo.FUERZA_RUBOR_DEFECTO;
    private SliderFuerza sliderFuerza;
    private int zona = CuerpoBaseTextures.ZONA_BASE;
    /**
     * Ropa interior y "usar siempre como skin" (2026-09-29, "un selector que
     * directamente te deje esa skin de default, quizas con ropa interior
     * base"); en dos partes desde el 2026-09-30 ("capaz se eligen las dos
     * partes?"). El color va en {@code tonos[ZONA_INTERIOR]} (null = el de siempre).
     */
    private com.modamod.body.InteriorArriba interiorArriba = com.modamod.body.RopaInterior.DEFECTO.arriba();
    private com.modamod.body.InteriorAbajo interiorAbajo = com.modamod.body.RopaInterior.DEFECTO.abajo();
    private boolean siempre = false;
    /** Busto en cajas (2026-10-02, "agreguemos un selector de tetas cuadradas en el selector de skin"). */
    private boolean bustoCuadrado = false;
    /** Volumen: 0 redondeado, 1 voxel, 2 voxel grueso (2026-10-04). */
    private int volumen = 0;
    private EstiloPergamino.BotonPergamino btnVolumen;
    private ButtonWidget btnArriba, btnAbajo, btnSiempre, btnBusto;
    private final SliderCanal[] sliders = new SliderCanal[3];
    private final BotonZona[] botonesZona = new BotonZona[ZONAS];
    private ButtonWidget btnAutomatico;
    private float anguloVista = 0f;
    private boolean arrastrando = false;

    public ElegirCuerpoScreen() {
        super(Text.translatable("modamod.elegir_cuerpo.titulo"));
        ClientPlayerEntity jugador = MinecraftClient.getInstance().player;
        PerfilCuerpo perfil = jugador == null ? PerfilCuerpo.DEFECTO : PerfilesDeCuerpo.de(jugador);
        this.elegido = perfil.cuerpo();
        tonos[0] = perfil.tonoDerivado() ? null : perfil.tono();
        tonos[1] = perfil.tonoClaro() == PerfilCuerpo.TONO_AUTOMATICO ? null : perfil.tonoClaro();
        tonos[2] = perfil.tonoOscuro() == PerfilCuerpo.TONO_AUTOMATICO ? null : perfil.tonoOscuro();
        tonos[3] = perfil.tonoRubor() == PerfilCuerpo.TONO_AUTOMATICO ? null : perfil.tonoRubor();
        interiorArriba = perfil.interior().arriba();
        interiorAbajo = perfil.interior().abajo();
        tonos[ZONA_INTERIOR] = perfil.interior().color() == com.modamod.body.RopaInterior.COLOR_DEFECTO
                ? null : perfil.interior().color();
        siempre = perfil.siempre();
        bustoCuadrado = perfil.bustoCuadrado();
        volumen = perfil.volumen();
        fuerzaRubor = perfil.fuerzaRubor();
    }

    private int tonoDeLaSkin() {
        ClientPlayerEntity jugador = MinecraftClient.getInstance().player;
        Integer t = jugador == null ? null : CuerpoBaseTextures.tonoDeLaSkin(jugador);
        return t == null ? TONO_RESERVA : t;
    }

    /** El color con el que se ve la zona {@code z} ahora (las automáticas muestran el Base). */
    private int colorDeZona(int z) {
        if (tonos[z] != null) return tonos[z];
        if (z == ZONA_INTERIOR) return com.modamod.body.RopaInterior.COLOR_DEFECTO;
        int base = tonos[0] != null ? tonos[0] : tonoDeLaSkin();
        // El rubor automático es el mismo color, más saturado y oscuro.
        return z == CuerpoBaseTextures.ZONA_RUBOR ? CuerpoBaseTextures.ruborAutomatico(base) : base;
    }

    /** Clara y Oscura solo tienen sentido en los cuerpos de animal. */
    private boolean zonaDisponible(int z) {
        return z == CuerpoBaseTextures.ZONA_BASE || z == CuerpoBaseTextures.ZONA_RUBOR || z == ZONA_INTERIOR
                || elegido.animal;
    }

    @Override
    protected void init() {
        EstiloPergamino.usarTema(EstiloPergamino.Tema.LATON);
        x0 = (this.width - ANCHO) / 2;
        y0 = (this.height - ALTO) / 2;

        // Una celda por cuerpo: el click lo elige.
        CuerpoBase[] todos = CuerpoBase.values();
        for (int i = 0; i < todos.length; i++) {
            CuerpoBase c = todos[i];
            int cx = x0 + GRILLA_X + (i % COLUMNAS) * CELDA_W, cy = y0 + GRILLA_Y + (i / COLUMNAS) * CELDA_H;
            BotonCuerpo b = new BotonCuerpo(c, cx, cy);
            b.setTooltip(Tooltip.of(Text.translatable(c.traduccion())));
            addDrawableChild(b);
        }

        // Cuatro zonas (2026-09-29, se sumó Rubor: "3 por default, mas selector propio").
        for (int z = 0; z < ZONAS; z++) {
            botonesZona[z] = new BotonZona(z, x0 + DERECHA_X + z * 32, y0 + ZONAS_Y);
            botonesZona[z].setTooltip(Tooltip.of(Text.translatable("modamod.elegir_cuerpo.tooltip.zona." + CLAVE_ZONA[z])));
            addDrawableChild(botonesZona[z]);
        }

        // Paleta: hasta 5 colores sacados de la skin.
        ClientPlayerEntity jugador = MinecraftClient.getInstance().player;
        List<Integer> paleta = jugador == null ? List.of() : CuerpoBaseTextures.paletaDeLaSkin(jugador);
        for (int i = 0; i < paleta.size(); i++) {
            BotonPaleta b = new BotonPaleta(paleta.get(i), x0 + DERECHA_X + i * 26, y0 + PALETA_Y);
            b.setTooltip(Tooltip.of(Text.translatable("modamod.elegir_cuerpo.tooltip.paleta")));
            addDrawableChild(b);
        }

        int t = colorDeZona(zona);
        for (int canal = 0; canal < 3; canal++) {
            sliders[canal] = new SliderCanal(x0 + DERECHA_X, y0 + SLIDERS_Y + canal * 18, canal, (t >> (16 - 8 * canal)) & 0xFF);
            addDrawableChild(sliders[canal]);
        }

        btnAutomatico = new EstiloPergamino.BotonPergamino(x0 + DERECHA_X, y0 + SLIDERS_Y + 56, DERECHA_ANCHO, 16,
                Text.empty(), b -> {
            tonos[zona] = null;
            sincronizarSliders();
        });
        addDrawableChild(btnAutomatico);

        // Solo con la zona Rubor elegida, debajo de Automática.
        sliderFuerza = new SliderFuerza(x0 + DERECHA_X, y0 + SLIDERS_Y + 74);
        addDrawableChild(sliderFuerza);

        // Debajo de la vista previa: la ropa interior. Debajo de la grilla: usar siempre.
        // A la izquierda, la muestra del color (elige la ropa interior en los sliders).
        addDrawableChild(new BotonColorInterior(x0 + PREVIEW_X1, y0 + 208));
        btnArriba = new EstiloPergamino.BotonPergamino(x0 + PREVIEW_X1 + 18, y0 + 208, PREVIEW_X2 - PREVIEW_X1 - 18, 16,
                Text.empty(), b -> interiorArriba = interiorArriba.siguiente());
        btnArriba.setTooltip(Tooltip.of(Text.translatable("modamod.elegir_cuerpo.tooltip.interior_arriba")));
        addDrawableChild(btnArriba);
        btnAbajo = new EstiloPergamino.BotonPergamino(x0 + PREVIEW_X1 + 18, y0 + 226, PREVIEW_X2 - PREVIEW_X1 - 18, 16,
                Text.empty(), b -> interiorAbajo = interiorAbajo.siguiente());
        btnAbajo.setTooltip(Tooltip.of(Text.translatable("modamod.elegir_cuerpo.tooltip.interior_abajo")));
        addDrawableChild(btnAbajo);
        // Usar siempre, la forma del busto y el volumen comparten la fila (tercios).
        int mitad = (COLUMNAS * CELDA_W - 4 - 8) / 3;
        btnSiempre = new EstiloPergamino.BotonPergamino(x0 + GRILLA_X, y0 + 224, mitad, 16,
                Text.empty(), b -> siempre = !siempre);
        btnSiempre.setTooltip(Tooltip.of(Text.translatable("modamod.elegir_cuerpo.tooltip.siempre")));
        addDrawableChild(btnSiempre);
        btnBusto = new EstiloPergamino.BotonPergamino(x0 + GRILLA_X + mitad + 4, y0 + 224, mitad, 16,
                Text.empty(), b -> bustoCuadrado = !bustoCuadrado);
        btnBusto.setTooltip(Tooltip.of(Text.translatable("modamod.elegir_cuerpo.tooltip.busto")));
        addDrawableChild(btnBusto);
        btnVolumen = new EstiloPergamino.BotonPergamino(x0 + GRILLA_X + 2 * (mitad + 4), y0 + 224, mitad, 16,
                Text.empty(), b -> volumen = (volumen + 1) % 3);
        btnVolumen.setTooltip(Tooltip.of(Text.translatable("modamod.elegir_cuerpo.tooltip.volumen")));
        addDrawableChild(btnVolumen);

        addDrawableChild(new EstiloPergamino.BotonPergamino(x0 + DERECHA_X, y0 + 200, DERECHA_ANCHO, 18,
                Text.translatable("modamod.elegir_cuerpo.boton.confirmar"), b -> confirmar()));
        addDrawableChild(new EstiloPergamino.BotonPergamino(x0 + DERECHA_X, y0 + 222, DERECHA_ANCHO, 18,
                Text.translatable("modamod.elegir_cuerpo.boton.despues"), b -> close()));
        refrescar();
    }

    private void refrescar() {
        if (!zonaDisponible(zona)) {
            zona = CuerpoBaseTextures.ZONA_BASE;
            sincronizarSliders();
        }
        for (int z = 0; z < ZONAS; z++) botonesZona[z].active = zonaDisponible(z);
        btnArriba.setMessage(Text.literal("\u2191 ").append(Text.translatable(interiorArriba.traduccion())));
        btnAbajo.setMessage(Text.literal("\u2193 ").append(Text.translatable(interiorAbajo.traduccion())));
        btnSiempre.setMessage(Text.translatable("modamod.elegir_cuerpo.boton.siempre",
                Text.translatable(siempre ? "modamod.si" : "modamod.no")));
        btnBusto.setMessage(Text.translatable("modamod.elegir_cuerpo.boton.busto",
                Text.translatable(bustoCuadrado ? "modamod.elegir_cuerpo.busto.cuadrado"
                        : "modamod.elegir_cuerpo.busto.redondo")));
        btnVolumen.setMessage(Text.translatable("modamod.elegir_cuerpo.boton.volumen",
                Text.translatable("modamod.elegir_cuerpo.volumen." + volumen)));
        sliderFuerza.visible = zona == CuerpoBaseTextures.ZONA_RUBOR;
        boolean base = zona == CuerpoBaseTextures.ZONA_BASE;
        btnAutomatico.setMessage(Text.translatable(base
                ? "modamod.elegir_cuerpo.boton.skin" : "modamod.elegir_cuerpo.boton.automatico"));
        btnAutomatico.setTooltip(Tooltip.of(Text.translatable(base
                ? "modamod.elegir_cuerpo.tooltip.skin" : "modamod.elegir_cuerpo.tooltip.automatico")));
    }

    private void sincronizarSliders() {
        int t = colorDeZona(zona);
        for (int canal = 0; canal < 3; canal++) sliders[canal].fijar((t >> (16 - 8 * canal)) & 0xFF);
    }

    private void confirmar() {
        ClientPlayNetworking.send(new RedCuerpo.Elegir(elegido.clave,
                tonos[0] == null ? PerfilCuerpo.TONO_DE_LA_SKIN : tonos[0],
                tonos[1] == null ? PerfilCuerpo.TONO_AUTOMATICO : tonos[1],
                tonos[2] == null ? PerfilCuerpo.TONO_AUTOMATICO : tonos[2],
                tonos[3] == null ? PerfilCuerpo.TONO_AUTOMATICO : tonos[3], fuerzaRubor,
                interiorArriba.clave, interiorAbajo.clave, colorDeZona(ZONA_INTERIOR), siempre, bustoCuadrado, volumen));
        close();
    }

    /** 0 es "automático" en el perfil: el negro puro elegido a mano va como 1. */
    private static int sinCero(int rgb) {
        return rgb == 0 ? 1 : rgb;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        refrescar();
        super.render(c, mouseX, mouseY, delta);
        c.drawText(textRenderer, this.title, x0 + 8, y0 + 8, EstiloPergamino.TEXTO, false);

        // Nombre del elegido debajo de la grilla y la ayuda del comando.
        c.drawText(textRenderer, Text.translatable("modamod.elegir_cuerpo.elegido",
                Text.translatable(elegido.traduccion())), x0 + GRILLA_X, y0 + 212, EstiloPergamino.TEXTO, false);
        // Arriba, al lado del título (abajo va el botón de usar siempre).
        c.drawText(textRenderer, Text.translatable("modamod.elegir_cuerpo.ayuda"),
                x0 + GRILLA_X, y0 + 8, EstiloPergamino.TEXTO_APAGADO, false);

        // Colores: título, de dónde sale el de la zona elegida, y la paleta.
        c.drawText(textRenderer, Text.translatable("modamod.elegir_cuerpo.tono"), x0 + DERECHA_X, y0 + 22, EstiloPergamino.TEXTO, false);
        String estado = tonos[zona] != null ? "modamod.elegir_cuerpo.tono_mano"
                : zona == CuerpoBaseTextures.ZONA_BASE ? "modamod.elegir_cuerpo.tono_skin"
                : "modamod.elegir_cuerpo.tono_auto";
        c.drawText(textRenderer, Text.translatable(estado), x0 + DERECHA_X, y0 + 66, EstiloPergamino.TEXTO, false);
        c.drawText(textRenderer, Text.translatable("modamod.elegir_cuerpo.de_tu_skin"), x0 + DERECHA_X, y0 + PALETA_Y - 10,
                EstiloPergamino.TEXTO, false);

        dibujarPreview(c, mouseY);
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        super.renderBackground(c, mouseX, mouseY, delta);
        // Panel pergamino con marco de madera, mismos colores que las máquinas.
        c.fill(x0 - 4, y0 - 4, x0 + ANCHO + 4, y0 + ALTO + 4, 0xFF4A2E1A);
        c.fill(x0 - 2, y0 - 2, x0 + ANCHO + 2, y0 + ALTO + 2, 0xFF7A5230);
        c.fill(x0, y0, x0 + ANCHO, y0 + ALTO, 0xFFDEBC84);
        c.fill(x0 + PREVIEW_X1, y0 + PREVIEW_Y1, x0 + PREVIEW_X2, y0 + PREVIEW_Y2, 0xFF2C221C);
        c.drawBorder(x0 + PREVIEW_X1 - 1, y0 + PREVIEW_Y1 - 1, PREVIEW_X2 - PREVIEW_X1 + 2, PREVIEW_Y2 - PREVIEW_Y1 + 2, 0xFF684818);
    }

    private void dibujarPreview(DrawContext c, int mouseY) {
        ClientPlayerEntity jugador = MinecraftClient.getInstance().player;
        if (jugador == null) return;
        GarmentFeatureRenderer.perfilOverride = new PerfilCuerpo(elegido,
                tonos[0] == null ? PerfilCuerpo.TONO_DE_LA_SKIN : tonos[0],
                new com.modamod.body.RopaInterior(interiorArriba, interiorAbajo, colorDeZona(ZONA_INTERIOR)), true,
                tonos[1] == null ? PerfilCuerpo.TONO_AUTOMATICO : tonos[1],
                tonos[2] == null ? PerfilCuerpo.TONO_AUTOMATICO : tonos[2],
                tonos[3] == null ? PerfilCuerpo.TONO_AUTOMATICO : tonos[3], fuerzaRubor, siempre,
                // El relieve que ya tiene (busto de los Estrógenos, definición).
                com.modamod.body.PerfilesDeCuerpo.de(jugador).busto(),
                com.modamod.body.PerfilesDeCuerpo.de(jugador).definicion(),
                com.modamod.body.PerfilesDeCuerpo.de(jugador).estrogenosHasta(), bustoCuadrado, volumen);
        // Sin ropa: solo el cuerpo que se está eligiendo.
        GarmentFeatureRenderer.previewOverride = List.of();
        try {
            PreviewJugador.dibujar(c, jugador, x0 + PREVIEW_X1, y0 + PREVIEW_Y1, x0 + PREVIEW_X2, y0 + PREVIEW_Y2,
                    70, anguloVista, (float) mouseY);
        } finally {
            GarmentFeatureRenderer.perfilOverride = null;
            GarmentFeatureRenderer.previewOverride = null;
        }
    }

    private boolean enPreview(double mx, double my) {
        return mx >= x0 + PREVIEW_X1 && mx < x0 + PREVIEW_X2 && my >= y0 + PREVIEW_Y1 && my < y0 + PREVIEW_Y2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && enPreview(mx, my)) arrastrando = true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) arrastrando = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (arrastrando) {
            anguloVista = Math.floorMod(Math.round(anguloVista + (float) dx * 1.15f), 360);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    /**
     * Miniatura de frente: cabeza de la skin del jugador + torso, brazos y
     * piernas de la máscara (o de la skin, en "Tu skin"), teñidos con el color Base.
     */
    private void dibujarMiniatura(DrawContext c, CuerpoBase cuerpo, int x, int y) {
        ClientPlayerEntity jugador = MinecraftClient.getInstance().player;
        if (jugador == null) return;
        SkinTextures skin = jugador.getSkinTextures();
        boolean skinReal = !cuerpo.tieneTextura();
        Identifier tex = skinReal ? skin.texture() : cuerpo.textura();
        int aw = (skinReal ? skin.model() == SkinTextures.Model.SLIM : cuerpo.slim) ? 3 : 4;
        float escala = 1.25f;
        int anchoFig = aw * 2 + 8, altoFig = 32;
        float fx = x + (CELDA_W - anchoFig * escala) / 2f, fy = y + (CELDA_H - altoFig * escala) / 2f;

        c.getMatrices().push();
        c.getMatrices().translate(fx, fy, 0);
        c.getMatrices().scale(escala, escala, 1f);
        RenderSystem.enableBlend();
        // Cabeza: siempre la cara del jugador (el cuerpo base no la tapa).
        c.drawTexture(skin.texture(), aw, 0, 8, 8, 8, 8, 64, 64);
        if (!skinReal) {
            int t = colorDeZona(CuerpoBaseTextures.ZONA_BASE);
            float k = 1.35f;
            RenderSystem.setShaderColor(Math.min(1f, ((t >> 16) & 0xFF) / 255f * k),
                    Math.min(1f, ((t >> 8) & 0xFF) / 255f * k), Math.min(1f, (t & 0xFF) / 255f * k), 1f);
        }
        c.drawTexture(tex, aw, 8, 20, 20, 8, 12, 64, 64);          // torso
        c.drawTexture(tex, 0, 8, 44, 20, aw, 12, 64, 64);          // brazo derecho (a la izquierda de frente)
        c.drawTexture(tex, aw + 8, 8, 36, 52, aw, 12, 64, 64);     // brazo izquierdo
        c.drawTexture(tex, aw, 20, 4, 20, 4, 12, 64, 64);          // pierna derecha
        c.drawTexture(tex, aw + 4, 20, 20, 52, 4, 12, 64, 64);     // pierna izquierda
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        c.getMatrices().pop();
    }

    /** Una celda de la grilla: miniatura, marco dorado si es el elegido. */
    private class BotonCuerpo extends ButtonWidget {
        private final CuerpoBase cuerpo;

        BotonCuerpo(CuerpoBase cuerpo, int x, int y) {
            super(x, y, CELDA_W - 2, CELDA_H - 2, Text.translatable(cuerpo.traduccion()), b -> elegido = cuerpo, DEFAULT_NARRATION_SUPPLIER);
            this.cuerpo = cuerpo;
        }

        @Override
        protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            c.fill(x, y, x + w, y + h, isHovered() ? 0xFFC9A66E : 0xFFB9956A);
            dibujarMiniatura(c, cuerpo, x, y);
            boolean sel = cuerpo == elegido;
            c.drawBorder(x, y, w, h, sel ? 0xFFFFD24C : 0xFF6B4E2A);
            if (sel) c.drawBorder(x + 1, y + 1, w - 2, h - 2, 0xFFFFD24C);
        }
    }

    /** Una zona (Base/Clara/Oscura/Rubor): muestra su color; click la elige para editar. */
    private class BotonZona extends ButtonWidget {
        private final int z;

        BotonZona(int z, int x, int y) {
            super(x, y, 31, 28, Text.translatable("modamod.elegir_cuerpo.zona." + CLAVE_ZONA[z]), b -> {
                zona = z;
                sincronizarSliders();
            }, DEFAULT_NARRATION_SUPPLIER);
            this.z = z;
        }

        @Override
        protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            c.fill(x, y, x + w, y + h, active ? 0xFFB9956A : 0xFF9A8466);
            if (active) {
                c.fill(x + 3, y + 3, x + w - 3, y + 15, 0xFF000000 | colorDeZona(z));
                // Automática: una "A" sobre la muestra.
                if (tonos[z] == null) c.drawText(textRenderer, "A", x + w - 10, y + 5, 0xFFFFFFFF, true);
            }
            c.drawText(textRenderer, getMessage(), x + (w - textRenderer.getWidth(getMessage())) / 2, y + 18,
                    active ? EstiloPergamino.TEXTO : EstiloPergamino.TEXTO_APAGADO, false);
            boolean sel = z == zona;
            c.drawBorder(x, y, w, h, sel ? 0xFFFFD24C : 0xFF6B4E2A);
            if (sel) c.drawBorder(x + 1, y + 1, w - 2, h - 2, 0xFFFFD24C);
        }
    }

    /** La muestra del color de la ropa interior (2026-09-30): click la elige para los sliders. */
    private class BotonColorInterior extends ButtonWidget {
        BotonColorInterior(int x, int y) {
            super(x, y, 16, 34, Text.translatable("modamod.elegir_cuerpo.zona.interior"), b -> {
                zona = ZONA_INTERIOR;
                sincronizarSliders();
            }, DEFAULT_NARRATION_SUPPLIER);
            setTooltip(Tooltip.of(Text.translatable("modamod.elegir_cuerpo.tooltip.zona.interior")));
        }

        @Override
        protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            c.fill(x, y, x + w, y + h, 0xFFB9956A);
            c.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF000000 | colorDeZona(ZONA_INTERIOR));
            if (tonos[ZONA_INTERIOR] == null) c.drawText(textRenderer, "A", x + 5, y + h / 2 - 4, 0xFF6B4E2A, false);
            boolean sel = zona == ZONA_INTERIOR;
            c.drawBorder(x, y, w, h, sel ? 0xFFFFD24C : 0xFF6B4E2A);
            if (sel) c.drawBorder(x + 1, y + 1, w - 2, h - 2, 0xFFFFD24C);
        }
    }

    /** Un color sacado de la skin: click lo pone en la zona elegida. */
    private class BotonPaleta extends ButtonWidget {
        private final int color;

        BotonPaleta(int color, int x, int y) {
            super(x, y, 24, 16, Text.empty(), b -> {}, DEFAULT_NARRATION_SUPPLIER);
            this.color = color;
        }

        @Override
        public void onPress() {
            tonos[zona] = sinCero(color);
            sincronizarSliders();
        }

        @Override
        protected void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            c.fill(x, y, x + w, y + h, 0xFF000000 | color);
            c.drawBorder(x, y, w, h, isHovered() ? 0xFFFFD24C : 0xFF2A180C);
        }
    }

    /** Un canal R/G/B de la zona elegida (0..255): moverlo la pasa a elegida a mano. */
    private class SliderCanal extends SliderWidget {
        private static final String[] CLAVE = {"rojo", "verde", "azul"};
        private final int canal;

        SliderCanal(int x, int y, int canal, int valor) {
            super(x, y, DERECHA_ANCHO, 16, Text.empty(), valor / 255.0);
            this.canal = canal;
            updateMessage();
        }

        int nivel() {
            return (int) Math.round(this.value * 255);
        }

        void fijar(int valor) {
            this.value = valor / 255.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("modamod.elegir_cuerpo.canal." + CLAVE[canal], nivel()));
        }

        @Override
        protected void applyValue() {
            int t = 0;
            for (int i = 0; i < 3; i++) t |= sliders[i] == null ? 0 : sliders[i].nivel() << (16 - 8 * i);
            tonos[zona] = sinCero(t);
        }
    }

    /** Fuerza del rubor, 0..100% de a 5. */
    private class SliderFuerza extends SliderWidget {
        SliderFuerza(int x, int y) {
            super(x, y, DERECHA_ANCHO, 14, Text.empty(), fuerzaRubor / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("modamod.elegir_cuerpo.fuerza_rubor", Math.round(this.value * 20) * 5));
        }

        @Override
        protected void applyValue() {
            fuerzaRubor = (int) (Math.round(this.value * 20) * 5);
        }
    }
}
