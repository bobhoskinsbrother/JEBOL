package org.jebol.domain.eval.blend2d;

final class GlyfOutlines {

    private static final int ON_CURVE = 0x01;

    private static final int X_IS_BYTE = 0x02;

    private static final int Y_IS_BYTE = 0x04;

    private static final int REPEAT = 0x08;

    private static final int X_IS_SAME_OR_POSITIVE = 0x10;

    private static final int Y_IS_SAME_OR_POSITIVE = 0x20;

    private static final int IMPORTANT_FLAGS = 0x3F;

    private static final int OFF_CURVE_SPLINE = ON_CURVE | ON_CURVE << 7;

    private static final int ARGS_ARE_WORDS = 0x0001;

    private static final int ARGS_ARE_XY_VALUES = 0x0002;

    private static final int WE_HAVE_SCALE = 0x0008;

    private static final int MORE_COMPONENTS = 0x0020;

    private static final int WE_HAVE_SCALE_XY = 0x0040;

    private static final int WE_HAVE_2X2 = 0x0080;

    private static final int SCALED_COMPONENT_OFFSET = 0x0800;

    private static final int UNSCALED_COMPONENT_OFFSET = 0x1000;

    private static final int ANY_SCALE = WE_HAVE_SCALE | WE_HAVE_SCALE_XY | WE_HAVE_2X2;

    private static final int ANY_OFFSET = SCALED_COMPONENT_OFFSET | UNSCALED_COMPONENT_OFFSET;

    private static final int GLYPH_HEADER_SIZE = 10;

    private static final int DEEPEST_COMPOUND = 16;

    private static final double F2DOT14 = 1.0 / 16384.0;

    private final OpenTypeFace face;

    private final int[] componentAt = new int[DEEPEST_COMPOUND];

    private final int[] componentRemaining = new int[DEEPEST_COMPOUND];

    private final int[] componentFlags = new int[DEEPEST_COMPOUND];

    private final Blend2DMatrix[] componentTransform = new Blend2DMatrix[DEEPEST_COMPOUND];

    private OpenTypeTable glyphData;

    private int cursor;

    private int remaining;

    GlyfOutlines(OpenTypeFace face) {
        this.face = face;
    }

    private static int xSize(int flags) {
        return (flags & X_IS_BYTE) != 0 ? 1 : (flags & X_IS_SAME_OR_POSITIVE) != 0 ? 0 : 2;
    }

    private static int ySize(int flags) {
        return (flags & Y_IS_BYTE) != 0 ? 1 : (flags & Y_IS_SAME_OR_POSITIVE) != 0 ? 0 : 2;
    }

    private long[] locate(int glyphId) {
        OpenTypeTable loca = face.loca;
        long offset;
        long end;
        if (!face.hasLongLocaOffsets) {
            long index = glyphId * 2L;
            if (index + 4 > loca.size()) {
                return null;
            }
            offset = loca.readU16((int) index) * 2L;
            end = loca.readU16((int) index + 2) * 2L;
        } else {
            long index = glyphId * 4L;
            if (index + 8 > loca.size()) {
                return null;
            }
            offset = loca.readU32((int) index);
            end = loca.readU32((int) index + 4);
        }
        return new long[] {offset, end};
    }

    boolean decode(int glyphId, Blend2DMatrix transform, Blend2DPath out) {
        if (glyphId >= face.glyphCount) {
            return false;
        }
        OpenTypeTable glyf = face.glyf;
        int level = 0;
        componentFlags[0] = ARGS_ARE_XY_VALUES;
        componentTransform[0] = transform;
        int glyph = glyphId;
        while (true) {
            long[] span = locate(glyph);
            if (span == null) {
                return false;
            }
            long offset = span[0];
            long end = span[1];
            boolean isCompound = false;
            if (offset >= end || end > glyf.size()) {
                if (offset != end || end > glyf.size()) {
                    return false;
                }
            } else {
                glyphData = glyf;
                cursor = (int) offset;
                remaining = (int) (end - offset);
                if (remaining < GLYPH_HEADER_SIZE) {
                    return false;
                }
                int contours = glyf.readI16(cursor);
                if (contours > 0) {
                    if (!decodeSimple(contours, componentTransform[level], out)) {
                        return false;
                    }
                } else if (contours == -1) {
                    cursor += GLYPH_HEADER_SIZE;
                    remaining -= GLYPH_HEADER_SIZE;
                    if (++level >= DEEPEST_COMPOUND) {
                        return false;
                    }
                    isCompound = true;
                } else if (contours < -1) {
                    return false;
                }
            }
            if (!isCompound) {
                if (level == 0) {
                    return true;
                }
                while ((componentFlags[level] & MORE_COMPONENTS) == 0) {
                    if (--level == 0) {
                        return true;
                    }
                }
                cursor = componentAt[level];
                remaining = componentRemaining[level];
            }
            glyph = readComponent(level);
            if (glyph < 0) {
                return false;
            }
        }
    }

    private int readComponent(int level) {
        OpenTypeTable glyf = face.glyf;
        if (remaining < 6) {
            return -1;
        }
        remaining -= 6;
        int flags = glyf.readU16(cursor);
        int glyph = glyf.readU16(cursor + 2);
        if (glyph >= face.glyphCount) {
            return -1;
        }
        int arg1 = glyf.readI8(cursor + 4);
        int arg2 = glyf.readI8(cursor + 5);
        cursor += 6;
        if ((flags & ARGS_ARE_WORDS) != 0) {
            if (remaining < 2) {
                return -1;
            }
            remaining -= 2;
            arg1 = arg1 << 8 | (arg2 & 0xFF);
            arg2 = glyf.readI16(cursor);
            cursor += 2;
        }
        if ((flags & ARGS_ARE_XY_VALUES) == 0) {
            arg1 &= 0xFFFF;
            arg2 &= 0xFFFF;
        }
        double m00 = 1.0;
        double m01 = 0.0;
        double m10 = 0.0;
        double m11 = 1.0;
        double m20 = arg1;
        double m21 = arg2;
        if ((flags & ANY_SCALE) != 0) {
            if ((flags & WE_HAVE_SCALE) != 0) {
                if (remaining < 2) {
                    return -1;
                }
                remaining -= 2;
                double scale = glyf.readI16(cursor) * F2DOT14;
                m00 = scale;
                m11 = scale;
                cursor += 2;
            } else if ((flags & WE_HAVE_SCALE_XY) != 0) {
                if (remaining < 4) {
                    return -1;
                }
                remaining -= 4;
                m00 = glyf.readI16(cursor) * F2DOT14;
                m11 = glyf.readI16(cursor + 2) * F2DOT14;
                cursor += 4;
            } else {
                if (remaining < 8) {
                    return -1;
                }
                remaining -= 8;
                m00 = glyf.readI16(cursor) * F2DOT14;
                m01 = glyf.readI16(cursor + 2) * F2DOT14;
                m10 = glyf.readI16(cursor + 4) * F2DOT14;
                m11 = glyf.readI16(cursor + 6) * F2DOT14;
                cursor += 8;
            }
            if ((flags & (ARGS_ARE_XY_VALUES | ANY_OFFSET)) == (ARGS_ARE_XY_VALUES | SCALED_COMPONENT_OFFSET)) {
                m20 *= Math.sqrt(m00 * m00 + m01 * m01);
                m21 *= Math.sqrt(m10 * m10 + m11 * m11);
            }
        }
        componentAt[level] = cursor;
        componentRemaining[level] = remaining;
        componentFlags[level] = flags;
        componentTransform[level] = new Blend2DMatrix(m00, m01, m10, m11, m20, m21)
                .multiply(componentTransform[level - 1]);
        return glyph;
    }

    private boolean decodeSimple(int contours, Blend2DMatrix transform, Blend2DPath out) {
        OpenTypeTable glyf = glyphData;
        cursor += GLYPH_HEADER_SIZE;
        long left = (long) remaining - (GLYPH_HEADER_SIZE + contours * 2L + 2);
        if (left < 0) {
            return false;
        }
        int contourArrayAt = cursor;
        cursor += contours * 2;
        int instructions = glyf.readU16(cursor);
        left -= instructions;
        if (left < 0) {
            return false;
        }
        cursor += 2 + instructions;
        int end = (int) (cursor + left);
        int vertexCount = glyf.readU16(contourArrayAt + (contours - 1) * 2) + 1;
        if (vertexCount <= 1) {
            return true;
        }
        int[] flagData = new int[vertexCount];
        long xBytes = 0;
        long yBytes = 0;
        int at = 0;
        int previous = ON_CURVE;
        do {
            if (cursor == end) {
                return false;
            }
            int flag = glyf.readU8(cursor++) & IMPORTANT_FLAGS;
            previous = (previous << 7 | flag) & 0xFF;
            flagData[at++] = previous;
            xBytes += xSize(flag);
            yBytes += ySize(flag);
            if ((previous & REPEAT) == 0) {
                continue;
            }
            if (cursor == end) {
                return false;
            }
            int repeats = glyf.readU8(cursor++);
            if (repeats > vertexCount - at) {
                return false;
            }
            previous = (previous << 7 | flag) & 0xFF;
            xBytes += (long) repeats * xSize(flag);
            yBytes += (long) repeats * ySize(flag);
            for (int repeat = 0; repeat < repeats; repeat++) {
                flagData[at++] = previous;
            }
        } while (at < vertexCount);
        xBytes &= 0xFFFF;
        yBytes &= 0xFFFF;
        if (xBytes + yBytes > end - cursor) {
            return false;
        }
        int xAt = cursor;
        int yAt = (int) (cursor + xBytes);
        double[] deltaX = new double[vertexCount];
        double[] deltaY = new double[vertexCount];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            int flag = flagData[vertex];
            int x = 0;
            int y = 0;
            if ((flag & X_IS_BYTE) != 0) {
                x = glyf.readU8(xAt++);
                if ((flag & X_IS_SAME_OR_POSITIVE) == 0) {
                    x = -x;
                }
            } else if ((flag & X_IS_SAME_OR_POSITIVE) == 0) {
                x = glyf.readI16(xAt);
                xAt += 2;
            }
            if ((flag & Y_IS_BYTE) != 0) {
                y = glyf.readU8(yAt++);
                if ((flag & Y_IS_SAME_OR_POSITIVE) == 0) {
                    y = -y;
                }
            } else if ((flag & Y_IS_SAME_OR_POSITIVE) == 0) {
                y = glyf.readI16(yAt);
                yAt += 2;
            }
            deltaX[vertex] = (double) x * transform.m00() + (double) y * transform.m10();
            deltaY[vertex] = (double) x * transform.m01() + (double) y * transform.m11();
        }
        return emitContours(glyf, contourArrayAt, contours, vertexCount, flagData, deltaX, deltaY, transform, out);
    }

    private static boolean emitContours(OpenTypeTable glyf, int contourArrayAt, int contours, int vertexCount,
                                        int[] flagData, double[] deltaX, double[] deltaY, Blend2DMatrix transform,
                                        Blend2DPath out) {
        double currentX = transform.m20();
        double currentY = transform.m21();
        int at = 0;
        for (int contour = 0; contour < contours; contour++) {
            int contourEnd = glyf.readU16(contourArrayAt + contour * 2) + 1;
            if (contourEnd <= at || contourEnd > vertexCount) {
                return false;
            }
            int flag = flagData[at];
            currentX += deltaX[at];
            currentY += deltaY[at];
            if (++at >= contourEnd) {
                continue;
            }
            double initialX = currentX;
            double initialY = currentY;
            boolean startsOnCurve = (flag & ON_CURVE) != 0;
            int initialIndex = out.size();
            if (startsOnCurve) {
                out.addRaw(Blend2DPath.MOVE, initialX, initialY);
            }
            while (at < contourEnd) {
                flag = flagData[at];
                double dx = deltaX[at];
                double dy = deltaY[at];
                currentX += dx;
                currentY += dy;
                at++;
                if ((flag & OFF_CURVE_SPLINE) != 0) {
                    out.addRaw((flag & ON_CURVE) != 0 ? Blend2DPath.LINE : Blend2DPath.QUAD, currentX, currentY);
                } else {
                    out.addRaw(Blend2DPath.LINE, currentX - dx * 0.5, currentY - dy * 0.5);
                    out.addRaw(Blend2DPath.QUAD, currentX, currentY);
                }
            }
            if (!startsOnCurve) {
                double finalX = out.xAt(initialIndex);
                double finalY = out.yAt(initialIndex);
                out.setCommandAt(initialIndex, Blend2DPath.MOVE);
                if ((flag & ON_CURVE) == 0) {
                    out.addRaw(Blend2DPath.LINE, (currentX + initialX) * 0.5, (currentY + initialY) * 0.5);
                    finalX = (initialX + finalX) * 0.5;
                    finalY = (initialY + finalY) * 0.5;
                }
                out.addRaw(Blend2DPath.QUAD, initialX, initialY);
                out.addRaw(Blend2DPath.LINE, finalX, finalY);
            } else if ((flag & ON_CURVE) == 0) {
                out.addRaw(Blend2DPath.LINE, initialX, initialY);
            }
            out.close();
        }
        return true;
    }
}
