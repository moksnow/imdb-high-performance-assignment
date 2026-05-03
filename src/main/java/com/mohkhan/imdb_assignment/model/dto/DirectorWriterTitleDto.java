package com.mohkhan.imdb_assignment.model.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:07 PM
 */
@Getter
@Builder
public class DirectorWriterTitleDto {

    private final String tconst;
    private final String primaryTitle;
    private final Integer startYear;
    private final String genres;
    private final String personId;
    private final String personName;
    private final Double averageRating;
    private final Integer numVotes;
}
