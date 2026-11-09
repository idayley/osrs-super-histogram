package com.superhistogram.ui;

import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Whole-pixel shapes. With {@link #snap} on, rectangles and pills land on whole pixels (at least one wide), so a thin
 * line is one solid pixel rather than two half-strength ones, and stays sharp when the game is stretched.
 */
public final class Px
{
	public static volatile boolean snap = true;

	private Px()
	{
	}

	public static Rectangle2D.Float rect(float x, float y, float w, float h)
	{
		if (!snap)
		{
			return new Rectangle2D.Float(x, y, w, h);
		}
		float[] a = span(x, w), b = span(y, h);
		return new Rectangle2D.Float(a[0], b[0], a[1], b[1]);
	}

	public static RoundRectangle2D.Float round(float x, float y, float w, float h, float aw, float ah)
	{
		if (!snap)
		{
			return new RoundRectangle2D.Float(x, y, w, h, aw, ah);
		}
		float[] a = span(x, w), b = span(y, h);
		return new RoundRectangle2D.Float(a[0], b[0], a[1], b[1], Math.min(aw, b[1]), Math.min(ah, b[1]));
	}

	/** {start, length} on whole pixels; a positive length stays at least 1. */
	private static float[] span(float start, float len)
	{
		if (len <= 0)
		{
			return new float[]{Math.round(start), len};
		}
		int s = Math.round(start), e = Math.round(start + len);
		return new float[]{s, Math.max(1, e - s)};
	}
}
