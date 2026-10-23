package com.nekyia.colorfulleaves;

import com.nekyia.colorfulleaves.LeafColors.Look;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.SpriteSourceRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Shows the leaf colours Terranova's trees carry. The server tells the client, chunk by
 * chunk, which leaves get which colour; which tree that is and why is the server's
 * business alone. The falling leaves take the colour of the leaf they fall from.
 *
 * <p>A tint only darkens the texture it lies on, and leaf textures are a mid grey - so a
 * white comes out grey. Where the server asks for it, the leaves are drawn with a lighter
 * copy of their texture instead, made from it by the palettes this mod brings along.
 *
 * <p>Azalea, cherry, pale oak and poplar leaves have their colour painted into the texture,
 * which a tint would only muddy. Where the server gives them a colour, they are drawn with
 * a grey copy of it, as light as oak's, and fall as tinted leaves; the flowers of flowering
 * azalea keep their colour.
 *
 * <p>A mask per kind of leaves, painted in Aseprite (art/masks), tells the single leaves of a
 * texture apart. Coloured leaves get highlights from it; with a secondary colour from the
 * server their leaves spread between the two, and with a blossom colour they bloom.
 *
 * <p>A bush standing on moss takes the moss's green, so that mistletoe - moss with a bush
 * on top, as builders hang it in crowns - reads as one clump.
 */
public final class ColorfulLeaves implements ClientModInitializer {

    /** The game tints the first seven, the others have their colour painted in. */
    private static final List<Block> LEAVES = List.of(Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.MANGROVE_LEAVES,
            Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES, Blocks.CHERRY_LEAVES, Blocks.PALE_OAK_LEAVES,
            Blocks.RED_POPLAR_LEAVES, Blocks.ORANGE_POPLAR_LEAVES, Blocks.YELLOW_POPLAR_LEAVES);

    @Override
    public void onInitializeClient() {
        SpriteSourceRegistry.register(Identifier.fromNamespaceAndPath("colorfulleaves", "leaf_layers"),
                LeafLayers.MAP_CODEC);
        for (CustomPacketPayload.Type<ChunkColors> type : List.of(ChunkColors.CHUNK, ChunkColors.LEAVES)) {
            PayloadTypeRegistry.clientboundPlay().register(type, ChunkColors.codec(type));
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> LeafColors.set(context.client(), payload));
        }
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> LeafColors.forget(chunk.getPos()));
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> LeafColors.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> LeafColors.clear());
        ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register((model, context) ->
                LEAVES.contains(context.state().getBlock()) ? new LeafModel(model, context.state().getBlock()) : model));

        // Once the game has set up its own tints, so that leaves without a colour from the
        // server look exactly as they would without the mod.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            BlockColors colors = client.getBlockColors();
            for (Block leaves : LEAVES) {
                List<BlockTintSource> own = colors.getTintSources(leaves.defaultBlockState());
                // Painted leaves have no tint of their own: white leaves them as they are.
                BlockTintSource vanilla = own.isEmpty() ? BlockTintSources.constant(-1) : own.getFirst();
                List<BlockTintSource> sources = new ArrayList<>();
                for (int layer = 0; layer <= LeafColors.BLOSSOMS; layer++) {
                    sources.add(new LeafTint(vanilla, layer));
                }
                colors.register(sources, leaves);
            }
            List<BlockTintSource> bush = new ArrayList<>(colors.getTintSources(Blocks.BUSH.defaultBlockState()));
            if (!bush.isEmpty()) {
                bush.set(0, new MossBush(bush.getFirst()));
                colors.register(bush, Blocks.BUSH);
            }
        });
    }

    /** A bush's own tint, unless it stands on moss. */
    private record MossBush(BlockTintSource vanilla) implements BlockTintSource {

        /** The bush texture comes out at the moss blocks' own average colour with these. */
        private static final int ON_MOSS = 0xFFBEE861;
        private static final int ON_PALE_MOSS = 0xFFE3EDDF;

        @Override
        public int color(BlockState state) {
            return vanilla.color(state);
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            int moss = moss(level, pos);
            return moss != 0 ? moss : vanilla.colorInWorld(state, level, pos);
        }

        @Override
        public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            int moss = moss(level, pos);
            return moss != 0 ? moss : vanilla.colorAsTerrainParticle(state, level, pos);
        }

        @Override
        public Set<Property<?>> relevantProperties() {
            return vanilla.relevantProperties();
        }

        private static int moss(BlockAndTintGetter level, BlockPos pos) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(Blocks.MOSS_BLOCK) ? ON_MOSS : below.is(Blocks.PALE_MOSS_BLOCK) ? ON_PALE_MOSS : 0;
        }
    }

    /**
     * A leaf's own tint, unless the server gave the leaf at that spot a colour: then that of a
     * layer, a class of leaves or the blossoms.
     */
    private record LeafTint(BlockTintSource vanilla, int layer) implements BlockTintSource {

        @Override
        public int color(BlockState state) {
            return vanilla.color(state);
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            Look look = LeafColors.look(pos);
            return look != null ? look.tint(layer, pos) : vanilla.colorInWorld(state, level, pos);
        }

        @Override
        public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            Look look = LeafColors.look(pos);
            return look != null ? look.tint(layer, pos) : vanilla.colorAsTerrainParticle(state, level, pos);
        }

        @Override
        public Set<Property<?>> relevantProperties() {
            return vanilla.relevantProperties();
        }
    }
}
