package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.ModeView;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Graphics2D;

/** AFK view: one long "idle" pill since the last input; right = mint countdown to idle logout. */
public final class AfkView implements ModeView
{
	private static final int LOGOUT_SECONDS = 300;
	private static final double TICK_SECONDS = 0.6;

	@Override
	public void paintTimeline(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.idle != null && s.nowTick >= 0)
		{
			int idleSeconds = LOGOUT_SECONDS - s.idle.secondsToLogout();
			int ticks = (int) Math.round(idleSeconds / TICK_SECONDS);
			float x1 = TimelinePainter.nowX(l);
			float x0 = Math.max(l.timelineX, x1 - ticks * l.tickWidth);
			TimelinePainter.pill(g, l, x0, x1, Palette.TEXT, true,
				"idle " + ReadoutPainter.minSec(idleSeconds) + " since last click");
		}
		TimelinePainter.xp(g, l, s);
		TimelinePainter.hits(g, l, s);
		TimelinePainter.clicks(g, l, s);
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.idle == null)
		{
			return;
		}
		ReadoutPainter.right(g, l, s, ReadoutPainter.minSec(s.idle.secondsToLogout()), Palette.MINT, "until idle logout");
	}
}
