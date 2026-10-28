package com.modamod.body;

/**
 * Lo que trae puesto el cuerpo base: una parte de arriba, una de abajo y su
 * color — a pedido (2026-09-30, "me gustaria agregar un binder mas boxer?
 * capaz se eligen las dos partes?"). Antes era un solo valor de 5
 * (basica/slip/boxer/bralette/deportiva) y sus texturas nunca existieron, así
 * que no se veía nada; los perfiles viejos se pasan con {@link #deClaveVieja}.
 *
 * <p>Va pintada en la textura del cuerpo y no como prenda, porque el cuerpo
 * se dibuja siempre que haya cualquier prenda: si fuera una prenda más, un
 * croptop sin nada abajo dejaría el torso desnudo. Es el mínimo que siempre
 * está; lo personalizable (encaje, patrones, fotos) se hace con remeras y
 * pantalones recortados en las máquinas.
 *
 * <p>Las texturas son grises ({@code tools/generar_ropa_interior.py}) y se
 * tiñen con {@link #color}, ver {@code CuerpoBaseTextures#superponer}.
 */
public record RopaInterior(InteriorArriba arriba, InteriorAbajo abajo, int color) {

    /** Blanco roto: el color de siempre si nadie eligió otro. */
    public static final int COLOR_DEFECTO = 0xF2EEE8;

    public static final RopaInterior DEFECTO = new RopaInterior(InteriorArriba.BRALETTE, InteriorAbajo.SLIP, COLOR_DEFECTO);

    /** Los valores del enum viejo, guardados en perfiles de antes del 2026-09-30. */
    public static RopaInterior deClaveVieja(String clave) {
        return switch (clave) {
            case "slip" -> new RopaInterior(InteriorArriba.NINGUNA, InteriorAbajo.SLIP, COLOR_DEFECTO);
            case "boxer" -> new RopaInterior(InteriorArriba.NINGUNA, InteriorAbajo.BOXER, COLOR_DEFECTO);
            case "deportiva" -> new RopaInterior(InteriorArriba.DEPORTIVO, InteriorAbajo.CULOTTE, COLOR_DEFECTO);
            default -> DEFECTO;
        };
    }

    public RopaInterior conArriba(InteriorArriba a) {
        return new RopaInterior(a, abajo, color);
    }

    public RopaInterior conAbajo(InteriorAbajo b) {
        return new RopaInterior(arriba, b, color);
    }

    public RopaInterior conColor(int rgb) {
        return new RopaInterior(arriba, abajo, rgb & 0xFFFFFF);
    }

    public String clave() {
        return arriba.clave + "+" + abajo.clave + "#" + Integer.toHexString(color);
    }
}
