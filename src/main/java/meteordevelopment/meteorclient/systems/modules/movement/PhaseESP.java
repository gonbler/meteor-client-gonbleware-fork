package meteordevelopment.meteorclient.systems.modules.movement;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class PhaseESP extends Module {
    private final SettingGroup sgRender = settings.createGroup("Render");

    public PhaseESP() {
        super(Categories.Render, "phase-esp", "Shows you where it's safe to phase.");
    }

    private final Setting<SettingColor> safeBedrockColor = sgRender.add(new ColorSetting.Builder()
            .name("Safe Bedrock Color").description("Bedrock that has a safe block below it")
            .defaultValue(new SettingColor(0, 255, 0, 40)).build());

    private final Setting<SettingColor> unsafeBedrockColor = sgRender.add(new ColorSetting.Builder()
            .name("Unsafe Bedrock Color").description("Bedrock that does not have a safe block below it")
            .defaultValue(new SettingColor(255, 0, 0, 60)).build());

    private final Setting<SettingColor> safeObsidianColor = sgRender.add(new ColorSetting.Builder()
            .name("Safe Obsidian Color").description("Obsidian that has a safe block below it")
            .defaultValue(new SettingColor(220, 255, 0, 60)).build());

    private final Setting<SettingColor> unsafesafeObsidianColor =
            sgRender.add(new ColorSetting.Builder().name("Unsafe Obsidian Color")
                    .description("Obsidian that does not have a safe block below it")
                    .defaultValue(new SettingColor(255, 0, 0, 60)).build());

    private final Pool<PhaseBlock> phaseBlockPool = new Pool<>(PhaseBlock::new);
    private final List<PhaseBlock> phaseBlocks = new ArrayList<>();

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        for (PhaseBlock hole : phaseBlocks)
            phaseBlockPool.free(hole);

        phaseBlocks.clear();

        BlockPos playerPos = mc.player.getBlockPos();

        checkBlock(playerPos.offset(Direction.NORTH));
        checkBlock(playerPos.offset(Direction.EAST));
        checkBlock(playerPos.offset(Direction.SOUTH));
        checkBlock(playerPos.offset(Direction.WEST));

        checkBlock(playerPos.offset(Direction.NORTH).offset(Direction.EAST));
        checkBlock(playerPos.offset(Direction.EAST).offset(Direction.SOUTH));
        checkBlock(playerPos.offset(Direction.SOUTH).offset(Direction.WEST));
        checkBlock(playerPos.offset(Direction.WEST).offset(Direction.NORTH));
    }

    private void checkBlock(BlockPos pos) {
        BlockState block = mc.world.getBlockState(pos);
        BlockState downBlock = mc.world.getBlockState(pos.offset(Direction.DOWN));

        if (downBlock == null || block == null) {
            return;
        }

        boolean obsidian = block.getBlock().equals(Blocks.OBSIDIAN);
        boolean bedrock = block.getBlock().equals(Blocks.BEDROCK);

        boolean obsidianDown = downBlock.getBlock().equals(Blocks.OBSIDIAN);
        boolean bedrockDown = downBlock.getBlock().equals(Blocks.BEDROCK);

        if (obsidian) {
            if (obsidianDown || bedrockDown) {
                phaseBlocks.add(phaseBlockPool.get().set(pos, PhaseBlock.Type.SafeObsidian));
            } else {
                phaseBlocks.add(phaseBlockPool.get().set(pos, PhaseBlock.Type.UnsafeObsidian));
            }
        } else if (bedrock) {
            if (bedrockDown) {
                phaseBlocks.add(phaseBlockPool.get().set(pos, PhaseBlock.Type.SafeBedrock));
            } else {
                phaseBlocks.add(phaseBlockPool.get().set(pos, PhaseBlock.Type.UnsafeBedrock));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onRender(Render3DEvent event) {
        for (PhaseBlock phaseBlock : phaseBlocks) {
            phaseBlock.render(event.renderer);
        }
    }

    private static class PhaseBlock {
        public BlockPos.Mutable blockPos = new BlockPos.Mutable();
        public Type type;

        private PhaseESP parent;

        public PhaseBlock() {
            parent = Modules.get().get(PhaseESP.class);
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
                case SafeBedrock -> parent.safeBedrockColor.get();
                case UnsafeBedrock -> parent.unsafeBedrockColor.get();
                case SafeObsidian -> parent.safeObsidianColor.get();
                case UnsafeObsidian -> parent.unsafesafeObsidianColor.get();
            };

            renderer.quadHorizontal(x1, y1, z1, x2, z2, color);
        }

        public enum Type {
            SafeBedrock, SafeObsidian, UnsafeBedrock, UnsafeObsidian,
        }
    }
}
