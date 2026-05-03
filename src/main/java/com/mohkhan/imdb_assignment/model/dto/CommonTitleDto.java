package com.mohkhan.imdb_assignment.model.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:08 PM
 */
@Getter
@Builder
public class CommonTitleDto {

    private final String tconst;
    private final String primaryTitle;
    private final Integer startYear;
    private final String genres;
    private final Double averageRating;
    private final Integer numVotes;
}
