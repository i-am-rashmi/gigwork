package com.gigwork.core.hiring.application;

import java.util.List;

/** One page of the Shortlisted tab and, when there is more, the opaque cursor that asks for the next page. */
public record ShortlistedPage(List<ShortlistedItem> items, String nextCursor) {
}
