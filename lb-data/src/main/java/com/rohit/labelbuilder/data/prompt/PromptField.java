package com.rohit.labelbuilder.data.prompt;

import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One field of the form shown at print time (FR-DS-08, lbl-format.md §5).
 *
 * <p>The validation is the reason this is more than a text box. An operator typing a batch code at a
 * terminal is the last check before several hundred labels are printed and stuck to cartons; catching
 * a mistyped code here costs a retype, and catching it afterwards costs a reprint and a relabel. So a
 * field can insist on being filled, match a pattern, or offer a fixed list.
 *
 * @param defaultValue prefilled in the form; empty is not the same as absent for a required field
 * @param validation a regular expression the answer must match in full, when given
 * @param pickList the only permitted answers, or empty for free text
 */
public record PromptField(
        String name,
        FieldType type,
        String defaultValue,
        boolean required,
        Optional<String> validation,
        List<String> pickList) {

    public PromptField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(validation, "validation");
        pickList = List.copyOf(Objects.requireNonNull(pickList, "pickList"));
        if (name.isBlank()) {
            throw new IllegalArgumentException("a prompt field needs a name");
        }
        if (validation.isPresent()) {
            try {
                Pattern.compile(validation.get());
            } catch (PatternSyntaxException e) {
                // Refused at design time: a broken pattern would otherwise fail every answer at print
                // time, with the operator unable to tell why.
                throw new IllegalArgumentException("the validation pattern for '" + name
                        + "' is not a valid regular expression: " + e.getDescription());
            }
        }
        if (!pickList.isEmpty()
                && defaultValue != null
                && !defaultValue.isBlank()
                && !pickList.contains(defaultValue)) {
            throw new IllegalArgumentException(
                    "the default '" + defaultValue + "' for '" + name + "' is not one of its pick-list values");
        }
    }

    public static PromptField required(String name, FieldType type) {
        return new PromptField(name, type, "", true, Optional.empty(), List.of());
    }

    public static PromptField optional(String name, FieldType type) {
        return new PromptField(name, type, "", false, Optional.empty(), List.of());
    }

    public PromptField withDefault(String defaultValue) {
        return new PromptField(name, type, defaultValue, required, validation, pickList);
    }

    public PromptField withValidation(String pattern) {
        return new PromptField(name, type, defaultValue, required, Optional.of(pattern), pickList);
    }

    public PromptField withPickList(String... values) {
        return new PromptField(name, type, defaultValue, required, validation, List.of(values));
    }

    public PromptField withRequired(boolean required) {
        return new PromptField(name, type, defaultValue, required, validation, pickList);
    }

    /** A pick-list field is a dropdown; everything else is a text box with validation. */
    public boolean hasPickList() {
        return !pickList.isEmpty();
    }

    /** The compiled pattern, for a form that validates as the operator types. */
    public Optional<Pattern> pattern() {
        return validation.map(Pattern::compile);
    }

    /** This field as it appears in the source's schema. */
    public FieldDefinition toFieldDefinition() {
        return new FieldDefinition(name, type, java.util.OptionalInt.empty(), java.util.OptionalInt.empty(), !required);
    }
}
