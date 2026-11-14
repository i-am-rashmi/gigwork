"""Projection helpers. All distances and areas are computed in metres in the city's UTM zone."""

from __future__ import annotations

from pyproj import CRS, Transformer
from shapely.ops import transform

WGS84 = CRS.from_epsg(4326)


def utm_crs_for(lon: float, lat: float) -> CRS:
    """The UTM zone containing the point (EPSG:326xx north, 327xx south)."""
    zone = int((lon + 180) // 6) + 1
    return CRS.from_epsg((32600 if lat >= 0 else 32700) + zone)


def to_metric(geom, crs: CRS):
    t = Transformer.from_crs(WGS84, crs, always_xy=True)
    return transform(t.transform, geom)


def to_wgs84(geom, crs: CRS):
    t = Transformer.from_crs(crs, WGS84, always_xy=True)
    return transform(t.transform, geom)


def xy_to_lonlat(x: float, y: float, crs: CRS) -> tuple[float, float]:
    t = Transformer.from_crs(crs, WGS84, always_xy=True)
    return t.transform(x, y)


def lonlat_to_xy(lon, lat, crs: CRS):
    t = Transformer.from_crs(WGS84, crs, always_xy=True)
    return t.transform(lon, lat)
