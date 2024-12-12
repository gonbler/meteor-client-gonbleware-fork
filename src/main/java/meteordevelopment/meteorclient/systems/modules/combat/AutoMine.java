package meteordevelopment.meteorclient.systems.modules.combat;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.events.meteor.SilentMineFinishedEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range")
            .description("Max range to target").defaultValue(6.0).min(0).sliderMax(7.0).build());

    private final Setting<SortPriority> targetPriority =
            sgGeneral.add(new EnumSetting.Builder<SortPriority>().name("target-priority")
                    .description("How to choose the target").defaultValue(SortPriority.ClosestAngle)
                    .build());

    private final Setting<AntiSwimMode> antiSwim =
            sgGeneral.add(new EnumSetting.Builder<AntiSwimMode>().name("anti-swim-mode")
                    .description(
                            "Starts mining your head block when the enemy starts mining your feet")
                    .defaultValue(AntiSwimMode.OnMine).build());

    private final Setting<Double> antiSurroundInnerTime = sgGeneral.add(new DoubleSetting.Builder()
            .name("anti-surround-inner-spam-time").description("Max range to target")
            .defaultValue(0.1).min(0).sliderMax(0.3).build());

    private final Setting<AntiSurroundMode> antiSurroundMode =
            sgGeneral.add(new EnumSetting.Builder<AntiSurroundMode>().name("anti-surround-mode")
                    .description("Places crystals in places to prevent surround")
                    .defaultValue(AntiSurroundMode.Auto).build());

    private SilentMine silentMine = null;

    private PlayerEntity targetPlayer = null;

    private CityBlock target1 = null;
    private CityBlock target2 = null;

    private Map<BlockPos, Long> crystalSpamTargets = new HashMap<>();

    private List<BlockPos> removePoses = new ArrayList<>();

    public AutoMine() {
        super(Categories.Combat, "auto-mine", "Automatically mines blocks. Requires SilentMine to work.");

        silentMine = (SilentMine) Modules.get().get(SilentMine.class);
    }

    @Override
    public void onActivate() {
        super.onActivate();

        if (silentMine == null) {
            silentMine = (SilentMine) Modules.get().get(SilentMine.class);
        }

        crystalSpamTargets.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (silentMine == null) {
            silentMine = (SilentMine) Modules.get().get(SilentMine.class);
        }

        Long currentTime = System.currentTimeMillis();
        removePoses.clear();

        synchronized (crystalSpamTargets) {
            for (Map.Entry<BlockPos, Long> spamTarget : crystalSpamTargets.entrySet()) {
                double difference = (currentTime - spamTarget.getValue()) / 1000.0;

                if (difference > antiSurroundInnerTime.get()) {
                    removePoses.add(spamTarget.getKey());
                }

                Modules.get().get(AutoCrystal.class).preplaceCrystal(spamTarget.getKey());
                info("Placed?");
            }

            for (BlockPos removePos : removePoses) {
                crystalSpamTargets.remove(removePos);
            }
        }
    }

    @EventHandler
    private void onSilentMineFinished(SilentMineFinishedEvent.Pre event) {
        if (targetPlayer == null) {
            return;
        }

        AntiSurroundMode mode = antiSurroundMode.get();

        if (mode == AntiSurroundMode.None) {
            return;
        }

        if (mode == AntiSurroundMode.Auto || mode == AntiSurroundMode.Outer) {
            for (Direction dir : Direction.HORIZONTAL) {
                BlockPos playerSurroundBlock = targetPlayer.getBlockPos().offset(dir);

                for (Direction outerDir : Direction.HORIZONTAL) {
                    BlockPos outerCrystalPos = playerSurroundBlock.offset(outerDir);

                    Modules.get().get(AutoCrystal.class).preplaceCrystal(outerCrystalPos);

                    mode = AntiSurroundMode.Outer;
                }
            }
        }

        synchronized (crystalSpamTargets) {
            if (mode == AntiSurroundMode.Auto || mode == AntiSurroundMode.Inner) {
                for (Direction dir : Direction.HORIZONTAL) {
                    BlockPos playerSurroundBlock = targetPlayer.getBlockPos().offset(dir);

                    if (playerSurroundBlock.equals(event.getBlockPos())) {
                        // Modules.get().get(AutoCrystal.class).preplaceCrystal(playerSurroundBlock);
                        crystalSpamTargets.put(playerSurroundBlock, System.currentTimeMillis());
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (silentMine == null) {
            silentMine = (SilentMine) Modules.get().get(SilentMine.class);
        }

        if (event.packet instanceof BlockBreakingProgressS2CPacket packet) {
            if (antiSwim.get() == AntiSwimMode.OnMine
                    || antiSwim.get() == AntiSwimMode.OnMineAndSwim) {
                if (!mc.player.getBlockPos().equals(packet.getPos())) {
                    return;
                }

                BlockState selfFeetBlock = mc.world.getBlockState(mc.player.getBlockPos());
                BlockState selfHeadBlock =
                        mc.world.getBlockState(mc.player.getBlockPos().offset(Direction.UP));

                if (BlockUtils.canBreak(mc.player.getBlockPos().offset(Direction.UP), selfHeadBlock)
                        && selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)) {
                    silentMine.silentBreakBlock(mc.player.getBlockPos().offset(Direction.UP), 20);
                }
            }
        }
    }

    private void update() {
        if (silentMine == null) {
            silentMine = (SilentMine) Modules.get().get(SilentMine.class);
        }

        BlockState selfFeetBlock = mc.world.getBlockState(mc.player.getBlockPos());
        BlockState selfHeadBlock =
                mc.world.getBlockState(mc.player.getBlockPos().offset(Direction.UP));

        boolean prioHead = false;

        if (antiSwim.get() == AntiSwimMode.Always) {
            if (BlockUtils.canBreak(mc.player.getBlockPos().offset(Direction.UP), selfHeadBlock)
                    && selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)
                    && (!silentMine.hasDelayedDestroy()
                            || silentMine.getRebreakBlockPos() == null)) {
                silentMine.silentBreakBlock(mc.player.getBlockPos().offset(Direction.UP), 20);

                prioHead = true;
            }
        }

        if (antiSwim.get() == AntiSwimMode.OnMineAndSwim && mc.player.isCrawling()) {
            if (BlockUtils.canBreak(mc.player.getBlockPos().offset(Direction.UP), selfHeadBlock)
                    && selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)
                    && (!silentMine.hasDelayedDestroy()
                            || silentMine.getRebreakBlockPos() == null)) {

                silentMine.silentBreakBlock(mc.player.getBlockPos().offset(Direction.UP), 20);

                prioHead = true;
            }
        }

        targetPlayer = TargetUtils.getPlayerTarget(range.get(), targetPriority.get());

        if (targetPlayer == null) {
            return;
        }

        if (silentMine.hasDelayedDestroy() && selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)
                && selfFeetBlock.isAir() && silentMine.getRebreakBlockPos() == mc.player
                        .getBlockPos().offset(Direction.UP)) {
            return;
        }

        if (!prioHead) {
            findTargetBlocks();

            boolean isTargetingFeetBlock = (target1 != null && target1.isFeetBlock) || (target2 != null && target2.isFeetBlock);

            if (!isTargetingFeetBlock && ((target1 != null && target1.blockPos.equals(silentMine.getRebreakBlockPos())) || (target2 != null && target2.blockPos.equals(silentMine.getRebreakBlockPos())))) {
                return;
            }

            boolean hasBothInProgress = silentMine.hasDelayedDestroy()
                    && silentMine.hasRebreakBlock() && !silentMine.canRebreakRebreakBlock();

            if (hasBothInProgress) {
                return;
            }

            Queue<BlockPos> targetBlocks = new LinkedList<>();
            if (target1 != null) {
                targetBlocks.add(target1.blockPos);
            }

            if (target2 != null) {
                targetBlocks.add(target2.blockPos);
            }


            if (!targetBlocks.isEmpty() && silentMine.hasDelayedDestroy()) {
                silentMine.silentBreakBlock(targetBlocks.remove(), 10);
            }

            if (!targetBlocks.isEmpty() && (!silentMine.hasRebreakBlock() || silentMine.canRebreakRebreakBlock())) {
                silentMine.silentBreakBlock(targetBlocks.remove(), 10);
            }
        }
    }

    private void render(Render3DEvent event) {

    }

    private void findTargetBlocks() {
        target1 = findCityBlock(null);
        target2 = findCityBlock(target1 != null ? target1.blockPos : null);
    }

    private CityBlock findCityBlock(BlockPos exclude) {
        if (targetPlayer == null) {
            return null;
        }

        boolean set = false;
        CityBlock bestBlock = new CityBlock();
        
        List<BlockPos> checkPos = Direction.Type.HORIZONTAL.stream()
                .map(x -> targetPlayer.getBlockPos().offset(x)).collect(Collectors.toList());
        checkPos.add(targetPlayer.getBlockPos());


        for (BlockPos pos : checkPos) {
            if (pos.equals(exclude)) {
                continue;
            }

            BlockState block = mc.world.getBlockState(pos);
            boolean isPosGoodRebreak = silentMine.canRebreakRebreakBlock()
                    && pos.equals(silentMine.getRebreakBlockPos())
                    && !pos.equals(targetPlayer.getBlockPos());

            if (block.isAir() && !isPosGoodRebreak) {
                continue;
            }

            double score = 0;

            if (!BlockUtils.canBreak(pos, block) && !isPosGoodRebreak) {
                continue;
            }

            boolean isFeetBlock = false;

            // Feet / swim case
            if (pos.equals(targetPlayer.getBlockPos())) {
                BlockState headBlock = mc.world.getBlockState(pos.offset(Direction.UP));

                // If their in 2-tall bedrock, mine out their feet
                if (headBlock.getBlock().equals(Blocks.OBSIDIAN)) {
                    // Give lots of score to blocks that will make them swim
                    score += 100;
                } else {
                    // Mine out their feet-only phase
                    score += 30;
                }

                isFeetBlock = true;
            } else {
                BlockState selfFeetBlock = mc.world.getBlockState(mc.player.getBlockPos());
                BlockState selfHeadBlock =
                        mc.world.getBlockState(mc.player.getBlockPos().offset(Direction.UP));

                if (pos.equals(mc.player.getBlockPos())
                        && (selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)
                                || selfHeadBlock.getBlock().equals(Blocks.BEDROCK))) {
                    continue;
                }

                if (!selfFeetBlock.getBlock().equals(Blocks.OBSIDIAN)
                        && !selfFeetBlock.getBlock().equals(Blocks.BEDROCK)) {
                    boolean isPosSurroundBlock = false;
                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        if (!mc.player.getBlockPos().offset(dir).equals(pos)) {
                            continue;
                        }

                        BlockState possibleSurroundBlock =
                                mc.world.getBlockState(mc.player.getBlockPos().offset(dir));
                        if (possibleSurroundBlock.getBlock().equals(Blocks.OBSIDIAN)) {
                            isPosSurroundBlock = true;
                            break;
                        }
                    }

                    if (isPosSurroundBlock) {
                        score -= 5;
                    }

                    boolean isPosAntiSurround = false;
                    for (Direction dir : Direction.Type.HORIZONTAL) {
                        if (pos.offset(dir).equals(pos) || pos.offset(dir).equals(targetPlayer.getBlockPos())) {
                            continue;
                        }

                        if (mc.world.getBlockState(pos.offset(dir)).isAir()) {
                            isPosAntiSurround = true;
                            break;
                        }
                    }

                    if (isPosAntiSurround) {
                        score += 15;
                    }
                }

                if (isPosGoodRebreak) {
                    score += 50;
                }
            }


            boolean outOfRange = Utils.distance(mc.player.getX() - 0.5,
                    mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
                    mc.player.getZ() - 0.5, pos.getX(), pos.getY(),
                    pos.getZ()) > mc.player.getBlockInteractionRange() + 1;

            if (outOfRange) {
                continue;
            }

            double d = targetPlayer.getPos().distanceTo(Vec3d.ofCenter(pos));

            // The closer the block is, the higher score it gets
            score += 10 / d;

            if (score > bestBlock.score) {
                bestBlock.score = score;
                bestBlock.blockPos = pos;
                bestBlock.isFeetBlock = isFeetBlock;
                set = true;
            }
        }

        if (set) {
            return bestBlock;
        } else {
            return null;
        }
    }

    public boolean isTargetedPos(BlockPos pos) {
        return (target1 != null && target1.blockPos.equals(pos)) || (target2 != null && target2.blockPos.equals(pos));
    }

    public boolean isTargetingAnything() {
        return target1 != null && target2 != null;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null)
            return;

        update();
        render(event);
    }

    private class CityBlock {
        public BlockPos blockPos;

        public double score;

        public boolean isFeetBlock = false;
    }

    private enum AntiSwimMode {
        None, Always, OnMine, OnMineAndSwim
    }

    private enum AntiSurroundMode {
        None, Inner, Outer, Auto
    }
}
