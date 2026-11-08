package com.superhistogram.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/** Production-view drawing from cooking.html (62-px frame, scaled by u). Static helpers only. */
public final class SkillPainter
{
	/** Quarter-circle bezier constant. */
	private static final float K = 0.5523f;

	private SkillPainter()
	{
	}

	/** The pot with steam; (x, y) is the mockup's x, y (the rim's centre line is 2u above y). */
	public static void pot(Graphics2D g, float x, float y, float u, Color c)
	{
		g.setColor(c);
		float r = 3.5f * u;
		Path2D body = new Path2D.Float();
		body.moveTo(x - 5 * u, y - 2 * u);
		body.lineTo(x + 5 * u, y - 2 * u);
		body.lineTo(x + 5 * u, y + 0.5f * u);
		body.curveTo(x + 5 * u, y + 0.5f * u + r * K, x + 1.5f * u + r * K, y + 4 * u, x + 1.5f * u, y + 4 * u);
		body.lineTo(x - 1.5f * u, y + 4 * u);
		body.curveTo(x - 1.5f * u - r * K, y + 4 * u, x - 5 * u, y + 0.5f * u + r * K, x - 5 * u, y + 0.5f * u);
		body.closePath();
		g.fill(body);
		g.fill(new RoundRectangle2D.Float(x - 7 * u, y - 2 * u, 14 * u, 1.6f * u, 1.6f * u, 1.6f * u));
		Path2D steam = new Path2D.Float();
		steam.moveTo(x - 2 * u, y - 4.5f * u);
		steam.quadTo(x - 1 * u, y - 6 * u, x - 2 * u, y - 7.5f * u);
		steam.moveTo(x + 1.5f * u, y - 4.5f * u);
		steam.quadTo(x + 2.5f * u, y - 6 * u, x + 1.5f * u, y - 7.5f * u);
		g.setStroke(new BasicStroke(1.1f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.draw(steam);
	}

	/** Finish flag: a 1.5u pole from y0 to y1 at x with a pennant at the top. */
	public static void flag(Graphics2D g, float x, float y0, float y1, float u, Color c)
	{
		g.setColor(c);
		g.fill(new Rectangle2D.Float(x - 0.75f * u, y0, 1.5f * u, y1 - y0));
		Path2D p = new Path2D.Float();
		p.moveTo(x + 0.75f * u, y0);
		p.lineTo(x + 8.75f * u, y0);
		p.lineTo(x + 6.25f * u, y0 + 3 * u);
		p.lineTo(x + 8.75f * u, y0 + 6 * u);
		p.lineTo(x + 0.75f * u, y0 + 6 * u);
		p.closePath();
		g.fill(p);
	}

	/** Bank building: roof, three pillars and a base, centred on x with the roof's base line at y - 2u. */
	public static void bank(Graphics2D g, float x, float y, float u, Color c)
	{
		g.setColor(c);
		Path2D roof = new Path2D.Float();
		roof.moveTo(x - 6 * u, y - 2 * u);
		roof.lineTo(x, y - 6 * u);
		roof.lineTo(x + 6 * u, y - 2 * u);
		roof.closePath();
		g.fill(roof);
		for (float px : new float[]{-5, -1, 3})
		{
			g.fill(new Rectangle2D.Float(x + px * u, y - 1 * u, 2 * u, 5 * u));
		}
		g.fill(new Rectangle2D.Float(x - 6 * u, y + 4.5f * u, 12 * u, 1.6f * u));
	}

	/** Gold coin: a DAMAGE disc of radius r with a darker ring at 0.53r (stroke 0.26r). */
	public static void coin(Graphics2D g, float cx, float cy, float r)
	{
		g.setColor(Palette.DAMAGE);
		g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
		float rr = 0.53f * r;
		g.setColor(Palette.COIN_RING);
		java.awt.Stroke old = g.getStroke();
		g.setStroke(new BasicStroke(0.26f * r));
		g.draw(new Ellipse2D.Float(cx - rr, cy - rr, 2 * rr, 2 * rr));
		g.setStroke(old);
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

	/** Axe: a 1.6u x 10u handle centred on x and a 5.2u x 5u head to its right with rounded right corners. */
	public static void axe(Graphics2D g, float x, float y, float u, Color c)
	{
		g.setColor(c);
		g.fill(new RoundRectangle2D.Float(x - 0.8f * u, y - 5 * u, 1.6f * u, 10 * u, 1.6f * u, 1.6f * u));
		Path2D head = new Path2D.Float();
		head.moveTo(x + 0.8f * u, y - 5 * u);
		float r = 2f * u;
		head.lineTo(x + 6f * u - r, y - 5 * u);
		head.curveTo(x + 6f * u - r + r * K, y - 5 * u, x + 6f * u, y - 5 * u + r - r * K, x + 6f * u, y - 5 * u + r);
		head.lineTo(x + 6f * u, y - r);
		head.curveTo(x + 6f * u, y - r + r * K, x + 6f * u - r + r * K, y, x + 6f * u - r, y);
		head.lineTo(x + 0.8f * u, y);
		head.closePath();
		g.fill(head);
	}

	/** Log: a 10u x 5.2u rounded bar centred on (x, y) with a paler end circle (r 2.6u) at x + 4u. */
	public static void log(Graphics2D g, float x, float y, float u)
	{
		g.setColor(Palette.LOG);
		g.fill(new RoundRectangle2D.Float(x - 5 * u, y - 2.6f * u, 10 * u, 5.2f * u, 5.2f * u, 5.2f * u));
		g.setColor(Palette.LOG_END);
		g.fill(new Ellipse2D.Float(x + 4 * u - 2.6f * u, y - 2.6f * u, 5.2f * u, 5.2f * u));
	}

	/** Tree stump: a trapezoid with a dim cut top; (x, y) is the mockup's x, y. */
	public static void stump(Graphics2D g, float x, float y, float u)
	{
		g.setColor(Palette.TEXT);
		Path2D p = new Path2D.Float();
		p.moveTo(x - 4 * u, y + 4 * u);
		p.lineTo(x + 4 * u, y + 4 * u);
		p.lineTo(x + 2.8f * u, y - 2 * u);
		p.lineTo(x - 2.8f * u, y - 2 * u);
		p.closePath();
		g.fill(p);
		g.setColor(Palette.DIM);
		g.fill(new Ellipse2D.Float(x - 2.8f * u, y - 2 * u - 1.1f * u, 5.6f * u, 2.2f * u));
	}

	/** A gathered item with no icon yet: a disc of radius 2.8u. */
	public static void item(Graphics2D g, float x, float y, float u, Color c)
	{
		g.setColor(c);
		g.fill(new Ellipse2D.Float(x - 2.8f * u, y - 2.8f * u, 5.6f * u, 5.6f * u));
	}

	/** A depleted rock or spot: a ring of radius 3u, stroke 1.2u. */
	public static void depleted(Graphics2D g, float x, float y, float u)
	{
		g.setColor(Palette.TEXT);
		g.setStroke(new BasicStroke(1.2f * u));
		g.draw(new Ellipse2D.Float(x - 3 * u, y - 3 * u, 6 * u, 6 * u));
	}
}
