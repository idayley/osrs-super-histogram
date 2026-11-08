package com.superhistogram.ui;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;

/**
 * The one icon set. Every icon is a filled shape drawn in a 12 x 12 design box (y down) that is scaled by
 * {@code u = height / 62}. All icons rest on the box's bottom edge, the shared baseline. Painters fill the
 * returned shape with their own colour; no painter draws paths of its own.
 *
 * <p>Some icons have a companion part (log end, stump top, coin ring) that painters fill in a second colour.
 */
public final class Icons
{
	/** Width and height of the design box, in u. */
	public static final float BOX = 12f;

	/** K for quarter-circle beziers. */
	private static final float K = 0.5523f;

	public enum Icon
	{
		POT, AXE, LOG, LOG_END, FOOD, SWORD, FLAG, COIN, COIN_RING, MARK, BANK, HIT, STUMP, STUMP_TOP,
		DEPLETED, DOT, CARET, CHECK, CROSS, SHIRT, BOLT, STEPS
	}

	private static final Map<Icon, Shape> SHAPES = new EnumMap<>(Icon.class);

	static
	{
		SHAPES.put(Icon.POT, pot());
		SHAPES.put(Icon.AXE, axe());
		SHAPES.put(Icon.LOG, new RoundRectangle2D.Float(0.2f, 6.8f, 10f, 5.2f, 5.2f, 5.2f));
		SHAPES.put(Icon.LOG_END, new Ellipse2D.Float(6.8f, 6.8f, 5.2f, 5.2f));
		SHAPES.put(Icon.FOOD, food());
		SHAPES.put(Icon.SWORD, sword());
		SHAPES.put(Icon.FLAG, flag());
		SHAPES.put(Icon.COIN, new Ellipse2D.Float(0, 0, 12, 12));
		SHAPES.put(Icon.COIN_RING, ring(6, 6, 3.96f, 2.4f));
		SHAPES.put(Icon.MARK, poly(6, 3, 10, 7.5f, 6, 12, 2, 7.5f));
		SHAPES.put(Icon.BANK, bank());
		SHAPES.put(Icon.HIT, hit());
		SHAPES.put(Icon.STUMP, poly(2, 12, 10, 12, 8.8f, 6, 3.2f, 6));
		SHAPES.put(Icon.STUMP_TOP, new Ellipse2D.Float(3.2f, 4.9f, 5.6f, 2.2f));
		SHAPES.put(Icon.DEPLETED, ring(6, 8.4f, 3.6f, 2.4f));
		SHAPES.put(Icon.DOT, new Ellipse2D.Float(3, 6, 6, 6));
		SHAPES.put(Icon.CARET, poly(2, 12, 6, 7, 10, 12));
		SHAPES.put(Icon.CHECK, stroked(1.4f, new float[]{1.9f, 8.7f, 4.5f, 11.3f, 10.1f, 5.7f}));
		SHAPES.put(Icon.SHIRT, poly(0, 3.6f, 3.6f, 0.6f, 4.8f, 0.6f, 6, 2, 7.2f, 0.6f, 8.4f, 0.6f, 12, 3.6f, 10, 6.2f,
			9.2f, 5.6f, 9.2f, 12, 2.8f, 12, 2.8f, 5.6f, 2, 6.2f));
		SHAPES.put(Icon.BOLT, poly(7.4f, 0, 1.8f, 7, 5.6f, 7, 4.2f, 12, 10.2f, 4.6f, 6.4f, 4.6f));
		SHAPES.put(Icon.STEPS, steps());
		SHAPES.put(Icon.CROSS, stroked(1.6f, new float[]{3, 5.2f, 9, 11.2f}, new float[]{9, 5.2f, 3, 11.2f}));
	}

	private Icons()
	{
	}

	/**
	 * The icon's shape: its 12u box is centred horizontally on {@code cx} and its bottom edge sits on
	 * {@code baselineY}.
	 */
	public static Shape icon(Icon kind, double cx, double baselineY, double u)
	{
		AffineTransform at = AffineTransform.getTranslateInstance(cx - 6 * u, baselineY - 12 * u);
		at.scale(u, u);
		return at.createTransformedShape(SHAPES.get(kind));
	}

	// ---- shapes in box coordinates ----

	/** Potion bottle: round body, narrow neck, cork. */
	private static Shape pot()
	{
		Area a = new Area(new Ellipse2D.Float(0.8f, 4.2f, 10.4f, 7.8f));
		a.add(new Area(new RoundRectangle2D.Float(4.3f, 1.8f, 3.4f, 4f, 1f, 1f)));
		a.add(new Area(new RoundRectangle2D.Float(3.9f, 0f, 4.2f, 2.4f, 1f, 1f)));
		return a;
	}

	private static Shape axe()
	{
		Path2D p = new Path2D.Float();
		p.append(new RoundRectangle2D.Float(3.6f, 0, 1.6f, 12, 1.6f, 1.6f), false);
		Path2D head = new Path2D.Float();
		head.moveTo(4.4f, 0);
		head.lineTo(9.6f, 0);
		head.curveTo(9.6f + 2 * K, 0, 11.6f, 2 - 2 * K, 11.6f, 2);
		head.lineTo(11.6f, 3.6f);
		head.curveTo(11.6f, 3.6f + 2 * K, 9.6f + 2 * K, 5.6f, 9.6f, 5.6f);
		head.lineTo(4.4f, 5.6f);
		head.closePath();
		p.append(head, false);
		return p;
	}

	private static Shape food()
	{
		Path2D p = new Path2D.Float();
		p.append(new Ellipse2D.Float(0, 6, 9, 5.6f), false);
		p.append(poly(8.5f, 8.8f, 12, 5.6f, 12, 12), false);
		return p;
	}

	/** Diagonal sword: tip upper-right, pommel lower-left, drawn upright then turned 45 degrees and fitted to the box. */
	private static Shape sword()
	{
		Area a = new Area(poly(0, 0, 1, 1.6f, 1, 7.4f, -1, 7.4f, -1, 1.6f));
		a.add(new Area(new RoundRectangle2D.Float(-3, 7.4f, 6, 1.4f, 0.6f, 0.6f)));
		a.add(new Area(new Rectangle2D.Float(-0.7f, 8.8f, 1.4f, 2f)));
		a.add(new Area(new Ellipse2D.Float(-1.2f, 10.6f, 2.4f, 2.4f)));
		AffineTransform turn = AffineTransform.getRotateInstance(Math.PI / 4);
		Shape r = turn.createTransformedShape(a);
		java.awt.geom.Rectangle2D b = r.getBounds2D();
		double k = 12 / Math.max(b.getWidth(), b.getHeight());
		AffineTransform fit = AffineTransform.getTranslateInstance(6 - k * b.getCenterX(), 12 - k * b.getMaxY());
		fit.scale(k, k);
		return fit.createTransformedShape(r);
	}

	/** Two footprints, the right one ahead. */
	private static Shape steps()
	{
		Path2D p = new Path2D.Float();
		p.append(new Ellipse2D.Float(1.2f, 5.8f, 3.6f, 6.2f), false);
		p.append(new Ellipse2D.Float(7.2f, 1.2f, 3.6f, 6.2f), false);
		return p;
	}

	private static Shape flag()
	{
		Path2D p = new Path2D.Float();
		p.append(new Rectangle2D.Float(1, 0, 1.5f, 12), false);
		p.append(poly(2.5f, 0, 10.5f, 0, 8, 3, 10.5f, 6, 2.5f, 6), false);
		return p;
	}

	private static Shape bank()
	{
		Path2D p = new Path2D.Float();
		p.append(poly(0, 4, 6, 0, 12, 4), false);
		for (float px : new float[]{1, 5, 9})
		{
			p.append(new Rectangle2D.Float(px, 5, 2, 5), false);
		}
		p.append(new Rectangle2D.Float(0, 10.5f, 12, 1.5f), false);
		return p;
	}

	/** The demon head, redrawn from a 14 x 13 path into the 12 box. */
	private static Shape hit()
	{
		Path2D d = new Path2D.Float();
		d.moveTo(-6, -1);
		d.lineTo(-7, -7);
		d.lineTo(-3, -4);
		d.lineTo(0, -5);
		d.lineTo(3, -4);
		d.lineTo(7, -7);
		d.lineTo(6, -1);
		d.quadTo(6, 5, 0, 6);
		d.quadTo(-6, 5, -6, -1);
		d.closePath();
		float s = 12f / 14f;
		AffineTransform at = AffineTransform.getTranslateInstance(0, 12 - 13 * s);
		at.scale(s, s);
		at.translate(7, 7);
		return at.createTransformedShape(d);
	}

	private static Shape poly(float... xy)
	{
		Path2D p = new Path2D.Float();
		p.moveTo(xy[0], xy[1]);
		for (int i = 2; i < xy.length; i += 2)
		{
			p.lineTo(xy[i], xy[i + 1]);
		}
		p.closePath();
		return p;
	}

	private static Shape ring(float cx, float cy, float outer, float inner)
	{
		Area a = new Area(new Ellipse2D.Float(cx - outer, cy - outer, 2 * outer, 2 * outer));
		a.subtract(new Area(new Ellipse2D.Float(cx - inner, cy - inner, 2 * inner, 2 * inner)));
		return a;
	}

	/** Open polylines (each an array of x, y pairs) thickened to one filled outline with round caps and joins. */
	private static Shape stroked(float width, float[]... lines)
	{
		Path2D path = new Path2D.Float();
		for (float[] xy : lines)
		{
			path.moveTo(xy[0], xy[1]);
			for (int i = 2; i < xy.length; i += 2)
			{
				path.lineTo(xy[i], xy[i + 1]);
			}
		}
		return new Area(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(path));
	}
}
