package com.gigwork.core.reference.application;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.gigwork.core.reference.LocalityInfo;

/**
 * Typeahead over localities, entirely in memory. Digits match the start of a PIN. Text must match the start of
 * words of the name (every typed word must start some word of the name), ignoring case and accents. Results
 * whose name starts with the text come first; ties are broken by name and id so the order never changes.
 */
final class LocalitySearch {

	private record Entry(LocalityInfo locality, String normalisedName, List<String> words) {
	}

	private final List<Entry> entries;

	LocalitySearch(List<LocalityInfo> localities) {
		List<Entry> list = new ArrayList<>(localities.size());
		for (LocalityInfo locality : localities) {
			String normalised = normalise(locality.name());
			list.add(new Entry(locality, normalised, List.of(normalised.split(" "))));
		}
		this.entries = List.copyOf(list);
	}

	int size() {
		return entries.size();
	}

	List<LocalityInfo> search(String text, int limit) {
		if (text == null || limit <= 0) {
			return List.of();
		}
		String trimmed = text.strip();
		if (trimmed.isEmpty()) {
			return List.of();
		}
		if (trimmed.chars().allMatch(Character::isDigit)) {
			return entries.stream().filter(e -> e.locality().pin() != null && e.locality().pin().startsWith(trimmed))
					.sorted(Comparator.comparing((Entry e) -> e.locality().pin()).thenComparing(e -> e.normalisedName())
							.thenComparing(e -> e.locality().id()))
					.limit(limit).map(Entry::locality).toList();
		}
		String query = normalise(trimmed);
		if (query.isEmpty()) {
			return List.of();
		}
		List<String> tokens = List.of(query.split(" "));
		return entries.stream().filter(e -> matches(e, tokens))
				.sorted(Comparator.comparing((Entry e) -> !e.normalisedName().startsWith(query))
						.thenComparing(e -> e.normalisedName()).thenComparing(e -> e.locality().id()))
				.limit(limit).map(Entry::locality).toList();
	}

	private static boolean matches(Entry entry, List<String> tokens) {
		for (String token : tokens) {
			boolean found = false;
			for (String word : entry.words()) {
				if (word.startsWith(token)) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	/** Lower case, accents removed, anything that is not a letter or digit becomes a single space. */
	static String normalise(String text) {
		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
	}

}
