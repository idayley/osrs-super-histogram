package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.model.Timeline;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class GeneralPrayerColourTest
{
	@Test
	public void prayerPillsUseTheirPrayerColourInGeneralMode()
	{
		BarLayout l = BarLayout.of(519, 62);
		Timeline t = new Timeline(64);
		t.openSpan("pray-melee", 2);
		t.closeSpan("pray-melee", 8);
		BarState s = BarState.builder().timeline(t).nowTick(10).mode(Mode.GENERAL).build();
		BufferedImage img = new BufferedImage(519, 62, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		new BarPainter(null).paint(g, l, s, 0L);
		g.dispose();
		int x = (int) (TimelinePainter.x(l, s, 5) + l.tickWidth / 2f);
		int y = (int) (l.laneY(Lane.PRAYER) + l.laneH(Lane.PRAYER) / 2f);
		assertEquals(Palette.PRAYER_MELEE.getRGB(), img.getRGB(x, y));
	}
}
