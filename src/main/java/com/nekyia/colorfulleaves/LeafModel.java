package com.nekyia.colorfulleaves;

import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A leaf model that draws the leaves the server coloured on a copy of their texture: the
 * same quads, moved over to where that copy sits on the atlas. Leaves the server asked to
 * be bright go on a lighter copy. Leaves with their colour painted into the texture go on
 * a grey copy, which the tint colours as it colours oak; the flowers of flowering azalea
 * are left out of it and drawn over it as they are. The copies are made while the atlas
 * is stitched, by the palettes in assets/colorfulleaves.
 */
record LeafModel(BlockStateModel model, Identifier texture, Identifier bright, @Nullable Identifier grey,
                 @Nullable Identifier flowers) implements BlockStateModel {

    /** Leaves whose model the game tints: grey already, so only the lighter copy is needed. */
    static LeafModel tinted(BlockStateModel model, Block leaves) {
        return new LeafModel(model, texture(leaves, ""), texture(leaves, "_colorfulleaves_bright"), null, null);
    }

    /** Leaves with their colour painted in: drawn on a grey copy wherever the server gave a colour. */
    static LeafModel painted(BlockStateModel model, Block leaves) {
        return new LeafModel(model, texture(leaves, ""), texture(leaves, "_colorfulleaves_bright"),
                texture(leaves, "_colorfulleaves_grey"),
                leaves == Blocks.FLOWERING_AZALEA_LEAVES ? texture(leaves, "_colorfulleaves_flowers") : null);
    }

    private static Identifier texture(Block leaves, String suffix) {
        Identifier block = BuiltInRegistries.BLOCK.getKey(leaves);
        return block.withPath("block/" + block.getPath() + suffix);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        TextureAtlasSprite copy = copy(pos);
        if (copy == null) {
            model.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        emitOn(copy, 0, emitter, level, pos, state, random, cullTest);
        // Opaque leaves put every quad on the solid layer, where the flowers would cover the
        // leaves whole; there the grey copy shows them instead, tinted with the rest.
        if (flowers != null && Minecraft.getInstance().options.cutoutLeaves().get()) {
            TextureAtlasSprite petals = usable(sprite(flowers));
            if (petals != null) {
                emitOn(petals, -1, emitter, level, pos, state, random, cullTest);
            }
        }
    }

    /** The model's quads on {@code to} instead of the leaves' own texture, with that tint. */
    private void emitOn(TextureAtlasSprite to, int tintIndex, QuadEmitter emitter, BlockAndTintGetter level,
                        BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
        TextureAtlasSprite from = sprite(texture);
        float du = to.getU0() - from.getU0();
        float dv = to.getV0() - from.getV0();
        emitter.pushTransform(quad -> {
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.uv(vertex, quad.u(vertex) + du, quad.v(vertex) + dv);
            }
            quad.tintIndex(tintIndex);
            return true;
        });
        model.emitQuads(emitter, level, pos, state, random, cullTest);
        emitter.popTransform();
    }

    /** The copy these leaves are drawn on, or null for their own texture. */
    private @Nullable TextureAtlasSprite copy(BlockPos pos) {
        Identifier copy = LeafColors.bright(pos) ? bright : grey != null && LeafColors.tint(pos) != 0 ? grey : null;
        return copy == null ? null : usable(sprite(copy));
    }

    /** A resource pack that leaves a copy out leaves the leaves as they are. */
    private static @Nullable TextureAtlasSprite usable(TextureAtlasSprite sprite) {
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? null : sprite;
    }

    private static TextureAtlasSprite sprite(Identifier texture) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture);
    }

    /** Leaves on a copy look unlike the plain ones, so neither may stand in for the other. */
    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                              RandomSource random) {
        return copy(pos) != null ? null : model.createGeometryKey(level, pos, state, random);
    }

    /** Broken leaves crumble into bits of the copy they are drawn on, tinted as they are. */
    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Material.Baked own = model.particleMaterial(level, pos, state);
        TextureAtlasSprite copy = copy(pos);
        return copy == null ? own : new Material.Baked(copy, own.forceTranslucent());
    }

    @Override
    public void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        model.collectParts(random, parts);
    }

    @Override
    public Material.Baked particleMaterial() {
        return model.particleMaterial();
    }

    @Override
    public int materialFlags() {
        return model.materialFlags();
    }

    @Override
    public boolean hasMaterialFlag(int flag) {
        return model.hasMaterialFlag(flag);
    }
}
