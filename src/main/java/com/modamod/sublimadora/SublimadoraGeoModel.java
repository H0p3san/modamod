package com.modamod.sublimadora;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/**
 * Ata el block entity a su geometria, su textura y sus animaciones, y
 * escala las cuatro barras del display CMYK segun la tinta restante.
 *
 * Cada barra es un hueso propio (ink_c, ink_m, ink_y, ink_k) con el pivote
 * en su base, asi que escalar Y de 0 a 1 la llena de abajo hacia arriba.
 */
public class SublimadoraGeoModel extends GeoModel<SublimadoraBlockEntity> {
    // Modelo nuevo (2026-09-19, "instalemos la sublimadora nueva... el
    // nuevo modelo hermano de las otras dos estaciones") — reemplaza el
    // placeholder viejo (sublimadora.geo.json).
    private static final Identifier GEO =
        Identifier.of(Modamod.MOD_ID, "geo/sublimator.geo.json");
    /** No privado a propósito: {@link SublimadoraRenderer} lo necesita para el atlas real detrás de los LEDs. */
    static final Identifier TEX =
        Identifier.of(Modamod.MOD_ID, "textures/block/sublimator_atlas.png");
    private static final Identifier ANIM =
        Identifier.of(Modamod.MOD_ID, "animations/sublimator.animation.json");

    private static final String[] HUESOS = { "ink_cyan", "ink_magenta", "ink_yellow", "ink_key" };

    /**
     * Los 3 "LEDs falsos" del panel, ahora conectados de verdad
     * (2026-09-21, "que brillen de verdad, tipo lucecitas geckolib") —
     * mismo mecanismo que {@code ModeladoGeoModel}, ver su javadoc.
     */
    static final int ANCHO_ATLAS = 128, ALTO_ATLAS = 128;
    static final com.modamod.render.PantallaLed.Rect[] LEDS = {
            new com.modamod.render.PantallaLed.Rect(64, 16, 1, 1),  // verde
            new com.modamod.render.PantallaLed.Rect(80, 16, 1, 1),  // rojo
            new com.modamod.render.PantallaLed.Rect(96, 16, 1, 1),  // amarillo
    };
    private static final int VERDE = 0xFF00FF00, ROJO = 0xFF0000FF, AMARILLO = 0xFF00FFFF;

    /** Verde fijo al terminar, rojo titilando mientras prensa, amarillo titilando lento con la línea encendida. */
    static int[] coloresLed(SublimadoraBlockEntity be) {
        int verde = 0, rojo = 0, amarillo = 0;
        if (be.getEstado() == SublimadoraBlockEntity.Estado.LISTO) {
            verde = VERDE;
        } else if (be.getEstado() == SublimadoraBlockEntity.Estado.PRENSANDO) {
            long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
            if (t % 20 < 10) rojo = ROJO;
        }
        long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
        if (be.linea() && t % 40 < 20) amarillo = AMARILLO;
        return new int[]{verde, rojo, amarillo};
    }

    /**
     * Factor de calibración POR BARRA (2026-09-19, "hay que calibrar
     * ninguno llega hasta arriba"): en el .geo.json cada barra arranca a
     * una altura DEMO distinta y arbitraria (no a la altura real de
     * "llena") — cyan 2.21, magenta 1.56, yellow 0.91, key 1.82, paper
     * 1.43 — mientras que el marco estático detrás de cada una (hueso
     * "base", las 5 cajas angostas pegadas a cada barra) sí mide la
     * altura real del tanque: 2.6/2.59/2.58/2.57/2.55 respectivamente. Sin
     * este factor, {@code setScaleY(1)} solo reproducía la altura DEMO, y
     * el tanque se veía corto incluso lleno. factor = marco/demo.
     */
    private static final float[] FACTOR_HUESOS = {
            2.60f / 2.21f,   // cyan
            2.59f / 1.56f,   // magenta
            2.58f / 0.91f,   // yellow
            2.57f / 1.82f }; // key
    private static final float FACTOR_PAPEL = 2.55f / 1.43f;
    /**
     * Mismo problema que {@link #FACTOR_HUESOS}, para la barra de
     * progreso — bug real (2026-09-21, "la barrita de progreso... se
     * llena hasta la mitad, deberia llegar hasta el borde"): el cubo
     * "progress" mide 1.9801 de ancho (tamaño DEMO de Blockbench) pero
     * el marco estático detrás (un cubo de "base" en la misma zona,
     * X≈0.95→5.35) mide 4.4 — sin este factor, {@code setScaleX(1)}
     * solo llegaba al ~45% del marco real.
     */
    private static final float FACTOR_PROGRESO = 4.4f / 1.9801f;

    @Override public Identifier getModelResource(SublimadoraBlockEntity be) { return GEO; }

    /** Pantallita de vista previa (2026-09-21, "haceme la pantallita") — ver {@link com.modamod.render.PantallaMaquina}. */
    @Override
    public Identifier getTextureResource(SublimadoraBlockEntity be) {
        // La VISTA PREVIA (estampa del borrador ya aplicada), no la
        // remera cruda — a pedido (2026-09-21, "que muestre el preview
        // del setting de la ultima prenda seteada").
        return com.modamod.render.PantallaMaquina.con(com.modamod.util.MaquinaCreativa.textura(TEX, com.modamod.util.MaquinaCreativa.es(be)), com.modamod.render.PantallaMaquina.PANEL_128, be.vistaPreviaPersistente(), be.getPos());
    }

    @Override public Identifier getAnimationResource(SublimadoraBlockEntity be) { return ANIM; }

    @Override
    public void setCustomAnimations(SublimadoraBlockEntity be, long instanceId, AnimationState<SublimadoraBlockEntity> state) {
        super.setCustomAnimations(be, instanceId, state);
        // LEDs del panel: rojo parpadeando mientras prensa, verde fijo cuando
        // esta lista, los dos apagados en reposo. Se prenden y apagan con
        // setHidden, igual que las barras de tinta.
        //
        // OJO (2026-09-19, modelo nuevo): "led_rojo"/"led_verde"/"palanca"
        // eran huesos del modelo VIEJO — sublimator.geo.json no los tiene
        // (tiene "paper"/"progress"/"design"/"cargo"/"fan" en su lugar,
        // sin mapear todavia). getBone(...) devuelve null para estos y el
        // if de abajo no hace nada — no rompe nada, pero estas señales
        // visuales (led/palanca) quedan sin efecto hasta que se sepa a qué
        // hueso nuevo corresponde cada una. "remera" SÍ tiene equivalente
        // ahora — ver "REMERA" más abajo (2026-09-23, agrupada a mano en
        // Blockbench por el usuario, ya estaba en "base" sin nombre).
        SublimadoraBlockEntity.Estado estado = be.getEstado();
        boolean rojo = false, verde = false;
        if (estado == SublimadoraBlockEntity.Estado.PRENSANDO) {
            // Medio segundo prendido, medio apagado.
            long t = be.getWorld() == null ? 0 : be.getWorld().getTime();
            rojo = (t % 20) < 10;
        } else if (estado == SublimadoraBlockEntity.Estado.LISTO) {
            verde = true;
        }
        GeoBone ledRojo = getAnimationProcessor().getBone("led_rojo");
        if (ledRojo != null) ledRojo.setHidden(!rojo);
        GeoBone ledVerde = getAnimationProcessor().getBone("led_verde");
        if (ledVerde != null) ledVerde.setHidden(!verde);

        // El slider de modo VIEJO (LOGO/CENTRADA/COMPLETO por posición del
        // pomo) se sacó (2026-09-19, "sacamos los controles del frente"):
        // escala/posición ahora son libres, se controlan desde la pantalla
        // (ver SublimadoraScreen), no con un hueso físico. El modelo nuevo
        // tampoco tiene un hueso "palanca" — nada que hacer acá.

        // El selector de cara, del otro lado del frente. Arriba el frente,
        // abajo la espalda: el slider de modo muestra el modo de la cara que
        // este eligiendo este.
        GeoBone selector = getAnimationProcessor().getBone("selector");
        if (selector != null) {
            selector.setPosY(be.getSeleccion() == Estampa.Cara.FRENTE ? 1.5f : -1.5f);
        }


        float parcial = state == null ? 1f : (float) state.getPartialTick();
        for (int i = 0; i < HUESOS.length; i++) {
            GeoBone barra = getAnimationProcessor().getBone(HUESOS[i]);
            if (barra == null) continue;
            float nivel = be.getNivelInterpolado(i, parcial);
            barra.setScaleY(Math.max(0.001f, nivel * FACTOR_HUESOS[i]));   // 0 = vacia, 1 = llena de VERDAD
            barra.setHidden(nivel <= 0.001f);           // tanque vacio: barra invisible
        }

        // Barra de progreso del modelo nuevo (hueso "progress", pivote en
        // su borde izquierdo — escalar X la llena de izquierda a derecha,
        // mismo truco que las barras de tinta pero en X) — a pedido
        // (2026-09-19, "progress va avanzando mientras la sublimadora
        // trabaja"). Al terminar (LISTO) se deja LLENA en vez de ocultarla:
        // reemplaza a la vieja luz verde de "lista para retirar".
        GeoBone progreso = getAnimationProcessor().getBone("progress");
        if (progreso != null) {
            float valor = switch (estado) {
                case PRENSANDO -> be.getProgreso();
                case LISTO -> 1f;
                case REPOSO -> 0f;
            };
            progreso.setScaleX(Math.max(0.001f, valor * FACTOR_PROGRESO));
            progreso.setHidden(valor <= 0.001f);
        }

        // Papel: MISMO mecanismo que las barras de tinta (pivote en la
        // base, escala Y con su propio factor de calibración) y no un
        // simple visible/oculto — a pedido (2026-09-19, "el nivel de tinta
        // y papel hay que calibrar, ninguno llega hasta arriba": el hueso
        // "paper" tiene el mismo problema de altura demo que ink_*).
        GeoBone papel = getAnimationProcessor().getBone("paper");
        if (papel != null) {
            float nivelPapel = be.getPapel() / (float) SublimadoraBlockEntity.CARGA_MAXIMA;
            papel.setScaleY(Math.max(0.001f, nivelPapel * FACTOR_PAPEL));
            papel.setHidden(nivelPapel <= 0.001f);
        }

        // Las 4 categorías YA modeladas en cubos sobre "base" (2026-09-23,
        // "REMERA" agrupada a mano en Blockbench por el usuario, las otras
        // 3 armadas con el mismo criterio — "no aparecen las otras
        // prendas") — solo una visible a la vez, según qué se estampó.
        // Visible mientras haya una prenda cargada O la recién estampada
        // no se retiró.
        // Siempre ocultos (2026-09-29): la prenda la dibuja PrendaEnMaquinaLayer con su ícono real.
        for (String huesoCat : HUESOS_CATEGORIA) {
            GeoBone hueso = getAnimationProcessor().getBone(huesoCat);
            if (hueso != null) hueso.setHidden(true);
        }
    }

    private static final String[] HUESOS_CATEGORIA = { "REMERA", "PANTALON", "MEDIAS", "CALIENTABRAZOS" };

    /** Nombre del hueso de categoría según el tipo real del ítem — mismos 4 tipos que {@code ModItems#esEstampable}. null = ninguno/vacío. */
    @org.jetbrains.annotations.Nullable
    private static String huesoDe(net.minecraft.item.ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.getItem() instanceof RemeraItem) return "REMERA"; // la chaqueta también
        if (stack.getItem() instanceof com.modamod.item.PantalonItem) return "PANTALON";
        if (stack.getItem() == com.modamod.item.ModamodItems.SOCKS_SOLID) return "MEDIAS";
        if (stack.getItem() instanceof com.modamod.item.CalientabrazosItem) return "CALIENTABRAZOS";
        return null;
    }
}
