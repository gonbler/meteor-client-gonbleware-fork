/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;

import java.util.Set;

public class ESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // General

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Rendering mode.")
        .defaultValue(Mode.Shader)
        .build()
    );

    public final Setting<Integer> outlineWidth = sgGeneral.add(new IntSetting.Builder()
        .name("outline-width")
        .description("The width of the shader outline.")
        .visible(() -> mode.get() == Mode.Shader)
        .defaultValue(2)
        .range(1, 10)
        .sliderRange(1, 5)
        .build()
    );

    public final Setting<Double> glowMultiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-multiplier")
        .description("Multiplier for glow effect")
        .visible(() -> mode.get() == Mode.Shader)
        .decimalPlaces(3)
        .defaultValue(3.5)
        .min(0)
        .sliderMax(10)
        .build()
    );

    public final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Ignores yourself drawing the shader.")
        .defaultValue(true)
        .build()
    );

    public final Setting<ShapeMode> shapeMode = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .visible(() -> mode.get() != Mode.Glow)
        .defaultValue(ShapeMode.Both)
        .build()
    );

    public final Setting<Double> fillOpacity = sgGeneral.add(new DoubleSetting.Builder()
        .name("fill-opacity")
        .description("The opacity of the shape fill.")
        .visible(() -> shapeMode.get() != ShapeMode.Lines && mode.get() != Mode.Glow)
        .defaultValue(0.3)
        .range(0, 1)
        .sliderMax(1)
        .build()
    );

    private final Setting<Double> fadeDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("fade-distance")
        .description("The distance from an entity where the color begins to fade.")
        .defaultValue(3)
        .min(0)
        .sliderMax(12)
        .build()
    );

    private final Setting<Double> endCrystalFadeDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("end-crystal-fade-distance")
        .description("The distance from an end crystal where the color begins to fade.")
        .defaultValue(3)
        .min(0)
        .sliderMax(12)
        .build()
    );

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Select specific entities.")
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    // Colors

    private final Setting<SettingColor> playersLineColor = sgColors.add(new ColorSetting.Builder()
        .name("players-line-color")
        .description("The line color for players.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> playersSideColor = sgColors.add(new ColorSetting.Builder()
        .name("players-side-color")
        .description("The side color for players.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> friendPlayersLineColor = sgColors.add(new ColorSetting.Builder()
        .name("friend-players-line-color")
        .description("The line color for players you have added.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> friendPlayersSideColor = sgColors.add(new ColorSetting.Builder()
        .name("friend-players-side-color")
        .description("The side color for playersyou have added.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> enemyPlayersLineColor = sgColors.add(new ColorSetting.Builder()
        .name("enemy-players-line-color")
        .description("The line color for players you have enemied.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> enemyPlayersSideColor = sgColors.add(new ColorSetting.Builder()
        .name("enemy-players-side-color")
        .description("The side color for players you have enemied.")
        .defaultValue(new SettingColor(255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> animalsLineColor = sgColors.add(new ColorSetting.Builder()
        .name("animals-line-color")
        .description("The line color for animals.")
        .defaultValue(new SettingColor(25, 255, 25, 255))
        .build()
    );

    private final Setting<SettingColor> animalsSideColor = sgColors.add(new ColorSetting.Builder()
        .name("animals-side-color")
        .description("The side color for animals.")
        .defaultValue(new SettingColor(25, 255, 25, 255))
        .build()
    );

    private final Setting<SettingColor> waterAnimalsLineColor = sgColors.add(new ColorSetting.Builder()
        .name("water-animals-line-color")
        .description("The line color for water animals.")
        .defaultValue(new SettingColor(25, 25, 255, 255))
        .build()
    );

    private final Setting<SettingColor> waterAnimalsSideColor = sgColors.add(new ColorSetting.Builder()
        .name("water-animals-side-color")
        .description("The side color for water animals.")
        .defaultValue(new SettingColor(25, 25, 255, 255))
        .build()
    );

    private final Setting<SettingColor> monstersLineColor = sgColors.add(new ColorSetting.Builder()
        .name("monsters-line-color")
        .description("The line color for monsters.")
        .defaultValue(new SettingColor(255, 25, 25, 255))
        .build()
    );

    private final Setting<SettingColor> monstersSideColor = sgColors.add(new ColorSetting.Builder()
        .name("monsters-side-color")
        .description("The side color for monsters.")
        .defaultValue(new SettingColor(255, 25, 25, 255))
        .build()
    );

    private final Setting<SettingColor> ambientLineColor = sgColors.add(new ColorSetting.Builder()
        .name("ambient-line-color")
        .description("The line color for ambient entities.")
        .defaultValue(new SettingColor(25, 25, 25, 255))
        .build()
    );

    private final Setting<SettingColor> ambientSideColor = sgColors.add(new ColorSetting.Builder()
        .name("ambient-side-color")
        .description("The side color for ambient entities.")
        .defaultValue(new SettingColor(25, 25, 25, 255))
        .build()
    );

    private final Setting<SettingColor> miscLineColor = sgColors.add(new ColorSetting.Builder()
        .name("misc-line-color")
        .description("The line color for miscellaneous entities.")
        .defaultValue(new SettingColor(175, 175, 175, 255))
        .build()
    );

    private final Setting<SettingColor> miscSideColor = sgColors.add(new ColorSetting.Builder()
        .name("misc-side-color")
        .description("The side color for miscellaneous entities.")
        .defaultValue(new SettingColor(175, 175, 175, 255))
        .build()
    );

    private final Color lineColor = new Color();
    private final Color sideColor = new Color();
    private final Color baseSideColor = new Color();
    private final Color baseLineColor = new Color();

    private final Vector3d pos1 = new Vector3d();
    private final Vector3d pos2 = new Vector3d();
    private final Vector3d pos = new Vector3d();

    private int count;

    public ESP() {
        super(Categories.Render, "esp", "Renders entities through walls.");
    }

    // Box

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mode.get() == Mode._2D) return;

        count = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (shouldSkip(entity)) continue;

            if (mode.get() == Mode.Box || mode.get() == Mode.Wireframe) drawBoundingBox(event, entity);
            count++;
        }
    }

    private void drawBoundingBox(Render3DEvent event, Entity entity) {
        Color entitySideColor = getSideColor(entity);
        Color entityLineColor = getLineColor(entity);
        if (entitySideColor != null && entityLineColor != null) {
            double alpha = 1.0;

            if (entity instanceof EndCrystalEntity) {
                double fadeDist = endCrystalFadeDistance.get() * endCrystalFadeDistance.get();
                double distance = PlayerUtils.squaredDistanceToCamera(entity);

                if (distance <= fadeDist / 2) {
                    alpha = 1.0;
                } else if (distance >= fadeDist * 2) {
                    alpha = 0.0;
                } else {
                    alpha = 1.0 - (distance - fadeDist / 2) / (fadeDist * 1.5);
                }

                if (alpha <= 0.075) {
                    alpha = 0.0;
                } else {
                    alpha += 0.1;
                }

                if (alpha > 1.0) {
                    alpha = 1.0;
                }
            }

            sideColor.set(entitySideColor).a((int) (sideColor.a * fillOpacity.get() * alpha * alpha));
            lineColor.set(entityLineColor).a((int) (lineColor.a * alpha * alpha));
        }

        if (mode.get() == Mode.Box) {
            double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
            double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
            double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();

            Box box = entity.getBoundingBox();
            event.renderer.box(x + box.minX, y + box.minY, z + box.minZ, x + box.maxX, y + box.maxY, z + box.maxZ, sideColor, lineColor, shapeMode.get(), 0);
        } else {
            WireframeEntityRenderer.render(event, entity, 1, sideColor, lineColor, shapeMode.get());
        }
    }

    // 2D

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mode.get() != Mode._2D) return;

        Renderer2D.COLOR.begin();
        count = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (shouldSkip(entity)) continue;

            Box box = entity.getBoundingBox();

            double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
            double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
            double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();

            // Check corners
            pos1.set(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
            pos2.set(0, 0, 0);

            //     Bottom
            if (checkCorner(box.minX + x, box.minY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.minY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.minX + x, box.minY + y, box.maxZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.minY + y, box.maxZ + z, pos1, pos2)) continue;

            //     Top
            if (checkCorner(box.minX + x, box.maxY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.maxY + y, box.minZ + z, pos1, pos2)) continue;
            if (checkCorner(box.minX + x, box.maxY + y, box.maxZ + z, pos1, pos2)) continue;
            if (checkCorner(box.maxX + x, box.maxY + y, box.maxZ + z, pos1, pos2)) continue;

            // Setup color
            Color entitySideColor = getSideColor(entity);
            Color entityLineColor = getLineColor(entity);
            if (entitySideColor != null && entityLineColor != null) {
                sideColor.set(entitySideColor).a((int) (sideColor.a * fillOpacity.get()));
                lineColor.set(entityLineColor);
            }

            // Render
            if (shapeMode.get() != ShapeMode.Lines && sideColor.a > 0) {
                Renderer2D.COLOR.quad(pos1.x, pos1.y, pos2.x - pos1.x, pos2.y - pos1.y, sideColor);
            }

            if (shapeMode.get() != ShapeMode.Sides) {
                Renderer2D.COLOR.line(pos1.x, pos1.y, pos1.x, pos2.y, lineColor);
                Renderer2D.COLOR.line(pos2.x, pos1.y, pos2.x, pos2.y, lineColor);
                Renderer2D.COLOR.line(pos1.x, pos1.y, pos2.x, pos1.y, lineColor);
                Renderer2D.COLOR.line(pos1.x, pos2.y, pos2.x, pos2.y, lineColor);
            }

            count++;
        }

        Renderer2D.COLOR.render(null);
    }

    private boolean checkCorner(double x, double y, double z, Vector3d min, Vector3d max) {
        pos.set(x, y, z);
        if (!NametagUtils.to2D(pos, 1)) return true;

        // Check Min
        if (pos.x < min.x) min.x = pos.x;
        if (pos.y < min.y) min.y = pos.y;
        if (pos.z < min.z) min.z = pos.z;

        // Check Max
        if (pos.x > max.x) max.x = pos.x;
        if (pos.y > max.y) max.y = pos.y;
        if (pos.z > max.z) max.z = pos.z;

        return false;
    }

    // Utils

    public boolean shouldSkip(Entity entity) {
        if (!entities.get().contains(entity.getType())) return true;
        if (entity == mc.player && ignoreSelf.get()) return true;
        if (entity == mc.cameraEntity && mc.options.getPerspective().isFirstPerson()) return true;
        return !EntityUtils.isInRenderDistance(entity);
    }

    public Color getLineColor(Entity entity) {
        if (!entities.get().contains(entity.getType())) return null;

        double alpha = getFadeAlpha(entity);
        if (alpha == 0) return null;

        Color color = getEntityTypeLineColor(entity);
        return baseLineColor.set(color.r, color.g, color.b, (int) (color.a * alpha));
    }

    public Color getSideColor(Entity entity) {
        if (!entities.get().contains(entity.getType())) return null;

        double alpha = getFadeAlpha(entity);
        if (alpha == 0) return null;

        Color color = getEntityTypeSideColor(entity);
        return baseSideColor.set(color.r, color.g, color.b, (int) (color.a * alpha));
    }

    public Color getEntityTypeLineColor(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            if (Friends.get().isFriend(player)) {
                return friendPlayersLineColor.get();
            }

            if (Friends.get().isEnemy(player)) {
                return enemyPlayersLineColor.get();
            }
            
            return playersLineColor.get();
        } else {
            return switch (entity.getType().getSpawnGroup()) {
                case CREATURE -> animalsLineColor.get();
                case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> waterAnimalsLineColor.get();
                case MONSTER -> monstersLineColor.get();
                case AMBIENT -> ambientLineColor.get();
                default -> miscLineColor.get();
            };
        }
    }

    public Color getEntityTypeSideColor(Entity entity) {
        if (entity instanceof PlayerEntity player) {
            if (Friends.get().isFriend(player)) {
                return friendPlayersSideColor.get();
            }

            if (Friends.get().isEnemy(player)) {
                return enemyPlayersSideColor.get();
            }
            
            return playersSideColor.get();
        } else {
            return switch (entity.getType().getSpawnGroup()) {
                case CREATURE -> animalsSideColor.get();
                case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> waterAnimalsSideColor.get();
                case MONSTER -> monstersSideColor.get();
                case AMBIENT -> ambientSideColor.get();
                default -> miscSideColor.get();
            };
        }
    }

    private double getFadeAlpha(Entity entity) {
        double dist = PlayerUtils.squaredDistanceToCamera(entity.getX() + entity.getWidth() / 2, entity.getY() + entity.getEyeHeight(entity.getPose()), entity.getZ() + entity.getWidth() / 2);
        double fadeDist = Math.pow(fadeDistance.get(), 2);
        double alpha = 1;
        if (dist <= fadeDist * fadeDist) alpha = (float) (Math.sqrt(dist) / fadeDist);
        if (alpha <= 0.075) alpha = 0;
        return alpha;
    }

    @Override
    public String getInfoString() {
        return Integer.toString(count);
    }

    public boolean isShader() {
        return isActive() && mode.get() == Mode.Shader;
    }

    public boolean isGlow() {
        return isActive() && mode.get() == Mode.Glow;
    }

    public enum Mode {
        Box,
        Wireframe,
        _2D,
        Shader,
        Glow;

        @Override
        public String toString() {
            return this == _2D ? "2D" : super.toString();
        }
    }
}
