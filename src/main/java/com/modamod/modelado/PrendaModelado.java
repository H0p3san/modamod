package com.modamod.modelado;

import com.modamod.item.Calce;
import com.modamod.item.CalientabrazosItem;
import com.modamod.item.ModamodItems;
import com.modamod.item.MediasLargo;
import com.modamod.item.PantalonItem;
import com.modamod.item.PatronRed;
import com.modamod.sublimadora.ModItems;
import com.modamod.sublimadora.RemeraItem;
import net.minecraft.item.ItemStack;

/**
 * Aplica un {@link ComboCorte} sobre una prenda — el mismo dispatch por tipo
 * que antes vivía en {@code ClothingLoomScreenHandler}, factorizado acá para
 * que la Mesa de Modelado (y el Telar viejo, que llama a este mismo método
 * ahora) no dupliquen la lógica de cada eje.
 *
 * Cada campo del combo se ignora si no aplica al tipo de prenda en mano — el
 * mismo molde de corte físico sirve para cualquier prenda, cada una toma
 * solo lo suyo. Los ejes de EXTREMIDAD (pantalón-pierna, medias,
 * calientabrazos) además respetan {@link ComboCorte#lado} — cada entrada
 * puebla el anclaje superior O inferior de UN lado (o ambos).
 */
public final class PrendaModelado {

    /** Devuelve una copia con el combo aplicado, o la prenda original si no cambió nada. */
    public static ItemStack aplicar(ItemStack prenda, ComboCorte combo) {
        if (combo.estaVacio() || prenda.isEmpty()) {
            return prenda;
        }

        ItemStack out = prenda.copyWithCount(prenda.getCount());
        boolean cambio = false;
        var lado = combo.lado();

        // Calce: transversal a las 4, sin anclaje ni lado (a diferencia de
        // los demás ejes, no recorta filas — cambia la dilatación de la
        // geometría 3D, ver CuerpoGeometria).
        if (combo.calce().isPresent() && (out.getItem() instanceof RemeraItem
                || out.getItem() instanceof PantalonItem
                || out.getItem() == ModamodItems.SOCKS_SOLID
                || out.getItem() instanceof CalientabrazosItem
                || out.getItem() instanceof com.modamod.item.PolleraItem
                || out.getItem() instanceof com.modamod.item.DelantalItem)) {
            Calce.escribir(out, combo.calce().get());
            cambio = true;
        }

        // Red: mismo criterio transversal que Calce — perfora la textura ya
        // compuesta en vez de recortar filas (ver PatronRed, PiezasDelMod).
        if (combo.red().isPresent() && (out.getItem() instanceof RemeraItem
                || out.getItem() instanceof PantalonItem
                || out.getItem() == ModamodItems.SOCKS_SOLID
                || out.getItem() instanceof CalientabrazosItem
                || out.getItem() instanceof com.modamod.item.PolleraItem
                || out.getItem() instanceof com.modamod.item.DelantalItem)) {
            PatronRed.escribir(out, combo.red().get());
            cambio = true;
        }

        if (out.getItem() instanceof RemeraItem
                && (combo.remeraLargo().isPresent() || combo.remeraCuello().isPresent())) {
            // Parchea SOLO los ejes presentes sobre el Variante ACTUAL de la
            // prenda (no un valor congelado) — antes esto reemplazaba el
            // Variante entero, así que fijar largo después de manga pisaba
            // la manga de vuelta a un default; ahora cada fijada preserva
            // lo que las anteriores ya escribieron, igual que pantalón/medias.
            com.modamod.sublimadora.Variante actual = RemeraItem.variante(out);
            com.modamod.sublimadora.Variante nuevo = new com.modamod.sublimadora.Variante(
                    combo.remeraLargo().orElse(actual.largo()),
                    actual.manga(),
                    combo.remeraCuello().orElse(actual.cuello()));
            out.set(ModItems.VARIANTE, nuevo);
            cambio = true;
        }
        // Manga: eje aparte (2026-09-24, "vamos con mangas distintas") —
        // respeta combo.lado() en vez de ir siempre adentro del Variante
        // completo (izquierda/derecha independientes, ver RemeraItem#setManga).
        if (out.getItem() instanceof RemeraItem && combo.remeraManga().isPresent()) {
            RemeraItem.setManga(out, lado, combo.remeraManga().get());
            cambio = true;
        }
        // Materiales/Calado (2026-09-24): un pin = una capa de patrón en un
        // índice puntual. Lee las capas YA puestas en la prenda y reemplaza
        // (o agrega, si el índice cae justo después de la última) solo esa
        // — así 3 pines independientes arman hasta 3 capas apiladas, en vez
        // de que cada fijada pise a las otras (ponerPatron(..., List) exige
        // la lista completa de una, no soporta "agregar una sola").
        if (combo.capaPatron().isPresent() && (out.getItem() instanceof RemeraItem
                || out.getItem() instanceof PantalonItem
                || out.getItem() == ModamodItems.SOCKS_SOLID
                || out.getItem() instanceof CalientabrazosItem
                || out.getItem() instanceof com.modamod.item.PolleraItem
                || out.getItem() instanceof com.modamod.item.CapaItem
                || out.getItem() instanceof com.modamod.item.DelantalItem)) {
            var ci = combo.capaPatron().get();
            var patronItem = com.modamod.item.ClothingPatternItem.porId(ci.patronId());
            var forma = patronItem != null ? patronItem.forma : com.modamod.render.PatronGenerador.Forma.ALTERNADO;
            var nueva = new com.modamod.region.RegionResolver.CapaPatron(
                    ci.patronId(), 0xFFFFFF, com.modamod.item.TamanoPatron.GRANDE, 0f, 0.5f, forma, false);
            var actuales = new java.util.ArrayList<>(
                    com.modamod.region.RegionResolver.capasAplicadas(out, lado));
            int idx = Math.min(ci.indice(), actuales.size());
            if (idx < actuales.size()) actuales.set(idx, nueva);
            else actuales.add(nueva);
            if (actuales.size() > 3) actuales = new java.util.ArrayList<>(actuales.subList(0, 3));
            com.modamod.region.RegionResolver.ponerPatron(out, lado, actuales);
            cambio = true;
        }
        if (out.getItem() instanceof PantalonItem) {
            if (combo.pantalonTiro().isPresent()) {
                PantalonItem.setTiro(out, combo.pantalonTiro().get());
                cambio = true;
            }
            if (combo.pantalonLargoSuperior().isPresent()) {
                PantalonItem.setLargoSuperior(out, lado, combo.pantalonLargoSuperior().get());
                cambio = true;
            }
            if (combo.pantalonLargoInferior().isPresent()) {
                PantalonItem.setLargoInferior(out, lado, combo.pantalonLargoInferior().get());
                cambio = true;
            }
        }
        if (out.getItem() == ModamodItems.SOCKS_SOLID) {
            if (combo.mediasLargoSuperior().isPresent()) {
                MediasLargo.setSuperior(out, lado, combo.mediasLargoSuperior().get());
                cambio = true;
            }
            if (combo.mediasLargoInferior().isPresent()) {
                MediasLargo.setInferior(out, lado, combo.mediasLargoInferior().get());
                cambio = true;
            }
        }
        if (out.getItem() instanceof CalientabrazosItem) {
            if (combo.calientabrazosCoberturaSuperior().isPresent()) {
                CalientabrazosItem.setCoberturaSuperior(out, lado, combo.calientabrazosCoberturaSuperior().get());
                cambio = true;
            }
            if (combo.calientabrazosCoberturaInferior().isPresent()) {
                CalientabrazosItem.setCoberturaInferior(out, lado, combo.calientabrazosCoberturaInferior().get());
                cambio = true;
            }
        }

        // Pollera (2026-09-29): largo y forma.
        if (out.getItem() instanceof com.modamod.item.PolleraItem && combo.pollera().isPresent()) {
            var p = combo.pollera().get();
            if (p.largo().isPresent()) {
                com.modamod.item.PolleraItem.setLargo(out, p.largo().get());
                cambio = true;
            }
            if (p.forma().isPresent()) {
                com.modamod.item.PolleraItem.setForma(out, p.forma().get());
                cambio = true;
            }
        }

        // Capa (2026-09-29): largo, ruedo, capucha y cuello alto.
        if (out.getItem() instanceof com.modamod.item.CapaItem && combo.pollera().isPresent()) {
            var p = combo.pollera().get();
            if (p.capaLargo().isPresent()) {
                com.modamod.item.CapaItem.setLargo(out, p.capaLargo().get());
                cambio = true;
            }
            if (p.capaRuedo().isPresent()) {
                com.modamod.item.CapaItem.setRuedo(out, p.capaRuedo().get());
                cambio = true;
            }
            if (p.capaCapucha().isPresent()) {
                com.modamod.item.CapaItem.setCapucha(out, p.capaCapucha().get());
                cambio = true;
            }
            if (p.chaqueta().isPresent() && p.chaqueta().get().capuchaCajas().isPresent()) {
                com.modamod.item.TopCorte.setEstiloCapucha(out, p.chaqueta().get().capuchaCajas().get()
                        ? com.modamod.item.SombreroEstilo.CAJAS : com.modamod.item.SombreroEstilo.MALLA);
                cambio = true;
            }
            if (p.capaCuello().isPresent()) {
                com.modamod.item.CapaItem.setCuelloAlto(out, p.capaCuello().get());
                cambio = true;
            }
        }

        // Embozo de la capa (2026-10-11): largo por molde de rango (−1 = sin embozo) y borde.
        if (out.getItem() instanceof com.modamod.item.CapaItem && combo.pollera().isPresent()
                && combo.pollera().get().chaqueta().isPresent() && combo.pollera().get().chaqueta().get().embozo().isPresent()) {
            var em = combo.pollera().get().chaqueta().get().embozo().get();
            if (em.nivel().isPresent()) { com.modamod.item.CapaItem.setEmbozo(out, em.nivel().get()); cambio = true; }
            if (em.borde().isPresent()) { com.modamod.item.CapaItem.setBordeEmbozo(out, em.borde().get()); cambio = true; }
        }

        // Sombrero de bruja (2026-10-05): ala y punta.
        if (out.getItem() instanceof com.modamod.item.SombreroBrujaItem && combo.pollera().isPresent()) {
            var p = combo.pollera().get();
            if (p.sombreroAla().isPresent()) {
                com.modamod.item.SombreroBrujaItem.setAla(out, p.sombreroAla().get());
                cambio = true;
            }
            if (p.sombreroPunta().isPresent()) {
                com.modamod.item.SombreroBrujaItem.setPunta(out, p.sombreroPunta().get());
                cambio = true;
            }
            if (p.chaqueta().isPresent() && p.chaqueta().get().sombrero().isPresent()) {
                var so = p.chaqueta().get().sombrero().get();
                if (so.plantaCuadrada().isPresent()) {
                    com.modamod.item.SombreroBrujaItem.setPlanta(out, so.plantaCuadrada().get()
                            ? com.modamod.item.SombreroPlanta.CUADRADA : com.modamod.item.SombreroPlanta.REDONDA);
                    cambio = true;
                }
                if (so.estiloCajas().isPresent()) {
                    com.modamod.item.SombreroBrujaItem.setEstilo(out, so.estiloCajas().get()
                            ? com.modamod.item.SombreroEstilo.CAJAS : com.modamod.item.SombreroEstilo.MALLA);
                    cambio = true;
                }
                if (so.grosorGrueso().isPresent()) {
                    com.modamod.item.SombreroBrujaItem.setGrosor(out, so.grosorGrueso().get()
                            ? com.modamod.item.SombreroGrosor.GRUESA : com.modamod.item.SombreroGrosor.FINA);
                    cambio = true;
                }
                if (so.banda().isPresent()) {
                    com.modamod.item.SombreroBrujaItem.setBanda(out, so.banda().get());
                    cambio = true;
                }
            }
        }

        // Volados de la pollera (2026-10-05): del borde de abajo y de toda la pollera.
        if (out.getItem() instanceof com.modamod.item.PolleraItem && combo.pollera().isPresent()) {
            var p = combo.pollera().get();
            if (p.voladoRuedo().isPresent()) {
                com.modamod.item.PolleraItem.setVoladoRuedo(out, sinVolado(p.voladoRuedo().get()));
                cambio = true;
            }
            if (p.voladoTodo().isPresent()) {
                com.modamod.item.PolleraItem.setVoladoTodo(out, sinVolado(p.voladoTodo().get()));
                cambio = true;
            }
            if (p.cintura().isPresent()) {
                com.modamod.item.PolleraItem.setCintura(out, p.cintura().get());
                cambio = true;
            }
        }

        // Ruedo de un borde libre (2026-10-07, fusiona el borde de la pollera y el remate de la chaqueta): un pin por zona.
        if (combo.pollera().isPresent() && combo.pollera().get().ruedo().isPresent()) {
            var r = combo.pollera().get().ruedo().get();
            if (ruedoVale(out, r.zona(), r.ruedo())) {
                com.modamod.item.Ruedos.set(out, r.zona(), r.ruedo());
                cambio = true;
            }
            if (r.espejo() && ruedoVale(out, r.zona().opuesta(), r.ruedo())) {
                com.modamod.item.Ruedos.set(out, r.zona().opuesta(), r.ruedo());
                cambio = true;
            }
        }

        // Borcegos (2026-10-08): altura de la caña, suela y cómo se lleva la botamanga.
        if (out.getItem() instanceof com.modamod.item.BorcegosItem && combo.pollera().isPresent()
                && combo.pollera().get().chaqueta().isPresent() && combo.pollera().get().chaqueta().get().borcego().isPresent()) {
            var bo = combo.pollera().get().chaqueta().get().borcego().get();
            if (bo.cana().isPresent()) {
                com.modamod.item.BorcegosItem.setCana(out, bo.cana().get(), lado);
                cambio = true;
            }
            if (bo.suela().isPresent()) {
                com.modamod.item.BorcegosItem.setSuela(out, bo.suela().get(), lado);
                cambio = true;
            }
            if (bo.botamanga().isPresent()) {
                com.modamod.item.BorcegosItem.setBotamanga(out, bo.botamanga().get(), lado);
                cambio = true;
            }
            if (bo.estiloCajas().isPresent()) {
                com.modamod.item.BorcegosItem.setEstilo(out, bo.estiloCajas().get()
                        ? com.modamod.item.SombreroEstilo.CAJAS : com.modamod.item.SombreroEstilo.MALLA);
                cambio = true;
            }
            if (bo.punta().isPresent()) {
                com.modamod.item.BorcegosItem.setPunta(out, bo.punta().get(), lado);
                cambio = true;
            }
            if (bo.cierre().isPresent()) {
                com.modamod.item.BorcegosItem.setCierre(out, bo.cierre().get(), lado);
                cambio = true;
            }
            if (bo.detalle().isPresent()) {
                com.modamod.item.BorcegosItem.setDetalle(out, bo.detalle().get(), lado);
                cambio = true;
            }
        }

        // Delantal (2026-10-11): inicio y largo (rango), forma, esquinas y volado.
        if (out.getItem() instanceof com.modamod.item.DelantalItem && combo.pollera().isPresent()
                && combo.pollera().get().chaqueta().isPresent() && combo.pollera().get().chaqueta().get().delantal().isPresent()) {
            var d = combo.pollera().get().chaqueta().get().delantal().get();
            if (d.inicio().isPresent()) { com.modamod.item.DelantalItem.setInicio(out, d.inicio().get()); cambio = true; }
            if (d.largo().isPresent()) { com.modamod.item.DelantalItem.setLargo(out, d.largo().get()); cambio = true; }
            if (d.forma().isPresent()) { com.modamod.item.DelantalItem.setForma(out, d.forma().get()); cambio = true; }
            if (d.esquinas().isPresent()) { com.modamod.item.DelantalItem.setEsquinas(out, d.esquinas().get()); cambio = true; }
            if (d.volado().isPresent()) { com.modamod.item.DelantalItem.setVolado(out, sinVolado(d.volado().get())); cambio = true; }
            if (d.lateral().isPresent()) { com.modamod.item.DelantalItem.setVoladoLateral(out, sinVolado(d.lateral().get())); cambio = true; }
            if (d.superior().isPresent()) { com.modamod.item.DelantalItem.setVoladoSuperior(out, sinVolado(d.superior().get())); cambio = true; }
            if (d.tirantes().isPresent()) { com.modamod.item.DelantalItem.setTirantes(out, d.tirantes().get()); cambio = true; }
            if (d.cobertura().isPresent()) { com.modamod.item.DelantalItem.setCobertura(out, d.cobertura().get()); cambio = true; }
        }

        // Banda (2026-10-05): zona, ancho y herraje.
        if (out.getItem() instanceof com.modamod.item.BandaItem && combo.pollera().isPresent()) {
            var p = combo.pollera().get();
            if (p.bandaZona().isPresent()) {
                com.modamod.item.BandaItem.setZona(out, p.bandaZona().get());
                cambio = true;
            }
            if (p.bandaAncho().isPresent()) {
                com.modamod.item.BandaItem.setAncho(out, p.bandaAncho().get());
                cambio = true;
            }
            if (p.bandaHerraje().isPresent()) {
                com.modamod.item.BandaItem.setHerraje(out, p.bandaHerraje().get());
                cambio = true;
            }
        }

        // Chaqueta (2026-10-07): frente, capucha y solapa (el cuello, las mangas y el largo salen del bloque de remera).
        if (out.getItem() instanceof com.modamod.sublimadora.RemeraItem && combo.pollera().isPresent()
                && combo.pollera().get().chaqueta().isPresent()) {
            var c = combo.pollera().get().chaqueta().get();
            if (c.frente().isPresent()) {
                com.modamod.item.TopCorte.setFrente(out, c.frente().get());
                cambio = true;
            }
            if (c.capucha().isPresent()) {
                com.modamod.item.TopCorte.setConCapucha(out, c.capucha().get());
                cambio = true;
            }
            if (c.capuchaCajas().isPresent()) {
                com.modamod.item.TopCorte.setEstiloCapucha(out, c.capuchaCajas().get()
                        ? com.modamod.item.SombreroEstilo.CAJAS : com.modamod.item.SombreroEstilo.MALLA);
                cambio = true;
            }
            if (c.solapa().isPresent()) {
                com.modamod.item.TopCorte.setSolapa(out, c.solapa().get());
                cambio = true;
            }
            if (c.faldon().isPresent()) {
                com.modamod.item.TopCorte.setFaldon(out, c.faldon().get());
                cambio = true;
            }
        }

        return cambio ? out : prenda;
    }

    private PrendaModelado() {}

    /** Si el ruedo {@code r} tiene sentido en esa zona de esta prenda (el borde decorativo solo en la pollera). */
    private static boolean ruedoVale(ItemStack prenda, com.modamod.item.ZonaRuedo zona, com.modamod.item.Ruedo r) {
        if (r.esDeCapa()) return false;
        // El fruncido (2026-10-17) solo vale en los bordes de arriba y de abajo de medias y calientabrazos.
        if (r == com.modamod.item.Ruedo.FRUNCIDO && zona != com.modamod.item.ZonaRuedo.SUP_IZQ && zona != com.modamod.item.ZonaRuedo.SUP_DER
                && zona != com.modamod.item.ZonaRuedo.INF_IZQ && zona != com.modamod.item.ZonaRuedo.INF_DER) return false;
        return switch (zona) {
            case TORSO -> prenda.getItem() instanceof com.modamod.sublimadora.RemeraItem && r.valeEnTela();
            case PUNO_IZQ, PUNO_DER -> prenda.getItem() instanceof com.modamod.sublimadora.RemeraItem;
            case POLLERA -> prenda.getItem() instanceof com.modamod.item.PolleraItem;
            case DELANTAL -> prenda.getItem() instanceof com.modamod.item.DelantalItem;
            case BOTA_IZQ, BOTA_DER -> prenda.getItem() instanceof PantalonItem
                    && (r.ordinal() <= com.modamod.item.Ruedo.CAMPANA.ordinal() || r.volado() != null || r.borde() != null);
            case SUP_IZQ, SUP_DER, INF_IZQ, INF_DER -> (prenda.getItem() == ModamodItems.SOCKS_SOLID
                    || prenda.getItem() instanceof CalientabrazosItem)
                    && (r.ordinal() <= com.modamod.item.Ruedo.CAMPANA.ordinal() || r.volado() != null || r.borde() != null
                            || r == com.modamod.item.Ruedo.FRUNCIDO);
            default -> false;
        };
    }

    /** SIN (el molde en blanco) quita el volado: los setters reciben null. */
    @org.jetbrains.annotations.Nullable
    private static com.modamod.item.PolleraVolado sinVolado(com.modamod.item.PolleraVolado v) {
        return v == com.modamod.item.PolleraVolado.SIN ? null : v;
    }
}
