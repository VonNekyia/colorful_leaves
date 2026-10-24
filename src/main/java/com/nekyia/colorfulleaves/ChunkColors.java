package com.nekyia.colorfulleaves;

import com.nekyia.colorfulleaves.LeafColors.Look;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The leaf colours of one chunk, as the server sends them. It replaces whatever the chunk
 * had before; a chunk without colours is sent to take them away.
 *
 * <p>On the wire, all numbers big-endian ints except the leading version byte:
 * <pre>
 * byte version, int chunkX, int chunkZ, int groups,
 *   per group: int colour, [version 2: int secondary, int blossom,] int count, count x int position
 * </pre>
 * Colours are 0xRRGGBB in bits 0 to 23. Bit 24 of the colour asks for the leaves' lighter
 * texture, so that light colours come out light instead of greyish; bit 24 of secondary and
 * blossom says they are there. A position packs the block within the chunk as
 * {@code x | z << 4 | (y + 2048) << 8}.
 *
 * <p>Version 1 comes on colorfulleaves:chunk, which mods before 1.1 listen on too; version 2,
 * with a secondary colour and blossoms, on colorfulleaves:leaves. Either channel takes either.
 */
record ChunkColors(Type<ChunkColors> type, int chunkX, int chunkZ, @Nullable Long2ObjectMap<Look> looks)
        implements CustomPacketPayload {

    static final Type<ChunkColors> CHUNK = new Type<>(Identifier.fromNamespaceAndPath("colorfulleaves", "chunk"));
    static final Type<ChunkColors> LEAVES = new Type<>(Identifier.fromNamespaceAndPath("colorfulleaves", "leaves"));

    private static final int Y_OFFSET = 2048;
    private static final int RGB = 0xFFFFFF;
    /** Bit 24 on the wire: bright on the colour, there on secondary and blossom. */
    private static final int BIT_24 = 1 << 24;

    static StreamCodec<FriendlyByteBuf, ChunkColors> codec(Type<ChunkColors> type) {
        return CustomPacketPayload.codec(ChunkColors::write, buffer -> read(type, buffer));
    }

    /**
     * Anything unreadable - an unknown version, a broken count - is skipped whole instead of
     * thrown: a throwing decoder would cost the player their connection.
     */
    private static ChunkColors read(Type<ChunkColors> type, FriendlyByteBuf buffer) {
        int version = buffer.readableBytes() < 13 ? 0 : buffer.readByte();
        if (version != 1 && version != 2) {
            buffer.skipBytes(buffer.readableBytes());
            return new ChunkColors(type, 0, 0, null);
        }
        int chunkX = buffer.readInt();
        int chunkZ = buffer.readInt();
        int groups = buffer.readInt();
        Long2ObjectMap<Look> looks = new Long2ObjectOpenHashMap<>();
        for (int group = 0; group < groups; group++) {
            if (buffer.readableBytes() < (version == 2 ? 16 : 8)) {
                break;
            }
            int colour = buffer.readInt();
            int secondary = version == 2 ? buffer.readInt() : 0;
            int blossom = version == 2 ? buffer.readInt() : 0;
            Look look = new Look(0xFF000000 | colour & RGB, (colour & BIT_24) != 0, there(secondary), there(blossom));
            int count = buffer.readInt();
            if (count < 0 || count > buffer.readableBytes() / 4) {
                break;
            }
            for (int i = 0; i < count; i++) {
                int packed = buffer.readInt();
                looks.put(BlockPos.asLong(chunkX << 4 | (packed & 15), (packed >>> 8 & 4095) - Y_OFFSET,
                        chunkZ << 4 | (packed >>> 4 & 15)), look);
            }
        }
        boolean broken = buffer.readableBytes() > 0;
        buffer.skipBytes(buffer.readableBytes());
        return new ChunkColors(type, chunkX, chunkZ, broken ? null : looks);
    }

    /** A secondary or blossom colour from the wire as a tint, or 0 when bit 24 says it is not there. */
    private static int there(int wire) {
        return (wire & BIT_24) != 0 ? 0xFF000000 | wire & RGB : 0;
    }

    /**
     * The other way round, as a codec has to have it; the client never sends this. On
     * colorfulleaves:chunk as version 1, without secondary colours and blossoms.
     */
    private void write(FriendlyByteBuf buffer) {
        boolean two = type == LEAVES;
        Map<Look, List<Long>> byLook = new HashMap<>();
        looks.forEach((pos, look) -> byLook.computeIfAbsent(two ? look : new Look(look.colour(), look.bright(), 0, 0),
                ignored -> new ArrayList<>()).add(pos));
        buffer.writeByte(two ? 2 : 1);
        buffer.writeInt(chunkX);
        buffer.writeInt(chunkZ);
        buffer.writeInt(byLook.size());
        byLook.forEach((look, positions) -> {
            buffer.writeInt(look.colour() & RGB | (look.bright() ? BIT_24 : 0));
            if (two) {
                buffer.writeInt(look.secondary() == 0 ? 0 : look.secondary() & RGB | BIT_24);
                buffer.writeInt(look.blossom() == 0 ? 0 : look.blossom() & RGB | BIT_24);
            }
            buffer.writeInt(positions.size());
            positions.forEach(pos -> buffer.writeInt(BlockPos.getX(pos) & 15
                    | (BlockPos.getZ(pos) & 15) << 4 | (BlockPos.getY(pos) + Y_OFFSET) << 8));
        });
    }
}
