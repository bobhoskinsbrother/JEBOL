package org.jebol.domain.value;

import java.util.ArrayList;
import java.util.List;

public record ImageValue(ImageStorage storage, int index) implements RebolSeries {

    public ImageValue {
        if (storage == null) {
            throw new IllegalArgumentException("an image value needs storage");
        }
        if (index < 1 || index > storage.length() + 1) {
            throw new IllegalArgumentException(
                    "index " + index + " is outside 1.." + (storage.length() + 1));
        }
    }

    public static ImageValue of(int wide, int high) {
        return new ImageValue(ImageStorage.of(wide, high), 1);
    }

    @Override
    public boolean isProtected() {
        return storage.isProtected();
    }

    @Override
    public List<Value> items() {
        List<Value> read = new ArrayList<>(lengthFromHere());
        for (int at = index; at <= storageLength(); at++) {
            int[] channels = storage.pixelAt(at);
            read.add(TupleValue.of(channels[0], channels[1], channels[2], channels[3]));
        }
        return List.copyOf(read);
    }

    @Override
    public Datatype datatype() {
        return Datatype.IMAGE;
    }

    @Override
    public int storageLength() {
        return storage.length();
    }

    @Override
    public ImageValue atIndex(int oneBasedIndex) {
        return new ImageValue(storage, oneBasedIndex);
    }

    public ImageValue standingAt(int oneBasedIndex) {
        return atIndex(Math.min(oneBasedIndex, storage.length() + 1));
    }

    @Override
    public ImageValue head() {
        return atIndex(1);
    }

    @Override
    public ImageValue tail() {
        return atIndex(storage.length() + 1);
    }

    @Override
    public boolean sharesStorageWith(RebolSeries other) {
        return other instanceof ImageValue image && image.storage == storage;
    }

    public int[] pixelAt(int offsetFromHere) {
        return storage.pixelAt(index + offsetFromHere - 1);
    }

    public PairValue size() {
        return PairValue.of(storage.wide(), storage.high());
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ImageValue image)) {
            return false;
        }
        if (storage.wide() != image.storage.wide()
                || storage.high() != image.storage.high()
                || lengthFromHere() != image.lengthFromHere()) {
            return false;
        }
        for (int offset = 1; offset <= lengthFromHere(); offset++) {
            if (!java.util.Arrays.equals(pixelAt(offset), image.pixelAt(offset))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = storage.wide() * 31 + storage.high();
        for (int offset = 1; offset <= lengthFromHere(); offset++) {
            hash = hash * 31 + java.util.Arrays.hashCode(pixelAt(offset));
        }
        return hash;
    }

    @Override
    public String toString() {
        return "image " + storage.wide() + "x" + storage.high() + " @" + index;
    }
}
