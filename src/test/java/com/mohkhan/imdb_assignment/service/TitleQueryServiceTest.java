package com.mohkhan.imdb_assignment.service;

import com.mohkhan.imdb_assignment.exception.DataNotReadyException;
import com.mohkhan.imdb_assignment.exception.InvalidRequestException;
import com.mohkhan.imdb_assignment.model.dto.BestTitlePerYearDto;
import com.mohkhan.imdb_assignment.model.dto.CommonTitleDto;
import com.mohkhan.imdb_assignment.model.dto.DirectorWriterTitleDto;
import com.mohkhan.imdb_assignment.model.entity.TitleBasicEntity;
import com.mohkhan.imdb_assignment.model.entity.TitleRatingEntity;
import com.mohkhan.imdb_assignment.model.response.PagedResponse;
import com.mohkhan.imdb_assignment.model.store.ImdbDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Moh Khandan
 * Date: 5/3/2026
 * Time: 7:53 AM
 */
class TitleQueryServiceTest {

    private ImdbDataStore store;
    private TitleQueryService service;

    @BeforeEach
    void setUp() {
        store = new ImdbDataStore();
        DataLoadStateService readyState = new DataLoadStateService();
        readyState.markReady();
        service = new TitleQueryService(store, readyState);
    }

    @Test
    @DisplayName("Task 2: throws DataNotReadyException before data is loaded")
    void task2_throwsWhenNotReady() {
        DataLoadStateService notReady = new DataLoadStateService();
        TitleQueryService notReadyService = new TitleQueryService(store, notReady);

        assertThatThrownBy(() -> notReadyService.getDirectorWriterTitles(0, 10))
                .isInstanceOf(DataNotReadyException.class);
    }

    @Test
    @DisplayName("Task 2: pagination slices cached list correctly")
    void task2_paginationCorrect() {
        for (int i = 1; i <= 5; i++) {
            store.addDirectorWriterTitle(DirectorWriterTitleDto.builder()
                    .tconst("tt00" + i).primaryTitle("Film " + i)
                    .startYear(2000 + i).genres("Drama")
                    .personId("nm001").personName("Director")
                    .averageRating(7.0).numVotes(1000).build());
        }

        PagedResponse<DirectorWriterTitleDto> page0 = service.getDirectorWriterTitles(0, 2);
        PagedResponse<DirectorWriterTitleDto> page1 = service.getDirectorWriterTitles(1, 2);

        assertThat(page0.items()).hasSize(2);
        assertThat(page0.totalItems()).isEqualTo(5);
        assertThat(page0.hasNext()).isTrue();
        assertThat(page1.items()).hasSize(2);
    }

    @Test
    @DisplayName("Task 3: throws InvalidRequestException when same actor passed twice")
    void task3_throwsWhenSameActor() {
        assertThatThrownBy(() -> service.getCommonTitles("nm001", "nm001"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("different");
    }

    @Test
    @DisplayName("Task 3: returns intersection of titles for two actors sorted by rating DESC")
    void task3_returnsCommonTitlesSortedByRating() {
        store.putTitle(title("tt001", "High Rated", 2020, "Drama"));
        store.putTitle(title("tt002", "Low Rated", 2019, "Drama"));
        store.putTitle(title("tt003", "Solo Film", 2018, "Comedy"));
        store.putRating(rating("tt001", 8.5, 50000));
        store.putRating(rating("tt002", 6.0, 10000));
        store.addActorTitle("nm001", "tt001");
        store.addActorTitle("nm001", "tt002");
        store.addActorTitle("nm001", "tt003");
        store.addActorTitle("nm002", "tt001");
        store.addActorTitle("nm002", "tt002");

        List<CommonTitleDto> result = service.getCommonTitles("nm001", "nm002");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTconst()).isEqualTo("tt001"); // higher rating first
    }

    @Test
    @DisplayName("Task 4: returns best title per year, tiebreaker by numVotes, sorted year DESC")
    void task4_bestPerYearWithTiebreaker() {
        store.putTitle(title("tt001", "Few Votes", 2021, "Action"));
        store.putTitle(title("tt002", "Many Votes", 2021, "Action"));
        store.putTitle(title("tt003", "Only 2020", 2020, "Action"));
        store.putRating(rating("tt001", 8.0, 1000));
        store.putRating(rating("tt002", 8.0, 50000)); // same rating, more votes — should win
        store.putRating(rating("tt003", 7.0, 5000));
        store.addGenreTitle("Action", "tt001");
        store.addGenreTitle("Action", "tt002");
        store.addGenreTitle("Action", "tt003");

        List<BestTitlePerYearDto> result = service.getBestTitlesByGenre("Action");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getYear()).isEqualTo(2021);       // most recent first
        assertThat(result.get(0).getTconst()).isEqualTo("tt002");  // more votes wins tie
        assertThat(result.get(1).getYear()).isEqualTo(2020);
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    private TitleBasicEntity title(String tconst, String name, Integer year, String genre) {
        return TitleBasicEntity.builder()
                .tconst(tconst).primaryTitle(name)
                .startYear(year).genres(genre).titleType("movie").build();
    }

    private TitleRatingEntity rating(String tconst, double avg, int votes) {
        return TitleRatingEntity.builder()
                .tconst(tconst).averageRating(avg).numVotes(votes).build();
    }
}

