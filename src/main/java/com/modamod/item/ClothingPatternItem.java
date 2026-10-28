package com.modamod.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item de patrón reusable, como los patrones de estandarte de vanilla:
 * cada uno lleva un PATTERN_ID fijo (ej. "modamod:stripe_top") que
 * se transfiere a la prenda en la estación de personalización. El item
 * de patrón NO se consume al usarlo (igual que un patrón de estandarte
 * en el Telar vanilla) — lo que se consume es el tinte.
 *
 * A diferencia de los moldes (cambian el CORTE), un patrón pinta un motivo
 * sobre la tela y necesita un tinte puesto junto a él para saber de qué
 * color — por eso la categoría y el texto de ayuda son distintos, y el
 * color de la categoría (AQUA) es a propósito el opuesto del de los moldes
 * (GOLD): son las dos familias que más se confundían en el inventario.
 */
public class ClothingPatternItem extends Item {

    public final Identifier patternId;
    /** Cómo generar la máscara de este patrón — ver {@link com.modamod.render.PatronGenerador}. */
    public final com.modamod.render.PatronGenerador.Forma forma;
    /** Grosor en tamaño GRANDE (px, escala 8x); Mediano/Chico lo escalan (§{@link TamanoPatron}). */
    public final int grosorBase;
    private final boolean visibleEnListas;

    /**
     * La Estación de Tintes guarda solo el {@code Identifier} en su lista
     * de "aprendidos" (es lo único que viaja en NBT/componente), pero la
     * GUI necesita el ITEM de vuelta para dibujar su ícono/nombre — este
     * registro chiquito evita tener que pedirle a `ModamodItems` una
     * vuelta completa por todos los items registrados cada frame.
     */
    private static final Map<Identifier, ClothingPatternItem> POR_ID = new HashMap<>();

    /**
     * Motivo repetido (corazones, estrellas...) — null en los moldes de
     * rayas. Con motivo, {@link #forma} no se usa y {@link #grosorBase}
     * pasa a ser la escala del sprite (pixeles del atlas por pixel del
     * dibujo, en tamaño GRANDE). Ver {@code PatronGenerador#mascaraDeCapa}.
     */
    @org.jetbrains.annotations.Nullable
    public final com.modamod.render.Motivo motivo;

    public ClothingPatternItem(Settings settings, Identifier patternId,
                               com.modamod.render.PatronGenerador.Forma forma, int grosorBase) {
        this(settings, patternId, forma, grosorBase, null, true);
    }

    public ClothingPatternItem(Settings settings, Identifier patternId,
                               com.modamod.render.PatronGenerador.Forma forma, int grosorBase,
                               boolean visibleEnListas) {
        this(settings, patternId, forma, grosorBase, null, visibleEnListas);
    }

    /** Molde de motivo (2026-09-28, Fase 1 de la propuesta de patrones). */
    public ClothingPatternItem(Settings settings, Identifier patternId, com.modamod.render.Motivo motivo, int escala) {
        this(settings, patternId, com.modamod.render.PatronGenerador.Forma.ALTERNADO, escala, motivo, true);
    }

    private ClothingPatternItem(Settings settings, Identifier patternId,
                                com.modamod.render.PatronGenerador.Forma forma, int grosorBase,
                                @org.jetbrains.annotations.Nullable com.modamod.render.Motivo motivo,
                                boolean visibleEnListas) {
        super(settings);
        this.patternId = patternId;
        this.forma = forma;
        this.grosorBase = grosorBase;
        this.motivo = motivo;
        this.visibleEnListas = visibleEnListas;
        POR_ID.put(patternId, this);
    }

    public static ClothingPatternItem porId(Identifier patternId) {
        return POR_ID.get(patternId);
    }

    /** Todos los patrones registrados — para cargar la Estación de Tintes creativa y el kit de la pestaña creativa. */
    public static java.util.Collection<ClothingPatternItem> todos() {
        return POR_ID.values().stream().filter(p -> p.visibleEnListas).toList();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        // Los de dibujo (corazones, estrellas, lunares, vichy) son "Motivo"; las rayas siguen siendo "Patrón".
        String id = net.minecraft.registry.Registries.ITEM.getId(this).getPath();
        boolean motivo = id.contains("corazones") || id.contains("estrellas") || id.contains("lunares") || id.contains("vichy")
                || id.contains("leopardo") || id.contains("cebra") || id.contains("tigre") || id.contains("vaca")
                || id.contains("jirafa") || id.contains("serpiente") || id.contains("dalmata")
                || id.contains("tartan") || id.contains("lenador") || id.contains("pata_de_gallo")
                || id.contains("argyle") || id.contains("camuflaje")
                || id.contains("diplomaticas") || id.contains("ventana") || id.contains("zigzag");
        PrendaLore.molde(tooltip, motivo ? "motivo" : "patron", PrendaLore.Maquina.TINTES,
                "remera", "pantalon", "medias", "calientabrazos", "pollera");
        tooltip.add(Text.translatable("modamod.pattern.tooltip.ayuda").formatted(Formatting.DARK_GRAY));
    }
}
