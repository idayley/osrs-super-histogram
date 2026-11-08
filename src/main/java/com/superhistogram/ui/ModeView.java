package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import java.awt.Graphics2D;

/** One mode's painting: what goes on the timeline and in the right readout. */
public interface ModeView
{
	void paintTimeline(Graphics2D g, BarLayout l, BarState s);

	void paintRight(Graphics2D g, BarLayout l, BarState s);
}
