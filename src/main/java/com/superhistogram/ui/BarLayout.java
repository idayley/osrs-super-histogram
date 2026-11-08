package com.superhistogram.ui;

/** Pure geometry for the bar at a given size, including resize breakpoints. */
public final class BarLayout
{
	static final int MIN_W = 220, MAX_W = 4000, MIN_H = 22, MAX_H = 160;
	/** Bar height from which all five lanes show (replay finding 2: swings and text at 52 px). */
	static final int FULL_LANES_FROM = 44;
	private static final float BASE_H = 62f;
	/** Widths (px) where the readouts go compact, then disappear. */
	static final int COMPACT_BELOW = 720, RIGHT_FROM = 400, LEFT_FROM = 330;
	/** Compact readout widths in u. */
	static final float COMPACT_LEFT_U = 92f, COMPACT_RIGHT_U = 124f;

	public int width, height;
	public float u;
	public float tickWidth;
	/** 3, 2 or 1. */
	public int rows;
	public boolean showLeft, showRight;
	/** Under 720 wide (e.g. the 519 px chat): shorter readouts instead of hiding them. */
	public boolean compact;
	public float timelineX, timelineW;
	public int visibleTicks;
	public float topY, pillY, pillH, actionsY, rulerY;
	/** Smooth scrolling: how far (whole px) the timeline has slid left since the newest tick. */
	public float glide;
	/**
	 * Smooth scrolling: one slot when on, else 0. The tick in progress is the slot just left of {@link
	 * TimelinePainter#nowX}; with smooth scrolling "now" moves through it, so the timeline is drawn a slot
	 * further right and slid left as the tick goes by.
	 */
	public float lag;
	/** Where the "now" line sits across the timeline, 10-100 %; 0 = the default spot. */
	public int nowPct;
	/** Top and height (px) of each {@link Lane}, by ordinal; height 0 when the bar has no such lane. */
	private final float[] laneY = new float[Lane.values().length], laneH = new float[Lane.values().length];

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
		c.compact = compact;
		c.timelineX = timelineX;
		c.timelineW = timelineW;
		c.visibleTicks = (int) Math.floor(timelineW / tw);
		c.topY = topY;
		c.pillY = pillY;
		c.pillH = pillH;
		c.actionsY = actionsY;
		c.rulerY = rulerY;
		c.glide = glide;
		c.lag = lag;
		c.nowPct = nowPct;
		System.arraycopy(laneY, 0, c.laneY, 0, laneY.length);
		System.arraycopy(laneH, 0, c.laneH, 0, laneH.length);
		return c;
	}

	/** Top (px) of a lane. */
	public float laneY(Lane lane)
	{
		return laneY[lane.ordinal()];
	}

	/** Height (px) of a lane; 0 when {@link #hasLane} is false. */
	public float laneH(Lane lane)
	{
		return laneH[lane.ordinal()];
	}

	/** The inputs lane is only on a 3-row bar; every other lane is always there. */
	public boolean hasLane(Lane lane)
	{
		return laneH[lane.ordinal()] > 0f;
	}

	private void lane(Lane lane, float y, float h)
	{
		laneY[lane.ordinal()] = y;
		laneH[lane.ordinal()] = h;
	}

	/**
	 * Lanes at 62 px (u = 1): top 4-18, doing 20-31, prayer 33-44, inputs 46-53, ruler 55-58, all × u. Under
	 * 44 px doing and prayer split the middle band in half and there is no inputs lane; under 40 px the doing
	 * pill is the one row with prayer as a 3u strip under it.
	 */
	private void lanes()
	{
		lane(Lane.RULER, height - 7f * u, 3f * u);
		if (rows == 3)
		{
			lane(Lane.TOP, 4f * u, 14f * u);
			lane(Lane.DOING, 20f * u, 11f * u);
			lane(Lane.PRAYER, 33f * u, 11f * u);
			lane(Lane.INPUTS, 46f * u, 7f * u);
			return;
		}
		// Same middle band as the 2- and 1-row pills (TimelinePainter.pillY / pillH).
		float bandY = rows == 2 ? height * 0.48f : height * 0.32f;
		float bandH = rows == 2 ? pillH : Math.max(8f, height * 0.3f);
		lane(Lane.INPUTS, 0f, 0f);
		if (rows == 2)
		{
			lane(Lane.TOP, 4f * u, Math.max(0f, bandY - 6f * u));
			lane(Lane.DOING, bandY, bandH / 2f);
			lane(Lane.PRAYER, bandY + bandH / 2f, bandH / 2f);
			return;
		}
		lane(Lane.TOP, Math.max(0f, bandY - 5f * u), Math.min(bandY, 5f * u));
		lane(Lane.DOING, bandY, bandH);
		lane(Lane.PRAYER, bandY + bandH, 3f * u);
	}

	/** A copy with the "now" line at {@code pct} % across the timeline (0 = the default spot). */
	public BarLayout withNowPct(int pct)
	{
		BarLayout c = withTickWidth(tickWidth);
		c.nowPct = pct;
		return c;
	}

	/** A copy that has slid {@code px} left since the newest tick. */
	public BarLayout withGlide(float px)
	{
		return withGlide(px, lag);
	}

	/** As {@link #withGlide(float)} with the smooth-scrolling lag (one slot, or 0 when stepping per tick). */
	public BarLayout withGlide(float px, float lag)
	{
		BarLayout c = withTickWidth(tickWidth);
		c.glide = px;
		c.lag = lag;
		return c;
	}

	public static BarLayout of(int width, int height)
	{
		BarLayout l = new BarLayout();
		l.width = Math.max(MIN_W, Math.min(MAX_W, width));
		l.height = Math.max(MIN_H, Math.min(MAX_H, height));
		l.u = l.height / BASE_H;
		l.tickWidth = Math.max(18f, 32f * l.u);
		// All five lanes from 44 px (they shrink together); the half-height layout only for 40-43.
		l.rows = l.height < 40 ? 1 : l.height < FULL_LANES_FROM ? 2 : 3;
		l.compact = l.width < COMPACT_BELOW;
		l.showLeft = l.width >= LEFT_FROM;
		l.showRight = l.width >= RIGHT_FROM;

		float leftW = !l.showLeft ? 0.5f * l.height
			: l.compact ? Math.max(0.7f * l.height, COMPACT_LEFT_U * l.u)
			: Math.max(0.7f * l.height, 128f * l.u);
		float rightW = !l.showRight ? 0.5f * l.height
			: l.compact ? COMPACT_RIGHT_U * l.u
			: Math.min(210f * l.u, 0.25f * l.width);
		l.timelineX = leftW;
		l.timelineW = Math.max(0f, l.width - leftW - rightW);
		l.visibleTicks = (int) Math.floor(l.timelineW / l.tickWidth);

		l.topY = 15f * l.u;
		l.pillY = 22f * l.u;
		l.pillH = 14f * l.u;
		l.actionsY = 46f * l.u;
		l.rulerY = 53f * l.u;
		l.lanes();
		return l;
	}
}
