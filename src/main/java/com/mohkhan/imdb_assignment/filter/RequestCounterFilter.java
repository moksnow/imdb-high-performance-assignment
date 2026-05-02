package com.mohkhan.imdb_assignment.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Moh Khandan
 * Date: 5/1/2026
 * Time: 5:11 PM
 */
@Component
public class RequestCounterFilter extends OncePerRequestFilter {

    private final AtomicLong count = new AtomicLong(0);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        count.incrementAndGet();
        chain.doFilter(request, response);
    }

    public long getCount() {
        return count.get();
    }
}

