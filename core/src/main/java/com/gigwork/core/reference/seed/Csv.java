package com.gigwork.core.reference.seed;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A small RFC 4180 reader (quoted fields, doubled quotes, LF or CRLF) for the seed files; the first row names the columns. */
final class Csv {

	private Csv() {
	}

	static List<Map<String, String>> parse(byte[] bytes, String fileName) {
		List<List<String>> rows = rows(new String(bytes, StandardCharsets.UTF_8), fileName);
		if (rows.isEmpty()) {
			throw new SeedDataException(fileName + " is empty");
		}
		List<String> header = rows.get(0);
		List<Map<String, String>> out = new ArrayList<>();
		for (int i = 1; i < rows.size(); i++) {
			List<String> row = rows.get(i);
			if (row.size() != header.size()) {
				throw new SeedDataException(fileName + " line " + (i + 1) + " has " + row.size() + " fields, expected "
						+ header.size());
			}
			Map<String, String> record = new LinkedHashMap<>();
			for (int c = 0; c < header.size(); c++) {
				record.put(header.get(c), row.get(c));
			}
			out.add(record);
		}
		return out;
	}

	private static List<List<String>> rows(String text, String fileName) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		boolean fieldStarted = false;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (quoted) {
				if (ch == '"') {
					if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
						field.append('"');
						i++;
					}
					else {
						quoted = false;
					}
				}
				else {
					field.append(ch);
				}
			}
			else if (ch == '"' && field.isEmpty()) {
				quoted = true;
				fieldStarted = true;
			}
			else if (ch == ',') {
				row.add(field.toString());
				field.setLength(0);
				fieldStarted = true;
			}
			else if (ch == '\n' || ch == '\r') {
				if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
					i++;
				}
				if (fieldStarted || !field.isEmpty() || !row.isEmpty()) {
					row.add(field.toString());
					rows.add(row);
				}
				row = new ArrayList<>();
				field.setLength(0);
				fieldStarted = false;
			}
			else {
				field.append(ch);
				fieldStarted = true;
			}
		}
		if (quoted) {
			throw new SeedDataException(fileName + " ends inside a quoted field");
		}
		if (fieldStarted || !field.isEmpty() || !row.isEmpty()) {
			row.add(field.toString());
			rows.add(row);
		}
		return rows;
	}

}
