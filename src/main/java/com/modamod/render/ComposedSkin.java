package com.modamod.render;

import com.modamod.prenda.Parte;
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
        // El cinto que aprieta (2026-11-08, "solo en sus filas"): la segunda capa del torso se borra solo a su altura.
        float[] cinto = partes.contains(Parte.TORSO) ? null : GarmentFeatureRenderer.compresionDeCintos(GarmentFeatureRenderer.cintosDe(player));
        // El tiro del pantalón (2026-11-10, "el pantalon con tiro alto se abre al costado"): el pantalón no tapa el torso, así
        // que la segunda capa de la skin seguía ahí y, con 3D Skin Layers, asomaba por los costados de la cintura holgada.
        int tiro = partes.contains(Parte.TORSO) ? 0 : filasDeTiro(player);
        // El cuello alto de la capa, como el sombrero (2026-11-10, "se sale una puntita de la cabeza atras", con 3D Skin
        // Layers): el pelo de la segunda capa atravesaba el cuello. Se borra atrás y a los costados, en las filas que rodea.
        ItemStack capa = partes.contains(Parte.CABEZA) ? null : GarmentFeatureRenderer.capaDe(prendasPuestas(player));
        boolean cuelloCapa = capa != null && com.modamod.item.CapaItem.cuelloAlto(capa);
        if (partes.isEmpty() && !sombrero && caras.isEmpty() && cinto == null && tiro == 0 && !cuelloCapa) return original;

        boolean slim = original.model() == SkinTextures.Model.SLIM;
        String key = original.texture() + "|" + SkinRegions.clave(partes, slim) + (sombrero ? "|sombrero" + filas : "")
                + (caras.isEmpty() ? "" : "|cara" + caras) + (cinto == null ? "" : "|cinto" + (int) cinto[0] + "-" + (int) cinto[1]) + (tiro == 0 ? "" : "|tiro" + tiro) + (cuelloCapa ? "|cuellocapa" : "");
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

        if (cuelloCapa) {
            CajaSkin c = LayoutSkin.overlay(Parte.CABEZA, slim);
            for (CajaSkin.Rect r : List.of(c.derecha(), c.izquierda(), c.atras())) {
                borrar(composed, new CajaSkin.Rect(r.x0(), r.y1() - FILAS_CUELLO_CAPA, r.x1(), r.y1()));
            }
        }

        if (tiro > 0) {
            CajaSkin c = LayoutSkin.overlay(Parte.TORSO, slim);
            for (CajaSkin.Rect r : List.of(c.frente(), c.derecha(), c.izquierda(), c.atras())) {
                borrar(composed, new CajaSkin.Rect(r.x0(), r.y1() - tiro, r.x1(), r.y1()));
            }
        }

        if (cinto != null) {
            CajaSkin c = LayoutSkin.overlay(Parte.TORSO, slim);
            for (CajaSkin.Rect r : List.of(c.frente(), c.derecha(), c.izquierda(), c.atras())) {
                borrar(composed, new CajaSkin.Rect(r.x0(), r.y0() + (int) cinto[0], r.x1(), r.y0() + (int) cinto[1]));
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
        if (so != null) return com.modamod.prenda.Prendas.es(so, com.modamod.prenda.CategoriaDePrenda.SOMBRERO) ? so : null;
        if (GarmentFeatureRenderer.previewOverride != null) return null;
        ItemStack[] hay = {null};
        TrinketsApi.getTrinketComponent(player).ifPresent(c -> {
            for (var par : c.getAllEquipped()) if (com.modamod.prenda.Prendas.es(par.getRight(), com.modamod.prenda.CategoriaDePrenda.SOMBRERO)) hay[0] = par.getRight();
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

    /** Las filas de abajo de la cabeza que rodea el cuello alto de la capa (3,5 px sobre los hombros). */
    private static final int FILAS_CUELLO_CAPA = 4;

    /** Lo puesto, o la prenda en construcción del visor de la Mesa (que no está puesta, ver partesDePrendas). */
    private static List<ItemStack> prendasPuestas(AbstractClientPlayerEntity player) {
        List<ItemStack> override = GarmentFeatureRenderer.previewOverride;
        if (override != null) return override;
        List<ItemStack> puestas = new ArrayList<>();
        TrinketsApi.getTrinketComponent(player).ifPresent(c -> {
            for (var par : c.getAllEquipped()) puestas.add(par.getRight());
        });
        return puestas;
    }

    /** Las filas del torso que tapa el pantalón más alto puesto (su tiro), 0 sin pantalón. */
    private static int filasDeTiro(AbstractClientPlayerEntity player) {
        int filas = 0;
        for (ItemStack stack : prendasPuestas(player)) {
            if (com.modamod.prenda.Prendas.es(stack, com.modamod.prenda.CategoriaDePrenda.PANTALON))
                filas = Math.max(filas, Math.min(12, com.modamod.item.PantalonItem.tiro(stack).filas));
        }
        return filas;
    }

    private static List<Parte> partesDePrendas(AbstractClientPlayerEntity player) {
        List<ItemStack> override = GarmentFeatureRenderer.previewOverride;
        if (override != null) return com.modamod.prenda.Prendas.partesCubiertas(override);

        List<ItemStack> prendas = new ArrayList<>();
        TrinketsApi.getTrinketComponent(player).ifPresent(c -> {
            for (var par : c.getAllEquipped()) {
                ItemStack stack = par.getRight();
                if (com.modamod.prenda.Prendas.esRopaDeCuerpo(stack)) prendas.add(stack);
            }
        });
        return com.modamod.prenda.Prendas.partesCubiertas(prendas);
    }
}
