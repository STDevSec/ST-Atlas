package com.stdevsec.atlas.finding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** CVSS v3.1 base score from a vector string (FIRST specification, section 7). */
public final class Cvss31 {

    private static final Pattern VECTOR = Pattern.compile(
            "CVSS:3\\.1/AV:[NALP]/AC:[LH]/PR:[NLH]/UI:[NR]/S:[UC]/C:[NLH]/I:[NLH]/A:[NLH]");

    private Cvss31() {}

    public static boolean isValid(String vector) {
        return vector != null && VECTOR.matcher(vector).matches();
    }

    public static double baseScore(String vector) {
        if (!isValid(vector)) {
            throw new IllegalArgumentException("Invalid CVSS 3.1 vector");
        }
        Map<String, String> m = new HashMap<>();
        for (String part : vector.substring("CVSS:3.1/".length()).split("/")) {
            String[] kv = part.split(":");
            m.put(kv[0], kv[1]);
        }
        boolean changed = m.get("S").equals("C");
        double av = switch (m.get("AV")) { case "N" -> 0.85; case "A" -> 0.62; case "L" -> 0.55; default -> 0.2; };
        double ac = m.get("AC").equals("L") ? 0.77 : 0.44;
        double pr = switch (m.get("PR")) {
            case "N" -> 0.85;
            case "L" -> changed ? 0.68 : 0.62;
            default -> changed ? 0.5 : 0.27;
        };
        double ui = m.get("UI").equals("N") ? 0.85 : 0.62;
        double c = cia(m.get("C"));
        double i = cia(m.get("I"));
        double a = cia(m.get("A"));
        double iss = 1 - ((1 - c) * (1 - i) * (1 - a));
        double impact = changed ? 7.52 * (iss - 0.029) - 3.25 * Math.pow(iss - 0.02, 15) : 6.42 * iss;
        double exploitability = 8.22 * av * ac * pr * ui;
        if (impact <= 0) {
            return 0.0;
        }
        double raw = changed ? Math.min(1.08 * (impact + exploitability), 10) : Math.min(impact + exploitability, 10);
        return roundUp(raw);
    }

    private static double cia(String v) {
        return switch (v) { case "H" -> 0.56; case "L" -> 0.22; default -> 0.0; };
    }

    /** "Roundup" as defined in CVSS v3.1 Appendix A (avoids floating-point artefacts). */
    static double roundUp(double value) {
        long intInput = Math.round(value * 100000);
        if (intInput % 10000 == 0) {
            return intInput / 100000.0;
        }
        return (Math.floor(intInput / 10000.0) + 1) / 10.0;
    }

    static BigDecimal asDecimal(double score) {
        return BigDecimal.valueOf(score).setScale(1, RoundingMode.HALF_UP);
    }
}
