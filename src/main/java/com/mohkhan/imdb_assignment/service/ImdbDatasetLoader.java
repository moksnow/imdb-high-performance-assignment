package com.mohkhan.imdb_assignment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @author Moh Khandan
 * Date: 4/30/2026
 * Time: 5:00 PM
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImdbDatasetLoader implements CommandLineRunner {

    private final ImdbLoadCoordinator loadCoordinator;

    @Value("${app.skip-data-load:false}")
    private boolean skipDataLoad;

    @Override
    public void run(String... args) {
        if (skipDataLoad) {
            log.info("Skipping data load — store already populated");
            return;
        }

        loadCoordinator.loadAllData();
    }
}