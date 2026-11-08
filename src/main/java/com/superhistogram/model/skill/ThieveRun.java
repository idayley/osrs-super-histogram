package com.superhistogram.model.skill;

import com.superhistogram.model.Span;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks pickpocketing and stall thieving. Each click is an attempt (a pill on the bar); Thieving XP ends it as a
 * success, a hit on you shortly after ends it as a failure and starts the stun (no new attempt for 8 ticks).
 * The readout counts coin pouches (pickpocketing, up to the pouch cap) or stolen items (stalls, up to a full
 * inventory). Read by the render thread, so the public methods are synchronized.
 */
public final class ThieveRun
{
	public static final String SKILL = "Thieving";
	/** Timeline span kinds: an attempt, and the stun after a failure. */
	public static final String KIND = "thieve", STUN = "stunned";
	/** Ticks you can't pickpocket after a failure. */
	public static final int STUN_TICKS = 8;
	/** A hit this many ticks after the attempt is its stun. */
	public static final int FAIL_WINDOW = 3;
	/** An attempt with no outcome for this long is dropped (you walked off). */
	public static final int STALE = 6;
	/** The view stays this long after the last attempt. */
	public static final int LINGER = 20;
	/** Two successes at most this far apart mean you're thieving. */
	public static final int SHOW_GAP = 30;
	/** Outcomes kept for the success rate. */
	public static final int WINDOW = 50;
	/** The coin pouch cap without the Ardougne diary. */
	public static final int DEFAULT_CAP = 28;
	/** The pouch caps: no Ardougne diary, then the medium, hard and elite diaries. */
	static final int[] CAPS = {28, 56, 84, 140};
	/** The game's message when you hit the pouch cap. */
	public static final String CAP_MESSAGE = "empty your coin pouches";

	/** Every coin pouch item id. */
	public static final Set<Integer> POUCHES = idsStartingWith("PICKPOCKET_COIN_POUCH");

	private Timeline timeline;
	private int pendingTick = -1;
	private boolean stall;
	private String target = "";
	private int stunUntil = -1;
	private int lastSuccess = -1, prevSuccess = -1, lastActivity = -1;
	private final ArrayDeque<Boolean> outcomes = new ArrayDeque<>();
	/** Ticks of the last successes, for the pace. */
	private final ArrayDeque<Integer> successTicks = new ArrayDeque<>();
	private int successes, fails;
	private Map<Integer, Integer> inventory;
	private int free = -1;
	private final Set<Integer> stolen = new HashSet<>();
	private int cap = DEFAULT_CAP;
	/** The last success gave a coin pouch (most pickpockets); otherwise items count (stalls, master farmers). */
	private boolean pouchMode;
	/** The cap message came; the cap is read from the pouches once this tick's inventory is in. */
	private boolean capPending;

	public ThieveRun(Timeline timeline)
	{
		this.timeline = timeline;
	}

	/** Item ids in RuneLite's ItemID whose constant name starts with {@code prefix}. */
	static Set<Integer> idsStartingWith(String prefix)
	{
		Set<Integer> out = new HashSet<>();
		try
		{
			for (Field f : Class.forName("net.runelite.api.gameval.ItemID").getFields())
			{
				if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class && f.getName().startsWith(prefix))
				{
					out.add(f.getInt(null));
				}
			}
		}
		catch (ReflectiveOperationException e)
		{
			// No item table (never in the client): nothing counts.
		}
		return Collections.unmodifiableSet(out);
	}

	/** You clicked Pickpocket (or Steal-from on a stall, {@code stall}); the game acts on it at {@code tick}. */
	public synchronized void onAttempt(int tick, String targetName, boolean isStall)
	{
		if (tick < stunUntil)
		{
			// "You're stunned": the click does nothing.
			return;
		}
		if (pendingTick >= 0 && tick - pendingTick < 2)
		{
			// Spam clicks within one attempt.
			return;
		}
		if (pendingTick >= 0)
		{
			timeline.closeSpan(KIND, tick - 1);
		}
		pendingTick = tick;
		if (isStall)
		{
			pouchMode = false;
		}
		stall = isStall;
		target = targetName == null ? "" : targetName;
		timeline.openSpan(KIND, tick);
		lastActivity = tick;
	}

	/** A game message; the pouch-cap one teaches the cap (it rises with the Ardougne diary). */
	public synchronized void onMessage(String message)
	{
		if (message != null && message.contains(CAP_MESSAGE))
		{
			capPending = true;
		}
	}

	/**
	 * @param inventory full item counts, null when unchanged
	 * @param freeSlots free inventory slots, -1 when unchanged
	 */
	public synchronized void onTick(int tick, List<XpDrop> drops, boolean hitOnMe, Map<Integer, Integer> inventory,
		int freeSlots)
	{
		Map<Integer, Integer> gained = new HashMap<>();
		if (inventory != null)
		{
			if (this.inventory != null)
			{
				for (Map.Entry<Integer, Integer> e : inventory.entrySet())
				{
					int d = e.getValue() - this.inventory.getOrDefault(e.getKey(), 0);
					if (d > 0)
					{
						gained.put(e.getKey(), d);
					}
				}
			}
			this.inventory = new HashMap<>(inventory);
		}
		int n = pouches();
		if (capPending && n > 0)
		{
			// At the cap: it is the pouches you hold (to the nearest cap, in case one more is still on its way).
			cap = CAPS[CAPS.length - 1];
			for (int i = CAPS.length - 1; i >= 0; i--)
			{
				if (CAPS[i] >= n)
				{
					cap = CAPS[i];
				}
			}
			capPending = false;
		}
		// More pouches than the cap we assumed: a diary raised it.
		for (int c : CAPS)
		{
			if (cap < n && c >= n)
			{
				cap = c;
			}
		}
		if (freeSlots >= 0)
		{
			free = freeSlots;
		}
		boolean xp = false;
		for (XpDrop d : drops)
		{
			if (SKILL.equals(d.skill))
			{
				xp = true;
			}
		}
		if (xp)
		{
			if (pendingTick < 0)
			{
				// A success without a click we saw (a queued click, blackjacking): a two-tick attempt.
				timeline.openSpan(KIND, tick - 1);
			}
			timeline.closeSpan(KIND, tick);
			outcome(true);
			successes++;
			prevSuccess = lastSuccess;
			lastSuccess = tick;
			successTicks.addLast(tick);
			while (successTicks.size() > 12)
			{
				successTicks.pollFirst();
			}
			lastActivity = tick;
			pendingTick = -1;
			TickRecord r = timeline.at(tick);
			if (r != null)
			{
				r.stolen = true;
			}
			boolean pouch = false, items = false;
			for (int id : gained.keySet())
			{
				if (POUCHES.contains(id))
				{
					pouch = true;
				}
				else
				{
					stolen.add(id);
					items = true;
				}
			}
			if (pouch || items)
			{
				pouchMode = pouch && !stall;
			}
		}
		else if (hitOnMe && pendingTick >= 0 && tick - pendingTick <= FAIL_WINDOW)
		{
			timeline.closeSpan(KIND, tick);
			outcome(false);
			fails++;
			lastActivity = tick;
			pendingTick = -1;
			stunUntil = tick + 1 + STUN_TICKS;
			Span s = timeline.openSpan(STUN, tick + 1);
			s.endTick = tick + STUN_TICKS;
			s.closedAt = tick;
			s.label = "stunned";
		}
		else if (pendingTick >= 0 && tick - pendingTick > STALE)
		{
			timeline.closeSpan(KIND, pendingTick + 1);
			pendingTick = -1;
		}
	}

	private void outcome(boolean success)
	{
		outcomes.addLast(success);
		while (outcomes.size() > WINDOW)
		{
			outcomes.pollFirst();
		}
	}

	/** True while thieving: two successes close together, and the last attempt recent. */
	public synchronized boolean showing(int tick)
	{
		return lastSuccess >= 0 && prevSuccess >= 0 && lastSuccess - prevSuccess <= SHOW_GAP
			&& tick - lastActivity <= LINGER;
	}

	/** True when the last target was a stall (items to a full inventory) rather than a pickpocket (pouches). */
	public synchronized boolean stall()
	{
		return stall;
	}

	public synchronized String target()
	{
		return target;
	}

	/** Coin pouches in the inventory. */
	public synchronized int pouches()
	{
		int n = 0;
		if (inventory != null)
		{
			for (int id : POUCHES)
			{
				n += inventory.getOrDefault(id, 0);
			}
		}
		return n;
	}

	/** True while counting coin pouches; false while counting stolen items to a full inventory. */
	public synchronized boolean countsPouches()
	{
		return pouchMode;
	}

	/** Pouches (pickpocketing) or stolen items in the inventory (stalls, seeds). */
	public synchronized int count()
	{
		if (pouchMode)
		{
			return pouches();
		}
		int n = 0;
		if (inventory != null)
		{
			for (int id : stolen)
			{
				n += inventory.getOrDefault(id, 0);
			}
		}
		return n;
	}

	/** The pouch cap (pickpocketing) or count + free slots (stalls); -1 when unknown. */
	public synchronized int capacity()
	{
		if (pouchMode)
		{
			return cap;
		}
		return free < 0 ? -1 : count() + free;
	}

	/** Percent of the last attempts that succeeded; -1 before three. */
	public synchronized int successRate()
	{
		if (outcomes.size() < 3)
		{
			return -1;
		}
		int ok = 0;
		for (boolean b : outcomes)
		{
			if (b)
			{
				ok++;
			}
		}
		return Math.round(ok * 100f / outcomes.size());
	}

	/** Average ticks per success over the last few (failures and stuns included); -1 before two. */
	public synchronized float ticksPerSuccess()
	{
		if (successTicks.size() < 2)
		{
			return -1;
		}
		return (successTicks.peekLast() - successTicks.peekFirst()) / (float) (successTicks.size() - 1);
	}

	public synchronized int successes()
	{
		return successes;
	}

	public synchronized int fails()
	{
		return fails;
	}

	/** True until the stun after a failure wears off. */
	public synchronized boolean stunned(int tick)
	{
		return tick < stunUntil;
	}

	/** A fresh timeline (after logout): attempts in progress are dropped. */
	public synchronized void rebind(Timeline t)
	{
		timeline = t;
		pendingTick = -1;
		stunUntil = -1;
		lastSuccess = -1;
		prevSuccess = -1;
		lastActivity = -1;
		successTicks.clear();
	}

	/** Another account: everything goes. */
	public synchronized void reset()
	{
		rebind(timeline);
		outcomes.clear();
		successes = 0;
		fails = 0;
		inventory = null;
		free = -1;
		stolen.clear();
		cap = DEFAULT_CAP;
		pouchMode = false;
		capPending = false;
	}
}
