package org.Cry_Legende.sHIslandJake;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Condition « A opérateur B » d'une réaction (même écriture que les conditions de SHGuide). Les deux côtés passent
 * par les placeholders du joueur, sauf l'expression régulière de ~. Deux nombres se comparent comme des nombres,
 * sinon comme du texte sans la casse ; >, <, >= et <= demandent deux nombres.
 */
final class Condition {

    private static final String[] OPS = {" == ", " != ", " >= ", " <= ", " > ", " < ", " ~ "};

    private Condition() {
    }

    /** Vrai si l'expression contient un opérateur connu (et, pour ~, une expression régulière valide). */
    static boolean valide(String expr) {
        if (expr == null) {
            return false;
        }
        for (String op : OPS) {
            int at = expr.indexOf(op);
            if (at >= 0) {
                if (op.equals(" ~ ")) {
                    try {
                        Pattern.compile(expr.substring(at + op.length()).trim());
                    } catch (PatternSyntaxException e) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /** Toutes vraies ; une liste vide est vraie. */
    static boolean toutes(List<String> conditions, UnaryOperator<String> placeholders) {
        for (String c : conditions) {
            if (!vraie(c, placeholders)) {
                return false;
            }
        }
        return true;
    }

    static boolean vraie(String expr, UnaryOperator<String> placeholders) {
        if (expr == null) {
            return false;
        }
        for (String op : OPS) {
            int at = expr.indexOf(op);
            if (at < 0) {
                continue;
            }
            String left = placeholders.apply(expr.substring(0, at)).trim();
            String right = expr.substring(at + op.length());
            if (op.equals(" ~ ")) {
                try {
                    return Pattern.compile(right.trim()).matcher(left).find();
                } catch (PatternSyntaxException e) {
                    return false;
                }
            }
            right = placeholders.apply(right).trim();
            OptionalDouble a = Num.parse(left);
            OptionalDouble b = Num.parse(right);
            boolean numbers = a.isPresent() && b.isPresent();
            return switch (op) {
                case " == " -> numbers ? a.getAsDouble() == b.getAsDouble() : left.equalsIgnoreCase(right);
                case " != " -> numbers ? a.getAsDouble() != b.getAsDouble() : !left.equalsIgnoreCase(right);
                case " >= " -> numbers && a.getAsDouble() >= b.getAsDouble();
                case " <= " -> numbers && a.getAsDouble() <= b.getAsDouble();
                case " > " -> numbers && a.getAsDouble() > b.getAsDouble();
                default -> numbers && a.getAsDouble() < b.getAsDouble();
            };
        }
        return false;
    }
}
