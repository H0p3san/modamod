package com.modamod.modelado;

import com.modamod.modelado.ModeladoBlockEntity.Categoria;
import com.modamod.modelado.ModeladoBlockEntity.Rol;

import java.util.ArrayList;
import java.util.List;

/**
 * Zonas de la Modeladora (2026-10-12, "el visor sea más grande y cambie de enfoque en vez de tener un esquema de pines
 * con casilleros por todos lados"): cada prenda agrupa sus casilleros (pines) en 2 o 3 zonas; la pantalla muestra solo
 * los de la zona activa, apilados en la misma columna, y el visor 3D se acerca al {@link Enfoque} de esa zona. Los
 * casilleros siguen siendo los pines de {@link ModeladoBlockEntity#ROLES}: acá solo se decide cómo se agrupan.
 */
public final class ZonasModelado {

    private ZonasModelado() {}

    /** Punto del cuerpo (altura en bloques desde los pies) y tamaño al que se acerca el visor; se comparten entre prendas. */
    public enum Enfoque {
        CABEZA(1.62f, 215f), TORSO(1.15f, 150f), BRAZOS(1.1f, 135f), CINTURA(0.95f, 175f),
        PIERNAS(0.5f, 140f), PIES(0.15f, 230f), ENTERO(0.9f, 112f);

        public final float focoY, tamano;

        Enfoque(float focoY, float tamano) {
            this.focoY = focoY;
            this.tamano = tamano;
        }
    }

    /** Una zona: clave de traducción (sin prefijo), enfoque del visor y los roles de sus casilleros, de arriba abajo. */
    public record Zona(String clave, Enfoque enfoque, Rol[] roles) {}

    /** Filas más altas de la columna de casilleros (la Remera tiene 4 en Brazos: manga y puño de cada lado). */
    public static final int FILAS_MAX = 4;

    private static Zona z(String clave, Enfoque e, Rol... roles) { return new Zona(clave, e, roles); }

    private static final Zona[][] ZONAS = completar(new Zona[][]{
            // Remera / Top
            {z("cabeza_cuello", Enfoque.CABEZA, Rol.CUELLO, Rol.SOLAPA_CHAQUETA, Rol.CAPUCHA_CHAQUETA),
                    z("torso", Enfoque.TORSO, Rol.TORSO, Rol.FRENTE_CHAQUETA, Rol.RUEDO_TORSO),
                    z("brazos", Enfoque.BRAZOS, Rol.MANGA_IZQ, Rol.MANGA_DER, Rol.RUEDO_PUNO_IZQ, Rol.RUEDO_PUNO_DER),
                    z("entero", Enfoque.ENTERO, Rol.CALCE, Rol.MAT1, Rol.FALDON_TOP)},
            // Pantalón
            {z("cintura", Enfoque.CINTURA, Rol.TIRO),
                    z("piernas", Enfoque.PIERNAS, Rol.BOTA_IZQ, Rol.BOTA_DER, Rol.RUEDO_BOTA_IZQ, Rol.RUEDO_BOTA_DER),
                    z("entero", Enfoque.ENTERO, Rol.CALCE, Rol.MAT1)},
            // Medias
            {z("arriba", Enfoque.PIERNAS, Rol.SUP_IZQ, Rol.SUP_DER, Rol.RUEDO_SUP_IZQ, Rol.RUEDO_SUP_DER),
                    z("abajo", Enfoque.PIES, Rol.INF_IZQ, Rol.INF_DER, Rol.RUEDO_INF_IZQ, Rol.RUEDO_INF_DER),
                    z("entero", Enfoque.ENTERO, Rol.CALCE, Rol.PERS_IZQ1, Rol.PERS_DER1)},
            // Calientabrazos
            {z("arriba", Enfoque.BRAZOS, Rol.SUP_IZQ, Rol.SUP_DER, Rol.RUEDO_SUP_IZQ, Rol.RUEDO_SUP_DER),
                    z("abajo", Enfoque.BRAZOS, Rol.INF_IZQ, Rol.INF_DER, Rol.RUEDO_INF_IZQ, Rol.RUEDO_INF_DER),
                    z("entero", Enfoque.ENTERO, Rol.CALCE, Rol.PERS_IZQ1, Rol.PERS_DER1)},
            // Pollera
            {z("cintura", Enfoque.CINTURA, Rol.CINTURA_POLLERA, Rol.FORMA_POLLERA),
                    z("largo_bordes", Enfoque.PIERNAS, Rol.LARGO_POLLERA, Rol.RUEDO_POLLERA, Rol.VOLADO_INFERIOR),
                    z("entero", Enfoque.ENTERO, Rol.CALCE, Rol.MAT1, Rol.VOLADO_TOTAL)},
            // Capa
            {z("cabeza_hombros", Enfoque.CABEZA, Rol.CUELLO_CAPA, Rol.CAPUCHA_CAPA, Rol.EMBOZO_CAPA),
                    z("caida", Enfoque.ENTERO, Rol.LARGO_CAPA, Rol.RUEDO_CAPA, Rol.BORDE_EMBOZO_CAPA),
                    z("entero", Enfoque.ENTERO, Rol.MAT1)},
            // Sombrero
            {z("sombrero", Enfoque.CABEZA, Rol.ALA_SOMBRERO, Rol.PUNTA_SOMBRERO, Rol.PLANTA_SOMBRERO),
                    z("estilo", Enfoque.CABEZA, Rol.ESTILO_SOMBRERO, Rol.GROSOR_SOMBRERO, Rol.BANDA_SOMBRERO)},
            // Banda
            {z("banda", Enfoque.CINTURA, Rol.ZONA_BANDA, Rol.ANCHO_BANDA, Rol.HERRAJE_BANDA)},
            // Calzado
            {z("cana", Enfoque.PIES, Rol.CANA_BORCEGO_IZQ, Rol.CANA_BORCEGO_DER),
                    z("suela", Enfoque.PIES, Rol.SUELA_BORCEGO_IZQ, Rol.SUELA_BORCEGO_DER),
                    z("botamanga", Enfoque.PIES, Rol.BOTAMANGA_BORCEGO_IZQ, Rol.BOTAMANGA_BORCEGO_DER),
                    z("punta", Enfoque.PIES, Rol.PUNTA_BORCEGO_IZQ, Rol.PUNTA_BORCEGO_DER),
                    z("cierre", Enfoque.PIES, Rol.CIERRE_BORCEGO_IZQ, Rol.CIERRE_BORCEGO_DER),
                    z("detalle", Enfoque.PIES, Rol.DETALLE_BORCEGO_IZQ, Rol.DETALLE_BORCEGO_DER, Rol.ESTILO_CALZADO)},
            // Delantal
            {z("pecho", Enfoque.TORSO, Rol.FORMA_DELANTAL, Rol.TIRANTES_DELANTAL, Rol.COBERTURA_DELANTAL),
                    z("largo", Enfoque.CINTURA, Rol.LARGO_DELANTAL, Rol.INICIO_DELANTAL, Rol.ESQUINAS_DELANTAL),
                    z("bordes", Enfoque.PIERNAS, Rol.VOLADO_DELANTAL, Rol.VOLADO_LATERAL_DELANTAL, Rol.VOLADO_SUPERIOR_DELANTAL),
                    z("entero", Enfoque.ENTERO, Rol.CALCE, Rol.MAT1, Rol.RUEDO_DELANTAL)},
    });

    /**
     * Si algún pin con rol de {@link ModeladoBlockEntity#ROLES} no quedó en ninguna zona, se agrega a la última de su
     * prenda (nunca queda un casillero inalcanzable); si la última ya está llena, se abre una zona "otros".
     */
    private static Zona[][] completar(Zona[][] zonas) {
        Zona[][] r = new Zona[zonas.length][];
        for (int c = 0; c < zonas.length; c++) {
            List<Zona> lista = new ArrayList<>(List.of(zonas[c]));
            List<Rol> faltan = new ArrayList<>();
            for (Rol rol : ModeladoBlockEntity.ROLES[c]) {
                if (rol == Rol.NINGUNO || faltan.contains(rol)) continue;
                boolean esta = false;
                for (Zona zona : lista) for (Rol x : zona.roles()) if (x == rol) esta = true;
                if (!esta) faltan.add(rol);
            }
            if (!faltan.isEmpty()) lista.add(new Zona("otros", Enfoque.ENTERO, faltan.toArray(new Rol[0])));
            r[c] = lista.toArray(new Zona[0]);
        }
        return r;
    }

    public static Zona[] de(Categoria cat) { return ZONAS[cat.ordinal()]; }

    public static int cantidad(Categoria cat) { return ZONAS[cat.ordinal()].length; }

    /** Índice del pin ({@code ROLES[cat][i]}) que ocupa el casillero {@code fila} de la zona, o -1. */
    public static int pinDe(Categoria cat, int zona, int fila) {
        Zona[] zs = ZONAS[cat.ordinal()];
        if (zona < 0 || zona >= zs.length || fila < 0 || fila >= zs[zona].roles().length) return -1;
        Rol rol = zs[zona].roles()[fila];
        Rol[] roles = ModeladoBlockEntity.ROLES[cat.ordinal()];
        for (int i = 0; i < roles.length; i++) if (roles[i] == rol) return i;
        return -1;
    }

    /** Zona a la que pertenece el pin {@code i}, o -1 si no es de ninguna (pin NINGUNO). */
    public static int zonaDe(Categoria cat, int i) {
        Rol rol = ModeladoBlockEntity.ROLES[cat.ordinal()][i];
        if (rol == Rol.NINGUNO) return -1;
        Zona[] zs = ZONAS[cat.ordinal()];
        for (int zi = 0; zi < zs.length; zi++) for (Rol x : zs[zi].roles()) if (x == rol) return zi;
        return -1;
    }

    /** Fila (0..) del pin {@code i} dentro de su zona, o -1. */
    public static int filaDe(Categoria cat, int i) {
        int zi = zonaDe(cat, i);
        if (zi < 0) return -1;
        Rol rol = ModeladoBlockEntity.ROLES[cat.ordinal()][i];
        Rol[] roles = ZONAS[cat.ordinal()][zi].roles();
        for (int f = 0; f < roles.length; f++) if (roles[f] == rol) return f;
        return -1;
    }
}
