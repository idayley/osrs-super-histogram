package com.superhistogram.model.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.combat.Beat.Verdict;
import java.util.function.IntPredicate;
import org.junit.Test;

public class BeatTest
{
	private final Beat beat = new Beat();

	/** Attacks at 101, 105, ... and one onTick per tick in [from, to]. */
	private void run(int from, int to, IntPredicate prayerOn)
	{
		for (int t = from; t <= to; t++)
		{
			if (t >= 101 && (t - 101) % 4 == 0)
			{
				beat.onAttack(t);
			}
			beat.onTick(t, prayerOn.test(t));
		}
	}

	private void pressed()
	{
		assertTrue(beat.press(100, 4, true));
	}

	@Test
	public void beatNeverStartsWithoutHotkey()
	{
		run(100, 150, t -> t % 3 == 0);
		assertFalse(beat.active());
		assertEquals(-1, beat.nextBeat());
		for (int t = 90; t < 160; t++)
		{
			assertFalse(beat.isBeat(t));
		}
		assertTrue(beat.trail().isEmpty());
	}

	@Test
	public void hotkeyIgnoredOutOfCombat()
	{
		assertFalse(beat.press(100, 4, false));
		assertFalse(beat.active());
	}

	@Test
	public void pressTickIsBeatWithoutVerdict()
	{
		pressed();
		beat.onTick(101, true);
		assertTrue(beat.isBeat(100));
		assertEquals(104, beat.nextBeat());
		assertNull(beat.verdict(100));
	}

	@Test
	public void beatsRepeatAtWeaponSpeed()
	{
		pressed();
		run(100, 112, t -> false);
		assertTrue(beat.isBeat(104));
		assertTrue(beat.isBeat(108));
		assertTrue(beat.isBeat(112));
		assertFalse(beat.isBeat(102));
		assertEquals(4, beat.speed());
	}

	@Test
	public void onTimeWhenPrayerClickedOnBeat()
	{
		pressed();
		run(100, 105, t -> t >= 105);
		assertEquals(Verdict.ON_TIME, beat.verdict(104));
	}

	@Test
	public void lateWhenPrayerAfterBeat()
	{
		pressed();
		run(100, 106, t -> t >= 106);
		assertEquals(Verdict.LATE, beat.verdict(104));
	}

	@Test
	public void earlyWhenPrayerAlreadyOn()
	{
		pressed();
		run(100, 105, t -> t >= 104);
		assertEquals(Verdict.EARLY, beat.verdict(104));
	}

	@Test
	public void streakCounts()
	{
		pressed();
		// beats 104, 108, ..., 124: OT, OT, LATE, OT, OT, OT
		boolean[] ontime = {true, true, false, true, true, true};
		run(100, 125, t ->
		{
			int i = (t - 104) / 4;
			return t >= 104 && (t - 104) % 4 == 1 && ontime[i];
		});
		assertEquals(3, beat.streak());
		assertEquals(6, beat.trail().size());
		assertEquals(Verdict.LATE, beat.trail().get(2));
	}

	@Test
	public void trailKeepsLastTen()
	{
		pressed();
		run(100, 100 + 4 * 12 + 1, t -> false);
		assertEquals(10, beat.trail().size());
		beat.trail().clear();
		assertEquals(10, beat.trail().size());
	}

	@Test
	public void endsAfter10TicksWithoutAttack()
	{
		pressed();
		for (int t = 100; t <= 109; t++)
		{
			beat.onTick(t, false);
		}
		assertTrue(beat.active());
		beat.onTick(110, false);
		assertFalse(beat.active());
		assertTrue(beat.isBeat(104));
		assertTrue(beat.isBeat(108));
		assertEquals(-1, beat.nextBeat());
	}

	@Test
	public void attacksKeepItAlive()
	{
		pressed();
		beat.onAttack(105);
		for (int t = 100; t <= 114; t++)
		{
			beat.onTick(t, false);
		}
		assertTrue(beat.active());
		beat.onTick(115, false);
		assertFalse(beat.active());
	}

	@Test
	public void weaponSwapAppliesFromLastBeat()
	{
		pressed();
		run(100, 102, t -> false);
		beat.onWeaponSpeed(102, 5);
		assertEquals(105, beat.nextBeat());
		run(103, 110, t -> false);
		assertTrue(beat.isBeat(105));
		assertTrue(beat.isBeat(110));
	}

	@Test
	public void swapToFasterNeverSchedulesInPast()
	{
		assertTrue(beat.press(100, 6, true));
		run(100, 104, t -> false);
		beat.onWeaponSpeed(104, 2);
		assertEquals(105, beat.nextBeat());
	}

	@Test
	public void pressAgainRephases()
	{
		pressed();
		run(100, 101, t -> false);
		assertTrue(beat.press(102, 4, true));
		assertTrue(beat.isBeat(102));
		assertEquals(106, beat.nextBeat());
		assertTrue(beat.isBeat(100));
	}

	@Test
	public void resetClearsBeat()
	{
		pressed();
		run(100, 110, t -> false);
		beat.reset();
		assertFalse(beat.active());
		assertTrue(beat.trail().isEmpty());
		assertEquals(-1, beat.nextBeat());
		assertFalse(beat.isBeat(100));
	}
}
