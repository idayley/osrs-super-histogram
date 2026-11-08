package com.superhistogram.collect;

import com.superhistogram.model.Timeline;
import net.runelite.api.Client;
import net.runelite.api.Prayer;

/** Turns the player's own overhead-prayer state, observed each game tick, into timeline spans. */
public final class PrayerCollector
{
	private final Timeline timeline;
	private String current;

	public PrayerCollector(Timeline timeline)
	{
		this.timeline = timeline;
	}

	/** kind is "melee", "ranged", "magic", "other" or null when no overhead is on. */
	public void onTick(int tick, String kind)
	{
		if (kind == null ? current == null : kind.equals(current))
		{
			return;
		}
		// A click during tick t-1 shows on GameTick t, so spans are shifted back one tick.
		if (current != null)
		{
			timeline.closeSpan("pray-" + current, Math.max(0, tick - 2));
		}
		if (kind != null)
		{
			timeline.openSpan("pray-" + kind, Math.max(0, tick - 1));
		}
		current = kind;
	}

	public static String overheadKind(Client c)
	{
		if (c.isPrayerActive(Prayer.PROTECT_FROM_MELEE))
		{
			return "melee";
		}
		if (c.isPrayerActive(Prayer.PROTECT_FROM_MISSILES))
		{
			return "ranged";
		}
		if (c.isPrayerActive(Prayer.PROTECT_FROM_MAGIC))
		{
			return "magic";
		}
		if (c.isPrayerActive(Prayer.RETRIBUTION) || c.isPrayerActive(Prayer.REDEMPTION) || c.isPrayerActive(Prayer.SMITE))
		{
			return "other";
		}
		return null;
	}
}
