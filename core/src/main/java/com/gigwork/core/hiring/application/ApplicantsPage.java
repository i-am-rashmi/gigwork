package com.gigwork.core.hiring.application;

import java.util.List;

/** One page of a gig's applicants and, when there is more, the opaque cursor that asks for the next page. */
public record ApplicantsPage(List<Applicant> items, String nextCursor) {
}
