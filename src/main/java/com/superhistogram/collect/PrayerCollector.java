package com.superhistogram.collect;

import com.superhistogram.model.Timeline;
import net.runelite.api.Client;
import net.runelite.api.Prayer;

/** Turns the player's own overhead-prayer state, observed each game tick, into timeline spans. */
public final class PrayerCollector
{
	private final Timeline timeline;
	private String current;
	private com.superhistogram.model.Span open;
	private int lastPoints = -1, used;

	public PrayerCollector(Timeline timeline)
	{
		this.timeline = timeline;
	}

	/** kind is "melee", "ranged", "magic", "other" or null when no overhead is on. */
	public void onTick(int tick, String kind)
	{
		onTick(tick, kind, -1);
	}

	/** As {@link #onTick(int, String)}, also counting prayer points used while the overhead is on (-1 = unknown). */
	public void onTick(int tick, String kind, int points)
	{
		boolean same = kind == null ? current == null : kind.equals(current);
		if (same)
		{
			countPoints(points);
			lastPoints = points;
			return;
		}
		// The span is the game's own state: on from the tick the game turned it on, through the last tick it was on
		// (your clicks are the white marks; the game acts on them a tick or two later).
		if (current != null)
		{
			timeline.closeSpan("pray-" + current, Math.max(0, tick - 1));
		}
		if (kind != null)
		{
			open = timeline.openSpan("pray-" + kind, Math.max(0, tick));
			used = 0;
			// The tick it turned on can already have drained a point.
			countPoints(points);
		}
		else
		{
			open = null;
		}
		lastPoints = points;
		current = kind;
	}

	/** Adds any drop since the last tick to the open prayer's tally and shows it as the pill's label. */
	private void countPoints(int points)
	{
		if (open == null || points < 0 || lastPoints < 0 || points >= lastPoints)
		{
			return;
		}
		used += lastPoints - points;
		open.label = label(used);
	}

	public static String label(int used)
	{
		return used + (used == 1 ? " pt" : " pts");
	}

	/** Whether a prayer is on, read from its varbit (Client.isPrayerActive is deprecated). */
	private static boolean on(Client c, Prayer p)
	{
		return c.getVarbitValue(p.getVarbit()) == 1;
	}

	public static String overheadKind(Client c)
	{
		if (on(c, Prayer.PROTECT_FROM_MELEE))
		{
			return "melee";
		}
		if (on(c, Prayer.PROTECT_FROM_MISSILES))
		{
			return "ranged";
		}
		if (on(c, Prayer.PROTECT_FROM_MAGIC))
		{
			return "magic";
		}
		if (on(c, Prayer.RETRIBUTION) || on(c, Prayer.REDEMPTION) || on(c, Prayer.SMITE))
		{
			return "other";
		}
		return null;
	}
}
