package com.nekyia.colorfulleaves;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/** The leaf colours the server has sent, for the chunks the client holds. */
public final class LeafColors {

    /** The tint layer of the blossoms; 0 to 2 are the three classes of leaves. */
    static final int BLOSSOMS = 3;

    /**
     * What the server gave one block. Colours as tints, 0xFFRRGGBB; secondary and blossom
     * 0 for none. Blocks of one group share one look.
     *
     * @param bright    draw the leaves on their lighter texture
     * @param secondary spread the block's leaves between the colour and this one
     * @param blossom   draw the blossoms of the mask in this colour
     */
    record Look(int colour, boolean bright, int secondary, int blossom) {

        /** Which share of the way to the secondary colour each class takes: one row per block. */
        private static final int[][] SHARES = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};

        /** The tint of a layer here: a class of leaves, or the blossoms. */
        int tint(int layer, BlockPos pos) {
            if (layer == BLOSSOMS) {
                return blossom != 0 ? blossom : colour;
            }
            if (secondary == 0) {
                return colour;
            }
            // Mth.getSeed as the game seeds a block's model: the same pattern for everyone.
            int share = SHARES[(int) Math.floorMod(Mth.getSeed(pos), 6L)][layer];
            return share == 0 ? colour : share == 2 ? secondary : halfway(colour, secondary);
        }

        private static int halfway(int a, int b) {
            int mixed = 0xFF000000;
            for (int shift = 0; shift <= 16; shift += 8) {
                mixed |= ((a >> shift & 255) + (b >> shift & 255) + 1) / 2 << shift;
            }
            return mixed;
        }
    }

    /**
     * Chunk -> block -> look. The chunk builders read this from their own threads, so a
     * chunk's map is only ever replaced whole, never changed.
     */
    private static final Map<Long, Long2ObjectMap<Look>> BY_CHUNK = new ConcurrentHashMap<>();

    private LeafColors() {
    }

    /** What the server gave this block, or null for nothing. */
    static @Nullable Look look(BlockPos pos) {
        Long2ObjectMap<Look> looks = BY_CHUNK.get(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
        return looks == null ? null : looks.get(pos.asLong());
    }

    /** The colour the server gave this block as a tint, or 0 for none. */
    public static int tint(BlockPos pos) {
        Look look = look(pos);
        return look == null ? 0 : look.colour();
    }

    /** Takes a chunk's new colours and redraws what changed. */
    static void set(Minecraft client, ChunkColors payload) {
        if (payload.looks() == null) {
            return;
        }
        long chunk = ChunkPos.pack(payload.chunkX(), payload.chunkZ());
        Long2ObjectMap<Look> old = payload.looks().isEmpty() ? BY_CHUNK.remove(chunk) : BY_CHUNK.put(chunk, payload.looks());
        redraw(client, payload.chunkX(), payload.chunkZ(), old, payload.looks());
    }

    static void forget(ChunkPos chunk) {
        BY_CHUNK.remove(chunk.pack());
    }

    /** Another world, or no world: nothing sent so far applies any more. */
    static void clear() {
        BY_CHUNK.clear();
    }

    /** Rebuilds the sections of the chunk that held coloured leaves before or after. */
    private static void redraw(Minecraft client, int chunkX, int chunkZ, @Nullable Long2ObjectMap<Look> before,
                               Long2ObjectMap<Look> after) {
        if (client.level == null) {
            return;
        }
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (Long2ObjectMap<Look> looks : List.of(before == null ? after : before, after)) {
            for (long pos : looks.keySet()) {
                lowest = Math.min(lowest, BlockPos.getY(pos));
                highest = Math.max(highest, BlockPos.getY(pos));
            }
        }
        if (lowest <= highest) {
            client.level.setSectionRangeDirty(chunkX, lowest >> 4, chunkZ, chunkX, highest >> 4, chunkZ);
        }
    }
}
