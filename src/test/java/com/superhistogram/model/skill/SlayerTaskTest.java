package com.superhistogram.model.skill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.XpDrop;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class SlayerTaskTest
{
	private static final List<XpDrop> NONE = Collections.emptyList();

	private static List<XpDrop> slayer(int n)
	{
		return Collections.singletonList(new XpDrop("Slayer", n));
	}

	@Test
	public void projectsTheLevelAtTaskEndFromXpPerKill()
	{
		SlayerTask t = new SlayerTask();
		t.onTick(0, 14, NONE);
		assertFalse("no kill yet: not known to be on task", t.showing(0));
		t.onTick(9, 13, slayer(111));
		t.onTick(18, 12, slayer(111));
		assertTrue(t.showing(20));
		assertEquals(111.0, t.xpPerKill(), 0.001);
		assertEquals(3_500_000 + 12 * 111, t.xpAtEnd(3_500_000));
		// 85 starts at 3,258,594 and 86 at 3,596,792: 3,501,332 is 71% of the way.
		assertEquals(71, SlayerTask.percentInto(t.xpAtEnd(3_500_000)));
		assertFalse(t.showing(18 + SlayerTask.LINGER + 1));
		t.onTick(30, 0, slayer(111));
		assertFalse("task done", t.showing(31));
	}
}
