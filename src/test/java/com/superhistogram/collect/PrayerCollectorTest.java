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
	public void spanStartsTickBeforeObservation()
	{
		prayers.onTick(10, null);
		prayers.onTick(11, "melee");
		assertEquals(1, spans().size());
		assertEquals("pray-melee", spans().get(0).kind);
		assertEquals(10, spans().get(0).startTick);
		assertEquals(-1, spans().get(0).endTick);
	}

	@Test
	public void offClosesSpan()
	{
		prayers.onTick(11, "melee");
		prayers.onTick(12, "melee");
		prayers.onTick(13, null);
		assertEquals(1, spans().size());
		assertEquals(11, spans().get(0).endTick);
	}

	@Test
	public void switchSplitsSpan()
	{
		prayers.onTick(11, "melee");
		prayers.onTick(12, "melee");
		prayers.onTick(13, "magic");
		assertEquals(2, spans().size());
		assertEquals(10, spans().get(0).startTick);
		assertEquals(11, spans().get(0).endTick);
		assertEquals("pray-magic", spans().get(1).kind);
		assertEquals(12, spans().get(1).startTick);
		assertEquals(-1, spans().get(1).endTick);
	}

	@Test
	public void oneTickFlickIsOneTickSpan()
	{
		prayers.onTick(11, "ranged");
		prayers.onTick(12, null);
		assertEquals(10, spans().get(0).startTick);
		assertEquals(10, spans().get(0).endTick);
	}
}
