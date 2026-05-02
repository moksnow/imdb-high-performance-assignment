package com.mohkhan.imdb_assignment.service;

import com.mohkhan.imdb_assignment.model.dto.DirectorWriterTitleDto;
import com.mohkhan.imdb_assignment.model.entity.PersonEntity;
import com.mohkhan.imdb_assignment.model.entity.TitleBasicEntity;
import com.mohkhan.imdb_assignment.model.entity.TitleRatingEntity;
import com.mohkhan.imdb_assignment.service.utils.FieldUtil;
import com.mohkhan.imdb_assignment.service.utils.TsvFileUtil;
import com.mohkhan.imdb_assignment.store.ImdbDataStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author M_Khandan
 * Date: 5/2/2026
 * Time: 4:23 PM
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImdbLoadCoordinator {

    private final ImdbDataStore store;
    private final DataLoadStateService dataLoadStateService;
    private final TsvFileUtil tsvFileReader;
    private final FieldUtil fieldParser;

    @Value("${imdb.dataset-path}")
    private String datasetPath;

    public void loadAllData() {
        try {
            log.info("Starting IMDB data load from: {}", datasetPath);
            long start = System.currentTimeMillis();

            ExecutorService phase1Pool = Executors.newFixedThreadPool(3);

            CompletableFuture<Void> basicsFuture = CompletableFuture.runAsync(this::loadTitleBasicsSafe, phase1Pool);

            CompletableFuture<Void> ratingsFuture = CompletableFuture.runAsync(this::loadTitleRatingsSafe, phase1Pool);

            CompletableFuture<Void> personsFuture = CompletableFuture.runAsync(this::loadNameBasicsSafe, phase1Pool);

            CompletableFuture.allOf(basicsFuture, ratingsFuture, personsFuture).join();
            phase1Pool.shutdown();

            log.info("Phase 1 complete in {} ms", System.currentTimeMillis() - start);

            loadTitleCrew();
            loadTitlePrincipals();

            dataLoadStateService.markReady();

            log.info("IMDB load complete in {} ms", System.currentTimeMillis() - start);
            log.info("Titles: {}, DirectorWriter matches: {}",
                    store.allTitles().size(),
                    store.findDirectorWriterTitles().size());

        } catch (Exception e) {
            log.error("IMDB data load failed — all data endpoints will return 503", e);
        }
    }

    private void loadTitleBasicsSafe() {
        try {
            loadTitleBasics();
        } catch (Exception e) {
            throw new RuntimeException("title.basics load failed", e);
        }
    }

    private void loadTitleRatingsSafe() {
        try {
            loadTitleRatings();
        } catch (Exception e) {
            throw new RuntimeException("title.ratings load failed", e);
        }
    }

    private void loadNameBasicsSafe() {
        try {
            loadNameBasics();
        } catch (Exception e) {
            throw new RuntimeException("name.basics load failed", e);
        }
    }

    private void loadTitleBasics() throws Exception {
        log.info("Loading title.basics...");
        long count = 0;

        try (BufferedReader reader = tsvFileReader.openGzip(datasetPath, "title.basics.tsv.gz")) {
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length < 9) continue;

                String tconst = p[0];

                store.putTitle(TitleBasicEntity.builder()
                        .tconst(tconst)
                        .titleType(fieldParser.value(p[1]))
                        .primaryTitle(fieldParser.value(p[2]))
                        .isAdult(fieldParser.parseBoolean(p[4]))
                        .startYear(fieldParser.parseInt(p[5]))
                        .endYear(fieldParser.parseInt(p[6]))
                        .runtimeMinutes(fieldParser.parseInt(p[7]))
                        .genres(fieldParser.value(p[8]))
                        .build());

                String genresRaw = fieldParser.value(p[8]);
                if (genresRaw != null) {
                    for (String genre : genresRaw.split(",")) {
                        String clean = genre.trim();
                        if (!clean.isEmpty()) {
                            store.addGenreTitle(clean, tconst);
                        }
                    }
                }

                if (++count % 500_000 == 0) {
                    log.info("title.basics: {} rows", count);
                }
            }
        }

        log.info("title.basics done: {} rows", count);
    }

    private void loadTitleRatings() throws Exception {
        log.info("Loading title.ratings...");
        long count = 0;

        try (BufferedReader reader = tsvFileReader.openGzip(datasetPath, "title.ratings.tsv.gz")) {
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length < 3) continue;

                Double rating = fieldParser.parseDouble(p[1]);
                Integer votes = fieldParser.parseInt(p[2]);
                if (rating == null || votes == null) continue;

                store.putRating(TitleRatingEntity.builder()
                        .tconst(p[0])
                        .averageRating(rating)
                        .numVotes(votes)
                        .build());

                if (++count % 500_000 == 0) {
                    log.info("title.ratings: {} rows", count);
                }
            }
        }

        log.info("title.ratings done: {} rows", count);
    }

    private void loadNameBasics() throws Exception {
        log.info("Loading name.basics...");
        long count = 0;

        try (BufferedReader reader = tsvFileReader.openGzip(datasetPath, "name.basics.tsv.gz")) {
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = fieldParser.split(line);
                if (p.length < 4) continue;

                PersonEntity person = PersonEntity.builder()
                        .nconst(p[0])
                        .primaryName(fieldParser.value(p[1]))
                        .deathYear(fieldParser.parseInt(p[3]))
                        .build();

                store.putPerson(person);

                if (person.getPrimaryName() != null) {
                    store.addPersonByName(
                            person.getPrimaryName().toLowerCase().trim(),
                            person.getNconst()
                    );
                }

                if (++count % 500_000 == 0) {
                    log.info("name.basics: {} rows", count);
                }
            }
        }

        log.info("name.basics done: {} rows", count);
    }

    private void loadTitleCrew() throws Exception {
        log.info("Loading title.crew...");
        long count = 0;
        long matches = 0;

        try (BufferedReader reader = tsvFileReader.openGzip(datasetPath, "title.crew.tsv.gz")) {
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length < 3) continue;

                String tconst = p[0];
                String directorsRaw = fieldParser.value(p[1]);
                String writersRaw = fieldParser.value(p[2]);

                if (directorsRaw == null || writersRaw == null) {
                    if (++count % 500_000 == 0) {
                        log.info("title.crew: {} rows", count);
                    }
                    continue;
                }

                Set<String> directors = new HashSet<>(Arrays.asList(directorsRaw.split(",")));
                Set<String> writers = new HashSet<>(Arrays.asList(writersRaw.split(",")));

                for (String directorId : directors) {
                    String cleanId = directorId.trim();
                    if (cleanId.isEmpty()) continue;

                    if (writers.contains(cleanId)) {
                        PersonEntity person = store.findPerson(cleanId);
                        if (person != null && person.isAlive()) {
                            TitleBasicEntity title = store.findTitle(tconst);
                            if (title == null) break;

                            TitleRatingEntity rating = store.findRating(tconst);

                            store.addDirectorWriterTitle(
                                    DirectorWriterTitleDto.builder()
                                            .tconst(tconst)
                                            .primaryTitle(title.getPrimaryTitle())
                                            .startYear(title.getStartYear())
                                            .genres(title.getGenres())
                                            .personId(person.getNconst())
                                            .personName(person.getPrimaryName())
                                            .averageRating(rating != null ? rating.getAverageRating() : null)
                                            .numVotes(rating != null ? rating.getNumVotes() : null)
                                            .build()
                            );
                            matches++;
                            break;
                        }
                    }
                }

                if (++count % 500_000 == 0) {
                    log.info("title.crew: {} rows processed, {} matches so far", count, matches);
                }
            }
        }

        log.info("title.crew done: {} rows, {} director-writer matches", count, matches);
    }

    private void loadTitlePrincipals() throws Exception {
        log.info("Loading title.principals...");
        long count = 0;

        try (BufferedReader reader = tsvFileReader.openGzip(datasetPath, "title.principals.tsv.gz")) {
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] p = fieldParser.split(line);
                if (p.length < 4) continue;

                String category = fieldParser.value(p[3]);
                if ("actor".equals(category) || "actress".equals(category)) {
                    store.addActorTitle(p[2], p[0]);
                }

                if (++count % 500_000 == 0) {
                    log.info("title.principals: {} rows", count);
                }
            }
        }

        log.info("title.principals done: {} rows", count);
    }
}
