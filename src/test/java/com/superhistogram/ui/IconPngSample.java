package com.superhistogram.ui;

import static org.junit.Assume.assumeTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Hit;
import com.superhistogram.model.IdleTracker;
import com.superhistogram.model.Mode;
import com.superhistogram.model.SessionClock;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpTracker;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.Test;

/**
 * Writes the Plugin Hub icon (icon.png, 48 x 72 at most) from the real bar painter: a slice of the lazy-flick
 * bar with prayer pills and a hit line on the dark ground. Needs {@code -PrenderDir=dir}; copy the result to
 * the repo root.
 */
public class IconPngSample
{
	static final int W = 48, H = 72;

	@Test
	public void writeHubIcon() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		int now = SampleStates.NOW_TICK;
		Timeline t = new Timeline(512);
		t.openSpan("pray-melee", now - 3).endTick = now - 3;
		t.openSpan("pray-ranged", now - 2).endTick = now - 2;
		t.openSpan("pray-magic", now - 1).endTick = now - 1;
		t.at(now - 2).hits.add(new Hit(14, false));
		t.at(now);
		BarState s = BarState.builder()
			.timeline(t).nowTick(now).xp(new XpTracker(t)).idle(new IdleTracker(60)).clock(new SessionClock())
			.runEnergyPct(64).modeName("Lazy flick").modeSub("").showRunEnergy(false).showLoginTime(false)
			.mode(Mode.LAZY_FLICK).nowMs(SampleStates.NOW_MS).build();
		BarLayout l = BarLayout.of(1026, 62);
		BufferedImage bar = BarRenderSmokeTest.render(1026, 62, s, null);
		// three ticks are 96 px wide: take them with a little margin, then halve the slice into the icon
		int x0 = Math.round(TimelinePainter.x(l, s, now - 3)) - 4;
		BufferedImage slice = bar.getSubimage(x0, 0, 104, 62);
		BufferedImage icon = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = icon.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
		g.setColor(new Color(0x14171b));
		g.fillRect(0, 0, W, H);
		g.drawImage(slice, 0, (H - 31) / 2, W, 31, null);
		g.dispose();
		ImageIO.write(icon, "png", new File(dir, "icon.png"));
	}
}
