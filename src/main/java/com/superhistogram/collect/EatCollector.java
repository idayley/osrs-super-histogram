package com.superhistogram.collect;

import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.combat.AttackTimer;
import com.superhistogram.model.combat.FoodKind;
import com.superhistogram.model.combat.Foods;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Confirms "Eat" clicks by watching inventory counts drop, then charges the attack timer. */
public final class EatCollector
{
	private static final int CLICK_LIFE_TICKS = 2;

	private static final class Click
	{
		final int tick;
		final int itemId;

		Click(int tick, int itemId)
		{
			this.tick = tick;
			this.itemId = itemId;
		}
	}

	private final Timeline timeline;
	private final AttackTimer timer;
	private final List<Click> pending = new ArrayList<>();
	private Map<Integer, Integer> baseline;

	public EatCollector(Timeline timeline, AttackTimer timer)
	{
		this.timeline = timeline;
		this.timer = timer;
	}

	public void onEatClick(int tick, int itemId)
	{
		pending.add(new Click(tick, itemId));
	}

	public void onInventory(int tick, Map<Integer, Integer> counts)
	{
		Map<Integer, Integer> prev = baseline;
		baseline = new HashMap<>(counts);
		if (prev == null)
		{
			return;
		}
		for (Iterator<Click> it = pending.iterator(); it.hasNext(); )
		{
			if (tick - it.next().tick > CLICK_LIFE_TICKS)
			{
				it.remove();
			}
		}
		if (pending.isEmpty())
		{
			return;
		}
		List<Click> confirmed = new ArrayList<>();
		Map<Integer, Integer> drops = new HashMap<>();
		for (Map.Entry<Integer, Integer> e : prev.entrySet())
		{
			int drop = e.getValue() - counts.getOrDefault(e.getKey(), 0);
			if (drop > 0)
			{
				drops.put(e.getKey(), drop);
			}
		}
		// pending is in click order, so the oldest clicks for an ID are confirmed first.
		for (Iterator<Click> it = pending.iterator(); it.hasNext(); )
		{
			Click c = it.next();
			Integer left = drops.get(c.itemId);
			if (left != null && left > 0)
			{
				drops.put(c.itemId, left - 1);
				confirmed.add(c);
				it.remove();
			}
		}
		for (Click c : confirmed)
		{
			FoodKind kind = Foods.kindOf(c.itemId);
			int cost = timer.onEat(c.tick, kind);
			TickRecord r = timeline.at(c.tick);
			if (r != null)
			{
				r.eat = r.eat == null ? kind : FoodKind.merge(r.eat, kind);
				r.eatCost = cost;
			}
		}
	}
}
