package com.superhistogram.model.combat;

import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import java.util.ArrayList;
import java.util.List;

/**
 * Judges each tick of 1-tick flicking from the player's own prayer clicks. Called once per tick,
 * oldest first. A clean flick is two clicks in the same tick; a lone click is a split whose
 * partner lands in the next tick.
 */
public final class FlickClassifier
{
	public enum Kind
	{
		OK, SPLIT, SKIP, DRAIN, NONE
	}

	public static final int HIST_BINS = 12, HIST_TICKS = 40, SPLIT_BIN = 10;

	private boolean dangling;
	private Kind last = Kind.NONE;
	private float phase;
	private int streak;

	public Kind classify(List<Float> prayerClicks, boolean onAfter)
	{
		List<Float> rest = new ArrayList<>(prayerClicks);
		boolean consumed = false;
		if (dangling)
		{
			dangling = false;
			if (!rest.isEmpty())
			{
				rest.remove(0);
				consumed = true;
			}
		}
		Kind k;
		phase = 0f;
		if (!rest.isEmpty())
		{
			if (rest.size() % 2 == 0)
			{
				k = Kind.OK;
				phase = rest.get(0);
			}
			else
			{
				k = Kind.SPLIT;
				phase = rest.get(rest.size() - 1);
				dangling = true;
			}
		}
		else if (consumed)
		{
			k = Kind.NONE;
		}
		else if (onAfter)
		{
			k = Kind.DRAIN;
		}
		else if (last == Kind.OK || last == Kind.SPLIT)
		{
			k = Kind.SKIP;
		}
		else
		{
			k = Kind.NONE;
		}
		if (k == Kind.OK)
		{
			streak++;
		}
		else if (k != Kind.NONE)
		{
			streak = 0;
		}
		last = k;
		return k;
	}

	/** In-tick position (0..1) of the last classified flick; 0 when it had none. */
	public float phase()
	{
		return phase;
	}

	/** Clean flicks in a row, ending at the last classified tick. */
	public int streak()
	{
		return streak;
	}

	public Kind last()
	{
		return last;
	}

	public void reset()
	{
		dangling = false;
		last = Kind.NONE;
		phase = 0f;
		streak = 0;
	}

	/** Counts of OK and SPLIT phases over [fromTick, toTick] in HIST_BINS bins. */
	public static int[] histogram(Timeline t, int fromTick, int toTick)
	{
		int[] bins = new int[HIST_BINS];
		for (int tick = fromTick; tick <= toTick; tick++)
		{
			TickRecord r = t.peek(tick);
			if (r == null || (r.flick != Kind.OK && r.flick != Kind.SPLIT))
			{
				continue;
			}
			int b = Math.min(HIST_BINS - 1, (int) Math.floor(r.flickPhase * HIST_BINS));
			bins[Math.max(0, b)]++;
		}
		return bins;
	}
}
