package com.mohkhan.imdb_assignment.exception;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:10 PM
 */
public class DataNotReadyException extends RuntimeException {

    public DataNotReadyException() {
        super("Data is still loading. Please try again shortly.");
    }
}
