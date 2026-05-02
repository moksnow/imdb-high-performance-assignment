package com.mohkhan.imdb_assignment.controller;

import com.mohkhan.imdb_assignment.filter.RequestCounterFilter;
import com.mohkhan.imdb_assignment.model.dto.BestTitlePerYearDto;
import com.mohkhan.imdb_assignment.model.dto.CommonTitleDto;
import com.mohkhan.imdb_assignment.model.dto.DirectorWriterTitleDto;
import com.mohkhan.imdb_assignment.model.response.ApiResponse;
import com.mohkhan.imdb_assignment.model.response.PagedResponse;
import com.mohkhan.imdb_assignment.service.TitleQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:37 PM
 */

@Tag(name = "Titles", description = "IMDb Query APIs")
@RestController
@RequestMapping("/api/imdb")
@RequiredArgsConstructor
public class TitleQueryController {

    private final TitleQueryService titleQueryService;
    private final RequestCounterFilter requestCounterFilter;

    @GetMapping("/task2/director-writer-titles")
    public ApiResponse<PagedResponse<DirectorWriterTitleDto>> directorWriterTitles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ApiResponse.success(
                titleQueryService.getDirectorWriterTitles(page, size),
                requestCounterFilter.getCount()
        );
    }

    @GetMapping("/task3/common-titles-by-name")
    public ApiResponse<List<CommonTitleDto>> commonTitlesByName(
            @RequestParam String actor1,
            @RequestParam String actor2) {
        return ApiResponse.success(
                titleQueryService.getCommonTitlesByName(actor1, actor2),
                requestCounterFilter.getCount()
        );
    }

    @GetMapping("/task3/common-titles")
    public ApiResponse<List<CommonTitleDto>> commonTitles(
            @RequestParam String actor1,
            @RequestParam String actor2) {

        return ApiResponse.success(
                titleQueryService.getCommonTitles(actor1, actor2),
                requestCounterFilter.getCount()
        );
    }

    @GetMapping("/task4/best-by-genre")
    public ApiResponse<List<BestTitlePerYearDto>> bestByGenre(
            @RequestParam String genre
    ) {
        return ApiResponse.success(
                titleQueryService.getBestTitlesByGenre(genre),
                requestCounterFilter.getCount()
        );
    }

    @GetMapping("/request-count")
    public ApiResponse<Long> requestCount() {
        return ApiResponse.success(
                requestCounterFilter.getCount(),
                requestCounterFilter.getCount()
        );
    }
}
