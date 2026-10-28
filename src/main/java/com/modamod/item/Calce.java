package com.modamod.item;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StringIdentifiable;

/**
 * Cuán ajustada o voluminosa se ve una prenda respecto del cuerpo — a
 * pedido (2026-09-15), el eje "fit" que ya estaba anotado sin implementar
 * en `docs/archivo/MAQUINAS.md` desde antes de esta sesión.
 *
 * A diferencia de los demás ejes de esta sesión (largo/manga/cuello/tiro,
 * que cambian qué filas tienen tela), Calce NO recorta nada — cambia la
 * DILATACIÓN de la geometría 3D (cuánto se infla la caja de tela respecto
 * del cuerpo), ver {@link com.modamod.render.CuerpoGeometria}. Es
 * transversal a las 4 prendas (remera, pantalón, medias, calientabrazos):
 * un solo componente compartido, cada ítem lo lee/escribe igual (ver
 * {@link #leer}/{@link #escribir}), sin necesidad de un getter/setter
 * propio por clase.
 *
 * Por prenda individual, no global — dos prendas apiladas con calces muy
 * distintos pueden mostrar un "anillo" en la superposición (riesgo
 * aceptado a propósito, ver el javadoc de
 * {@code CuerpoGeometria.Superficie}).
 */
public enum Calce implements StringIdentifiable {

    // Pegado reemplaza la piel: misma dilatación que el CUERPO (ver
    // CuerpoGeometria.Superficie.CUERPO), sin ningún hueco entre tela y
    // piel.
    //
    // 2026-09-20, "por ahi parpadea la camiseta de medias red": con una
    // dilatación IDÉNTICA (0.0F == 0.0F) las dos cajas quedan en el mismo
    // plano exacto — un empate de profundidad de verdad, no solo "cerca".
    // Con tela opaca no se notaba (la tela se dibuja DESPUÉS y gana el
    // empate de forma consistente), pero con la cámara moviéndose o a
    // distancia la precisión de ese empate varía cuadro a cuadro y
    // titila. Un margen de EPSILON (más grande, nunca exactamente igual
    // ni más chico — más chico repite el bug de "invisible" ya
    // documentado más abajo para Ajustado) saca el empate sin cambiar
    // nada visible: "reemplaza la piel" sigue siendo cierto a esta escala.
    //
    // 2026-09-15, probado en juego: Ajustado con dilatación por DEBAJO de
    // la del cuerpo dejaba la prenda TOTALMENTE INVISIBLE -- la caja de
    // tela quedaba adentro de la caja del cuerpo, que ya se dibuja antes y
    // gana el depth-test en cada píxel de la superficie. No era un bug de
    // cache ni de textura: una caja "por debajo" de otra caja opaca del
    // MISMO tamaño no se ve nunca.
    //
    // 2026-09-16: se resolvió de raíz, no bajando la dilatación de Ajustado
    // sino achicando el CUERPO mismo, PERO SOLO donde la tela de Ajustado
    // realmente tapa (ver GarmentFeatureRenderer#segmentosCuerpo,
    // CuerpoGeometria#cuerpoSegmentado) -- a pedido explícito: la piel
    // expuesta fuera de esa tela (ej. la panza bajo una musculosa ajustada)
    // no se toca, sigue con el cuerpo normal. Con eso Ajustado SÍ puede ir
    // por debajo de la dilatación del cuerpo sin desaparecer.
    // 2026-09-16: CUERPO pasó de 0.30 a 0.0 exacto (ver el javadoc de
    // CuerpoGeometria.Superficie.CUERPO -- el empate de profundidad
    // contra la skin de vanilla sin reconstruir que esto causaba se
    // resolvió de raíz con LivingEntityRendererMixin, que apaga la capa
    // base de vanilla en vez de necesitar un margen de dilatación acá).
    // Para que Normal/Suelto/Oversize sigan separándose de la piel LO
    // MISMO que separaban antes (no de golpe 0.32 en vez de 0.02), se
    // les resta el mismo 0.30 que bajó el cuerpo -- mismo "crecimiento"
    // relativo, ancla más abajo.
    // +0.001F: un enum constant no puede leer un static field declarado
    // más abajo en la misma clase, así que el margen queda inline acá
    // (ver el comentario de arriba para el porqué del valor).
    //
    // 2026-09-30, "quiero que resolvamos el calce para que quede
    // diferenciado ahora se ven bastante similares. la idea del pegado es
    // que reemplace la skin, el ajustado que apriete achicando el cuboide
    // donde corta la prenda, el normal un poco holgado y el suelto mas
    // holgado y si se puede con caida y el oversize super grande": los
    // valores de antes (0.02 / 0.15 / 0.30) eran décimas de píxel sobre un
    // brazo de 4 — Normal y Oversize se diferenciaban en un 7 %. Ahora:
    // Ajustado aprieta de verdad (el cuerpo de abajo se achica igual, ver
    // GarmentFeatureRenderer#segmentosCuerpo), Normal deja un cuarto de
    // píxel de aire, y Suelto/Oversize además se abren hacia el ruedo
    // ({@link #caida}) y cuelgan más abajo de donde corta la prenda
    // ({@link #colgado}) — a pedido, "además cuelga más abajo".
    PEGADO("pegado", com.modamod.render.CuerpoGeometria.Superficie.CUERPO.dilatacion + 0.001F, 0F, 0, 1.2F),
    AJUSTADO("ajustado", com.modamod.render.CuerpoGeometria.Superficie.CUERPO.dilatacion - 0.25F, 0F, 0, 1.0F),
    NORMAL("normal", 0.25F, 0F, 0, 0.8F),
    SUELTO("suelto", 0.6F, 0.5F, 1, 0.4F),
    OVERSIZE("oversize", 1.2F, 1.0F, 2, 0.25F);

    public final String clave;
    /** Cuánto se separa la tela del cuerpo (en píxeles de skin; negativo = aprieta). */
    public final float dilatacion;
    /** Cuánto MÁS se abre la tela en la fila del ruedo que arriba (en píxeles; crece de a poco hacia el ruedo). */
    public final float caida;
    /** Cuántos píxeles cuelga la tela por debajo de la fila donde corta la prenda. */
    public final int colgado;
    /**
     * Cuánto se marca el muslo/pantorrilla de las medias con volumen
     * ({@code CuerpoGeometria#telaConVolumenDePierna}): tela que aprieta =
     * carne que abulta, así que cuanto más ajustada, más se marca.
     */
    public final float factorVolumen;

    Calce(String clave, float dilatacion, float caida, int colgado, float factorVolumen) {
        this.clave = clave;
        this.dilatacion = dilatacion;
        this.caida = caida;
        this.colgado = colgado;
        this.factorVolumen = factorVolumen;
    }

    /**
     * El calce con esa dilatación exacta, o null — las piezas
     * ({@code Pieza}) guardan la dilatación y no el calce, y todas la sacan
     * de {@link #dilatacionEfectiva}, así que la comparación exacta alcanza.
     */
    @org.jetbrains.annotations.Nullable
    public static Calce de(float dilatacion) {
        for (Calce c : values()) if (c.dilatacion == dilatacion) return c;
        return null;
    }

    @Override
    public String asString() {
        return clave;
    }

    public String traduccion() {
        return "modamod.calce." + clave;
    }

    public static Calce leer(ItemStack stack) {
        Calce c = stack.get(ModamodComponents.CALCE);
        return c == null ? NORMAL : c;
    }

    public static void escribir(ItemStack stack, Calce valor) {
        if (valor == NORMAL) stack.remove(ModamodComponents.CALCE);
        else stack.set(ModamodComponents.CALCE, valor);
    }

    /**
     * La dilatación a usar de verdad al dibujar una pieza.
     *
     * <p><b>2026-09-20</b>: se probó sumar acá un margen FIJO cuando 3D
     * Skin Layers está instalado, para que la tela no quede "adentro" del
     * bulto 3D que genera 3DSL (que infla con factores MULTIPLICATIVOS,
     * ~1.05-1.18x, bastante más que cualquier {@link #dilatacion} nuestra
     * aditiva) — probado en juego: "parece una canasta la media", porque
     * el margen se suma SIEMPRE, aunque esa skin puntual no tenga nada
     * dibujado en la segunda capa (sin bulto real que evitar, la tela
     * igual se infla de más). El bulto de 3DSL varía skin por skin, así
     * que un número fijo no puede acertarle — se retiró. La solución de
     * fondo es reemplazar el bulto auto-generado por una malla 3D propia
     * vía la API de 3DSL, no seguir ajustando este margen a ojo.
     */
    public static float dilatacionEfectiva(ItemStack stack) {
        return leer(stack).dilatacion;
    }
}
