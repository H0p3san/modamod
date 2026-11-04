package com.modamod.client;

import com.modamod.ropa.Cosmeticos;
import com.modamod.ropa.GrillaDeRopa;
import com.modamod.ropa.RopaScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * La parte de Ropa de una pantalla (la de Ropa del jugador, la del Guardarropas y la del Maniquí): la grilla de
 * {@link GrillaDeRopa} con la banda de la fila de la armadura y sus flechas y, en la del jugador, el Ocultar de cada pieza
 * de armadura real. Los slots los pone la pantalla; acá van los botones y el dibujo, en coordenadas de la pantalla.
 */
final class PanelDeRopa {

    private final ScreenHandler handler;
    private final IntSupplier fila;
    /** Esquina de la grilla, dentro de la pantalla. */
    private final int gx, gy;
    /** Con la armadura real del jugador y su Ocultar (no en el Maniquí, cuya armadura es la de la fila). */
    private final boolean conReal;
    private final ButtonWidget[] ocultar = new ButtonWidget[Cosmeticos.ZONAS.length];
    private final ButtonWidget arriba, abajo;

    /** Crea los botones con {@code agregar} (el addDrawableChild de la pantalla); {@code clic} manda el id del botón. */
    PanelDeRopa(ScreenHandler handler, IntSupplier fila, int gx, int gy, boolean conReal, int x, int y,
                Consumer<ButtonWidget> agregar, IntConsumer clic) {
        this.handler = handler;
        this.fila = fila;
        this.gx = gx;
        this.gy = gy;
        this.conReal = conReal;
        if (conReal) {
            for (int z = 0; z < ocultar.length; z++) {
                int id = z;
                int by = y + RopaScreenHandler.Y_ARMADURA + z * RopaScreenHandler.PASO_ARMADURA - 1;
                ocultar[z] = new EstiloPergamino.BotonPergamino(x + RopaScreenHandler.X_REAL + 20, by, 60, 18, Text.empty(), b -> clic.accept(id));
                agregar.accept(ocultar[z]);
            }
        }
        arriba = new EstiloPergamino.BotonPergamino(x, y, 12, 9, Text.literal("▲"), b -> clic.accept(GrillaDeRopa.BTN_FILA_ARRIBA));
        abajo = new EstiloPergamino.BotonPergamino(x, y, 12, 9, Text.literal("▼"), b -> clic.accept(GrillaDeRopa.BTN_FILA_ABAJO));
        for (ButtonWidget b : new ButtonWidget[]{arriba, abajo}) {
            b.setTooltip(Tooltip.of(Text.translatable("modamod.ropa.fila_armadura")));
            agregar.accept(b);
        }
    }

    /** Pone las flechas al lado de la fila de la armadura y el Ocultar con su estado; una vez por cuadro. */
    void actualizar(int x, int y) {
        int f = fila.getAsInt();
        int fy = y + gy + f * GrillaDeRopa.PASO_FILA - 1;
        arriba.setPosition(x + gx - GrillaDeRopa.FLECHAS, fy);
        abajo.setPosition(x + gx - GrillaDeRopa.FLECHAS, fy + 9);
        arriba.active = f > 0;
        abajo.active = f < Cosmeticos.FILAS;
        if (!conReal) return;
        var jugador = MinecraftClient.getInstance().player;
        Cosmeticos cosm = jugador != null ? Cosmeticos.de(jugador) : Cosmeticos.VACIO;
        for (int z = 0; z < ocultar.length; z++) {
            boolean activo = cosm.oculta(z);
            ocultar[z].setMessage(Text.literal(activo ? "[x] " : "[ ] ").append(Text.translatable("modamod.ropa.ocultar"))
                    .formatted(activo ? Formatting.GREEN : Formatting.GRAY));
        }
    }

    /** El marco de cada slot visible de la pantalla y la banda que marca la fila de la armadura. */
    void fondo(DrawContext c, int x, int y) {
        int fy = y + gy + fila.getAsInt() * GrillaDeRopa.PASO_FILA - 2;
        c.fill(x + gx - 2, fy, x + gx + GrillaDeRopa.ANCHO, fy + 20, 0x405B7FA8);
        for (Slot s : handler.slots) {
            if (!s.isEnabled()) continue;
            int sx = x + s.x - 1, sy = y + s.y - 1;
            c.fill(sx, sy, sx + 18, sy + 18, 0xFF6B5136);
            c.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFFC9A877);
            c.fill(sx + 1, sy + 1, sx + 17, sy + 2, 0xFFA58558);
        }
    }

    /** Los títulos de la grilla y, si va, de la armadura real (coordenadas de dentro de la pantalla). */
    void rotulos(DrawContext c, TextRenderer texto) {
        int tinta = EstiloPergamino.TEXTO;
        c.drawText(texto, Text.translatable("modamod.ropa.prendas"), gx - 1, gy - 10, tinta, false);
        if (conReal) c.drawText(texto, Text.translatable("modamod.ropa.real"), RopaScreenHandler.X_REAL, gy - 10, tinta, false);
    }
}
