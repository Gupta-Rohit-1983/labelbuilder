package com.rohit.labelbuilder.data.prompt;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.DataSource;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.InMemoryDataSource;
import com.rohit.labelbuilder.data.MapRecord;
import com.rohit.labelbuilder.data.RecordSet;
import com.rohit.labelbuilder.data.query.ParameterValues;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The prompt-at-print data source: a form the operator fills in, yielding exactly one record
 * (FR-DS-08, the {@code MANUAL} kind of lbl-format.md §5).
 *
 * <p>It is a {@link DataSource} like any other, so a label binds to {@code ${Prompt.SKU}} the same way
 * it binds to a database column, and the preview, the binder and the print engine need no special case
 * for it. The answers arrive as the {@code parameters} map that {@link DataSource#open} already takes
 * — which is what that parameter was for.
 *
 * <p><b>Every answer is validated before a single label is printed.</b> Required fields, the
 * validation pattern and the pick list are all checked together, and all failures are reported in one
 * message: an operator at a terminal should see everything wrong with the form at once, not fix one
 * field per attempt. Values are then converted to the field's declared type, so a label bound to a
 * numeric prompt gets a number rather than the text of one.
 */
public final class PromptDataSource implements DataSource {

    private final String name;
    private final List<PromptField> promptFields;

    public PromptDataSource(String name, List<PromptField> promptFields) {
        this.name = Objects.requireNonNull(name, "name");
        this.promptFields = List.copyOf(Objects.requireNonNull(promptFields, "promptFields"));
        List<String> names = this.promptFields.stream().map(PromptField::name).toList();
        if (names.stream().distinct().count() != names.size()) {
            throw new IllegalArgumentException("prompt field names must be unique, got " + names);
        }
    }

    public PromptDataSource(String name, PromptField... promptFields) {
        this(name, List.of(promptFields));
    }

    @Override
    public String name() {
        return name;
    }

    public List<PromptField> promptFields() {
        return promptFields;
    }

    @Override
    public List<FieldDefinition> fields() {
        return promptFields.stream().map(PromptField::toFieldDefinition).toList();
    }

    /**
     * The one record the form describes.
     *
     * @param answers the operator's answers by field name; unknown names are ignored
     * @throws DataException if any answer is missing or invalid, listing every problem
     */
    @Override
    public RecordSet open(Map<String, Object> answers) {
        List<String> problems = validate(answers);
        if (!problems.isEmpty()) {
            throw new DataException("the print form is incomplete: " + String.join("; ", problems));
        }
        return new InMemoryDataSource(name, fields(), List.of(record(answers))).open();
    }

    /**
     * What is wrong with the answers so far, for a form that checks as it is filled in.
     *
     * <p>Returns every problem rather than the first, and never throws — a form showing one error at a
     * time makes the operator guess how many more there are.
     */
    public List<String> validate(Map<String, Object> answers) {
        List<String> problems = new ArrayList<>();
        for (PromptField field : promptFields) {
            String answer = answerFor(field, answers);
            if (answer.isBlank()) {
                if (field.required()) {
                    problems.add("'" + field.name() + "' is required");
                }
                continue;
            }
            if (field.hasPickList() && !field.pickList().contains(answer)) {
                problems.add("'" + answer + "' is not one of the permitted values for '" + field.name() + "' ("
                        + String.join(", ", field.pickList()) + ")");
                continue;
            }
            Pattern pattern = field.pattern().orElse(null);
            if (pattern != null && !pattern.matcher(answer).matches()) {
                problems.add("'" + answer + "' does not match the required format for '" + field.name() + "'");
                continue;
            }
            try {
                ParameterValues.coerce(field.type(), answer, field.name());
            } catch (DataException e) {
                problems.add(e.getMessage());
            }
        }
        return problems;
    }

    /** The record the answers make, assuming they have already been validated. */
    private DataRecord record(Map<String, Object> answers) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (PromptField field : promptFields) {
            String answer = answerFor(field, answers);
            // An optional field left empty is present with no value, not missing: a label bound to it
            // prints blank, and a print condition can still ask whether it was filled in.
            values.put(
                    field.name(), answer.isBlank() ? null : ParameterValues.coerce(field.type(), answer, field.name()));
        }
        return new MapRecord(values);
    }

    /**
     * The answer for one field, falling back to its default.
     *
     * <p>Blank counts as unanswered so a default stays reachable, matching how
     * {@link ParameterValues} treats an untouched form field.
     */
    private static String answerFor(PromptField field, Map<String, Object> answers) {
        Object supplied = answers.get(field.name());
        String answer = supplied == null ? "" : String.valueOf(supplied).trim();
        if (answer.isBlank()) {
            answer = field.defaultValue() == null ? "" : field.defaultValue().trim();
        }
        return answer;
    }
}
