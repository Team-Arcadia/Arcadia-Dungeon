package com.arcadia.dungeon.client.arcadiaui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArcaBindingResolver {

    private static final Pattern EXPR = Pattern.compile("\\{\\{(.*?)\\}\\}");

    private ArcaBindingResolver() {}

    public static String resolve(String template, ArcaModel model) {
        if (template == null || template.isEmpty()) return template;
        var sb = new StringBuffer();
        var m = EXPR.matcher(template);
        while (m.find()) {
            String expr = m.group(1).trim();
            String value = evaluateExpr(expr, model);
            m.appendReplacement(sb, Matcher.quoteReplacement(
                value != null ? value : "{{ " + expr + " }}"
            ));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String evaluateExpr(String expr, ArcaModel model) {
        int qMark = expr.indexOf('?');
        if (qMark >= 0) {
            String cond    = expr.substring(0, qMark).trim();
            String rest    = expr.substring(qMark + 1);
            int colon      = rest.indexOf(':');
            if (colon < 0) return null;
            String trueVal  = stripQuotes(rest.substring(0, colon).trim());
            String falseVal = stripQuotes(rest.substring(colon + 1).trim());
            return evaluateCondition(cond, model) ? trueVal : falseVal;
        }
        return evaluateArithmetic(expr, model);
    }

    // ── Conditions ────────────────────────────────────────────────────────

    private static boolean evaluateCondition(String cond, ArcaModel model) {
        // Comparaisons : key OP value
        for (String op : new String[]{">=", "<=", "!=", ">", "<", "=="}) {
            int idx = cond.indexOf(op);
            if (idx >= 0) {
                String left  = cond.substring(0, idx).trim();
                String right = cond.substring(idx + op.length()).trim();
                double lv = toDouble(evaluateArithmetic(left, model));
                double rv = toDouble(stripQuotes(right));
                return switch (op) {
                    case ">"  -> lv > rv;
                    case "<"  -> lv < rv;
                    case ">=" -> lv >= rv;
                    case "<=" -> lv <= rv;
                    case "==" -> lv == rv;
                    case "!=" -> lv != rv;
                    default   -> false;
                };
            }
        }
        // Pas d'opérateur → truthy check sur la valeur
        String val = model.resolve(cond);
        return isTruthy(val);
    }

    // ── Arithmétique ──────────────────────────────────────────────────────

    private static String evaluateArithmetic(String expr, ArcaModel model) {
        for (String op : new String[]{"+", "-", "*", "/"}) {
            int idx = findOperator(expr, op);
            if (idx >= 0) {
                String left  = expr.substring(0, idx).trim();
                String right = expr.substring(idx + 1).trim();
                String lv = evaluateArithmetic(left, model);
                String rv = evaluateArithmetic(right, model);
                try {
                    double result = applyOp(toDouble(lv), op, toDouble(rv));
                    // Retourner entier si sans décimale
                    return result == Math.floor(result) ? String.valueOf((long) result) : String.valueOf(result);
                } catch (Exception e) { return null; }
            }
        }
        // Clé simple
        String resolved = model.resolve(expr);
        return resolved != null ? resolved : (isNumericLiteral(expr) ? expr : null);
    }

    private static int findOperator(String expr, String op) {
        // Cherche l'opérateur de droite à gauche pour respecter la priorité des opérations
        for (int i = expr.length() - 1; i >= 0; i--) {
            if (expr.charAt(i) == op.charAt(0) && expr.substring(i).startsWith(op)) {
                // Éviter de confondre '-' unaire avec binaire
                if (op.equals("-") && i == 0) continue;
                return i;
            }
        }
        return -1;
    }

    private static double applyOp(double l, String op, double r) {
        return switch (op) {
            case "+" -> l + r;
            case "-" -> l - r;
            case "*" -> l * r;
            case "/" -> r != 0 ? l / r : 0;
            default  -> l;
        };
    }

    // ── Utilitaires ───────────────────────────────────────────────────────

    private static boolean isTruthy(String value) {
        if (value == null) return false;
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            default -> false;
        };
    }

    private static double toDouble(String value) {
        if (value == null) return 0;
        try { return Double.parseDouble(value.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private static boolean isNumericLiteral(String s) {
        try { Double.parseDouble(s.trim()); return true; }
        catch (NumberFormatException e) { return false; }
    }

    private static String stripQuotes(String s) {
        return s.replaceAll("^\"|\"$|^'|'$", "");
    }
}
