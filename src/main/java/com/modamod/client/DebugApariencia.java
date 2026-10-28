package com.modamod.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Herramienta de debug (a pedido, 2026-09-16): cambiar de skin y alternar
 * slim/ancho sin tocar la cuenta real, para ver cómo queda la ropa sobre
 * cuerpos distintos mientras se prueba Calce y compañía.
 *
 * Cliente-only y solo para EL PROPIO jugador local (ver
 * {@code AbstractClientPlayerEntityMixin}) — no sincroniza nada, no es
 * parte del juego real, solo cambia lo que VE quien lo usa.
 *
 * Reusa las skins default de Minecraft ({@code DefaultSkinHelper}, que no
 * es público) en vez de bundlear arte propia — ya vienen en todas las
 * combinaciones slim/ancho que hacen falta para probar.
 */
public final class DebugApariencia {

    private DebugApariencia() {}

    private record SkinDePrueba(String nombre, Identifier textura, SkinTextures.Model modelo) {}

    private static final SkinDePrueba[] SKINS = {
            new SkinDePrueba("steve", Identifier.ofVanilla("textures/entity/player/wide/steve.png"), SkinTextures.Model.WIDE),
            new SkinDePrueba("alex", Identifier.ofVanilla("textures/entity/player/slim/alex.png"), SkinTextures.Model.SLIM),
            new SkinDePrueba("zuri", Identifier.ofVanilla("textures/entity/player/wide/zuri.png"), SkinTextures.Model.WIDE),
            new SkinDePrueba("noor", Identifier.ofVanilla("textures/entity/player/slim/noor.png"), SkinTextures.Model.SLIM),
            new SkinDePrueba("kai", Identifier.ofVanilla("textures/entity/player/wide/kai.png"), SkinTextures.Model.WIDE),
    };

    /** -1 = skin real del jugador. */
    private static int indiceSkin = -1;

    /** Fuerza el modelo de brazo sin importar la skin activa; null = el que traiga la skin. */
    @Nullable
    private static SkinTextures.Model modeloForzado = null;

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(raiz()));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> raiz() {
        return ClientCommandManager.literal("modamoddebug")
                .then(ClientCommandManager.literal("skin").executes(ctx -> {
                    // OJO: (indiceSkin + 1) % (SKINS.length + 1) - 1 NO
                    // cicla -- para indiceSkin=-1 da (0)%n-1=-1 de vuelta,
                    // se queda pegado ahí para siempre (bug real, probado
                    // jugando: el comando corría pero la skin nunca
                    // cambiaba). Esto sí avanza.
                    indiceSkin = indiceSkin + 1 >= SKINS.length ? -1 : indiceSkin + 1;
                    feedback(ctx.getSource());
                    return 1;
                }))
                .then(ClientCommandManager.literal("slim").executes(ctx -> {
                    modeloForzado = SkinTextures.Model.SLIM;
                    feedback(ctx.getSource());
                    return 1;
                }))
                .then(ClientCommandManager.literal("ancho").executes(ctx -> {
                    modeloForzado = SkinTextures.Model.WIDE;
                    feedback(ctx.getSource());
                    return 1;
                }))
                .then(ClientCommandManager.literal("automodelo").executes(ctx -> {
                    modeloForzado = null;
                    feedback(ctx.getSource());
                    return 1;
                }))
                .then(ClientCommandManager.literal("reset").executes(ctx -> {
                    indiceSkin = -1;
                    modeloForzado = null;
                    feedback(ctx.getSource());
                    return 1;
                }))
                .then(relieve())
                .then(cuello())
                .then(delantal());
    }

    /**
     * Cómo se ata el cuellito de la polera (2026-10-04): {@code torso}, {@code cabeza} o {@code partido}; sin argumento
     * pasa al siguiente. Solo para quien lo usa, sin persistencia.
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> cuello() {
        LiteralArgumentBuilder<FabricClientCommandSource> raiz = ClientCommandManager.literal("cuello")
                .executes(ctx -> {
                    var modos = com.modamod.render.CuelloYCapucha.ModoCuello.values();
                    com.modamod.render.CuelloYCapucha.modoCuello =
                            modos[(com.modamod.render.CuelloYCapucha.modoCuello.ordinal() + 1) % modos.length];
                    ctx.getSource().sendFeedback(Text.literal("[modamod debug] cuello de polera: "
                            + com.modamod.render.CuelloYCapucha.modoCuello.name().toLowerCase(java.util.Locale.ROOT)));
                    return 1;
                });
        for (com.modamod.render.CuelloYCapucha.ModoCuello m : com.modamod.render.CuelloYCapucha.ModoCuello.values()) {
            String nombre = m.name().toLowerCase(java.util.Locale.ROOT);
            raiz.then(ClientCommandManager.literal(nombre).executes(ctx -> {
                com.modamod.render.CuelloYCapucha.modoCuello = m;
                ctx.getSource().sendFeedback(Text.literal("[modamod debug] cuello de polera: " + nombre));
                return 1;
            }));
        }
        return raiz;
    }

    /** Delantal en una sola pieza de malla o con cajas sueltas (2026-10-12): {@code pieza}, {@code partes}; sin argumento alterna. */
    private static LiteralArgumentBuilder<FabricClientCommandSource> delantal() {
        return ClientCommandManager.literal("delantal")
                .executes(ctx -> {
                    com.modamod.render.DelantalPieza.activa = !com.modamod.render.DelantalPieza.activa;
                    return avisoDelantal(ctx.getSource());
                })
                .then(ClientCommandManager.literal("pieza").executes(ctx -> {
                    com.modamod.render.DelantalPieza.activa = true;
                    return avisoDelantal(ctx.getSource());
                }))
                .then(ClientCommandManager.literal("partes").executes(ctx -> {
                    com.modamod.render.DelantalPieza.activa = false;
                    return avisoDelantal(ctx.getSource());
                }));
    }

    private static int avisoDelantal(FabricClientCommandSource fuente) {
        fuente.sendFeedback(Text.literal("[modamod debug] delantal: "
                + (com.modamod.render.DelantalPieza.activa ? "pieza única" : "partes sueltas")));
        return 1;
    }

    /**
     * Estilo del relieve (2026-10-01, "podemos hacer una muestra de prueba de
     * los dos?"): {@code suave} (superficie continua), {@code escalonado}
     * (bloquecitos tipo 3D Skin Layers) o {@code apagado} (las cajas de
     * siempre). Solo para quien lo usa, sin persistencia.
     */
    private static LiteralArgumentBuilder<FabricClientCommandSource> relieve() {
        LiteralArgumentBuilder<FabricClientCommandSource> raiz = ClientCommandManager.literal("relieve");
        for (com.modamod.render.relieve.RelieveRender.Estilo e : com.modamod.render.relieve.RelieveRender.Estilo.values()) {
            String nombre = e.name().toLowerCase(java.util.Locale.ROOT);
            raiz.then(ClientCommandManager.literal(nombre).executes(ctx -> {
                com.modamod.render.relieve.RelieveRender.forzado = e;
                com.modamod.render.relieve.RelieveRender.estilo = e;
                ctx.getSource().sendFeedback(Text.literal("[modamod debug] relieve " + nombre));
                return 1;
            }));
        }
        // "perfil" suelta el forzado: vuelve a mandar el volumen que eligió cada jugador.
        raiz.then(ClientCommandManager.literal("perfil").executes(ctx -> {
            com.modamod.render.relieve.RelieveRender.forzado = null;
            ctx.getSource().sendFeedback(Text.literal("[modamod debug] relieve según el perfil"));
            return 1;
        }));
        return raiz;
    }

    private static void feedback(FabricClientCommandSource fuente) {
        String skin = indiceSkin < 0 ? "real" : SKINS[indiceSkin].nombre();
        String modelo = modeloForzado != null ? modeloForzado.getName()
                : (indiceSkin < 0 ? "el de tu skin" : SKINS[indiceSkin].modelo().getName());
        fuente.sendFeedback(Text.literal("[modamod debug] skin=" + skin + " modelo=" + modelo));
    }

    /** Aplica el override activo (si hay) sobre la skin real del jugador local. */
    public static SkinTextures aplicar(SkinTextures original) {
        if (indiceSkin < 0 && modeloForzado == null) return original;
        Identifier textura = original.texture();
        SkinTextures.Model modelo = original.model();
        if (indiceSkin >= 0) {
            textura = SKINS[indiceSkin].textura();
            modelo = SKINS[indiceSkin].modelo();
        }
        if (modeloForzado != null) modelo = modeloForzado;
        return new SkinTextures(textura, original.textureUrl(), original.capeTexture(),
                original.elytraTexture(), modelo, original.secure());
    }
}
