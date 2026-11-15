package com.gigwork.core.hiring.web.dto;

/** @param reason why the hire is cancelled, 1 to 500 characters; recorded for future reliability scores */
public record CancelHireRequest(String reason) {
}
