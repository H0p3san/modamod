package com.modamod.body;

import com.modamod.Modamod;
import net.minecraft.util.Identifier;
import net.minecraft.util.StringIdentifiable;

/**
 * El cuerpo que va DEBAJO de la ropa.
 *
 * Reemplaza la reconstruccion de piel. Antes el mod leia la skin del jugador,
 * le sampleaba el tono y repintaba a mano las zonas que la prenda dejaba a la
 * vista, con toda la lista de trampas que eso trajo: leer pixeles de vuelta
 * desde la GPU, el empaquetado ABGR, adivinar cual pixel es piel y cual es
 * manga. Un cuerpo base elegido a mano borra el problema entero: el UV es
 * conocido y las zonas desnudas tambien.
 *
 * Set FIJO y no uno por prenda: es arte que se dibuja una vez.
 *
 * SKIN_REAL sigue siendo el default a proposito. El set curado son
 * alternativas, no un reemplazo: la tesis del mod es que la ropa ande con
 * cualquier skin, y arrancar reemplazandole el cuerpo al jugador la
 * contradice.
 *
 * <p>2026-09-29 ("agregue al origin un zip con texturas... te de la
 * alternativa de elegir cualquiera de esas texturas base"): los cuerpos
 * curados son las 19 mascaras en gris del zip (10 humanos y 9 animales),
 * en {@code textures/entity/cuerpo/<clave>.png}, 64x64. Cada una esta
 * pintada para brazos classic o slim ({@link #slim}); si la skin del
 * jugador tiene los otros, se adapta sola al componer (ver
 * {@code CuerpoBaseTextures}). Reemplazan a PLANO/CURVY/BINDER, que nunca
 * tuvieron arte — ver {@link #deClave} para los perfiles viejos.
 */
public enum CuerpoBase implements StringIdentifiable {

    /**
     * El cuerpo del propio jugador: tono sampleado de su skin y sombreado por
     * cara, sin textura curada de por medio. Es lo que hacia el mod antes,
     * pero dibujado como sustrato en vez de repintado sobre la skin.
     */
    SKIN_REAL("skin_real", false, false),

    // Humanos.
    ESTANDAR("estandar", false, false),
    DELGADO("delgado", false, false),
    ATLETICO("atletico", false, false),
    MUSCULOSO("musculoso", false, false),
    GORDITO("gordito", false, false),
    VELLUDO("velludo", false, false),
    FEM_ESTANDAR("fem_estandar", true, false),
    FEM_ATLETICA("fem_atletica", true, false),
    FEM_CURVAS("fem_curvas", true, false),
    FEM_GORDITA("fem_gordita", true, false),

    // Animales: manchas y rayas en blanco y negro, tambien tenidas con el tono.
    CEBRA("cebra", true, true),
    DALMATA("dalmata", false, true),
    LEOPARDO("leopardo", true, true),
    LOBO("lobo", false, true),
    OSITO("osito", false, true),
    PANDA("panda", false, true),
    TIGRE("tigre", false, true),
    VACA("vaca", false, true),
    ZORRO("zorro", true, true);

    public final String clave;
    /** Para que brazos esta pintada la mascara: slim (3 px) o classic (4 px). */
    public final boolean slim;
    public final boolean animal;

    CuerpoBase(String clave, boolean slim, boolean animal) {
        this.clave = clave;
        this.slim = slim;
        this.animal = animal;
    }

    @Override
    public String asString() {
        return clave;
    }

    /**
     * Por clave, tolerando las de antes del 2026-09-29: un perfil guardado
     * con un cuerpo que ya no existe cae al mas parecido en vez de romper la
     * lectura del attachment entero.
     */
    public static CuerpoBase deClave(String clave) {
        for (CuerpoBase c : values()) if (c.clave.equals(clave)) return c;
        return switch (clave) {
            case "curvy" -> FEM_CURVAS;
            case "plano", "binder" -> ESTANDAR;
            default -> SKIN_REAL;
        };
    }

    /** Si el tono sale de la skin del jugador cuando el perfil no fija uno. */
    public boolean derivaDeLaSkin() {
        return this == SKIN_REAL;
    }

    /**
     * La mascara del cuerpo, en layout de skin a 1x (64x64), en gris: se
     * multiplica por el tono. SKIN_REAL no tiene: sale liso con el
     * sombreado por cara.
     */
    public Identifier textura() {
        return Identifier.of(Modamod.MOD_ID, "textures/entity/cuerpo/" + clave + ".png");
    }

    public boolean tieneTextura() {
        return this != SKIN_REAL;
    }

    public String traduccion() {
        return "modamod.cuerpo." + clave;
    }

    public CuerpoBase siguiente() {
        return values()[(ordinal() + 1) % values().length];
    }
}
