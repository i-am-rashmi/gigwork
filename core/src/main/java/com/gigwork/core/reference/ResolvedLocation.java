package com.gigwork.core.reference;

import java.util.UUID;

/**
 * A place chosen by a user, turned into what the platform stores: coordinates, the city and zone they fall in, and a
 * label to show instead of an address.
 *
 * @param lat latitude: the locality's centre, or the chosen map point rounded to 3 decimals
 * @param lng longitude, likewise
 * @param cityId the live city the place belongs to
 * @param zoneId the live zone the place is in, or the nearest one
 * @param label the locality name, or the nearest locality's, or the zone's
 * @param localityId the chosen locality, null when a map point was chosen
 */
public record ResolvedLocation(double lat, double lng, UUID cityId, UUID zoneId, String label, UUID localityId) {
}
