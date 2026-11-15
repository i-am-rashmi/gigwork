package com.gigwork.core.hiring.web.dto;

/**
 * @param note optional, at most 500 characters, no contact details
 * @param acknowledgeBusy true to apply although the gig overlaps your calendar (after a first attempt was refused)
 */
public record ApplyRequest(String note, Boolean acknowledgeBusy) {
}
