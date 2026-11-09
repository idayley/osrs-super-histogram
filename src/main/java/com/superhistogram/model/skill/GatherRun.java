package com.superhistogram.model.skill;

import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks gathering (woodcutting, mining, fishing, firemaking). A pill is one click on a resource.
 * A roll is a tick animating while a pill is open; a success is an XP drop of the pill's skill,
 * optionally matched with the item gained on the same, previous or next tick.
 * Read by the render thread, so the public methods are synchronized and lists are snapshots.
 */
public final class GatherRun
{
	public static final Set<String> SKILLS = Collections.unmodifiableSet(new LinkedHashSet<>(
		Arrays.asList("Woodcutting", "Mining", "Fishing", "Firemaking", "Sailing")));
	/** Shipwreck salvage items: Sailing XP is a salvaging success only when one of these came in recently. */
	public static final Set<Integer> SALVAGE = GameIds.SALVAGE;
	/** Sailing XP this long after salvage came in is salvaging (the hook pulls every few ticks). */
	static final int SALVAGE_WINDOW = 12;
	private static final String SAILING = "Sailing";
	public static final int INVENTORY = 28;
	public static final int ROLL_WINDOW = 100;
	public static final int MIN_SUCCESSES = 2;
	public static final int IDLE_END = 3;
	public static final int STALE = 15;
	public static final int LOST_MAX = 10;
	public static final int LINGER = 20;
	public static final int HISTORY = 16;
	private static final String FIREMAKING = "Firemaking";

	/** An immutable snapshot of one pill. */
	public static final class Pill
	{
		public final String skill;
		public final int key;
		public final String name;
		public final int startTick;
		/** -1 while open. */
		public final int endTick;
		public final boolean depleted;
		public final int rolls;

		Pill(String skill, int key, String name, int startTick, int endTick, boolean depleted, int rolls)
		{
			this.skill = skill;
			this.key = key;
			this.name = name;
			this.startTick = startTick;
			this.endTick = endTick;
			this.depleted = depleted;
			this.rolls = rolls;
		}
	}

	private static final class MutablePill
	{
		final String skill;
		final int key;
		final String name;
		final int startTick;
		int endTick = -1;
		boolean depleted;
		int rolls;
		int lastRoll = -1;

		MutablePill(String skill, int key, String name, int startTick)
		{
			this.skill = skill;
			this.key = key;
			this.name = name;
			this.startTick = startTick;
		}

		Pill snapshot()
		{
			return new Pill(skill, key, name, startTick, endTick, depleted, rolls);
		}
	}

	private final Timeline timeline;
	private final List<MutablePill> history = new ArrayList<>();
	private final Map<String, Set<Integer>> gathered = new HashMap<>();
	private final Set<Integer> gone = new HashSet<>();
	private final boolean[] rolled = new boolean[ROLL_WINDOW];
	private final boolean[] succeeded = new boolean[ROLL_WINDOW];
	private int windowPos;
	private int lastEntryTick = -1;

	private Map<Integer, Integer> baseline;
	private int free = -1;
	/** The previous tick's unmatched item changes. */
	private Map<Integer, Integer> prevInc = new HashMap<>();
	private Map<Integer, Integer> prevDec = new HashMap<>();
	private int prevTick = -1;
	/** A success waiting for its item on the next tick; -1 none. */
	private int pendingTick = -1;
	/** The last tick shipwreck salvage came into the inventory. */
	private int lastSalvageTick = Integer.MIN_VALUE / 2;
	/** Salvage left the inventory this tick (sorting it at a station: production, not salvaging). */
	private boolean salvageOut;

	private MutablePill open;
	private String skill;
	private int successes;
	private int lastEnd = -1;
	private int lastActivity = -1;
	private boolean full;

	public GatherRun(Timeline timeline)
	{
		this.timeline = timeline;
	}

	public synchronized void onTargetClick(int tick, String skillName, int key, String name)
	{
		if (open != null && key != 0 && key == open.key && skillName.equals(open.skill))
		{
			return;
		}
		if (open != null)
		{
			closeOpen(tick);
		}
		if (lastEnd >= 0)
		{
			int d = tick - lastEnd;
			if (d >= 1 && d <= LOST_MAX)
			{
				TickRecord r = timeline.at(tick);
				if (r != null)
				{
					r.reclick = d;
				}
			}
			lastEnd = -1;
		}
		openPill(skillName, key, name, tick);
	}

	/**
	 * Call from the despawn event handler; {@link #onTick} only from GameTick. A despawn that
	 * arrives after onTick(t) is stamped at t + 1, because the next onTick consumes it.
	 */
	public synchronized void onTargetGone(int tick, int key)
	{
		gone.add(key);
	}

	public synchronized void onBankClick(int tick)
	{
		if (open != null)
		{
			closeOpen(tick);
		}
		lastEnd = -1;
		lastActivity = tick;
		if (full)
		{
			TickRecord r = timeline.at(tick);
			if (r != null)
			{
				r.gatherBank = true;
			}
		}
	}

	/**
	 * @param inventory full item counts, null when unchanged
	 * @param freeSlots free inventory slots, -1 when unchanged
	 * @return true exactly once when a success fills the inventory (Firemaking: uses the last log)
	 */
	public synchronized boolean onTick(int tick, boolean animating, List<XpDrop> drops,
		Map<Integer, Integer> inventory, int freeSlots)
	{
		TickRecord rec = timeline.at(tick);
		if (rec == null)
		{
			gone.clear();
			return false;
		}
		boolean successNow = false;

		// (1) inventory diff and free slots
		Map<Integer, Integer> inc = new HashMap<>();
		Map<Integer, Integer> dec = new HashMap<>();
		if (inventory != null)
		{
			if (baseline != null)
			{
				for (Map.Entry<Integer, Integer> e : inventory.entrySet())
				{
					int d = e.getValue() - baseline.getOrDefault(e.getKey(), 0);
					if (d > 0)
					{
						inc.put(e.getKey(), d);
					}
				}
				for (Map.Entry<Integer, Integer> e : baseline.entrySet())
				{
					int d = e.getValue() - inventory.getOrDefault(e.getKey(), 0);
					if (d > 0)
					{
						dec.put(e.getKey(), d);
					}
				}
			}
			baseline = new HashMap<>(inventory);
		}
		salvageOut = false;
		for (int id : SALVAGE)
		{
			if (inc.containsKey(id))
			{
				lastSalvageTick = tick;
			}
			if (dec.containsKey(id))
			{
				salvageOut = true;
			}
		}
		if (freeSlots >= 0)
		{
			free = freeSlots;
		}
		if (full && !fullCondition())
		{
			full = false;
		}
		if (prevTick != tick - 1)
		{
			prevInc = new HashMap<>();
			prevDec = new HashMap<>();
		}

		// a success from the previous tick waits for its item
		if (pendingTick >= 0)
		{
			if (pendingTick == tick - 1)
			{
				boolean fm = FIREMAKING.equals(skill);
				int item = take(fm ? dec : inc);
				if (item != 0)
				{
					mark(pendingTick, item);
					successNow = true;
				}
			}
			pendingTick = -1;
		}

		// (2) roll
		if (open != null && animating)
		{
			rec.roll = true;
			open.rolls++;
			open.lastRoll = tick;
			lastActivity = tick;
			pushEntry(tick, false);
		}

		// (3) success
		String xpSkill = pickSkill(drops, tick);
		boolean success = xpSkill != null;
		if (success)
		{
			if (open == null)
			{
				openPill(xpSkill, 0, "", tick - 1);
			}
			successes++;
			lastActivity = tick;
			if (lastEntryTick == tick)
			{
				succeeded[(windowPos + ROLL_WINDOW - 1) % ROLL_WINDOW] = true;
			}
			else
			{
				pushEntry(tick, true);
			}
			boolean fm = FIREMAKING.equals(xpSkill);
			int item = take(fm ? dec : inc);
			if (item == 0)
			{
				item = takeIfPrev(fm ? prevDec : prevInc, tick);
			}
			if (item != 0)
			{
				mark(tick, item);
			}
			else
			{
				pendingTick = tick;
			}
			successNow = true;
		}

		// (4) depletion
		if (open != null && open.key != 0 && gone.contains(open.key))
		{
			open.depleted = true;
			rec.depleted = true;
			lastEnd = tick;
			lastActivity = tick;
			closeOpen(tick);
		}
		gone.clear();

		// (5) Firemaking: a success ends the pill
		if (success && FIREMAKING.equals(xpSkill) && open != null)
		{
			lastEnd = tick;
			closeOpen(tick);
		}

		// (6) full / out event
		boolean fullEvent = false;
		if (successNow && !full && fullCondition())
		{
			full = true;
			fullEvent = true;
			if (open != null)
			{
				closeOpen(tick);
			}
		}

		// (7) idle end and stale drop
		if (open != null)
		{
			if (open.rolls > 0 && tick - open.lastRoll >= IDLE_END)
			{
				closeOpen(open.lastRoll + 1);
			}
			else if (open.rolls == 0 && tick - open.startTick >= STALE)
			{
				history.remove(open);
				open = null;
			}
		}

		prevInc = inc;
		prevDec = dec;
		prevTick = tick;
		return fullEvent;
	}

	public synchronized boolean showing(int tick)
	{
		if (history.isEmpty())
		{
			return false;
		}
		MutablePill latest = null;
		for (int i = history.size() - 1; i >= 0 && latest == null; i--)
		{
			// The newest pill that settled in (2+ rolls): a fresh tree's first roll doesn't drop the view for a tick.
			if (history.get(i).rolls >= 2)
			{
				latest = history.get(i);
			}
		}
		if (latest == null || latest.rolls < 2)
		{
			return false;
		}
		return (open != null && open.rolls > 0) || tick - lastActivity <= LINGER;
	}

	public synchronized boolean active()
	{
		return open != null;
	}

	public synchronized String skill()
	{
		return skill;
	}

	public synchronized String targetName()
	{
		return history.isEmpty() ? null : history.get(history.size() - 1).name;
	}

	/** Inventory total of the item ids gathered with the current skill. */
	public synchronized int count()
	{
		Set<Integer> ids = gathered.get(skill);
		if (ids == null || baseline == null)
		{
			return 0;
		}
		int n = 0;
		for (int id : ids)
		{
			n += baseline.getOrDefault(id, 0);
		}
		return n;
	}

	/** count + free slots; -1 when unknown (and for Firemaking). */
	public synchronized int capacity()
	{
		if (skill == null || FIREMAKING.equals(skill) || free < 0)
		{
			return -1;
		}
		return count() + free;
	}

	public synchronized boolean full()
	{
		return full;
	}

	public synchronized int successes()
	{
		return successes;
	}

	/** Successes per roll over the last ROLL_WINDOW rolls; 0 when unknown. */
	public synchronized double rate()
	{
		int s = windowSuccesses();
		return s < MIN_SUCCESSES ? 0 : s / (double) windowRolls();
	}

	/** Ticks until the inventory is full (Firemaking: logs gone); -1 when unknown. */
	public synchronized int etaTicks()
	{
		int s = windowSuccesses();
		if (s < MIN_SUCCESSES)
		{
			return -1;
		}
		int left = FIREMAKING.equals(skill) ? count() : free;
		if (left < 0)
		{
			return -1;
		}
		long n = (long) left * windowRolls();
		return (int) ((n + s - 1) / s);
	}

	public synchronized long perHour()
	{
		int s = windowSuccesses();
		return s < MIN_SUCCESSES ? 0 : Math.round(s * 6000.0 / windowRolls());
	}

	/** The tick to click the next resource; -1 when none or the inventory is full. */
	public synchronized int cueTick()
	{
		return lastEnd >= 0 && !full ? lastEnd + 1 : -1;
	}

	/** Pills oldest first, snapshots. */
	public synchronized List<Pill> pills()
	{
		List<Pill> out = new ArrayList<>();
		for (MutablePill p : history)
		{
			out.add(p.snapshot());
		}
		return out;
	}

	public synchronized void reset()
	{
		history.clear();
		gathered.clear();
		gone.clear();
		clearWindow();
		baseline = null;
		free = -1;
		prevInc = new HashMap<>();
		prevDec = new HashMap<>();
		prevTick = -1;
		pendingTick = -1;
		lastSalvageTick = Integer.MIN_VALUE / 2;
		open = null;
		skill = null;
		successes = 0;
		lastEnd = -1;
		lastActivity = -1;
		full = false;
	}

	private boolean fullCondition()
	{
		if (FIREMAKING.equals(skill))
		{
			Set<Integer> ids = gathered.get(skill);
			return ids != null && !ids.isEmpty() && count() == 0;
		}
		return free == 0;
	}

	private void openPill(String skillName, int key, String name, int startTick)
	{
		if (skill != null && !skill.equals(skillName))
		{
			clearWindow();
			successes = 0;
			pendingTick = -1;
		}
		skill = skillName;
		open = new MutablePill(skillName, key, name, startTick);
		history.add(open);
		while (history.size() > HISTORY)
		{
			history.remove(0);
		}
	}

	private void closeOpen(int endTick)
	{
		open.endTick = endTick;
		open = null;
	}

	private String pickSkill(List<XpDrop> drops, int tick)
	{
		if (drops == null)
		{
			return null;
		}
		for (XpDrop d : drops)
		{
			if (SAILING.equals(d.skill) && (salvageOut || tick - lastSalvageTick > SALVAGE_WINDOW))
			{
				// Sailing XP from anything but the salvage hook (sorting, courier tasks, the boat itself).
				continue;
			}
			if (open != null ? d.skill.equals(open.skill) : SKILLS.contains(d.skill))
			{
				return d.skill;
			}
		}
		return null;
	}

	/** Removes and returns the item with the largest change (lowest id on ties); 0 when empty. */
	private static int take(Map<Integer, Integer> changes)
	{
		int best = 0;
		int bestAmount = 0;
		for (Map.Entry<Integer, Integer> e : changes.entrySet())
		{
			if (e.getValue() > bestAmount || (e.getValue() == bestAmount && e.getKey() < best))
			{
				best = e.getKey();
				bestAmount = e.getValue();
			}
		}
		if (best != 0)
		{
			changes.remove(best);
		}
		return best;
	}

	private int takeIfPrev(Map<Integer, Integer> prev, int tick)
	{
		return prevTick == tick - 1 ? take(prev) : 0;
	}

	private void mark(int successTick, int item)
	{
		TickRecord r = timeline.at(successTick);
		if (r != null)
		{
			r.gathered = item;
		}
		gathered.computeIfAbsent(skill, k -> new HashSet<>()).add(item);
	}

	private void pushEntry(int tick, boolean success)
	{
		rolled[windowPos] = true;
		succeeded[windowPos] = success;
		windowPos = (windowPos + 1) % ROLL_WINDOW;
		lastEntryTick = tick;
	}

	private int windowRolls()
	{
		int n = 0;
		for (boolean b : rolled)
		{
			if (b)
			{
				n++;
			}
		}
		return Math.max(1, n);
	}

	private int windowSuccesses()
	{
		int n = 0;
		for (boolean b : succeeded)
		{
			if (b)
			{
				n++;
			}
		}
		return n;
	}

	private void clearWindow()
	{
		Arrays.fill(rolled, false);
		Arrays.fill(succeeded, false);
		windowPos = 0;
		lastEntryTick = -1;
	}
}
