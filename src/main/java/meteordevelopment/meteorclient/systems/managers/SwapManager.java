package meteordevelopment.meteorclient.systems.managers;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IClientPlayerInteractionManager;
import meteordevelopment.meteorclient.systems.config.AntiCheatConfig;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SwapManager {
    public SwapManager() {
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    private final AntiCheatConfig antiCheatConfig = AntiCheatConfig.get();

    private final Object swapLock = new Object();

    private SwapState multiTickSwapState = new SwapState();
    private SwapState instantSwapState = new SwapState();;

    public boolean beginSwap(Item item, boolean instant) {
        FindItemResult result = InvUtils.findInHotbar(item);

        // If the mode is none, we just give up if it's not in our main hand
        if (getItemSwapMode() == SwapMode.None && !result.isMainHand()) {
            return false;
        }

        // If the mode is silent hotbar, we can only swap to items in the hotbar
        if (getItemSwapMode() == SwapMode.SilentHotbar && !result.found()) {
            return false;
        }

        if (!result.found()) {
            result = InvUtils.find(item);
        }

        if (!result.found()) {
            return false;
        }

        return beginSwap(result, instant);
    }

    public boolean beginSwap(FindItemResult result, boolean instant) {
        if (!result.found()) {
            return false;
        }

        // If the mode is none, we just give up if it's not in our main hand
        if (getItemSwapMode() == SwapMode.None && !result.isMainHand()) {
            return false;
        }

        if (!instant && mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND) {
            return false;
        }

        // Ues a mutex to swap to support multithreaded calls (idk like from network thread or
        // something)
        synchronized (swapLock) {
            // If we're doing an instant swap (like sub tick), we have to wait for the swap to end

            // If we're doing a multi tick swap, we can only do instant swaps
            if (instantSwapState.isSwapped) {
                return false;
            } else if (multiTickSwapState.isSwapped && !instant) {
                return false;
            }

            getSwapState(instant).isSwapped = true;
        }

        SwapState swapState = getSwapState(instant);

        switch (getItemSwapMode()) {
            case SilentHotbar -> {
                swapState.hotbarSelectedSlot = mc.player.getInventory().selectedSlot;
                swapState.hotbarItemSlot = result.slot();
                swapState.didSilentSwap = false;

                mc.player.getInventory().selectedSlot = result.slot();
                ((IClientPlayerInteractionManager) mc.interactionManager).meteor$syncSelected();
            }
            case Auto -> {
                boolean shouldSilentSwap = !result.isHotbar()
                        || (multiTickSwapState.isSwapped && instant)
                        || (mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND);

                if (shouldSilentSwap) {
                    if (antiCheatConfig.swapAntiScreenClose.get()) {
                        if (mc.currentScreen instanceof GameMenuScreen) {
                            getSwapState(instant).isSwapped = false;
                            return false;
                        }
                    }

                    swapState.silentSwapInventorySlot = result.slot();
                    swapState.silentSwapSelectedSlot = mc.player.getInventory().selectedSlot;
                    swapState.didSilentSwap = true;

                    InvUtils.quickSwap().fromId(mc.player.getInventory().selectedSlot)
                            .to(result.slot());
                } else {
                    swapState.hotbarSelectedSlot = mc.player.getInventory().selectedSlot;
                    swapState.hotbarItemSlot = result.slot();
                    swapState.didSilentSwap = false;

                    mc.player.getInventory().selectedSlot = result.slot();
                    ((IClientPlayerInteractionManager) mc.interactionManager).meteor$syncSelected();
                }
            }
            case SilentSwap -> {
                if (antiCheatConfig.swapAntiScreenClose.get()) {
                    if (mc.currentScreen instanceof GameMenuScreen) {
                        getSwapState(instant).isSwapped = false;
                        return false;
                    }
                }

                swapState.silentSwapInventorySlot = result.slot();
                swapState.silentSwapSelectedSlot = mc.player.getInventory().selectedSlot;
                swapState.didSilentSwap = true;

                InvUtils.quickSwap().fromId(mc.player.getInventory().selectedSlot)
                        .to(result.slot());
            }
            case None -> {
                // Fall
            }
        }

        return true;
    }

    public FindItemResult getSlot(Item item) {
        FindItemResult result = InvUtils.findInHotbar(item);

        // If the mode is none, we just give up if it's not in our main hand
        if (getItemSwapMode() == SwapMode.None && !result.isMainHand()) {
            return new FindItemResult(-1, 0);
        }

        // If the mode is silent hotbar, we can only swap to items in the hotbar
        if (getItemSwapMode() == SwapMode.SilentHotbar && !result.found()) {
            return new FindItemResult(-1, 0);
        }

        if (!result.found()) {
            result = InvUtils.find(item);
        }

        if (!result.found()) {
            return new FindItemResult(-1, 0);
        }

        return result;
    }

    public void endSwap(boolean instantSwap) {
        synchronized (swapLock) {
            if (instantSwap && !getSwapState(instantSwap).isSwapped) {
                return;
            }
        }

        SwapState swapState = getSwapState(instantSwap);

        if (swapState.didSilentSwap) {
            InvUtils.quickSwap().fromId(swapState.silentSwapSelectedSlot)
                    .to(swapState.silentSwapInventorySlot);
        } else {
            mc.player.getInventory().selectedSlot = swapState.hotbarSelectedSlot;
            ((IClientPlayerInteractionManager) mc.interactionManager).meteor$syncSelected();
        }

        swapState.isSwapped = false;
    }

    public SwapMode getItemSwapMode() {
        return antiCheatConfig.swapMode.get();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Ensure that the selected slot hasn't changed
        if (multiTickSwapState.isSwapped) {
            if (multiTickSwapState.didSilentSwap) {
                if (multiTickSwapState.silentSwapSelectedSlot != mc.player.getInventory().selectedSlot) {
                    mc.player.getInventory().selectedSlot = multiTickSwapState.silentSwapSelectedSlot;
                    ((IClientPlayerInteractionManager) mc.interactionManager).meteor$syncSelected();
                }
            } else {
                if (multiTickSwapState.hotbarItemSlot != mc.player.getInventory().selectedSlot) {
                    mc.player.getInventory().selectedSlot = multiTickSwapState.hotbarItemSlot;
                    ((IClientPlayerInteractionManager) mc.interactionManager).meteor$syncSelected();
                }
            }
        }
    }

    private SwapState getSwapState(boolean instantSwap) {
        if (instantSwap) {
            return instantSwapState;
        } else {
            return multiTickSwapState;
        }
    }

    public enum SwapMode {
        None, Auto, SilentHotbar, SilentSwap
    }

    private class SwapState {
        public boolean isSwapped = false;

        public boolean didSilentSwap = false;

        public int hotbarSelectedSlot = 0;
        public int hotbarItemSlot = 0;

        public int silentSwapSelectedSlot = 0;
        public int silentSwapInventorySlot = 0;
    }
}
