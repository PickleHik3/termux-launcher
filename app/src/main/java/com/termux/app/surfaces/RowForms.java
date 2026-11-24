package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import com.termux.app.surfaces.EditorRows.Form;
import com.termux.app.surfaces.EditorRows.Spec;

import java.util.List;

/**
 * Which rows of a card share one widget, by the {@link Form} the card file listed them in
 * (spec #326). Pure, so the grouping is held by JVM tests; {@link CardBody} draws what this says.
 */
public final class RowForms {

    private RowForms() {}

    /**
     * Where the widget that starts at {@code start} ends (exclusive): the rows after it that stand
     * in the same tier and the same form, up to the form's capacity. A {@link Form#PLAIN} row
     * stands alone here; {@link CardBody} groups plain rows by their kind.
     */
    public static int groupEnd(@NonNull List<Spec> specs, int start) {
        Spec first = specs.get(start);
        int end = start + 1;
        if (first.form == Form.PLAIN)
            return end;
        while (end < specs.size() && end - start < first.form.capacity()
            && specs.get(end).tier == first.tier && specs.get(end).form == first.form)
            end++;
        return end;
    }
}
