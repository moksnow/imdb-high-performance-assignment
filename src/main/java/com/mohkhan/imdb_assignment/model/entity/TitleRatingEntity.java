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
public class TitleRatingEntity {
    private final String tconst;
    private final Double averageRating;
    private final Integer numVotes;
}