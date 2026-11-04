package com.modamod.client;

import com.modamod.ropa.RopaScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * Pantalla de Ropa (2026-10-05, "limpiar la gui de tanto ruido... un boton de ropa que te lleve a una gui
 * especifica"): el muñeco y la parte de Ropa ({@link PanelDeRopa}: la lista de 16 con la fila de la armadura cosmética,
 * la armadura real y sus interruptores). Dibujada por código, sin textura de fondo.
 */
public class RopaScreen extends HandledScreen<RopaScreenHandler> {

    private PanelDeRopa panel;

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
        panel = new PanelDeRopa(handler, handler::fila, com.modamod.ropa.RopaScreenHandler.X_MOD, com.modamod.ropa.RopaScreenHandler.Y_MOD,
                true, this.x, this.y, this::addDrawableChild, this::clic);
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
        panel.fondo(c, x, y);
    }

    @Override
    protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        c.drawText(textRenderer, title, titleX, titleY, EstiloPergamino.TEXTO, false);
        panel.rotulos(c, textRenderer);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        panel.actualizar(this.x, this.y);
        super.render(c, mouseX, mouseY, delta);
        drawMouseoverTooltip(c, mouseX, mouseY);
    }
}
