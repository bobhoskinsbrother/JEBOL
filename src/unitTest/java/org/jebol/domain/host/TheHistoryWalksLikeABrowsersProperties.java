package org.jebol.domain.host;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.jebol.domain.value.BlockValue;
import org.jebol.domain.value.WordValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TheHistoryWalksLikeABrowsersProperties {

    private record Step(String action, String placeName) {
    }

    private final class AWalk {

        private long millisecondsNow;
        private final History history = new History(() -> millisecondsNow);
        private final List<Place> modelEntries = new ArrayList<>(List.of(Place.start()));
        private int modelPosition;

        void take(Step step) {
            millisecondsNow += 1_000;
            Place named = Place.of(BlockValue.of(List.of(WordValue.of(step.placeName()))));
            switch (step.action()) {
                case "go" -> go(named);
                case "replace" -> replace(named);
                case "back" -> arrive(history.backTarget(), modelPosition - 1);
                case "forward" -> arrive(history.forwardTarget(), modelPosition + 1);
                case "operator-back" -> arrive(history.operatorBackTarget(), modelPosition - 1);
                default -> arrive(history.operatorForwardTarget(), modelPosition + 1);
            }
        }

        private void go(Place named) {
            history.goTo(named);
            if (!named.equals(modelEntries.get(modelPosition))) {
                modelEntries.subList(modelPosition + 1, modelEntries.size()).clear();
                modelEntries.add(named);
                modelPosition++;
            }
        }

        private void replace(Place named) {
            history.replace(named);
            modelEntries.set(modelPosition, named);
        }

        private void arrive(Optional<HistoryEntry> target, int expectedIndex) {
            boolean modelCanMove = expectedIndex >= 0 && expectedIndex < modelEntries.size();
            assertThat(target.isPresent()).isEqualTo(modelCanMove);
            target.ifPresent(entry -> {
                assertThat(entry.index()).isEqualTo(expectedIndex);
                assertThat(entry.address()).isEqualTo(modelEntries.get(expectedIndex).address());
                Optional<ScreenEventType> direction =
                        history.arriveAt(entry.index(), Place.readAddress(entry.address()).orElseThrow());
                assertThat(direction).contains(expectedIndex < modelPosition
                        ? ScreenEventType.BACK
                        : ScreenEventType.FORWARD);
                modelPosition = expectedIndex;
            });
        }

        void agreesWithTheModel() {
            assertThat(history.position()).isEqualTo(modelPosition);
            assertThat(history.position()).isNotNegative();
            assertThat(history.current()).isEqualTo(modelEntries.get(modelPosition));
            assertThat(history.operatorBackTarget().isPresent()).isEqualTo(modelPosition > 0);
            assertThat(history.operatorForwardTarget().isPresent())
                    .isEqualTo(modelPosition < modelEntries.size() - 1);
        }
    }

    @Property
    void everyWalkKeepsThePositionOnAnEntryAndAgreesWithABrowser(@ForAll("walks") List<Step> walk) {
        AWalk walking = new AWalk();
        for (Step step : walk) {
            walking.take(step);
            walking.agreesWithTheModel();
        }
    }

    @Provide
    Arbitrary<List<Step>> walks() {
        Arbitrary<Step> step = Arbitraries.of("go", "replace", "back", "forward", "operator-back", "operator-forward")
                .flatMap(action -> Arbitraries.of("a", "b", "c").map(name -> new Step(action, name)));
        return step.list().ofMaxSize(40);
    }
}
