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
 * carrying the coins known when the item was clicked.
 */
public final class AlchTracker
{
	public static final int CAST_TICKS = 5, XP = 65, PENDING_TICKS = 2, LOST_MAX = 10, LINGER = 10;
	/** Two casts further apart than this are not one session of alching. */
	private static final int SHOW_GAP = 15;

	private static final class Click
	{
		final int tick;
		final Long coins;

		Click(int tick, Long coins)
		{
			this.tick = tick;
			this.coins = coins;
		}
	}

	private Timeline timeline;
	private final List<Click> pending = new ArrayList<>();
	private int lastCast = -1, prevCast = -1;
	private int casts, unpriced;
	private long gold;
	/** Coins of this run: the casts since you last did something else. */
	private long runGold;
	/** Something else was done since the last cast: the next cast starts a new run. */
	private boolean runBroken;
	/** Coins of the newest cast (0 when unpriced). */
	private long lastCoins;
	/** Priced casts of the last hour, oldest first: {tick, coins}. */
	private final java.util.ArrayDeque<long[]> hour = new java.util.ArrayDeque<>();
	/** An hour in ticks. */
	public static final int HOUR_TICKS = 6000;

	public AlchTracker(Timeline timeline)
	{
		this.timeline = timeline;
	}

	/** The player cast High Level Alchemy on an item on this tick; {@code coins} is null when unpriced. */
	public void onCastClick(int tick, Long coins)
	{
		pending.add(new Click(tick, coins));
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
				record(tick, c.coins);
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

	private void record(int tick, Long coins)
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
			r.castCoins = coins;
			r.castLost = lost;
		}
		prevCast = lastCast;
		lastCast = tick;
		casts++;
		if (runBroken)
		{
			// The bar shows alching only from the second close cast, so the first one belongs to this run too.
			runGold = prevCast >= 0 && tick - prevCast <= SHOW_GAP ? lastCoins : 0;
			runBroken = false;
		}
		lastCoins = coins == null ? 0 : coins;
		if (coins == null)
		{
			unpriced++;
		}
		else
		{
			gold += coins;
			runGold += coins;
			hour.addLast(new long[]{tick, coins});
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

	/** Sum of the known coins of this session's casts. */
	public long sessionGold()
	{
		return gold;
	}

	/** Coins of this run: the priced casts since another activity last took over the bar. */
	public long runGold()
	{
		return runGold;
	}

	/** Another activity (salvaging, a fight, ...) took over: the next cast starts a new run. */
	public void breakRun()
	{
		runBroken = true;
	}

	/** Coins from the casts of the last hour (rolling), up to {@code tick}. */
	public long goldLastHour(int tick)
	{
		while (!hour.isEmpty() && hour.peekFirst()[0] <= tick - HOUR_TICKS)
		{
			hour.pollFirst();
		}
		long sum = 0;
		for (long[] c : hour)
		{
			sum += c[1];
		}
		return sum;
	}

	/** Casts whose coins was unknown (not counted in the gold). */
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
		runGold = 0;
		runBroken = false;
		lastCoins = 0;
		hour.clear();
	}
}
