package org.jebol.domain.image;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MedianCutQuantiser {

    private static final int CHANNELS = 3;
    private static final int RGB = 0xFFFFFF;
    private static final float RIGHT = 7f / 16;
    private static final float BELOW_LEFT = 3f / 16;
    private static final float BELOW = 5f / 16;
    private static final float BELOW_RIGHT = 1f / 16;

    private MedianCutQuantiser() {
    }

    public static int[] choosePalette(int[] rgb, int colours) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int pixel : rgb) {
            counts.merge(pixel & RGB, 1, Integer::sum);
        }
        int[] distinct = counts.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
        List<Box> boxes = new ArrayList<>();
        Box everything = new Box(new int[distinct.length], new int[distinct.length]);
        for (int at = 0; at < distinct.length; at++) {
            everything.colours[at] = distinct[at];
            everything.counts[at] = counts.get(distinct[at]);
        }
        everything.measure();
        boxes.add(everything);
        while (boxes.size() < colours) {
            Box widest = null;
            for (Box box : boxes) {
                if (box.colours.length > 1 && (widest == null || box.score() > widest.score())) {
                    widest = box;
                }
            }
            if (widest == null) {
                break;
            }
            int index = boxes.indexOf(widest);
            Box[] halves = widest.split();
            boxes.set(index, halves[0]);
            boxes.add(index + 1, halves[1]);
        }
        int[] palette = new int[boxes.size()];
        for (int at = 0; at < palette.length; at++) {
            palette[at] = boxes.get(at).mean();
        }
        return palette;
    }

    public static byte[] ditherOnto(int[] rgb, int wide, int high, int[] palette) {
        byte[] indices = new byte[wide * high];
        float[] errorRed = new float[wide * high];
        float[] errorGreen = new float[wide * high];
        float[] errorBlue = new float[wide * high];
        Map<Integer, Integer> nearestFound = new HashMap<>();
        for (int down = 0; down < high; down++) {
            for (int across = 0; across < wide; across++) {
                int at = down * wide + across;
                int pixel = rgb[at];
                float red = ((pixel >> 16) & 0xFF) + errorRed[at];
                float green = ((pixel >> 8) & 0xFF) + errorGreen[at];
                float blue = (pixel & 0xFF) + errorBlue[at];
                int wanted = (clamp(Math.round(red)) << 16) | (clamp(Math.round(green)) << 8) | clamp(Math.round(blue));
                int chosen = nearestFound.computeIfAbsent(wanted, colour -> nearest(palette, colour));
                indices[at] = (byte) chosen;
                int landed = palette[chosen];
                float offRed = red - ((landed >> 16) & 0xFF);
                float offGreen = green - ((landed >> 8) & 0xFF);
                float offBlue = blue - (landed & 0xFF);
                spread(errorRed, errorGreen, errorBlue, offRed, offGreen, offBlue, across + 1, down, wide, high, RIGHT);
                spread(errorRed, errorGreen, errorBlue, offRed, offGreen, offBlue, across - 1, down + 1, wide, high,
                        BELOW_LEFT);
                spread(errorRed, errorGreen, errorBlue, offRed, offGreen, offBlue, across, down + 1, wide, high, BELOW);
                spread(errorRed, errorGreen, errorBlue, offRed, offGreen, offBlue, across + 1, down + 1, wide, high,
                        BELOW_RIGHT);
            }
        }
        return indices;
    }

    private static void spread(float[] red, float[] green, float[] blue, float offRed, float offGreen,
            float offBlue, int across, int down, int wide, int high, float share) {
        if (across < 0 || across >= wide || down >= high) {
            return;
        }
        int at = down * wide + across;
        red[at] += offRed * share;
        green[at] += offGreen * share;
        blue[at] += offBlue * share;
    }

    private static int nearest(int[] palette, int colour) {
        int red = (colour >> 16) & 0xFF;
        int green = (colour >> 8) & 0xFF;
        int blue = colour & 0xFF;
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int at = 0; at < palette.length; at++) {
            int dr = ((palette[at] >> 16) & 0xFF) - red;
            int dg = ((palette[at] >> 8) & 0xFF) - green;
            int db = (palette[at] & 0xFF) - blue;
            int distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = at;
            }
        }
        return best;
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(0xFF, channel));
    }

    private static int channel(int colour, int which) {
        return (colour >> (16 - 8 * which)) & 0xFF;
    }

    private static final class Box {

        private final int[] colours;
        private final int[] counts;
        private long pixels;
        private int widestChannel;
        private int widestRange;

        private Box(int[] colours, int[] counts) {
            this.colours = colours;
            this.counts = counts;
        }

        private void measure() {
            pixels = 0;
            for (int count : counts) {
                pixels += count;
            }
            widestRange = -1;
            for (int which = 0; which < CHANNELS; which++) {
                int low = 0xFF;
                int highest = 0;
                for (int colour : colours) {
                    low = Math.min(low, channel(colour, which));
                    highest = Math.max(highest, channel(colour, which));
                }
                if (highest - low > widestRange) {
                    widestRange = highest - low;
                    widestChannel = which;
                }
            }
        }

        private long score() {
            return widestRange * pixels;
        }

        private Box[] split() {
            Integer[] order = new Integer[colours.length];
            for (int at = 0; at < order.length; at++) {
                order[at] = at;
            }
            Arrays.sort(order, Comparator.<Integer>comparingInt(at -> channel(colours[at], widestChannel))
                    .thenComparingInt(at -> colours[at]));
            long half = pixels / 2;
            long seen = 0;
            int cut = 1;
            for (int at = 0; at < order.length - 1; at++) {
                seen += counts[order[at]];
                if (seen >= half) {
                    cut = at + 1;
                    break;
                }
            }
            return new Box[] {part(order, 0, cut), part(order, cut, order.length)};
        }

        private Box part(Integer[] order, int from, int to) {
            Box part = new Box(new int[to - from], new int[to - from]);
            for (int at = from; at < to; at++) {
                part.colours[at - from] = colours[order[at]];
                part.counts[at - from] = counts[order[at]];
            }
            part.measure();
            return part;
        }

        private int mean() {
            long red = 0;
            long green = 0;
            long blue = 0;
            for (int at = 0; at < colours.length; at++) {
                red += (long) channel(colours[at], 0) * counts[at];
                green += (long) channel(colours[at], 1) * counts[at];
                blue += (long) channel(colours[at], 2) * counts[at];
            }
            return (int) ((red / pixels) << 16 | (green / pixels) << 8 | (blue / pixels));
        }
    }
}
