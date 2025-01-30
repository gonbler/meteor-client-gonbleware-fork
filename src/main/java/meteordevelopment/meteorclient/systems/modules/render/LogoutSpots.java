/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.systems.modules.render;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.PlayerJoinLeaveEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LogoutSpots extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General
    private final Setting<Boolean> notifyOnRejoin = sgGeneral.add(new BoolSetting.Builder().name("notify-on-rejoin")
            .description("Notifies you when a player rejoins.").defaultValue(true).build());

    private final Setting<Boolean> notifyOnRejoinShowCoords = sgGeneral
            .add(new BoolSetting.Builder().name("notify-on-show-coords")
                    .description("Shows the coords of the player when they rejoin.")
                    .defaultValue(true).visible(() -> notifyOnRejoin.get()).build());

    private final Setting<Boolean> notifyOnRejoinLimitDistance = sgGeneral
            .add(new BoolSetting.Builder().name("notify-on-rejoin-limit-distance")
                    .description(
                            "Whether or not to limit distances for rejoin coord notifications.")
                    .defaultValue(true)
                    .visible(() -> notifyOnRejoin.get() && notifyOnRejoinShowCoords.get()).build());

    private final Setting<Double> notifyOnRejoinDistance = sgGeneral.add(new DoubleSetting.Builder()
            .name("notify-on-rejoin-distance").description("The limit to show coords on rejoin.")
            .defaultValue(5000).min(0).visible(() -> notifyOnRejoin.get()
                    && notifyOnRejoinShowCoords.get() && notifyOnRejoinLimitDistance.get())
            .build());

    // Render

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode").description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> sideColor = sgRender
            .add(new ColorSetting.Builder().name("side-color").description("The side color.")
                    .defaultValue(new SettingColor(255, 0, 255, 55)).build());

    private final Setting<SettingColor> lineColor = sgRender
            .add(new ColorSetting.Builder().name("line-color").description("The line color.")
                    .defaultValue(new SettingColor(255, 0, 255)).build());

    private final Setting<SettingColor> nameColor = sgRender
            .add(new ColorSetting.Builder().name("name-color").description("The name color.")
                    .defaultValue(new SettingColor(255, 255, 255)).build());

    private final Setting<SettingColor> timeColor = sgRender
            .add(new ColorSetting.Builder().name("time-color").description("The time color.")
                    .defaultValue(new SettingColor(255, 255, 255)).build());

    private final Setting<SettingColor> totemPopsColor = sgRender.add(new ColorSetting.Builder()
            .name("totem-pop-color")
            .description("The color of the totem pops.")
            .defaultValue(new SettingColor(225, 120, 20))
            .build());

    private final Setting<SettingColor> textBackgroundColor = sgRender
            .add(new ColorSetting.Builder().name("text-background-color")
                    .description("The text background color.")
                    .defaultValue(new SettingColor(0, 0, 0, 75)).build());

    private final Setting<Double> nametageScale = sgRender.add(new DoubleSetting.Builder()
            .name("text-scale").description("The scale for text.")
            .defaultValue(1.0).min(0.1).sliderMax(2.0)
            .build());

    private final Map<UUID, GhostPlayer> loggedPlayers = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerTimedEntity> lastPlayers = new ConcurrentHashMap<>();

    private Dimension lastDimension;

    public LogoutSpots() {
        super(Categories.Render, "logout-spots",
                "Displays a box where another player has logged out at.");
        lineColor.onChanged();
    }

    @Override
    public void onActivate() {

        lastDimension = PlayerUtils.getDimension();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTick(TickEvent.Post event) {
        Dimension dimension = PlayerUtils.getDimension();

        if (dimension != lastDimension)
            loggedPlayers.clear();

        lastDimension = dimension;

        long currentTime = System.currentTimeMillis();

        lastPlayers.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue().lastSeenTime > 150) {
                return true;
            }

            return false;
        });

        for (PlayerEntity player : mc.world.getPlayers()) {
            PlayerTimedEntity timedEntity = new PlayerTimedEntity();
            timedEntity.lastSeenTime = currentTime;
            timedEntity.playerEntity = player;

            lastPlayers.put(player.getUuid(), timedEntity);
        }

        loggedPlayers.entrySet().removeIf(entry -> {
            // If the playerList contains the UUID, remove them from the list, because
            // they've rejoined
            if (mc.getNetworkHandler().getPlayerListEntry(entry.getKey()) != null) {
                return true;
            }

            return false;
        });
    }

    @EventHandler
    private void onPlayerJoin(PlayerJoinLeaveEvent.Join event) {
        if (event.getEntry().profileId() == null)
            return;

        if (!loggedPlayers.containsKey(event.getEntry().profileId())) {
            return;
        }

        GhostPlayer ghost = loggedPlayers.remove(event.getEntry().profileId());

        if (notifyOnRejoin.get()) {
            boolean showCoords = notifyOnRejoinShowCoords.get();

            if (notifyOnRejoinLimitDistance.get() && notifyOnRejoinDistance
                    .get() < ghost.pos.distanceTo(Vec3d.ZERO)) {
                showCoords = false;
            }

            if (showCoords) {
                info("(highlight)%s(default) rejoined at %d, %d, %d (highlight)(%.1fm away)(default).",
                        ghost.name, (int) Math.floor(ghost.pos.x), (int) Math.floor(ghost.pos.y),
                        (int) Math.floor(ghost.pos.z), mc.player.getPos()
                                .distanceTo(ghost.pos));
            } else {
                info("(highlight)%s(default) rejoined", ghost.name);
            }

            mc.world.playSoundFromEntity(mc.player, mc.player,
                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.AMBIENT, 3.0F,
                    1.0F);
        }
    }

    @EventHandler
    private void onPlayerLeave(PlayerJoinLeaveEvent.Leave event) {
        if (event.getEntry().getProfile() == null)
            return;

        if (loggedPlayers.containsKey(event.getEntry().getProfile().getId())) {
            return;
        }

        if (!lastPlayers.containsKey(event.getEntry().getProfile().getId())) {
            return;
        }

        PlayerTimedEntity player = lastPlayers.get(event.getEntry().getProfile().getId());

        GhostPlayer ghost = new GhostPlayer(player);

        loggedPlayers.put(event.getEntry().getProfile().getId(), ghost);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        lastPlayers.values().forEach(player -> {
            player.cacheRenderParts(event);
        });

        loggedPlayers.values().forEach(player -> {
            player.render3D(event);
        });
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        loggedPlayers.values().forEach(player -> {
            player.render2D(event);
        });
    }

    @Override
    public String getInfoString() {
        return Integer.toString(loggedPlayers.size());
    }

    private class PlayerTimedEntity {
        private long lastSeenTime;

        private PlayerEntity playerEntity;

        private Vec3d pos = new Vec3d(0, 0, 0);

        private List<WireframeEntityRenderer.RenderablePart> parts = null;

        public void cacheRenderParts(Render3DEvent event) {
            parts = WireframeEntityRenderer.cloneEntityForRendering(event, playerEntity, pos);
        }
    }

    private class GhostPlayer {
        private final UUID uuid;
        private long logoutTime;
        private String name;

        private Box hitbox;

        private List<WireframeEntityRenderer.RenderablePart> parts;
        private Vec3d pos;

        public GhostPlayer(PlayerTimedEntity player) {
            uuid = player.playerEntity.getUuid();
            name = player.playerEntity.getName().getString();
            hitbox = player.playerEntity.getBoundingBox();

            parts = player.parts;
            pos = player.pos;

            logoutTime = System.currentTimeMillis();
        }

        public void render3D(Render3DEvent event) {
            if (parts == null) {
                return;
            }

            WireframeEntityRenderer.render(event, pos, parts, 1.0, sideColor.get(), lineColor.get(), shapeMode.get());
        }

        public void render2D(Render2DEvent event) {
            if (!PlayerUtils.isWithinCamera(pos.x, pos.y, pos.z, mc.options.getViewDistance().getValue() * 16))
                return;

            TextRenderer text = TextRenderer.get();
            double scale = nametageScale.get();

            Vector3d nametagPos = new Vector3d((hitbox.minX + hitbox.maxX) / 2, hitbox.maxY + 0.5,
                    (hitbox.minZ + hitbox.maxZ) / 2);

            if (!NametagUtils.to2D(nametagPos, scale))
                return;

            NametagUtils.begin(nametagPos);

            String timeText = " " + getTimeText();
            String totemPopsText = " " + (-MeteorClient.INFO.getPops(uuid));

            // i = half the length of the text
            double i = text.getWidth(name) / 2.0 + text.getWidth(timeText) / 2.0 + text.getWidth(totemPopsText) / 2.0;

            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(-i, 0, i * 2, text.getHeight(), textBackgroundColor.get());
            Renderer2D.COLOR.render(null);

            // Render the actual text
            text.beginBig();

            double hX = text.render(name, -i, 0, nameColor.get());
            hX = text.render(timeText, hX, 0, timeColor.get());
            hX = text.render(totemPopsText, hX, 0, totemPopsColor.get());

            text.end();

            NametagUtils.end();
        }

        // Why the fuck doesn't have have a good time formatting system
        private String getTimeText() {
            double timeSinceLogout = (System.currentTimeMillis() - logoutTime) / 1000.0;

            int totalSeconds = (int) timeSinceLogout;
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int seconds = totalSeconds % 60;

            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }
}
