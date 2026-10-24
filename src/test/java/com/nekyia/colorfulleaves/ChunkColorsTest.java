package com.nekyia.colorfulleaves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nekyia.colorfulleaves.LeafColors.Look;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Reads payloads written the way the server writes them: with a plain DataOutputStream. */
class ChunkColorsTest {

    private static int packed(int x, int y, int z) {
        return x | z << 4 | (y + 2048) << 8;
    }

    private static FriendlyByteBuf wire(int version, int chunkX, int chunkZ, int[][] groups) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(version);
        out.writeInt(chunkX);
        out.writeInt(chunkZ);
        out.writeInt(groups.length);
        for (int[] group : groups) {
            for (int value : group) {
                out.writeInt(value);
            }
        }
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes.toByteArray()));
    }

    private static ChunkColors read(FriendlyByteBuf buffer) {
        return ChunkColors.codec(ChunkColors.CHUNK).decode(buffer);
    }

    @Test
    void readsWhatTheServerWrites() throws IOException {
        FriendlyByteBuf buffer = wire(1, -3, 5, new int[][] {
                {0xD98C2B, 2, packed(1, 64, 2), packed(15, -64, 15)},
                {0x00FF00, 1, packed(0, 319, 0)},
        });

        ChunkColors colors = read(buffer);

        assertEquals(0, buffer.readableBytes());
        assertEquals(3, colors.looks().size());
        Look orange = new Look(0xFFD98C2B, false, 0, 0);
        assertEquals(orange, colors.looks().get(BlockPos.asLong(-47, 64, 82)));
        assertEquals(orange, colors.looks().get(BlockPos.asLong(-33, -64, 95)));
        assertEquals(new Look(0xFF00FF00, false, 0, 0), colors.looks().get(BlockPos.asLong(-48, 319, 80)));
    }

    @Test
    void keepsTheAskForTheLighterTexture() throws IOException {
        FriendlyByteBuf buffer = wire(1, 0, 0, new int[][] {{1 << 24 | 0xFFFFFF, 1, packed(3, 70, 4)}});

        assertEquals(new Look(0xFFFFFFFF, true, 0, 0), read(buffer).looks().get(BlockPos.asLong(3, 70, 4)));
    }

    @Test
    void readsSecondaryColoursAndBlossomsFromVersionTwo() throws IOException {
        FriendlyByteBuf buffer = wire(2, 0, 0, new int[][] {
                {0xB5361D, 1 << 24 | 0xE8C24A, 1 << 24 | 0xF4C6E0, 1, packed(1, 80, 1)},
                {1 << 24 | 0x2A46C8, 0, 0xFFFFFF, 1, packed(2, 80, 2)},
        });

        ChunkColors colors = read(buffer);

        assertEquals(0, buffer.readableBytes());
        assertEquals(new Look(0xFFB5361D, false, 0xFFE8C24A, 0xFFF4C6E0), colors.looks().get(BlockPos.asLong(1, 80, 1)));
        // Without bit 24 a secondary colour or blossom is not there, whatever the other bits say.
        assertEquals(new Look(0xFF2A46C8, true, 0, 0), colors.looks().get(BlockPos.asLong(2, 80, 2)));
    }

    @Test
    void skipsWhatItCannotReadWithoutThrowing() throws IOException {
        FriendlyByteBuf tooMany = wire(1, 0, 0, new int[][] {{0xFF0000, 50, packed(0, 0, 0)}});
        FriendlyByteBuf newer = wire(3, 0, 0, new int[][] {{0xFF0000, 1, packed(0, 0, 0)}});

        assertNull(read(tooMany).looks());
        assertNull(read(newer).looks());
        assertEquals(0, tooMany.readableBytes());
        assertEquals(0, newer.readableBytes());
    }

    @Test
    void writesVersionTwoOnLeavesAndVersionOneOnChunk() {
        Look full = new Look(0xFFB5361D, true, 0xFFE8C24A, 0xFFF4C6E0);
        Long2ObjectMap<Look> looks = new Long2ObjectOpenHashMap<>();
        looks.put(BlockPos.asLong(17, 64, -30), full);

        for (var type : java.util.List.of(ChunkColors.LEAVES, ChunkColors.CHUNK)) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            ChunkColors.codec(type).encode(buffer, new ChunkColors(type, 1, -2, looks));
            Look back = ChunkColors.codec(type).decode(buffer).looks().get(BlockPos.asLong(17, 64, -30));
            assertEquals(type == ChunkColors.LEAVES ? full : new Look(0xFFB5361D, true, 0, 0), back);
        }
    }

    @Test
    void spreadsTheClassesBetweenColourAndSecondary() {
        Look look = new Look(0xFF000000 | 10 << 16 | 100, false, 0xFF000000 | 21 << 16 | 201, 0xFF123456);
        for (BlockPos pos : java.util.List.of(new BlockPos(0, 0, 0), new BlockPos(5, 70, -9), new BlockPos(-300, 12, 44))) {
            Set<Integer> classes = IntStream.range(0, 3).mapToObj(layer -> look.tint(layer, pos)).collect(Collectors.toSet());
            // Halfway rounds up: (10 + 21 + 1) / 2 = 16, (100 + 201 + 1) / 2 = 151.
            assertEquals(Set.of(look.colour(), 0xFF000000 | 16 << 16 | 151, look.secondary()), classes);
            assertEquals(0xFF123456, look.tint(LeafColors.BLOSSOMS, pos));
        }
        Look plain = new Look(0xFF2A46C8, false, 0, 0);
        assertEquals(plain.colour(), plain.tint(2, BlockPos.ZERO));
    }
}
