package com.rohit.labelbuilder.desktop.canvas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionModelTest {

    private final SelectionModel<String> selection = new SelectionModel<>();

    @Test
    void replaceWithSelectsExactlyOne() {
        selection.addAll(List.of("a", "b"));

        selection.replaceWith("c");

        assertThat(selection.selected()).containsExactly("c");
        assertThat(selection.primary()).isEqualTo("c");
    }

    @Test
    void toggleAddsThenRemoves() {
        selection.toggle("a");
        assertThat(selection.isSelected("a")).isTrue();
        assertThat(selection.primary()).isEqualTo("a");

        selection.toggle("a");
        assertThat(selection.isSelected("a")).isFalse();
        assertThat(selection.isEmpty()).isTrue();
        assertThat(selection.primary()).isNull();
    }

    @Test
    void removingPrimaryPromotesAnotherSelectedItem() {
        selection.toggle("a");
        selection.toggle("b"); // b is primary now

        selection.toggle("b");

        assertThat(selection.selected()).containsExactly("a");
        assertThat(selection.primary()).isEqualTo("a");
    }

    @Test
    void addAllMakesTheLastAddedPrimary() {
        selection.addAll(List.of("a", "b", "c"));

        assertThat(selection.size()).isEqualTo(3);
        assertThat(selection.primary()).isEqualTo("c");
    }

    @Test
    void clearEmptiesEverything() {
        selection.addAll(List.of("a", "b"));

        selection.clear();

        assertThat(selection.isEmpty()).isTrue();
        assertThat(selection.primary()).isNull();
    }

    @Test
    void selectedViewIsUnmodifiable() {
        selection.toggle("a");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> selection.selected().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
