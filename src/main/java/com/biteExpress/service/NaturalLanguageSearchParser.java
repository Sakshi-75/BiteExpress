package com.biteExpress.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A deliberately small grammar for fields the restaurant document can actually answer. */
@Component
public class NaturalLanguageSearchParser {
    private static final Pattern RANKING = Pattern.compile("\\b(?:best|top\\s+rated|top|highest\\s+rated)\\b");
    private static final Pattern PROXIMITY = Pattern.compile("\\b(?:nearest|closest|near\\s+me|nearby)\\b");
    private static final Pattern COST = Pattern.compile(
            "\\b(?:under|below|up\\s+to|at\\s+most)\\s+(?:(rs|inr|rupees)\\s*)?(\\d{1,9})" +
                    "(?:\\s*(rs|inr|rupees))?(?:\\s+for\\s+(?:two|2))?\\b");
    private static final Pattern RADIUS = Pattern.compile(
            "\\bwithin\\s+(\\d+(?:\\.\\d+)?)\\s*(?:km|kilometers?|kilometres?)\\b");
    private static final Pattern DISH = Pattern.compile("\\b(?:dish(?:es)?|menu|menu\\s+item|order|ordering)\\b");
    private static final Pattern FILLER = Pattern.compile(
            "\\b(?:find|show|me|the|some|please|restaurants?|places?|to|eat)\\b");
    private static final List<String> CUISINES = List.of(
            "North Indian", "South Indian", "Fast Food", "Street Food", "Healthy Food", "Finger Food",
            "Indian", "Chinese", "Italian", "Mughlai", "Continental", "Cafe", "Bakery", "Desserts",
            "American", "Pizza", "Thai", "Asian", "Mexican", "Biryani", "Seafood", "Japanese",
            "Mediterranean", "Sushi", "Lebanese", "Burger", "Sandwich", "Breakfast", "BBQ");

    public Intent parse(String query) {
        if (query == null || query.isBlank() || query.length() > 250)
            throw new InvalidRestaurantSearchException("q must contain a supported request of at most 250 characters");
        String remaining = query.toLowerCase(Locale.ROOT).replace("₹", "rs")
                .replaceAll("[?!.:,;\\-]", " ").replaceAll("\\s+", " ").strip();
        if (DISH.matcher(remaining).find())
            throw new InvalidRestaurantSearchException("Dish and menu-item search is unavailable; search by cuisine");

        boolean best = RANKING.matcher(remaining).find();
        boolean nearest = PROXIMITY.matcher(remaining).find();
        if (best && nearest)
            throw new InvalidRestaurantSearchException("Best-rated and nearest are ambiguous together");
        remaining = RANKING.matcher(remaining).replaceAll(" ");
        remaining = PROXIMITY.matcher(remaining).replaceAll(" ");

        Integer maxCost = null;
        String currency = null;
        Matcher cost = COST.matcher(remaining);
        if (cost.find()) {
            maxCost = Integer.parseInt(cost.group(2));
            if (cost.group(1) != null || cost.group(3) != null) currency = "INR";
            remaining = cost.replaceFirst(" ");
        }
        Double radiusKm = null;
        Matcher radius = RADIUS.matcher(remaining);
        if (radius.find()) {
            radiusKm = Double.parseDouble(radius.group(1));
            remaining = radius.replaceFirst(" ");
        }

        remaining = FILLER.matcher(remaining).replaceAll(" ").replaceAll("\\s+", " ").strip();
        String city = null;
        Matcher in = Pattern.compile("\\bin\\b").matcher(remaining);
        if (in.find()) {
            int start = in.start();
            int end = in.end();
            if (in.find()) throw new InvalidRestaurantSearchException("Only one city can be inferred");
            String after = remaining.substring(end).strip();
            Matcher afterCuisine = Pattern.compile("\\b(?:serving|with)\\b").matcher(after);
            if (afterCuisine.find()) {
                city = after.substring(0, afterCuisine.start()).strip();
                remaining = remaining.substring(0, start) + " " + after.substring(afterCuisine.end());
            } else {
                city = after;
                remaining = remaining.substring(0, start);
            }
            if (!city.matches("[a-z][a-z '\\-]{0,79}") || city.matches(".*\\b(?:and|or)\\b.*"))
                throw new InvalidRestaurantSearchException("City request is ambiguous or unsupported");
            city = titleCase(city);
        }

        remaining = remaining.replaceAll("\\b(?:serving|with|cuisine)\\b", " ")
                .replaceAll("\\s+", " ").strip();
        String cuisine = null;
        if (!remaining.isEmpty()) {
            String candidate = remaining;
            String originalCandidate = candidate;
            if (candidate.endsWith(" food") && CUISINES.stream().noneMatch(value -> value.equalsIgnoreCase(originalCandidate)))
                candidate = candidate.substring(0, candidate.length() - " food".length());
            String matchedCuisine = candidate;
            cuisine = CUISINES.stream().filter(value -> value.equalsIgnoreCase(matchedCuisine)).findFirst()
                    .orElseThrow(() -> new InvalidRestaurantSearchException(
                            "Unsupported cuisine or dish request; use a supported cuisine label"));
        }
        if (city == null && cuisine == null && maxCost == null && radiusKm == null && !best && !nearest)
            throw new InvalidRestaurantSearchException("No supported city, cuisine, rating, distance, or cost intent found");
        return new Intent(city, cuisine, best ? 0.0 : null, maxCost, currency, radiusKm,
                best ? "rating" : nearest ? "distance" : null,
                best ? "desc" : nearest ? "asc" : null,
                nearest || radiusKm != null);
    }

    private String titleCase(String text) {
        StringBuilder result = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    public record Intent(String city, String cuisine, Double minRating, Integer maxCostForTwo, String currency,
                         Double radiusKm, String sortBy, String sortDir, boolean requiresCoordinates) {}
}
