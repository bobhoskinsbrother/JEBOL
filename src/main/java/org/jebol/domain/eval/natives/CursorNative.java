package org.jebol.domain.eval.natives;

import org.jebol.domain.eval.GrantedServices;
import org.jebol.domain.eval.RefinedCallable;
import org.jebol.domain.host.HostService;
import org.jebol.domain.host.ImagePort;
import org.jebol.domain.host.ScreenCursor;
import org.jebol.domain.value.ImageValue;
import org.jebol.domain.value.IntegerValue;
import org.jebol.domain.value.NoneValue;
import org.jebol.domain.value.Parameter;
import org.jebol.domain.value.UnsetValue;
import org.jebol.domain.value.Value;

import java.util.List;
import java.util.Set;

public class CursorNative extends ScreenNative {

    public CursorNative(GrantedServices granted) {
        super(granted);
    }

    @Override
    public String nativeName() {
        return "cursor";
    }

    @Override
    public List<Parameter> parametersAsWritten() {
        return List.of(Parameter.of("image", Set.of(IntegerValue.TYPE, ImageValue.TYPE, NoneValue.TYPE)));
    }

    @Override
    public RefinedCallable behaviour() {
        return (arguments, evaluator, context, refinements) -> {
            granted.require(HostService.WINDOWS);
            ScreenCursor chosen = cursorNamedBy(arguments.getFirst());
            runThroughScreen(() -> evaluator.ports().screen().useCursor(chosen));
            return UnsetValue.unset();
        };
    }

    private ScreenCursor cursorNamedBy(Value given) {
        return switch (given) {
            case IntegerValue(long id) -> ScreenCursor.numbered(id);
            case ImageValue image -> ScreenCursor.painted(new ImagePort.Pixels(
                    image.storage().wide(), image.storage().high(), image.head().copyEveryPixel()));
            default -> new ScreenCursor.Hidden();
        };
    }
}
