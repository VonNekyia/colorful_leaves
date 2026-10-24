package com.nekyia.colorfulleaves;

import com.nekyia.colorfulleaves.LeafColors.Look;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadView;
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
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A leaf model that draws the leaves the server coloured on the copies {@link LeafLayers}
 * makes of their texture: the same quads, moved over to where a copy sits on the atlas, in
 * layers with their own tints - the whole leaves, or with a secondary colour their three
 * classes; over them the blossoms, or the flowers of flowering azalea as they are.
 */
record LeafModel(BlockStateModel model, Identifier texture) implements BlockStateModel {

    LeafModel(BlockStateModel model, Block leaves) {
        this(model, BuiltInRegistries.BLOCK.getKey(leaves).withPrefix("block/"));
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state,
                          RandomSource random, Predicate<Direction> cullTest) {
        Look look = LeafColors.look(pos);
        TextureAtlasSprite whole = look == null ? null : copy(look.bright() ? LeafLayers.BRIGHT : LeafLayers.LEAVES);
        if (whole == null) {
            model.emitQuads(emitter, level, pos, state, random, cullTest);
            return;
        }
        // Opaque leaves put every quad on the solid layer, where each would cover the one
        // below: there the leaves come whole, in their colour, flowers and all.
        if (!Minecraft.getInstance().options.cutoutLeaves().get()) {
            emitOn(whole, 0, true, emitter, level, pos, state, random, cullTest);
            return;
        }
        TextureAtlasSprite[] parts = new TextureAtlasSprite[3];
        for (int part = 0; part < 3 && look.secondary() != 0; part++) {
            parts[part] = copy(LeafLayers.PART + part + (look.bright() ? "_bright" : ""));
        }
        if (parts[2] != null) {
            for (int part = 0; part < 3; part++) {
                emitOn(parts[part], part, part == 0, emitter, level, pos, state, random, cullTest);
            }
        } else {
            emitOn(whole, 0, true, emitter, level, pos, state, random, cullTest);
        }
        TextureAtlasSprite over = look.blossom() != 0 ? copy(LeafLayers.BLOSSOMS) : copy(LeafLayers.FLOWERS);
        if (over != null) {
            emitOn(over, look.blossom() != 0 ? LeafColors.BLOSSOMS : -1, false, emitter, level, pos, state, random, cullTest);
        }
    }

    /**
     * The model's quads on {@code to} instead of the leaves' own texture, with that tint.
     * Quads on another texture - a resource pack's model may bring its own - stay as they
     * are, drawn on the {@code first} layer only.
     */
    private void emitOn(TextureAtlasSprite to, int tintIndex, boolean first, QuadEmitter emitter,
                        BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
                        Predicate<Direction> cullTest) {
        TextureAtlasSprite from = sprite(texture);
        float du = to.getU0() - from.getU0();
        float dv = to.getV0() - from.getV0();
        emitter.pushTransform(quad -> {
            if (!on(from, quad)) {
                return first;
            }
            for (int vertex = 0; vertex < 4; vertex++) {
                quad.uv(vertex, quad.u(vertex) + du, quad.v(vertex) + dv);
            }
            quad.tintIndex(tintIndex);
            return true;
        });
        model.emitQuads(emitter, level, pos, state, random, cullTest);
        emitter.popTransform();
    }

    /** Whether the quad's corners all lie on this sprite of the atlas. */
    private static boolean on(TextureAtlasSprite sprite, QuadView quad) {
        // Far below a texel, for corners baked a rounding off the sprite's edge.
        float slack = 1e-5f;
        for (int vertex = 0; vertex < 4; vertex++) {
            float u = quad.u(vertex);
            float v = quad.v(vertex);
            if (u < sprite.getU0() - slack || u > sprite.getU1() + slack
                    || v < sprite.getV0() - slack || v > sprite.getV1() + slack) {
                return false;
            }
        }
        return true;
    }

    /** A copy of the leaves' texture, or null where a resource pack left it out. */
    private @Nullable TextureAtlasSprite copy(String suffix) {
        TextureAtlasSprite sprite = sprite(texture.withSuffix(suffix));
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? null : sprite;
    }

    private static TextureAtlasSprite sprite(Identifier texture) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture);
    }

    /** Coloured leaves look unlike the plain ones, so neither may stand in for the other. */
    @Override
    public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                              RandomSource random) {
        return LeafColors.look(pos) != null ? null : model.createGeometryKey(level, pos, state, random);
    }

    /** Broken leaves crumble into bits of the copy they are drawn on, tinted as they are. */
    @Override
    public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Material.Baked own = model.particleMaterial(level, pos, state);
        Look look = LeafColors.look(pos);
        TextureAtlasSprite copy = look == null ? null : copy(look.bright() ? LeafLayers.BRIGHT : LeafLayers.LEAVES);
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
