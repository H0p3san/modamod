package com.modamod.aplique;

import com.modamod.item.MuestraColorItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * El "modelo" de un aplique de objeto (2026-10-04, "un motor para generar
 * apliques de bloques ya existentes en minecraft como calabazas caladas
 * cabezas de esqueletos, etc"): un ítem cualquiera, de vanilla o de otro mod,
 * que Minecraft dibuja tal cual ({@code render.ApliqueRenderer#dibujarObjeto})
 * en vez de un modelo GeckoLib.
 *
 * @param item     el ítem puesto, tal cual (vuelve igual al quitar el aplique)
 * @param muestra  la muestra de color que lo tiñe, o vacía (sin teñir)
 * @param bloque   dibujarlo como el BLOQUE colocado (con sus propiedades, ej. vela
 *                 encendida) en vez de como ítem; solo si el ítem es de un bloque
 * @param variante cuál de los estados útiles del bloque ({@link #estados})
 * @param inclinarX grados que se inclina hacia adelante/atrás (eje X del aplique)
 * @param inclinarY grados que gira de costado (eje Y del aplique)
 * @param deMolde  vino de un molde de aplique personalizado (2026-10-04): el objeto no se gastó y, al quitar el
 *                 aplique, no se devuelve (si no, el molde fabricaría objetos)
 */
public record ObjetoAplique(ItemStack item, ItemStack muestra, boolean bloque, int variante,
                            float inclinarX, float inclinarY, boolean deMolde) {

    public static final Codec<ObjetoAplique> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(ObjetoAplique::item),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("muestra", ItemStack.EMPTY).forGetter(ObjetoAplique::muestra),
            Codec.BOOL.optionalFieldOf("bloque", false).forGetter(ObjetoAplique::bloque),
            Codec.INT.optionalFieldOf("variante", 0).forGetter(ObjetoAplique::variante),
            Codec.FLOAT.optionalFieldOf("inclinar_x", 0f).forGetter(ObjetoAplique::inclinarX),
            Codec.FLOAT.optionalFieldOf("inclinar_y", 0f).forGetter(ObjetoAplique::inclinarY),
            Codec.BOOL.optionalFieldOf("de_molde", false).forGetter(ObjetoAplique::deMolde)
    ).apply(i, ObjetoAplique::new));

    /** Un objeto recién puesto: como ítem, sin inclinar. */
    public static ObjetoAplique de(ItemStack item, ItemStack muestra) {
        return new ObjetoAplique(item.copyWithCount(1), muestra.isEmpty() ? ItemStack.EMPTY : muestra.copyWithCount(1),
                false, 0, 0f, 0f, false);
    }

    /** El objeto de un molde puesto en una prenda: con la muestra del retazo (si hay) y marcado como "de molde". */
    public ObjetoAplique paraPoner(ItemStack muestraNueva) {
        return new ObjetoAplique(item.copyWithCount(1), muestraNueva.isEmpty() ? ItemStack.EMPTY : muestraNueva.copyWithCount(1),
                bloque, variante, inclinarX, inclinarY, true);
    }

    /** El objeto tal como va guardado en un molde: sin muestra y marcado como "de molde". */
    public ObjetoAplique paraMolde() {
        return new ObjetoAplique(item.copyWithCount(1), ItemStack.EMPTY, bloque, variante, inclinarX, inclinarY, true);
    }

    // Un ItemStack no se compara por valor: sin esto el aplique nunca sería "igual" a su copia y el
    // inventario reenviaría la prenda en cada tick.
    @Override
    public boolean equals(Object o) {
        return o instanceof ObjetoAplique p && ItemStack.areEqual(item, p.item) && ItemStack.areEqual(muestra, p.muestra)
                && bloque == p.bloque && variante == p.variante && inclinarX == p.inclinarX && inclinarY == p.inclinarY
                && deMolde == p.deMolde;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(ItemStack.hashCode(item), ItemStack.hashCode(muestra), bloque, variante, inclinarX, inclinarY, deMolde);
    }

    public ObjetoAplique conBloque(boolean b) {
        return new ObjetoAplique(item, muestra, b, variante, inclinarX, inclinarY, deMolde);
    }

    public ObjetoAplique conVariante(int v) {
        return new ObjetoAplique(item, muestra, bloque, v, inclinarX, inclinarY, deMolde);
    }

    public ObjetoAplique conInclinacion(float x, float y) {
        return new ObjetoAplique(item, muestra, bloque, variante, x, y, deMolde);
    }

    // ── qué entra ──────────────────────────────────────────────────────────

    /**
     * Si el ítem puede ser un aplique: nada con contenido adentro (cofres llenos, shulkers, bolsas,
     * libros escritos, ballestas cargadas), porque viaja dentro de la prenda a todos los que la ven.
     */
    public static boolean admite(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.contains(DataComponentTypes.CONTAINER) || stack.contains(DataComponentTypes.BUNDLE_CONTENTS)
                || stack.contains(DataComponentTypes.CHARGED_PROJECTILES) || stack.contains(DataComponentTypes.BLOCK_ENTITY_DATA)
                || stack.contains(DataComponentTypes.WRITTEN_BOOK_CONTENT) || stack.contains(DataComponentTypes.WRITABLE_BOOK_CONTENT)
                || stack.contains(DataComponentTypes.CONTAINER_LOOT)) {
            // Las cabezas de jugador también llevan BLOCK_ENTITY_DATA en algunos mundos: se aceptan si no hay más.
            return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof net.minecraft.block.AbstractSkullBlock
                    && !stack.contains(DataComponentTypes.CONTAINER) && !stack.contains(DataComponentTypes.BUNDLE_CONTENTS);
        }
        return true;
    }

    // ── bloque y sus estados ───────────────────────────────────────────────

    /** El bloque del ítem, o null si no es un ítem de bloque. */
    public static Block bloqueDe(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi ? bi.getBlock() : null;
    }

    /** Propiedades que no cuentan como "variante" (la orientación la elige la rotación del aplique). */
    private static final Set<String> IGNORADAS = Set.of("facing", "rotation", "axis", "waterlogged", "powered", "persistent",
            "distance", "east", "west", "north", "south", "up", "down", "attached", "in_wall", "open", "triggered");

    /** Los estados del bloque que se ven distinto (vela encendida, 1 a 4 velas...), en un orden fijo; máximo 32. */
    public static List<BlockState> estados(Block bloque) {
        BlockState base = bloque.getDefaultState();
        List<BlockState> out = new ArrayList<>();
        for (BlockState s : bloque.getStateManager().getStates()) {
            boolean ok = true;
            for (Property<?> p : s.getProperties()) {
                if (IGNORADAS.contains(p.getName()) && !s.get(p).equals(base.get(p))) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(s);
            if (out.size() >= 32) break;
        }
        if (out.isEmpty()) out.add(base);
        return out;
    }

    /** El estado a dibujar en modo bloque (siempre hay uno si el ítem es de un bloque). */
    public BlockState estado() {
        Block b = bloqueDe(item);
        if (b == null) return net.minecraft.block.Blocks.AIR.getDefaultState();
        List<BlockState> l = estados(b);
        return l.get(Math.floorMod(variante, l.size()));
    }

    /** Cuántas variantes tiene (1 si solo hay una: el botón Variante no hace nada). */
    public int cantidadDeVariantes() {
        Block b = bloqueDe(item);
        return b == null ? 1 : estados(b).size();
    }

    /** Color RGB de la muestra, o -1 si no hay (sin teñir). */
    public int tinte() {
        int[] m = MuestraColorItem.mezcla(muestra);
        return m == null ? -1 : MuestraColorItem.rgb(m);
    }
}
