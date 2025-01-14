package meteordevelopment.meteorclient.systems.modules.world;

import java.util.ArrayList;
import java.util.List;
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalBlock;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class AutoPortal extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> buildBind = sgGeneral.add(new KeybindSetting.Builder()
            .name("key-bind").description("Build a portal on keybind press").build());

    private final Setting<Boolean> lightPortal =
            sgGeneral.add(new BoolSetting.Builder().name("light-portal")
                    .description("Whether or not to light the portal").defaultValue(true).build());

    private final Setting<Boolean> baritonePathToPortal =
            sgGeneral.add(new BoolSetting.Builder().name("baritone-to-portal")
                    .description("Baritones to the portal after finishing building")
                    .defaultValue(true).visible(() -> BaritoneUtils.IS_AVAILABLE).build());

    public AutoPortal() {
        super(Categories.World, "auto-portal", "Automatically builds and paths to a portal");
    }

    private boolean active = false;
    private boolean keyUnpressed = false;
    private List<BlockPos> bestPortalFrameBlocks = null;
    private BlockPos ignitionPos = null;

    private void activate() {
        active = true;

        if (mc.player == null || mc.world == null)
            return;

        update();
    }

    private void deactivate(boolean built) {
        bestPortalFrameBlocks = null;
        ignitionPos = null;
        active = false;

        if (built) {
            info("Built portal");
        }
    }

    private void update() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (!active) {
            return;
        }

        if (!InvUtils.find(Items.OBSIDIAN).found()) {
            deactivate(false);
            return;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!active) {
            return;
        }

        if (bestPortalFrameBlocks == null || bestPortalFrameBlocks.isEmpty()) {
            bestPortalFrameBlocks = findBestPortalFrame();
        }

        // If we can't find a good portalm spot, give up
        if (bestPortalFrameBlocks == null || bestPortalFrameBlocks.isEmpty()) {
            deactivate(false);
            return;
        }

        if (mc.player.isUsingItem()) {
            return;
        }

        List<BlockPos> placesLeft = bestPortalFrameBlocks.stream().filter(blockPos -> {
            return mc.world.isAir(blockPos);
        }).toList();

        if (placesLeft.isEmpty()) {
            if (lightPortal.get()) {
                if (MeteorClient.SWAP.beginSwap(Items.FLINT_AND_STEEL, true)) {
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                            new BlockHitResult(ignitionPos.down().toCenterPos(), Direction.UP,
                                    ignitionPos.down(), false));

                    MeteorClient.SWAP.endSwap(true);

                    if (baritonePathToPortal.get() && BaritoneUtils.IS_AVAILABLE) {
                        BaritoneAPI.getProvider().getBaritoneForPlayer(mc.player).getCustomGoalProcess().setGoalAndPath(new GoalBlock(ignitionPos));
                    }
                } else {
                    info("Failed to light portal");
                }

                deactivate(true);
                return;
            } else {
                // If we're not lighting a portal, give up
                deactivate(true);
                return;
            }
        } else {
            if (MeteorClient.BLOCK.beginPlacement(placesLeft, Items.OBSIDIAN)) {
                placesLeft.forEach(blockPos -> {
                    MeteorClient.BLOCK.placeBlock(Items.OBSIDIAN, blockPos);
                });

                MeteorClient.BLOCK.endPlacement();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        // Custom keypress implementation because.... I don't like binding modules like this? idk
        if (!buildBind.get().isPressed()) {
            keyUnpressed = true;
        }

        if (buildBind.get().isPressed() && keyUnpressed
                && !(mc.currentScreen instanceof ChatScreen)) {
            activate();
            keyUnpressed = false;
        }

        update();

        if (ignitionPos != null) {
            event.renderer.box(ignitionPos, Color.RED, Color.RED, ShapeMode.Both, 0);
        }
    }

    private List<BlockPos> findBestPortalFrame() {
        BlockPos startPos = mc.player.getBlockPos();
        List<BlockPos> bestFrame = new ArrayList<>();
        BlockPos bestIgnitionPos = null;
        double bestPortalScore = 0;

        for (int x = -10; x <= 10; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -10; z <= 10; z++) {
                    BlockPos pos = startPos.add(x, y, z);
                    if (canBuildPortalAtPosition(pos)) {
                        int distance =
                                pos.add(1, 2, 0).getManhattanDistance(mc.player.getBlockPos());

                        double score = 1 / (double) distance;

                        if (mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) {
                            // Score portals on the ground higher because they're way easier to get
                            // to
                            score += 10;
                        }

                        if (score > bestPortalScore) {
                            bestPortalScore = score;
                            bestFrame = getPortalFramePositions(pos);
                            bestIgnitionPos = pos.add(1, 1, 0);
                        }
                    }
                }
            }
        }

        if (!bestFrame.isEmpty()) {
            ignitionPos = bestIgnitionPos;
        } else {
            ignitionPos = null;
        }

        return bestFrame;
    }

    private List<BlockPos> getPortalFramePositions(BlockPos basePos) {
        List<BlockPos> framePositions = new ArrayList<>();

        // 1-4 and 1-3 to exlude the corner pieces
        for (int y = 1; y < 4; y++) {
            framePositions.add(basePos.add(0, y, 0));
            framePositions.add(basePos.add(3, y, 0));
        }

        for (int x = 1; x < 3; x++) {
            framePositions.add(basePos.add(x, 0, 0));
            framePositions.add(basePos.add(x, 4, 0));
        }

        return framePositions;
    }

    private boolean canBuildPortalAtPosition(BlockPos pos) {
        // Just search the 4x5 portal area
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 4; x++) {
                BlockPos checkPos = pos.add(x, y, 0);
                if (!BlockUtils.canPlace(checkPos, true)
                        || mc.player.getEyePos().distanceTo(checkPos.toCenterPos()) > 6.0) {
                    return false;
                }
            }
        }

        return true;
    }
}
