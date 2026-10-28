package com.modamod.client;

import com.modamod.guardarropas.GuardarropasBlockEntity;
import com.modamod.guardarropas.MaletinScreenHandler;
import com.modamod.guardarropas.Outfits;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * La lista del Maletín (2026-10-21, "no tiene vista previa solo outfits swappeables... no sirve para guardar y combinar solo para
 * vestir"): un botón por outfit de la colección vinculada; tocarlo te lo pone. Sin slots, sin muñeco y sin guardar.
 */
public class MaletinScreen extends HandledScreen<MaletinScreenHandler> {

    private static final int ANCHO = 190, FILA = 20, ARRIBA = 28;
    private final ButtonWidget[] botones = new ButtonWidget[Outfits.MAXIMO];

    public MaletinScreen(MaletinScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = ANCHO;
        this.backgroundHeight = ARRIBA + Outfits.MAXIMO * FILA + 8;
        this.titleX = 8;
        this.titleY = 8;
        this.playerInventoryTitleY = -100;
    }

    @Override
    protected void init() {
        EstiloPergamino.usarTema(EstiloPergamino.Tema.PLATA);
        super.init();
        for (int i = 0; i < botones.length; i++) {
            int id = GuardarropasBlockEntity.BTN_PONER_BASE + i;
            botones[i] = addDrawableChild(new EstiloPergamino.BotonPergamino(this.x + 8, this.y + ARRIBA + i * FILA, ANCHO - 16, FILA - 3, Text.empty(),
                    b -> { if (client != null && client.interactionManager != null) client.interactionManager.clickButton(handler.syncId, id); }));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var lista = handler.outfits();
        // Alto justo para la lista (centrado otra vez).
        this.backgroundHeight = ARRIBA + Math.max(1, lista.size()) * FILA + 8;
        this.y = (this.height - this.backgroundHeight) / 2;
        for (int i = 0; i < botones.length; i++) {
            botones[i].visible = i < lista.size();
            botones[i].setY(this.y + ARRIBA + i * FILA);
            if (i < lista.size()) botones[i].setMessage(Text.literal(lista.get(i).nombre()));
        }
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y, w = backgroundWidth, h = backgroundHeight;
        c.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF2A1A0E);
        c.fill(x, y, x + w, y + h, 0xFFE3CC9E);
        c.fill(x + 3, y + 3, x + w - 3, y + h - 3, 0xFFEAD8B0);
    }

    @Override
    protected void drawForeground(DrawContext c, int mouseX, int mouseY) {
        EstiloPergamino.titulo(c, this.textRenderer, this.title, this.titleX, this.titleY);
        if (handler.outfits().isEmpty()) {
            c.drawText(textRenderer, Text.translatable("modamod.maletin.lista_vacia"), 10, ARRIBA + 4, EstiloPergamino.TEXTO_APAGADO, false);
        }
    }
}
