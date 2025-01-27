package meteordevelopment.meteorclient.systems.modules.combat;


import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import org.joml.Vector3d;
import meteordevelopment.meteorclient.events.meteor.SilentMineFinishedEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.autocrystal.AutoCrystal;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
import meteordevelopment.meteorclient.systems.modules.render.BreakIndicators;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
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
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final double INVALID_SCORE = -1000;

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

    private final Setting<AntiSurroundMode> antiSurroundMode =
            sgGeneral.add(new EnumSetting.Builder<AntiSurroundMode>().name("anti-surround-mode")
                    .description("Places crystals in places to prevent surround")
                    .defaultValue(AntiSurroundMode.Auto).build());

    private final Setting<Boolean> antiSurroundInnerSnap = sgGeneral.add(new BoolSetting.Builder()
            .name("anti-surround-inner-snap")
            .description("Instantly snaps the camera when it needs to for inner place")
            .defaultValue(true).visible(() -> antiSurroundMode.get() == AntiSurroundMode.Auto
                    || antiSurroundMode.get() == AntiSurroundMode.Inner)
            .build());

    private final Setting<Boolean> renderDebugScores =
            sgRender.add(new BoolSetting.Builder().name("render-debug-scores")
                    .description("Renders scores and their blocks.").defaultValue(false).build());


    private SilentMine silentMine = null;

    private PlayerEntity targetPlayer = null;

    private CityBlock target1 = null;
    private CityBlock target2 = null;
    private BlockPos ignorePos = null;

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
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (silentMine == null) {
            silentMine = (SilentMine) Modules.get().get(SilentMine.class);
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

                if (event.getBlockPos().equals(playerSurroundBlock)) {
                    BlockPos outerPos = targetPlayer.getBlockPos().offset(dir, 2);

                    if (mc.world.isAir(outerPos)) {
                        Modules.get().get(AutoCrystal.class).preplaceCrystal(outerPos, false);
                    } else {
                        for (Direction dir2 : Direction.HORIZONTAL) {
                            BlockPos outerAroundPos = playerSurroundBlock.offset(dir2);

                            if (mc.world.isAir(outerAroundPos)) {
                                Modules.get().get(AutoCrystal.class).preplaceCrystal(outerAroundPos,
                                        false);
                            }
                        }
                    }
                }
            }
        }

        if (mode == AntiSurroundMode.Auto || mode == AntiSurroundMode.Inner) {
            for (Direction dir : Direction.HORIZONTAL) {
                BlockPos playerSurroundBlock = targetPlayer.getBlockPos().offset(dir);

                if (playerSurroundBlock.equals(event.getBlockPos())) {
                    Modules.get().get(AutoCrystal.class).preplaceCrystal(playerSurroundBlock, antiSurroundInnerSnap.get());
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



    private void findTargetBlocks() {
        target1 = findCityBlock(null);
        ignorePos = target1 != null ? target1.blockPos : null;

        target2 = findCityBlock(target1 != null ? target1.blockPos : null);
    }

    private CityBlock findCityBlock(BlockPos exclude) {
        if (targetPlayer == null) {
            return null;
        }

        boolean set = false;
        CityBlock bestBlock = new CityBlock();

        Set<CheckPos> checkPos = new HashSet<>();

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

        for (CheckPos pos : checkPos) {
            BlockPos blockPos = pos.blockPos;

            if (blockPos.equals(exclude)) {
                continue;
            }

            BlockState block = mc.world.getBlockState(blockPos);
            boolean isPosGoodRebreak = false;

            if (silentMine.canRebreakRebreakBlock()
                    && blockPos.equals(silentMine.getRebreakBlockPos())) {
                if (inBedrock) {
                    boolean isSelfTrapBlock = false;

                    for (Direction dir : Direction.HORIZONTAL) {
                        if (targetPlayer.getBlockPos().up().offset(dir).equals(blockPos)) {
                            isSelfTrapBlock = true;
                            break;
                        }
                    }


                    boolean canFacePlace =
                            mc.world.getBlockState(targetPlayer.getBlockPos().up()).isAir();

                    // It's a good rebreak if it's a self trap block for their head or the block
                    // above their head (no velo meta)
                    isPosGoodRebreak = BlockPos.stream(feetBox).count() == 1
                            && (blockPos.equals(targetPlayer.getBlockPos().up(2))
                                    || (isSelfTrapBlock && canFacePlace));
                } else {
                    // It's a good rebreak if it's their surround block
                    isPosGoodRebreak = !blockPos.equals(targetPlayer.getBlockPos())
                            && !isBlockInFeet(blockPos)
                            && Arrays.stream(Direction.HORIZONTAL).anyMatch(
                                    dir -> targetPlayer.getBlockPos().offset(dir).equals(blockPos)
                                            && isCrystalBlock(
                                                    targetPlayer.getBlockPos().offset(dir).down()));
                }
            }

            if (block.isAir() && !isPosGoodRebreak) {
                continue;
            }

            boolean isFeetBlock = isBlockInFeet(blockPos);

            if (!BlockUtils.canBreak(blockPos, block) && !isPosGoodRebreak) {
                continue;
            }


            if (!silentMine.inBreakRange(blockPos)) {
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
                bestBlock.blockPos = blockPos;
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
    private void addNormalCaseCheckPositions(Set<CheckPos> checkPos) {
        Box boundingBox = targetPlayer.getBoundingBox().shrink(0.01, 0.1, 0.01);
        double feetY = targetPlayer.getY();
        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        for (BlockPos pos : BlockUtils.iterate(feetBox)) {
            checkPos.add(new CheckPos(pos, CheckPosType.Feet));
        }

        for (BlockPos pos : BlockUtils.iterate(feetBox)) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                checkPos.add(new CheckPos(pos.offset(dir), CheckPosType.Surround));
            }
        }

        checkPos.add(new CheckPos(targetPlayer.getBlockPos(), CheckPosType.Feet));

        boolean inMultipleBlocks = BlockPos.stream(feetBox).count() > 1;

        if (!inMultipleBlocks) {
            for (Direction dir : Direction.Type.HORIZONTAL) {
                checkPos.add(new CheckPos(targetPlayer.getBlockPos().offset(dir, 2),
                        CheckPosType.Extend));
            }
        }
    }

    private void addBedrockCaseCheckPositions(Set<CheckPos> checkPos) {
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

        for (BlockPos pos : BlockUtils.iterate(feetBox)) {
            if (canFallDown) {
                // Mine out obsidian below them to make them swim and die horribly
                checkPos.add(new CheckPos(pos.down(), CheckPosType.Below));
            }

            if (canBeHitUp) {
                // Head for velo fails
                checkPos.add(new CheckPos(pos.up(2), CheckPosType.Head));
            }

            // Face place
            checkPos.add(new CheckPos(pos.up(), CheckPosType.FacePlace));

            for (Direction dir : Direction.Type.HORIZONTAL) {
                checkPos.add(new CheckPos(pos.up().offset(dir), CheckPosType.FacePlace));
            }

            // Feet place when half in bedrock/obsidian
            checkPos.add(new CheckPos(pos, CheckPosType.Surround));

            for (Direction dir : Direction.Type.HORIZONTAL) {
                checkPos.add(new CheckPos(pos.offset(dir), CheckPosType.Surround));
            }
        }
    }

    private double scoreNormalCityBlock(CheckPos pos) {
        BlockPos blockPos = pos.blockPos;

        double score = 0;

        BlockState block = mc.world.getBlockState(blockPos);

        // Feet / swim case
        if (blockPos.equals(targetPlayer.getBlockPos())) {
            BlockState headBlock = mc.world.getBlockState(blockPos.up());

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

            // We don't want to mine our selves out
            if (blockPos.equals(mc.player.getBlockPos())
                    && (selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)
                            || selfHeadBlock.getBlock().equals(Blocks.BEDROCK))) {
                return INVALID_SCORE;
            }

            if (pos.type == CheckPosType.Surround) {
                score += 3;

                boolean isPosAntiSurround = false;
                for (Direction dir : Direction.Type.HORIZONTAL) {
                    // Only add anti surround when it's this position
                    if (!targetPlayer.getBlockPos().offset(dir).equals(blockPos)) {
                        continue;
                    }

                    BlockPos antiSurroundBlockPos = targetPlayer.getBlockPos().offset(dir, 2);
                    if (getBlockStateIgnore(antiSurroundBlockPos).isAir()
                            && isCrystalBlock(antiSurroundBlockPos.down())) {
                        isPosAntiSurround = true;
                        break;
                    }
                }

                if (isPosAntiSurround) {
                    score += 25;
                }
            }

            if (pos.type == CheckPosType.Extend) {
                score += 20;
            }
        }

        // The closer the block is, the higher score it gets
        double d = targetPlayer.getPos().distanceTo(Vec3d.ofCenter(blockPos));
        score += 10 / d;

        return score;
    }

    private double scoreBedrockCityBlock(CheckPos pos) {
        BlockPos blockPos = pos.blockPos;

        double score = 0;
        BlockState block = mc.world.getBlockState(blockPos);

        // Prioritize the blocks above and below them to either velo fail or make them fall down
        if (blockPos.getY() == targetPlayer.getBlockY() + 2
                || blockPos.getY() == targetPlayer.getBlockY() - 1) {
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
                if (blockPos.equals(targetPlayer.getBlockPos().up())) {
                    score += 20;
                } else {
                    boolean isSelfTrapBlock = false;

                    for (Direction dir : Direction.HORIZONTAL) {
                        if (targetPlayer.getBlockPos().up().offset(dir).equals(blockPos)) {
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
        double d = targetPlayer.getPos().distanceTo(Vec3d.ofCenter(blockPos));
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

    private boolean isCrystalBlock(BlockPos blockPos) {
        BlockState blockState = mc.world.getBlockState(blockPos);

        return blockState.isOf(Blocks.OBSIDIAN) || blockState.isOf(Blocks.BEDROCK);
    }

    public boolean isTargetedPos(BlockPos blockPos) {
        return (target1 != null && target1.blockPos.equals(blockPos))
                || (target2 != null && target2.blockPos.equals(blockPos));
    }

    private BlockState getBlockStateIgnore(BlockPos blockPos) {
        if (blockPos == null) {
            return null;
        }

        if (blockPos.equals(ignorePos)) {
            return Blocks.AIR.getDefaultState();
        }

        return mc.world.getBlockState(blockPos);
    }

    public boolean isTargetingAnything() {
        return target1 != null && target2 != null;
    }

    private void onRender3d(Render3DEvent event) {
        if (targetPlayer == null) {
            return;
        }

        if (renderDebugScores.get()) {
            double bestScore = 0;

            Set<CheckPos> checkPos = new HashSet<>();

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

            for (CheckPos pos : checkPos) {
                BlockPos blockPos = pos.blockPos;

                BlockState block = mc.world.getBlockState(blockPos);
                boolean isPosGoodRebreak = false;

                if (silentMine.canRebreakRebreakBlock()
                        && blockPos.equals(silentMine.getRebreakBlockPos())) {
                    if (inBedrock) {
                        boolean isSelfTrapBlock = false;

                        for (Direction dir : Direction.HORIZONTAL) {
                            if (targetPlayer.getBlockPos().up().offset(dir).equals(blockPos)) {
                                isSelfTrapBlock = true;
                                break;
                            }
                        }


                        boolean canFacePlace =
                                mc.world.getBlockState(targetPlayer.getBlockPos().up()).isAir();

                        // It's a good rebreak if it's a self trap block for their head or the block
                        // above their head (no velo meta)
                        isPosGoodRebreak = BlockPos.stream(feetBox).count() == 1
                                && (blockPos.equals(targetPlayer.getBlockPos().up(2))
                                        || (isSelfTrapBlock && canFacePlace));
                    } else {
                        // It's a good rebreak if it's their surround block
                        isPosGoodRebreak = !blockPos.equals(targetPlayer.getBlockPos())
                                && !isBlockInFeet(blockPos)
                                && Arrays.stream(Direction.HORIZONTAL).anyMatch(dir -> targetPlayer
                                        .getBlockPos().offset(dir).equals(blockPos)
                                        && isCrystalBlock(
                                                targetPlayer.getBlockPos().offset(dir).down()));
                    }
                }

                if (block.isAir() && !isPosGoodRebreak) {
                    continue;
                }

                if (!BlockUtils.canBreak(blockPos, block) && !isPosGoodRebreak) {
                    continue;
                }


                if (!silentMine.inBreakRange(blockPos)) {
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


                if (score > bestScore) {
                    bestScore = score;
                }
            }

            Color color = Color.RED;

            for (CheckPos pos : checkPos) {
                BlockPos blockPos = pos.blockPos;

                BlockState block = mc.world.getBlockState(blockPos);
                boolean isPosGoodRebreak = false;

                if (silentMine.canRebreakRebreakBlock()
                        && blockPos.equals(silentMine.getRebreakBlockPos())) {
                    if (inBedrock) {
                        boolean isSelfTrapBlock = false;

                        for (Direction dir : Direction.HORIZONTAL) {
                            if (targetPlayer.getBlockPos().up().offset(dir).equals(blockPos)) {
                                isSelfTrapBlock = true;
                                break;
                            }
                        }


                        boolean canFacePlace =
                                mc.world.getBlockState(targetPlayer.getBlockPos().up()).isAir();

                        // It's a good rebreak if it's a self trap block for their head or the block
                        // above their head (no velo meta)
                        isPosGoodRebreak = BlockPos.stream(feetBox).count() == 1
                                && (blockPos.equals(targetPlayer.getBlockPos().up(2))
                                        || (isSelfTrapBlock && canFacePlace));
                    } else {
                        // It's a good rebreak if it's their surround block
                        isPosGoodRebreak = !blockPos.equals(targetPlayer.getBlockPos())
                                && !isBlockInFeet(blockPos)
                                && Arrays.stream(Direction.HORIZONTAL).anyMatch(dir -> targetPlayer
                                        .getBlockPos().offset(dir).equals(blockPos)
                                        && isCrystalBlock(
                                                targetPlayer.getBlockPos().offset(dir).down()));
                    }
                }

                if (block.isAir() && !isPosGoodRebreak) {
                    continue;
                }

                if (!BlockUtils.canBreak(blockPos, block) && !isPosGoodRebreak) {
                    continue;
                }


                if (!silentMine.inBreakRange(blockPos)) {
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

                double alpha = (score / bestScore) / 4;

                event.renderer.box(blockPos, color.a((int) (255.0 * alpha)), Color.WHITE,
                        ShapeMode.Sides, 0);
            }
        }
    }

    @EventHandler
    private void onRender2d(Render2DEvent event) {
        if (targetPlayer == null) {
            return;
        }

        if (renderDebugScores.get()) {
            Vector3d vec3 = new Vector3d();

            Set<CheckPos> checkPos = new HashSet<>();

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

            for (CheckPos pos : checkPos) {
                BlockPos blockPos = pos.blockPos;

                BlockState block = mc.world.getBlockState(blockPos);
                boolean isPosGoodRebreak = false;

                if (silentMine.canRebreakRebreakBlock()
                        && blockPos.equals(silentMine.getRebreakBlockPos())) {
                    if (inBedrock) {
                        boolean isSelfTrapBlock = false;

                        for (Direction dir : Direction.HORIZONTAL) {
                            if (targetPlayer.getBlockPos().up().offset(dir).equals(blockPos)) {
                                isSelfTrapBlock = true;
                                break;
                            }
                        }


                        boolean canFacePlace =
                                mc.world.getBlockState(targetPlayer.getBlockPos().up()).isAir();

                        // It's a good rebreak if it's a self trap block for their head or the block
                        // above their head (no velo meta)
                        isPosGoodRebreak = BlockPos.stream(feetBox).count() == 1
                                && (blockPos.equals(targetPlayer.getBlockPos().up(2))
                                        || (isSelfTrapBlock && canFacePlace));
                    } else {
                        // It's a good rebreak if it's their surround block
                        isPosGoodRebreak = !blockPos.equals(targetPlayer.getBlockPos())
                                && !isBlockInFeet(blockPos)
                                && Arrays.stream(Direction.HORIZONTAL).anyMatch(dir -> targetPlayer
                                        .getBlockPos().offset(dir).equals(blockPos)
                                        && isCrystalBlock(
                                                targetPlayer.getBlockPos().offset(dir).down()));
                    }
                }

                if (block.isAir() && !isPosGoodRebreak) {
                    continue;
                }

                if (!BlockUtils.canBreak(blockPos, block) && !isPosGoodRebreak) {
                    continue;
                }


                if (!silentMine.inBreakRange(blockPos)) {
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

                vec3.set(blockPos.toCenterPos().x, blockPos.toCenterPos().y,
                        blockPos.toCenterPos().z);

                if (NametagUtils.to2D(vec3, 1.25)) {
                    NametagUtils.begin(vec3);
                    TextRenderer.get().begin(1, false, true);

                    String text = String.format("%.1f", score);
                    double w = TextRenderer.get().getWidth(text) / 2;
                    TextRenderer.get().render(text, -w, 0, Color.WHITE, true);

                    TextRenderer.get().end();
                    NametagUtils.end();
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null)
            return;

        update();
        onRender3d(event);
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

    private class CheckPos {
        public final BlockPos blockPos;

        public final CheckPosType type;

        public CheckPos(BlockPos blockPos, CheckPosType type) {
            this.blockPos = blockPos;
            this.type = type;
        }

        @Override
        public int hashCode() {
            return blockPos.hashCode();
        }
    }

    public enum CheckPosType {
        Feet, Surround, Extend, FacePlace, Head, Below
    }

    private enum AntiSwimMode {
        None, Always, OnMine, OnMineAndSwim
    }

    private enum AntiSurroundMode {
        None, Inner, Outer, Auto
    }
}
