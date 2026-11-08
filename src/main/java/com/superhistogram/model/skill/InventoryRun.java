package com.superhistogram.model.skill;

import com.superhistogram.model.Span;
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
 * Tracks a production run (cooking, fletching, ...) by watching input items leave the inventory.
 * An action is a decrease of an input item matched with an XP drop of a production skill.
 */
public final class InventoryRun
{
	public static final Set<String> SKILLS = Collections.unmodifiableSet(new LinkedHashSet<>(
		Arrays.asList("Cooking", "Fletching", "Herblore", "Crafting", "Smithing", "Prayer", "Sailing", "Construction")));
	public static final int DETECT_WINDOW = 12;
	public static final int LINGER = 20;
	public static final int HISTORY = 8;
	private static final int GAPS_KEPT = 3;

	/** A decrease waiting for XP on the same or next tick. */
	private static final class Pending
	{
		final int tick;
		final Map<Integer, Integer> dec;
		final Map<Integer, Integer> before;

		Pending(int tick, Map<Integer, Integer> dec, Map<Integer, Integer> before)
		{
			this.tick = tick;
			this.dec = dec;
			this.before = before;
		}
	}

	/** One matched action, not yet confirmed as a run. */
	private static final class Candidate
	{
		final String skill;
		final int tick;
		final Set<Integer> inputs;
		final int total;

		Candidate(String skill, int tick, Set<Integer> inputs, int total)
		{
			this.skill = skill;
			this.tick = tick;
			this.inputs = inputs;
			this.total = total;
		}
	}

	private final Timeline timeline;
	private final List<Span> history = new ArrayList<>();
	private final List<Integer> gaps = new ArrayList<>();
	private Map<Integer, Integer> baseline;
	private Pending pending;
	private Candidate candidate;

	private boolean running;
	private boolean finished;
	private String skill;
	private Set<Integer> inputs = new HashSet<>();
	private int total;
	private int done;
	private int lastActionTick = -1;
	private int bankTick = -1;
	private int wasted;
	private Span span;

	public InventoryRun(Timeline timeline)
	{
		this.timeline = timeline;
	}

	/**
	 * @param inventory full item counts, null when unchanged
	 * @return true exactly once per run, on the tick its last item is made
	 */
	public boolean onTick(int tick, List<XpDrop> drops, Map<Integer, Integer> inventory)
	{
		boolean finishedNow = false;
		Map<Integer, Integer> before = baseline;
		Map<Integer, Integer> dec = new HashMap<>();
		if (inventory != null)
		{
			if (baseline == null)
			{
				before = null;
			}
			else
			{
				boolean rose = false;
				for (Map.Entry<Integer, Integer> e : baseline.entrySet())
				{
					int d = e.getValue() - inventory.getOrDefault(e.getKey(), 0);
					if (d > 0)
					{
						dec.put(e.getKey(), d);
					}
				}
				Set<Integer> watched = running ? inputs : candidate != null ? candidate.inputs : null;
				if (watched != null)
				{
					for (int item : watched)
					{
						if (inventory.getOrDefault(item, 0) > baseline.getOrDefault(item, 0))
						{
							rose = true;
						}
					}
				}
				if (rose)
				{
					endRun();
					candidate = null;
					pending = null;
				}
			}
			baseline = new HashMap<>(inventory);
		}
		boolean haveBefore = before != null;
		String xpSkill = pickSkill(drops);
		if ("Sailing".equals(xpSkill) && !sorting(dec) && (pending == null || !sorting(pending.dec)))
		{
			// Sailing XP is production only when salvage is sorted (the boat, the hook, tasks give it too).
			xpSkill = null;
		}
		Map<Integer, Integer> own = haveBefore && relevant(dec, xpSkill) ? dec : Collections.<Integer, Integer>emptyMap();

		if (pending != null)
		{
			Pending p = pending;
			pending = null;
			if (xpSkill != null && own.isEmpty() && p.tick == tick - 1)
			{
				finishedNow |= action(p.tick, xpSkill, p.dec, p.before);
			}
			else if (running)
			{
				finishedNow |= failed(p.tick);
			}
		}
		if (!own.isEmpty())
		{
			if (xpSkill != null)
			{
				finishedNow |= action(tick, xpSkill, own, before);
			}
			else
			{
				pending = new Pending(tick, own, before);
			}
		}
		return finishedNow;
	}

	/**
	 * Only the first bank click within LINGER ticks of the finish counts as the run's bank click;
	 * later ones are ignored. A click while running abandons the run.
	 */
	public void onBankClick(int tick)
	{
		pending = null;
		candidate = null;
		if (running)
		{
			endRun();
		}
		else if (finished && bankTick == -1 && showing(tick))
		{
			bankTick = tick;
			wasted = Math.max(0, bankTick - lastActionTick);
			TickRecord r = timeline.at(tick);
			if (r != null)
			{
				r.bankClick = true;
				r.wasted = wasted;
			}
		}
	}

	public boolean showing(int tick)
	{
		return (running || finished) && tick - Math.max(lastActionTick, bankTick) <= LINGER;
	}

	public boolean running()
	{
		return running;
	}

	public boolean finished()
	{
		return finished;
	}

	public String skill()
	{
		return skill;
	}

	public int done()
	{
		return done;
	}

	public int total()
	{
		return total;
	}

	public int remaining()
	{
		return Math.max(0, total - done);
	}

	/** Ticks per action; 0 when unknown. */
	public int ticksPerAction()
	{
		int best = 0;
		for (int g : gaps)
		{
			if (best == 0 || g < best)
			{
				best = g;
			}
		}
		return best;
	}

	/** Predicted tick of the last item; the last action tick once finished; -1 when unknown. */
	public int finishTick()
	{
		if (running)
		{
			return lastActionTick + remaining() * ticksPerAction();
		}
		return finished ? lastActionTick : -1;
	}

	public int bankTick()
	{
		return bankTick;
	}

	public int wasted()
	{
		return wasted;
	}

	/** Runs oldest first, a copy. The end tick is -1 while a run is going. */
	public List<Span> runs()
	{
		return new ArrayList<>(history);
	}

	public void reset()
	{
		baseline = null;
		pending = null;
		candidate = null;
		running = false;
		finished = false;
		skill = null;
		inputs = new HashSet<>();
		total = 0;
		done = 0;
		lastActionTick = -1;
		bankTick = -1;
		wasted = 0;
		span = null;
		gaps.clear();
		history.clear();
	}

	/** True when shipwreck salvage left the inventory: sorting it at a salvaging station. */
	private static boolean sorting(Map<Integer, Integer> dec)
	{
		for (int id : dec.keySet())
		{
			if (GatherRun.SALVAGE.contains(id))
			{
				return true;
			}
		}
		return false;
	}

	/** While running, only decreases that touch the run's inputs, or come with another skill's XP, count. */
	private boolean relevant(Map<Integer, Integer> dec, String xpSkill)
	{
		if (dec.isEmpty())
		{
			return false;
		}
		return !running || intersects(dec.keySet(), inputs) || (xpSkill != null && !xpSkill.equals(skill));
	}

	private String pickSkill(List<XpDrop> drops)
	{
		if (drops == null)
		{
			return null;
		}
		String first = null;
		for (XpDrop d : drops)
		{
			if (!SKILLS.contains(d.skill))
			{
				continue;
			}
			if (running && d.skill.equals(skill))
			{
				return d.skill;
			}
			if (first == null)
			{
				first = d.skill;
			}
		}
		return first;
	}

	private static boolean intersects(Set<Integer> a, Set<Integer> b)
	{
		for (int x : a)
		{
			if (b.contains(x))
			{
				return true;
			}
		}
		return false;
	}

	/** A decrease matched with XP at actionTick. Returns true when it makes the run's last item. */
	private boolean action(int actionTick, String xpSkill, Map<Integer, Integer> dec, Map<Integer, Integer> before)
	{
		if (running)
		{
			if (xpSkill.equals(skill) && intersects(dec.keySet(), inputs))
			{
				return step(actionTick);
			}
			endRun();
		}
		if (skill != null && xpSkill.equals(skill) && intersects(dec.keySet(), inputs))
		{
			// The same thing again (the next inventory): no need to wait for a second action to recognise it.
			int gap = Math.max(1, ticksPerAction());
			start(xpSkill, dec.keySet(), totalOf(dec, before), actionTick - gap, gap, 0);
			candidate = null;
			return step(actionTick);
		}
		Candidate c = candidate;
		if (c != null && c.skill.equals(xpSkill) && actionTick > c.tick
			&& actionTick - c.tick <= DETECT_WINDOW && intersects(dec.keySet(), c.inputs))
		{
			candidate = null;
			start(xpSkill, c.inputs, c.total, c.tick, actionTick - c.tick, 1);
			return step(actionTick);
		}
		candidate = new Candidate(xpSkill, actionTick, new HashSet<>(dec.keySet()), totalOf(dec, before));
		return false;
	}

	/** How many actions the inventory before this one holds inputs for. */
	private static int totalOf(Map<Integer, Integer> dec, Map<Integer, Integer> before)
	{
		int tot = Integer.MAX_VALUE;
		for (Map.Entry<Integer, Integer> e : dec.entrySet())
		{
			int had = before == null ? 0 : before.getOrDefault(e.getKey(), 0);
			tot = Math.min(tot, had / e.getValue());
		}
		return Math.max(1, tot);
	}

	/** Starts a run whose last counted action was at {@code lastTick} ({@code done} so far). */
	private void start(String xpSkill, Set<Integer> in, int tot, int lastTick, int gap, int doneSoFar)
	{
		skill = xpSkill;
		inputs = new HashSet<>(in);
		total = tot;
		done = doneSoFar;
		lastActionTick = lastTick;
		gaps.clear();
		bankTick = -1;
		wasted = 0;
		finished = false;
		running = true;
		span = timeline.openSpan(skill, Math.max(0, lastTick - (doneSoFar > 0 ? gap : 0)));
		history.add(span);
		while (history.size() > HISTORY)
		{
			history.remove(0);
		}
	}

	/** Counts one action at actionTick. Returns true when it was the last item. */
	private boolean step(int actionTick)
	{
		done++;
		int gap = actionTick - lastActionTick;
		if (gap > 0)
		{
			gaps.add(gap);
			if (gaps.size() > GAPS_KEPT)
			{
				gaps.remove(0);
			}
		}
		lastActionTick = actionTick;
		if (done >= total)
		{
			running = false;
			finished = true;
			closeSpan();
			return true;
		}
		return false;
	}

	/**
	 * A decrease that got no XP by the next tick (burnt). If it was the last item, the run finishes
	 * one tick late: onTick returns true on the tick the miss is known, finishTick is the decrease tick.
	 */
	private boolean failed(int actionTick)
	{
		TickRecord r = timeline.at(actionTick);
		if (r != null)
		{
			r.failedAction = true;
		}
		return step(actionTick);
	}

	/** Ends a run that has not finished: abandon or switch. No blink. */
	private void endRun()
	{
		if (running)
		{
			running = false;
			closeSpan();
		}
	}

	private void closeSpan()
	{
		if (span != null)
		{
			span.endTick = lastActionTick;
			timeline.closeSpan(span.kind, lastActionTick);
			span = null;
		}
	}
}
