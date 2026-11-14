package com.gigwork.core.gigs.application;

import java.util.List;

/** One page of gigs and, when there is more, the opaque cursor that asks for the next page. */
public record GigPage(List<GigView> items, String nextCursor) {
}
