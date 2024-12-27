package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

public class AntiDigDown extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General
    private final Setting<List<Block>> blocks = sgGeneral.add(
            new BlockListSetting.Builder().name("whitelist").description("Which blocks to use.")
                    .defaultValue(Blocks.OBSIDIAN, Blocks.NETHERITE_BLOCK).build());

    private final Setting<Integer> range =
            sgGeneral.add(new IntSetting.Builder().name("target-range")
                    .description("The range players can be targeted.").defaultValue(4).build());

    private final Setting<SortPriority> priority =
            sgGeneral.add(new EnumSetting.Builder<SortPriority>().name("target-priority")
                    .description("How to select the player to target.")
                    .defaultValue(SortPriority.LowestHealth).build());

    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
            .name("pause-eat").description("Pauses while eating.").defaultValue(true).build());

    // Render
    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder().name("render")
            .description("Renders an overlay where blocks will be placed.").defaultValue(true)
            .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color").description("The side color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232, 10)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color").description("The line color of the target block rendering.")
            .defaultValue(new SettingColor(197, 137, 232)).build());

    private PlayerEntity target;

    public AntiDigDown() {
        super(Categories.Combat, "anti-dig-down",
                "Places blocks directly below other players to stop them from digging down.");
    }

    @Override
    public void onActivate() {
        target = null;
    }

    @Override
    public void onDeactivate() {

    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (target == null || TargetUtils.isBadTarget(target, range.get())) {
            target = TargetUtils.getPlayerTarget(range.get(), priority.get());
            if (TargetUtils.isBadTarget(target, range.get()))
                return;
        }
    }

    private Item findUseItem() {
        FindItemResult result = InvUtils.findInHotbar(itemStack -> {
            for (Block blocks : blocks.get()) {
                if (blocks.asItem() == itemStack.getItem()) {
                    return true;
                }
            }

            return false;
        });

        if (!result.found()) {
            return null;
        }

        return mc.player.getInventory().getStack(result.slot()).getItem();
    }

    private BlockPos getBelowBlockPos() {
        return target.getBlockPos().down();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof BlockUpdateS2CPacket packet) {
            if (target == null) {
                return;
            }

            if (pauseEat.get() && mc.player.isUsingItem()) {
                return;
            }

            Item useItem = findUseItem();

            if (useItem == null) {
                return;
            }

            BlockPos belowPos = getBelowBlockPos();

            if (belowPos == null) {
                return;
            }

            SilentMine silentMine = Modules.get().get(SilentMine.class);

            // Don't target blocks we're targeting
            if ((silentMine.getDelayedDestroyBlockPos() != null
                    && belowPos.equals(silentMine.getDelayedDestroyBlockPos()))
                    || (silentMine.getRebreakBlockPos() != null
                            && belowPos.equals(silentMine.getRebreakBlockPos()))) {
                return;     
            }

            if (packet.getPos().equals(belowPos) && packet.getState().isAir()) {
                List<BlockPos> tempList1 = new ArrayList<>();
                tempList1.add(belowPos);

                if (!MeteorClient.BLOCK.beginPlacement(tempList1, useItem)) {
                    return;
                }

                tempList1.forEach(blockPos -> {
                    MeteorClient.BLOCK.placeBlock(blockPos);
                });

                MeteorClient.BLOCK.endPlacement();
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get())
            return;

        if (target == null) {
            return;
        }

        BlockPos pos = getBelowBlockPos();

        if (pos == null) {
            return;
        }

        event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    @Override
    public String getInfoString() {
        return EntityUtils.getName(target);
    }
}
