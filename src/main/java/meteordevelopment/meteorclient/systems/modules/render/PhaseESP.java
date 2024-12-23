package meteordevelopment.meteorclient.systems.modules.render;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.RaycastContext;

public class PhaseESP extends Module {
    private final SettingGroup sgRender = settings.createGroup("Render");

    public PhaseESP() {
        super(Categories.Render, "phase-esp", "Shows you where it's safe to phase.");
    }

    private final Setting<SettingColor> safeBedrockColor = sgRender.add(new ColorSetting.Builder()
            .name("safe-bedrock-color").description("Bedrock that has a safe block below it")
            .defaultValue(new SettingColor(150, 0, 255, 50)).build());

    private final Setting<SettingColor> unsafeBedrockColor =
            sgRender.add(new ColorSetting.Builder().name("unsafe-bedrock-color")
                    .description("Bedrock that does not have a safe block below it")
                    .defaultValue(new SettingColor(255, 0, 0, 70)).build());

    private final Setting<SettingColor> safeOpenHeadBedrockColor =
            sgRender.add(new ColorSetting.Builder().name("safe-open-head-bedrock-color")
                    .description("Bedrock that has a safe block below it and an open head")
                    .defaultValue(new SettingColor(135, 160, 20, 50)).build());


    private final Setting<SettingColor> safeObsidianColor = sgRender.add(new ColorSetting.Builder()
            .name("safe-obsidian-color").description("Obsidian that has a safe block below it")
            .defaultValue(new SettingColor(140, 0, 255, 10)).build());

    private final Setting<SettingColor> unsafesafeObsidianColor =
            sgRender.add(new ColorSetting.Builder().name("unsafe-obsidian-color")
                    .description("Obsidian that does not have a safe block below it")
                    .defaultValue(new SettingColor(255, 0, 0, 30)).build());

    private final Setting<SettingColor> openHeadColor = sgRender.add(new ColorSetting.Builder()
            .name("open-head-color").description("A block where the head is open")
            .defaultValue(new SettingColor(255, 0, 240, 30)).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").description("The line color of the rendering.")
            .defaultValue(new SettingColor(255, 255, 255, 20))
            .visible(() -> shapeMode.get().lines()).build());

    private final Pool<PhaseBlock> phaseBlockPool = new Pool<>(PhaseBlock::new);
    private final List<PhaseBlock> phaseBlocks = new ArrayList<>();

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (PhaseBlock hole : phaseBlocks)
            phaseBlockPool.free(hole);

        phaseBlocks.clear();

        BlockPos playerPos = mc.player.getBlockPos();

        Box boundingBox = mc.player.getBoundingBox().expand(0.999, 0.0, 0.999);
        double feetY = mc.player.getY();
        Box feetBox = new Box(boundingBox.minX, feetY, boundingBox.minZ, boundingBox.maxX,
                feetY + 0.1, boundingBox.maxZ);

        boolean isAccorssMultipleBlocks = false;

        if ((int) Math.floor(feetBox.maxX) - (int) Math.floor(feetBox.minX) >= 1
                || (int) Math.floor(feetBox.maxZ) - (int) Math.floor(feetBox.minZ) >= 1) {
            isAccorssMultipleBlocks = true;
        }

        for (int x = (int) Math.floor(feetBox.minX); x <= (int) Math.floor(feetBox.maxX); x++) {
            for (int z = (int) Math.floor(feetBox.minZ); z <= (int) Math.floor(feetBox.maxZ); z++) {
                BlockPos blockPos = new BlockPos(x, playerPos.getY(), z);

                if (!isAccorssMultipleBlocks && playerPos.getX() == x && playerPos.getZ() == z) {
                    continue;
                }

                BlockHitResult result =
                        mc.world.raycast(new RaycastContext(mc.player.getPos().add(0, 0.05, 0),
                                blockPos.toBottomCenterPos().add(0, 0.05, 0),
                                RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE,
                                mc.player));

                if (result == null || result.getType() == HitResult.Type.BLOCK) {
                    checkBlock(blockPos);
                }
            }
        }
    }

    private void checkBlock(BlockPos pos) {
        BlockState block = mc.world.getBlockState(pos);
        BlockState downBlock = mc.world.getBlockState(pos.offset(Direction.DOWN));
        BlockState upBlock = mc.world.getBlockState(pos.offset(Direction.UP));

        if (downBlock == null || block == null) {
            return;
        }

        boolean obsidian = block.isOf(Blocks.OBSIDIAN) || block.isOf(Blocks.CRYING_OBSIDIAN);
        boolean bedrock = block.isOf(Blocks.BEDROCK);

        boolean obsidianDown =
                downBlock.isOf(Blocks.OBSIDIAN) || downBlock.isOf(Blocks.CRYING_OBSIDIAN);
        boolean bedrockDown = downBlock.isOf(Blocks.BEDROCK);

        boolean airUp = upBlock.isAir();
        boolean bedrockUp = upBlock.isOf(Blocks.BEDROCK);
        boolean obsidianUp = upBlock.isOf(Blocks.OBSIDIAN) || upBlock.isOf(Blocks.CRYING_OBSIDIAN);

        if (bedrock) {
            if (bedrockDown) {
                if (bedrockUp) {
                    phaseBlocks.add(phaseBlockPool.get().set(pos, PhaseBlock.Type.SafeBedrock));
                } else {
                    phaseBlocks.add(
                            phaseBlockPool.get().set(pos, PhaseBlock.Type.SafeBedrockOpenHead));
                }
            } else {
                phaseBlocks.add(phaseBlockPool.get().set(pos, PhaseBlock.Type.UnsafeBedrock));
            }
        } else {
            if (obsidian) {
                if (obsidianDown || bedrockDown) {
                    if (airUp) {
                        phaseBlocks.add(phaseBlockPool.get().set(pos, PhaseBlock.Type.OpenHead));
                    } else {
                        phaseBlocks
                                .add(phaseBlockPool.get().set(pos, PhaseBlock.Type.SafeObsidian));
                    }
                } else {
                    phaseBlocks.add(phaseBlockPool.get().set(pos, PhaseBlock.Type.UnsafeObsidian));
                }
            } else if (obsidianUp) {
                phaseBlocks.add(phaseBlockPool.get().set(pos, PhaseBlock.Type.UnsafeObsidian));
            } else if (airUp) {
                if (obsidianDown || bedrockDown) {
                    phaseBlocks.add(phaseBlockPool.get().set(pos, PhaseBlock.Type.OpenHead));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        for (PhaseBlock phaseBlock : phaseBlocks) {
            phaseBlock.render(event.renderer);
        }
    }

    private class PhaseBlock {
        public BlockPos.Mutable blockPos = new BlockPos.Mutable();
        public Type type;

        public PhaseBlock() {

        }

        public PhaseBlock set(BlockPos blockPos, Type type) {
            this.blockPos.set(blockPos);
            this.type = type;

            return this;
        }

        public void render(Renderer3D renderer) {
            int x1 = blockPos.getX();
            int y1 = blockPos.getY();
            int z1 = blockPos.getZ();

            int x2 = blockPos.getX() + 1;
            int z2 = blockPos.getZ() + 1;

            Color color = switch (this.type) {
                case SafeBedrock -> safeBedrockColor.get();
                case UnsafeBedrock -> unsafeBedrockColor.get();
                case SafeObsidian -> safeObsidianColor.get();
                case UnsafeObsidian -> unsafesafeObsidianColor.get();
                case SafeBedrockOpenHead -> safeOpenHeadBedrockColor.get();
                case OpenHead -> openHeadColor.get();
            };

            renderer.sideHorizontal(x1, y1, z1, x2, z2, color, lineColor.get(), shapeMode.get());
        }

        public enum Type {
            SafeBedrock, SafeObsidian, UnsafeBedrock, UnsafeObsidian, SafeBedrockOpenHead, OpenHead
        }
    }
}
