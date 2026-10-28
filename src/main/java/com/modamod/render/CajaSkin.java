package com.modamod.render;

/**
 * El desdoblado de un cuboide en el layout de skin, calculado y no escrito a
 * mano.
 *
 * Antes las regiones estaban como rectangulos literales —{@code new Rect(40,
 * 32, 56, 48)}— y cada vez que hacia falta una nueva habia que volver a
 * contar pixeles sobre la plantilla. Son todas la misma cuenta: un cuboide de
 * ancho x alto x profundidad puesto en (u,v) se despliega SIEMPRE asi.
 *
 * <pre>
 *         +-----+-----+
 *         |arrib|abajo|          fila de tapas,  alto = prof
 *   +-----+-----+-----+-----+
 *   | der |frent| izq |atras|    fila de caras,  alto = alto
 *   +-----+-----+-----+-----+
 *   ^     ^     ^     ^
 *   u   u+prof  +ancho +prof
 * </pre>
 *
 * El orden der / frente / izq / atras es en coordenadas del MODELO, y ese
 * detalle ya mordio una vez: como un brazo esta en -X y el otro en +X, la
 * cara "externa" es la primera columna en un brazo y la tercera en el otro.
 * Por eso un cambio simetrico sale espejado en un solo brazo.
 */
public record CajaSkin(int u, int v, int ancho, int alto, int prof) {

    /** Un rectangulo en la textura. x1/y1 exclusivos. */
    public record Rect(int x0, int y0, int x1, int y1) {
        public boolean contiene(int x, int y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
        public int anchoRect() { return x1 - x0; }
        public int altoRect() { return y1 - y0; }
    }

    /** La misma caja en una textura N veces mas grande. */
    public CajaSkin escalada(int escala) {
        return new CajaSkin(u * escala, v * escala,
                ancho * escala, alto * escala, prof * escala);
    }

    /** La misma caja movida a otro origen: sirve para pasar de base a overlay. */
    public CajaSkin en(int nuevoU, int nuevoV) {
        return new CajaSkin(nuevoU, nuevoV, ancho, alto, prof);
    }

    public Rect arriba()     { return new Rect(u + prof, v, u + prof + ancho, v + prof); }
    public Rect abajo()      { return new Rect(u + prof + ancho, v, u + prof + 2 * ancho, v + prof); }
    public Rect derecha()    { return new Rect(u, v + prof, u + prof, v + prof + alto); }
    public Rect frente()     { return new Rect(u + prof, v + prof, u + prof + ancho, v + prof + alto); }
    public Rect izquierda()  { return new Rect(u + prof + ancho, v + prof, u + 2 * prof + ancho, v + prof + alto); }
    public Rect atras()      { return new Rect(u + 2 * prof + ancho, v + prof, u + 2 * prof + 2 * ancho, v + prof + alto); }

    /** Las tapas: arriba y abajo, en una sola fila. */
    public Rect tapas() {
        return new Rect(u + prof, v, u + prof + 2 * ancho, v + prof);
    }

    /** Las cuatro caras laterales, en una sola fila. */
    public Rect caras() {
        return new Rect(u, v + prof, u + 2 * prof + 2 * ancho, v + prof + alto);
    }

    /** Todo el desdoblado, tapas incluidas. */
    public Rect todo() {
        return new Rect(u, v, u + 2 * prof + 2 * ancho, v + prof + alto);
    }
}
