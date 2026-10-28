package com.modamod.pruebas;

import net.fabricmc.api.ClientModInitializer;

/**
 * Entrada de las pruebas de juego; solo vive en el source set de test (nunca en el jar). La suite sale de
 * -Dmodamod.suite: "pantallas" (runSmoke) o "prenda" (runWearableSmoke). Sin la propiedad no hace nada.
 */
public final class PruebasClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        String suite = System.getProperty("modamod.suite");
        if (suite == null) return;
        Corrida corrida = switch (suite) {
            case "modeladora-linea" -> EscenarioModeladoraLinea.armar();
            case "pantallas" -> EscenarioPantallas.armar();
            case "prenda" -> EscenarioPrenda.armar();
            case "salon" -> EscenarioSalon.armar();
            case "modashow" -> EscenarioModashow.armar();
            default -> throw new IllegalArgumentException("suite desconocida: " + suite);
        };
        corrida.iniciar();
    }
}
