/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2021 Meteor Development.
 */

package meteordevelopment.meteorclient;

import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.events.meteor.CharTypedEvent;
import meteordevelopment.meteorclient.events.meteor.KeyEvent;
import meteordevelopment.meteorclient.events.meteor.MouseButtonEvent;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.renderer.*;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.DiscordPresence;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.FakeClientPlayer;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.misc.input.KeyBinds;
import meteordevelopment.meteorclient.utils.network.Capes;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.network.OnlinePlayers;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.DamageUtils;
import meteordevelopment.meteorclient.utils.player.EChestMemory;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.Outlines;
import meteordevelopment.meteorclient.utils.render.color.RainbowColors;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.IEventBus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.lang.invoke.MethodHandles;

public class MeteorClient implements ClientModInitializer {
    public static MinecraftClient mc;
    public static MeteorClient INSTANCE;
    public static final IEventBus EVENT_BUS = new EventBus();
    public static final File FOLDER = new File(FabricLoader.getInstance().getGameDir().toString(), "meteor-client");
    public static final Logger LOG = LogManager.getLogger();

    @Override
    public void onInitializeClient() {
        if (INSTANCE == null) {
            INSTANCE = this;
            return;
        }

        LOG.info("Initializing Meteor Client");

        // Global minecraft client accessor
        mc = MinecraftClient.getInstance();

        // Register event handlers
        EVENT_BUS.registerLambdaFactory("meteordevelopment.meteorclient", (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));

        // Pre-load
        Systems.addPreLoadTask(() -> {
            if (!Modules.get().getFile().exists()) {
                Modules.get().get(DiscordPresence.class).toggle();
                Utils.addMeteorPvpToServerList();
            }
        });

        // Initialise stuff
        AddonManager.init();
        Utils.init();
        GL.init();
        Shaders.init();
        Renderer2D.init();
        Outlines.init();
        MeteorExecutor.init();
        Capes.init();
        RainbowColors.init();
        BlockIterator.init();
        EChestMemory.init();
        Rotations.init();
        Names.init();
        FakeClientPlayer.init();
        PostProcessRenderer.init();
        Tabs.init();
        GuiThemes.init();
        Fonts.init();
        DamageUtils.init();
        BlockUtils.init();

        // Register module categories
        Modules.REGISTERING_CATEGORIES = true;
        Categories.register();
        AddonManager.ADDONS.forEach(MeteorAddon::onRegisterCategories);
        Modules.REGISTERING_CATEGORIES = false;

        Systems.init();

        EVENT_BUS.subscribe(this);

        // Call onInitialize for addons
        AddonManager.ADDONS.forEach(MeteorAddon::onInitialize);

        Modules.get().sortModules();
        Systems.load();

        Fonts.load();
        GuiRenderer.init();
        GuiThemes.postInit();
        MeteorStarscript.init();
        ChatUtils.init();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            OnlinePlayers.leave();
            Systems.save();
            GuiThemes.save();
        }));
    }

    private void openClickGui() {
        Tabs.get().get(0).openScreen(GuiThemes.get());
    }

    @EventHandler
    private void onKeyGUI(KeyEvent event) {
        if (event.action == KeyAction.Press && KeyBinds.OPEN_CLICK_GUI.matchesKey(event.key, 0)) {
            if (Utils.canOpenClickGUI()) openClickGui();
        }
    }

    @EventHandler
    private void onMouseButtonGUI(MouseButtonEvent event) {
        if (event.action == KeyAction.Press && event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT && KeyBinds.OPEN_CLICK_GUI.matchesMouse(event.button)) {
            if (Utils.canOpenClickGUI()) openClickGui();
        }
    }

    // Console

    @EventHandler
    private void onCharTyped(CharTypedEvent event) {
        if (mc.currentScreen != null || !Config.get().prefixOpensConsole || Config.get().prefix.isBlank()) return;

        if (event.c == Config.get().prefix.charAt(0)) {
            mc.setScreen(new ChatScreen(Config.get().prefix));
            event.cancel();
        }
    }
}
