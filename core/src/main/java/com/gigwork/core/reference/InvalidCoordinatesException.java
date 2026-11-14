package com.gigwork.core.reference;

/** A latitude outside -90..90 or a longitude outside -180..180 (or not a number). */
public class InvalidCoordinatesException extends RuntimeException {

	public InvalidCoordinatesException(double lat, double lng) {
		super("invalid coordinates: lat=" + lat + ", lng=" + lng);
	}

}
