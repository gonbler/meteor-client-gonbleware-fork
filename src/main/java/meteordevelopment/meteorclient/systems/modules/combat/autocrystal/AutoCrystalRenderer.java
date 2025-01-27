package meteordevelopment.meteorclient.systems.modules.combat.autocrystal;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.utils.player.Timer;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer.RenderablePart;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class AutoCrystalRenderer {
    private final Settings settings = new Settings();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // -- Render -- //
    private final Setting<RenderMode> renderMode =
            sgRender.add(new EnumSetting.Builder<RenderMode>().name("render-mode")
                    .description("Mode for rendering.").defaultValue(RenderMode.DelayDraw).build());

    // Simple mode
    private final Setting<ShapeMode> simpleShapeMode =
            sgRender.add(new EnumSetting.Builder<ShapeMode>().name("simple-shape-mode")
                    .description("How the shapes are rendered.").defaultValue(ShapeMode.Both)
                    .visible(() -> renderMode.get() == RenderMode.Simple).build());

    private final Setting<SettingColor> simpleColor =
            sgRender.add(new ColorSetting.Builder().name("simple-color")
                    .description("Color to render place delays in").defaultValue(Color.RED.a(40))
                    .visible(() -> renderMode.get() == RenderMode.Simple).build());

    private final Setting<Double> simpleDrawTime = sgRender.add(new DoubleSetting.Builder()
            .name("simple-draw-time").description("How long to draw the box").defaultValue(0.15)
            .min(0).sliderMax(1.0).visible(() -> renderMode.get() == RenderMode.Simple).build());

    // Delay mode
    private final Setting<ShapeMode> placeDelayShapeMode =
            sgRender.add(new EnumSetting.Builder<ShapeMode>().name("place-delay-shape-mode")
                    .description("How the shapes are rendered.").defaultValue(ShapeMode.Both)
                    .visible(() -> renderMode.get() == RenderMode.DelayDraw).build());

    private final Setting<SettingColor> placeDelayColor = sgRender.add(new ColorSetting.Builder()
            .name("place-delay-color").description("Color to render place delays in")
            .defaultValue(new Color(110, 0, 255, 40))
            .visible(() -> renderMode.get() == RenderMode.DelayDraw).build());

    private final Setting<Double> placeDelayFadeTime = sgRender.add(new DoubleSetting.Builder()
            .name("place-delay-fade-time").description("How long to fade the box").defaultValue(0.7)
            .min(0.0).sliderMax(2.0).visible(() -> renderMode.get() == RenderMode.DelayDraw)
            .build());

    private final Setting<ShapeMode> breakDelayShapeMode =
            sgRender.add(new EnumSetting.Builder<ShapeMode>().name("break-delay-shape-mode")
                    .description("How the shapes are rendered.").defaultValue(ShapeMode.Both)
                    .visible(() -> renderMode.get() == RenderMode.DelayDraw).build());

    private final Setting<SettingColor> breakDelayColor =
            sgRender.add(new ColorSetting.Builder().name("break-delay-color")
                    .description("Color to render break delays in").defaultValue(Color.BLACK.a(0))
                    .visible(() -> renderMode.get() == RenderMode.DelayDraw).build());

    private final Setting<Double> breakDelayFadeTime = sgRender.add(new DoubleSetting.Builder()
            .name("break-delay-fade-time").description("How long to fade the box").defaultValue(0.4)
            .min(0.0).sliderMax(2.0).visible(() -> renderMode.get() == RenderMode.DelayDraw)
            .build());

    private final Setting<Double> breakDelayFadeExponent = sgRender.add(new DoubleSetting.Builder()
            .name("break-delay-fade-exponent").description("Adds an exponent to the fade")
            .defaultValue(1.6).min(0.2).sliderMax(4.0)
            .visible(() -> renderMode.get() == RenderMode.DelayDraw).build());

    private final Map<BlockPos, Long> crystalRenderPlaceDelays = new HashMap<>();
    private final Map<CrystalBreakRender, Long> crystalRenderBreakDelays = new HashMap<>();

    private final AutoCrystal autoCrystal;

    private BlockPos simpleRenderPos = null;
    private Timer simpleRenderTimer = new Timer();

    public AutoCrystalRenderer(AutoCrystal ac) {
        // Add the render settings to the parent AC modle
        ac.settings.groups.addAll(settings.groups);

        autoCrystal = ac;
    }

    // Called by AC
    public void onActivate() {
        crystalRenderPlaceDelays.clear();
        crystalRenderBreakDelays.clear();
    }

    // Called by AC
    public void onRender3D(Render3DEvent event) {
        switch (renderMode.get()) {
            case Simple -> drawSimple(event);
            case DelayDraw -> drawDelay(event);
            case Debug -> drawDebug(event);

            // Do nothing in case of none (duh (hello))
            case None -> {}
        }
    }

    // Called by AC
    public void onBreakCrystal(Entity entity) {
        long currentTime = System.currentTimeMillis();

        CrystalBreakRender breakRender = new CrystalBreakRender();
        breakRender.pos = new Vec3d(0, 0, 0);
        breakRender.entity = entity;
        crystalRenderBreakDelays.put(breakRender, currentTime);
    }

    // Called by AC
    public void onPlaceCrystal(BlockPos pos) {
        long currentTime = System.currentTimeMillis();

        crystalRenderPlaceDelays.put(pos, currentTime);

        simpleRenderPos = pos;
        simpleRenderTimer.reset();
    }

    private void drawSimple(Render3DEvent event) {
        if (simpleRenderPos != null && !simpleRenderTimer.passedS(simpleDrawTime.get())) {
            event.renderer.box(simpleRenderPos, simpleColor.get(), simpleColor.get(),
                    simpleShapeMode.get(), 0);
        }
    }

    private void drawDelay(Render3DEvent event) {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<BlockPos, Long> placeDelay : crystalRenderPlaceDelays.entrySet()) {
            if (currentTime - placeDelay.getValue() > placeDelayFadeTime.get() * 1000) {
                continue;
            }

            double time = (currentTime - placeDelay.getValue()) / 1000.0;

            double timeCompletion = time / placeDelayFadeTime.get();

            renderBoxSized(event, placeDelay.getKey(), 1.0, 1 - timeCompletion,
                    placeDelayColor.get(), placeDelayColor.get(), placeDelayShapeMode.get());
        }

        for (Map.Entry<CrystalBreakRender, Long> breakDelay : crystalRenderBreakDelays.entrySet()) {
            if (currentTime - breakDelay.getValue() > breakDelayFadeTime.get() * 1000) {
                continue;
            }

            CrystalBreakRender render = breakDelay.getKey();

            if (render.parts == null && render.entity != null) {
                render.parts = WireframeEntityRenderer.cloneEntityForRendering(event, render.entity,
                        render.pos);
                render.entity = null;
            }

            double time = (currentTime - breakDelay.getValue()) / 1000.0;

            double timeCompletion = time / breakDelayFadeTime.get();

            Color color = breakDelayColor.get().copy().a((int) (breakDelayColor.get().a
                    * Math.pow(1 - timeCompletion, breakDelayFadeExponent.get())));

            WireframeEntityRenderer.render(event, render.pos, render.parts, 1.0, color, color,
                    breakDelayShapeMode.get());
        }
    }

    private void drawDebug(Render3DEvent event) {
        int r = (int) Math.floor(autoCrystal.placeRange.get());

        BlockPos eyePos = BlockPos.ofFloored(mc.player.getEyePos());
        int ex = eyePos.getX();
        int ey = eyePos.getY();
        int ez = eyePos.getZ();

        // TODO: Move to final field
        BlockPos.Mutable mutablePos = new BlockPos.Mutable(0, 0, 0);

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (autoCrystal.cachedValidSpots
                            .get((x + r) * ((2 * r) * (2 * r)) + (y + r) * (2 * r) + (z + r))) {
                        BlockPos pos = mutablePos.set(ex + x, ey + y, ez + z);

                        event.renderer.box(pos, simpleColor.get(), simpleColor.get(),
                                simpleShapeMode.get(), 0);
                    }
                }
            }
        }
    }

    private void renderBoxSized(Render3DEvent event, BlockPos blockPos, double size, double alpha,
            Color sideColor, Color lineColor, ShapeMode shapeMode) {
        Box orig = new Box(0, 0, 0, 1.0, 1.0, 1.0);

        double shrinkFactor = 1d - size;

        Box box = orig.shrink(orig.getLengthX() * shrinkFactor, orig.getLengthY() * shrinkFactor,
                orig.getLengthZ() * shrinkFactor);

        double xShrink = (orig.getLengthX() * shrinkFactor) / 2;
        double yShrink = (orig.getLengthY() * shrinkFactor) / 2;
        double zShrink = (orig.getLengthZ() * shrinkFactor) / 2;

        double x1 = blockPos.getX() + box.minX + xShrink;
        double y1 = blockPos.getY() + box.minY + yShrink;
        double z1 = blockPos.getZ() + box.minZ + zShrink;
        double x2 = blockPos.getX() + box.maxX + xShrink;
        double y2 = blockPos.getY() + box.maxY + yShrink;
        double z2 = blockPos.getZ() + box.maxZ + zShrink;

        event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor.copy().a((int) (sideColor.a * alpha)),
                sideColor.copy().a((int) (lineColor.a * alpha)), shapeMode, 0);
    }

    private class CrystalBreakRender {
        public Vec3d pos;

        public List<RenderablePart> parts;

        public Entity entity;
    }

    private enum RenderMode {
        None, DelayDraw, Simple, Debug
    }
}
