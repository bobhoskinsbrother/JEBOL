package org.jebol.domain.eval;

import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.Value;

/**
 * What an image does when an action is performed on it, which is what
 * {@code REBTYPE(Image)} answers in {@code t-image.c}.
 *
 * <p>An image is a series of pixels, so APPEND and INSERT put pixels in and
 * the width is made to fit again afterwards.
 *
 * <p>None of /PART, /ONLY or /DUP is served. The repeat count handed to
 * {@link ImageSeries} is therefore always one -- it is read after the refusal
 * that would have stopped /DUP ever reaching it, and is kept only because
 * ImageSeries takes it.
 */
public final class ImageActions implements Actions {

    private final ImageValue picture;

    public ImageActions(ImageValue picture) {
        this.picture = picture;
    }

    @Override
    public Value subject() {
        return picture;
    }

    @Override
    public int length() {
        return picture.lengthFromHere();
    }

    @Override
    public Value append(Asked asked) {
        asked.refuseRefinementsThisDatatypeDoesNotServe("append");
        return ImageSeries.appended(picture, asked.given(), asked.howManyTimes());
    }

    @Override
    public Value insert(Asked asked) {
        asked.refuseRefinementsThisDatatypeDoesNotServe("insert");
        return ImageSeries.inserted(picture, asked.given(), asked.howManyTimes());
    }
}
