package com.biteExpress.model;

public record Address(
        String line1,
        String city,
        String locality,
        String localityVerbose
) {
}