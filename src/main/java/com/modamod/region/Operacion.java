package com.modamod.region;

/**
 * Que se le esta por hacer a la prenda.
 *
 * Una prenda declara sus regiones POR OPERACION y no de una sola vez, porque
 * no coinciden: una remera se tine entera (una remera, un color base) pero se
 * estampa por cara, y unas medias se tinen por pierna pero no se cortan por
 * pierna. Que cada estacion pregunte por su operacion es lo que deja al
 * selector mostrar exactamente los botones que sirven.
 */
public enum Operacion {

    /** Color base. Sin enie a proposito: es un identificador Java. */
    TENIR,
    PATRON,
    CORTE,
    ESTAMPAR
}
