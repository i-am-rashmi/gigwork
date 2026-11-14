package com.gigwork.core.reference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Turns "a locality ID or a map point" into a {@link ResolvedLocation} (FR-5): the one place that rounds map points,
 * applies the distance rule and picks the label, shared by Talent profiles and gigs.
 *
 * <p>It only uses {@link ReferenceData}, so it needs no database of its own.
 */
public class LocationResolver {

	private final ReferenceData reference;
	private final double maxDistanceFromZoneKm;
	private final double labelRadiusMeters;

	/**
	 * @param maxDistanceFromZoneKm a map point farther than this from the nearest live zone is refused
	 * @param labelRadiusMeters a map point is labelled with the nearest locality within this distance, else the zone
	 */
	public LocationResolver(ReferenceData reference, double maxDistanceFromZoneKm, double labelRadiusMeters) {
		this.reference = reference;
		this.maxDistanceFromZoneKm = maxDistanceFromZoneKm;
		this.labelRadiusMeters = labelRadiusMeters;
	}

	/**
	 * @param localityId a live locality, or null
	 * @param lat a map point's latitude, or null
	 * @param lng a map point's longitude, or null
	 * @throws InvalidLocationException unless exactly one of a locality and a (complete) map point is given and it is
	 *         a place we serve
	 */
	public ResolvedLocation resolve(UUID localityId, Double lat, Double lng) {
		boolean hasLocality = localityId != null;
		boolean hasPoint = lat != null || lng != null;
		if (hasLocality == hasPoint) {
			throw new InvalidLocationException("Give either a locality or a map point, not both and not neither.");
		}
		try {
			if (hasLocality) {
				LocalityInfo locality = reference.locality(localityId).orElseThrow(
						() -> new InvalidLocationException("That locality does not exist or is no longer offered."));
				AreaResolution area = reference.resolveArea(locality.lat(), locality.lng());
				return new ResolvedLocation(locality.lat(), locality.lng(), area.cityId(), area.zoneId(),
						locality.name(), locality.id());
			}
			if (lat == null || lng == null) {
				throw new InvalidLocationException("A map point needs both a latitude and a longitude.");
			}
			double roundedLat = round3(lat);
			double roundedLng = round3(lng);
			AreaResolution area = reference.resolveArea(roundedLat, roundedLng);
			if (area.distanceToZoneMeters() > maxDistanceFromZoneKm * 1000) {
				throw new InvalidLocationException("That point is more than " + trim(maxDistanceFromZoneKm)
						+ " km from the nearest area we serve. Choose a place closer to a city we serve.");
			}
			String label = reference.nearestLocality(roundedLat, roundedLng, labelRadiusMeters)
					.map(LocalityInfo::name)
					.orElseGet(() -> reference.zone(area.zoneId()).map(ZoneInfo::name).orElse("Unknown area"));
			return new ResolvedLocation(roundedLat, roundedLng, area.cityId(), area.zoneId(), label, null);
		}
		catch (InvalidCoordinatesException e) {
			throw new InvalidLocationException("Those coordinates are not a valid place on Earth.");
		}
		catch (NoZonesDefinedException e) {
			throw new InvalidLocationException("We don't serve any area yet.");
		}
	}

	/** Map points are kept to 3 decimals (about 110 m): enough for matching, too coarse to find a home. */
	public static double round3(double value) {
		return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
	}

	private static String trim(double km) {
		return km == Math.rint(km) ? Long.toString((long) km) : Double.toString(km);
	}

}
