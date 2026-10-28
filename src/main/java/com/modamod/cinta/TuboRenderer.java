package com.modamod.cinta;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/**
 * Lo que viaja por el tubo y por su empalme (2026-10-20): el ítem flotando en el centro, avanzando por el caño (recto o en codo)
 * o entrando al centro del empalme. Los filtros del empalme se ven como ítems sobre cada cara.
 */
public final class TuboRenderer {

    /**
     * El ítem flotando en el centro del caño y girando despacio, como uno tirado (2026-10-21, "hay items que viajan por la superficie
     * del tubo en vez del centro, otros como la ropa aparecen planos y se pierden en el descenso vertical"): se lee igual en vertical,
     * en horizontal y en los codos, y la ropa muestra su ícono.
     */
    private static void dibujar(ItemStack stack, net.minecraft.world.World mundo, MatrixStack m, VertexConsumerProvider b, int luz, int overlay,
                                float x, float y, float z, float tickDelta) {
        dibujar(stack, mundo, m, b, luz, overlay, x, y, z, tickDelta, 0f, 0f);
    }

    /**
     * Con {@code k} de 0 a 1 el ítem pasa de flotar girando a la pose de la cinta (2026-10-21, "cómo podemos ocultar mejor el cambio de
     * tamaño y orientación de un ítem al pasar de un tubo a una cinta"): deja de girar hacia el rumbo {@code yawCinta}, se achica a la
     * escala de la cinta y, si es plano, se acuesta. En modo GROUND como la cinta ({@link CintaRenderer#dibujarItem}); 0,8 en GROUND
     * es el mismo tamaño que tenía en FIXED a 0,4.
     */
    private static void dibujar(ItemStack stack, net.minecraft.world.World mundo, MatrixStack m, VertexConsumerProvider b, int luz, int overlay,
                                float x, float y, float z, float tickDelta, float k, float yawCinta) {
        var render = net.minecraft.client.MinecraftClient.getInstance().getItemRenderer();
        m.push();
        m.translate(0.5 + x, 0.5 + y, 0.5 + z);
        float giro = ((mundo.getTime() % 360) + tickDelta) * 4f;
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(giro + net.minecraft.util.math.MathHelper.wrapDegrees(yawCinta - giro) * k));
        float esc = 0.8f + (0.5f - 0.8f) * k;
        m.scale(esc, esc, esc);
        if (k > 0f && !render.getModel(stack, mundo, null, 0).hasDepth()) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f * k));
        render.renderItem(stack, ModelTransformationMode.GROUND, luz, overlay, m, b, mundo, 0);
        m.pop();
    }

    private static float suave(float u) { u = Math.max(0f, Math.min(1f, u)); return u * u * (3f - 2f * u); }

    /** Altura del ítem sobre la cinta, medida desde el centro de un bloque a su mismo nivel (la de {@link CintaRenderer#dibujarItem}). */
    private static final float ALTO_EN_CINTA = CintaRenderer.ALTO + 0.12f - 0.5f;

    /** El rumbo de la cinta en el marco del tubo: el mismo giro que aplica {@link CintaRenderer}. */
    private static float yawDe(net.minecraft.block.BlockState cinta) {
        return -((cinta.get(CintaBlock.FACING).asRotation() + 180f) % 360f);
    }

    /**
     * Si la salida {@code s} da a una cinta: hasta dónde (en bloques desde el centro del tubo) lleva el tubo al ítem, el punto donde
     * la cinta lo empieza a dibujar: el borde si entra por atrás (o por el costado de una curva), el medio de la banda si cae de
     * arriba o entra por el costado de una recta ({@link CintaBlockEntity}). {@code null} si no es una cinta.
     */
    @org.jetbrains.annotations.Nullable
    private static Float alcanceEnCinta(net.minecraft.world.World mundo, net.minecraft.util.math.BlockPos pos, Direction s) {
        net.minecraft.block.BlockState v = mundo.getBlockState(pos.offset(s));
        if (!(v.getBlock() instanceof CintaBlock)) return null;
        if (s == Direction.DOWN) return 1f - ALTO_EN_CINTA;
        if (s == Direction.UP) return null;
        boolean medio = v.get(CintaBlock.FORMA) == CintaBlock.Forma.RECTA && v.get(CintaBlock.FACING).getAxis() != s.getAxis();
        return medio ? 1f : 0.5f;
    }

    private static final net.minecraft.block.BlockState COBRE = net.minecraft.block.Blocks.CUT_COPPER.getDefaultState();

    private static void caja(MatrixStack m, VertexConsumerProvider b, int luz, int overlay, float x, float y, float z, float w, float h, float d) {
        m.push();
        m.translate(x, y, z);
        m.scale(w, h, d);
        net.minecraft.client.MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(COBRE, m, b, luz, overlay);
        m.pop();
    }

    /**
     * El manguito de cobre que alarga la boca de la cara {@code d} hasta tocar al contenedor de al lado: cuatro paredes de 1 px de un
     * caño de 8 px, del borde del bloque hacia afuera {@code largo} bloques (2026-10-21, "tiene que conectar físicamente").
     */
    private static void manguito(Direction d, float largo, MatrixStack m, VertexConsumerProvider b, int luz, int overlay) {
        if (largo < 0.02f) return;
        float f = 1f / 16f, a = 4 * f, c = 12 * f, p = f;
        // El eje del manguito y su tramo [e0, e1] (en bloques) fuera del bloque.
        float e0 = d.getDirection() == Direction.AxisDirection.POSITIVE ? 1f : -largo, e1 = e0 + largo;
        // Cuatro paredes en los otros dos ejes (u, v).
        float[][] paredes = {{a, a, c, a + p}, {a, c - p, c, c}, {a, a + p, a + p, c - p}, {c - p, a + p, c, c - p}};
        for (float[] w : paredes) {
            switch (d.getAxis()) {
                case X -> caja(m, b, luz, overlay, e0, w[1], w[0], largo, w[3] - w[1], w[2] - w[0]);
                case Y -> caja(m, b, luz, overlay, w[0], e0, w[1], w[2] - w[0], largo, w[3] - w[1]);
                case Z -> caja(m, b, luz, overlay, w[0], w[1], e0, w[2] - w[0], w[3] - w[1], largo);
            }
        }
    }

    /** Los manguitos de todas las bocas abiertas hacia un contenedor. */
    private static void manguitos(net.minecraft.world.World mundo, net.minecraft.util.math.BlockPos pos, net.minecraft.block.BlockState estado,
                                  net.minecraft.state.property.EnumProperty<TuboBlock.Extremo>[] prop, MatrixStack m, VertexConsumerProvider b, int luz, int overlay) {
        for (Direction d : Direction.values()) {
            if (estado.get(prop[d.getId()]) != TuboBlock.Extremo.BOCA) continue;
            float largo = TuboBlock.huecoHacia(mundo, pos, d);
            // Sobre una cinta frena arriba del ítem acostado, para que se lo vea caer a la banda.
            if (d == Direction.DOWN && mundo.getBlockState(pos.down()).getBlock() instanceof CintaBlock) largo = Math.max(0f, largo - 0.3f);
            manguito(d, largo, m, b, luz, overlay);
        }
    }

    public static class Tubo implements BlockEntityRenderer<TuboBlockEntity> {
        public Tubo(BlockEntityRendererFactory.Context ctx) {}

        /** Entra desde su cara hasta el centro y sale por la suya (recto o en codo); la posición sale de la hora del mundo. */
        @Override
        public void render(TuboBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider b, int luz, int overlay) {
            if (be.getWorld() != null) manguitos(be.getWorld(), be.getPos(), be.getCachedState(), TuboBlock.EXTREMO, m, b, luz, overlay);
            ItemStack stack = be.carga();
            Direction s = be.salida();
            if (stack.isEmpty() || be.getWorld() == null || s == null) return;
            Direction e = be.entrada() != null ? be.entrada() : s.getOpposite();
            float t = Math.max(0f, Math.min(1f, (be.getWorld().getTime() + tickDelta - be.llegada()) / be.ticks()));
            boolean primera = t < 0.5f;
            var mundo = be.getWorld();
            var pos = be.getPos();
            boolean boca = be.getCachedState().get(TuboBlock.EXTREMO[s.getId()]) == TuboBlock.Extremo.BOCA;
            // Al salir recorre también el manguito hasta el contenedor; hacia una cinta, hasta donde ella lo empieza a dibujar.
            Float enCinta = boca ? alcanceEnCinta(mundo, pos, s) : null;
            float largoSalida = enCinta != null ? enCinta : boca ? 0.5f + TuboBlock.huecoHacia(mundo, pos, s) : 0.5f;
            float r = primera ? 0.5f - t : (t - 0.5f) / 0.5f * largoSalida;
            Direction cara = primera ? e : s;
            float x = cara.getOffsetX() * r, y = cara.getOffsetY() * r, z = cara.getOffsetZ() * r;
            // Fundido a la pose de la cinta: al salir hacia una, o al entrar desde una (que le entrega por esa cara).
            float k = 0f, yaw = 0f;
            if (!primera && enCinta != null) {
                k = suave((t - 0.5f) / 0.5f);
                yaw = yawDe(mundo.getBlockState(pos.offset(s)));
                if (s != Direction.DOWN) y += ALTO_EN_CINTA * k;
            } else if (primera && TuboBlock.cintaHacia(mundo, pos, e)) {
                k = suave(1f - t / 0.5f);
                yaw = yawDe(mundo.getBlockState(pos.offset(e)));
                y += ALTO_EN_CINTA * k;
            }
            dibujar(stack, mundo, m, b, luz, overlay, x, y, z, tickDelta, k, yaw);
        }
    }

    public static class Empalme implements BlockEntityRenderer<EmpalmeTuboBlockEntity> {
        public Empalme(BlockEntityRendererFactory.Context ctx) {}

        @Override
        public void render(EmpalmeTuboBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider b, int luz, int overlay) {
            if (be.getWorld() == null) return;
            manguitos(be.getWorld(), be.getPos(), be.getCachedState(), EmpalmeTuboBlock.EXTREMO, m, b, luz, overlay);
            for (Direction d : Direction.values()) {
                ItemStack filtro = be.filtro(d);
                if (filtro.isEmpty()) continue;
                filtroEnCara(filtro, be.filtroPorTag(d), d, 0.505, be.getWorld(), m, b, luz, overlay);
            }
            ItemStack stack = be.carga();
            if (stack.isEmpty()) return;
            // Entra desde su cara hasta el centro y espera ahí a que se decida la salida.
            float t = Math.max(0f, Math.min(1f, (be.getWorld().getTime() + tickDelta - be.llegada()) / EmpalmeTuboBlockEntity.TICKS));
            Direction e = be.entrada();
            float r = e == null ? 0f : 0.5f * (1f - t);
            float x = e == null ? 0 : e.getOffsetX() * r, y = e == null ? 0 : e.getOffsetY() * r, z = e == null ? 0 : e.getOffsetZ() * r;
            dibujar(stack, be.getWorld(), m, b, luz, overlay, x, y, z, tickDelta);
        }
    }

    /** Un filtro sobre la cara {@code d}, mirando hacia afuera; con filtro por tag se repite apenas corrido, como una pila. */
    private static void filtroEnCara(ItemStack filtro, boolean porTag, Direction d, double dist, net.minecraft.world.World mundo,
                                     MatrixStack m, VertexConsumerProvider b, int luz, int overlay) {
        for (int copia = 0; copia < (porTag ? 2 : 1); copia++) {
            m.push();
            m.translate(0.5 + d.getOffsetX() * dist, 0.5 + d.getOffsetY() * dist, 0.5 + d.getOffsetZ() * dist);
            switch (d) {
                case NORTH -> m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
                case EAST -> m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
                case WEST -> m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90));
                case UP -> m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                case DOWN -> m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                default -> {}
            }
            m.translate(copia * 0.07, copia * -0.07, copia * 0.01);
            m.scale(0.3f, 0.3f, 0.3f);
            net.minecraft.client.MinecraftClient.getInstance().getItemRenderer().renderItem(filtro, ModelTransformationMode.FIXED,
                    luz, overlay, m, b, mundo, 0);
            m.pop();
        }
    }

    /** El filtro del extractor, sobre el costado de arriba de su cuerpo (o el de adelante si el extractor va vertical). */
    public static class Extractor implements BlockEntityRenderer<ExtractorBlockEntity> {
        public Extractor(BlockEntityRendererFactory.Context ctx) {}

        @Override
        public void render(ExtractorBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider b, int luz, int overlay) {
            if (be.getWorld() == null || be.filtro().isEmpty()) return;
            Direction f = be.getCachedState().get(ExtractorBlock.FACING);
            Direction cara = f.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
            filtroEnCara(be.filtro(), be.filtroPorTag(), cara, 0.27, be.getWorld(), m, b, luz, overlay);
        }
    }

    private TuboRenderer() {}
}
