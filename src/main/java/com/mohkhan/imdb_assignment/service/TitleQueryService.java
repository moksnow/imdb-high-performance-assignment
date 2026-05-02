package com.mohkhan.imdb_assignment.service;

import com.mohkhan.imdb_assignment.dao.store.ImdbDataStore;
import com.mohkhan.imdb_assignment.exception.DataNotReadyException;
import com.mohkhan.imdb_assignment.exception.InvalidRequestException;
import com.mohkhan.imdb_assignment.model.dto.BestTitlePerYearDto;
import com.mohkhan.imdb_assignment.model.dto.CommonTitleDto;
import com.mohkhan.imdb_assignment.model.dto.DirectorWriterTitleDto;
import com.mohkhan.imdb_assignment.model.entity.PersonEntity;
import com.mohkhan.imdb_assignment.model.entity.TitleBasicEntity;
import com.mohkhan.imdb_assignment.model.entity.TitleRatingEntity;
import com.mohkhan.imdb_assignment.model.response.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:32 PM
 */
@Service
@RequiredArgsConstructor
public class TitleQueryService {

    private final ImdbDataStore store;
    private final DataLoadStateService dataLoadStateService;

    // -----------------------------------------------------------------------
    // Task 2: titles where director == writer and person is alive
    // -----------------------------------------------------------------------
    private final Map<String, List<CommonTitleDto>> commonTitlesCache =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(100, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, List<CommonTitleDto>> eldest) {
                            return size() > 100;
                        }
                    }
            );
    private volatile List<DirectorWriterTitleDto> directorWriterCache = null;

    public PagedResponse<DirectorWriterTitleDto> getDirectorWriterTitles(int page, int size) {
        ensureReady();

        if (directorWriterCache == null) {
            synchronized (this) {
                if (directorWriterCache == null) {
                    directorWriterCache = computeDirectorWriterTitles();
                }
            }
        }

        int total = directorWriterCache.size();
        int fromIndex = page * size;

        if (fromIndex >= total) {
            return new PagedResponse<>(Collections.emptyList(), page, size, total,
                    (int) Math.ceil((double) total / size), false);
        }

        int toIndex = Math.min(fromIndex + size, total);
        int totalPages = (int) Math.ceil((double) total / size);

        return new PagedResponse<>(
                directorWriterCache.subList(fromIndex, toIndex),
                page, size, total, totalPages,
                page < totalPages - 1
        );
    }

    private List<DirectorWriterTitleDto> computeDirectorWriterTitles() {
        return store.findTitlesWithSameAliveDirectorWriter().stream()
                .map(this::buildDirectorWriterDto)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(DirectorWriterTitleDto::getStartYear,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DirectorWriterTitleDto::getAverageRating,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Task 3: titles where both actors appeared
    // No pagination — result naturally small (0-50 titles typically)
    // -----------------------------------------------------------------------

    private DirectorWriterTitleDto buildDirectorWriterDto(String tconst) {
        TitleBasicEntity title = store.findTitle(tconst);
        if (title == null) return null;

        TitleRatingEntity rating = store.findRating(tconst);

        return DirectorWriterTitleDto.builder()
                .tconst(tconst)
                .primaryTitle(title.getPrimaryTitle())
                .startYear(title.getStartYear())
                .genres(title.getGenres())
//                .personId(alivePerson.getNconst())
//                .personName(alivePerson.getPrimaryName())
                .averageRating(rating != null ? rating.getAverageRating() : null)
                .numVotes(rating != null ? rating.getNumVotes() : null)
                .build();
    }

    public List<CommonTitleDto> getCommonTitles(String actor1Nconst, String actor2Nconst) {
        ensureReady();

        if (actor1Nconst.equals(actor2Nconst)) {
            throw new InvalidRequestException("actor1 and actor2 must be different persons.");
        }

        // Normalize key — (A,B) and (B,A) return same result, share one cache entry
        String cacheKey = actor1Nconst.compareTo(actor2Nconst) < 0
                ? actor1Nconst + "|" + actor2Nconst
                : actor2Nconst + "|" + actor1Nconst;

        return commonTitlesCache.computeIfAbsent(cacheKey,
                k -> computeCommonTitles(actor1Nconst, actor2Nconst));
    }

    private List<CommonTitleDto> computeCommonTitles(String actor1Nconst, String actor2Nconst) {
        Set<String> titles1 = store.findTitlesByActor(actor1Nconst);
        Set<String> titles2 = store.findTitlesByActor(actor2Nconst);

        Set<String> smaller = titles1.size() <= titles2.size() ? titles1 : titles2;
        Set<String> larger = smaller == titles1 ? titles2 : titles1;

        return smaller.stream()
                .filter(larger::contains)
                .map(this::buildCommonTitleDto)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(CommonTitleDto::getAverageRating,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CommonTitleDto::getNumVotes,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public List<CommonTitleDto> getCommonTitlesByName(String actor1Name, String actor2Name) {
        ensureReady();

        PersonEntity person1 = resolveActor(actor1Name);
        PersonEntity person2 = resolveActor(actor2Name);

        return getCommonTitles(person1.getNconst(), person2.getNconst());
    }

    private PersonEntity resolveActor(String name) {
        List<String> nconsts = store.findPersonsByName(name);
        if (nconsts.isEmpty()) {
            throw new InvalidRequestException("Actor not found: " + name);
        }
        return nconsts.stream()
                .map(store::findPerson)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(p ->
                        store.findTitlesByActor(p.getNconst()).size()))
                .orElseThrow();
    }

    private CommonTitleDto buildCommonTitleDto(String tconst) {
        TitleBasicEntity title = store.findTitle(tconst);
        if (title == null) return null;

        TitleRatingEntity rating = store.findRating(tconst);

        return CommonTitleDto.builder()
                .tconst(tconst)
                .primaryTitle(title.getPrimaryTitle())
                .startYear(title.getStartYear())
                .genres(title.getGenres())
                .averageRating(rating != null ? rating.getAverageRating() : null)
                .numVotes(rating != null ? rating.getNumVotes() : null)
                .build();
    }

    // -----------------------------------------------------------------------
    // Task 4: best title per year for a given genre
    // No pagination — max ~130 rows (one per year)
    // Cached per genre — stable result, expensive to compute
    // -----------------------------------------------------------------------
    @Cacheable(value = "bestTitlesByGenre", key = "#genre")
    public List<BestTitlePerYearDto> getBestTitlesByGenre(String genre) {
        ensureReady();

        Set<String> tconsts = store.findTitlesByGenre(genre);
        if (tconsts.isEmpty()) {
            return Collections.emptyList();
        }

        // Group by year, pick best per year in one stream pass
        Map<Integer, BestTitlePerYearDto> bestByYear = new HashMap<>();

        for (String tconst : tconsts) {
            TitleBasicEntity title = store.findTitle(tconst);
            if (title == null || title.getStartYear() == null) continue;

            TitleRatingEntity rating = store.findRating(tconst);
            if (rating == null) continue;

            int year = title.getStartYear();
            BestTitlePerYearDto current = bestByYear.get(year);

            if (current == null || isBetter(rating, current)) {
                bestByYear.put(year, buildBestTitleDto(title, rating));
            }
        }

        return bestByYear.values().stream()
                .sorted(Comparator.comparingInt(BestTitlePerYearDto::getYear).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Returns true if the challenger rating beats the current best.
     * Primary: higher averageRating. Secondary: higher numVotes.
     */
    private boolean isBetter(TitleRatingEntity challenger, BestTitlePerYearDto current) {
        int ratingCmp = Double.compare(
                challenger.getAverageRating(),
                current.getAverageRating() != null ? current.getAverageRating() : 0.0);
        if (ratingCmp != 0) return ratingCmp > 0;
        return challenger.getNumVotes() > (current.getNumVotes() != null ? current.getNumVotes() : 0);
    }

    private BestTitlePerYearDto buildBestTitleDto(TitleBasicEntity title, TitleRatingEntity rating) {
        return BestTitlePerYearDto.builder()
                .year(title.getStartYear())
                .tconst(title.getTconst())
                .primaryTitle(title.getPrimaryTitle())
                .genres(title.getGenres())
                .averageRating(rating.getAverageRating())
                .numVotes(rating.getNumVotes())
                .build();
    }

    private void ensureReady() {
        if (!dataLoadStateService.isReady()) {
            throw new DataNotReadyException();
        }
    }
}
