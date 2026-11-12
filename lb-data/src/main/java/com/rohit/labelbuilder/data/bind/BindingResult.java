package com.rohit.labelbuilder.data.bind;

import com.rohit.labelbuilder.model.document.LabelDocument;
import java.util.List;
import java.util.Objects;

/**
 * A document with its bindings resolved, plus whatever went wrong doing it.
 *
 * <p>Both halves are always present. A document with errors is still returned, because the designer's
 * preview has to show something and a placeholder left on the canvas is the clearest possible
 * description of the problem. The print engine looks at {@link #hasErrors()} and refuses.
 *
 * @param document the merged document, ready to render
 */
public record BindingResult(LabelDocument document, List<BindingProblem> problems) {

    public BindingResult {
        Objects.requireNonNull(document, "document");
        problems = List.copyOf(Objects.requireNonNull(problems, "problems"));
    }

    public boolean hasErrors() {
        return problems.stream().anyMatch(BindingProblem::isError);
    }

    public boolean hasProblems() {
        return !problems.isEmpty();
    }

    public List<BindingProblem> errors() {
        return problems.stream().filter(BindingProblem::isError).toList();
    }

    public List<BindingProblem> warnings() {
        return problems.stream().filter(problem -> !problem.isError()).toList();
    }
}
