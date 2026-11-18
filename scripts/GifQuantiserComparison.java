import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class GifQuantiserComparison {

    private static final int PALETTE_SIZE = 256;
    private static final int LABEL_HEIGHT = 44;
    private static final int GUTTER = 12;

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) {
            System.err.println("usage: java scripts/GifQuantiserComparison.java <photo> <out-folder>");
            System.exit(2);
        }
        File out = new File(arguments[1]);
        out.mkdirs();
        BufferedImage photo = asRgb(ImageIO.read(new File(arguments[0])));
        writeSheet(scaleUp(photo, 2), new File(out, "1-photo.png"));
        writeSheet(gradient(512, 512), new File(out, "2-gradient.png"));
        writeSheet(skyAndSkin(512, 512), new File(out, "3-soft-shading.png"));
    }

    private static void writeSheet(BufferedImage source, File into) throws IOException {
        Map<String, Function<BufferedImage, BufferedImage>> candidates = new java.util.LinkedHashMap<>();
        candidates.put("A  original, 16M colours", image -> image);
        candidates.put("B  ImageIO today", GifQuantiserComparison::throughImageIo);
        candidates.put("C  octree", image -> remap(image, octreePalette(image), false));
        candidates.put("D  median cut", image -> remap(image, medianCutPalette(image), false));
        candidates.put("E  octree + dither", image -> remap(image, octreePalette(image), true));
        candidates.put("F  median cut + dither", image -> remap(image, medianCutPalette(image), true));

        int columns = 2;
        int rows = (candidates.size() + 1) / columns;
        int tileWide = source.getWidth();
        int tileHigh = source.getHeight() + LABEL_HEIGHT;
        BufferedImage sheet = new BufferedImage(columns * tileWide + (columns + 1) * GUTTER,
                rows * tileHigh + (rows + 1) * GUTTER, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = sheet.createGraphics();
        pen.setColor(Color.WHITE);
        pen.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        pen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        pen.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        int index = 0;
        for (Map.Entry<String, Function<BufferedImage, BufferedImage>> candidate : candidates.entrySet()) {
            int x = GUTTER + (index % columns) * (tileWide + GUTTER);
            int y = GUTTER + (index / columns) * (tileHigh + GUTTER);
            pen.setColor(Color.BLACK);
            pen.drawString(candidate.getKey(), x, y + 32);
            pen.drawImage(candidate.getValue().apply(source), x, y + LABEL_HEIGHT, null);
            index++;
        }
        pen.dispose();
        ImageIO.write(sheet, "png", into);
        System.out.println("wrote " + into);
    }

    private static BufferedImage asRgb(BufferedImage read) {
        BufferedImage rgb = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = rgb.createGraphics();
        pen.drawImage(read, 0, 0, null);
        pen.dispose();
        return rgb;
    }

    private static BufferedImage scaleUp(BufferedImage image, int factor) {
        BufferedImage bigger = new BufferedImage(image.getWidth() * factor, image.getHeight() * factor,
                BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < bigger.getHeight(); y++) {
            for (int x = 0; x < bigger.getWidth(); x++) {
                bigger.setRGB(x, y, image.getRGB(x / factor, y / factor));
            }
        }
        return bigger;
    }

    private static BufferedImage gradient(int wide, int high) {
        BufferedImage image = new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                float hue = (float) x / wide;
                float brightness = 1f - (float) y / high * 0.9f;
                float saturation = 0.35f + 0.65f * (float) y / high;
                image.setRGB(x, y, Color.HSBtoRGB(hue, saturation, brightness));
            }
        }
        return image;
    }

    private static BufferedImage skyAndSkin(int wide, int high) {
        BufferedImage image = new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                double sky = (double) y / high;
                int red = (int) (40 + 120 * sky);
                int green = (int) (90 + 100 * sky);
                int blue = (int) (200 + 50 * sky);
                double dx = x - wide * 0.5;
                double dy = y - high * 0.62;
                double distance = Math.sqrt(dx * dx + dy * dy) / (wide * 0.3);
                if (distance < 1) {
                    double light = 0.55 + 0.45 * Math.cos(distance * Math.PI / 2) - 0.25 * (dx + dy) / wide;
                    red = (int) Math.min(255, 235 * light);
                    green = (int) Math.min(255, 180 * light);
                    blue = (int) Math.min(255, 150 * light);
                }
                image.setRGB(x, y, (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue));
            }
        }
        return image;
    }

    private static BufferedImage throughImageIo(BufferedImage image) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "gif", bytes);
            return asRgb(ImageIO.read(new ByteArrayInputStream(bytes.toByteArray())));
        } catch (IOException failed) {
            throw new IllegalStateException(failed);
        }
    }

    private static Map<Integer, Integer> countColours(BufferedImage image) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                counts.merge(image.getRGB(x, y) & 0xFFFFFF, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static final class OctreeNode {
        final OctreeNode[] children = new OctreeNode[8];
        final int level;
        long red;
        long green;
        long blue;
        long pixels;
        boolean leaf;

        OctreeNode(int level) {
            this.level = level;
            this.leaf = level == 8;
        }
    }

    private static int[] octreePalette(BufferedImage image) {
        OctreeNode root = new OctreeNode(0);
        List<List<OctreeNode>> reducible = new ArrayList<>();
        for (int level = 0; level < 8; level++) {
            reducible.add(new ArrayList<>());
        }
        int[] leaves = {0};
        for (Map.Entry<Integer, Integer> colour : countColours(image).entrySet()) {
            insert(root, colour.getKey(), colour.getValue(), reducible, leaves);
            while (leaves[0] > PALETTE_SIZE) {
                reduce(reducible, leaves);
            }
        }
        List<Integer> palette = new ArrayList<>();
        collectLeaves(root, palette);
        return palette.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void insert(OctreeNode root, int rgb, int count, List<List<OctreeNode>> reducible, int[] leaves) {
        OctreeNode node = root;
        while (!node.leaf) {
            int shift = 7 - node.level;
            int index = (((rgb >> (16 + shift)) & 1) << 2) | (((rgb >> (8 + shift)) & 1) << 1) | ((rgb >> shift) & 1);
            if (node.children[index] == null) {
                OctreeNode child = new OctreeNode(node.level + 1);
                node.children[index] = child;
                if (child.leaf) {
                    leaves[0]++;
                } else {
                    reducible.get(child.level).add(child);
                }
            }
            node = node.children[index];
        }
        node.red += (long) ((rgb >> 16) & 0xFF) * count;
        node.green += (long) ((rgb >> 8) & 0xFF) * count;
        node.blue += (long) (rgb & 0xFF) * count;
        node.pixels += count;
    }

    private static void reduce(List<List<OctreeNode>> reducible, int[] leaves) {
        for (int level = 7; level >= 1; level--) {
            List<OctreeNode> candidates = reducible.get(level);
            if (candidates.isEmpty()) {
                continue;
            }
            OctreeNode fewest = candidates.stream()
                    .min(Comparator.comparingLong(GifQuantiserComparison::pixelsBeneath)).orElseThrow();
            candidates.remove(fewest);
            int merged = 0;
            for (int index = 0; index < 8; index++) {
                OctreeNode child = fewest.children[index];
                if (child != null) {
                    fewest.red += child.red;
                    fewest.green += child.green;
                    fewest.blue += child.blue;
                    fewest.pixels += child.pixels;
                    fewest.children[index] = null;
                    merged++;
                }
            }
            fewest.leaf = true;
            leaves[0] -= merged - 1;
            return;
        }
    }

    private static long pixelsBeneath(OctreeNode node) {
        long total = node.pixels;
        for (OctreeNode child : node.children) {
            if (child != null) {
                total += pixelsBeneath(child);
            }
        }
        return total;
    }

    private static void collectLeaves(OctreeNode node, List<Integer> palette) {
        if (node.leaf) {
            if (node.pixels > 0) {
                palette.add(packed(node.red / node.pixels, node.green / node.pixels, node.blue / node.pixels));
            }
            return;
        }
        for (OctreeNode child : node.children) {
            if (child != null) {
                collectLeaves(child, palette);
            }
        }
    }

    private record WeightedColour(int rgb, int count) {
        int channel(int which) {
            return (rgb >> (16 - 8 * which)) & 0xFF;
        }
    }

    private static int[] medianCutPalette(BufferedImage image) {
        List<List<WeightedColour>> boxes = new ArrayList<>();
        List<WeightedColour> all = new ArrayList<>();
        countColours(image).forEach((rgb, count) -> all.add(new WeightedColour(rgb, count)));
        boxes.add(all);
        while (boxes.size() < PALETTE_SIZE) {
            List<WeightedColour> widest = null;
            long widestScore = 0;
            for (List<WeightedColour> box : boxes) {
                if (box.size() < 2) {
                    continue;
                }
                long score = (long) widestRange(box)[1] * pixels(box);
                if (score > widestScore) {
                    widestScore = score;
                    widest = box;
                }
            }
            if (widest == null) {
                break;
            }
            int channel = widestRange(widest)[0];
            widest.sort(Comparator.comparingInt(colour -> colour.channel(channel)));
            long half = pixels(widest) / 2;
            long seen = 0;
            int cut = 1;
            for (int index = 0; index < widest.size() - 1; index++) {
                seen += widest.get(index).count();
                if (seen >= half) {
                    cut = index + 1;
                    break;
                }
            }
            boxes.remove(widest);
            boxes.add(new ArrayList<>(widest.subList(0, cut)));
            boxes.add(new ArrayList<>(widest.subList(cut, widest.size())));
        }
        int[] palette = new int[boxes.size()];
        for (int index = 0; index < boxes.size(); index++) {
            long red = 0;
            long green = 0;
            long blue = 0;
            long total = pixels(boxes.get(index));
            for (WeightedColour colour : boxes.get(index)) {
                red += (long) colour.channel(0) * colour.count();
                green += (long) colour.channel(1) * colour.count();
                blue += (long) colour.channel(2) * colour.count();
            }
            palette[index] = packed(red / total, green / total, blue / total);
        }
        return palette;
    }

    private static int[] widestRange(List<WeightedColour> box) {
        int bestChannel = 0;
        int bestRange = -1;
        for (int channel = 0; channel < 3; channel++) {
            int low = 255;
            int high = 0;
            for (WeightedColour colour : box) {
                low = Math.min(low, colour.channel(channel));
                high = Math.max(high, colour.channel(channel));
            }
            if (high - low > bestRange) {
                bestRange = high - low;
                bestChannel = channel;
            }
        }
        return new int[] {bestChannel, bestRange};
    }

    private static long pixels(List<WeightedColour> box) {
        long total = 0;
        for (WeightedColour colour : box) {
            total += colour.count();
        }
        return total;
    }

    private static BufferedImage remap(BufferedImage image, int[] palette, boolean dither) {
        int wide = image.getWidth();
        int high = image.getHeight();
        float[][] error = new float[3][wide * high];
        BufferedImage mapped = new BufferedImage(wide, high, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                int at = y * wide + x;
                int rgb = image.getRGB(x, y);
                float red = ((rgb >> 16) & 0xFF) + error[0][at];
                float green = ((rgb >> 8) & 0xFF) + error[1][at];
                float blue = (rgb & 0xFF) + error[2][at];
                int chosen = nearest(palette, clamp(Math.round(red)), clamp(Math.round(green)), clamp(Math.round(blue)));
                if (dither) {
                    float[] off = {red - ((chosen >> 16) & 0xFF), green - ((chosen >> 8) & 0xFF), blue - (chosen & 0xFF)};
                    spread(error, off, x + 1, y, wide, high, 7f / 16);
                    spread(error, off, x - 1, y + 1, wide, high, 3f / 16);
                    spread(error, off, x, y + 1, wide, high, 5f / 16);
                    spread(error, off, x + 1, y + 1, wide, high, 1f / 16);
                }
                mapped.setRGB(x, y, chosen);
            }
        }
        return mapped;
    }

    private static void spread(float[][] error, float[] off, int x, int y, int wide, int high, float share) {
        if (x < 0 || x >= wide || y >= high) {
            return;
        }
        int at = y * wide + x;
        for (int channel = 0; channel < 3; channel++) {
            error[channel][at] += off[channel] * share;
        }
    }

    private static int nearest(int[] palette, int red, int green, int blue) {
        int best = palette[0];
        int bestDistance = Integer.MAX_VALUE;
        for (int candidate : palette) {
            int dr = ((candidate >> 16) & 0xFF) - red;
            int dg = ((candidate >> 8) & 0xFF) - green;
            int db = (candidate & 0xFF) - blue;
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static int packed(long red, long green, long blue) {
        return (int) ((red << 16) | (green << 8) | blue);
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }
}
