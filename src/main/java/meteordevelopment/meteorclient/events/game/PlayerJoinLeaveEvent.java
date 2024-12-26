package meteordevelopment.meteorclient.events.game;

import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;

public class PlayerJoinLeaveEvent {
    public static class Join {
        private static final Join INSTANCE = new Join();

        private PlayerListS2CPacket.Entry entry;
    
        public static Join get(PlayerListS2CPacket.Entry entry) {
            INSTANCE.entry = entry;
            return INSTANCE;
        }
        
        public PlayerListS2CPacket.Entry getEntry() {
            return entry;
        }
    }

    public static class Leave {
        private static final Leave INSTANCE = new Leave();

        private PlayerListEntry entry;
    
        public static Leave get(PlayerListEntry entry) {
            INSTANCE.entry = entry;
            return INSTANCE;
        }

        public PlayerListEntry getEntry() {
            return entry;
        }
    }
}
