package com.modamod.sublimadora;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class SublimadoraMod implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("modamod");

    @Override
    public void onInitialize() {
        // Sello de build, escrito por processResources. Comparado contra
        // build/resources/main/sublimadora_build.txt contesta sin ambiguedad
        // si el cliente que esta corriendo trae los ultimos cambios.
        LOGGER.info("ModaMod cargado - build {}", sello());

        ModBlocks.register();
        ModItems.register();
        // Sus ítems van en la pestaña propia del mod (Modamod.PESTANA, 2026-09-28).
    }

    private static String sello() {
        try (InputStream in = SublimadoraMod.class.getResourceAsStream("/build.txt")) {
            return in == null ? "sin sello" : new String(in.readAllBytes()).trim();
        } catch (Exception e) {
            return "sin sello: " + e;
        }
    }
}
