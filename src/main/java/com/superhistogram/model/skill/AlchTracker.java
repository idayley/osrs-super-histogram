package com.superhistogram.model.skill;

import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks high-alchemy casts. A click on the spell-on-item menu entry is only a candidate; a Magic XP
 * drop of exactly 65 within {@link #PENDING_TICKS} ticks confirms it (a cast needs a fire staff and
 * the item, and the click alone does not prove either). Each confirmed cast is marked on its tick,
 * carrying the profit known when the item was clicked.
 */
public final class AlchTracker
{
	public static final int CAST_TICKS = 5, XP = 65, PENDING_TICKS = 2, LOST_MAX = 10, LINGER = 10;
	/** Two casts further apart than this are not one session of alching. */
	private static final int SHOW_GAP = 15;

	private static final class Click
	{
		final int tick;
		final Long profit;

		Click(int tick, Long profit)
		{
			this.tick = tick;
			this.profit = profit;
		}
	}

	private Timeline timeline;
	private final List<Click> pending = new ArrayList<>();
	private int lastCast = -1, prevCast = -1;
	private int casts, unpriced;
	private long gold;

	public AlchTracker(Timeline timeline)
	{
		this.timeline = timeline;
	}

	/** The player cast High Level Alchemy on an item on this tick; {@code profit} is null when unpriced. */
	public void onCastClick(int tick, Long profit)
	{
		pending.add(new Click(tick, profit));
	}

	/** Call once per tick, in order, with that tick's XP drops. */
	public void onTick(int tick, List<XpDrop> drops)
	{
		for (Iterator<Click> it = pending.iterator(); it.hasNext(); )
		{
			if (it.next().tick + PENDING_TICKS < tick)
			{
				it.remove();
			}
		}
		if (drops == null || !hasCastXp(drops))
		{
			return;
		}
		for (Iterator<Click> it = pending.iterator(); it.hasNext(); )
		{
			Click c = it.next();
			if (c.tick <= tick && tick <= c.tick + PENDING_TICKS)
			{
				it.remove();
				record(tick, c.profit);
				return;
			}
		}
	}

	private static boolean hasCastXp(List<XpDrop> drops)
	{
		for (XpDrop d : drops)
		{
			if ("Magic".equals(d.skill) && d.amount == XP)
			{
				return true;
			}
		}
		return false;
	}

	private void record(int tick, Long profit)
	{
		int lost = lastCast < 0 ? 0 : tick - (lastCast + CAST_TICKS);
		if (lost < 1 || lost > LOST_MAX)
		{
			lost = 0;
		}
		TickRecord r = timeline.at(tick);
		if (r != null)
		{
			r.cast = true;
			r.castProfit = profit;
			r.castLost = lost;
		}
		prevCast = lastCast;
		lastCast = tick;
		casts++;
		if (profit == null)
		{
			unpriced++;
		}
		else
		{
			gold += profit;
		}
	}

	/** True while alching: the last two casts are close together and the last one is recent. */
	public boolean showing(int tick)
	{
		return lastCast >= 0 && prevCast >= 0 && lastCast - prevCast <= SHOW_GAP && tick - lastCast <= LINGER;
	}

	public int casts()
	{
		return casts;
	}

	/** Tick of the newest cast, or -1. */
	public int lastCastTick()
	{
		return lastCast;
	}

	/** First tick the next cast can land, or -1 without a cast. */
	public int nextReadyTick()
	{
		return lastCast < 0 ? -1 : lastCast + CAST_TICKS;
	}

	/** Sum of the known profits of this session's casts. */
	public long sessionGold()
	{
		return gold;
	}

	/** Casts whose profit was unknown (not counted in the gold). */
	public int unpricedCasts()
	{
		return unpriced;
	}

	public long goldPerHour(long sessionMs)
	{
		return sessionMs <= 0 ? 0 : gold * 3_600_000L / sessionMs;
	}

	/** Points at a fresh timeline (after logout): casts in progress are forgotten, session gold is kept. */
	public void rebind(Timeline newTimeline)
	{
		timeline = newTimeline;
		pending.clear();
		lastCast = -1;
		prevCast = -1;
	}

	/** Forgets everything, session gold included. */
	public void reset()
	{
		rebind(timeline);
		casts = 0;
		unpriced = 0;
		gold = 0;
	}
}
