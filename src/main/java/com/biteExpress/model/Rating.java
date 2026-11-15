package com.biteExpress.model;

public record Rating(
        Double average,
        String color,
        String text,
        Integer votes
) {
}
