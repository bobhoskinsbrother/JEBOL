package org.jebol.domain.eval.blend2d;

import java.util.Optional;

public final class Blend2DCanvas {

    private static final double FIXED_POINT_SCALE = 256.0;

    private static final int FRACTION_MASK = 0xFF;

    private static final int OPAQUE_BLACK = 0xFF000000;

    private static final int TRANSPARENT = 0x00000000;

    private static final int FULL_ALPHA = 255;

    private static final double STROKE_MITER_LIMIT = 4.0;

    private static final int BLIT_EXTEND = 0;

    private final Blend2DSurface surface;

    private CompositingOperator operator = CompositingOperator.SOURCE_OVER;

    private int globalAlpha = FULL_ALPHA;

    private int fillColour = OPAQUE_BLACK;

    private Optional<Blend2DPaint> fillPaint = Optional.empty();

    private Optional<Blend2DMatrix> fillPaintDeviceToUser = Optional.empty();

    private int strokeColour = OPAQUE_BLACK;

    private double strokeWidth = 1.0;

    private int strokeJoin = PathStroker.JOIN_MITER_CLIP;

    private int strokeStartCap = PathStroker.CAP_BUTT;

    private int strokeEndCap = PathStroker.CAP_BUTT;

    private Blend2DMatrix transform = Blend2DMatrix.IDENTITY;

    private Blend2DBox clip;

    private Blend2DBox clipFixed;

    private int[] clipFixedWhole;

    private int[] clipWhole;

    private boolean isClipAligned = true;

    private boolean hasNoClipArea;

    public Blend2DCanvas(Blend2DSurface surface) {
        this.surface = surface;
        resetClipToSurface();
    }

    public void setOperator(CompositingOperator chosen) {
        operator = chosen;
    }

    public void setGlobalAlpha(double alpha) {
        if (!Double.isNaN(alpha)) {
            globalAlpha = SolidFill.scaleAlpha(alpha);
        }
    }

    public void setFillColour(int argb) {
        fillColour = argb;
        fillPaint = Optional.empty();
    }

    public void setFillPaint(Blend2DPaint paint) {
        fillPaint = Optional.of(paint);
        fillPaintDeviceToUser = transform.invert();
    }

    public void translate(double across, double down) {
        transform = transform.postTranslate(across, down);
    }

    public void scale(double across, double down) {
        transform = transform.postScale(across, down);
    }

    public void rotate(double radians) {
        transform = transform.postRotate(radians);
    }

    public void rotateAbout(double radians, double across, double down) {
        transform = transform.postRotateAbout(radians, across, down);
    }

    public void resetTransform() {
        transform = Blend2DMatrix.IDENTITY;
    }

    public void clipToRect(double x, double y, double wide, double high) {
        Optional<Blend2DBox> narrowed = clip.intersectWith(transform.mapBox(Blend2DBox.fromRect(x, y, wide, high)));
        if (narrowed.isEmpty()) {
            clip = new Blend2DBox(0, 0, 0, 0);
            clipWhole = new int[] {0, 0, 0, 0};
            setClipFixed(new Blend2DBox(0, 0, 0, 0));
            hasNoClipArea = true;
            isClipAligned = true;
            return;
        }
        Blend2DBox box = narrowed.get();
        setClipFixed(box.scaledBy(FIXED_POINT_SCALE));
        clip = box;
        clipWhole = new int[] {clipFixedWhole[0] >> 8, clipFixedWhole[1] >> 8,
                (clipFixedWhole[2] + FRACTION_MASK) >> 8, (clipFixedWhole[3] + FRACTION_MASK) >> 8};
        int bits = clipFixedWhole[0] | clipFixedWhole[1] | clipFixedWhole[2] | clipFixedWhole[3];
        isClipAligned = (bits & FRACTION_MASK) == 0;
    }

    public void restoreClipping() {
        hasNoClipArea = false;
        clip = new Blend2DBox(0, 0, surface.wide(), surface.high());
        clipWhole = new int[] {0, 0, surface.wide(), surface.high()};
        setClipFixed(clip.scaledBy(FIXED_POINT_SCALE));
    }

    private void resetClipToSurface() {
        restoreClipping();
        isClipAligned = true;
    }

    private void setClipFixed(Blend2DBox fixed) {
        clipFixed = fixed;
        clipFixedWhole = fixed.truncate();
    }

    public void fillAll() {
        resolveFill().ifPresent(fill -> paintClipArea(fill, globalAlpha));
    }

    public void clearAll() {
        resolveClearing().ifPresent(fill -> paintClipArea(fill, FULL_ALPHA));
    }

    public void fillRect(double x, double y, double wide, double high) {
        resolveFill().ifPresent(fill -> paintBox(fill, globalAlpha, Blend2DBox.fromRect(x, y, wide, high)));
    }

    public void clearRect(double x, double y, double wide, double high) {
        resolveClearing().ifPresent(fill -> paintBox(fill, FULL_ALPHA, Blend2DBox.fromRect(x, y, wide, high)));
    }

    public void fill(Blend2DShape shape) {
        resolveFill().ifPresent(fill -> paintPath(fill, globalAlpha, shape.path(), AnalyticFill.FillRule.NON_ZERO));
    }

    public void stroke(Blend2DShape shape) {
        if (hasNoClipArea) {
            return;
        }
        SolidFill.resolve(operator, strokeColour, globalAlpha).ifPresent(fill -> paintStroke(fill, shape.path()));
    }

    public void setStrokeColour(int argb) {
        strokeColour = argb;
    }

    public void setStrokeWidth(double width) {
        strokeWidth = width;
    }

    public void setStrokeJoin(int join) {
        strokeJoin = join;
    }

    public void setStrokeCaps(int start, int end) {
        strokeStartCap = start;
        strokeEndCap = end;
    }

    public void setStrokeStartCap(int cap) {
        strokeStartCap = cap;
    }

    public void setStrokeEndCap(int cap) {
        strokeEndCap = cap;
    }

    private void paintPolygon(PixelSource fill, int alpha, double[] points, AnalyticFill.FillRule rule) {
        paintPath(fill, alpha, Blend2DShape.polygon(points).path(), rule);
    }

    private Blend2DPath mapToFixedPoint(Blend2DPath path, Blend2DMatrix fixed) {
        boolean isScaledOnly = fixed.kind().compareTo(Blend2DMatrix.Kind.SCALE) <= 0;
        Blend2DPath mapped = new Blend2DPath();
        for (int at = 0; at < path.size(); at++) {
            double x = path.xAt(at);
            double y = path.yAt(at);
            double mappedX;
            double mappedY;
            if (isScaledOnly) {
                mappedX = x * fixed.m00() + fixed.m20();
                mappedY = y * fixed.m11() + fixed.m21();
            } else {
                mappedX = x * fixed.m00() + y * fixed.m10() + fixed.m20();
                mappedY = x * fixed.m01() + y * fixed.m11() + fixed.m21();
            }
            mapped.addRaw(path.commandAt(at), mappedX, mappedY);
        }
        return mapped;
    }

    private void paintPath(PixelSource fill, int alpha, Blend2DPath path, AnalyticFill.FillRule rule) {
        if (transform.kind() == Blend2DMatrix.Kind.INVALID || path.isEmpty()) {
            return;
        }
        EdgeBuilder builder = new EdgeBuilder(clipFixed);
        if (!builder.addFilledPath(mapToFixedPoint(path, transform.toFixedPoint()))) {
            return;
        }
        paintEdges(builder, fill, alpha, rule);
    }

    private void paintStroke(SolidFill fill, Blend2DPath path) {
        if (transform.kind() == Blend2DMatrix.Kind.INVALID || path.isEmpty()) {
            return;
        }
        Blend2DMatrix fixed = transform.toFixedPoint();
        EdgeBuilder builder = new EdgeBuilder(clipFixed);
        PathStroker.Options options = new PathStroker.Options(strokeWidth, strokeJoin, STROKE_MITER_LIMIT,
                strokeStartCap, strokeEndCap);
        boolean[] isValid = {true};
        boolean isStroked = new PathStroker(path, options).stroke((outer, inner, startCap) -> {
            isValid[0] &= builder.addPath(mapToFixedPoint(outer, fixed), false);
            isValid[0] &= builder.addReversedPath(mapToFixedPoint(inner, fixed));
            if (!startCap.isEmpty()) {
                isValid[0] &= builder.addPath(mapToFixedPoint(startCap, fixed), false);
            }
        });
        if (!isStroked || !isValid[0]) {
            return;
        }
        paintEdges(builder, fill, globalAlpha, AnalyticFill.FillRule.NON_ZERO);
    }

    private void paintEdges(EdgeBuilder builder, PixelSource fill, int alpha, AnalyticFill.FillRule rule) {
        builder.finish();
        if (builder.isEmpty()) {
            return;
        }
        AnalyticCells cells = new AnalyticCells(surface.wide(), surface.high());
        AnalyticRasterizer rasterizer = new AnalyticRasterizer(cells);
        for (EdgeBuilder.EdgeVector edge : builder.edges()) {
            rasterizer.rasterize(edge);
        }
        AnalyticFill.fill(surface, cells, rule, alpha, fill);
    }

    private Optional<PixelSource> resolveFill() {
        if (hasNoClipArea) {
            return Optional.empty();
        }
        if (fillPaint.isPresent()) {
            return fillPaintDeviceToUser.map(deviceToUser -> new PaintedFill(fillPaint.get(), operator, globalAlpha,
                    new double[] {deviceToUser.m00(), deviceToUser.m01(), deviceToUser.m10(), deviceToUser.m11(),
                        deviceToUser.m20(), deviceToUser.m21()}));
        }
        return SolidFill.resolve(operator, fillColour, globalAlpha).map(fill -> fill);
    }

    private Optional<SolidFill> resolveClearing() {
        if (hasNoClipArea) {
            return Optional.empty();
        }
        return SolidFill.resolve(CompositingOperator.SOURCE_COPY, TRANSPARENT, FULL_ALPHA);
    }

    private void paintClipArea(PixelSource fill, int alpha) {
        if (isClipAligned) {
            paintAlignedBox(fill, clipWhole[0], clipWhole[1], clipWhole[2], clipWhole[3]);
        } else {
            UnalignedBoxFill.fill(surface, clipFixedWhole[0], clipFixedWhole[1], clipFixedWhole[2], clipFixedWhole[3],
                    alpha, fill);
        }
    }

    private void paintBox(PixelSource fill, int alpha, Blend2DBox box) {
        Blend2DMatrix.Kind kind = transform.kind();
        if (kind == Blend2DMatrix.Kind.INVALID) {
            return;
        }
        if (kind == Blend2DMatrix.Kind.AFFINE) {
            double[] corners = {box.x0(), box.y0(), box.x1(), box.y0(), box.x1(), box.y1(), box.x0(), box.y1()};
            paintPolygon(fill, alpha, corners, AnalyticFill.FillRule.EVEN_ODD);
            return;
        }
        Optional<Blend2DBox> clipped = transform.toFixedPoint().mapBoxScaledOrSwapped(box).intersectWith(clipFixed);
        if (clipped.isEmpty()) {
            return;
        }
        paintFixedBox(fill, alpha, clipped.get().truncate());
    }

    private void paintFixedBox(PixelSource fill, int alpha, int[] whole) {
        if (whole[0] >= whole[2] || whole[1] >= whole[3]) {
            return;
        }
        if (((whole[0] | whole[1] | whole[2] | whole[3]) & FRACTION_MASK) == 0) {
            paintAlignedBox(fill, whole[0] >> 8, whole[1] >> 8, whole[2] >> 8, whole[3] >> 8);
        } else {
            UnalignedBoxFill.fill(surface, whole[0], whole[1], whole[2], whole[3], alpha, fill);
        }
    }

    public void fillText(Blend2DFont font, double x, double y, int[] codePoints) {
        Optional<PixelSource> fill = resolveFill();
        if (fill.isEmpty() || transform.kind() == Blend2DMatrix.Kind.INVALID) {
            return;
        }
        Optional<Blend2DFont.GlyphRun> run;
        try {
            run = font.shape(codePoints);
        } catch (IndexOutOfBoundsException pastTheFont) {
            return;
        }
        if (run.isEmpty() || run.get().glyphs().length == 0) {
            return;
        }
        EdgeBuilder builder = new EdgeBuilder(clipFixed);
        try {
            if (!addGlyphRunEdges(builder, font, run.get(), x, y)) {
                return;
            }
        } catch (IndexOutOfBoundsException pastTheFont) {
            return;
        }
        paintEdges(builder, fill.get(), globalAlpha, AnalyticFill.FillRule.NON_ZERO);
    }

    private boolean addGlyphRunEdges(EdgeBuilder builder, Blend2DFont font, Blend2DFont.GlyphRun run,
                                     double x, double y) {
        Blend2DMatrix fixed = transform.toFixedPoint();
        double originX = x * fixed.m00() + y * fixed.m10() + fixed.m20();
        double originY = x * fixed.m01() + y * fixed.m11() + fixed.m21();
        double scale = font.scale();
        double fontM00 = scale;
        double fontM01 = 0.0;
        double fontM10 = 0.0;
        double fontM11 = -scale;
        double m00 = fontM00 * fixed.m00() + fontM01 * fixed.m10();
        double m01 = fontM00 * fixed.m01() + fontM01 * fixed.m11();
        double m10 = fontM10 * fixed.m00() + fontM11 * fixed.m10();
        double m11 = fontM10 * fixed.m01() + fontM11 * fixed.m11();
        double advanceX = originX;
        double advanceY = originY;
        int[] glyphs = run.glyphs();
        int[][] placements = run.placements();
        for (int at = 0; at < glyphs.length; at++) {
            double placementX = placements[at][0];
            double placementY = placements[at][1];
            double glyphX = placementX * m00 + placementY * m10 + advanceX;
            double glyphY = placementX * m01 + placementY * m11 + advanceY;
            Blend2DPath outline = new Blend2DPath();
            if (!font.decodeGlyph(glyphs[at], new Blend2DMatrix(m00, m01, m10, m11, glyphX, glyphY), outline)) {
                return false;
            }
            if (!builder.addPath(outline, true)) {
                return false;
            }
            double stepX = placements[at][2];
            double stepY = placements[at][3];
            advanceX += stepX * m00 + stepY * m10;
            advanceY += stepX * m01 + stepY * m11;
        }
        return true;
    }

    public void blitImage(Blend2DImage source, double x, double y) {
        int wide = source.wide();
        int high = source.high();
        if (hasNoClipArea || wide <= 0 || high <= 0) {
            return;
        }
        Blend2DPattern image = new Blend2DPattern(source, BLIT_EXTEND);
        if (transform.kind().compareTo(Blend2DMatrix.Kind.TRANSLATE) <= 0) {
            blitTranslated(image, wide, high, x, y);
            return;
        }
        paintImage(image, transform.translate(x, y), Blend2DBox.fromRect(x, y, wide, high));
    }

    public void blitScaledImage(Blend2DImage source, double x, double y, double toWide, double toHigh) {
        int wide = source.wide();
        int high = source.high();
        if (toWide == wide && toHigh == high) {
            blitImage(source, x, y);
            return;
        }
        if (hasNoClipArea || wide <= 0 || high <= 0) {
            return;
        }
        Blend2DMatrix placed = new Blend2DMatrix(toWide / wide, 0, 0, toHigh / high, x, y).multiply(transform);
        paintImage(new Blend2DPattern(source, BLIT_EXTEND), placed, Blend2DBox.fromRect(x, y, toWide, toHigh));
    }

    private void blitTranslated(Blend2DPattern image, int wide, int high, double x, double y) {
        Blend2DMatrix fixed = transform.toFixedPoint();
        double startX = x * fixed.m00() + fixed.m20();
        double startY = y * fixed.m11() + fixed.m21();
        double x0 = Math.max(startX, clipFixed.x0());
        double y0 = Math.max(startY, clipFixed.y0());
        double x1 = Math.min(startX + wide * fixed.m00(), clipFixed.x1());
        double y1 = Math.min(startY + high * fixed.m11(), clipFixed.y1());
        if (!(x0 < x1 && y0 < y1)) {
            return;
        }
        double offsetX = Math.floor(startX) / FIXED_POINT_SCALE;
        double offsetY = Math.floor(startY) / FIXED_POINT_SCALE;
        PaintedFill fill = new PaintedFill(image, operator, globalAlpha, new double[] {1, 0, 0, 1, -offsetX, -offsetY});
        paintFixedBox(fill, globalAlpha, new int[] {(int) x0, (int) y0, (int) x1, (int) y1});
    }

    private void paintImage(Blend2DPattern image, Blend2DMatrix imageToDevice, Blend2DBox box) {
        imageToDevice.invert().ifPresent(deviceToImage -> paintBox(new PaintedFill(image, operator, globalAlpha,
                new double[] {deviceToImage.m00(), deviceToImage.m01(), deviceToImage.m10(), deviceToImage.m11(),
                    deviceToImage.m20(), deviceToImage.m21()}), globalAlpha, box));
    }

    private void paintAlignedBox(PixelSource fill, int left, int top, int right, int bottom) {
        for (int down = top; down < bottom; down++) {
            for (int across = left; across < right; across++) {
                surface.setPixel(across, down, fill.composite(surface.pixelAt(across, down), across, down));
            }
        }
    }

    public void refuse(String what) {
        throw new Blend2DRefusal(what);
    }
}
