package com.superhistogram.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Production-view drawing from cooking.html (62-px frame, scaled by u). Static helpers only. */
public final class SkillPainter
{
	private SkillPainter()
	{
	}

	/** The pot with steam; (x, y) is the mockup's x, y: the pot's bottom is 4u below it. */
	public static void pot(Graphics2D g, float x, float y, float u, Color c)
	{
		g.setColor(c);
		g.fill(Icons.icon(Icons.Icon.POT, x, y + 4 * u, u));
	}

	/**
	 * Finish flag: the flag icon with its pole at x and its top at y0; the pole is carried on down to y1.
	 */
	public static void flag(Graphics2D g, float x, float y0, float y1, float u, Color c)
	{
		g.setColor(c);
		g.fill(Icons.icon(Icons.Icon.FLAG, x + 4.25f * u, y0 + Icons.BOX * u, u));
		float iconBottom = y0 + Icons.BOX * u;
		if (y1 > iconBottom)
		{
			g.fill(new Rectangle2D.Float(x - 0.75f * u, iconBottom, 1.5f * u, y1 - iconBottom));
		}
	}

	/** Bank building centred on x; its base is 6.1u below y. */
	public static void bank(Graphics2D g, float x, float y, float u, Color c)
	{
		g.setColor(c);
		g.fill(Icons.icon(Icons.Icon.BANK, x, y + 6.1f * u, u));
	}

	/** Gold coin: a DAMAGE disc of radius r with a darker ring. */
	public static void coin(Graphics2D g, float cx, float cy, float r)
	{
		float u = r / 6f;
		g.setColor(Palette.damage());
		g.fill(Icons.icon(Icons.Icon.COIN, cx, cy + r, u));
		g.setColor(Palette.COIN_RING);
		g.fill(Icons.icon(Icons.Icon.COIN_RING, cx, cy + r, u));
	}

	/** The wasted-ticks mark: a line from x0 to x1 at 45.25u with "+n" centred above it. */
	public static void wasted(Graphics2D g, BarLayout l, float x0, float x1, int n)
	{
		if (x1 <= x0)
		{
			return;
		}
		float u = l.u;
		g.setColor(Palette.WASTED_LINE);
		g.fill(new Rectangle2D.Float(x0, 45.25f * u, x1 - x0, 1.5f * u));
		String text = "+" + n;
		g.setFont(Fonts.bold(9f * u));
		g.setColor(Palette.LOST);
		float w = g.getFontMetrics().stringWidth(text);
		g.drawString(text, (x0 + x1) / 2f - w / 2f, 42f * u);
	}

	/** Axe: the handle is centred on x; the head extends to its right. */
	public static void axe(Graphics2D g, float x, float y, float u, Color c)
	{
		g.setColor(c);
		g.fill(Icons.icon(Icons.Icon.AXE, x + 1.6f * u, y + 5 * u, u));
	}

	/** Log centred on (x, y) with a paler end circle. */
	public static void log(Graphics2D g, float x, float y, float u)
	{
		g.setColor(Palette.LOG);
		g.fill(Icons.icon(Icons.Icon.LOG, x + 0.8f * u, y + 2.6f * u, u));
		g.setColor(Palette.LOG_END);
		g.fill(Icons.icon(Icons.Icon.LOG_END, x + 0.8f * u, y + 2.6f * u, u));
	}

	/** Tree stump with a dim cut top; (x, y) is the mockup's x, y (the base is 4u below it). */
	public static void stump(Graphics2D g, float x, float y, float u)
	{
		g.setColor(Palette.TEXT);
		g.fill(Icons.icon(Icons.Icon.STUMP, x, y + 4 * u, u));
		g.setColor(Palette.DIM);
		g.fill(Icons.icon(Icons.Icon.STUMP_TOP, x, y + 4 * u, u));
	}

	/** A gathered item with no icon yet: a disc 5.6u across centred on (x, y). */
	public static void item(Graphics2D g, float x, float y, float u, Color c)
	{
		float d = u * 5.6f / 6f;
		g.setColor(c);
		g.fill(Icons.icon(Icons.Icon.DOT, x, y + 3 * d, d));
	}

	/** A depleted rock or spot: a ring centred on (x, y). */
	public static void depleted(Graphics2D g, float x, float y, float u)
	{
		g.setColor(Palette.TEXT);
		g.fill(Icons.icon(Icons.Icon.DEPLETED, x, y + 3.6f * u, u));
	}

	/** Mark of grace: a MINT diamond centred on (cx, cy). */
	public static void mark(Graphics2D g, float cx, float cy, float u)
	{
		g.setColor(Palette.mint());
		g.fill(Icons.icon(Icons.Icon.MARK, cx, cy + 4.5f * u, u));
	}
}
