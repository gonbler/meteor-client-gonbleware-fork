package meteordevelopment.meteorclient.systems.modules.combat;


import java.util.List;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class AutoMine extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<AntiSwimType> antiSwim =
            sgGeneral.add(new EnumSetting.Builder<AntiSwimType>().name("Anti-Swim")
                    .description(
                            "Starts mining your head block when the enemy starts mining your feet")
                    .defaultValue(AntiSwimType.OnMine).build());

    private SilentMine silentMine = null;

    private AbstractClientPlayerEntity targetPlayer = null;

    private BlockPos target1 = null;
    private BlockPos target2 = null;

    public AutoMine() {
        super(Categories.Combat, "auto-mine", "Automatically mines blocks");

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
    private void onTick(TickEvent event) {
        if (silentMine == null) {
            silentMine = (SilentMine) Modules.get().get(SilentMine.class);
        }
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (silentMine == null) {
            silentMine = (SilentMine) Modules.get().get(SilentMine.class);
        }

        if (event.packet instanceof BlockBreakingProgressS2CPacket packet) {
            if (antiSwim.get() == AntiSwimType.OnMine || antiSwim.get() == AntiSwimType.OnMineAndSwim) {
                if (!mc.player.getBlockPos().equals(packet.getPos())) {
                    return;
                }

                BlockState selfFeetBlock = mc.world.getBlockState(mc.player.getBlockPos());
                BlockState selfHeadBlock =
                        mc.world.getBlockState(mc.player.getBlockPos().offset(Direction.UP));

                if (BlockUtils.canBreak(mc.player.getBlockPos().offset(Direction.UP), selfHeadBlock)
                        && selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)) {
                    silentBreakBlock(mc.player.getBlockPos().offset(Direction.UP));
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

        if (antiSwim.get() == AntiSwimType.Always) {
            if (BlockUtils.canBreak(mc.player.getBlockPos().offset(Direction.UP), selfHeadBlock)
                    && selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)
                    && (!silentMine.miningSingleBreakBlock() || silentMine.getRebreakBlockPos() == null)) {
                silentBreakBlock(mc.player.getBlockPos().offset(Direction.UP));

                prioHead = true;
            }
        }

        if (antiSwim.get() == AntiSwimType.OnMineAndSwim && mc.player.isCrawling()) {
            if (BlockUtils.canBreak(mc.player.getBlockPos().offset(Direction.UP), selfHeadBlock)
                    && selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)
                    && (!silentMine.miningSingleBreakBlock() || silentMine.getRebreakBlockPos() == null)) {

                silentBreakBlock(mc.player.getBlockPos().offset(Direction.UP));

                prioHead = true;
            }
        }

        targetPlayer = findClosestTargetPlayer();

        if (targetPlayer == null) {
            return;
        }

        if (silentMine.miningSingleBreakBlock() && selfHeadBlock.getBlock().equals(Blocks.OBSIDIAN)
                && selfFeetBlock.isAir() && silentMine.getRebreakBlockPos() == mc.player
                        .getBlockPos().offset(Direction.UP)) {
            return;
        }

        if (!silentMine.miningSingleBreakBlock()) {
            findTargetBlocks();
            silentBreakBlock(target1);
            silentBreakBlock(target2);
        }
    }

    private void render(Render3DEvent event) {

    }

    private void findTargetBlocks() {
        target1 = findCityBlock(null);
        target2 = findCityBlock(target1);
    }

    private BlockPos findCityBlock(BlockPos exclude) {
        if (targetPlayer == null) {
            return null;
        }

        BlockPos bestPos = null;
        double bestScore = 0;
        List<BlockPos> checkPos = Direction.Type.HORIZONTAL.stream()
                .map(x -> targetPlayer.getBlockPos().offset(x)).collect(Collectors.toList());
        checkPos.add(targetPlayer.getBlockPos());


        for (BlockPos pos : checkPos) {
            if (pos.equals(exclude)) {
                continue;
            }

            BlockState block = mc.world.getBlockState(pos);

            if (block.isAir() && !(silentMine.canRebreak() && pos.equals(silentMine.getRebreakBlockPos()))) {
                continue;
            }

            double score = 0;

            if (!BlockUtils.canBreak(pos, block) && !(silentMine.canRebreak() && pos.equals(silentMine.getRebreakBlockPos()))) {
                continue;
            }

            // Feet / swim case
            if (pos.equals(targetPlayer.getBlockPos())) {
                BlockState headBlock = mc.world.getBlockState(pos.offset(Direction.UP));

                // If their in 2-tall bedrock, mine out their feet
                if (headBlock.getBlock().equals(Blocks.OBSIDIAN)) {
                    // Give lots of score to blocks that will make them swim
                    score += 100;
                } else {
                    // Mine out their feet-only phase
                    score += 6.0;
                }
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
                }

                if (silentMine.canRebreak() && pos.equals(silentMine.getRebreakBlockPos())) {
                    score += 40;
                }
            }
            

            boolean outOfRange = Utils.distance(mc.player.getX() - 0.5,
                    mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()),
                    mc.player.getZ() - 0.5, pos.getX(), pos.getY(),
                    pos.getZ()) > mc.player.getBlockInteractionRange() + 1;

            if (outOfRange) {
                continue;
            }

            double d = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));

            // The closer the block is, the higher score it gets
            score += 10 / d;

            if (score > bestScore) {
                bestScore = score;
                bestPos = pos;
            }
        }

        return bestPos;
    }

    public boolean isTargetedPos(BlockPos pos) {
        return (target1 != null && target1.equals(pos)) || (target2 != null && target2.equals(pos));
    }

    public boolean isTargetingAnything() {
        return target1 != null && target2 != null;
    }

    public BlockPos getPrimaryBreakBos() {
        if (target1 != null && target1 == silentMine.getRebreakBlockPos()) {
            return target1;
        }

        if (target2 != null && target2 == silentMine.getRebreakBlockPos()) {
            return target2;
        }

        return null;
    }

    public double getPrimaryBreakProgress() {
        if (target1 != null && target1 == silentMine.getRebreakBlockPos()) {
            return silentMine.getRebreakBlockProgress();
        }

        if (target2 != null && target2 == silentMine.getRebreakBlockPos()) {
            return silentMine.getRebreakBlockProgress();
        }

        return 0;
    }

    private AbstractClientPlayerEntity findClosestTargetPlayer() {
        return mc.world
                .getPlayers().stream().filter(player -> player != mc.player
                        && !Friends.get().isFriend(player) && player.distanceTo(mc.player) < 12)
                .sorted((p1, p2) -> {
                    return Float.compare(mc.player.distanceTo(p1), mc.player.distanceTo(p2));
                }).findFirst().orElse(null);
    }

    private void silentBreakBlock(BlockPos pos) {
        if (!silentMine.isActive()) {
            return;
        }

        if (pos == null) {
            return;
        }

        StartBreakingBlockEvent breakEvent = new StartBreakingBlockEvent();
        breakEvent.blockPos = pos;
        breakEvent.direction = Direction.UP;

        silentMine.onStartBreakingBlock(breakEvent);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null)
            return;

        update();
        render(event);
    }

    private enum AntiSwimType {
        None, Always, OnMine, OnMineAndSwim
    }
}
