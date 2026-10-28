package com.modamod.client;

import com.modamod.guardarropas.GuardarropasBlockEntity;
import com.modamod.guardarropas.GuardarropasScreenHandler;
import com.modamod.render.GarmentFeatureRenderer;
import com.modamod.ropa.Cosmeticos;
import com.modamod.ropa.RopaScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Guardarropas (2026-10-21, "arreglemos el guardarropas, tiene que tener los mismos slots que la pantalla de ropa del jugador y tiene
 * que poder guardar con nombre el outfit del jugador con todas sus prendas sin acceso puntual a las prendas sino a todo el outfit"):
 * a la izquierda, la misma pantalla de Ropa del jugador (los mismos slots, con muñeco, armadura real y cosmética); a la derecha, la
 * lista de outfits guardados con nombre. Se guarda lo que llevás puesto (las prendas se mueven al mueble), se ve cada outfit en el
 * muñeco antes de ponértelo y se pone entero con un botón. Antes era una grilla de borrador para combinar prendas (el Maniquí cubre eso).
 */
public class GuardarropasScreen extends HandledScreen<GuardarropasScreenHandler> {

    private static final int ANCHO = 504, ALTO = RopaScreenHandler.ALTO;
    private static final int X_LISTA = RopaScreenHandler.ANCHO + 8, ANCHO_LISTA = ANCHO - X_LISTA - 8;
    private static final int Y_FILAS = 46, ALTO_FILA = 14;

    private float anguloVista = 0f;
    /** El último nombre que el mueble nos recordó (para dejar el campo escrito una sola vez por cambio). */
    private String ultimoVisto;
    private int seleccionado = -1;
    private TextFieldWidget txtNombre;
    private ButtonWidget btnGuardar, btnCandado;
    private final ButtonWidget[] btnNombre = new ButtonWidget[com.modamod.guardarropas.Outfits.MAXIMO];
    private final ButtonWidget[] btnPoner = new ButtonWidget[com.modamod.guardarropas.Outfits.MAXIMO];
    private final ButtonWidget[] btnBorrar = new ButtonWidget[com.modamod.guardarropas.Outfits.MAXIMO];
    private final ButtonWidget[] ver = new ButtonWidget[4], ocultar = new ButtonWidget[4];

    public GuardarropasScreen(GuardarropasScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = ANCHO;
        this.backgroundHeight = ALTO;
        this.titleX = 8;
        this.titleY = 6;
        this.playerInventoryTitleY = -100;
    }

    @Override
    protected void init() {
        EstiloPergamino.usarTema(EstiloPergamino.Tema.PLATA);
        super.init();
        // Los interruptores de la armadura cosmética, igual que en la pantalla de Ropa.
        for (int z = 0; z < 4; z++) {
            int id = z;
            int y = this.y + RopaScreenHandler.Y_ARMADURA + z * RopaScreenHandler.PASO_ARMADURA - 1;
            ver[z] = addDrawableChild(new EstiloPergamino.BotonPergamino(this.x + 196, y, 40, 18, Text.empty(), b -> clickBoton(id)));
            ocultar[z] = addDrawableChild(new EstiloPergamino.BotonPergamino(this.x + 238, y, 40, 18, Text.empty(), b -> clickBoton(4 + id)));
        }
        // La derecha: nombre, "Guardar" y la lista.
        txtNombre = new TextFieldWidget(this.textRenderer, this.x + X_LISTA, this.y + 22, ANCHO_LISTA - 66, 16, Text.translatable("modamod.guardarropas.nombre"));
        txtNombre.setMaxLength(24);
        txtNombre.setPlaceholder(Text.translatable("modamod.guardarropas.nombre"));
        addDrawableChild(txtNombre);
        btnGuardar = new EstiloPergamino.BotonPergamino(this.x + X_LISTA + ANCHO_LISTA - 62, this.y + 22, 62, 16, Text.translatable("modamod.guardarropas.guardar"), b -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new com.modamod.guardarropas.GuardarOutfitPayload(txtNombre.getText()));
            txtNombre.setText("");
        });
        btnGuardar.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("modamod.guardarropas.tooltip.guardar")));
        addDrawableChild(btnGuardar);
        for (int i = 0; i < btnNombre.length; i++) {
            int idx = i;
            int y = this.y + Y_FILAS + i * ALTO_FILA;
            btnNombre[i] = addDrawableChild(new EstiloPergamino.BotonPergamino(this.x + X_LISTA, y, ANCHO_LISTA - 66, ALTO_FILA - 1, Text.empty(),
                    b -> seleccionado = seleccionado == idx ? -1 : idx));
            btnPoner[i] = addDrawableChild(new EstiloPergamino.BotonPergamino(this.x + X_LISTA + ANCHO_LISTA - 64, y, 42, ALTO_FILA - 1,
                    Text.translatable("modamod.guardarropas.poner"), b -> clickBoton(GuardarropasBlockEntity.BTN_PONER_BASE + idx)));
            btnPoner[i].setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("modamod.guardarropas.tooltip.poner")));
            btnBorrar[i] = addDrawableChild(new EstiloPergamino.BotonPergamino(this.x + X_LISTA + ANCHO_LISTA - 20, y, 20, ALTO_FILA - 1,
                    Text.literal("x"), b -> clickBoton(GuardarropasBlockEntity.BTN_BORRAR_BASE + idx)));
            btnBorrar[i].setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("modamod.guardarropas.tooltip.desarmar")));
        }
        btnCandado = addDrawableChild(new EstiloPergamino.BotonPergamino(this.x + X_LISTA, this.y + ALTO - 22, ANCHO_LISTA, 16, Text.empty(),
                b -> clickBoton(GuardarropasBlockEntity.BTN_CANDADO)));
    }

    private void clickBoton(int id) {
        if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id);
    }

    /** Escribir el nombre no tiene que cerrar la pantalla con la "e" (mismo motivo que en Tintes y la Modeladora). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return (!txtNombre.keyPressed(keyCode, scanCode, modifiers) && !txtNombre.isActive())
                ? super.keyPressed(keyCode, scanCode, modifiers) : true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        refrescar();
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private void refrescar() {
        List<GuardarropasBlockEntity.Outfit> outfits = handler.outfits();
        boolean toca = handler.puedeTocar();
        // El nombre del último outfit que te pusiste queda escrito para volver a guardarlo con el mismo (2026-10-21).
        String ultimo = client != null && client.player != null ? handler.ultimoDe(client.player.getUuid()) : null;
        if (ultimo != null && !ultimo.equals(ultimoVisto)) txtNombre.setText(ultimo);
        ultimoVisto = ultimo;
        if (seleccionado >= outfits.size()) seleccionado = -1;
        for (int i = 0; i < btnNombre.length; i++) {
            boolean hay = i < outfits.size();
            btnNombre[i].visible = btnPoner[i].visible = btnBorrar[i].visible = hay;
            if (!hay) continue;
            var o = outfits.get(i);
            btnNombre[i].setMessage(Text.literal((i == seleccionado ? "> " : "") + o.nombre() + " (" + o.piezas().size() + ")"));
            btnPoner[i].active = btnBorrar[i].active = toca;
        }
        btnGuardar.active = toca && outfits.size() < com.modamod.guardarropas.Outfits.MAXIMO;
        btnCandado.active = com.modamod.util.Candado.puedeAlternar(handler.estado());
        btnCandado.setMessage(Text.translatable(com.modamod.util.Candado.cerrado(handler.estado())
                ? "modamod.candado.cerrado" : "modamod.candado.abierto"));
        Cosmeticos cosm = client != null && client.player != null ? Cosmeticos.de(client.player) : Cosmeticos.VACIO;
        for (int z = 0; z < 4; z++) {
            ver[z].setMessage(etiqueta("modamod.ropa.ver", cosm.veCosmetico(z)));
            ocultar[z].setMessage(etiqueta("modamod.ropa.ocultar", cosm.oculta(z)));
        }
    }

    private static Text etiqueta(String clave, boolean activo) {
        return Text.literal(activo ? "[x] " : "[ ] ").append(Text.translatable(clave)).formatted(activo ? Formatting.GREEN : Formatting.GRAY);
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y, w = backgroundWidth, h = backgroundHeight;
        c.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF2A1A0E);
        c.fill(x, y, x + w, y + h, 0xFFE3CC9E);
        c.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFEAD8B0);
        // Separador entre la pantalla de Ropa y la lista.
        c.fill(x + RopaScreenHandler.ANCHO + 2, y + 8, x + RopaScreenHandler.ANCHO + 3, y + h - 8, 0xFF6B5136);

        // Muñeco: el jugador (o el outfit elegido de la lista, probado de mentira).
        c.fill(x + 6, y + 20, x + 70, y + 126, 0xFF6B5136);
        c.fill(x + 7, y + 21, x + 69, y + 125, 0xFF3C2B1B);
        if (client != null && client.player != null) {
            var lista = handler.outfits();
            if (seleccionado >= 0 && seleccionado < lista.size()) {
                dibujarOutfit(c, client.player, lista.get(seleccionado), x + 7, y + 21, x + 69, y + 125, anguloVista, mouseY);
            } else {
                InventoryScreen.drawEntity(c, x + 7, y + 21, x + 69, y + 125, 40, 0.0625F, mouseX, mouseY, client.player);
            }
        }
        for (Slot s : handler.slots) {
            if (!s.isEnabled()) continue;
            int sx = x + s.x - 1, sy = y + s.y - 1;
            c.fill(sx, sy, sx + 18, sy + 18, 0xFF6B5136);
            c.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFC9A877);
            c.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0xFFA58558);
        }
        for (int z = 0; z < 4; z++) {
            int sy = y + RopaScreenHandler.Y_ARMADURA + z * RopaScreenHandler.PASO_ARMADURA + 8;
            c.fill(x + RopaScreenHandler.X_REAL + 17, sy, x + RopaScreenHandler.X_COSM - 1, sy + 1, 0xFF6B5136);
        }
    }

    @Override
    protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        int tinta = EstiloPergamino.TEXTO;
        c.drawText(textRenderer, title, titleX, titleY, tinta, false);
        c.drawText(textRenderer, Text.translatable("modamod.ropa.prendas"), RopaScreenHandler.X_MOD - 1, 16, tinta, false);
        c.drawText(textRenderer, Text.translatable("modamod.ropa.real"), RopaScreenHandler.X_REAL, 16, tinta, false);
        c.drawText(textRenderer, Text.translatable("modamod.ropa.cosmetico"), RopaScreenHandler.X_COSM - 4, 16, tinta, false);
        c.drawText(textRenderer, Text.translatable("modamod.guardarropas.lista"), X_LISTA, 8, tinta, false);
        if (handler.outfits().isEmpty()) {
            c.drawText(textRenderer, Text.translatable("modamod.guardarropas.vacio"), X_LISTA, Y_FILAS + 4, EstiloPergamino.TEXTO_APAGADO, false);
        }
    }

    /**
     * El jugador con las piezas de un outfit guardado puestas de mentira (2026-10-21): se arma un inventario con el formato viejo del
     * borrador para reusar {@link #dibujarConOutfit} (armadura, sombrero, calzado y delantal van por sus slots; el resto, a la lista).
     */
    static void dibujarOutfit(DrawContext context, PlayerEntity jugador, GuardarropasBlockEntity.Outfit o,
                              int x1, int y1, int x2, int y2, float angulo, int mouseY) {
        SimpleInventory inv = new SimpleInventory(GuardarropasBlockEntity.TAMANO);
        List<ItemStack> prendas = new ArrayList<>();
        for (var e : o.piezas().entrySet()) {
            String k = e.getKey();
            if (k.startsWith("armadura#")) inv.setStack(GuardarropasBlockEntity.ARMADURA_INICIO + Integer.parseInt(k.substring(9)), e.getValue());
            else if (k.startsWith("cosmetico#")) continue;
            else if (k.startsWith("head/sombrero")) inv.setStack(GuardarropasBlockEntity.SLOT_SOMBRERO, e.getValue());
            else if (k.startsWith("socks/calzado")) inv.setStack(GuardarropasBlockEntity.SLOT_CALZADO, e.getValue());
            else if (k.startsWith("torso/delantal")) inv.setStack(GuardarropasBlockEntity.SLOT_DELANTAL, e.getValue());
            else prendas.add(e.getValue());
        }
        dibujarConOutfit(context, jugador, inv, prendas, x1, y1, x2, y2, angulo, mouseY);
    }

    /**
     * El preview del jugador con las prendas Y la armadura de {@code inv}
     * (2026-09-30, columna de armadura): la armadura se pone de mentira en
     * el inventario del jugador SOLO del cliente, directo en la lista (sin
     * equipStack: nada de sonidos ni eventos), y se devuelve en el mismo
     * frame. Una pieza vacía en el outfit deja ver la que el jugador tiene
     * puesta, igual que "Equipar" no desequipa nada.
     */
    static void dibujarConOutfit(DrawContext context, PlayerEntity jugador, net.minecraft.inventory.Inventory inv,
                                 List<ItemStack> prendas, int x1, int y1, int x2, int y2,
                                 float angulo, int mouseY) {
        var armadura = jugador.getInventory().armor;
        ItemStack[] antes = new ItemStack[armadura.size()];
        for (int i = 0; i < antes.length; i++) antes[i] = armadura.get(i);
        // El delantal (2026-10-11) se dibuja con el resto de la ropa.
        ItemStack delantal = inv.getStack(GuardarropasBlockEntity.SLOT_DELANTAL);
        if (!delantal.isEmpty()) {
            prendas = new java.util.ArrayList<>(prendas);
            prendas.add(delantal);
        }
        GarmentFeatureRenderer.previewOverride = prendas;
        ItemStack sombrero = inv.getStack(GuardarropasBlockEntity.SLOT_SOMBRERO);
        GarmentFeatureRenderer.sombreroOverride = sombrero.isEmpty() ? null : sombrero;
        ItemStack calzado = inv.getStack(GuardarropasBlockEntity.SLOT_CALZADO);
        GarmentFeatureRenderer.calzadoOverride = calzado.isEmpty() ? null : calzado;
        try {
            for (int i = 0; i < GuardarropasBlockEntity.SLOTS_ARMADURA.length; i++) {
                ItemStack pieza = inv.getStack(GuardarropasBlockEntity.ARMADURA_INICIO + i);
                if (!pieza.isEmpty()) armadura.set(GuardarropasBlockEntity.SLOTS_ARMADURA[i].getEntitySlotId(), pieza);
            }
            PreviewJugador.dibujar(context, jugador, x1, y1, x2, y2, 35, angulo, (float) mouseY);
        } finally {
            GarmentFeatureRenderer.previewOverride = null;
            GarmentFeatureRenderer.sombreroOverride = null;
            GarmentFeatureRenderer.calzadoOverride = null;
            for (int i = 0; i < antes.length; i++) armadura.set(i, antes[i]);
        }
    }

    /** Marco del slot del delantal (2026-10-11), debajo del del calzado. */
    static void marcoDelantal(DrawContext c, int ox, int oy) {
        int x0 = ox + GuardarropasScreenHandler.X_ARMADURA, y0 = oy + GuardarropasScreenHandler.Y_DELANTAL;
        c.fill(x0 - 1, y0 - 1, x0 + 17, y0 + 17, 0xFF2A180C);
        c.fill(x0, y0, x0 + 16, y0 + 16, 0xFF5A4028);
        c.drawTexture(Identifier.of("modamod", "textures/gui/slot/delantal.png"), x0, y0, 0, 0, 16, 16, 16, 16);
    }

    /** Marco del slot del calzado (2026-10-08), debajo del del sombrero. */
    static void marcoCalzado(DrawContext c, int ox, int oy) {
        int x0 = ox + GuardarropasScreenHandler.X_ARMADURA, y0 = oy + GuardarropasScreenHandler.Y_CALZADO;
        c.fill(x0 - 1, y0 - 1, x0 + 17, y0 + 17, 0xFF2A180C);
        c.fill(x0, y0, x0 + 16, y0 + 16, 0xFF5A4028);
        c.drawTexture(Identifier.of("modamod", "textures/gui/slot/calzado.png"), x0, y0, 0, 0, 16, 16, 16, 16);
    }

    /** Marco del slot del sombrero (2026-10-05), dibujado por código como los de la grilla de la chaqueta. */
    static void marcoSombrero(DrawContext c, int ox, int oy) {
        int x0 = ox + GuardarropasScreenHandler.X_ARMADURA, y0 = oy + GuardarropasScreenHandler.Y_SOMBRERO;
        c.fill(x0 - 1, y0 - 1, x0 + 17, y0 + 17, 0xFF2A180C);
        c.fill(x0, y0, x0 + 16, y0 + 16, 0xFF5A4028);
        c.drawTexture(Identifier.of("modamod", "textures/gui/slot/sombrero.png"), x0, y0, 0, 0, 16, 16, 16, 16);
    }

}
