package com.superhistogram.collect;

import com.superhistogram.model.InputMark;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import java.util.ArrayList;
import java.util.List;

/**
 * Places clicks on the current tick at their in-tick position (0 = just after the tick, 1 = next tick).
 *
 * <p>With a ping set, a click made so late in the tick that it reaches the server after the next tick is
 * moved to that next tick, at the position it arrived (click position + ping). Its tick keeps a ghost where
 * you clicked. The ping counts in full: the tick reached you half a ping late, and the click takes half a
 * ping to get back.
 */
public final class ClickCollector
{
	private static final long TICK_MS = 600;

	private enum Kind
	{
		CLICK, PRAYER, OVERHEAD, INPUT
	}

	private static final class Moved
	{
		final Kind kind;
		final float at;
		final String overheadKind;
		final InputMark.Kind input;

		Moved(Kind kind, float at, String overheadKind, InputMark.Kind input)
		{
			this.kind = kind;
			this.at = at;
			this.overheadKind = overheadKind;
			this.input = input;
		}
	}

	private final Timeline timeline;
	private long lastTickMs = -1;
	private int pingMs;
	private final List<Moved> moved = new ArrayList<>();

	public ClickCollector(Timeline timeline)
	{
		this.timeline = timeline;
	}

	/** Round-trip ping used to move late clicks; 0 turns it off. */
	public void setPingMs(int pingMs)
	{
		this.pingMs = Math.max(0, Math.min((int) TICK_MS - 1, pingMs));
	}

	public int pingMs()
	{
		return pingMs;
	}

	/** A new tick: clicks that reached the server after the last one land on it. */
	public void onTick(int tick, long nowMs)
	{
		lastTickMs = nowMs;
		if (moved.isEmpty())
		{
			return;
		}
		TickRecord r = timeline.at(tick);
		if (r != null)
		{
			for (Moved m : moved)
			{
				add(r, m.kind, m.at, m.overheadKind, m.input);
			}
		}
		moved.clear();
	}

	/** True when a click now reaches the server only after the next tick, so the game sees it a tick later. */
	public boolean late(long nowMs)
	{
		return pingMs > 0 && lastTickMs >= 0 && (nowMs - lastTickMs) + pingMs >= TICK_MS;
	}

	/** Records a click on {@code tick}; ignored when the tick is not in the timeline. */
	public void onClick(int tick, long nowMs)
	{
		place(tick, nowMs, Kind.CLICK, null, null);
	}

	/** Records a prayer toggle click, kept apart from the general clicks. */
	public void onPrayerClick(int tick, long nowMs)
	{
		place(tick, nowMs, Kind.PRAYER, null, null);
	}

	/** Records a click on an overhead prayer; {@code kind} is what it turns on, null when it turns one off. */
	public void onOverheadClick(int tick, long nowMs, String kind)
	{
		place(tick, nowMs, Kind.OVERHEAD, kind, null);
	}

	/** Records a gear switch, spec, walk or drink, placed like a click. */
	public void onInput(int tick, long nowMs, InputMark.Kind input)
	{
		place(tick, nowMs, Kind.INPUT, null, input);
	}

	private void place(int tick, long nowMs, Kind kind, String overheadKind, InputMark.Kind input)
	{
		TickRecord r = timeline.at(tick);
		if (r == null)
		{
			return;
		}
		float f = fraction(nowMs);
		if (late(nowMs))
		{
			// Arrives after the next tick: it belongs there. The ghost marks where you clicked.
			float at = Math.min(0.99f, Math.max(0f, f + pingMs / (float) TICK_MS - 1f));
			moved.add(new Moved(kind, at, overheadKind, input));
			if (kind == Kind.CLICK)
			{
				r.clickGhosts.add(f);
			}
			return;
		}
		add(r, kind, f, overheadKind, input);
	}

	private static void add(TickRecord r, Kind kind, float at, String overheadKind, InputMark.Kind input)
	{
		switch (kind)
		{
			case CLICK:
				r.clicks.add(at);
				break;
			case PRAYER:
				r.prayerClicks.add(at);
				break;
			case INPUT:
				r.inputs.add(new InputMark(input, at));
				break;
			default:
				r.overheadClicks.add(at);
				r.overheadClickKind = overheadKind;
				break;
		}
	}

	private float fraction(long nowMs)
	{
		float f = lastTickMs < 0 ? 0f : (nowMs - lastTickMs) / (float) TICK_MS;
		return Math.max(0f, Math.min(0.99f, f));
	}
}
