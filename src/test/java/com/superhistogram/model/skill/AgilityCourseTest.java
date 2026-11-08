package com.superhistogram.model.skill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class AgilityCourseTest
{
	private static final int[] IDS = {14927, 14928, 14932, 14929, 14930, 14931};
	private static final int[] DUR = {7, 9, 6, 8, 11, 7};
	private static final int[] XP = {30, 40, 25, 35, 60, 30};
	private static final List<XpDrop> NONE = Collections.emptyList();

	private final Timeline tl = new Timeline(4096);
	private final AgilityCourse ac = new AgilityCourse(tl);
	private int now = -1;
	private int ends;
	private final int[] clicks = new int[6];

	@Before
	public void setUp()
	{
		ac.onCourse("SEERS");
	}

	private void tickTo(int t)
	{
		while (now < t)
		{
			now++;
			if (ac.onTick(now, NONE, false, false))
			{
				ends++;
			}
		}
	}

	private void click(int t, int id)
	{
		tickTo(t - 1);
		ac.onObstacleClick(t, id);
		tickTo(t);
	}

	private boolean land(int t, int k)
	{
		tickTo(t - 1);
		now = t;
		boolean r = ac.onTick(t, Collections.singletonList(new XpDrop("Agility", XP[k])), false, k == 5);
		if (r)
		{
			ends++;
		}
		return r;
	}

	/** Clicks obstacle k at c and lands it; returns the landing tick. */
	private int ob(int k, int c)
	{
		click(c, IDS[k]);
		clicks[k] = c;
		int l = c + DUR[k];
		land(l, k);
		return l;
	}

	private int lap(int start, int... gaps)
	{
		int c = start;
		int l = 0;
		for (int k = 0; k < 6; k++)
		{
			l = ob(k, c);
			c = l + (k < 5 && k < gaps.length ? gaps[k] : 0);
		}
		return l;
	}

	private int lost(int t)
	{
		TickRecord r = tl.peek(t);
		return r == null ? 0 : r.agilityLost;
	}

	private AgilityCourse.Obstacle last()
	{
		List<AgilityCourse.Obstacle> o = ac.obstacles();
		return o.get(o.size() - 1);
	}

	/** Two laps (53, 52); returns the end tick of lap 2. */
	private int twoLaps()
	{
		int e1 = lap(100, 1, 0, 1, 3, 0);
		return lap(e1 + 3, 0, 1, 0, 2, 1);
	}

	@Test
	public void courseTable()
	{
		assertEquals("SEERS", AgilityCourses.byRegion(10806).id);
		assertEquals("Seers'", AgilityCourses.byRegion(10806).name);
		assertEquals("Gnome", AgilityCourses.byRegion(9781).name);
		assertNull(AgilityCourses.byRegion(12850));
		assertTrue(AgilityCourses.isLapEnd("SEERS", 2704, 3464, 0));
		assertFalse(AgilityCourses.isLapEnd("SEERS", 2704, 3464, 2));
		assertFalse(AgilityCourses.isLapEnd("SEERS", 2705, 3464, 0));
		assertTrue(AgilityCourses.isLapEnd("GNOME", 2487, 3437, 0));
		assertEquals(12, AgilityCourses.all().size());
		assertNotNull(AgilityCourses.byId("FALADOR"));
		for (AgilityCourses.Course c : AgilityCourses.all())
		{
			for (int[] e : c.ends)
			{
				assertEquals(c.id, c.region, ((e[0] >> 6) << 8) | (e[1] >> 6));
			}
		}
	}

	@Test
	public void obstaclePillFromClickToLanding()
	{
		ob(0, 100);
		assertEquals(1, ac.obstacles().size());
		AgilityCourse.Obstacle o = last();
		assertEquals(14927, o.objectId);
		assertEquals(100, o.startTick);
		assertEquals(107, o.endTick);
		assertEquals(1, o.number);
		assertFalse(o.failed);
		assertEquals(108, ac.readyTick());
	}

	@Test
	public void pendingUntilXp()
	{
		click(100, IDS[0]);
		tickTo(104);
		assertEquals(-1, last().endTick);
		assertTrue(ac.pending());
		assertEquals(-1, ac.readyTick());
	}

	@Test
	public void clickWithoutCourseIgnored()
	{
		AgilityCourse fresh = new AgilityCourse(new Timeline(64));
		fresh.onObstacleClick(5, IDS[0]);
		assertTrue(fresh.obstacles().isEmpty());
		assertFalse(fresh.pending());
	}

	@Test
	public void spamClickKeepsFirst()
	{
		click(100, IDS[0]);
		click(101, IDS[0]);
		assertEquals(1, ac.obstacles().size());
		assertEquals(100, last().startTick);
	}

	@Test
	public void newClickReplacesPending()
	{
		click(100, IDS[0]);
		click(102, IDS[1]);
		assertEquals(1, ac.obstacles().size());
		assertEquals(14928, last().objectId);
		assertEquals(102, last().startTick);
	}

	@Test
	public void staleClickDropped()
	{
		click(100, IDS[0]);
		tickTo(131);
		assertTrue(ac.obstacles().isEmpty());
		assertFalse(ac.pending());
	}

	@Test
	public void ticksLostSummed()
	{
		lap(100, 1, 0, 3, 0, 2);
		assertEquals(3, lost(clicks[3]));
		assertEquals(1, lost(clicks[1]));
		assertEquals(6, ac.lapLost());
	}

	@Test
	public void lostOverTenIsPause()
	{
		lap(100, 12, 0, 0, 0, 0);
		assertEquals(0, ac.lapLost());
		assertEquals(0, lost(clicks[1]));
	}

	@Test
	public void gapAfterLapEndNotCounted()
	{
		int e = lap(100);
		int s = e + 3;
		click(s, IDS[0]);
		assertEquals(0, lost(s));
		assertEquals(0, ac.lapLost());
	}

	@Test
	public void lapTimeAndBest()
	{
		lap(100, 1, 0, 1, 3, 0);
		assertEquals(1, ends);
		assertEquals(53, ac.lastLapTicks());
		assertEquals(-1, ac.bestTicks());
		assertFalse(ac.lastLapWasBest());
		assertEquals(-1, ac.lapStartTick());
		int s = now + 3;
		click(s, IDS[0]);
		assertEquals(53, ac.bestTicks());
		assertTrue(ac.takeBestChanged());
		assertFalse(ac.takeBestChanged());
		int c = s;
		int[] gaps = {0, 1, 0, 2, 1};
		for (int k = 0; k < 6; k++)
		{
			if (k > 0)
			{
				click(c, IDS[k]);
			}
			int l = c + DUR[k];
			land(l, k);
			c = l + (k < 5 ? gaps[k] : 0);
		}
		assertEquals(52, ac.lastLapTicks());
		assertEquals(53, ac.previousLapTicks());
		assertEquals(52, ac.bestTicks());
		assertTrue(ac.lastLapWasBest());
		assertTrue(ac.takeBestChanged());
	}

	@Test
	public void lapEndReturnsTrueOnce()
	{
		lap(100);
		assertEquals(1, ends);
	}

	@Test
	public void firstLapPartialNotBest()
	{
		int c = 100;
		int l = 0;
		for (int k = 2; k < 6; k++)
		{
			l = ob(k, c);
			c = l + 5;
		}
		assertEquals(4, ac.obstacles().size());
		int e2 = lap(l + 3);
		assertEquals(e2 - (l + 3), ac.bestTicks());
		int s3 = e2 + 3;
		click(s3, IDS[0]);
		assertEquals(1, last().number);
		int l3 = ob(1, s3 + DUR[0]);
		assertEquals(2, last().number);
		assertEquals(0, ac.slowestAfter());
		assertTrue(l3 > 0);
	}

	@Test
	public void fallingOffNoXp()
	{
		int e2 = twoLaps();
		int s = e2 + 3;
		int l = ob(0, s);
		l = ob(1, l + 1);
		int t = l;
		click(t, IDS[2]);
		int lostBefore = ac.lapLost();
		tickTo(t + 3);
		now = t + 4;
		assertFalse(ac.onTick(t + 4, NONE, true, false));
		AgilityCourse.Obstacle o = last();
		assertTrue(o.failed);
		assertEquals(t + 4, o.endTick);
		assertEquals(3, o.number);
		assertEquals(s, ac.lapStartTick());
		assertEquals(lostBefore, ac.lapLost());
		assertFalse(ac.pending());
		click(t + 10, IDS[2]);
		assertEquals(3, last().number);
		assertEquals(0, lost(t + 10));
	}

	@Test
	public void hitThenXpIsLanding()
	{
		click(100, IDS[0]);
		tickTo(102);
		now = 103;
		ac.onTick(103, NONE, true, false);
		assertTrue(last().failed);
		land(104, 0);
		assertFalse(last().failed);
		assertEquals(104, last().endTick);
	}

	@Test
	public void hitWithoutPendingIgnored()
	{
		now = 50;
		ac.onTick(50, NONE, true, false);
		assertTrue(ac.obstacles().isEmpty());
	}

	@Test
	public void slowestObstacle()
	{
		int e = lap(100, 1, 0, 1, 3, 0);
		e = lap(e + 3, 0, 1, 0, 2, 2);
		lap(e + 3, 0, 0, 1, 3);
		assertEquals(4, ac.slowestAfter());
	}

	@Test
	public void slowestZeroWhenNoGaps()
	{
		int e = lap(100);
		e = lap(e + 3);
		lap(e + 3);
		assertEquals(0, ac.slowestAfter());
	}

	@Test
	public void landingWithoutClickCountsForLapEnd()
	{
		int c = 100;
		int l = 0;
		for (int k = 0; k < 5; k++)
		{
			l = ob(k, c);
			c = l;
		}
		int end = l + DUR[5];
		assertTrue(land(end, 5));
		assertEquals(end - 100, ac.lastLapTicks());
	}

	@Test
	public void changingCoursesResetsLap()
	{
		int e2 = twoLaps();
		int s = e2 + 3;
		ob(0, s);
		ac.onCourse("CANIFIS");
		assertEquals("CANIFIS", ac.course());
		assertEquals(-1, ac.lapStartTick());
		assertFalse(ac.pending());
		assertEquals(-1, ac.bestTicks());
		ac.onCourse("SEERS");
		assertEquals(52, ac.bestTicks());
	}

	@Test
	public void nullCourseKeepsLap()
	{
		int e2 = twoLaps();
		int s = e2 + 3;
		ob(0, s);
		ac.onCourse(null);
		assertEquals(s, ac.lapStartTick());
		assertEquals("SEERS", ac.course());
	}

	@Test
	public void markOfGraceOnLanding()
	{
		click(100, IDS[0]);
		tickTo(106);
		ac.onMark(107);
		land(107, 0);
		assertTrue(tl.peek(107).mark);
		tickTo(108);
		ac.onMark(109);
		tickTo(109);
		assertTrue(tl.peek(109).mark);
	}

	@Test
	public void markWithoutRecentLandingIgnored()
	{
		ob(0, 100);
		tickTo(111);
		ac.onMark(112);
		tickTo(112);
		assertTrue(tl.peek(112) == null || !tl.peek(112).mark);
	}

	@Test
	public void markOffCourseIgnored()
	{
		Timeline t2 = new Timeline(64);
		AgilityCourse fresh = new AgilityCourse(t2);
		fresh.onMark(10);
		fresh.onTick(10, NONE, false, false);
		assertTrue(t2.peek(10) == null || !t2.peek(10).mark);
	}

	@Test
	public void loggingOutMidLap()
	{
		int e2 = twoLaps();
		int s = e2 + 3;
		int l = ob(0, s);
		ac.rebind(new Timeline(512));
		assertEquals(-1, ac.lapStartTick());
		assertTrue(ac.obstacles().isEmpty());
		assertEquals(52, ac.bestTicks());
		assertFalse(ac.showing(l));
		ac.onObstacleClick(l + 100, 14929);
		assertEquals(4, ac.obstacles().get(0).number);
	}

	@Test
	public void firstLapAfterLoginIsProvisional()
	{
		int e2 = twoLaps();
		int s = e2 + 3;
		ob(0, s);
		ac.rebind(new Timeline(4096));
		int e = lap(now + 20);
		assertTrue(e > 0);
		assertEquals(52, ac.bestTicks());
		click(e + 3, IDS[0]);
		assertEquals(48, ac.bestTicks());
	}

	@Test
	public void showingNeedsTwoLandingsAndLingers()
	{
		ob(0, 100);
		assertFalse(ac.showing(107));
		ob(1, 107);
		assertTrue(ac.showing(116));
		assertTrue(ac.showing(166));
		assertFalse(ac.showing(167));
	}

	@Test
	public void loadBestKeepsLower()
	{
		ac.loadBest("SEERS", 80);
		ac.loadBest("SEERS", 90);
		assertEquals(80, ac.bestTicks());
		ac.loadBest("SEERS", 0);
		assertEquals(80, ac.bestTicks());
		assertFalse(ac.takeBestChanged());
	}

	@Test
	public void historyBounded()
	{
		int e = 100 - 3;
		for (int i = 0; i < 20; i++)
		{
			e = lap(e + 3);
		}
		assertEquals(64, ac.obstacles().size());
		assertEquals(16, ac.lapStarts().size());
	}

	@Test
	public void resetClearsAll()
	{
		twoLaps();
		ac.reset();
		assertNull(ac.course());
		ac.onCourse("SEERS");
		assertEquals(-1, ac.bestTicks());
		ac.onObstacleClick(now + 5, 14929);
		assertEquals(1, ac.obstacles().get(0).number);
	}
}
