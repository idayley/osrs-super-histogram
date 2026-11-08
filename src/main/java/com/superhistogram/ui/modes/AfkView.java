package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Graphics2D;

/** AFK lens: dims the doing lane under one long "idle" pill since the last input; right = mint countdown to idle logout. */
public final class AfkView implements Lens
{
	private static final int LOGOUT_SECONDS = 300;
	private static final double TICK_SECONDS = 0.6;
	/** The idle pill's label needs this much lane height (px). */
	private static final float LABEL_MIN_H = 9f;

	@Override
	public float[] emphasis(BarState s)
	{
		return Lens.full();
	}

	@Override
	public void overlay(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.idle == null || s.timeline == null || s.nowTick < 0 || !l.hasLane(Lane.DOING))
		{
			return;
		}
		int idleSeconds = LOGOUT_SECONDS - s.idle.secondsToLogout();
		int ticks = (int) Math.round(idleSeconds / TICK_SECONDS);
		float x1 = TimelinePainter.liveX(l);
		float x0 = Math.max(l.timelineX, x1 - ticks * l.tickWidth);
		float h = l.laneH(Lane.DOING);
		TimelinePainter.pill(g, l, x0, x1, l.laneY(Lane.DOING), h, Palette.TEXT, true,
			l.rows > 1 && h >= LABEL_MIN_H ? "idle " + ReadoutPainter.minSec(idleSeconds) + " since last click" : null);
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.idle == null)
		{
			return;
		}
		ReadoutPainter.right(g, l, s, ReadoutPainter.minSec(s.idle.secondsToLogout()), Palette.mint(), "until idle logout");
	}
}
