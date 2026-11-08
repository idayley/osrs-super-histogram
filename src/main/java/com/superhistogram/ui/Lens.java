package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import java.awt.Graphics2D;
import java.util.Arrays;

/**
 * One mode as a lens over the shared {@link BaseTimeline}: it dims the lanes it doesn't care about, adds its
 * own helpers on top and sets the right readout. It never moves, hides or re-scales the base.
 */
public interface Lens
{
	/** Lane alpha of a dimmed lane. */
	float DIM = 0.3f;

	/** Alpha per Lane.ordinal(); 1 = full, DIM = 0.3. */
	float[] emphasis(BarState s);

	/** Mode helpers over the base timeline (timeline coordinates, same translation as the base). */
	void overlay(Graphics2D g, BarLayout l, BarState s);

	void paintRight(Graphics2D g, BarLayout l, BarState s);

	/** Every lane at full strength except {@code dimmed}, which are at {@link #DIM}. */
	static float[] dimming(Lane... dimmed)
	{
		float[] a = full();
		for (Lane lane : dimmed)
		{
			a[lane.ordinal()] = DIM;
		}
		return a;
	}

	/** Every lane at full strength. */
	static float[] full()
	{
		float[] a = new float[Lane.values().length];
		Arrays.fill(a, 1f);
		return a;
	}
}
