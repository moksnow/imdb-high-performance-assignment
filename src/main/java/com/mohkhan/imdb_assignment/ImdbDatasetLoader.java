package com.mohkhan.imdb_assignment;

import com.mohkhan.imdb_assignment.dao.store.ImdbDataStore;
import com.mohkhan.imdb_assignment.model.entity.PersonEntity;
import com.mohkhan.imdb_assignment.model.entity.TitleBasicEntity;
import com.mohkhan.imdb_assignment.model.entity.TitleRatingEntity;
import com.mohkhan.imdb_assignment.service.DataLoadStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * @author Moh Khandan
 * Date: 4/30/2026
 * Time: 5:00 PM
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class ImdbDatasetLoader implements CommandLineRunner {

    private final ImdbDataStore store;
    private final DataLoadStateService dataLoadStateService;

    @Value("${imdb.dataset-path}")
    private String datasetPath;

    @Value("${app.skip-data-load:false}")
    private boolean skipDataLoad;

    @Override
    public void run(String... args) throws InterruptedException {
        Thread loaderThread = new Thread(this::loadAllData, "imdb-loader");
        loaderThread.start();
        loaderThread.join();
    }

    private void loadAllData() {
        try {
            if (skipDataLoad) {
                log.info("Skipping data load — store already populated");
                dataLoadStateService.markReady();
                return;
            }

            log.info("Starting IMDB data load from: {}", datasetPath);
            long start = System.currentTimeMillis();

            loadTitleBasics();
            loadTitleRatings();
            loadNameBasics();
            loadTitleCrew();
            loadTitlePrincipals();

            dataLoadStateService.markReady();
            log.info("IMDB load complete in {} ms", System.currentTimeMillis() - start);
            log.info("Titles: {}",
                    store.allTitles().size());

        } catch (Exception e) {
            log.error("IMDB data load failed — all data endpoints will return 503", e);
        }
    }

    // -----------------------------------------------------------------------

    private void loadTitleBasics() throws Exception {
        log.info("Loading title.basics...");
        long count = 0;

        try (BufferedReader reader = openGzip("title.basics.tsv.gz")) {
            reader.readLine(); // skip header

            String line;

            while ((line = reader.readLine()) != null) {
                String[] p = split(line);
                if (p.length < 9) continue;

                String tconst = p[0];

                store.putTitle(TitleBasicEntity.builder()
                        .tconst(tconst)
                        .titleType(value(p[1]))
                        .primaryTitle(value(p[2]))
                        .isAdult(parseBoolean(p[4]))
                        .startYear(parseInt(p[5]))
                        .endYear(parseInt(p[6]))
                        .runtimeMinutes(parseInt(p[7]))
                        .genres(value(p[8]))
                        .build());

                String genresRaw = value(p[8]);
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

        try (BufferedReader reader = openGzip("title.ratings.tsv.gz")) {
            reader.readLine();

            String line;

            while ((line = reader.readLine()) != null) {
                String[] p = split(line);
                if (p.length < 3) continue;

                Double rating = parseDouble(p[1]);
                Integer votes = parseInt(p[2]);
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

        try (BufferedReader reader = openGzip("name.basics.tsv.gz")) {
            reader.readLine();

            String line;

            while ((line = reader.readLine()) != null) {
                String[] p = split(line);
                if (p.length < 4) continue;

                PersonEntity person = PersonEntity.builder()
                        .nconst(p[0])
                        .primaryName(value(p[1]))
                        .deathYear(parseInt(p[3]))
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

        try (BufferedReader reader = openGzip("title.crew.tsv.gz")) {
            reader.readLine();

            String line;

            while ((line = reader.readLine()) != null) {
                String[] p = split(line);
                if (p.length < 3) continue;

                String tconst = p[0];

                String directorsRaw = value(p[1]);
                String writersRaw = value(p[2]);

                if (directorsRaw == null || writersRaw == null) {
                    if (++count % 500_000 == 0) log.info("title.crew: {} rows", count);
                    continue;
                }

                // Build sets for this title only — no longer stored in maps
                Set<String> directors = new HashSet<>(Arrays.asList(directorsRaw.split(",")));
                Set<String> writers = new HashSet<>(Arrays.asList(writersRaw.split(",")));

                // Find intersection — person must be both director and writer
                for (String directorId : directors) {
                    directorId = directorId.trim();
                    if (directorId.isEmpty()) continue;

                    if (writers.contains(directorId) || writers.contains(directorId.trim())) {
                        PersonEntity person = store.findPerson(directorId);
                        if (person != null && person.isAlive()) {
                            // This title qualifies — store tconst and move to next line
                            store.addDirectorWriterTitle(tconst);
                            break;
                        }
                    }
                }

                if (++count % 500_000 == 0) {
                    log.info("title.crew: {} rows", count);
                }
            }
        }
        log.info("title.crew done: {} rows", count);
    }

    private void loadTitlePrincipals() throws Exception {
        log.info("Loading title.principals...");
        long count = 0;

        try (BufferedReader reader = openGzip("title.principals.tsv.gz")) {
            reader.readLine();

            String line;

            while ((line = reader.readLine()) != null) {
                // columns: tconst, ordering, nconst, category, job, characters
                String[] p = split(line);
                if (p.length < 4) continue;

                String category = value(p[3]);
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

    // -----------------------------------------------------------------------
    // Parsing helpers
    // -----------------------------------------------------------------------

    private BufferedReader openGzip(String fileName) throws Exception {
        Path file = Paths.get(datasetPath, fileName);

        return new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(file), 65536),
                        StandardCharsets.UTF_8
                ),
                1024 * 1024  // 1MB text buffer
        );
    }

    private String[] split(String line) {
        return line.split("\t", -1);
    }

    private String value(String raw) {
        return "\\N".equals(raw) ? null : raw;
    }

    private Integer parseInt(String raw) {
        if (raw == null || raw.isEmpty() || "\\N".equals(raw)) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String raw) {
        if (raw == null || raw.isEmpty() || "\\N".equals(raw)) return null;
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean parseBoolean(String raw) {
        if ("\\N".equals(raw) || raw == null) return null;
        return "1".equals(raw);
    }
}