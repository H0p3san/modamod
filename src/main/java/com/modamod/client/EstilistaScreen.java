package com.modamod.client;

import com.modamod.estilista.EstilistaBlockEntity;
import com.modamod.estilista.EstilistaScreenHandler;
import com.modamod.estilista.GuardarDisenoPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * La Estilista automática (2026-10-05, etapa 3) en la ventana de 560×408 del plan de GUIs (2026-10-12, "dale vamos con
 * todo"): el visor grande de la Mesa de estilado (con su tira de enfoques) a la izquierda y las pestañas del editor
 * (Aplique / Correa / Acabado) en la columna derecha, que ahora trae también el almacén de plantillas, retazos y objetos
 * y lo que dice el diseño. Cabecera a la derecha del inventario: Entrada grande → Aplicar → barra → Salida grande, y
 * abajo los contadores de hilo y cuero (se cargan soltando el ítem encima, con shift-click o con la mano en el bloque).
 * A la izquierda del inventario, el diseño: el último por prenda y los guardados con nombre.
 */
public class EstilistaScreen extends EstiladoScreen {

    private static final int FILA = 13, FILAS_VISIBLES = 3;
    private net.minecraft.client.gui.widget.TextFieldWidget txtNombre;
    /** El diseño de la lista elegido para exportar (el último que se cargó con click izquierdo; 2026-10-25). */
    private int disenoElegido = -1;
    private final EstilistaScreenHandler yo;
    private ButtonWidget btnCatAtras, btnCategoria, btnCatAdelante;
    /** Primera fila visible de la lista de diseños con nombre (la rueda sobre la lista la corre). */
    private int desplazamiento = 0;
    /** La última pestaña con memoria propia (0..2): las Bolsas (3) no guardan nada. */
    private int pestanaConMemoria = 0;

    public EstilistaScreen(EstilistaScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.yo = handler;
    }

    @Override
    void alCambiarPestana(int antes, int ahora) {
        if (ahora > 2) return;
        if (pestanaConMemoria != ahora) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.modamod.estilista.EditorBolsaPayload(
                    yo.host.getPos(), com.modamod.estilista.EditorBolsaPayload.CAMBIAR_PESTANA, pestanaConMemoria, ahora,
                    net.minecraft.util.Identifier.of("minecraft", "air")));
        }
        pestanaConMemoria = ahora;
    }

    @Override
    boolean sinSlotPrenda() { return true; }

    @Override
    boolean conBolsas() { return true; }

    // ── selectores contextuales (2026-10-12, "bolsa de apliques, bolsa de retazos y bolsa de moldes mas controles contextuales") ──
    private record Ent(ItemStack s, int idx, net.minecraft.util.Identifier id) { }
    /** modo: ELEGIR (de una bolsa), TRIM o MATERIAL; tipo = la bolsa; slot = el casillero del editor que marca lo elegido. */
    private record Fila(String clave, int modo, int tipo, int slot, java.util.function.Supplier<java.util.List<Ent>> ents) { }
    private static final int COLS = 10;
    private final int[] pag = new int[3];
    private java.util.List<Ent> trims, materiales;

    private java.util.List<Ent> delaBolsa(int tipo, java.util.function.Predicate<ItemStack> filtro) {
        java.util.List<Ent> l = new java.util.ArrayList<>();
        var b = yo.host.bolsa(tipo);
        for (int i = 0; i < b.size(); i++) {
            if (filtro.test(b.get(i))) l.add(new Ent(b.get(i), i, net.minecraft.registry.Registries.ITEM.getId(b.get(i).getItem())));
        }
        return l;
    }

    private java.util.List<Ent> posibles(boolean trim) {
        if (trims == null) {
            trims = new java.util.ArrayList<>();
            materiales = new java.util.ArrayList<>();
            var ed = yo.host.editor();
            for (net.minecraft.item.Item it : net.minecraft.registry.Registries.ITEM) {
                ItemStack st = new ItemStack(it);
                if (ed.patronDeTrim(st) != null) trims.add(new Ent(st, -1, net.minecraft.registry.Registries.ITEM.getId(it)));
                if (ed.materialDe(st) != null) materiales.add(new Ent(st, -1, net.minecraft.registry.Registries.ITEM.getId(it)));
            }
        }
        java.util.List<Ent> todos = trim ? trims : materiales;
        if (com.modamod.util.MaquinaCreativa.es(yo.host)) return todos;
        // La Estilista normal muestra solo lo que hay en los Insumos, con cuántos quedan (2026-10-24, "muestra todos los
        // trims y materiales disponibles como lo hace la creativa").
        java.util.List<Ent> hay = new java.util.ArrayList<>();
        for (Ent e : todos) {
            int n = yo.host.cantidadEnInsumos(e.s().getItem());
            if (n > 0) hay.add(new Ent(e.s().copyWithCount(n), -1, e.id()));
        }
        return hay;
    }

    private java.util.List<Fila> filas() {
        int B = com.modamod.estilista.EditorBolsaPayload.ELEGIR;
        int molde = com.modamod.estilado.EstiladoBlockEntity.SLOT_MOLDE, retazo = com.modamod.estilado.EstiladoBlockEntity.SLOT_RETAZO;
        if (pestana == 2 && modoDisp == DisposicionEstilista.Modo.COLOR) {
            return java.util.List.of(new Fila("retazos", B, EstilistaBlockEntity.BOLSA_RETAZOS, retazo,
                    () -> delaBolsa(EstilistaBlockEntity.BOLSA_RETAZOS, x -> true)));
        }
        return switch (pestana) {
            case 0 -> java.util.List.of(
                    new Fila("apliques", B, EstilistaBlockEntity.BOLSA_APLIQUES, molde, () -> delaBolsa(EstilistaBlockEntity.BOLSA_APLIQUES, x -> true)),
                    new Fila("retazos", B, EstilistaBlockEntity.BOLSA_RETAZOS, retazo, () -> delaBolsa(EstilistaBlockEntity.BOLSA_RETAZOS, x -> true)));
            case 1 -> java.util.List.of(
                    new Fila("correas", B, EstilistaBlockEntity.BOLSA_MOLDES, molde,
                            () -> delaBolsa(EstilistaBlockEntity.BOLSA_MOLDES, x -> x.getItem() instanceof com.modamod.correa.MoldeCorreaItem)),
                    new Fila("retazos", B, EstilistaBlockEntity.BOLSA_RETAZOS, retazo, () -> delaBolsa(EstilistaBlockEntity.BOLSA_RETAZOS, x -> true)));
            case 2 -> java.util.List.of(
                    new Fila("texturas", B, EstilistaBlockEntity.BOLSA_MOLDES, molde,
                            () -> delaBolsa(EstilistaBlockEntity.BOLSA_MOLDES, x -> x.getItem() instanceof com.modamod.item.MoldeTexturaItem)),
                    new Fila("trims", com.modamod.estilista.EditorBolsaPayload.TRIM, -1, molde, () -> posibles(true)),
                    new Fila("materiales", com.modamod.estilista.EditorBolsaPayload.MATERIAL, -1,
                            com.modamod.estilado.EstiladoBlockEntity.SLOT_MATERIAL, () -> posibles(false)));
            default -> java.util.List.of();
        };
    }

    private static java.util.List<com.modamod.modelado.ZonasModelado.Enfoque> zonasDe(ItemStack s) {
        var E = com.modamod.modelado.ZonasModelado.Enfoque.class;
        var CAB = com.modamod.modelado.ZonasModelado.Enfoque.CABEZA;
        var TOR = com.modamod.modelado.ZonasModelado.Enfoque.TORSO;
        var BRA = com.modamod.modelado.ZonasModelado.Enfoque.BRAZOS;
        var CIN = com.modamod.modelado.ZonasModelado.Enfoque.CINTURA;
        var PIE = com.modamod.modelado.ZonasModelado.Enfoque.PIERNAS;
        var PIS = com.modamod.modelado.ZonasModelado.Enfoque.PIES;
        var TODO = com.modamod.modelado.ZonasModelado.Enfoque.ENTERO;
        if (s.isEmpty()) return java.util.List.of(TODO);
        if (s.getItem() instanceof com.modamod.item.BandaItem) {
            return com.modamod.item.BandaItem.zona(s) == com.modamod.item.BandaZona.CUELLO ? java.util.List.of(CAB, TOR, TODO) : java.util.List.of(CIN, TODO);
        }
        if (s.getItem() instanceof com.modamod.item.SombreroBrujaItem) return java.util.List.of(CAB, TODO);
        if (s.getItem() instanceof com.modamod.item.BorcegosItem) return java.util.List.of(PIS, PIE, TODO);
        if (s.getItem() instanceof com.modamod.item.CalientabrazosItem) return java.util.List.of(BRA, TODO);
        if (s.getItem() instanceof com.modamod.guardarropas.MaletinItem) return java.util.List.of(CIN, TODO);
        if (s.getItem() instanceof com.modamod.item.PantalonItem || s.getItem() instanceof com.modamod.item.PolleraItem) return java.util.List.of(CIN, PIE, TODO);
        if (s.getItem() instanceof com.modamod.item.DelantalItem) return java.util.List.of(TOR, CIN, TODO);
        if (s.getItem() instanceof com.modamod.item.CapaItem) return java.util.List.of(TOR, TODO);
        String n = s.getItem().getClass().getSimpleName();
        if (n.contains("Remera")) return java.util.List.of(TOR, BRA, TODO);
        return java.util.List.of(PIE, PIS, TODO);
    }

    /** Lo elegido ahora (2026-10-13, "un slot donde poner los apliques seleccionados"): dos casilleros con el ítem y su nombre. */
    private void dibujarElegidos(DrawContext c) {
        var ed = yo.host.editor();
        DisposicionEstilista.R er = disp.get("elegidos");
        if (er == null) return;
        int[] slots = modoDisp == DisposicionEstilista.Modo.COLOR
                ? new int[] { com.modamod.estilado.EstiladoBlockEntity.SLOT_RETAZO }
                : modoDisp == DisposicionEstilista.Modo.ACABADO
                ? new int[] { com.modamod.estilado.EstiladoBlockEntity.SLOT_MOLDE, com.modamod.estilado.EstiladoBlockEntity.SLOT_MATERIAL }
                : new int[] { com.modamod.estilado.EstiladoBlockEntity.SLOT_MOLDE, com.modamod.estilado.EstiladoBlockEntity.SLOT_RETAZO };
        for (int k = 0; k < slots.length; k++) {
            int ix = this.x + er.x() + 1 + k * 112, iy = this.y + er.y();
            ItemStack s = ed.getStack(slots[k]);
            c.fill(ix - 1, iy - 1, ix + 19, iy + 19, 0xFF2A180C);
            c.fill(ix, iy, ix + 18, iy + 18, 0xFF6B5A78);
            if (!s.isEmpty()) c.drawItem(s, ix + 1, iy + 1);
            String nombre = s.isEmpty() ? "-" : s.getName().getString();
            c.drawText(textRenderer, textRenderer.trimToWidth(nombre, 70), ix + 22, iy + 5, EstiloPergamino.TEXTO, false);
            // Chincheta: fija lo elegido (no se cambia al clickear otro de la lista).
            int cx = ix + 96, cy = iy + 5;
            boolean fijo = pestana <= 2 && yo.host.fijado(pestana, slots[k] == com.modamod.estilado.EstiladoBlockEntity.SLOT_MOLDE ? 0 : slots[k] == com.modamod.estilado.EstiladoBlockEntity.SLOT_RETAZO ? 1 : 2);
            TinturasScreen.dibujarChinchetaLila(c, cx - 2, cy - 2, fijo);
        }
    }

    private void dibujarSelectores(DrawContext c, int mx, int my) {
        var filas = filas();
        for (int r = 0; r < filas.size(); r++) {
            Fila f = filas.get(r);
            var l = f.ents().get();
            DisposicionEstilista.R sr = disp.get("sel" + r);
            if (sr == null) continue;
            int y0 = this.y + sr.y(), X_SEL = sr.x();
            c.drawText(textRenderer, Text.translatable("modamod.estilado.sel." + f.clave()), this.x + X_SEL, y0, EstiloPergamino.TEXTO, false);
            int maxPag = Math.max(0, (l.size() - 1) / COLS);
            pag[r] = Math.max(0, Math.min(pag[r], maxPag));
            c.drawText(textRenderer, "<", this.x + X_SEL + 6, y0 + 15, 0xFF4B2E83, false);
            c.drawText(textRenderer, ">", this.x + X_SEL + 24 + COLS * 18, y0 + 15, 0xFF4B2E83, false);
            if (l.isEmpty()) {
                c.drawText(textRenderer, Text.translatable("modamod.estilado.sel.vacio"), this.x + X_SEL + 20, y0 + 15, 0xFF7A6A52, false);
                continue;
            }
            ItemStack puesto = yo.host.editor().getStack(f.slot());
            for (int k = 0; k < COLS && pag[r] * COLS + k < l.size(); k++) {
                Ent e = l.get(pag[r] * COLS + k);
                int ix = this.x + X_SEL + 20 + k * 18, iy = y0 + 10;
                boolean sel = !puesto.isEmpty() && (f.modo() == com.modamod.estilista.EditorBolsaPayload.ELEGIR
                        ? ItemStack.areItemsAndComponentsEqual(puesto, e.s()) : puesto.getItem() == e.s().getItem());
                c.fill(ix, iy, ix + 18, iy + 18, sel ? 0xFF7B4FA8 : 0xFF2A180C);
                c.fill(ix + 1, iy + 1, ix + 17, iy + 17, mx >= ix && mx < ix + 18 && my >= iy && my < iy + 18 ? 0xFFA38968 : 0xFF8B7355);
                c.drawItem(e.s(), ix + 1, iy + 1);
                if (e.s().getCount() > 1) c.drawItemInSlot(textRenderer, e.s(), ix + 1, iy + 1);
            }
        }
    }

    /** El ítem bajo el mouse en los selectores: {fila, columna} (columna -1 = flecha izquierda, COLS = derecha) o null. */
    private int[] selectorBajo(double mx, double my) {
        var filas = filas();
        for (int r = 0; r < filas.size(); r++) {
            DisposicionEstilista.R sr = disp.get("sel" + r);
            if (sr == null) continue;
            int y0 = this.y + sr.y() + 10;
            if (my < y0 || my >= y0 + 18) continue;
            double rx = mx - (this.x + sr.x());
            if (rx >= 0 && rx < 20) return new int[] {r, -1};
            if (rx >= 20 + COLS * 18 && rx < 40 + COLS * 18) return new int[] {r, COLS};
            if (rx >= 20 && rx < 20 + COLS * 18) return new int[] {r, (int) ((rx - 20) / 18)};
        }
        return null;
    }

    private int pagDis = 0;

    /** Los diseños de la bolsa que sirven para la prenda de la muestra (todos si no hay muestra): índices en la bolsa. */
    private java.util.List<Integer> disenosDeEstaPrenda() {
        java.util.List<Integer> l = new java.util.ArrayList<>();
        var todos = yo.host.disenosItem();
        net.minecraft.item.Item tipo = yo.host.tipoDeReferencia();
        String id = tipo == null ? null : net.minecraft.registry.Registries.ITEM.getId(tipo).toString();
        for (int i = 0; i < todos.size(); i++) {
            var c = todos.get(i).get(com.modamod.item.ModamodComponents.DISENO_ESTILO);
            if (c != null && (id == null || id.equals(c.getString("Prenda")))) l.add(i);
        }
        return l;
    }

    private void dibujarListaDisenos(DrawContext c, EstilistaBlockEntity be, int mx, int my) {
        DisposicionEstilista.R ay = disp.get("depositoAyuda");
        int ty = this.y + ay.y();
        for (var linea : textRenderer.wrapLines(Text.translatable("modamod.estilista.disenos.deposito"), ay.w())) {
            c.drawText(textRenderer, linea, this.x + ay.x(), ty, 0xFF6B5A78, false);
            ty += 10;
        }
        net.minecraft.item.Item tipo = be.tipoDeReferencia();
        ItemStack u = tipo == null ? null : be.diseno(new ItemStack(tipo));
        DisposicionEstilista.R ur = disp.get("dUltimo");
        c.fill(this.x + ur.x(), this.y + ur.y(), this.x + ur.x() + ur.w(), this.y + ur.y() + ur.h(), 0xFFD2B47F);
        c.fill(this.x + ur.x(), this.y + ur.y(), this.x + ur.x() + 2, this.y + ur.y() + ur.h(), 0xFF7B4FA8);
        String res = u == null ? Text.translatable("modamod.estilista.disenos.ninguno").getString() : EstilistaBlockEntity.resumen(u).getString();
        c.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("modamod.estilista.disenos.ultimo").getString() + " " + res, ur.w() - 8),
                this.x + ur.x() + 5, this.y + ur.y() + 4, u == null ? 0xFF7A6A52 : 0xFF4B2E83, false);
        var idx = disenosDeEstaPrenda();
        int paginas = Math.max(1, (idx.size() + 5) / 6);
        pagDis = Math.max(0, Math.min(pagDis, paginas - 1));
        var todos = be.disenosItem();
        if (idx.isEmpty()) {
            DisposicionEstilista.R d0 = disp.get("d0");
            c.drawText(textRenderer, Text.translatable("modamod.estilista.disenos.vacio_tab"), this.x + d0.x(), this.y + d0.y() + 4, 0xFF7A6A52, false);
        }
        for (int k = 0; k < 6 && pagDis * 6 + k < idx.size(); k++) {
            DisposicionEstilista.R r = disp.get("d" + k);
            ItemStack d = todos.get(idx.get(pagDis * 6 + k));
            int rx = this.x + r.x(), ry = this.y + r.y();
            boolean sobre = mx >= rx && mx < rx + r.w() && my >= ry && my < ry + r.h();
            IconoDiseno.fila(c, textRenderer, rx, ry, r.w(), r.h(), sobre || idx.get(pagDis * 6 + k) == disenoElegido, d, null);
        }
        DisposicionEstilista.R pg = disp.get("dPag");
        c.drawText(textRenderer, "<", this.x + pg.x() + 4, this.y + pg.y() + 4, 0xFF4B2E83, false);
        c.drawText(textRenderer, ">", this.x + pg.x() + pg.w() - 10, this.y + pg.y() + 4, 0xFF4B2E83, false);
        c.drawCenteredTextWithShadow(textRenderer, (pagDis + 1) + "/" + paginas, this.x + pg.x() + pg.w() / 2, this.y + pg.y() + 4, EstiloPergamino.TEXTO_CLARO);
    }

    private boolean clickListaDisenos(double mx, double my, int boton) {
        var idx = disenosDeEstaPrenda();
        int paginas = Math.max(1, (idx.size() + 5) / 6);
        DisposicionEstilista.R pg = disp.get("dPag");
        if (pg != null && my >= this.y + pg.y() && my < this.y + pg.y() + pg.h()) {
            if (mx >= this.x + pg.x() && mx < this.x + pg.x() + 20) { pagDis = Math.max(0, pagDis - 1); return true; }
            if (mx >= this.x + pg.x() + pg.w() - 20 && mx < this.x + pg.x() + pg.w()) { pagDis = Math.min(paginas - 1, pagDis + 1); return true; }
        }
        for (int k = 0; k < 6 && pagDis * 6 + k < idx.size(); k++) {
            DisposicionEstilista.R r = disp.get("d" + k);
            if (r != null && mx >= this.x + r.x() && mx < this.x + r.x() + r.w() && my >= this.y + r.y() && my < this.y + r.y() + r.h()) {
                int accion = boton == 1 ? com.modamod.estilista.EditorBolsaPayload.SACAR_DISENO : com.modamod.estilista.EditorBolsaPayload.CARGAR_DISENO;
                if (boton != 1) disenoElegido = idx.get(pagDis * 6 + k);
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.modamod.estilista.EditorBolsaPayload(
                        yo.host.getPos(), accion, 0, idx.get(pagDis * 6 + k), net.minecraft.util.Identifier.of("minecraft", "air")));
                return true;
            }
        }
        return false;
    }

    /** Click en la chincheta de un casillero elegido: fija o suelta. */
    private boolean clickChincheta(double mx, double my) {
        DisposicionEstilista.R er = disp.get("elegidos");
        if (er == null || pestana > 2) return false;
        int n = modoDisp == DisposicionEstilista.Modo.COLOR ? 1 : 2;
        for (int k = 0; k < n; k++) {
            int cx = this.x + er.x() + 1 + k * 112 + 96, cy = this.y + er.y() + 5;
            if (mx >= cx - 2 && mx < cx + 10 && my >= cy - 2 && my < cy + 10) {
                // k es la posición en la fila; el casillero del editor es: molde 0, retazo 1, material 2.
                int casillero = modoDisp == DisposicionEstilista.Modo.COLOR ? 1 : modoDisp == DisposicionEstilista.Modo.ACABADO && k == 1 ? 2 : k;
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.modamod.estilista.EditorBolsaPayload(
                        yo.host.getPos(), com.modamod.estilista.EditorBolsaPayload.FIJAR, casillero, 0,
                        net.minecraft.util.Identifier.of("minecraft", "air")));
                return true;
            }
        }
        return false;
    }

    private boolean clickSelector(double mx, double my, int boton) {
        int[] h = selectorBajo(mx, my);
        if (h == null) return false;
        Fila f = filas().get(h[0]);
        var l = f.ents().get();
        if (h[1] < 0) { pag[h[0]]--; return true; }
        if (h[1] == COLS) { pag[h[0]]++; return true; }
        int i = pag[h[0]] * COLS + h[1];
        if (i >= l.size()) return true;
        Ent e = l.get(i);
        boolean deBolsa = f.modo() == com.modamod.estilista.EditorBolsaPayload.ELEGIR;
        int accion = deBolsa && boton == 1 ? com.modamod.estilista.EditorBolsaPayload.SACAR : f.modo();
        if (!deBolsa && boton == 1) return true;
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new com.modamod.estilista.EditorBolsaPayload(yo.host.getPos(), accion, f.tipo(), e.idx(), e.id()));
        return true;
    }

    @Override
    int panelExtra() { return 0; }

    @Override
    protected void init() {
        super.init();
        // Selector de categoría como en las otras máquinas (2026-10-13): cambia el tipo de la muestra si no hay prenda en la Entrada.
        int cx0 = this.x + 318;
        btnCatAtras = new EstiloPergamino.BotonPergamino(cx0, this.y + 24, 14, 14, Text.literal("<"),
                b -> this.client.interactionManager.clickButton(this.handler.syncId, EstilistaBlockEntity.BTN_CATEGORIA_ATRAS));
        btnCategoria = new EstiloPergamino.BotonPergamino(cx0 + 14, this.y + 24, 76, 14, Text.empty(), b -> { });
        btnCatAdelante = new EstiloPergamino.BotonPergamino(cx0 + 90, this.y + 24, 14, 14, Text.literal(">"),
                b -> this.client.interactionManager.clickButton(this.handler.syncId, EstilistaBlockEntity.BTN_CATEGORIA_ADELANTE));
        this.addDrawableChild(btnCatAtras);
        this.addDrawableChild(btnCategoria);
        this.addDrawableChild(btnCatAdelante);
        boton("aplicar", EstilistaBlockEntity.BTN_APLICAR, this.x + EstilistaScreenHandler.X_ENTRADA + 36, this.y + EstilistaScreenHandler.Y_IO, 60, 16);
        this.addDrawableChild(new EstiloPergamino.BotonLinea(this.x + 452, this.y + 3, 96, 16, () -> ((EstilistaScreenHandler) handler).host.linea(),
                b -> this.client.interactionManager.clickButton(this.handler.syncId, EstilistaBlockEntity.BTN_LINEA)));
        boton("borrar", EstilistaBlockEntity.BTN_BORRAR, this.x + 8, this.y + 387, 36, 14);   // abajo a la izquierda, como en las otras máquinas (2026-10-14)
        boton("borrar_todo", EstilistaBlockEntity.BTN_BORRAR_TODO, this.x + 46, this.y + 387, 67, 14);
        // Diseño: nombre + "+" abajo de la lista, a la izquierda del inventario.
        int dx = this.x + EstilistaScreenHandler.X_DISENOS, dy = this.y + EstilistaScreenHandler.Y_DISENOS;
        txtNombre = new net.minecraft.client.gui.widget.TextFieldWidget(this.textRenderer, dx, dy + 56, 83, 14,
                Text.translatable("modamod.estilista.disenos.nombre"));
        txtNombre.setMaxLength(40);
        txtNombre.setPlaceholder(Text.translatable("modamod.estilista.disenos.nombre"));
        this.addDrawableChild(txtNombre);
        ButtonWidget guardar = new EstiloPergamino.BotonPergamino(dx + 85, dy + 56, 20, 14, Text.literal("+"), btn -> {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new GuardarDisenoPayload(yo.host.getPos(), txtNombre.getText()));
            txtNombre.setText("");
        });
        guardar.setTooltip(Tooltip.of(Text.translatable("modamod.estilista.disenos.tooltip.guardar")));
        this.addDrawableChild(guardar);
        // Exportar el diseño elegido de la lista con el diseño en blanco del casillero de al lado (2026-10-25, "un botoncito de
        // exportar para exportar un diseñito que lleve el nombre del diseño guardado seleccionado").
        ButtonWidget btnExportar = new EstiloPergamino.BotonPergamino(this.x + 28, this.y + 346, 85, 14,
                Text.translatable("modamod.disenos.exportar"), b -> { net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.modamod.estilista.EditorBolsaPayload(yo.host.getPos(), com.modamod.estilista.EditorBolsaPayload.EXPORTAR_DISENO, 0, disenoElegido, net.minecraft.util.Identifier.of("minecraft", "air"))); });
        btnExportar.setTooltip(Tooltip.of(Text.translatable("modamod.disenos.tooltip.exportar")));
        this.addDrawableChild(btnExportar);
    }

    private void boton(String clave, int id, int bx, int by, int w, int h) {
        ButtonWidget b = new EstiloPergamino.BotonPergamino(bx, by, w, h, Text.translatable("modamod.estilista." + clave),
                btn -> this.client.interactionManager.clickButton(this.handler.syncId, id));
        b.setTooltip(Tooltip.of(Text.translatable("modamod.estilista.tooltip." + clave)));
        this.addDrawableChild(b);
    }

    /** Marco de 40x40 alrededor de un casillero de 16x16 dibujado a {@link EstilistaScreenHandler#ESCALA_IO}. */
    private void marcoGrande(DrawContext c, int sx, int sy) {
        c.fill(sx - 13, sy - 13, sx + 29, sy + 29, 0xFF2A180C);
        c.fill(sx - 12, sy - 12, sx + 28, sy + 28, 0xFF6B5A78);
    }

    @Override
    protected void drawSlot(DrawContext context, net.minecraft.screen.slot.Slot slot) {
        if (!EstilistaScreenHandler.esSlotGrande(slot.x, slot.y)) {
            super.drawSlot(context, slot);
            return;
        }
        float e = EstilistaScreenHandler.ESCALA_IO;
        context.getMatrices().push();
        context.getMatrices().translate(slot.x + 8, slot.y + 8, 0);
        context.getMatrices().scale(e, e, 1f);
        context.getMatrices().translate(-(slot.x + 8), -(slot.y + 8), 0);
        super.drawSlot(context, slot);
        context.getMatrices().pop();
    }

    @Override
    protected boolean isPointWithinBounds(int x, int y, int width, int height, double pointX, double pointY) {
        if (width == 16 && height == 16 && EstilistaScreenHandler.esSlotGrande(x, y)) {
            return super.isPointWithinBounds(x - 12, y - 12, 40, 40, pointX, pointY);
        }
        return super.isPointWithinBounds(x, y, width, height, pointX, pointY);
    }

    /** Un contador de insumo: barra con el nombre y "n/64" adentro. */
    private void contador(DrawContext c, int cx, String clave, int valor, int color) {
        int cy = this.y + EstilistaScreenHandler.Y_CONTADOR, w = EstilistaScreenHandler.ANCHO_CONTADOR, h = EstilistaScreenHandler.ALTO_CONTADOR;
        cx += this.x;
        c.fill(cx - 1, cy - 1, cx + w + 1, cy + h + 1, 0xFF2A180C);
        c.fill(cx, cy, cx + w, cy + h, 0xFF111111);
        int lleno = Math.round(w * (valor / (float) EstilistaBlockEntity.TOPE_CONTADOR));
        if (lleno > 0) {
            c.fill(cx, cy, cx + lleno, cy + h, color);
            c.fill(cx, cy, cx + lleno, cy + 1, 0x40FFFFFF);
        }
        Text t = Text.translatable("modamod.estilista." + clave, valor, EstilistaBlockEntity.TOPE_CONTADOR);
        c.drawText(textRenderer, t, cx + 4, cy + 2, 0xFF2A180C, false);
        c.drawText(textRenderer, t, cx + 3, cy + 1, valor == 0 ? 0xFFFF8A78 : 0xFFFFFFFF, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        yo.verBolsas = pestana == 3 || pestana == 4;
        yo.verInsumos = pestana == 3;
        ItemStack muestra = yo.host.editor().getStack(com.modamod.estilado.EstiladoBlockEntity.SLOT_PRENDA);
        btnCategoria.setMessage(muestra.isEmpty() ? Text.literal("-") : Text.literal(textRenderer.trimToWidth(muestra.getName().getString(), 70)));
        btnCatAtras.active = btnCatAdelante.active = yo.host.getStack(EstilistaBlockEntity.SLOT_PRENDA).isEmpty();
        // Zonas de cámara ligadas a la categoría (2026-10-13, "que esten ligadas a las categorias"): hasta 3 junto al selector.
        var zonas = zonasDe(muestra);
        if (!zonas.contains(enfoque)) enfoque = com.modamod.modelado.ZonasModelado.Enfoque.ENTERO;
        for (int i = 0; i < btnEnfoque.length; i++) {
            if (btnEnfoque[i] == null) continue;
            int k = zonas.indexOf(com.modamod.modelado.ZonasModelado.Enfoque.values()[i]);
            btnEnfoque[i].visible = k >= 0;
            if (k >= 0) { btnEnfoque[i].setX(this.x + 424 + k * 42); btnEnfoque[i].setY(this.y + 24); btnEnfoque[i].setWidth(41); }
        }
        super.render(context, mouseX, mouseY, delta);
        int[] h = selectorBajo(mouseX, mouseY);
        if (h != null && h[1] >= 0 && h[1] < COLS) {
            var l = filas().get(h[0]).ents().get();
            int i = pag[h[0]] * COLS + h[1];
            if (i < l.size()) context.drawItemTooltip(textRenderer, l.get(i).s(), mouseX, mouseY);
        }
    }

    @Override
    protected void drawBackground(DrawContext c, float delta, int mouseX, int mouseY) {
        super.drawBackground(c, delta, mouseX, mouseY);
        EstilistaBlockEntity be = yo.host;
        // Cabecera: Entrada y Salida grandes con la barra de progreso en el medio.
        marcoGrande(c, this.x + EstilistaScreenHandler.X_ENTRADA, this.y + EstilistaScreenHandler.Y_IO);
        if (be.editor().creativa()) {
            // Casillero de objeto de la creativa, en grande abajo de su panel (2026-10-14, "ponelo en el panel exclusivo").
            int ox = this.x + EstilistaScreenHandler.X_OBJETO, oy = this.y + EstilistaScreenHandler.Y_OBJETO;
            marcoGrande(c, ox, oy);
            c.drawText(textRenderer, Text.translatable("modamod.estilado.slot.objeto"), ox - 7, oy - 22, EstiloPergamino.TEXTO_CLARO, false);
        }
        marcoGrande(c, this.x + EstilistaScreenHandler.X_SALIDA, this.y + EstilistaScreenHandler.Y_IO);
        float nivel = switch (be.estado()) {
            case PROCESANDO -> Math.min(1f, be.progreso() / (float) EstilistaBlockEntity.TICKS_PROCESO);
            case LISTO -> 1f;
            default -> 0f;
        };
        int bx = this.x + EstilistaScreenHandler.X_ENTRADA + 104, by = this.y + EstilistaScreenHandler.Y_IO + 5, bw = 92;
        c.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF2A180C);
        c.fill(bx, by, bx + bw, by + 6, 0xFF111111);
        c.fill(bx, by, bx + (int) (bw * nivel), by + 6, EstiloPergamino.tema().claro);
        contador(c, EstilistaScreenHandler.X_HILO, "hilo", be.hilo(), 0xFFB8AC98);
        contador(c, EstilistaScreenHandler.X_CUERO, "cuero", be.cuero(), 0xFF8B5A2B);

        // Columna derecha, abajo de las pestañas: almacén y qué dice el diseño.
        int cx = this.x + DisposicionEstilista.X;
        if (pestana == 4 && disp.get("dUltimo") != null) {
            dibujarListaDisenos(c, be, mouseX, mouseY);
        } else if (pestana == 3 && disp.get("bolsaTexto") != null) {
            String[] claves = {"apliques", "moldes", "retazos"};
            for (int t = 0; t < 3; t++) {
                c.drawText(textRenderer, Text.translatable("modamod.estilado.bolsa." + claves[t]).append(": " + be.bolsa(t).size() + "/" + EstilistaBlockEntity.BOLSA_MAXIMO),
                        cx, this.y + disp.get("bolsaTexto").y() + t * 11, EstiloPergamino.TEXTO, false);
            }
            var ayuda = disp.get("depositoAyuda");
            int dy = this.y + ayuda.y();
            for (var linea : textRenderer.wrapLines(Text.translatable("modamod.estilado.bolsa.ayuda"), ayuda.w())) {
                c.drawText(textRenderer, linea, this.x + ayuda.x(), dy, 0xFF6B5A78, false);
                dy += 10;
            }
            c.drawText(textRenderer, Text.translatable("modamod.estilista.insumos"), cx, this.y + disp.get("insumosRotulo").y(), EstiloPergamino.TEXTO, false);
        } else if (pestana <= 2) {
            dibujarElegidos(c);
            dibujarSelectores(c, mouseX, mouseY);
        }
        // Qué diseño hay para la prenda de la entrada (o, si no hay, para la muestra del editor).
        ItemStack ref = be.getStack(EstilistaBlockEntity.SLOT_PRENDA);
        if (ref.isEmpty()) ref = be.editor().getStack(com.modamod.estilado.EstiladoBlockEntity.SLOT_PRENDA);
        Text info;
        if (ref.isEmpty()) {
            info = Text.translatable("modamod.estilista.info_vacia", be.cuantosDisenos());
        } else {
            ItemStack d = be.diseno(ref);
            info = d == null ? Text.translatable("modamod.estilista.info_sin_diseno")
                    : Text.translatable("modamod.estilista.info_diseno", EstilistaBlockEntity.resumen(d));
        }
        int y = this.y + 270;
        if (pestana <= 2) c.drawText(textRenderer, textRenderer.trimToWidth(info.getString(), 228), cx, y, EstiloPergamino.TEXTO, false);
        ItemStack falta = be.getStack(EstilistaBlockEntity.SLOT_PRENDA).isEmpty() ? ItemStack.EMPTY : be.faltaParaLaEntrada();
        if (!falta.isEmpty() && pestana <= 2) {
            c.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("modamod.estilista.info_falta", falta.getCount(), falta.getName()).getString(), 228),
                    cx, y + 10, 0xFF8B1A1A, false);
        }
        dibujarDisenos(c, be, mouseX, mouseY);
    }

    // ── diseños (2026-10-08, "guarde setting por prenda de manera visible y clara"), ahora a la izquierda del inventario ──

    private void dibujarDisenos(DrawContext c, EstilistaBlockEntity be, int mx, int my) {
        IconoDiseno.marcoBlanco(c, this.x + 9, this.y + 345, this.handler.slots.get(this.handler.slots.size() - 1).hasStack());
        int px = this.x + EstilistaScreenHandler.X_DISENOS, py = this.y + EstilistaScreenHandler.Y_DISENOS;
        int pw = EstilistaScreenHandler.ANCHO_DISENOS;
        net.minecraft.item.Item tipo = be.tipoDeReferencia();
        // El último: automático, se usa al aplicar.
        ItemStack u = tipo == null ? null : be.diseno(new ItemStack(tipo));
        c.fill(px, py, px + pw, py + FILA, 0xFFD2B47F);
        c.fill(px, py, px + 2, py + FILA, 0xFF7B4FA8);
        Text res = u == null ? Text.translatable("modamod.estilista.disenos.ninguno") : EstilistaBlockEntity.resumen(u);
        c.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("modamod.estilista.disenos.ultimo").getString() + " " + res.getString(), pw - 6),
                px + 4, py + 3, u == null ? 0xFF7A6A52 : 0xFF4B2E83, false);
        if (tipo == null) {
            c.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("modamod.estilista.disenos.sin_tipo").getString(), pw), px, py + 18, 0xFF7A6A52, false);
            return;
        }
        c.drawText(textRenderer, textRenderer.trimToWidth(Text.translatable("modamod.estilista.disenos.hint").getString(), pw), px, py + FILA + 4, 0xFF7A6A52, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Con el cursor en el nombre, las teclas son del texto (la E no cierra la pantalla).
        if (txtNombre != null && (txtNombre.keyPressed(keyCode, scanCode, modifiers) || txtNombre.isActive())) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Soltar hilo o cuero sobre su contador.
        int cy = this.y + EstilistaScreenHandler.Y_CONTADOR, w = EstilistaScreenHandler.ANCHO_CONTADOR, h = EstilistaScreenHandler.ALTO_CONTADOR;
        if (my >= cy && my < cy + h) {
            if (mx >= this.x + EstilistaScreenHandler.X_HILO && mx < this.x + EstilistaScreenHandler.X_HILO + w) {
                this.client.interactionManager.clickButton(this.handler.syncId, EstilistaBlockEntity.BTN_CARGAR_HILO);
                return true;
            }
            if (mx >= this.x + EstilistaScreenHandler.X_CUERO && mx < this.x + EstilistaScreenHandler.X_CUERO + w) {
                this.client.interactionManager.clickButton(this.handler.syncId, EstilistaBlockEntity.BTN_CARGAR_CUERO);
                return true;
            }
        }
        if (pestana == 4 && clickListaDisenos(mx, my, button)) return true;
        if (pestana <= 2 && (clickChincheta(mx, my) || clickSelector(mx, my, button))) return true;
        return super.mouseClicked(mx, my, button);
    }
}
