package com.mohkhan.imdb_assignment.model.entity;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Moh Khandan
 * Date: 4/30/2026
 * Time: 7:21 PM
 */
@Getter
@Builder
public class PersonEntity {

    private final String nconst;
    private final String primaryName;
    private final Integer deathYear;       // null = alive (per assignment spec)

    public boolean isAlive() {
        return deathYear == null;
    }
}
