package meteordevelopment.meteorclient.systems.modules.combat;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import meteordevelopment.meteorclient.events.meteor.SilentMineFinishedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
import meteordevelopment.meteorclient.systems.modules.render.BreakIndicators;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoMine extends Module {
    private final double INVALID_SCORE = -1000;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range")
            .description("Max range to target").defaultValue(6.5).min(0).sliderMax(7.0).build());

    private final Setting<SortPriority> targetPriority =
            sgGeneral.add(new EnumSetting.Builder<SortPriority>().name("target-priority")
                    .description("How to choose the target").defaultValue(SortPriority.ClosestAngle)
                    .build());

    private final Setting<Boolean> ignoreNakeds =
            sgGeneral.add(new BoolSetting.Builder().name("ignore-nakeds")
                    .description("Ignore players with no items.").defaultValue(true).build());

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
        super(Categories.Combat, "auto-mine",
                "Automatically mines blocks. Requires SilentMine to work.");

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

    private void update() {
        if (silentMine == null) {
            silentMine = (SilentMine) Modules.get().get(SilentMine.class);
        }

        BlockState selfFeetBlock = mc.world.getBlockState(mc.player.getBlockPos());
        BlockState selfHeadBlock = mc.world.getBlockState(mc.player.getBlockPos().up());
        boolean shouldBreakSelfHeadBlock =
                BlockUtils.canBreak(mc.player.getBlockPos().up(), selfHeadBlock)
                        && (selfHeadBlock.isOf(Blocks.OBSIDIAN)
                                || selfHeadBlock.isOf(Blocks.CRYING_OBSIDIAN));

        boolean prioHead = false;

        if (antiSwim.get() == AntiSwimMode.Always) {
            if (shouldBreakSelfHeadBlock) {
                silentMine.silentBreakBlock(mc.player.getBlockPos().up(), 10);
                prioHead = true;
            }
        }

        if (antiSwim.get() == AntiSwimMode.OnMineAndSwim && mc.player.isCrawling()) {
            if (shouldBreakSelfHeadBlock) {
                silentMine.silentBreakBlock(mc.player.getBlockPos().up(), 30);
                prioHead = true;
            }
        }

        if (antiSwim.get() == AntiSwimMode.OnMine || antiSwim.get() == AntiSwimMode.OnMineAndSwim) {
            BreakIndicators breakIndicators = Modules.get().get(BreakIndicators.class);

            if (breakIndicators.isBlockBeingBroken(mc.player.getBlockPos())
                    && shouldBreakSelfHeadBlock) {
                silentMine.silentBreakBlock(mc.player.getBlockPos().up(), 20);
                prioHead = true;
            }
        }

        targetPlayer = (PlayerEntity) TargetUtils.get(entity -> {
            if (entity.equals(mc.player) || entity.equals(mc.cameraEntity))
                return false;

            if (!(entity instanceof PlayerEntity player)) {
                return false;
            }

            if (!player.isAlive() || player.isDead())
                return false;

            if (player.isCreative())
                return false;

            if (!Friends.get().shouldAttack(player))
                return false;

            if (entity.getPos().distanceTo(mc.player.getEyePos()) > range.get()) {
                return false;
            }

            if (ignoreNakeds.get()) {
                if (player.getInventory().armor.get(0).isEmpty()
                        && player.getInventory().armor.get(1).isEmpty()
                        && player.getInventory().armor.get(2).isEmpty()
                        && player.getInventory().armor.get(3).isEmpty())
                    return false;
            }

            return true;
        }, targetPriority.get());

        if (targetPlayer == null) {
            return;
        }

        if (silentMine.hasDelayedDestroy() && selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)
                && selfFeetBlock.isAir()
                && silentMine.getRebreakBlockPos() == mc.player.getBlockPos().up()) {
            return;
        }

        if (prioHead) {
            return;
        }

        findTargetBlocks();

        boolean isTargetingFeetBlock = (target1 != null && target1.isFeetBlock)
                || (target2 != null && target2.isFeetBlock);

        if (!isTargetingFeetBlock && ((target1 != null
                && target1.blockPos.equals(silentMine.getRebreakBlockPos()))
                || (target2 != null && target2.blockPos.equals(silentMine.getRebreakBlockPos())))) {
            return;
        }

        boolean hasBothInProgress = silentMine.hasDelayedDestroy() && silentMine.hasRebreakBlock()
                && !silentMine.canRebreakRebreakBlock();

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

        if (!targetBlocks.isEmpty()
                && (!silentMine.hasRebreakBlock() || silentMine.canRebreakRebreakBlock())) {
            silentMine.silentBreakBlock(targetBlocks.remove(), 10);
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

        Set<BlockPos> checkPos = new HashSet<>();

        Box boundingBox = targetPlayer.getBoundingBox().shrink(0.01, 0.1, 0.01);
        double feetY = targetPlayer.getY();
        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        boolean inBedrock = BlockPos.stream(feetBox).anyMatch(blockPos -> {
            return mc.world.getBlockState(blockPos).getBlock() == Blocks.BEDROCK;
        });

        if (inBedrock) {
            addBedrockCaseCheckPositions(checkPos);
        } else {
            addNormalCaseCheckPositions(checkPos);
        }

        for (BlockPos pos : checkPos) {
            if (pos.equals(exclude)) {
                continue;
            }

            BlockState block = mc.world.getBlockState(pos);
            boolean isPosGoodRebreak = false;

            if (silentMine.canRebreakRebreakBlock()
                    && pos.equals(silentMine.getRebreakBlockPos())) {
                if (inBedrock) {
                    boolean isSelfTrapBlock = false;

                    for (Direction dir : Direction.HORIZONTAL) {
                        if (targetPlayer.getBlockPos().up().offset(dir).equals(pos)) {
                            isSelfTrapBlock = true;
                            break;
                        }
                    }


                    boolean canFacePlace =
                            mc.world.getBlockState(targetPlayer.getBlockPos().up()).isAir();

                    // It's a good rebreak if it's a self trap block for their head or the block
                    // above their head (no velo meta)
                    isPosGoodRebreak = BlockPos.stream(feetBox).count() == 1
                            && (pos.equals(targetPlayer.getBlockPos().up(2))
                                    || (isSelfTrapBlock && canFacePlace));
                } else {
                    // It's a good rebreak if it's not their feet block
                    isPosGoodRebreak =
                            !pos.equals(targetPlayer.getBlockPos()) && !isBlockInFeet(pos);
                }
            }

            if (block.isAir() && !isPosGoodRebreak) {
                continue;
            }

            boolean isFeetBlock = isBlockInFeet(pos);

            if (!BlockUtils.canBreak(pos, block) && !isPosGoodRebreak) {
                continue;
            }


            if (!silentMine.inBreakRange(pos)) {
                continue;
            }

            double score = 0;

            if (inBedrock) {
                score = scoreBedrockCityBlock(pos);
            } else {
                score = scoreNormalCityBlock(pos);
            }

            // Ignore blocks with -1000
            if (score == INVALID_SCORE) {
                continue;
            }

            // If it's a good rebreak, keep it
            if (isPosGoodRebreak) {
                score += 40;
            }

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

    // Adds the positions for normal flat pvp (like obsidian and shit)
    private void addNormalCaseCheckPositions(Set<BlockPos> checkPos) {
        Box boundingBox = targetPlayer.getBoundingBox().shrink(0.01, 0.1, 0.01);
        double feetY = targetPlayer.getY();
        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        for (BlockPos pos : BlockPos.iterate((int) Math.floor(feetBox.minX),
                (int) Math.floor(feetBox.minY), (int) Math.floor(feetBox.minZ),
                (int) Math.floor(feetBox.maxX), (int) Math.floor(feetBox.maxY),
                (int) Math.floor(feetBox.maxZ))) {

            checkPos.add(pos);

            for (Direction dir : Direction.Type.HORIZONTAL) {
                if (checkPos.contains(pos.offset(dir))) {
                    continue;
                }

                checkPos.add(pos.offset(dir));
            }
        }

        checkPos.add(targetPlayer.getBlockPos());
    }

    private void addBedrockCaseCheckPositions(Set<BlockPos> checkPos) {
        Box boundingBox = targetPlayer.getBoundingBox().shrink(0.01, 0.1, 0.01);
        double feetY = targetPlayer.getY();
        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        // Only mine them down if they can actually fall
        boolean canFallDown = BlockPos.stream(feetBox).allMatch(blockPos -> {
            return mc.world.getBlockState(blockPos.down()).getBlock() != Blocks.BEDROCK;
        });

        // Only break their head if they can actually be pushed up
        boolean canBeHitUp = BlockPos.stream(feetBox).allMatch(blockPos -> {
            return mc.world.getBlockState(blockPos.up(2)).getBlock() != Blocks.BEDROCK;
        });

        for (BlockPos pos : BlockPos.iterate((int) Math.floor(feetBox.minX),
                (int) Math.floor(feetBox.minY), (int) Math.floor(feetBox.minZ),
                (int) Math.floor(feetBox.maxX), (int) Math.floor(feetBox.maxY),
                (int) Math.floor(feetBox.maxZ))) {

            if (canFallDown) {
                // Mine out obsidian below them to make them swim and die horribly
                checkPos.add(pos.down());
            }

            if (canBeHitUp) {
                // Head for velo fails
                checkPos.add(pos.up(2));
            }

            // Face place
            checkPos.add(pos.up());

            for (Direction dir : Direction.Type.HORIZONTAL) {
                checkPos.add(pos.up().offset(dir));
            }

            // Feet place when half in bedrock/obsidian
            checkPos.add(pos);

            for (Direction dir : Direction.Type.HORIZONTAL) {
                checkPos.add(pos.offset(dir));
            }
        }
    }

    private double scoreNormalCityBlock(BlockPos pos) {
        double score = 0;

        BlockState block = mc.world.getBlockState(pos);

        // Feet / swim case
        if (pos.equals(targetPlayer.getBlockPos())) {
            BlockState headBlock = mc.world.getBlockState(pos.up());

            // If they're in 2-tall bedrock, mine out their feet
            if (headBlock.getBlock().equals(Blocks.OBSIDIAN)) {
                // Always prioritize swimming them
                score += 100;
            } else {
                // Ignore webs lol
                if (block.getBlock() == Blocks.COBWEB) {
                    return INVALID_SCORE;
                }

                // Mine out their feet-only phase
                score += 50;
            }
        } else {
            BlockState selfFeetBlock = mc.world.getBlockState(mc.player.getBlockPos());
            BlockState selfHeadBlock = mc.world.getBlockState(mc.player.getBlockPos().up());

            if (pos.equals(mc.player.getBlockPos())
                    && (selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)
                            || selfHeadBlock.getBlock().equals(Blocks.BEDROCK))) {
                return INVALID_SCORE;
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
                    if (pos.offset(dir).equals(pos)
                            || pos.offset(dir).equals(targetPlayer.getBlockPos())) {
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
        }


        // The closer the block is, the higher score it gets
        double d = targetPlayer.getPos().distanceTo(Vec3d.ofCenter(pos));
        score += 10 / d;

        return score;
    }

    private double scoreBedrockCityBlock(BlockPos pos) {
        double score = 0;
        BlockState block = mc.world.getBlockState(pos);

        // Prioritize the blocks above and below them to either velo fail or make them fall down
        if (pos.getY() == targetPlayer.getBlockY() + 2
                || pos.getY() == targetPlayer.getBlockY() - 1) {
            score += 10;
        }

        Box boundingBox = targetPlayer.getBoundingBox().shrink(0.01, 0.1, 0.01);
        double feetY = targetPlayer.getY();
        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        if (BlockPos.stream(feetBox).count() == 1) {
            boolean canMineFaceBlock = mc.world.getBlockState(targetPlayer.getBlockPos().up())
                    .getBlock() != Blocks.BEDROCK;

            if (canMineFaceBlock) {
                // Prioritize mining their face block first
                // And also only mine the self trap blocks when they can be face placed
                if (pos.equals(targetPlayer.getBlockPos().up())) {
                    score += 20;
                } else {
                    boolean isSelfTrapBlock = false;

                    for (Direction dir : Direction.HORIZONTAL) {
                        if (targetPlayer.getBlockPos().up().offset(dir).equals(pos)) {
                            isSelfTrapBlock = true;
                            break;
                        }
                    }

                    // Prioritize self trap blocks
                    if (isSelfTrapBlock) {
                        score += 7.5;
                    }
                }
            }
        }

        // The closer the block is, the higher score it gets
        // This also prioritizes feet blocks (below them and stuff) since getPos returns their feet
        // positions
        double d = targetPlayer.getPos().distanceTo(Vec3d.ofCenter(pos));
        score += 10 / d;

        return score;
    }

    private boolean isBlockInFeet(BlockPos blockPos) {
        Box boundingBox = targetPlayer.getBoundingBox().shrink(0.01, 0.1, 0.01);
        double feetY = targetPlayer.getY();
        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        for (BlockPos pos : BlockPos.iterate((int) Math.floor(feetBox.minX),
                (int) Math.floor(feetBox.minY), (int) Math.floor(feetBox.minZ),
                (int) Math.floor(feetBox.maxX), (int) Math.floor(feetBox.maxY),
                (int) Math.floor(feetBox.maxZ))) {

            if (blockPos.equals(pos)) {
                return true;
            }
        }

        return false;
    }

    public boolean isTargetedPos(BlockPos pos) {
        return (target1 != null && target1.blockPos.equals(pos))
                || (target2 != null && target2.blockPos.equals(pos));
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

    @Override
    public String getInfoString() {
        if (targetPlayer == null) {
            return null;
        }

        return String.format("%s", EntityUtils.getName(targetPlayer));
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
