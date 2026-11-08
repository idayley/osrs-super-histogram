package com.superhistogram.model.skill;

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
 * Tracks a skilling spell other than High Level Alchemy (Plank Make, Enchant, Superheat, Spin Flax, String
 * Jewellery, ...). A spell click is a candidate; Magic XP within {@link #PENDING_TICKS} confirms the cast. Each
 * cast is a pill on the bar. The spell's rhythm is learned as the shortest gap between recent casts, and the
 * ticks a cast came later than that are lost. The input is the clicked item, or for spells cast on nothing the
 * item (not a rune or coins) that leaves the inventory on the cast; casts left = what's left of it / per cast.
 * Read by the render thread: public methods are synchronized.
 */
public final class SpellRun
{
	public static final String KIND = "spell";
	static final int PENDING_TICKS = 2;
	/** Two casts further apart than this are not one session. */
	static final int SHOW_GAP = 15;
	public static final int LINGER = 10;
	public static final int LOST_MAX = 10;
	/** Gaps kept for the rhythm. */
	static final int GAPS = 8;

	/** Runes and coins: spent by casts, never their input. */
	static final Set<Integer> RUNES = runes();

	private Timeline timeline;
	private String pendingSpell;
	private int pendingItem;
	private int pendingTick = -1;
	private String spell;
	private int input;
	/** Input items per cast (1 for spells on an item). */
	private int perCast = 1;
	private int lastCast = -1, prevCast = -1;
	private int casts;
	private final ArrayDeque<Integer> gaps = new ArrayDeque<>();
	private Map<Integer, Integer> inventory;
	private boolean spanOpen;

	public SpellRun(Timeline timeline)
	{
		this.timeline = timeline;
	}

	private static Set<Integer> runes()
	{
		Set<Integer> out = new HashSet<>();
		try
		{
			for (Field f : Class.forName("net.runelite.api.gameval.ItemID").getFields())
			{
				String n = f.getName();
				if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class
					&& (n.matches("[A-Z]+RUNE") || n.equals("COINS")))
				{
					out.add(f.getInt(null));
				}
			}
		}
		catch (ReflectiveOperationException e)
		{
			// No item table: no runes known.
		}
		return Collections.unmodifiableSet(out);
	}

	/**
	 * You clicked a spell; the game acts at {@code tick}. {@code itemId} is the item it was cast on, or 0 for a
	 * spell cast on nothing (Spin Flax) or on an object.
	 */
	public synchronized void onCastClick(int tick, String spellName, int itemId)
	{
		pendingSpell = spellName;
		pendingItem = itemId;
		pendingTick = tick;
	}

	/**
	 * @param inventory full item counts, null when unchanged
	 */
	public synchronized void onTick(int tick, List<XpDrop> drops, Map<Integer, Integer> inventory)
	{
		Map<Integer, Integer> dec = new HashMap<>();
		if (inventory != null)
		{
			if (this.inventory != null)
			{
				for (Map.Entry<Integer, Integer> e : this.inventory.entrySet())
				{
					int d = e.getValue() - inventory.getOrDefault(e.getKey(), 0);
					if (d > 0)
					{
						dec.put(e.getKey(), d);
					}
				}
			}
			this.inventory = new HashMap<>(inventory);
		}
		boolean magic = false;
		for (XpDrop d : drops)
		{
			if ("Magic".equals(d.skill))
			{
				magic = true;
			}
		}
		if (pendingTick >= 0 && tick > pendingTick + PENDING_TICKS)
		{
			pendingTick = -1;
		}
		if (magic && pendingTick >= 0 && tick >= pendingTick)
		{
			cast(tick, dec);
			pendingTick = -1;
		}
		else if (spanOpen && (tick - lastCast >= LINGER || rhythm() > 0 && tick >= lastCast + rhythm()))
		{
			timeline.closeSpan(KIND, lastCast + Math.max(1, rhythm() > 0 ? rhythm() : 1) - 1);
			spanOpen = false;
		}
	}

	private void cast(int tick, Map<Integer, Integer> dec)
	{
		if (!pendingSpell.equals(spell))
		{
			// Another spell: a new run.
			spell = pendingSpell;
			gaps.clear();
			lastCast = -1;
			prevCast = -1;
			input = 0;
			perCast = 1;
			casts = 0;
		}
		if (pendingItem > 0)
		{
			input = pendingItem;
			perCast = 1;
		}
		else
		{
			int best = 0, amount = 0;
			for (Map.Entry<Integer, Integer> e : dec.entrySet())
			{
				if (!RUNES.contains(e.getKey()) && e.getValue() > amount)
				{
					best = e.getKey();
					amount = e.getValue();
				}
			}
			if (best != 0)
			{
				input = best;
				perCast = amount;
			}
		}
		if (lastCast >= 0)
		{
			int gap = tick - lastCast;
			if (gap <= SHOW_GAP)
			{
				gaps.addLast(gap);
				while (gaps.size() > GAPS)
				{
					gaps.pollFirst();
				}
			}
			if (spanOpen)
			{
				timeline.closeSpan(KIND, Math.min(tick - 1, lastCast + Math.max(1, rhythm()) - 1));
			}
		}
		int r = rhythm();
		int lost = lastCast < 0 || r <= 0 ? 0 : tick - lastCast - r;
		TickRecord rec = timeline.at(tick);
		if (rec != null && lost >= 1 && lost <= LOST_MAX)
		{
			rec.spellLost = lost;
		}
		timeline.openSpan(KIND, tick);
		spanOpen = true;
		prevCast = lastCast;
		lastCast = tick;
		casts++;
	}

	/** The spell's own pace: the shortest recent gap between casts; -1 before two casts. */
	public synchronized int rhythm()
	{
		int min = -1;
		for (int g : gaps)
		{
			if (min < 0 || g < min)
			{
				min = g;
			}
		}
		return min;
	}

	/** True while casting: two casts close together, the last one recent. */
	public synchronized boolean showing(int tick)
	{
		return lastCast >= 0 && prevCast >= 0 && lastCast - prevCast <= SHOW_GAP && tick - lastCast <= LINGER;
	}

	public synchronized String spell()
	{
		return spell;
	}

	/** Casts of this spell since you started it. */
	public synchronized int casts()
	{
		return casts;
	}

	/** Casts left from the input in the inventory; -1 when the input is unknown. */
	public synchronized int left()
	{
		if (input == 0 || inventory == null)
		{
			return -1;
		}
		return inventory.getOrDefault(input, 0) / Math.max(1, perCast);
	}

	/** Ticks until the input runs out at the spell's pace; -1 when unknown. */
	public synchronized int etaTicks()
	{
		int left = left(), r = rhythm();
		return left < 0 || r <= 0 ? -1 : left * r;
	}

	/** Tick the next cast can land, or -1. */
	public synchronized int nextReadyTick()
	{
		int r = rhythm();
		return lastCast < 0 || r <= 0 ? -1 : lastCast + r;
	}

	public synchronized void rebind(Timeline t)
	{
		timeline = t;
		pendingTick = -1;
		lastCast = -1;
		prevCast = -1;
		gaps.clear();
		spanOpen = false;
	}

	public synchronized void reset()
	{
		rebind(timeline);
		spell = null;
		input = 0;
		perCast = 1;
		casts = 0;
		inventory = null;
	}
}
