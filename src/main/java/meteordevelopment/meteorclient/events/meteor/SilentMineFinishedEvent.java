package meteordevelopment.meteorclient.events.meteor;

import net.minecraft.util.math.BlockPos;

public class SilentMineFinishedEvent {
    public static class Pre {
        private boolean isRebreak;
        private BlockPos blockPos;

        public Pre(BlockPos blockPos, boolean isRebreak) {
            this.blockPos = blockPos;
            this.isRebreak = isRebreak;
        }

        public boolean getIsRebreak() {
            return isRebreak;
        }

        public BlockPos getBlockPos() {
            return blockPos;
        }
    }

    public static class Post {
        private boolean isRebreak;
        private BlockPos blockPos;

        public Post(BlockPos blockPos, boolean isRebreak) {
            this.blockPos = blockPos;
            this.isRebreak = isRebreak;
        }


        public boolean getIsRebreak() {
            return isRebreak;
        }

        public BlockPos getBlockPos() {
            return blockPos;
        }
    }
}
