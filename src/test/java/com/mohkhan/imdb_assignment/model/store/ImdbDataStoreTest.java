package com.mohkhan.imdb_assignment.model.store;

import com.mohkhan.imdb_assignment.model.entity.PersonEntity;
import com.mohkhan.imdb_assignment.model.entity.TitleBasicEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Moh Khandan
 * Date: 5/3/2026
 * Time: 7:56 AM
 */
class ImdbDataStoreTest {

    private ImdbDataStore store;

    @BeforeEach
    void setUp() {
        store = new ImdbDataStore();
    }

    @Test
    @DisplayName("putTitle / findTitle: stores and retrieves by tconst, returns null for unknown")
    void titleIndex_storeAndRetrieve() {
        store.putTitle(TitleBasicEntity.builder()
                .tconst("tt001").primaryTitle("Inception")
                .startYear(2010).genres("Action,Sci-Fi").titleType("movie").build());

        assertThat(store.findTitle("tt001").getPrimaryTitle()).isEqualTo("Inception");
        assertThat(store.findTitle("tt_unknown")).isNull();
    }

    @Test
    @DisplayName("personById: isAlive returns true when deathYear null, false when set")
    void personIndex_isAliveCheck() {
        store.putPerson(PersonEntity.builder()
                .nconst("nm001").primaryName("Living Person").deathYear(null).build());
        store.putPerson(PersonEntity.builder()
                .nconst("nm002").primaryName("Dead Person").deathYear(1999).build());

        assertThat(store.findPerson("nm001").isAlive()).isTrue();
        assertThat(store.findPerson("nm002").isAlive()).isFalse();
    }

    @Test
    @DisplayName("addGenreTitle: multiple tconsts per genre, no duplicates, unknown genre returns empty")
    void genreIndex_addsAndDeduplicates() {
        store.addGenreTitle("Drama", "tt001");
        store.addGenreTitle("Drama", "tt002");
        store.addGenreTitle("Drama", "tt001"); // duplicate — should be ignored

        assertThat(store.findTitlesByGenre("Drama")).containsExactlyInAnyOrder("tt001", "tt002");
        assertThat(store.findTitlesByGenre("UnknownGenre")).isEmpty();
    }

    @Test
    @DisplayName("addActorTitle: builds inverted index, unknown actor returns empty set")
    void actorIndex_buildsInvertedIndex() {
        store.addActorTitle("nm001", "tt001");
        store.addActorTitle("nm001", "tt002");
        store.addActorTitle("nm002", "tt001");

        assertThat(store.findTitlesByActor("nm001"))
                .containsExactlyInAnyOrder("tt001", "tt002");
        assertThat(store.findTitlesByActor("nm002"))
                .containsExactlyInAnyOrder("tt001");
        assertThat(store.findTitlesByActor("nm_unknown")).isEmpty();
    }

    @Test
    @DisplayName("addPersonByName: multiple nconsts per name, lookup is case-normalized")
    void personByNameIndex_multipleEntriesAndNormalization() {
        store.addPersonByName("john smith", "nm001");
        store.addPersonByName("john smith", "nm002");

        List<String> result = store.findPersonsByName("john smith");
        assertThat(result).containsExactlyInAnyOrder("nm001", "nm002");
        assertThat(store.findPersonsByName("nobody")).isEmpty();
    }
}

