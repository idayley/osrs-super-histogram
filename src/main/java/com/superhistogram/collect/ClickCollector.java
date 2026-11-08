package com.superhistogram.collect;

import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;

/** Places clicks on the current tick at their in-tick position (0 = just after the tick, 1 = next tick). */
public final class ClickCollector
{
	private static final long TICK_MS = 600;

	private final Timeline timeline;
	private long lastTickMs = -1;

	public ClickCollector(Timeline timeline)
	{
		this.timeline = timeline;
	}

	public void onTick(long nowMs)
	{
		lastTickMs = nowMs;
	}

	/** Records a click on {@code tick}; ignored when the tick is not in the timeline. */
	public void onClick(int tick, long nowMs)
	{
		TickRecord r = timeline.at(tick);
		if (r != null)
		{
			r.clicks.add(fraction(nowMs));
		}
	}

	/** Records a prayer toggle click, kept apart from the general clicks. */
	public void onPrayerClick(int tick, long nowMs)
	{
		TickRecord r = timeline.at(tick);
		if (r != null)
		{
			r.prayerClicks.add(fraction(nowMs));
		}
	}

	private float fraction(long nowMs)
	{
		float f = lastTickMs < 0 ? 0f : (nowMs - lastTickMs) / (float) TICK_MS;
		return Math.max(0f, Math.min(0.99f, f));
	}
}
