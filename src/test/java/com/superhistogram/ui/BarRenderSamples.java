package com.superhistogram.ui;

import static org.junit.Assume.assumeTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.Test;

/**
 * Writes sample renders for eyeballing against the mockups. Runs only when
 * {@code -PrenderDir=/some/dir} is passed to gradle.
 */
public class BarRenderSamples
{
	@Test
	public void writeSamples() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		BarState general = SampleStates.general();
		int[][] sizes = {{1026, 62}, {760, 46}, {620, 32}, {360, 28}};
		for (int[] wh : sizes)
		{
			write(dir, "task8-render-" + wh[0] + "x" + wh[1] + ".png", wh[0], wh[1], general);
		}
		write(dir, "task8-render-afk-1026x62.png", 1026, 62,
			SampleStates.afk(general));
		write(dir, "task8-render-minimal-1026x62.png", 1026, 62,
			SampleStates.withMode(general, Mode.MINIMAL, "XP", "minimal"));
	}

	/** Lazy-flick contact sheet: 1026x62, 720x62 and 400x36 stacked, scaled 2x. Needs -PrenderDir=dir; writes task12-contact.png. */
	@Test
	public void writeLazyFlickContactSheet() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		String file = new File(dir, "task12-contact.png").getPath();
		BarState s = SampleStates.lazyFlick();
		int[][] sizes = {{1026, 62}, {720, 62}, {400, 36}};
		int pad = 8, total = pad;
		for (int[] wh : sizes)
		{
			total += (wh[1] + pad) * 2;
		}
		java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(1026 * 2 + 2 * pad, total,
			java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = sheet.createGraphics();
		g.setColor(new java.awt.Color(0x2b3521));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		int y = pad;
		for (int[] wh : sizes)
		{
			java.awt.image.BufferedImage img = BarRenderSmokeTest.render(wh[0], wh[1], s, null);
			g.drawImage(img, pad, y, img.getWidth() * 2, img.getHeight() * 2, null);
			y += (wh[1] + pad) * 2;
		}
		g.dispose();
		ImageIO.write(sheet, "png", new File(file));
	}

	/** 1-tick contact sheet: 1026x62, 720x62 and 400x36 stacked, scaled 2x. Writes task13-contact.png. */
	@Test
	public void writeOneTickContactSheet() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		BarState s = SampleStates.oneTick(com.superhistogram.model.combat.FlickClassifier.Kind.OK);
		int[][] sizes = {{1026, 62}, {720, 62}, {400, 36}};
		int pad = 8, total = pad;
		for (int[] wh : sizes)
		{
			total += (wh[1] + pad) * 2;
		}
		java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(1026 * 2 + 2 * pad, total,
			java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = sheet.createGraphics();
		g.setColor(new java.awt.Color(0x2b3521));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		int y = pad;
		for (int[] wh : sizes)
		{
			java.awt.image.BufferedImage img = BarRenderSmokeTest.render(wh[0], wh[1], s, null);
			g.drawImage(img, pad, y, img.getWidth() * 2, img.getHeight() * 2, null);
			y += (wh[1] + pad) * 2;
		}
		g.dispose();
		ImageIO.write(sheet, "png", new File(dir, "task13-contact.png"));
	}

	/**
	 * Production contact sheet: for each size (1026x62, 720x62, 400x36) the RUNNING sample and the
	 * DONE sample (with "+N" before the bank click, i.e. BANKED), stacked and scaled 2x. Writes task16-contact.png.
	 */
	@Test
	public void writeProductionContactSheet() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		SampleStates.ProdPhase[] phases = {SampleStates.ProdPhase.RUNNING, SampleStates.ProdPhase.DONE,
			SampleStates.ProdPhase.BANKED};
		int[][] sizes = {{1026, 62}, {720, 62}, {400, 36}};
		int pad = 8, total = pad;
		for (int[] wh : sizes)
		{
			total += (wh[1] + pad) * 2 * phases.length;
		}
		java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(1026 * 2 + 2 * pad, total,
			java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = sheet.createGraphics();
		g.setColor(new java.awt.Color(0x2b3521));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		int y = pad;
		for (int[] wh : sizes)
		{
			for (SampleStates.ProdPhase p : phases)
			{
				java.awt.image.BufferedImage img = BarRenderSmokeTest.render(wh[0], wh[1], SampleStates.cooking(p), null);
				g.drawImage(img, pad, y, img.getWidth() * 2, img.getHeight() * 2, null);
				y += (wh[1] + pad) * 2;
			}
		}
		g.dispose();
		ImageIO.write(sheet, "png", new File(dir, "task16-contact.png"));
	}

	private static void write(String dir, String name, int w, int h, BarState s) throws IOException
	{
		ImageIO.write(BarRenderSmokeTest.render(w, h, s, null), "png", new File(dir, name));
	}
}
