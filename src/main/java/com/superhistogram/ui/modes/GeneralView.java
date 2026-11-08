package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.XpDrop;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.ModeView;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Graphics2D;

/** Baseline view: walk/run/action pills, XP and hits on top, clicks below; right = session XP. */
public final class GeneralView implements ModeView
{
	@Override
	public void paintTimeline(Graphics2D g, BarLayout l, BarState s)
	{
		TimelinePainter.spans(g, l, s);
		TimelinePainter.xp(g, l, s);
		TimelinePainter.hits(g, l, s);
		TimelinePainter.clicks(g, l, s);
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		long session = s.xp == null ? 0 : s.xp.sessionXp();
		XpDrop last = s.xp == null ? null : s.xp.lastDrop();
		String small = "this session" + (last == null ? "" : " · last: " + last.skill + " +" + last.amount);
		ReadoutPainter.right(g, l, s, ReadoutPainter.compact(session) + " xp", Palette.WHITE, small);
	}
}
