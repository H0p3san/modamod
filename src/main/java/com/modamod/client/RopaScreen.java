package com.modamod.client;

import com.modamod.ropa.Cosmeticos;
import com.modamod.ropa.RopaScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Pantalla de Ropa (2026-10-05, "limpiar la gui de tanto ruido... un boton de ropa que te lleve a una gui
 * especifica"): el muñeco, los slots de ropa del mod, la armadura real y la cosmética con sus dos interruptores
 * por pieza ("ver cosmético" y "ocultar"; con "ver cosmético" y el slot vacío, la pieza se ve oculta).
 * Dibujada por código, sin textura de fondo.
 */
public class RopaScreen extends HandledScreen<RopaScreenHandler> {

    private final ButtonWidget[] ver = new ButtonWidget[4];
    private final ButtonWidget[] ocultar = new ButtonWidget[4];

    public RopaScreen(RopaScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = RopaScreenHandler.ANCHO;
        this.backgroundHeight = RopaScreenHandler.ALTO;
        this.titleX = 8;
        this.titleY = 6;
        this.playerInventoryTitleY = -100;
    }

    @Override
    protected void init() {
        super.init();
        for (int z = 0; z < 4; z++) {
            int id = z;
            int y = this.y + RopaScreenHandler.Y_ARMADURA + z * RopaScreenHandler.PASO_ARMADURA - 1;
            ver[z] = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> clic(id))
                    .dimensions(this.x + 196, y, 40, 18).build());
            ocultar[z] = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> clic(4 + id))
                    .dimensions(this.x + 238, y, 40, 18).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("modamod.ropa.inventario"), b -> {
            close();
            client.setScreen(new InventoryScreen(client.player));
        }).dimensions(this.x + 6, this.y + RopaScreenHandler.ALTO - 24, 50, 18).build());
    }

    private void clic(int id) {
        if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (RopaCliente.tecla != null && RopaCliente.tecla.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y, w = backgroundWidth, h = backgroundHeight;
        c.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF2A1A0E);
        c.fill(x, y, x + w, y + h, 0xFFE3CC9E);
        c.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFEAD8B0);

        // Muñeco: ve lo mismo que los demás (con los cosméticos y los interruptores).
        c.fill(x + 6, y + 20, x + 70, y + 126, 0xFF6B5136);
        c.fill(x + 7, y + 21, x + 69, y + 125, 0xFF3C2B1B);
        if (client != null && client.player != null) {
            InventoryScreen.drawEntity(c, x + 7, y + 21, x + 69, y + 125, 40, 0.0625F, mouseX, mouseY, client.player);
        }

        for (Slot s : handler.slots) {
            if (!s.isEnabled()) continue;
            int sx = x + s.x - 1, sy = y + s.y - 1;
            c.fill(sx, sy, sx + 18, sy + 18, 0xFF6B5136);
            c.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFC9A877);
            c.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0xFFA58558);
        }

        // Marcas de cada zona de armadura: la fila une real → cosmético.
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

        Cosmeticos cosm = client != null && client.player != null ? Cosmeticos.de(client.player) : Cosmeticos.VACIO;
        for (int z = 0; z < 4; z++) {
            ver[z].setMessage(etiqueta("modamod.ropa.ver", cosm.veCosmetico(z)));
            ocultar[z].setMessage(etiqueta("modamod.ropa.ocultar", cosm.oculta(z)));
        }
    }

    private static Text etiqueta(String clave, boolean activo) {
        return Text.literal(activo ? "[x] " : "[ ] ").append(Text.translatable(clave))
                .formatted(activo ? Formatting.GREEN : Formatting.GRAY);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        super.render(c, mouseX, mouseY, delta);
        drawMouseoverTooltip(c, mouseX, mouseY);
    }
}
