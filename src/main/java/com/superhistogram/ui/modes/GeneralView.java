package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.XpDrop;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import java.awt.Graphics2D;

/** Baseline lens: every lane at full strength, no helpers; right = session XP. */
public final class GeneralView implements Lens
{
	@Override
	public float[] emphasis(BarState s)
	{
		return Lens.full();
	}

	@Override
	public void overlay(Graphics2D g, BarLayout l, BarState s)
	{
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
