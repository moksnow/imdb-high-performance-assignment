package com.mohkhan.imdb_assignment.service.utils;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;

/**
 * @author M_Khandan
 * Date: 5/2/2026
 * Time: 4:24 PM
 */
@Component
public class TsvFileUtil {
    public BufferedReader openGzip(String datasetPath, String fileName) throws Exception {
        Path file = Paths.get(datasetPath, fileName);
        return new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(file), 65536),
                        StandardCharsets.UTF_8
                ),
                1024 * 1024
        );
    }
}
