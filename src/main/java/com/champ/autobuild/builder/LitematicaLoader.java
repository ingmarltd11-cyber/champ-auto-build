package com.champ.autobuild.builder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;











public final class LitematicaLoader {
    private static final int MAX_BLOCKS = 2_000_000;

    private LitematicaLoader() {
    }

    public static Schematic load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("File not found: " + path);
        }
        NbtCompound root = readRoot(path);
        return parse(root, path.getFileName().toString());
    }

    





    private static NbtCompound readRoot(Path path) throws IOException {
        byte[] head = new byte[4];
        int read;
        try (InputStream in = Files.newInputStream(path)) {
            read = in.readNBytes(head, 0, head.length);
        }
        boolean gzip = read >= 2 && (head[0] & 0xFF) == 0x1F && (head[1] & 0xFF) == 0x8B;
        boolean zip = read >= 4 && head[0] == 'P' && head[1] == 'K'
            && (head[2] == 3 || head[2] == 5) && (head[3] == 4 || head[3] == 6);
        if (zip) return readZip(path);
        if (gzip) return NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
        try {
            return NbtIo.read(path);
        } catch (IOException plain) {
            try {
                return NbtIo.readCompressed(path, NbtSizeTracker.ofUnlimitedBytes());
            } catch (IOException compressed) {
                plain.addSuppressed(compressed);
                throw plain;
            }
        }
    }

    
    private static NbtCompound readZip(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry best = null;
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (!(name.endsWith(".litematic") || name.endsWith(".schematic")
                    || name.endsWith(".schem") || name.endsWith(".nbt"))) continue;
                best = entry;
                if (name.endsWith(".litematic")) break;
            }
            if (best == null) throw new IOException("Zip contains no schematic file");
            byte[] bytes;
            try (InputStream in = zip.getInputStream(best)) {
                bytes = in.readAllBytes();
            }
            if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B) {
                try (DataInputStream in = new DataInputStream(
                        new GZIPInputStream(new ByteArrayInputStream(bytes)))) {
                    return NbtIo.readCompound(in, NbtSizeTracker.ofUnlimitedBytes());
                }
            }
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                return NbtIo.readCompound(in, NbtSizeTracker.ofUnlimitedBytes());
            }
        }
    }

    
    public static List<Path> list() {
        List<Path> out = new ArrayList<>();
        try {
            Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("schematics");
            if (!Files.isDirectory(dir)) return out;
            try (Stream<Path> walk = Files.walk(dir, 4)) {
                walk.filter(Files::isRegularFile)
                    .filter(file -> {
                        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".litematic") || name.endsWith(".schem");
                    })
                    .sorted()
                    .forEach(out::add);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    
    public static Schematic parse(NbtCompound root, String fallbackName) throws IOException {
        if (root.contains("Regions")) return parseLitematica(root, fallbackName);
        if (root.contains("Schematic") || root.contains("Palette")) return parseSponge(root, fallbackName);
        throw new IOException("Not a Litematica or Sponge schematic");
    }

    
    private static Schematic parseLitematica(NbtCompound root, String fallbackName) throws IOException {
        NbtCompound regions = root.getCompoundOrEmpty("Regions");
        List<Schematic.Entry> entries = new ArrayList<>();
        boolean truncated = false;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        boolean anyRegion = false;

        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompoundOrEmpty(regionName);
            if (region.isEmpty()) continue;

            NbtCompound posTag = region.getCompoundOrEmpty("Position");
            NbtCompound sizeTag = region.getCompoundOrEmpty("Size");
            int px = posTag.getInt("x", 0);
            int py = posTag.getInt("y", 0);
            int pz = posTag.getInt("z", 0);
            int sx = sizeTag.getInt("x", 0);
            int sy = sizeTag.getInt("y", 0);
            int sz = sizeTag.getInt("z", 0);
            if (sx == 0 || sy == 0 || sz == 0) continue;

            
            int baseX = px, baseY = py, baseZ = pz;
            if (sx < 0) {
                baseX = px + sx + 1;
                sx = -sx;
            }
            if (sy < 0) {
                baseY = py + sy + 1;
                sy = -sy;
            }
            if (sz < 0) {
                baseZ = pz + sz + 1;
                sz = -sz;
            }

            
            
            anyRegion = true;
            minX = Math.min(minX, baseX);
            minY = Math.min(minY, baseY);
            minZ = Math.min(minZ, baseZ);
            maxX = Math.max(maxX, baseX + sx - 1);
            maxY = Math.max(maxY, baseY + sy - 1);
            maxZ = Math.max(maxZ, baseZ + sz - 1);

            NbtList palette = region.getListOrEmpty("BlockStatePalette");
            if (palette.isEmpty()) continue;
            List<BlockState> states = new ArrayList<>(palette.size());
            for (int i = 0; i < palette.size(); i++) {
                NbtCompound entry = palette.getCompoundOrEmpty(i);
                String name = entry.getString("Name", "minecraft:air");
                Map<String, String> props = new HashMap<>();
                NbtCompound propsTag = entry.getCompoundOrEmpty("Properties");
                for (String key : propsTag.getKeys()) {
                    props.put(key, propsTag.getString(key, ""));
                }
                BlockState state = BlockStates.parse(name, props);
                if (state == null) {
                    BuilderLog.LOGGER.warn("Unknown block '{}' in schematic, using air", name);
                    state = net.minecraft.block.Blocks.AIR.getDefaultState();
                }
                states.add(state);
            }

            long[] packed = region.getLongArray("BlockStates").orElse(new long[0]);
            long volume = (long) sx * sy * sz;
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long mask = (1L << bits) - 1L;

            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        long index = ((long) y * sz + z) * sx + x;
                        int paletteIndex;
                        if (packed.length == 0) {
                            paletteIndex = 0;
                        } else {
                            long bitIndex = index * bits;
                            int longIndex = (int) (bitIndex >>> 6);
                            int offset = (int) (bitIndex & 63L);
                            if (longIndex >= packed.length) continue;
                            long value = packed[longIndex] >>> offset;
                            if (offset + bits > 64 && longIndex + 1 < packed.length) {
                                value |= packed[longIndex + 1] << (64 - offset);
                            }
                            paletteIndex = (int) (value & mask);
                        }
                        if (paletteIndex < 0 || paletteIndex >= states.size()) continue;
                        BlockState state = states.get(paletteIndex);
                        
                        if (state.isAir() || state.isOf(net.minecraft.block.Blocks.STRUCTURE_VOID)) continue;

                        int rx = baseX + x;
                        int ry = baseY + y;
                        int rz = baseZ + z;
                        entries.add(new Schematic.Entry(new BlockPos(rx, ry, rz), state));
                        if (entries.size() >= MAX_BLOCKS) {
                            truncated = true;
                            break;
                        }
                    }
                    if (truncated) break;
                }
                if (truncated) break;
            }
            if (truncated) break;
        }

        if (!anyRegion) {
            throw new IOException("Schematic contains no regions");
        }
        if (entries.isEmpty()) {
            throw new IOException("Schematic contains no placeable blocks");
        }
        if (truncated) {
            BuilderLog.LOGGER.warn("Schematic has more than {} blocks; only the first {} were loaded", MAX_BLOCKS, MAX_BLOCKS);
        }

        String name = root.getCompoundOrEmpty("Metadata").getString("Name", fallbackName);

        List<Schematic.Entry> shifted = new ArrayList<>(entries.size());
        for (Schematic.Entry e : entries) {
            shifted.add(new Schematic.Entry(e.rel().add(-minX, -minY, -minZ), e.state()));
        }
        BlockPos size = new BlockPos(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

        return new Schematic(name, size, shifted);
    }

    

    







    private static Schematic parseSponge(NbtCompound root, String fallbackName) throws IOException {
        NbtCompound body = root.contains("Schematic") ? root.getCompoundOrEmpty("Schematic") : root;
        int width = intOf(body, "Width");
        int height = intOf(body, "Height");
        int length = intOf(body, "Length");
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException("Sponge schematic has no size");
        }

        NbtCompound blocks = body.getCompoundOrEmpty("Blocks");
        NbtCompound paletteTag = blocks.contains("Palette")
            ? blocks.getCompoundOrEmpty("Palette") : body.getCompoundOrEmpty("Palette");
        if (paletteTag.isEmpty()) {
            throw new IOException("Sponge schematic has no palette");
        }
        byte[] data = bytesOf(blocks.contains("Data") ? blocks : body, "Data");
        if (data.length == 0 && !blocks.contains("BlockData")) {
            data = bytesOf(body, "BlockData");
        }
        NbtCompound sparse = blocks.contains("BlockData") ? blocks.getCompoundOrEmpty("BlockData")
            : body.getCompoundOrEmpty("BlockData");

        Map<Integer, BlockState> palette = new HashMap<>();
        int highest = 0;
        for (String key : paletteTag.getKeys()) {
            int id = paletteTag.getInt(key, -1);
            if (id < 0) continue;
            BlockState state = stateOf(key);
            if (state == null) {
                BuilderLog.LOGGER.warn("Unknown block '{}' in schematic, using air", key);
                state = net.minecraft.block.Blocks.AIR.getDefaultState();
            }
            palette.put(id, state);
            highest = Math.max(highest, id);
        }

        List<Schematic.Entry> entries = new ArrayList<>();
        boolean truncated = false;
        int cursor = 0;
        for (int y = 0; y < height && !truncated; y++) {
            for (int z = 0; z < length && !truncated; z++) {
                for (int x = 0; x < width; x++) {
                    int index = (y * length + z) * width + x;
                    int id;
                    if (data.length > 0) {
                        if (cursor >= data.length) { truncated = true; break; }
                        int value = 0;
                        int shift = 0;
                        int b;
                        do {
                            if (cursor >= data.length) { truncated = true; break; }
                            b = data[cursor++] & 0xFF;
                            value |= (b & 0x7F) << shift;
                            shift += 7;
                        } while ((b & 0x80) != 0 && shift < 35);
                        id = value;
                    } else {
                        id = intOf(sparse, x + "," + y + "," + z);
                    }
                    if (id < 0 || id > highest) continue;
                    BlockState state = palette.get(id);
                    if (state == null || state.isAir()
                        || state.isOf(net.minecraft.block.Blocks.STRUCTURE_VOID)) continue;
                    entries.add(new Schematic.Entry(new BlockPos(x, y, z), state));
                    if (entries.size() >= MAX_BLOCKS) { truncated = true; break; }
                }
            }
        }

        if (entries.isEmpty()) throw new IOException("Schematic contains no placeable blocks");
        if (truncated) {
            BuilderLog.LOGGER.warn("Schematic has more than {} blocks; only the first {} were loaded", MAX_BLOCKS, MAX_BLOCKS);
        }
        String name = root.getCompoundOrEmpty("Metadata").getString("Name", fallbackName);
        return new Schematic(name, new BlockPos(width, height, length), entries);
    }

    
    private static BlockState stateOf(String key) {
        int bracket = key.indexOf('[');
        if (bracket < 0) return BlockStates.parse(key, Map.of());
        String name = key.substring(0, bracket);
        String body = key.substring(bracket + 1, key.length() > bracket + 1 && key.endsWith("]")
            ? key.length() - 1 : key.length());
        Map<String, String> properties = new HashMap<>();
        for (String pair : body.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) properties.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return BlockStates.parse(name, properties);
    }

    
    private static int intOf(NbtCompound tag, String key) {
        net.minecraft.nbt.NbtElement element = tag.get(key);
        if (element instanceof net.minecraft.nbt.AbstractNbtNumber number) return number.intValue();
        return 0;
    }

    private static byte[] bytesOf(NbtCompound tag, String key) {
        return tag.getByteArray(key).orElse(new byte[0]);
    }

    



    public static Path resolve(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.startsWith("~")) {
            s = s.substring(1);
            while (s.startsWith("/") || s.startsWith("\\")) s = s.substring(1);
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".litematic") && !lower.endsWith(".schem")
            && !lower.endsWith(".schematic") && !lower.endsWith(".nbt")
            && !lower.endsWith(".zip")) {
            s = s + ".litematic";
        }
        Path p = Path.of(s);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        Path gameDir = MinecraftClient.getInstance().runDirectory.toPath();
        Path direct = gameDir.resolve(p).normalize();
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        Path inSchematics = gameDir.resolve("schematics").resolve(p).normalize();
        if (Files.isRegularFile(inSchematics)) {
            return inSchematics;
        }
        return direct;
    }

    
    public static boolean exists(String raw) {
        try {
            return Files.isRegularFile(resolve(raw));
        } catch (Exception e) {
            return false;
        }
    }

}
