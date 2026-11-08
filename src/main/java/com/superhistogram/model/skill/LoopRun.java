package com.superhistogram.model.skill;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks a loop you repeat: Runecraft trips (each craft at an altar ends one), farm runs and bird house runs
 * (each patch or house is a stop; a run is stops close together in time). A stop is a burst of the skill's XP:
 * drops at most {@link Kind#merge} ticks apart are one stop (a pouch emptied into a second craft, a harvest then
 * a plant). Each kind is gated so other XP of that skill doesn't count: Runecraft XP only with essence leaving
 * the inventory, Hunter XP only right after you click a bird house. Read by the render thread: public methods are
 * synchronized and lists are snapshots.
 */
public final class LoopRun
{
	public enum Kind
	{
		/** A trip ends at each craft; trips have no runs (a run is the whole session of trips). */
		RUNECRAFT("Runecraft", "trip", 10, 1000, 200),
		FARMING("Farming", "patch", 25, 500, 400),
		BIRDHOUSE("Hunter", "bird house", 25, 500, 400);

		public final String skill;
		public final String noun;
		/** XP drops at most this many ticks apart are one stop. */
		public final int merge;
		/** A gap longer than this between stops starts a new run. */
		public final int runGap;
		/** The view stays this long after the last stop. */
		public final int linger;

		Kind(String skill, String noun, int merge, int runGap, int linger)
		{
			this.skill = skill;
			this.noun = noun;
			this.merge = merge;
			this.runGap = runGap;
			this.linger = linger;
		}
	}

	/** Where a stop (or, for Runecraft, a trip) starts, numbered within its run. */
	public static final class Mark
	{
		public final int tick;
		public final int number;

		Mark(int tick, int number)
		{
			this.tick = tick;
			this.number = number;
		}
	}

	/** A bird house click makes the Hunter XP this many ticks later at most a bird house's (you may walk there). */
	static final int CLICK_WINDOW = 30;
	static final int MARKS_KEPT = 16;

	/** Every essence a rune is crafted from (not Guardians of the Rift's, which is a minigame). */
	static final Set<Integer> ESSENCE = essence();
	/** Every bird house object (empty spot, built, full). */
	static final Set<Integer> BIRDHOUSES = birdhouses();

	private final Kind kind;
	private Timeline timeline;
	private Map<Integer, Integer> inventory;
	private final List<Mark> marks = new ArrayList<>();
	private int lastClick = -100;
	/** Last tick essence left the inventory (the craft's XP may come a tick apart from it). */
	private int lastEssence = -100;
	private int lastEvent = -1;
	private int stopStart = -1, prevStopStart = -1;
	private int runStart = -1;
	private int inRun;
	private int prevRunCount = -1;
	private int lastRunTicks = -1, bestRunTicks = -1;
	private int total;
	private int lastLoopTicks = -1, bestLoopTicks = -1;
	private int stopXp, lastStopXp;

	public LoopRun(Kind kind, Timeline timeline)
	{
		this.kind = kind;
		this.timeline = timeline;
	}

	private static Set<Integer> essence()
	{
		Set<Integer> out = new HashSet<>();
		try
		{
			for (Field f : Class.forName("net.runelite.api.gameval.ItemID").getFields())
			{
				String n = f.getName();
				if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class && !n.startsWith("GOTR_")
					&& (n.startsWith("BLANKRUNE") || n.endsWith("_ESSENCE") && !n.startsWith("BLOOD_")
					|| n.startsWith("ARCEUUS_ESSENCE_BLOCK") || n.equals("BIGBLANKRUNE")))
				{
					out.add(f.getInt(null));
				}
			}
		}
		catch (ReflectiveOperationException e)
		{
			// No item table: no essence known.
		}
		return Collections.unmodifiableSet(out);
	}

	private static Set<Integer> birdhouses()
	{
		Set<Integer> out = new HashSet<>();
		try
		{
			for (Field f : Class.forName("net.runelite.api.gameval.ObjectID").getFields())
			{
				if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class && f.getName().startsWith("BIRDHOUSE_"))
				{
					out.add(f.getInt(null));
				}
			}
		}
		catch (ReflectiveOperationException e)
		{
			// No object table: no bird houses.
		}
		return Collections.unmodifiableSet(out);
	}

	/** True for a bird house object (or the empty spot one goes on). */
	public static boolean isBirdhouse(int objectId)
	{
		return BIRDHOUSES.contains(objectId);
	}

	public Kind kind()
	{
		return kind;
	}

	/** You clicked the thing this loop is about (a bird house); the game acts at {@code tick}. */
	public synchronized void onClick(int tick)
	{
		lastClick = tick;
	}

	/**
	 * @param inventory full item counts, null when unchanged
	 */
	public synchronized void onTick(int tick, List<XpDrop> drops, Map<Integer, Integer> inventory)
	{
		if (inventory != null)
		{
			if (this.inventory != null)
			{
				for (int id : ESSENCE)
				{
					if (inventory.getOrDefault(id, 0) < this.inventory.getOrDefault(id, 0))
					{
						lastEssence = tick;
					}
				}
			}
			this.inventory = new java.util.HashMap<>(inventory);
		}
		int xp = 0;
		for (XpDrop d : drops)
		{
			if (kind.skill.equals(d.skill))
			{
				xp += Math.max(0, d.amount);
			}
		}
		if (xp <= 0 || !gate(tick))
		{
			return;
		}
		if (lastEvent < 0 || tick - lastEvent > kind.merge)
		{
			stop(tick);
		}
		stopXp += xp;
		lastEvent = tick;
	}

	private boolean gate(int tick)
	{
		switch (kind)
		{
			case RUNECRAFT:
				return tick - lastEssence <= 1;
			case BIRDHOUSE:
				return tick - lastClick <= CLICK_WINDOW && tick >= lastClick;
			default:
				return true;
		}
	}

	/** A new stop (a craft, a patch, a bird house) starts at {@code tick}. */
	private void stop(int tick)
	{
		if (lastEvent < 0 || tick - lastEvent > kind.runGap)
		{
			if (runStart >= 0)
			{
				// The last run is over.
				prevRunCount = inRun;
				lastRunTicks = lastEvent - runStart;
				if (inRun >= 2 && (bestRunTicks < 0 || lastRunTicks < bestRunTicks))
				{
					bestRunTicks = lastRunTicks;
				}
			}
			runStart = tick;
			inRun = 0;
			prevStopStart = -1;
			stopStart = -1;
		}
		if (stopStart >= 0)
		{
			lastLoopTicks = tick - stopStart;
			if (bestLoopTicks < 0 || lastLoopTicks < bestLoopTicks)
			{
				bestLoopTicks = lastLoopTicks;
			}
		}
		prevStopStart = stopStart;
		stopStart = tick;
		inRun++;
		total++;
		if (stopXp > 0)
		{
			lastStopXp = stopXp;
		}
		stopXp = 0;
		// For Runecraft the craft ends a trip: the line marks the next trip starting.
		marks.add(new Mark(tick, kind == Kind.RUNECRAFT ? inRun + 1 : inRun));
		while (marks.size() > MARKS_KEPT)
		{
			marks.remove(0);
		}
	}

	/** True while looping: two stops in this run and the last one recent. */
	public synchronized boolean showing(int tick)
	{
		return inRun >= 2 && lastEvent >= 0 && tick - lastEvent <= kind.linger;
	}

	public synchronized List<Mark> marks()
	{
		return new ArrayList<>(marks);
	}

	/** Stops (crafts) in the current run. */
	public synchronized int inRun()
	{
		return inRun;
	}

	/** Stops in the run before this one; -1 before one ended. */
	public synchronized int previousRunCount()
	{
		return prevRunCount;
	}

	/** First tick of the current run; -1 before one. */
	public synchronized int runStartTick()
	{
		return runStart;
	}

	/** Ticks of your fastest run of two or more stops; -1 before one. */
	public synchronized int bestRunTicks()
	{
		return bestRunTicks;
	}

	/** Stops (crafts) since login. */
	public synchronized int total()
	{
		return total;
	}

	/** Ticks between the last two stops (a Runecraft trip); -1 before two. */
	public synchronized int lastLoopTicks()
	{
		return lastLoopTicks;
	}

	public synchronized int bestLoopTicks()
	{
		return bestLoopTicks;
	}

	/** Start of the stop (trip) under way. */
	public synchronized int stopStartTick()
	{
		return stopStart;
	}

	/** The skill's XP of the last whole stop (a craft, pouches included); the one under way before that; 0 before one. */
	public synchronized int stopXp()
	{
		return lastStopXp > 0 ? lastStopXp : stopXp;
	}

	/** A fresh timeline (after logout): the run in progress is dropped. */
	public synchronized void rebind(Timeline t)
	{
		timeline = t;
		marks.clear();
		lastEvent = -1;
		stopStart = -1;
		prevStopStart = -1;
		runStart = -1;
		inRun = 0;
		lastClick = -100;
		lastEssence = -100;
		inventory = null;
	}

	/** Another account. */
	public synchronized void reset()
	{
		rebind(timeline);
		prevRunCount = -1;
		lastRunTicks = -1;
		bestRunTicks = -1;
		total = 0;
		lastLoopTicks = -1;
		bestLoopTicks = -1;
		stopXp = 0;
		lastStopXp = 0;
	}
}
