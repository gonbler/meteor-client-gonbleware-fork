package meteordevelopment.meteorclient.events.entity;

import net.minecraft.entity.player.PlayerEntity;

public class PlayerDeathEvent {
    public static class TotemPop extends PlayerDeathEvent {
        private static final TotemPop INSTANCE = new TotemPop();

        private PlayerEntity player;
        private int pops;

        public static PlayerDeathEvent get(PlayerEntity player, int pop) {
            INSTANCE.player = player;
            INSTANCE.pops = pop;
            return INSTANCE;
        }

        public PlayerEntity getPlayer() {
            return player;
        }

        public int getPops() {
            return pops;
        }
    }

    public static class Death extends PlayerDeathEvent {
        private static final Death INSTANCE = new Death();

        private PlayerEntity player;
        private int pops;

        public static Death get(PlayerEntity player, int pop) {
            INSTANCE.player = player;
            INSTANCE.pops = pop;
            return INSTANCE;
        }

        public PlayerEntity getPlayer() {
            return player;
        }

        public int getPops() {
            return pops;
        }
    }
}
