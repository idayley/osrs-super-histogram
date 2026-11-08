package com.superhistogram.collect;

import static org.junit.Assert.assertEquals;

import com.superhistogram.model.Span;
import com.superhistogram.model.Timeline;
import java.util.List;
import org.junit.Test;

public class PrayerCollectorTest
{
	private final Timeline timeline = new Timeline(512);
	private final PrayerCollector prayers = new PrayerCollector(timeline);

	private List<Span> spans()
	{
		return timeline.spansBetween(0, 100);
	}

	@Test
	public void spanStartsOnTheTickTheGameReportsIt()
	{
		prayers.onTick(10, null);
		prayers.onTick(11, "melee");
		assertEquals(1, spans().size());
		assertEquals("pray-melee", spans().get(0).kind);
		assertEquals(11, spans().get(0).startTick);
		assertEquals(-1, spans().get(0).endTick);
	}

	@Test
	public void offClosesSpan()
	{
		prayers.onTick(11, "melee");
		prayers.onTick(12, "melee");
		prayers.onTick(13, null);
		assertEquals(1, spans().size());
		assertEquals(12, spans().get(0).endTick);
	}

	@Test
	public void switchSplitsSpan()
	{
		prayers.onTick(11, "melee");
		prayers.onTick(12, "melee");
		prayers.onTick(13, "magic");
		assertEquals(2, spans().size());
		assertEquals(11, spans().get(0).startTick);
		assertEquals(12, spans().get(0).endTick);
		assertEquals("pray-magic", spans().get(1).kind);
		assertEquals(13, spans().get(1).startTick);
		assertEquals(-1, spans().get(1).endTick);
	}

	@Test
	public void oneTickFlickIsOneTickSpan()
	{
		prayers.onTick(11, "ranged");
		prayers.onTick(12, null);
		assertEquals(11, spans().get(0).startTick);
		assertEquals(11, spans().get(0).endTick);
	}

	@Test
	public void countsPointsUsedWhileOn()
	{
		prayers.onTick(10, null, 50);
		prayers.onTick(11, "melee", 50);
		prayers.onTick(12, "melee", 49);
		prayers.onTick(13, "melee", 47);
		assertEquals("3 pts", spans().get(0).label);
		// A restore mid-prayer doesn't count against it.
		prayers.onTick(14, "melee", 60);
		prayers.onTick(15, "melee", 59);
		assertEquals("4 pts", spans().get(0).label);
	}

	@Test
	public void nextPrayerStartsItsOwnCount()
	{
		prayers.onTick(10, "melee", 50);
		prayers.onTick(11, "melee", 48);
		prayers.onTick(12, "magic", 47);
		assertEquals("2 pts", spans().get(0).label);
		assertEquals("1 pt", spans().get(1).label);
	}
}
