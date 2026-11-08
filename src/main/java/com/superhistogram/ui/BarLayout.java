package com.superhistogram.ui;

/** Pure geometry for the bar at a given size, including resize breakpoints. */
public final class BarLayout
{
	static final int MIN_W = 220, MAX_W = 4000, MIN_H = 22, MAX_H = 160;
	private static final float BASE_H = 62f;

	public int width, height;
	public float u;
	public float tickWidth;
	/** 3, 2 or 1. */
	public int rows;
	public boolean showLeft, showRight;
	public float timelineX, timelineW;
	public int visibleTicks;
	public float topY, pillY, pillH, actionsY, rulerY;

	private BarLayout()
	{
	}

	/** A copy with another tick width; the visible tick count follows and everything else is equal. */
	public BarLayout withTickWidth(float tw)
	{
		BarLayout c = new BarLayout();
		c.width = width;
		c.height = height;
		c.u = u;
		c.tickWidth = tw;
		c.rows = rows;
		c.showLeft = showLeft;
		c.showRight = showRight;
		c.timelineX = timelineX;
		c.timelineW = timelineW;
		c.visibleTicks = (int) Math.floor(timelineW / tw);
		c.topY = topY;
		c.pillY = pillY;
		c.pillH = pillH;
		c.actionsY = actionsY;
		c.rulerY = rulerY;
		return c;
	}

	public static BarLayout of(int width, int height)
	{
		BarLayout l = new BarLayout();
		l.width = Math.max(MIN_W, Math.min(MAX_W, width));
		l.height = Math.max(MIN_H, Math.min(MAX_H, height));
		l.u = l.height / BASE_H;
		l.tickWidth = Math.max(18f, 32f * l.u);
		l.rows = l.height < 40 ? 1 : l.height < 56 ? 2 : 3;
		l.showLeft = l.width >= 480;
		l.showRight = l.width >= 720;

		float leftW = l.showLeft ? Math.max(0.7f * l.height, 128f * l.u) : 0.5f * l.height;
		float rightW = l.showRight ? Math.min(210f * l.u, 0.25f * l.width) : 0.5f * l.height;
		l.timelineX = leftW;
		l.timelineW = Math.max(0f, l.width - leftW - rightW);
		l.visibleTicks = (int) Math.floor(l.timelineW / l.tickWidth);

		l.topY = 15f * l.u;
		l.pillY = 22f * l.u;
		l.pillH = 14f * l.u;
		l.actionsY = 46f * l.u;
		l.rulerY = 53f * l.u;
		return l;
	}
}
