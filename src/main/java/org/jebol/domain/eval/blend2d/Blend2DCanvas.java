package org.jebol.domain.eval.blend2d;

import java.util.Optional;

public final class Blend2DCanvas {

    private static final double FIXED_POINT_SCALE = 256.0;

    private static final int FRACTION_MASK = 0xFF;

    private static final int OPAQUE_BLACK = 0xFF000000;

    private static final int TRANSPARENT = 0x00000000;

    private static final int FULL_ALPHA = 255;

    private final Blend2DSurface surface;

    private CompositingOperator operator = CompositingOperator.SOURCE_OVER;

    private int globalAlpha = FULL_ALPHA;

    private int fillColour = OPAQUE_BLACK;

    private Optional<String> fillThatIsNotSolid = Optional.empty();

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
        resolveSolidFill().ifPresent(fill -> paintBox(fill, globalAlpha, Blend2DBox.fromRect(x, y, wide, high), "a box"));
    }

    public void clearRect(double x, double y, double wide, double high) {
        resolveClearing().ifPresent(fill -> paintBox(fill, FULL_ALPHA, Blend2DBox.fromRect(x, y, wide, high), "a clear"));
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

    private void paintBox(SolidFill fill, int alpha, Blend2DBox box, String what) {
        Blend2DMatrix.Kind kind = transform.kind();
        if (kind == Blend2DMatrix.Kind.INVALID) {
            return;
        }
        if (kind == Blend2DMatrix.Kind.AFFINE) {
            throw new Blend2DNotPortedYet(what + " under a rotation or a skew");
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
