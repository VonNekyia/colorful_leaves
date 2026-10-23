package com.nekyia.colorfulleaves;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.client.resources.palette.Palette;
import net.minecraft.client.resources.palette.PaletteMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The copies of a leaf texture that coloured leaves are drawn with, made while the atlas is
 * stitched:
 * <ul>
 * <li>{@code _colorfulleaves_leaves}: the leaves in grey - leaves with their colour painted in
 *     through the palette's grey - their highlights halfway to white;
 * <li>{@code _colorfulleaves_bright}: the same on the palette's lighter greys;
 * <li>{@code _colorfulleaves_part0} to {@code 2}, each also {@code _bright}: the same split
 *     into the three classes of leaves of the mask;
 * <li>{@code _colorfulleaves_blossoms}: the blossoms of the mask;
 * <li>{@code _colorfulleaves_flowers}: what the grey palette leaves out, as it is - the
 *     flowers of flowering azalea. In the copies above those pixels keep their colour.
 * </ul>
 * The masks are in textures/masks, {@code <mask>_parts}, {@code _highlights} and
 * {@code _blossoms}, see masks.py; a texture of another size takes them scaled. A texture
 * that is not square - an animation from a resource pack - gets no copies, and its leaves
 * stay as they are.
 */
record LeafLayers(Identifier texture, Identifier palette, Identifier mask) implements SpriteSource {

    static final MapCodec<LeafLayers> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("texture").forGetter(LeafLayers::texture),
            Identifier.CODEC.fieldOf("palette").forGetter(LeafLayers::palette),
            Identifier.CODEC.fieldOf("mask").forGetter(LeafLayers::mask)
    ).apply(i, LeafLayers::new));

    static final String LEAVES = "_colorfulleaves_leaves";
    static final String BRIGHT = "_colorfulleaves_bright";
    static final String PART = "_colorfulleaves_part";
    static final String BLOSSOMS = "_colorfulleaves_blossoms";
    static final String FLOWERS = "_colorfulleaves_flowers";

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void run(ResourceManager resources, Output output) {
        Optional<Resource> resource = resources.getResource(TEXTURE_ID_CONVERTER.idToFile(texture));
        if (resource.isEmpty()) {
            LOGGER.warn("Unable to find texture {}", texture);
            return;
        }
        int size;
        int[] own;
        Optional<TextureMetadataSection> info;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            size = image.getWidth();
            if (image.getHeight() != size) {
                return;
            }
            own = pixels(image, size);
            // The copies mipmap as the leaves do: dark_cutout keeps them from thinning out far off.
            info = resource.get().metadata().getSection(TextureMetadataSection.TYPE);
        } catch (IOException e) {
            LOGGER.error("Unable to read {}", texture, e);
            return;
        }
        IntUnaryOperator grey = mapping(resources, "_grey");
        IntUnaryOperator bright = mapping(resources, "_bright");
        int[] parts = mask(resources, "_parts", size);
        int[] highlights = mask(resources, "_highlights", size);
        int[] blossoms = mask(resources, "_blossoms", size);

        int[][] copies = new int[8][size * size];  // leaves, bright, part0..2, part0..2 bright
        int[] flowers = new int[size * size];
        boolean anyFlowers = false;
        for (int i = 0; i < own.length; i++) {
            int colour = own[i];
            if (ARGB.alpha(colour) == 0) {
                continue;
            }
            int plain = grey.applyAsInt(colour);
            int light = bright.applyAsInt(colour);
            if (ARGB.alpha(plain) == 0) {
                flowers[i] = colour;
                anyFlowers = true;
                plain = colour;
                light = colour;
            } else if (ARGB.alpha(highlights[i]) != 0) {
                plain = halfwayToWhite(plain);
                light = halfwayToWhite(light);
            }
            int part = part(parts[i]);
            copies[0][i] = plain;
            copies[1][i] = light;
            copies[2 + part][i] = plain;
            copies[5 + part][i] = light;
        }

        add(output, LEAVES, copies[0], size, info);
        add(output, BRIGHT, copies[1], size, info);
        for (int part = 0; part < 3; part++) {
            add(output, PART + part, copies[2 + part], size, info);
            add(output, PART + part + "_bright", copies[5 + part], size, info);
        }
        add(output, BLOSSOMS, blossoms, size, info);
        if (anyFlowers) {
            add(output, FLOWERS, flowers, size, info);
        }
    }

    /** The class a pixel of the parts mask stands for: red 0, green 1, blue 2; anything else 0. */
    private static int part(int mask) {
        if (ARGB.alpha(mask) == 0) {
            return 0;
        }
        int red = ARGB.red(mask);
        int green = ARGB.green(mask);
        int blue = ARGB.blue(mask);
        return blue > red && blue > green ? 2 : green > red ? 1 : 0;
    }

    private static int halfwayToWhite(int colour) {
        return ARGB.color(ARGB.alpha(colour), (ARGB.red(colour) + 256) / 2, (ARGB.green(colour) + 256) / 2,
                (ARGB.blue(colour) + 256) / 2);
    }

    /** The palette's colours for each of the key's, or the colours as they are without that palette. */
    private IntUnaryOperator mapping(ResourceManager resources, String suffix) {
        @Nullable Palette key = palette(resources, palette);
        @Nullable Palette target = palette(resources, palette.withSuffix(suffix));
        return key == null || target == null ? colour -> colour : PaletteMapping.create(key, target);
    }

    private static @Nullable Palette palette(ResourceManager resources, Identifier id) {
        Optional<Resource> resource = resources.getResource(Palette.ID_CONVERTER.idToFile(id));
        try {
            return resource.isEmpty() ? null : Palette.load(resource.get());
        } catch (IOException e) {
            LOGGER.error("Unable to read palette {}", id, e);
            return null;
        }
    }

    /** A layer of the mask, scaled to the texture; all transparent without one. */
    private int[] mask(ResourceManager resources, String suffix, int size) {
        Identifier id = mask.withPath(path -> "textures/masks/" + path + suffix + ".png");
        Optional<Resource> resource = resources.getResource(id);
        if (resource.isEmpty()) {
            return new int[size * size];
        }
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            int[] scaled = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    scaled[y * size + x] = image.getPixel(x * image.getWidth() / size, y * image.getHeight() / size);
                }
            }
            return scaled;
        } catch (IOException e) {
            LOGGER.error("Unable to read mask {}", id, e);
            return new int[size * size];
        }
    }

    private static int[] pixels(NativeImage image, int size) {
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = image.getPixel(x, y);
            }
        }
        return pixels;
    }

    private void add(Output output, String suffix, int[] pixels, int size, Optional<TextureMetadataSection> info) {
        Identifier id = texture.withSuffix(suffix);
        output.add(id, loader -> {
            NativeImage image = new NativeImage(size, size, false);
            for (int i = 0; i < pixels.length; i++) {
                image.setPixel(i % size, i / size, pixels[i]);
            }
            return new SpriteContents(id, new FrameSize(size, size), image, Optional.empty(), List.of(), info);
        });
    }

    @Override
    public MapCodec<LeafLayers> codec() {
        return MAP_CODEC;
    }
}
