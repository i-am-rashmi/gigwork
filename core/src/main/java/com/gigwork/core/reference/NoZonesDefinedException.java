package com.gigwork.core.reference;

/** No live city has a zone, so a point cannot be assigned to any (a configuration problem, not bad input). */
public class NoZonesDefinedException extends RuntimeException {

	public NoZonesDefinedException() {
		super("no live city has a zone");
	}

}
