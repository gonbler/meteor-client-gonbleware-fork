package meteordevelopment.meteorclient.systems.config;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.managers.SwapManager;
import net.minecraft.nbt.NbtCompound;

public class AntiCheatConfig extends System<AntiCheatConfig> {
    public final Settings settings = new Settings();

    private final SettingGroup sgRotations = settings.createGroup("Rotations");
    private final SettingGroup sgBlockPlacement = settings.createGroup("Block Placement");
    private final SettingGroup sgSwap = settings.createGroup("Swap");

    // Rotations
    public final Setting<Boolean> tickSync = sgRotations.add(new BoolSetting.Builder()
            .name("tick-sync").description("Lets rotations be rotated. Should always be on.")
            .defaultValue(true).build());

    public final Setting<Boolean> grimRotation = sgRotations.add(new BoolSetting.Builder()
            .name("grim-rotation").description("Sends a full movement packet every tick")
            .defaultValue(false).visible(() -> tickSync.get()).build());

    public final Setting<Boolean> grimSnapRotation =
            sgRotations.add(new BoolSetting.Builder().name("grim-snap-rotation")
                    .description("Sends a full movement packet when snapping rotation")
                    .defaultValue(true).build());

    public final Setting<Boolean> blockRotatePlace =
            sgBlockPlacement.add(new BoolSetting.Builder().name("block-rotate-place")
                    .description("Rotates to place blcks").defaultValue(false).build());

    public final Setting<Boolean> blockPlaceAirPlace =
            sgBlockPlacement.add(new BoolSetting.Builder().name("grim-air-place")
                    .description("Allows modules to air place blocks").defaultValue(true).build());

    public final Setting<Boolean> forceAirPlace =
            sgBlockPlacement.add(new BoolSetting.Builder().name("force-air-place")
                    .description("Only air-places blocks").defaultValue(true).build());

    public final Setting<Double> blockPlacePerBlockCooldown =
            sgBlockPlacement.add(new DoubleSetting.Builder().name("block-place-cooldown")
                    .description("Amount of time to retry placing blocks in the same place")
                    .defaultValue(0.05).min(0).sliderMax(0.3).build());

    public final Setting<Double> blocksPerSecondCap =
            sgBlockPlacement.add(new DoubleSetting.Builder().name("blocks-per-second")
                    .description("Maximum number of blocks that can be placed every second")
                    .defaultValue(20).min(0).sliderMax(30).build());

    public final Setting<Boolean> swapAntiScreenClose =
            sgSwap.add(new BoolSetting.Builder().name("anti-screen-close")
                    .description("Pauses certain methods of swapping when certain screens are open")
                    .defaultValue(true).build());

    public final Setting<SwapManager.SwapMode> swapMode =
            sgSwap.add(new EnumSetting.Builder<SwapManager.SwapMode>().name("item-swap-mode")
                    .description("How to swap to items").defaultValue(SwapManager.SwapMode.Auto)
                    .build());

    public AntiCheatConfig() {
        super("anti-cheat-config");
    }

    public static AntiCheatConfig get() {
        return Systems.get(AntiCheatConfig.class);
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();

        tag.putString("version", MeteorClient.VERSION.toString());
        tag.put("settings", settings.toTag());

        return tag;
    }

    @Override
    public AntiCheatConfig fromTag(NbtCompound tag) {
        if (tag.contains("settings"))
            settings.fromTag(tag.getCompound("settings"));

        return this;
    }
}
