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

	/** Multi-XP contact sheet: three samples at 1026x62 plus the melee one at 720x62, stacked, 2x. Writes task17a-contact.png (or -PrenderFile path via superhistogram.renderFile). */
	@Test
	public void writeMultiXpContactSheet() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		Object[][] rows = {{1026, SampleStates.meleeSlayer()}, {1026, SampleStates.alchFletch()},
			{1026, SampleStates.tempoross()}, {720, SampleStates.meleeSlayer()},
			{1026, SampleStates.meleeSlayer(com.superhistogram.model.combat.ActionsRow.ATTACKS)}};
		int pad = 8, total = pad;
		for (int i = 0; i < rows.length; i++)
		{
			total += (62 + pad) * 2;
		}
		java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(1026 * 2 + 2 * pad, total,
			java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = sheet.createGraphics();
		g.setColor(new java.awt.Color(0x2b3521));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		int y = pad;
		for (Object[] r : rows)
		{
			java.awt.image.BufferedImage img = BarRenderSmokeTest.render((Integer) r[0], 62, (BarState) r[1], null);
			g.drawImage(img, pad, y, img.getWidth() * 2, img.getHeight() * 2, null);
			y += (62 + pad) * 2;
		}
		g.dispose();
		ImageIO.write(sheet, "png", new File(dir, "task17a-contact.png"));
	}

	/**
	 * High-alch contact sheet, stacked and scaled 2x. Writes task17b-contact.png (GOLD, XP, BOTH at 1026x62 and
	 * BOTH at 720x62) and task17b-contact-all.png (every top row x right side at 1026x62, plus 1026x46 and 600x30).
	 */
	@Test
	public void writeAlchContactSheet() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		com.superhistogram.model.skill.AlchTopRow[] tops = com.superhistogram.model.skill.AlchTopRow.values();
		com.superhistogram.model.RightSide per = com.superhistogram.model.RightSide.PER_HOUR,
			ses = com.superhistogram.model.RightSide.SESSION;
		java.util.List<Object[]> main = new java.util.ArrayList<>();
		for (com.superhistogram.model.skill.AlchTopRow top : tops)
		{
			main.add(new Object[]{1026, 62, SampleStates.alch(top, per)});
		}
		main.add(new Object[]{720, 62, SampleStates.alch(com.superhistogram.model.skill.AlchTopRow.BOTH, per)});
		sheet(new File(dir, "task17b-contact.png"), main);
		java.util.List<Object[]> all = new java.util.ArrayList<>();
		for (com.superhistogram.model.skill.AlchTopRow top : tops)
		{
			all.add(new Object[]{1026, 62, SampleStates.alch(top, per)});
			all.add(new Object[]{1026, 62, SampleStates.alch(top, ses)});
		}
		all.add(new Object[]{1026, 46, SampleStates.alch(com.superhistogram.model.skill.AlchTopRow.BOTH, per)});
		all.add(new Object[]{600, 30, SampleStates.alch(com.superhistogram.model.skill.AlchTopRow.GOLD, per)});
		sheet(new File(dir, "task17b-contact-all.png"), all);
	}

	/**
	 * Gathering contact sheet: each GatherPhase, the near-fall woodcutting sample, mining, fishing and
	 * firemaking at 1026x62, 720x62 and 400x36, stacked and scaled 2x. Writes task18b-contact.png.
	 */
	@Test
	public void writeGatheringContactSheet() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		java.util.List<BarState> states = new java.util.ArrayList<>();
		for (SampleStates.GatherPhase p : SampleStates.GatherPhase.values())
		{
			states.add(SampleStates.gathering(p));
		}
		states.add(SampleStates.gatheringFall());
		states.add(SampleStates.gatheringSkill("Mining", 440));
		states.add(SampleStates.gatheringSkill("Fishing", 335));
		states.add(SampleStates.firemaking());
		int[][] sizes = {{1026, 62}, {720, 62}, {400, 36}};
		java.util.List<Object[]> rows = new java.util.ArrayList<>();
		for (int[] wh : sizes)
		{
			for (BarState s : states)
			{
				rows.add(new Object[]{wh[0], wh[1], s});
			}
		}
		sheet(new File(dir, "task18b-contact.png"), rows);
	}

	/**
	 * Agility contact sheet: each AgilityPhase at 1026x62, 720x62, 480x62 and 1026x31, then MID_LAP with
	 * "last" and without the lost label, 2x. Writes task19b-contact.png.
	 */
	@Test
	public void writeAgilityContactSheet() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		java.util.List<Object[]> rows = new java.util.ArrayList<>();
		int[][] sizes = {{1026, 62}, {720, 62}, {480, 62}, {1026, 31}, {400, 36}};
		for (int[] wh : sizes)
		{
			for (SampleStates.AgilityPhase p : SampleStates.AgilityPhase.values())
			{
				rows.add(new Object[]{wh[0], wh[1], SampleStates.agility(p)});
			}
		}
		rows.add(new Object[]{1026, 62, SampleStates.agility(SampleStates.AgilityPhase.MID_LAP,
			com.superhistogram.model.skill.LapCompare.LAST, false)});
		sheet(new File(dir, "task19b-contact.png"), rows);
	}

	/** Icon contact sheet: every icon at u=1 and u=2 (shown magnified 4x) with its name. Writes icons-contact.png. */
	@Test
	public void writeIconContactSheet() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		Icons.Icon[] kinds = Icons.Icon.values();
		int cell = 120, zoom = 4, w = kinds.length * cell, h = 220;
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new java.awt.Color(0x14171b));
		g.fillRect(0, 0, w, h);
		g.setFont(Fonts.regular(11f));
		for (int i = 0; i < kinds.length; i++)
		{
			int x0 = i * cell;
			g.setColor(new java.awt.Color(0x30363d));
			g.drawLine(x0, 0, x0, h);
			String name = kinds[i].name().toLowerCase().replace('_', ' ');
			g.setColor(Palette.DIM);
			g.drawString(name, x0 + cell / 2f - g.getFontMetrics().stringWidth(name) / 2f, 14);
			g.drawString("u=1", x0 + 6, 34);
			g.drawString("u=2", x0 + 6, 134);
			for (int row = 0; row < 2; row++)
			{
				int u = row + 1;
				double cx = x0 + cell / 2.0, base = row == 0 ? 90 : 205;
				java.awt.Graphics2D z = (java.awt.Graphics2D) g.create();
				z.translate(cx, base);
				z.scale(zoom, zoom);
				z.setColor(new java.awt.Color(0x30363d));
				z.draw(new java.awt.geom.Rectangle2D.Double(-6 * u, -12 * u, 12 * u, 12 * u));
				z.setColor(Palette.TEXT);
				z.fill(Icons.icon(kinds[i], 0, 0, u));
				z.dispose();
			}
		}
		g.dispose();
		ImageIO.write(img, "png", new File(dir, "icons-contact.png"));
	}

	private static void sheet(File file, java.util.List<Object[]> rows) throws IOException
	{
		int pad = 8, total = pad;
		for (Object[] r : rows)
		{
			total += ((Integer) r[1] + pad) * 2;
		}
		java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(1026 * 2 + 2 * pad, total,
			java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D g = sheet.createGraphics();
		g.setColor(new java.awt.Color(0x2b3521));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		int y = pad;
		for (Object[] r : rows)
		{
			java.awt.image.BufferedImage img = BarRenderSmokeTest.render((Integer) r[0], (Integer) r[1], (BarState) r[2], null);
			g.drawImage(img, pad, y, img.getWidth() * 2, img.getHeight() * 2, null);
			y += ((Integer) r[1] + pad) * 2;
		}
		g.dispose();
		ImageIO.write(sheet, "png", file);
	}

	private static void write(String dir, String name, int w, int h, BarState s) throws IOException
	{
		ImageIO.write(BarRenderSmokeTest.render(w, h, s, null), "png", new File(dir, name));
	}
}
