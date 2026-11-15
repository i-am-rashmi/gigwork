package com.gigwork.core.hiring.application;

import java.util.List;

/** One page of the caller's applications and, when there is more, the opaque cursor that asks for the next page. */
public record MyApplicationsPage(List<MyApplication> items, String nextCursor) {
}
