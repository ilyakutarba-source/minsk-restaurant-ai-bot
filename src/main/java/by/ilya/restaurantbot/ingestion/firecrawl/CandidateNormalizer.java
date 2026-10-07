package by.ilya.restaurantbot.ingestion.firecrawl;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ArrayList;

public final class CandidateNormalizer {
    private CandidateNormalizer() { }

    public static String text(String value) {
        if (value == null) return null;
        var result = value.replaceAll("[\\p{Z}\\s]+", " ").strip();
        return result.isEmpty() ? null : result;
    }

    public static String normalized(String value) {
        var clean = text(value);
        if (clean == null) return "";
        var normalized = text(clean.toLowerCase(Locale.ROOT).replaceAll("\\p{P}+", " "));
        return normalized == null ? "" : normalized;
    }

    public record Identity(String name, String address) { }
    public record Duplicate(int firstIndex, int duplicateIndex) { }

    public static Optional<Identity> identity(RestaurantImportCandidate candidate) {
        var name = normalized(candidate.name());
        var address = normalized(candidate.address());
        return name.isEmpty() || address.isEmpty() ? Optional.empty() : Optional.of(new Identity(name, address));
    }

    /** Reports duplicates; never silently merges or approves candidates. Indexes are zero based. */
    public static List<Duplicate> duplicates(List<RestaurantImportCandidate> candidates) {
        var first = new HashMap<Identity, Integer>();
        var duplicates = new ArrayList<Duplicate>();
        for (int i = 0; i < candidates.size(); i++) {
            var identity = identity(candidates.get(i));
            if (identity.isPresent()) {
                var previous = first.putIfAbsent(identity.get(), i);
                if (previous != null) duplicates.add(new Duplicate(previous, i));
            }
        }
        return List.copyOf(duplicates);
    }

    static URI source(String value) {
        try {
            var uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null) {
                return uri;
            }
        } catch (IllegalArgumentException | NullPointerException ignored) { }
        throw new IllegalArgumentException("A public HTTPS source without credentials, query or fragment is required");
    }
}
