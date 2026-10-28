package com.modamod.render;

import com.modamod.garment.Garments;
import com.modamod.garment.Parte;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * La skin del jugador con la segunda capa borrada donde manda una prenda.
 *
 * Es lo unico que le queda por hacer a la skin. Antes tambien repintaba piel
 * en las zonas expuestas; eso lo hace ahora el cuerpo base, dibujado como
 * geometria propia abajo de la ropa ({@link CuerpoBaseTextures}).
 *
 * Se hace UNA vez sobre la skin y no una vez por prenda. Y si el jugador no
 * lleva ninguna prenda del mod se devuelve la skin original tal cual: costo
 * cero para cualquiera que no use ropa del mod.
 *
 * El enganche es AbstractClientPlayerEntity.getSkinTextures() y no
 * PlayerEntityRenderer.getTexture(), porque 3D Skin Layers lee de ahi.
 */
public final class ComposedSkin {

    private static final Map<String, SkinTextures> CACHE = new HashMap<>();

    private ComposedSkin() {}

    /** La skin original, o una compuesta si hay prendas que la afecten. */
    public static SkinTextures forPlayer(AbstractClientPlayerEntity player, SkinTextures original) {
        if (original == null || original.texture() == null) return original;

        // 2026-09-20: se probó NO borrar la segunda capa con 3D Skin
        // Layers instalado (para que su volumen 3D siguiera visible DEBAJO
        // de la tela) — probado en juego y descartado: "quiero que sea
        // como antes q cuando ponias la prenda no renderizaba ahi". Vuelve
        // al comportamiento de siempre: borra la segunda capa donde manda
        // una prenda, apagando el volumen 3D de 3DSL ahí (es justo la
        // razón por la que este mixin engancha getSkinTextures() y no el
        // renderer — ver el javadoc de la clase). La malla 3D propia vía
        // ModelPartInjector (ver memoria del proyecto) sigue siendo el
        // camino pendiente si más adelante se quiere volumen real ahí.

        List<Parte> partes = partesDePrendas(player);
        // Con sombrero la cabeza pierde la segunda capa menos la cara (hocico, nariz): "los sombreros que maten el 3dsl de la parte que tapan".
        ItemStack gorro = partes.contains(Parte.CABEZA) ? null : sombreroPuesto(player);
        int filas = gorro == null ? -1 : filasTapadas(gorro);
        boolean sombrero = gorro != null;
        // Máscaras y barbijo (2026-10-25, "que mascara antifaz y barbijo desactiven 3dsl de la zona que cubren").
        List<String> caras = partes.contains(Parte.CABEZA) ? List.of() : carasPuestas(player);
        if (partes.isEmpty() && !sombrero && caras.isEmpty()) return original;

        boolean slim = original.model() == SkinTextures.Model.SLIM;
        String key = original.texture() + "|" + SkinRegions.clave(partes, slim) + (sombrero ? "|sombrero" + filas : "")
                + (caras.isEmpty() ? "" : "|cara" + caras);
        SkinTextures cached = CACHE.get(key);
        if (cached != null) return cached;

        NativeImage base = SkinTextureAccess.tryGetImage(original);
        if (base == null) return original;

        NativeImage composed;
        try {
            composed = new NativeImage(base.getWidth(), base.getHeight(), false);
            for (int y = 0; y < base.getHeight(); y++) {
                for (int x = 0; x < base.getWidth(); x++) {
                    composed.setColor(x, y, base.getColor(x, y));
                }
            }
        } catch (Exception e) {
            return original;
        }

        for (CajaSkin.Rect r : SkinRegions.aBorrar(partes, slim)) borrar(composed, r);
        if (sombrero) {
            CajaSkin c = LayoutSkin.overlay(Parte.CABEZA, slim);
            for (CajaSkin.Rect r : List.of(c.arriba(), c.abajo(), c.derecha(), c.izquierda(), c.atras())) borrar(composed, r);
            CajaSkin.Rect f = c.frente();   // de la cara solo lo que tapa el sombrero: abajo quedan hocico y nariz
            borrar(composed, new CajaSkin.Rect(f.x0(), f.y0(), f.x1(), f.y0() + filas));
        }
        if (!caras.isEmpty()) {
            CajaSkin c = LayoutSkin.overlay(Parte.CABEZA, slim);
            for (String modelo : caras) {
                int[] t = TAPA.get(modelo);
                if (t == null) continue;
                // Frente y costados en las filas que cubre; atrás solo la cinta, si la tiene.
                for (CajaSkin.Rect r : List.of(c.frente(), c.derecha(), c.izquierda())) {
                    borrar(composed, new CajaSkin.Rect(r.x0(), r.y0() + t[0], r.x1(), r.y0() + t[1]));
                }
                if (t[2] < t[3]) borrar(composed, new CajaSkin.Rect(c.atras().x0(), c.atras().y0() + t[2], c.atras().x1(), c.atras().y0() + t[3]));
            }
        }

        Identifier id = Identifier.of("modamod", "dynamic/skin_" + Integer.toHexString(key.hashCode()));
        MinecraftClient.getInstance().getTextureManager()
                .registerTexture(id, ClothingSkinTexture.create(composed));

        SkinTextures result = new SkinTextures(id, original.textureUrl(),
                original.capeTexture(), original.elytraTexture(), original.model(), original.secure());
        CACHE.put(key, result);
        return result;
    }

    private static void borrar(NativeImage img, CajaSkin.Rect r) {
        for (int y = r.y0(); y < Math.min(r.y1(), img.getHeight()); y++) {
            for (int x = r.x0(); x < Math.min(r.x1(), img.getWidth()); x++) {
                img.setColor(x, y, 0);
            }
        }
    }

    /**
     * Las partes del cuerpo que gobierna alguna prenda del mod.
     *
     * 2026-09-16, bug real jugando: "la preview de la Mesa y la de
     * inventario muestran la skin original a través de las prendas".
     * Causa: {@link GarmentFeatureRenderer#previewOverride} (la lista que
     * usa el visor 3D de la Mesa para dibujar una prenda en construcción,
     * sin que el jugador se la tenga puesta) es una lista APARTE de lo que
     * de verdad hay equipado en Trinkets — esta función solo miraba
     * Trinkets, así que en el visor de la Mesa nunca borraba nada de la
     * skin real: {@code GarmentFeatureRenderer} dibujaba CUERPO+TELA
     * encima igual, pero la skin de ABAJO seguía intacta, sin agujero
     * donde debería, y con la dilatación del cuerpo ahora en 0 (mismo
     * tamaño que la skin cruda sin tocar, ver {@code CuerpoGeometria
     * .Superficie.CUERPO}) terminaba compitiendo cabeza a cabeza en
     * profundidad — a veces ganaba la original. Mirar primero el preview
     * override (si hay uno activo) resuelve el visor de la Mesa de raíz.
     */
    private static ItemStack sombreroPuesto(AbstractClientPlayerEntity player) {
        ItemStack so = GarmentFeatureRenderer.sombreroOverride;
        if (so != null) return so.getItem() instanceof com.modamod.item.SombreroBrujaItem ? so : null;
        if (GarmentFeatureRenderer.previewOverride != null) return null;
        ItemStack[] hay = {null};
        TrinketsApi.getTrinketComponent(player).ifPresent(c -> {
            for (var par : c.getAllEquipped()) if (par.getRight().getItem() instanceof com.modamod.item.SombreroBrujaItem) hay[0] = par.getRight();
        });
        return hay[0];
    }

    /**
     * Las filas de la cabeza (0 = arriba, 8 = el mentón) que tapa cada modelo de {@code docs/modelo_cara/cara.bbmodel}:
     * desde/hasta en el frente y los costados, desde/hasta atrás (la cinta). Sacadas de sus cajas (fila = 32 − y_blockbench).
     */
    private static final Map<String, int[]> TAPA = Map.of(
            "antifaz", new int[]{2, 6, 4, 5},
            "media_mascara", new int[]{0, 7, 3, 4},
            "mascara_completa", new int[]{0, 8, 3, 4},
            "barbijo", new int[]{4, 8, 0, 0});

    /** Los modelos de máscara y barbijo puestos (o el de la vista previa de Tintes o de la Sublimadora). */
    private static List<String> carasPuestas(AbstractClientPlayerEntity player) {
        List<String> out = new ArrayList<>();
        ItemStack ov = CaraRenderer.override;
        if (ov != null && ov.getItem() instanceof com.modamod.item.CaraItem c) out.add(c.modelo);
        TrinketsApi.getTrinketComponent(player).ifPresent(comp -> {
            for (var par : comp.getAllEquipped()) {
                if (!(par.getRight().getItem() instanceof com.modamod.item.CaraItem c)) continue;
                if (ov != null && ((com.modamod.item.CaraItem) ov.getItem()).barbijo == c.barbijo) continue;
                out.add(c.modelo);
            }
        });
        java.util.Collections.sort(out);
        return out;
    }

    /** Cuántas filas de la cara (de arriba) tapa el sombrero: hasta el borde más bajo de sus cajas en el marco de la cabeza (y −8 a 0). */
    private static int filasTapadas(ItemStack sombrero) {
        float bajo = -8f;
        for (var c : SombreroRenderer.cajas(sombrero)) bajo = Math.max(bajo, c.max()[1]);
        return Math.max(0, Math.min(8, (int) Math.ceil(bajo + 8f)));
    }

    private static List<Parte> partesDePrendas(AbstractClientPlayerEntity player) {
        List<ItemStack> override = GarmentFeatureRenderer.previewOverride;
        if (override != null) return Garments.partesCubiertas(override);

        List<ItemStack> prendas = new ArrayList<>();
        TrinketsApi.getTrinketComponent(player).ifPresent(c -> {
            for (var par : c.getAllEquipped()) {
                ItemStack stack = par.getRight();
                if (Garments.esPrenda(stack)) prendas.add(stack);
            }
        });
        return Garments.partesCubiertas(prendas);
    }
}
