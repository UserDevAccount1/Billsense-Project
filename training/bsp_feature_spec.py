"""
BSP New Generation Currency (NGC) — approximate security-feature locations, used ONLY to
sanity-check the empirically-measured reference geometry (training/build_reference_geometry.py).

Coordinates are NORMALISED to the banknote FRONT, landscape orientation:
  x = 0.0 (left edge) .. 1.0 (right edge),  y = 0.0 (top) .. 1.0 (bottom).
The hero portrait sits on the LEFT; the large blank watermark/see-through area is on the RIGHT.

These are deliberately COARSE (qualitative) — each entry is an expected centre + an acceptance
radius. The authoritative positions come from the empirical aggregation; BSP is only used to FLAG
empirical medians that land grossly outside the expected region. Source: BSP NGC banknote
security-features reference (bsp.gov.ph) + standard NGC layout.
"""

# feature -> (expected_cx, expected_cy, acceptance_radius)
_COMMON = {
    "watermark":          (0.80, 0.50, 0.25),  # blank area, right
    "see_through_mark":   (0.78, 0.30, 0.25),  # registration device, upper-right blank area
    "security_thread":    (0.50, 0.50, 0.30),  # embedded vertical thread (windowed on high denoms)
    "serial_number":      (0.70, 0.20, 0.35),  # horizontal upper-right + vertical lower-left
    "value":              (0.20, 0.80, 0.35),  # large value numeral (corners)
    "concealed_value":    (0.60, 0.80, 0.30),  # tilt-reveal concealed value (lower band)
}

# high-denomination-only optical features (PHP 500 / 1000)
_HIGH = {
    "optically_variable_ink": (0.85, 0.82, 0.25),  # OVI value panel, lower-right
    "enhanced_value_panel":   (0.85, 0.82, 0.25),  # enhanced value panel, lower-right (500/1000)
    "ovd":                    (0.90, 0.50, 0.25),  # OVD reflective patch (1000)
}

HIGH_DENOMS = {"500", "1000"}


def expected_region(denomination: str, feature: str):
    """Return (cx, cy, radius) for a feature on this denomination, or None if not expected."""
    if feature in _COMMON:
        return _COMMON[feature]
    if denomination in HIGH_DENOMS and feature in _HIGH:
        return _HIGH[feature]
    return None


def bsp_consistent(denomination: str, feature: str, cx: float, cy: float) -> bool:
    """True if the empirical centre is within the BSP acceptance region (or no spec to check)."""
    reg = expected_region(denomination, feature)
    if reg is None:
        return True  # nothing to check against -> don't flag
    ex, ey, r = reg
    return ((cx - ex) ** 2 + (cy - ey) ** 2) ** 0.5 <= r
