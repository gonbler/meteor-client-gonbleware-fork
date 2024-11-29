package meteordevelopment.meteorclient.systems.config;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.renderer.text.FontFace;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class AntiCheatConfig extends System<AntiCheatConfig> {
    public final Settings settings = new Settings();

    private final SettingGroup sgRotations = settings.createGroup("Rotations");

    // Visual

    public final Setting<Boolean> tickSync = sgRotations.add(new BoolSetting.Builder()
        .name("tick-sync")
        .description("Sends a rotation packet every tick if needed")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> grimRotation = sgRotations.add(new BoolSetting.Builder()
        .name("grim-rotation")
        .description("Sends a rotation packet every tick if needed")
        .defaultValue(true)
        .visible(() -> tickSync.get())
        .build()
    );

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
        if (tag.contains("settings")) settings.fromTag(tag.getCompound("settings"));

        return this;
    }
}
