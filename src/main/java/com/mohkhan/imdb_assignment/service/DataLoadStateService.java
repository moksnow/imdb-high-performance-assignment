package com.mohkhan.imdb_assignment.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:33 PM
 */
@Service
public class DataLoadStateService {

    private final AtomicBoolean ready = new AtomicBoolean(false);

    public boolean isReady() {
        return ready.get();
    }

    public void markReady() {
        ready.set(true);
    }
}
