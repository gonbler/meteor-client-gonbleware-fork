/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.SoundEventListSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;

import java.util.List;

public class SoundBlocker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<SoundEvent>> sounds =
            sgGeneral.add(new SoundEventListSetting.Builder().name("sounds")
                    .description("Sounds to block.").build());

    private final Setting<Double> crystalHitVolume = sgGeneral.add(new DoubleSetting.Builder()
            .name("crystal-hit-volume").description("Sets the volume of hitting the crystals")
            .min(0).defaultValue(0.2).sliderMax(1).build());

    private final Setting<Double> crystalVolume = sgGeneral.add(new DoubleSetting.Builder()
            .name("crystal-volume").description("Sets the volume of the crystals").min(0)
            .defaultValue(0.2).sliderMax(1).build());

    public SoundBlocker() {
        super(Categories.Misc, "sound-blocker", "Cancels out selected sounds.");
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        for (SoundEvent sound : sounds.get()) {
            if (sound.getId().equals(event.sound.getId())) {
                event.cancel();
                break;
            }
        }
    }

    public boolean shouldBlock(SoundInstance soundInstance) {
        return isActive() && sounds.get()
                .contains(Setting.parseId(Registries.SOUND_EVENT, soundInstance.getId().getPath()));
    }

    public double getCrystalVolume() {
        return crystalVolume.get();
    }

    public double getCrystalHitVolume() {
        return crystalHitVolume.get();
    }
}
