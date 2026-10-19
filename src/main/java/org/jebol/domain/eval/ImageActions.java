package org.jebol.domain.eval;

import org.jebol.domain.value.ImageStorage;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.Value;

import java.util.List;

public final class ImageActions extends SeriesActions {

    private final ImageValue picture;

    public ImageActions(ImageValue picture) {
        this.picture = picture;
    }

    @Override
    ImageValue held() {
        return picture;
    }

    @Override
    public Value poke(Value position, Value written) {
        ImagePath.write(picture, (int) position.asPosition(), written);
        return written;
    }

    @Override
    void takeOneOutAt(int oneBasedIndex) {
        picture.storage().removeFrom(oneBasedIndex, 1);
    }

    @Override
    public Value complement() {
        ImageStorage flipped = ImageStorage.of(
                picture.storage().wide(), picture.storage().high());
        for (int pixel = 1; pixel <= picture.storage().length(); pixel++) {
            int[] channels = picture.storage().readThePixelAt(pixel);
            flipped.setColourAt(pixel,
                    ~channels[0] & 0xFF, ~channels[1] & 0xFF, ~channels[2] & 0xFF);
            flipped.setAlphaAt(pixel, ~channels[3] & 0xFF);
        }
        return new ImageValue(flipped, 1);
    }


    @Override
    Value ofTheSameKindHolding(List<Value> items) {
        ImageValue made = ImageValue.of(items.size(), items.isEmpty() ? 0 : 1);
        for (int at = 1; at <= items.size(); at++) {
            ImagePath.write(made, at, items.get(at - 1));
        }
        return made;
    }

    @Override
    public Value append(Asked asked) {
        asked.refuseRefinementsThisDatatypeDoesNotServe("append");
        return picture.append(asked.given(), asked.howManyTimes());
    }

    @Override
    public Value insert(Asked asked) {
        asked.refuseRefinementsThisDatatypeDoesNotServe("insert");
        return picture.insert(asked.given(), asked.howManyTimes());
    }
}
