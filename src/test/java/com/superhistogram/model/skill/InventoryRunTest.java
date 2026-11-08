package com.superhistogram.model.skill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class InventoryRunTest
{
	private static final int SHARK = 383;
	private static final int LOGS = 1511;

	private final Timeline tl = new Timeline(512);
	private final InventoryRun run = new InventoryRun(tl);
	private final Map<Integer, Integer> inv = new HashMap<>();
	private final List<Integer> finishTicks = new ArrayList<>();
	private int now = -1;

	private void start(Object... itemCount)
	{
		for (int i = 0; i < itemCount.length; i += 2)
		{
			inv.put((Integer) itemCount[i], (Integer) itemCount[i + 1]);
		}
		now = 0;
		run.onTick(0, Collections.emptyList(), new HashMap<>(inv));
	}

	private void tick(int t, List<XpDrop> drops, Map<Integer, Integer> invOrNull)
	{
		assertTrue(t > now);
		tl.at(t);
		if (run.onTick(t, drops, invOrNull))
		{
			finishTicks.add(t);
		}
		now = t;
	}

	private void idleTo(int t)
	{
		for (int i = now + 1; i <= t; i++)
		{
			tick(i, Collections.emptyList(), null);
		}
	}

	/** Idle up to t-1, then a tick where item(s) fall by dec and the skill drops XP. */
	private void act(int t, String skill, int dec, int... items)
	{
		idleTo(t - 1);
		for (int it : items)
		{
			inv.put(it, inv.get(it) - dec);
		}
		List<XpDrop> d = skill == null ? Collections.<XpDrop>emptyList()
			: Collections.singletonList(new XpDrop(skill, 210));
		tick(t, d, new HashMap<>(inv));
	}

	private void cook(int t)
	{
		act(t, "Cooking", 1, SHARK);
	}

	private void cookFrom(int first, int count)
	{
		for (int i = 0; i < count; i++)
		{
			cook(first + 4 * i);
		}
	}

	@Test
	public void singleDropDoesNotDetect()
	{
		start(SHARK, 28);
		cook(100);
		idleTo(110);
		assertFalse(run.running());
		assertFalse(run.showing(110));
	}

	@Test
	public void twoDropsWithin12TicksDetect()
	{
		start(SHARK, 28);
		cookFrom(100, 2);
		assertTrue(run.running());
		assertEquals("Cooking", run.skill());
		assertEquals(28, run.total());
		assertEquals(2, run.done());
		assertEquals(96, run.runs().get(0).startTick);
		assertEquals(-1, run.runs().get(0).endTick);
	}

	@Test
	public void dropsTooFarApartDoNotDetect()
	{
		start(SHARK, 28);
		cook(100);
		cook(113);
		assertFalse(run.running());
	}

	@Test
	public void xpWithoutInventoryDropIsNoAction()
	{
		start(SHARK, 28);
		idleTo(99);
		tick(100, Collections.singletonList(new XpDrop("Cooking", 210)), null);
		idleTo(103);
		tick(104, Collections.singletonList(new XpDrop("Cooking", 210)), null);
		assertFalse(run.running());
		assertEquals(0, run.done());
	}

	@Test
	public void xpOneTickAfterDecreaseStillMatches()
	{
		start(SHARK, 28);
		idleTo(99);
		for (int t : new int[] {100, 104})
		{
			idleTo(t - 1);
			inv.put(SHARK, inv.get(SHARK) - 1);
			tick(t, Collections.emptyList(), new HashMap<>(inv));
			tick(t + 1, Collections.singletonList(new XpDrop("Cooking", 210)), null);
		}
		idleTo(106);
		assertTrue(run.running());
		assertEquals(2, run.done());
		assertEquals(96, run.runs().get(0).startTick);
		assertFalse(tl.peek(100).failedAction);
		assertFalse(tl.peek(104).failedAction);
	}

	@Test
	public void burntCountsButFlagged()
	{
		start(SHARK, 28);
		cook(100);
		cook(104);
		act(108, null, 1, SHARK);
		idleTo(111);
		assertTrue(tl.peek(108).failedAction);
		assertEquals(3, run.done());
		assertEquals(25, run.remaining());
		assertTrue(run.running());
	}

	@Test
	public void etaExactForCooking()
	{
		start(SHARK, 28);
		cookFrom(100, 14);
		assertEquals(4, run.ticksPerAction());
		assertEquals(14, run.remaining());
		assertEquals(208, run.finishTick());
	}

	@Test
	public void etaFollowsRateChange()
	{
		start(SHARK, 28);
		for (int t : new int[] {100, 104, 108, 112, 114, 116})
		{
			cook(t);
		}
		assertEquals(2, run.ticksPerAction());
		assertEquals(160, run.finishTick());
	}

	@Test
	public void pauseDoesNotInflateEta()
	{
		start(SHARK, 28);
		for (int t : new int[] {100, 104, 108, 128})
		{
			cook(t);
		}
		assertEquals(4, run.ticksPerAction());
		assertEquals(224, run.finishTick());
	}

	@Test
	public void finishReturnsTrueOnce()
	{
		start(SHARK, 28);
		cookFrom(100, 28);
		idleTo(230);
		assertEquals(Collections.singletonList(208), finishTicks);
		assertTrue(run.finished());
		assertFalse(run.running());
		assertEquals(208, run.runs().get(0).endTick);
		assertEquals(208, run.finishTick());
		assertEquals(0, run.remaining());
	}

	@Test
	public void wastedTicksBeforeBank()
	{
		start(SHARK, 28);
		cookFrom(100, 28);
		idleTo(210);
		run.onBankClick(211);
		assertEquals(3, run.wasted());
		assertEquals(211, run.bankTick());
		assertTrue(tl.peek(211).bankClick);
		assertEquals(3, tl.peek(211).wasted);

		InventoryRun r2 = new InventoryRun(new Timeline(512));
		Map<Integer, Integer> i2 = new HashMap<>();
		i2.put(SHARK, 28);
		r2.onTick(0, Collections.emptyList(), new HashMap<>(i2));
		for (int t = 1; t <= 208; t++)
		{
			List<XpDrop> d = Collections.emptyList();
			Map<Integer, Integer> in = null;
			if (t >= 100 && (t - 100) % 4 == 0)
			{
				i2.put(SHARK, i2.get(SHARK) - 1);
				in = new HashMap<>(i2);
				d = Collections.singletonList(new XpDrop("Cooking", 210));
			}
			r2.onTick(t, d, in);
		}
		r2.onBankClick(208);
		assertEquals(0, r2.wasted());
	}

	@Test
	public void bankMidInventoryEndsRunWithoutBlink()
	{
		start(SHARK, 28);
		cookFrom(100, 10);
		idleTo(139);
		run.onBankClick(140);
		tick(140, Collections.emptyList(), null);
		inv.put(SHARK, 0);
		idleTo(142);
		tick(143, Collections.emptyList(), new HashMap<>(inv));
		idleTo(160);
		assertTrue(finishTicks.isEmpty());
		assertFalse(run.running());
		assertFalse(run.finished());
		assertFalse(run.showing(140));
		assertFalse(tl.peek(140).bankClick);
		assertEquals(136, run.runs().get(0).endTick);
		assertEquals(10, run.done());
	}

	@Test
	public void withdrawEndsRun()
	{
		start(SHARK, 28);
		cookFrom(100, 5);
		idleTo(121);
		inv.put(SHARK, 28);
		tick(122, Collections.emptyList(), new HashMap<>(inv));
		assertFalse(run.running());
		assertEquals(116, run.runs().get(0).endTick);
		cook(130);
		cook(134);
		assertTrue(run.running());
		assertEquals(28, run.total());
		assertEquals(2, run.done());
		assertEquals(2, run.runs().size());
	}

	@Test
	public void secondProductionSkillStartsNewRun()
	{
		start(SHARK, 28, LOGS, 28);
		cookFrom(100, 10);
		act(150, "Fletching", 1, LOGS);
		act(153, "Fletching", 1, LOGS);
		assertEquals("Fletching", run.skill());
		assertEquals(28, run.total());
		assertEquals(2, run.done());
		assertEquals(136, run.runs().get(0).endTick);
		assertEquals(2, run.runs().size());
		assertEquals("Cooking", run.runs().get(0).kind);
		assertEquals("Fletching", run.runs().get(1).kind);
	}

	@Test
	public void limitingReagentIsInput()
	{
		start(1777, 14, 62, 20);
		act(100, "Fletching", 1, 1777, 62);
		act(103, "Fletching", 1, 1777, 62);
		assertTrue(run.running());
		assertEquals(14, run.total());
	}

	@Test
	public void multiUnitInput()
	{
		start(2363, 25);
		act(100, "Smithing", 5, 2363);
		act(104, "Smithing", 5, 2363);
		assertEquals(5, run.total());
		assertEquals(3, run.remaining());
	}

	@Test
	public void nonProductionSkillIgnored()
	{
		start(SHARK, 28);
		act(100, "Magic", 1, SHARK);
		act(104, "Magic", 1, SHARK);
		assertFalse(run.running());
		assertEquals(0, run.done());
	}

	@Test
	public void historyBounded()
	{
		start(SHARK, 4);
		int t = 100;
		for (int n = 0; n < 10; n++)
		{
			for (int i = 0; i < 4; i++)
			{
				cook(t);
				t += 4;
			}
			inv.put(SHARK, 4);
			tick(t, Collections.emptyList(), new HashMap<>(inv));
			t += 4;
		}
		assertEquals(10, finishTicks.size());
		assertEquals(8, run.runs().size());
	}

	@Test
	public void staleBankClickIgnored()
	{
		start(SHARK, 28);
		cookFrom(100, 28);
		idleTo(300);
		run.onBankClick(300);
		assertEquals(-1, run.bankTick());
		assertEquals(0, run.wasted());
		assertFalse(tl.peek(300).bankClick);
		assertEquals(0, tl.peek(300).wasted);
	}

	@Test
	public void burntFinalItemFinishesNextTick()
	{
		start(SHARK, 3);
		cook(100);
		cook(104);
		act(108, null, 1, SHARK);
		assertTrue(finishTicks.isEmpty());
		assertTrue(run.running());
		idleTo(109);
		assertEquals(Collections.singletonList(109), finishTicks);
		assertTrue(run.finished());
		assertEquals(108, run.finishTick());
		assertTrue(tl.peek(108).failedAction);
		assertEquals(108, run.runs().get(0).endTick);
	}

	@Test
	public void repeatedBankClicksKeepFirst()
	{
		start(SHARK, 28);
		cookFrom(100, 28);
		idleTo(215);
		run.onBankClick(211);
		run.onBankClick(215);
		assertEquals(211, run.bankTick());
		assertEquals(3, run.wasted());
		assertFalse(tl.peek(215).bankClick);
	}

	@Test
	public void resetClearsAll()
	{
		start(SHARK, 28);
		cookFrom(100, 3);
		run.reset();
		assertFalse(run.running());
		assertFalse(run.finished());
		assertFalse(run.showing(112));
		assertEquals(0, run.done());
		assertEquals(0, run.total());
		assertEquals(-1, run.finishTick());
		assertEquals(-1, run.bankTick());
		assertEquals(0, run.ticksPerAction());
		assertNotNull(run.runs());
		assertTrue(run.runs().isEmpty());
	}

	@Test
	public void nextInventoryOfTheSameThingShowsFromItsFirstCook()
	{
		start(SHARK, 3);
		cookFrom(10, 3);
		assertTrue(run.finished());
		run.onBankClick(25);
		idleTo(27);
		inv.put(SHARK, 28);
		tick(28, Collections.emptyList(), new HashMap<>(inv));
		cook(32);
		assertTrue(run.running());
		assertEquals(1, run.done());
		assertEquals(28, run.total());
		assertEquals(32 + 27 * 4, run.finishTick());
	}

	@Test
	public void aOneOffOfSomethingNewStillWaitsForASecond()
	{
		start(SHARK, 3, LOGS, 5);
		cookFrom(10, 3);
		act(30, "Firemaking", 1, LOGS);
		assertFalse(run.running());
	}
}
