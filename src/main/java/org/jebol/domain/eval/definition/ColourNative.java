package org.jebol.domain.eval.definition;

import org.jebol.domain.value.Datatype;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.TupleValue;
import org.jebol.domain.value.Value;

import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public abstract class ColourNative extends DefaultNative {

    protected Set<Datatype> aColourOrAnImage() {
        return Set.of(Datatype.TUPLE, Datatype.IMAGE);
    }

    protected int[] threeParts(TupleValue colour) {
        int[] parts = colour.segments();
        return new int[] {
                parts.length > 0 ? parts[0] : 0,
                parts.length > 1 ? parts[1] : 0,
                parts.length > 2 ? parts[2] : 0};
    }

    protected Value recolouredTuple(TupleValue colour, UnaryOperator<int[]> formula) {
        int[] made = colour.segments().clone();
        int[] recoloured = formula.apply(threeParts(colour));
        System.arraycopy(recoloured, 0, made, 0, Math.min(made.length, recoloured.length));
        return TupleValue.of(made);
    }

    protected Value overEveryColour(
            Value target, Function<TupleValue, Value> ofAColour, UnaryOperator<int[]> ofAPixel) {

        if (target instanceof TupleValue colour) {
            return ofAColour.apply(colour);
        }
        ImageValue image = (ImageValue) target;
        for (int pixel = 1; pixel <= image.lengthFromHere(); pixel++) {
            int[] channels = image.pixelAt(pixel);
            int[] recoloured = ofAPixel.apply(new int[] {channels[0], channels[1], channels[2]});
            image.storage().setColourAt(image.index() + pixel - 1,
                    recoloured[0], recoloured[1], recoloured[2]);
        }
        return image;
    }

    protected int[] allThreeAt(int grey) {
        return new int[] {grey, grey, grey};
    }
}
