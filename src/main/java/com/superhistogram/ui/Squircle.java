package com.superhistogram.ui;

import java.awt.geom.Path2D;

/**
 * A rounded rectangle with smooth ("squircle") corners: each corner is one cubic curve that eases
 * out of the straight edge instead of a circular arc, so there is no visible kink where the edge
 * meets the curve.
 */
final class Squircle
{
	/** How far along each edge a corner reaches, as a multiple of its radius. */
	private static final float REACH = 1.6f;
	/** Where the curve's handles sit between the edge point and the corner (0 = edge point, 1 = corner). */
	private static final float PULL = 0.82f;

	private Squircle()
	{
	}

	static Path2D.Float of(float x, float y, float w, float h, float r)
	{
		float p = Math.min(r * REACH, Math.min(w, h) / 2f);
		float c = p * (1f - PULL);
		float x1 = x + w, y1 = y + h;
		Path2D.Float s = new Path2D.Float();
		s.moveTo(x + p, y);
		s.lineTo(x1 - p, y);
		s.curveTo(x1 - c, y, x1, y + c, x1, y + p);
		s.lineTo(x1, y1 - p);
		s.curveTo(x1, y1 - c, x1 - c, y1, x1 - p, y1);
		s.lineTo(x + p, y1);
		s.curveTo(x + c, y1, x, y1 - c, x, y1 - p);
		s.lineTo(x, y + p);
		s.curveTo(x, y + c, x + c, y, x + p, y);
		s.closePath();
		return s;
	}
}
