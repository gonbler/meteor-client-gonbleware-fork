package meteordevelopment.meteorclient.systems.modules.render.newchunks;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.Chunk;
import meteordevelopment.meteorclient.utils.Utils;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class NewChunksChunk {

    private int cX;
    private int cZ;

    private double x1;
    private double z1;
    private double x2;
    private double z2;

    private boolean isNewChunk;

    public NewChunksChunk(Chunk chunk, boolean isNew) {
        x1 = chunk.getPos().getStartX();
        z1 = chunk.getPos().getStartZ();

        x2 = chunk.getPos().getEndX() + 1;
        z2 = chunk.getPos().getEndZ() + 1;

        cX = chunk.getPos().x;
        cZ = chunk.getPos().z;

        isNewChunk = isNew;
    }

    public void render(Color color, Render3DEvent event) {
        event.renderer.quadHorizontal(x1, 0, z1, x2, z2, color);
    }

    public boolean shouldBeDeleted() {
        int viewDist = Utils.getRenderDistance() + 64;
        int chunkX = ChunkSectionPos.getSectionCoord(mc.player.getBlockPos().getX());
        int chunkZ = ChunkSectionPos.getSectionCoord(mc.player.getBlockPos().getZ());

        return cX > chunkX + viewDist || cX < chunkX - viewDist || cZ > chunkZ + viewDist || cZ < chunkZ - viewDist;
    }

    public boolean getIsNewChunk() {
        return isNewChunk;
    }
}
