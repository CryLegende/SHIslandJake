package org.Cry_Legende.sHIslandJake;

import java.util.OptionalDouble;

/** Nombres lus dans les placeholders (« 12 273 », « 5.0 », « 2,5 »). */
final class Num {

    private Num() {
    }

    static OptionalDouble parse(String text) {
        if (text == null) {
            return OptionalDouble.empty();
        }
        String t = text.replace(" ", "").replace(" ", "").replace(" ", "").replace(',', '.').trim();
        if (t.isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            double d = Double.parseDouble(t);
            return Double.isFinite(d) ? OptionalDouble.of(d) : OptionalDouble.empty();
        } catch (NumberFormatException e) {
            return OptionalDouble.empty();
        }
    }
}
