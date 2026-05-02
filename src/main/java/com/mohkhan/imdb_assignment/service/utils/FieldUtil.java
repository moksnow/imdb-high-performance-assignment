package com.mohkhan.imdb_assignment.service.utils;

import org.springframework.stereotype.Component;

/**
 * @author M_Khandan
 * Date: 5/2/2026
 * Time: 4:24 PM
 */

@Component
public class FieldUtil {

    public String[] split(String line) {
        return line.split("\t", -1);
    }

    public String value(String raw) {
        return "\\N".equals(raw) ? null : raw;
    }

    public Integer parseInt(String raw) {
        if (raw == null || raw.isEmpty() || "\\N".equals(raw)) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Double parseDouble(String raw) {
        if (raw == null || raw.isEmpty() || "\\N".equals(raw)) return null;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Boolean parseBoolean(String raw) {
        if (raw == null || "\\N".equals(raw)) return null;
        return "1".equals(raw);
    }
}
