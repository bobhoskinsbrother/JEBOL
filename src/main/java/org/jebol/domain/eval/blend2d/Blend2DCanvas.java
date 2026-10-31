package org.jebol.domain.eval.blend2d;

import java.util.Optional;

public final class Blend2DCanvas {

    private static final double FIXED_POINT_SCALE = 256.0;

    private static final int FRACTION_MASK = 0xFF;

    private static final int OPAQUE_BLACK = 0xFF000000;

    private static final int TRANSPARENT = 0x00000000;

    private static final int FULL_ALPHA = 255;

    private static final double STROKE_MITER_LIMIT = 4.0;

    private final Blend2DSurface surface;

    private CompositingOperator operator = CompositingOperator.SOURCE_OVER;

    private int globalAlpha = FULL_ALPHA;

    private int fillColour = OPAQUE_BLACK;

    private Optional<String> fillThatIsNotSolid = Optional.empty();

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
        fillThatIsNotSolid = Optional.empty();
    }

    public void setFillThatIsNotSolid(String what) {
        fillThatIsNotSolid = Optional.of(what);
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
        resolveSolidFill().ifPresent(fill -> paintClipArea(fill, globalAlpha));
    }

    public void clearAll() {
        resolveClearing().ifPresent(fill -> paintClipArea(fill, FULL_ALPHA));
    }

    public void fillRect(double x, double y, double wide, double high) {
        resolveSolidFill().ifPresent(fill -> paintBox(fill, globalAlpha, Blend2DBox.fromRect(x, y, wide, high)));
    }

    public void clearRect(double x, double y, double wide, double high) {
        resolveClearing().ifPresent(fill -> paintBox(fill, FULL_ALPHA, Blend2DBox.fromRect(x, y, wide, high)));
    }

    public void fill(Blend2DShape shape) {
        resolveSolidFill().ifPresent(fill -> paintPath(fill, globalAlpha, shape.path(), AnalyticFill.FillRule.NON_ZERO));
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

    private void paintPolygon(SolidFill fill, int alpha, double[] points, AnalyticFill.FillRule rule) {
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

    private void paintPath(SolidFill fill, int alpha, Blend2DPath path, AnalyticFill.FillRule rule) {
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

    private void paintEdges(EdgeBuilder builder, SolidFill fill, int alpha, AnalyticFill.FillRule rule) {
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

    private Optional<SolidFill> resolveSolidFill() {
        if (hasNoClipArea) {
            return Optional.empty();
        }
        fillThatIsNotSolid.ifPresent(what -> {
            throw new Blend2DNotPortedYet("with " + what);
        });
        return SolidFill.resolve(operator, fillColour, globalAlpha);
    }

    private Optional<SolidFill> resolveClearing() {
        if (hasNoClipArea) {
            return Optional.empty();
        }
        return SolidFill.resolve(CompositingOperator.SOURCE_COPY, TRANSPARENT, FULL_ALPHA);
    }

    private void paintClipArea(SolidFill fill, int alpha) {
        if (isClipAligned) {
            paintAlignedBox(fill, clipWhole[0], clipWhole[1], clipWhole[2], clipWhole[3]);
        } else {
            UnalignedBoxFill.fill(surface, clipFixedWhole[0], clipFixedWhole[1], clipFixedWhole[2], clipFixedWhole[3],
                    alpha, fill);
        }
    }

    private void paintBox(SolidFill fill, int alpha, Blend2DBox box) {
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
        int[] whole = clipped.get().truncate();
        if (whole[0] >= whole[2] || whole[1] >= whole[3]) {
            return;
        }
        if (((whole[0] | whole[1] | whole[2] | whole[3]) & FRACTION_MASK) == 0) {
            paintAlignedBox(fill, whole[0] >> 8, whole[1] >> 8, whole[2] >> 8, whole[3] >> 8);
        } else {
            UnalignedBoxFill.fill(surface, whole[0], whole[1], whole[2], whole[3], alpha, fill);
        }
    }

    private void paintAlignedBox(SolidFill fill, int left, int top, int right, int bottom) {
        for (int down = top; down < bottom; down++) {
            for (int across = left; across < right; across++) {
                surface.setPixel(across, down, fill.composite(surface.pixelAt(across, down)));
            }
        }
    }

    public void refuse(String what) {
        throw new Blend2DNotPortedYet(what);
    }
}
