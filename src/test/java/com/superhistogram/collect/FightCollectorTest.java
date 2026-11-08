package com.superhistogram.collect;

import static org.junit.Assert.assertEquals;

import com.superhistogram.model.Span;
import com.superhistogram.model.Timeline;
import java.util.List;
import org.junit.Test;

public class FightCollectorTest
{
	private final Timeline timeline = new Timeline(512);
	private final FightCollector fight = new FightCollector(timeline);

	private List<Span> spans()
	{
		return timeline.spansBetween(0, 100);
	}

	@Test
	public void inCombatOpensOneLabelledSpan()
	{
		for (int t = 10; t <= 14; t++)
		{
			fight.onTick(t, "Abyssal demon");
		}
		fight.onTick(15, null);
		assertEquals(1, spans().size());
		Span s = spans().get(0);
		assertEquals("fight", s.kind);
		assertEquals("Abyssal demon", s.label);
		assertEquals(10, s.startTick);
		assertEquals(14, s.endTick);
	}

	@Test
	public void newTargetClosesAndOpens()
	{
		fight.onTick(10, "Goblin");
		fight.onTick(11, "Goblin");
		fight.onTick(12, "Cow");
		assertEquals(2, spans().size());
		assertEquals(11, spans().get(0).endTick);
		assertEquals(12, spans().get(1).startTick);
		assertEquals("Cow", spans().get(1).label);
		assertEquals(-1, spans().get(1).endTick);
	}

	@Test
	public void nullWithoutFightDoesNothing()
	{
		fight.onTick(3, null);
		assertEquals(0, spans().size());
	}
}
