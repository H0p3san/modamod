package com.modamod.sublimadora;

import org.jetbrains.annotations.Nullable;

/** Lo que el mixin de {@code BannerBlockEntity} le agrega: la foto sublimada del banner (2026-10-06). */
public interface BannerConImagen {
    @Nullable Estampa modamod$imagen();
    void modamod$ponerImagen(@Nullable Estampa estampa);

    /** Las capas de la Estación de Tintes (2026-10-24, "tintes banners"); vacía = sin teñir. */
    java.util.List<com.modamod.region.RegionResolver.CapaPatron> modamod$capas();
}
