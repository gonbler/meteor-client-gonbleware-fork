/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client/).
 * Copyright (c) 2020 Meteor Development.
 */

package minegame159.meteorclient.modules.player;

import baritone.api.BaritoneAPI;
import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import minegame159.meteorclient.events.GameJoinedEvent;
import minegame159.meteorclient.events.GameLeftEvent;
import minegame159.meteorclient.events.PostTickEvent;
import minegame159.meteorclient.modules.Category;
import minegame159.meteorclient.modules.ModuleManager;
import minegame159.meteorclient.modules.ToggleModule;
import minegame159.meteorclient.modules.combat.AutoLog;
import minegame159.meteorclient.modules.movement.AutoWalk;
import minegame159.meteorclient.modules.movement.NoFall;
import minegame159.meteorclient.settings.*;
import net.minecraft.item.ToolItem;

/**
 * @author Inclement
 * InfinityMiner is a module which alternates between mining a target block, and a repair block.
 * This allows the user to mine indefinitely, provided they have the mending enchantment.
 */
public class InfinityMiner extends ToggleModule {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAutoToggles = settings.createGroup("Auto Toggles");
    private final SettingGroup sgExtras = settings.createGroup("Extras");

    private final Setting<String> targetBlock = sgGeneral.add(new StringSetting.Builder()
            .name("Target Block")
            .description("The Target Block to Mine")
            .defaultValue("ancient_debris")
            .build()
    );

    private final Setting<String> repairBlock = sgGeneral.add(new StringSetting.Builder()
            .name("Repair Block")
            .description("The Block Mined to Repair Your Pickaxe")
            .defaultValue("nether_quartz_ore")
            .build()
    );
    private final Setting<Integer> durabilityThreshold = sgGeneral.add(new IntSetting.Builder()
            .name("Durability Threshold")
            .description("The durability at which to start repairing.")
            .defaultValue(150)
            .max(500)
            .min(50)
            .sliderMin(50)
            .sliderMax(500)
            .build());

    private final Setting<Boolean> autoToggleAutoLog = sgAutoToggles.add(new BoolSetting.Builder()
            .name("Toggle AutoLog")
            .description("Ensure AutoLog is enabled")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> autoToggleNoBreakDelay = sgAutoToggles.add(new BoolSetting.Builder()
            .name("Toggle NoBreakDelay")
            .description("Ensure NoBreakDelay is enabled")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> autoWalkHome = sgExtras.add(new BoolSetting.Builder()
            .name("Walk Home")
            .description("When your inventory is full, walk home.")
            .defaultValue(false)
            .build());


    public InfinityMiner() {
        super(Category.Player, "Infinity Miner", "Mine forever");
    }

    private boolean noFallWasActive;
    private boolean autoEatWasActive;
    private boolean autoToolWasActive;
    private boolean autoLogWasActive;
    private boolean noBreakDelayWasActive;
    private boolean baritoneSetting;
    private final String BARITONE_MINE = "mine ";
    private Mode currentMode = Mode.STILL;
    private Mode secondaryMode;
    private boolean baritoneRunning = false;
    private int playerX;
    private int playerY;
    private int playerZ;

    public enum Mode {
        TARGET,
        REPAIR,
        STILL,
        HOME
    }

    @Override
    public void onActivate() {
        baritoneSetting = BaritoneAPI.getSettings().mineScanDroppedItems.value;
        if (baritoneSetting) BaritoneAPI.getSettings().mineScanDroppedItems.value = false;
        BaritoneAPI.getSettings().mineScanDroppedItems.value = false;
        NoFall noFall = ModuleManager.INSTANCE.get(NoFall.class);
        noFallWasActive = noFall.isActive();
        if (!noFall.isActive()) noFall.toggle();

        AutoEat autoEat = ModuleManager.INSTANCE.get(AutoEat.class);
        autoEatWasActive = autoEat.isActive();
        if (!autoEat.isActive()) autoEat.toggle();

        AutoTool autoTool = ModuleManager.INSTANCE.get(AutoTool.class);
        autoToolWasActive = autoTool.isActive();
        if (!autoTool.isActive()) autoTool.toggle();

        if (autoToggleAutoLog.get()) {
            AutoLog autoLog = ModuleManager.INSTANCE.get(AutoLog.class);
            autoLogWasActive = autoLog.isActive();
            if (!autoLog.isActive()) autoLog.toggle();
        }

        if (autoToggleNoBreakDelay.get()) {
            NoBreakDelay noBreakDelay = ModuleManager.INSTANCE.get(NoBreakDelay.class);
            noBreakDelayWasActive = noBreakDelay.isActive();
            if (!noBreakDelay.isActive()) noBreakDelay.toggle();
        }
        AutoWalk autoWalk = ModuleManager.INSTANCE.get(AutoWalk.class);
        if (autoWalk.isActive()) autoWalk.toggle();

        if (mc.player != null && autoWalkHome.get()) {
            playerX = (int) mc.player.getX();
            playerY = (int) mc.player.getY();
            playerZ = (int) mc.player.getZ();
        }

        if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing())
            BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
    }

    @Override
    public void onDeactivate() {
        NoFall noFall = ModuleManager.INSTANCE.get(NoFall.class);
        if (!noFallWasActive && noFall.isActive()) noFall.toggle();

        AutoEat autoEat = ModuleManager.INSTANCE.get(AutoEat.class);
        if (!autoEatWasActive && autoEat.isActive()) autoEat.toggle();

        AutoTool autoTool = ModuleManager.INSTANCE.get(AutoTool.class);
        if (!autoToolWasActive && autoTool.isActive()) autoTool.toggle();

        if (autoToggleAutoLog.get()) {
            AutoLog autoLog = ModuleManager.INSTANCE.get(AutoLog.class);
            if (!autoLogWasActive && autoLog.isActive()) autoLog.toggle();
        }
        BaritoneAPI.getSettings().mineScanDroppedItems.value = baritoneSetting;

        if (autoToggleNoBreakDelay.get()) {
            NoBreakDelay noBreakDelay = ModuleManager.INSTANCE.get(NoBreakDelay.class);
            if (!noBreakDelayWasActive && noBreakDelay.isActive()) noBreakDelay.toggle();
        }
        baritoneRequestStop();
        baritoneRunning = false;
        currentMode = Mode.STILL;
        secondaryMode = null;
    }

    @SuppressWarnings("unused")
    @EventHandler
    private final Listener<PostTickEvent> onTick = new Listener<>(event -> {
        try {
            if (mc.player == null) return;
            if (!baritoneRunning && currentMode == Mode.STILL) {
                if (autoWalkHome.get() && isInventoryFull() && secondaryMode != Mode.HOME) {
                    baritoneRequestPathHome();
                    return;
                }
                currentMode = (isTool() && getCurrentDamage() <= durabilityThreshold.get()) ? Mode.REPAIR : Mode.TARGET;
                if (currentMode == Mode.REPAIR) baritoneRequestMineRepairBlock();
                else baritoneRequestMineTargetBlock();
            } else if (autoWalkHome.get() && isInventoryFull() && secondaryMode != Mode.HOME)
                baritoneRequestPathHome();
            else if (currentMode == Mode.REPAIR) {
                int REPAIR_BUFFER = 15;
                if (isTool() && getCurrentDamage() >= mc.player.getMainHandStack().getMaxDamage() - REPAIR_BUFFER) {
                    if (secondaryMode != Mode.HOME) {
                        currentMode = Mode.TARGET;
                        baritoneRequestMineTargetBlock();
                    } else {
                        currentMode = Mode.HOME;
                        baritoneRequestPathHome();
                    }
                }
            } else if (currentMode == Mode.TARGET) {
                if (isTool() && getCurrentDamage() <= durabilityThreshold.get()) {
                    currentMode = Mode.REPAIR;
                    baritoneRequestMineRepairBlock();
                } else if (autoWalkHome.get() && isInventoryFull()) baritoneRequestPathHome();
            } else if (currentMode == Mode.HOME)
                if (isTool() && getCurrentDamage() <= durabilityThreshold.get()) currentMode = Mode.REPAIR;
        } catch (Exception ignored) {
        }
    });

    private void baritoneRequestMineTargetBlock() {
        BaritoneAPI.getSettings().mineScanDroppedItems.value = true;
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(BARITONE_MINE + targetBlock.get());
        baritoneRunning = true;
    }

    private void baritoneRequestMineRepairBlock() {
        BaritoneAPI.getSettings().mineScanDroppedItems.value = false;
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(BARITONE_MINE + repairBlock.get());
        baritoneRunning = true;
    }

    private void baritoneRequestStop() {
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("stop");
        baritoneRunning = false;
        currentMode = Mode.STILL;
    }

    private void baritoneRequestPathHome() {
        if (autoWalkHome.get()) {
            baritoneRequestStop();
            secondaryMode = Mode.HOME;
            currentMode = Mode.HOME;
            String BARITONE_GOTO = "goto ";
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute(BARITONE_GOTO + playerX + " " + playerY + " " + playerZ);
        }
    }

    private Boolean isInventoryFull() {
        return mc.player != null && mc.player.inventory.getEmptySlot() == -1;
    }

    @SuppressWarnings("unused")
    @EventHandler
    private final Listener<GameLeftEvent> onGameDisconnect = new Listener<>(event -> {
        baritoneRequestStop();
        if (this.isActive()) this.toggle();
    });

    @SuppressWarnings("unused")
    @EventHandler
    private final Listener<GameJoinedEvent> onGameJoin = new Listener<>(event -> {
        baritoneRequestStop();
        if (this.isActive()) this.toggle();
    });

    public Enum<Mode> getMode() {
        return currentMode;
    }

    public String getCurrentTarget() {
        return (currentMode == Mode.REPAIR) ? repairBlock.get() : targetBlock.get();
    }

    public int[] getHomeCoords() {
        return new int[]{playerX, playerY, playerX};
    }

    public boolean isTool() {
        return mc.player != null && mc.player.getMainHandStack() != null && mc.player.getMainHandStack().getItem() instanceof ToolItem;
    }

    public int getCurrentDamage() {
        return (mc.player != null) ? mc.player.getMainHandStack().getItem().getMaxDamage() - mc.player.getMainHandStack().getDamage() : -1;
    }


}
