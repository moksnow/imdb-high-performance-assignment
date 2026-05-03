package com.mohkhan.imdb_assignment.service.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Moh Khandan
 * Date: 5/3/2026
 * Time: 7:57 AM
 */
class FieldUtilTest {

    private FieldUtil fieldUtil;

    @BeforeEach
    void setUp() {
        fieldUtil = new FieldUtil();
    }

    @Test
    @DisplayName("value(): returns null for \\N sentinel, returns string as-is otherwise")
    void value_handlesNullSentinelAndNormalString() {
        assertThat(fieldUtil.value("\\N")).isNull();
        assertThat(fieldUtil.value("Inception")).isEqualTo("Inception");
        assertThat(fieldUtil.value("")).isEqualTo(""); // empty is NOT null
    }

    @Test
    @DisplayName("parseInt(): parses valid year, returns null for \\N and null input, does not throw on garbage")
    void parseInt_handlesAllEdgeCases() {
        assertThat(fieldUtil.parseInt("2008")).isEqualTo(2008);
        assertThat(fieldUtil.parseInt("\\N")).isNull();
        assertThat(fieldUtil.parseInt(null)).isNull();
        assertThat(fieldUtil.parseInt("")).isNull();

        // Must return null — NOT throw NumberFormatException
        assertThatCode(() -> fieldUtil.parseInt("not_a_number")).doesNotThrowAnyException();
        assertThat(fieldUtil.parseInt("not_a_number")).isNull();
    }

    @Test
    @DisplayName("parseDouble(): parses valid rating, returns null for \\N and null, does not throw on garbage")
    void parseDouble_handlesAllEdgeCases() {
        assertThat(fieldUtil.parseDouble("8.5")).isEqualTo(8.5);
        assertThat(fieldUtil.parseDouble("10.0")).isEqualTo(10.0);
        assertThat(fieldUtil.parseDouble("\\N")).isNull();
        assertThat(fieldUtil.parseDouble(null)).isNull();
        assertThat(fieldUtil.parseDouble("")).isNull();

        // Must return null — NOT throw NumberFormatException
        assertThatCode(() -> fieldUtil.parseDouble("abc")).doesNotThrowAnyException();
        assertThat(fieldUtil.parseDouble("abc")).isNull();
    }

    @Test
    @DisplayName("parseBoolean(): '1'→true, '0'→false, \\N→null, null→null")
    void parseBoolean_handlesBooleanValues() {
        assertThat(fieldUtil.parseBoolean("1")).isTrue();
        assertThat(fieldUtil.parseBoolean("0")).isFalse();
        assertThat(fieldUtil.parseBoolean("\\N")).isNull();
        assertThat(fieldUtil.parseBoolean(null)).isNull();
        assertThat(fieldUtil.parseBoolean("true")).isFalse(); // only "1" is true
    }

    @Test
    @DisplayName("split(): splits by tab, preserves trailing empty fields, handles real IMDB row")
    void split_handlesTabsAndRealRow() {
        // Trailing empty fields must be preserved — critical for correct column indexing
        String[] parts = fieldUtil.split("tt001\tmovie\t\t");
        assertThat(parts).hasSize(4);
        assertThat(parts[2]).isEmpty();
        assertThat(parts[3]).isEmpty();

        // Real title.basics row
        String realRow = "tt0000001\tshort\tCarmencita\tCarmencita\t0\t1894\t\\N\t1\tDocumentary,Short";
        String[] realParts = fieldUtil.split(realRow);
        assertThat(realParts).hasSize(9);
        assertThat(realParts[0]).isEqualTo("tt0000001");
        assertThat(fieldUtil.parseInt(realParts[5])).isEqualTo(1894);
        assertThat(fieldUtil.parseInt(realParts[6])).isNull(); // \N endYear
        assertThat(realParts[8]).isEqualTo("Documentary,Short");
    }
}

