package com.modamod.client;

import com.modamod.ropa.Cosmeticos;
import com.modamod.ropa.RopaScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * La parte de Ropa de una pantalla (la de Ropa del jugador y la del Guardarropas): la grilla de la lista con la fila de la
 * armadura cosmética, sus flechas, la armadura real y el interruptor de ocultarla por pieza. Los slots los
 * pone {@link RopaScreenHandler}; acá van los botones y el dibujo, en coordenadas de la pantalla.
 */
final class PanelDeRopa {

    private final RopaScreenHandler handler;
    private final ButtonWidget[] ocultar = new ButtonWidget[4];
    private final ButtonWidget arriba, abajo;

    /** Crea los botones con {@code agregar} (el addDrawableChild de la pantalla); {@code clic} manda el id del botón. */
    PanelDeRopa(RopaScreenHandler handler, int x, int y, Consumer<ButtonWidget> agregar, IntConsumer clic) {
        this.handler = handler;
        for (int z = 0; z < 4; z++) {
            int id = z;
            int by = y + RopaScreenHandler.Y_ARMADURA + z * RopaScreenHandler.PASO_ARMADURA - 1;
            ocultar[z] = new EstiloPergamino.BotonPergamino(x + 202, by, 60, 18, Text.empty(), b -> clic.accept(id));
            agregar.accept(ocultar[z]);
        }
        arriba = new EstiloPergamino.BotonPergamino(x + RopaScreenHandler.X_FLECHAS, y, 12, 9, Text.literal("▲"),
                b -> clic.accept(RopaScreenHandler.BTN_FILA_ARRIBA));
        abajo = new EstiloPergamino.BotonPergamino(x + RopaScreenHandler.X_FLECHAS, y, 12, 9, Text.literal("▼"),
                b -> clic.accept(RopaScreenHandler.BTN_FILA_ABAJO));
        for (ButtonWidget b : new ButtonWidget[]{arriba, abajo}) {
            b.setTooltip(Tooltip.of(Text.translatable("modamod.ropa.fila_armadura")));
            agregar.accept(b);
        }
    }

    /** Pone las flechas al lado de la fila de la armadura y los interruptores con su estado; una vez por cuadro. */
    void actualizar(int x, int y) {
        int fila = handler.fila();
        int fy = y + RopaScreenHandler.Y_MOD + fila * RopaScreenHandler.PASO_FILA - 1;
        arriba.setPosition(x + RopaScreenHandler.X_FLECHAS, fy);
        abajo.setPosition(x + RopaScreenHandler.X_FLECHAS, fy + 9);
        arriba.active = fila > 0;
        abajo.active = fila < Cosmeticos.FILAS;
        var jugador = MinecraftClient.getInstance().player;
        Cosmeticos cosm = jugador != null ? Cosmeticos.de(jugador) : Cosmeticos.VACIO;
        for (int z = 0; z < 4; z++) {
            ocultar[z].setMessage(etiqueta("modamod.ropa.ocultar", cosm.oculta(z)));
        }
    }

    /** El marco de cada slot visible y la banda que marca la fila de la armadura. */
    void fondo(DrawContext c, int x, int y) {
        int fy = y + RopaScreenHandler.Y_MOD + handler.fila() * RopaScreenHandler.PASO_FILA - 2;
        c.fill(x + RopaScreenHandler.X_MOD - 2, fy, x + RopaScreenHandler.X_MOD + 4 * RopaScreenHandler.PASO_COLUMNA, fy + 20, 0x405B7FA8);
        for (Slot s : handler.slots) {
            if (!s.isEnabled()) continue;
            int sx = x + s.x - 1, sy = y + s.y - 1;
            c.fill(sx, sy, sx + 18, sy + 18, 0xFF6B5136);
            c.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFC9A877);
            c.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0xFFA58558);
        }
    }

    /** Los títulos de la grilla y de la armadura real (coordenadas de dentro de la pantalla). */
    static void rotulos(DrawContext c, TextRenderer texto) {
        int tinta = EstiloPergamino.TEXTO;
        c.drawText(texto, Text.translatable("modamod.ropa.prendas"), RopaScreenHandler.X_MOD - 1, 16, tinta, false);
        c.drawText(texto, Text.translatable("modamod.ropa.real"), RopaScreenHandler.X_REAL, 16, tinta, false);
    }

    private static Text etiqueta(String clave, boolean activo) {
        return Text.literal(activo ? "[x] " : "[ ] ").append(Text.translatable(clave)).formatted(activo ? Formatting.GREEN : Formatting.GRAY);
    }
}
