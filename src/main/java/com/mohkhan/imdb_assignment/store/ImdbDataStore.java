package com.mohkhan.imdb_assignment.store;

import com.mohkhan.imdb_assignment.model.dto.DirectorWriterTitleDto;
import com.mohkhan.imdb_assignment.model.entity.PersonEntity;
import com.mohkhan.imdb_assignment.model.entity.TitleBasicEntity;
import com.mohkhan.imdb_assignment.model.entity.TitleRatingEntity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Moh Khandan
 * Date: 5/2/2026
 * Time: 6:57 AM
 */
@Component
public class ImdbDataStore {

    // ---------------------------------------------------------------
    // Primary indexes — O(1) lookup by ID
    // ---------------------------------------------------------------

    private final Map<String, TitleBasicEntity> titleById = new ConcurrentHashMap<>(11_000_000);
    private final Map<String, TitleRatingEntity> ratingById = new ConcurrentHashMap<>(1_600_000);
    private final Map<String, PersonEntity> personById = new ConcurrentHashMap<>(14_000_000);
    private final List<DirectorWriterTitleDto> directorWriterTitles = Collections.synchronizedList(new ArrayList<>(2_000_000));
    private final Map<String, Set<String>> titlesByActor = new ConcurrentHashMap<>(3_500_000);
    private final Map<String, Set<String>> titlesByGenre = new ConcurrentHashMap<>(30);
    private final Map<String, List<String>> personsByName = new ConcurrentHashMap<>(13_000_000);

    // ---------------------------------------------------------------
    // Write methods — called only by ImdbDatasetLoader
    // ---------------------------------------------------------------

    public void putTitle(TitleBasicEntity title) {
        titleById.put(title.getTconst(), title);
    }

    public void putRating(TitleRatingEntity rating) {
        ratingById.put(rating.getTconst(), rating);
    }

    public void putPerson(PersonEntity person) {
        personById.put(person.getNconst(), person);
    }

    public void addDirectorWriterTitle(DirectorWriterTitleDto dto) {
        directorWriterTitles.add(dto);
    }

    public List<DirectorWriterTitleDto> findDirectorWriterTitles() {
        return directorWriterTitles;
    }

    public void addActorTitle(String nconst, String tconst) {
        titlesByActor
                .computeIfAbsent(nconst, k -> ConcurrentHashMap.newKeySet())
                .add(tconst);
    }

    public void addGenreTitle(String genre, String tconst) {
        titlesByGenre
                .computeIfAbsent(genre, k -> ConcurrentHashMap.newKeySet())
                .add(tconst);
    }

    public void addPersonByName(String normalizedName, String nconst) {
        personsByName
                .computeIfAbsent(normalizedName, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(nconst);
    }

    // ---------------------------------------------------------------
    // Read methods — called by service layer
    // ---------------------------------------------------------------

    public TitleBasicEntity findTitle(String tconst) {
        return titleById.get(tconst);
    }

    public TitleRatingEntity findRating(String tconst) {
        return ratingById.get(tconst);
    }

    public PersonEntity findPerson(String nconst) {
        return personById.get(nconst);
    }

    public List<String> findPersonsByName(String name) {
        return personsByName.getOrDefault(name.toLowerCase().trim(), Collections.emptyList());
    }

    public Set<String> findTitlesByActor(String nconst) {
        return titlesByActor.getOrDefault(nconst, Collections.emptySet());
    }

    public Set<String> findTitlesByGenre(String genre) {
        return titlesByGenre.getOrDefault(genre, Collections.emptySet());
    }

    public Collection<TitleBasicEntity> allTitles() {
        return titleById.values();
    }

}
