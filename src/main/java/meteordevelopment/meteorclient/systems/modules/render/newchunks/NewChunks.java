package meteordevelopment.meteorclient.systems.modules.render.newchunks;

import java.util.Iterator;
import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;

public class NewChunks extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> newChunkColor = sgGeneral.add(new ColorSetting.Builder()
            .name("new-chunk-color")
            .description("The color for new chunks")
            .defaultValue(Color.RED.a(50))
            .build());

    private final Setting<SettingColor> oldChunkColor = sgGeneral.add(new ColorSetting.Builder()
            .name("old-chunk-color")
            .description("The color for old chunks")
            .defaultValue(Color.GREEN.a(50))
            .build());

    private final Setting<List<Block>> overworldNewBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("overworld-new-chunk-blocks")
        .description("Blocks to search for in the overworld.")
        .onChanged(blocks1 -> {
            if (isActive() && Utils.canUpdate()) onActivate();
        })
        .defaultValue(
            Blocks.COPPER_ORE,
            Blocks.KELP,
            Blocks.KELP_PLANT
        )
        .build()
    );

    private final Setting<List<Block>> netherNewBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("nether-new-chunk-blocks")
        .description("Blocks to search for in the nether.")
        .onChanged(blocks1 -> {
            if (isActive() && Utils.canUpdate()) onActivate();
        })
        .defaultValue(
            Blocks.ANCIENT_DEBRIS,
            Blocks.NETHER_GOLD_ORE
        )
        .build()
    );

    private final Long2ObjectMap<NewChunksChunk> chunks = new Long2ObjectOpenHashMap<>();

    private Dimension lastDimension;

    public NewChunks() {
        super(Categories.Render, "new-chunks", "Renders chunks that are newly generated.");
    }

    @Override
    public void onActivate() {
        synchronized (chunks) {
            chunks.clear();
        }

        lastDimension = PlayerUtils.getDimension();

        for (Chunk chunk : Utils.chunks()) {
            searchChunk(chunk, null, lastDimension);
        }
    }

    @Override
    public void onDeactivate() {
        synchronized (chunks) {
            chunks.clear();
        }
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        searchChunk(event.chunk(), event, PlayerUtils.getDimension());
    }

    @EventHandler
    private void onPostTick(TickEvent.Post event) {
        Dimension dimension = PlayerUtils.getDimension();

        if (lastDimension != dimension)
            onActivate();

        lastDimension = dimension;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        synchronized (chunks) {
            for (Iterator<NewChunksChunk> it = chunks.values().iterator(); it.hasNext();) {
                NewChunksChunk chunk = it.next();

                if (chunk.shouldBeDeleted()) {
                    it.remove();
                } else {
                    chunk.render(chunk.getIsNewChunk() ? newChunkColor.get() : oldChunkColor.get(), event);
                }
            }
        }
    }

    private void searchChunk(Chunk chunk, ChunkDataEvent event, Dimension dimension) {
        if (!isActive()) return;

        MeteorExecutor.execute(() -> {
            BlockPos.Mutable blockPos = new BlockPos.Mutable();

            List<Block> blocks;

            if (dimension == Dimension.Overworld) {
                blocks = overworldNewBlocks.get();
            } else if (dimension == Dimension.Nether) {
                blocks = netherNewBlocks.get();
            } else { // Only search for new chunks in the overworld and nether
                return;
            }

            boolean isNew = false;

            for (int x = chunk.getPos().getStartX(); x <= chunk.getPos().getEndX(); x++) {
                for (int z = chunk.getPos().getStartZ(); z <= chunk.getPos().getEndZ(); z++) {
                    int height = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE).get(x - chunk.getPos().getStartX(), z - chunk.getPos().getStartZ());

                    for (int y = mc.world.getBottomY(); y < height; y++) {
                        blockPos.set(x, y, z);
                        BlockState bs = chunk.getBlockState(blockPos);

                        isNew |= blocks.contains(bs.getBlock());

                        if (isNew) {
                            break;
                        }
                    }

                    if (isNew) {
                        break;
                    }
                }

                if (isNew) {
                    break;
                }
            }

            synchronized (chunks) {
                if (!chunks.containsKey(chunk.getPos().toLong())) {
                    if (isNew) {
                        chunks.put(chunk.getPos().toLong(), new NewChunksChunk(chunk, true));
                    } else {
                        chunks.put(chunk.getPos().toLong(), new NewChunksChunk(chunk, false));
                    }
                }
            }
        });
    }
}
