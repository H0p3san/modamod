package com.modamod.item;

import com.modamod.garment.Parte;
import com.modamod.region.Lado;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Borcegos (2026-10-08, "modelame unos borcegos y agreguemos un slot de calzado"): un par de botas de cuero con
 * cordones y suela gruesa, en el slot de Trinkets {@code socks/calzado}. Como el sombrero y la banda NO es ropa de
 * cuerpo ({@code Garments.esPrenda} falso) y tiene 3 zonas con color y dibujo (cuero, suela, cordones) que se ponen en
 * la Estilista, que también le pone apliques y correas. La altura de la caña, la suela y cómo se lleva el pantalón son
 * componentes que se eligen con los moldes de la Modeladora. La dibuja {@link com.modamod.render.BorcegosRenderer}.
 *
 * <p>Los dos pies son independientes (2026-10-11, "no se puede independizar derecho e izquierdo?"): los componentes de
 * siempre son del pie DERECHO y los {@code _izq} son del izquierdo; si el izquierdo no tiene el suyo, copia al derecho.
 */
public class BorcegosItem extends TrinketItem implements ZonasTenibles {

    /** Cuero marrón oscuro, suela casi negra y cordones crema de fábrica. */
    public static final List<Integer> DE_FABRICA = List.of(0x5A3A24, 0x2E2824, 0xD9CDB0);

    public BorcegosItem(Settings settings) {
        super(settings);
    }

    // ── forma, por pie ────────────────────────────────────────────────────

    public static BorcegoCana cana(ItemStack stack) { return cana(stack, false); }

    public static BorcegoCana cana(ItemStack stack, boolean izq) {
        BorcegoCana c = izq ? stack.get(ModamodComponents.BORCEGO_CANA_IZQ) : null;
        if (c == null) c = stack.get(ModamodComponents.BORCEGO_CANA);
        return c == null ? BorcegoCana.MEDIA : c;
    }

    public static BorcegoSuela suela(ItemStack stack) { return suela(stack, false); }

    public static BorcegoSuela suela(ItemStack stack, boolean izq) {
        BorcegoSuela s = izq ? stack.get(ModamodComponents.BORCEGO_SUELA_IZQ) : null;
        if (s == null) s = stack.get(ModamodComponents.BORCEGO_SUELA);
        return s == null ? BorcegoSuela.CHATA : s;
    }

    /** Malla de fábrica o cuboides (el mismo molde que el sombrero). */
    public static SombreroEstilo estilo(ItemStack stack) {
        SombreroEstilo e = stack.get(ModamodComponents.BORCEGO_ESTILO);
        return e == null ? SombreroEstilo.MALLA : e;
    }

    public static void setEstilo(ItemStack stack, SombreroEstilo v) {
        if (v == SombreroEstilo.MALLA) stack.remove(ModamodComponents.BORCEGO_ESTILO);
        else stack.set(ModamodComponents.BORCEGO_ESTILO, v);
    }

    public static CalzadoPunta punta(ItemStack stack, boolean izq) {
        CalzadoPunta p = izq ? stack.get(ModamodComponents.BORCEGO_PUNTA_IZQ) : null;
        if (p == null) p = stack.get(ModamodComponents.BORCEGO_PUNTA);
        return p == null ? CalzadoPunta.CUERO : p;
    }

    public static CalzadoCierre cierre(ItemStack stack, boolean izq) {
        CalzadoCierre c = izq ? stack.get(ModamodComponents.BORCEGO_CIERRE_IZQ) : null;
        if (c == null) c = stack.get(ModamodComponents.BORCEGO_CIERRE);
        return c == null ? CalzadoCierre.CORDONES : c;
    }

    public static CalzadoDetalle detalle(ItemStack stack, boolean izq) {
        CalzadoDetalle d = izq ? stack.get(ModamodComponents.BORCEGO_DETALLE_IZQ) : null;
        if (d == null) d = stack.get(ModamodComponents.BORCEGO_DETALLE);
        return d == null ? CalzadoDetalle.NINGUNO : d;
    }

    /**
     * Cuántas filas de pierna cubre la caña de ese pie (2026-10-14): la altura de la capellada con la suela de referencia
     * más lo que la suela elegida la levanta (a z = −1, como las calculó el zip); el pantalón con la botamanga por
     * dentro se corta ahí. Sin capellada (chancla, sandalias) no cubre nada.
     */
    public static int alturaCana(ItemStack stack, boolean izq) {
        BorcegoCana c = cana(stack, izq);
        if (c == BorcegoCana.NINGUNA) return 0;
        return Math.max(0, Math.round(c.alturaRef + suela(stack, izq).alzado));
    }

    public static BorcegoBotamanga botamanga(ItemStack stack) { return botamanga(stack, false); }

    public static BorcegoBotamanga botamanga(ItemStack stack, boolean izq) {
        BorcegoBotamanga b = izq ? stack.get(ModamodComponents.BORCEGO_BOTAMANGA_IZQ) : null;
        if (b == null) b = stack.get(ModamodComponents.BORCEGO_BOTAMANGA);
        return b == null ? BorcegoBotamanga.ADENTRO : b;
    }

    /**
     * Escribe {@code valor} en el pie (o los pies) de {@code lado}: AMBAS pone el derecho y suelta el izquierdo (que
     * vuelve a copiarlo); DERECHA congela antes el izquierdo en lo que tenía para que no se mueva; IZQUIERDA escribe
     * solo el suyo.
     */
    private static <T> void escribir(ItemStack stack, ComponentType<T> der, ComponentType<T> izq, T porDefecto,
                                     T valor, Lado lado) {
        T actualDer = stack.get(der) == null ? porDefecto : stack.get(der);
        if (lado == Lado.IZQUIERDA) {
            stack.set(izq, valor);
            return;
        }
        if (lado == Lado.DERECHA && stack.get(izq) == null) stack.set(izq, actualDer);   // congela el izquierdo
        if (valor.equals(porDefecto)) stack.remove(der);
        else stack.set(der, valor);
        if (lado == Lado.AMBAS) stack.remove(izq);
    }

    public static void setCana(ItemStack stack, BorcegoCana v, Lado lado) {
        escribir(stack, ModamodComponents.BORCEGO_CANA, ModamodComponents.BORCEGO_CANA_IZQ, BorcegoCana.MEDIA, v, lado);
    }

    public static void setSuela(ItemStack stack, BorcegoSuela v, Lado lado) {
        escribir(stack, ModamodComponents.BORCEGO_SUELA, ModamodComponents.BORCEGO_SUELA_IZQ, BorcegoSuela.CHATA, v, lado);
    }

    public static void setBotamanga(ItemStack stack, BorcegoBotamanga v, Lado lado) {
        escribir(stack, ModamodComponents.BORCEGO_BOTAMANGA, ModamodComponents.BORCEGO_BOTAMANGA_IZQ,
                BorcegoBotamanga.ADENTRO, v, lado);
    }

    public static void setPunta(ItemStack stack, CalzadoPunta v, Lado lado) {
        escribir(stack, ModamodComponents.BORCEGO_PUNTA, ModamodComponents.BORCEGO_PUNTA_IZQ, CalzadoPunta.CUERO, v, lado);
    }

    public static void setCierre(ItemStack stack, CalzadoCierre v, Lado lado) {
        escribir(stack, ModamodComponents.BORCEGO_CIERRE, ModamodComponents.BORCEGO_CIERRE_IZQ, CalzadoCierre.CORDONES, v, lado);
    }

    public static void setDetalle(ItemStack stack, CalzadoDetalle v, Lado lado) {
        escribir(stack, ModamodComponents.BORCEGO_DETALLE, ModamodComponents.BORCEGO_DETALLE_IZQ, CalzadoDetalle.NINGUNO, v, lado);
    }

    public static void setCana(ItemStack stack, BorcegoCana v) { setCana(stack, v, Lado.AMBAS); }

    public static void setSuela(ItemStack stack, BorcegoSuela v) { setSuela(stack, v, Lado.AMBAS); }

    public static void setBotamanga(ItemStack stack, BorcegoBotamanga v) { setBotamanga(stack, v, Lado.AMBAS); }

    // ── colores y dibujos, por pie ────────────────────────────────────────

    public static List<Integer> colores(ItemStack stack) { return colores(stack, false); }

    public static List<Integer> colores(ItemStack stack, boolean izq) {
        List<Integer> c = izq ? stack.get(ModamodComponents.COLORES_BORCEGOS_IZQ) : null;
        if (c == null || c.size() < 3) c = stack.get(ModamodComponents.COLORES_BORCEGOS);
        return c == null || c.size() < 3 ? DE_FABRICA : c;
    }

    public static List<SombreroPatron> patrones(ItemStack stack) { return patrones(stack, false); }

    public static List<SombreroPatron> patrones(ItemStack stack, boolean izq) {
        List<Integer> p = izq ? stack.get(ModamodComponents.PATRONES_BORCEGOS_IZQ) : null;
        if (p == null) p = stack.get(ModamodComponents.PATRONES_BORCEGOS);
        SombreroPatron[] v = SombreroPatron.values();
        List<SombreroPatron> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int o = p != null && i < p.size() ? p.get(i) : 0;
            out.add(o >= 0 && o < v.length ? v[o] : SombreroPatron.LISO);
        }
        return out;
    }

    @Override public List<Integer> coloresDe(ItemStack stack) { return colores(stack); }

    @Override public List<Integer> coloresDe(ItemStack stack, boolean izq) { return colores(stack, izq); }

    @Override
    public ItemStack conColoresDe(ItemStack stack, int a, int b, int c) {
        // Sin pie elegido: los dos pies (el izquierdo vuelve a copiar al derecho).
        stack.set(ModamodComponents.COLORES_BORCEGOS, List.of(a, b, c));
        stack.remove(ModamodComponents.COLORES_BORCEGOS_IZQ);
        return stack;
    }

    @Override
    public ItemStack conColoresDe(ItemStack stack, int a, int b, int c, boolean izq) {
        if (!izq && stack.get(ModamodComponents.COLORES_BORCEGOS_IZQ) == null) {
            stack.set(ModamodComponents.COLORES_BORCEGOS_IZQ, colores(stack, false));   // congela el izquierdo
        }
        stack.set(izq ? ModamodComponents.COLORES_BORCEGOS_IZQ : ModamodComponents.COLORES_BORCEGOS, List.of(a, b, c));
        return stack;
    }

    @Override public List<SombreroPatron> patronesDe(ItemStack stack) { return patrones(stack); }

    @Override public List<SombreroPatron> patronesDe(ItemStack stack, boolean izq) { return patrones(stack, izq); }

    @Override
    public ItemStack conPatronDe(ItemStack stack, int zona, SombreroPatron patron) {
        conPatronDe(stack, zona, patron, false);
        stack.remove(ModamodComponents.PATRONES_BORCEGOS_IZQ);
        return stack;
    }

    @Override
    public ItemStack conPatronDe(ItemStack stack, int zona, SombreroPatron patron, boolean izq) {
        if (!izq && stack.get(ModamodComponents.PATRONES_BORCEGOS_IZQ) == null) {
            stack.set(ModamodComponents.PATRONES_BORCEGOS_IZQ, ordinales(patrones(stack, false)));   // congela el izquierdo
        }
        List<SombreroPatron> actuales = patrones(stack, izq);
        int[] n = {actuales.get(0).ordinal(), actuales.get(1).ordinal(), actuales.get(2).ordinal()};
        n[zona] = patron.ordinal();
        ComponentType<List<Integer>> tipo = izq ? ModamodComponents.PATRONES_BORCEGOS_IZQ : ModamodComponents.PATRONES_BORCEGOS;
        if (!izq && n[0] == 0 && n[1] == 0 && n[2] == 0) stack.remove(tipo);
        else stack.set(tipo, List.of(n[0], n[1], n[2]));
        return stack;
    }

    private static List<Integer> ordinales(List<SombreroPatron> l) {
        return List.of(l.get(0).ordinal(), l.get(1).ordinal(), l.get(2).ordinal());
    }

    @Override public boolean tienePares() { return true; }

    @Override public String claveZona(ItemStack stack, int i) { return "modamod.borcegos.parte." + (i + 1); }

    /** Marco "de referencia" (el derecho); la Mesa apunta a cada pie con su propio marco, ver {@code marcos}. */
    @Override
    public Parte marco(ItemStack stack) {
        return Parte.PIERNA_DER;
    }

    /**
     * El nombre sale de la caña, con un agregado por la suela (2026-10-23, "todas las botas se llaman igual"): borcegos bajos,
     * botas altas, zapatillas, chatitas... sin caña son sandalias, ojotas (suela chancla) o gladiadoras. El resto, en el tooltip.
     */
    @Override
    public Text getName(ItemStack stack) {
        BorcegoCana cana = cana(stack);
        BorcegoSuela suela = suela(stack);
        String base = cana != BorcegoCana.NINGUNA ? cana.clave
                : suela == BorcegoSuela.CHANCLA ? "ojotas"
                : cierre(stack, false) == CalzadoCierre.GLADIADORA ? "gladiadoras" : "sandalias";
        Text nombre = Text.translatable("item.modamod.borcegos." + base);
        String extra = switch (suela) {
            case PLATAFORMA, PLATAFORMA_TENIS -> "plataforma";
            case CUNA -> "cuna";
            case TACO_CHICO -> cana == BorcegoCana.TACO ? null : "taquito";
            default -> null;
        };
        return extra == null ? nombre : Text.translatable("item.modamod.borcegos.con_" + extra, nombre);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("modamod.borcegos.tooltip", Text.translatable(cana(stack).traduccion()),
                Text.translatable(suela(stack).traduccion()), Text.translatable(botamanga(stack).traduccion()))
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("modamod.calzado.tooltip", Text.translatable(punta(stack, false).traduccion()),
                Text.translatable(cierre(stack, false).traduccion()), Text.translatable(detalle(stack, false).traduccion()))
                .formatted(Formatting.GRAY));
        List<Integer> c = colores(stack);
        for (int i = 0; i < 3; i++) {
            int color = c.get(i);
            tooltip.add(Text.translatable("modamod.borcegos.parte." + (i + 1)).formatted(Formatting.GRAY)
                    .append(Text.literal(" ■■■").styled(s -> s.withColor(TextColor.fromRgb(color)))));
        }
    }
}
