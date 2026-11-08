package com.superhistogram.model.skill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class GatherRunTest
{
	private static final int YEW = 1515;
	private static final int A = 101;
	private static final int B = 102;

	private final Timeline tl = new Timeline(512);
	private final GatherRun run = new GatherRun(tl);
	private final Map<Integer, Integer> inv = new HashMap<>();
	private final List<Integer> fullTicks = new ArrayList<>();
	private int free = 28;
	private int now = -1;

	private void baseline()
	{
		baseline(0);
	}

	private void baseline(int logs)
	{
		inv.clear();
		if (logs > 0)
		{
			inv.put(YEW, logs);
		}
		free = 28 - logs;
		tl.at(99);
		run.onTick(99, false, Collections.<XpDrop>emptyList(), new HashMap<>(inv), free);
		now = 99;
	}

	private void tick(int t, boolean animating, List<XpDrop> drops, Map<Integer, Integer> invOrNull, int freeSlots)
	{
		assertTrue(t > now);
		tl.at(t);
		if (run.onTick(t, animating, drops, invOrNull, freeSlots))
		{
			fullTicks.add(t);
		}
		now = t;
	}

	private void roll(int t)
	{
		tick(t, true, Collections.<XpDrop>emptyList(), null, -1);
	}

	private void walk(int t)
	{
		tick(t, false, Collections.<XpDrop>emptyList(), null, -1);
	}

	private void success(int t)
	{
		success(t, "Woodcutting", 175, YEW);
	}

	private void success(int t, String skill, int xp, int item)
	{
		inv.merge(item, 1, Integer::sum);
		free--;
		tick(t, true, Arrays.asList(new XpDrop(skill, xp)), new HashMap<>(inv), free);
	}

	private void click()
	{
		run.onTargetClick(100, "Woodcutting", A, "Yew tree");
		walk(100);
		walk(101);
	}

	/** Rolls from now+1 through end, with a success on each listed tick. */
	private void gatherTo(int end, int... successTicks)
	{
		for (int t = now + 1; t <= end; t++)
		{
			boolean s = false;
			for (int x : successTicks)
			{
				s |= x == t;
			}
			if (s)
			{
				success(t);
			}
			else
			{
				roll(t);
			}
		}
	}

	@Test
	public void clickThenRollsOpenPill()
	{
		baseline();
		click();
		gatherTo(110);
		List<GatherRun.Pill> pills = run.pills();
		assertEquals(1, pills.size());
		assertEquals(100, pills.get(0).startTick);
		assertEquals(-1, pills.get(0).endTick);
		for (int t = 102; t <= 110; t++)
		{
			assertTrue(tl.peek(t).roll);
		}
		assertFalse(tl.peek(101).roll);
		assertTrue(run.showing(110));
		assertEquals("Woodcutting", run.skill());
		assertEquals("Yew tree", run.targetName());
	}

	@Test
	public void clickWithoutAnimationIsDropped()
	{
		baseline();
		click();
		for (int t = 102; t <= 120; t++)
		{
			walk(t);
		}
		assertTrue(run.pills().isEmpty());
		assertFalse(run.showing(120));
	}

	@Test
	public void itemWithoutXpIsNoSuccess()
	{
		baseline();
		click();
		gatherTo(104);
		inv.put(YEW, 1);
		free = 27;
		tick(105, true, Collections.<XpDrop>emptyList(), new HashMap<>(inv), free);
		roll(106);
		roll(107);
		assertEquals(0, run.successes());
		assertEquals(0, tl.peek(105).gathered);
	}

	@Test
	public void xpWithoutItemIsSuccess()
	{
		baseline();
		click();
		gatherTo(105);
		tick(106, true, Arrays.asList(new XpDrop("Woodcutting", 175)), null, -1);
		roll(107);
		assertEquals(1, run.successes());
		assertEquals(0, tl.peek(106).gathered);
		assertEquals(0, run.count());
	}

	@Test
	public void itemOneTickAfterXpMatches()
	{
		baseline();
		click();
		gatherTo(105);
		tick(106, true, Arrays.asList(new XpDrop("Woodcutting", 175)), null, -1);
		inv.put(YEW, 1);
		free = 27;
		tick(107, true, Collections.<XpDrop>emptyList(), new HashMap<>(inv), free);
		assertEquals(1515, tl.peek(106).gathered);
		assertEquals(1, run.count());
	}

	@Test
	public void estimateUsesObservedRate()
	{
		baseline();
		click();
		gatherTo(141, 111, 121, 131, 141);
		assertEquals(4, run.count());
		assertEquals(28, run.capacity());
		assertEquals(0.1, run.rate(), 1e-9);
		assertEquals(240, run.etaTicks());
		assertEquals(600, run.perHour());
	}

	@Test
	public void noEstimateBeforeTwoSuccesses()
	{
		baseline();
		click();
		gatherTo(115, 111);
		assertEquals(0.0, run.rate(), 0);
		assertEquals(-1, run.etaTicks());
		assertEquals(0, run.perHour());
	}

	@Test
	public void rateWindowIsLast100Rolls()
	{
		baseline();
		click();
		gatherTo(201);
		int[] s = new int[10];
		for (int i = 0; i < 10; i++)
		{
			s[i] = 211 + 10 * i;
		}
		gatherTo(301, s);
		assertEquals(0.1, run.rate(), 1e-9);
	}

	@Test
	public void depletionEndsPillAndMarks()
	{
		baseline();
		click();
		gatherTo(129);
		run.onTargetGone(130, A);
		roll(130);
		GatherRun.Pill p = run.pills().get(0);
		assertEquals(130, p.endTick);
		assertTrue(p.depleted);
		assertTrue(tl.peek(130).depleted);
		assertEquals(131, run.cueTick());
	}

	@Test
	public void otherTargetGoneIgnored()
	{
		baseline();
		click();
		gatherTo(129);
		run.onTargetGone(130, B);
		roll(130);
		assertEquals(-1, run.pills().get(0).endTick);
		assertEquals(-1, run.cueTick());
	}

	@Test
	public void reclickDelayCounted()
	{
		baseline();
		click();
		gatherTo(129);
		run.onTargetGone(130, A);
		roll(130);
		walk(131);
		run.onTargetClick(132, "Woodcutting", B, "Yew tree");
		walk(132);
		assertEquals(2, tl.peek(132).reclick);
		assertEquals(2, run.pills().size());
		assertEquals(-1, run.cueTick());
	}

	@Test
	public void twoTreesInARowKeepCountAndRate()
	{
		baseline();
		click();
		gatherTo(130, 111, 121);
		run.onTargetGone(131, A);
		success(131);
		walk(132);
		run.onTargetClick(133, "Woodcutting", B, "Yew tree");
		walk(133);
		walk(134);
		walk(135);
		gatherTo(165, 145, 155, 165);
		assertEquals(6, run.count());
		assertEquals(0.1, run.rate(), 1e-9);
		List<GatherRun.Pill> pills = run.pills();
		assertEquals(2, pills.size());
		assertEquals(30, pills.get(1).rolls);
		assertTrue(pills.get(0).depleted);
	}

	@Test
	public void longPauseIsNotReclick()
	{
		baseline();
		click();
		gatherTo(129);
		run.onTargetGone(130, A);
		roll(130);
		for (int t = 131; t <= 141; t++)
		{
			walk(t);
		}
		run.onTargetClick(142, "Woodcutting", B, "Yew tree");
		walk(142);
		assertEquals(0, tl.peek(142).reclick);
	}

	@Test
	public void sameTargetClickContinuesPill()
	{
		baseline();
		click();
		gatherTo(114);
		run.onTargetClick(115, "Woodcutting", A, "Yew tree");
		roll(115);
		assertEquals(1, run.pills().size());
		assertEquals(-1, run.pills().get(0).endTick);
	}

	@Test
	public void animationStopEndsPill()
	{
		baseline();
		click();
		gatherTo(120);
		walk(121);
		walk(122);
		walk(123);
		GatherRun.Pill p = run.pills().get(0);
		assertEquals(121, p.endTick);
		assertFalse(p.depleted);
	}

	@Test
	public void fullInventoryReturnsTrueOnce()
	{
		baseline(26);
		click();
		gatherTo(105);
		success(106);
		assertTrue(fullTicks.isEmpty());
		success(107);
		assertEquals(Arrays.asList(107), fullTicks);
		assertTrue(run.full());
		assertEquals(107, run.pills().get(0).endTick);
		assertEquals(-1, run.cueTick());
		assertFalse(run.active());
	}

	@Test
	public void fullAndDepletedSameTick()
	{
		baseline(27);
		click();
		gatherTo(105);
		run.onTargetGone(106, A);
		success(106);
		assertEquals(Arrays.asList(106), fullTicks);
		assertTrue(run.pills().get(0).depleted);
		assertTrue(run.full());
		assertEquals(-1, run.cueTick());
	}

	@Test
	public void powerdropMidGather()
	{
		baseline();
		click();
		gatherTo(130, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112);
		int before = run.successes();
		inv.clear();
		inv.put(YEW, 5);
		free = 23;
		tick(131, true, Collections.<XpDrop>emptyList(), new HashMap<>(inv), free);
		assertEquals(before, run.successes());
		assertEquals(-1, run.pills().get(0).endTick);
		assertEquals(5, run.count());
		assertEquals(28, run.capacity());
		assertFalse(run.full());
	}

	@Test
	public void dropAfterFullClearsFull()
	{
		baseline(27);
		click();
		gatherTo(105);
		success(106);
		assertTrue(run.full());
		inv.clear();
		free = 28;
		tick(107, false, Collections.<XpDrop>emptyList(), new HashMap<>(inv), free);
		assertFalse(run.full());
		walk(108);
		success(109);
		assertEquals(Arrays.asList(106), fullTicks);
		inv.put(YEW, 27);
		free = 1;
		success(110);
		assertEquals(Arrays.asList(106, 110), fullTicks);
		assertTrue(run.full());
	}

	@Test
	public void bankMidGatherEndsPill()
	{
		baseline();
		click();
		gatherTo(139, 111, 121);
		run.onBankClick(140);
		walk(140);
		GatherRun.Pill p = run.pills().get(0);
		assertEquals(140, p.endTick);
		assertFalse(p.depleted);
		assertFalse(tl.peek(140).gatherBank);
		inv.clear();
		free = 28;
		tick(141, false, Collections.<XpDrop>emptyList(), new HashMap<>(inv), free);
		assertEquals(0, run.count());
		run.onTargetClick(143, "Woodcutting", B, "Yew tree");
		walk(142);
		walk(143);
		assertEquals(0, tl.peek(143).reclick);
	}

	@Test
	public void bankAfterFullMarksBank()
	{
		baseline(27);
		click();
		gatherTo(105);
		success(106);
		walk(107);
		run.onBankClick(108);
		walk(108);
		assertTrue(tl.peek(108).gatherBank);
	}

	@Test
	public void fishingSpotMoves()
	{
		baseline();
		run.onTargetClick(100, "Fishing", -5, "Fishing spot");
		walk(100);
		walk(101);
		for (int t = 102; t <= 109; t++)
		{
			if (t == 106)
			{
				success(t, "Fishing", 50, 335);
			}
			else
			{
				roll(t);
			}
		}
		run.onTargetGone(110, -5);
		roll(110);
		assertTrue(run.pills().get(0).depleted);
		walk(111);
		walk(112);
		run.onTargetClick(113, "Fishing", -7, "Fishing spot");
		walk(113);
		assertEquals(3, tl.peek(113).reclick);
		assertEquals(1, run.count());
	}

	@Test
	public void unknownOptionStillGathers()
	{
		baseline();
		inv.put(440, 1);
		free = 27;
		tick(150, true, Arrays.asList(new XpDrop("Mining", 35)), new HashMap<>(inv), free);
		roll(151);
		roll(152);
		GatherRun.Pill p = run.pills().get(0);
		assertEquals(0, p.key);
		assertEquals(149, p.startTick);
		assertTrue(run.showing(152));
		assertEquals("Mining", run.skill());
		assertEquals(440, tl.peek(150).gathered);
	}

	@Test
	public void firemakingSuccessEndsPill()
	{
		baseline(27);
		free = 1;
		run.onTargetClick(100, "Firemaking", 0, "Yew logs");
		roll(100);
		roll(101);
		roll(102);
		inv.put(YEW, 26);
		free = 2;
		tick(103, true, Arrays.asList(new XpDrop("Firemaking", 202)), new HashMap<>(inv), free);
		GatherRun.Pill p = run.pills().get(0);
		assertEquals(103, p.endTick);
		assertFalse(p.depleted);
		assertEquals(104, run.cueTick());
		assertEquals(26, run.count());
		assertEquals(-1, run.capacity());
		walk(104);
		run.onTargetClick(105, "Firemaking", 0, "Yew logs");
		walk(105);
		assertEquals(2, tl.peek(105).reclick);
	}

	@Test
	public void firemakingOutOfLogsReturnsTrue()
	{
		baseline(1);
		run.onTargetClick(100, "Firemaking", 0, "Yew logs");
		roll(100);
		roll(101);
		roll(102);
		inv.clear();
		free = 28;
		tick(103, true, Arrays.asList(new XpDrop("Firemaking", 202)), new HashMap<>(inv), free);
		assertEquals(Arrays.asList(103), fullTicks);
		assertEquals(0, run.count());
		roll(104);
		assertEquals(1, fullTicks.size());
	}

	@Test
	public void skillChangeResetsRate()
	{
		baseline();
		click();
		gatherTo(131, 111, 121, 131);
		assertTrue(run.rate() > 0);
		run.onTargetClick(133, "Mining", 301, "Iron rocks");
		assertEquals(0.0, run.rate(), 0);
		assertEquals(0, run.successes());
	}

	@Test
	public void historyBounded()
	{
		baseline();
		int t = 100;
		for (int i = 0; i < 20; i++)
		{
			run.onTargetClick(t, "Woodcutting", 200 + i, "Tree");
			for (int k = 0; k < 4; k++)
			{
				roll(t + k);
			}
			t += 5;
		}
		assertEquals(16, run.pills().size());
	}

	@Test
	public void lingersAfterPillEnds()
	{
		baseline();
		click();
		gatherTo(149);
		run.onTargetGone(150, A);
		roll(150);
		assertTrue(run.showing(170));
		assertFalse(run.showing(171));
	}

	@Test
	public void resetClearsAll()
	{
		baseline();
		click();
		gatherTo(131, 111, 121, 131);
		run.reset();
		assertTrue(run.pills().isEmpty());
		assertFalse(run.active());
		assertFalse(run.showing(131));
		assertEquals(0, run.count());
		assertEquals(-1, run.capacity());
		assertEquals(0.0, run.rate(), 0);
		assertEquals(-1, run.cueTick());
		assertEquals(0, run.successes());
		assertFalse(run.full());
		assertEquals(null, run.skill());
		assertNotNull(run.pills());
	}

	@Test
	public void staleDespawnDoesNotEndLaterPill()
	{
		baseline();
		click();
		gatherTo(120);
		run.onTargetGone(121, A);
		// a tick with no record (negative) is skipped but must still drop the despawn
		run.onTick(-1, false, Collections.<XpDrop>emptyList(), null, -1);
		roll(121);
		assertEquals(-1, run.pills().get(0).endTick);
		assertFalse(run.pills().get(0).depleted);
		assertTrue(run.active());
	}

	@Test
	public void walkBetweenTreesKeepsShowing()
	{
		baseline();
		click();
		gatherTo(129);
		run.onTargetGone(130, A);
		roll(130);
		walk(131);
		assertTrue(run.showing(131));
		run.onTargetClick(132, "Woodcutting", B, "Yew tree");
		walk(132);
		assertTrue(run.showing(132));
		walk(133);
		assertTrue(run.showing(133));
	}

	@Test
	public void firstChopOnTheNextTreeKeepsShowing()
	{
		// Replay: the bar dropped to General for one tick on the new tree's first chop.
		baseline();
		click();
		gatherTo(129);
		run.onTargetGone(130, A);
		roll(130);
		walk(131);
		run.onTargetClick(133, "Woodcutting", B, "Yew tree");
		walk(133);
		roll(134);
		assertTrue(run.showing(134));
		roll(135);
		assertTrue(run.showing(135));
	}

	@Test
	public void sailingXpCountsOnlyWithSalvageComingIn()
	{
		int salvage = net.runelite.api.gameval.ItemID.SAILING_LARGE_SHIPWRECK_SALVAGE;
		baseline();
		List<XpDrop> sail = Collections.singletonList(new XpDrop("Sailing", 180));
		// Boat XP with nothing coming in: not salvaging.
		tl.at(100);
		run.onTick(100, true, sail, new HashMap<>(inv), free);
		assertEquals(0, run.successes());
		for (int t = 104, k = 1; t <= 108; t += 4, k++)
		{
			inv.put(salvage, k);
			tl.at(t);
			run.onTick(t, true, sail, new HashMap<>(inv), 28 - k);
		}
		assertEquals(2, run.successes());
		assertEquals("Sailing", run.skill());
		assertEquals(2, run.count());
		// Sorting: salvage leaves with Sailing XP. Not a salvaging success.
		inv.put(salvage, 1);
		tl.at(110);
		run.onTick(110, false, sail, new HashMap<>(inv), 27);
		assertEquals(2, run.successes());
	}
}
