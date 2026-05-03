package com.mohkhan.imdb_assignment.model.entity;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Moh Khandan
 * Date: 4/30/2026
 * Time: 5:47 PM
 */
@Getter
@Builder
public class TitleBasicEntity {

    private final String tconst;
    private final String titleType;
    private final String primaryTitle;
    private final Boolean isAdult;
    private final Integer startYear;
    private final Integer endYear;
    private final Integer runtimeMinutes;
    private final String genres;           // raw "Action,Drama" — kept for DTO response
}
