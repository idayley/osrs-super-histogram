package com.superhistogram.model.skill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SpellRunTest
{
	private static final int LOGS = 6332, ASTRAL = 9075, FLAX = 1779, BOW = 1777;
	private static final List<XpDrop> NONE = Collections.emptyList();
	private static final List<XpDrop> MAGIC = Collections.singletonList(new XpDrop("Magic", 90));
	private final Timeline tl = new Timeline(512);
	private final SpellRun sr = new SpellRun(tl);
	private final Map<Integer, Integer> inv = new HashMap<>();

	private void tick(int t, boolean xp)
	{
		tl.at(t);
		sr.onTick(t, xp ? MAGIC : NONE, new HashMap<>(inv));
	}

	private void plank(int t)
	{
		sr.onCastClick(t, "Plank Make", LOGS);
		inv.merge(LOGS, -1, Integer::sum);
		inv.merge(ASTRAL, -2, Integer::sum);
		tick(t, true);
	}

	@Test
	public void castsNeedMagicXpAndLearnTheRhythm()
	{
		inv.put(LOGS, 25);
		inv.put(ASTRAL, 500);
		tick(0, false);
		sr.onCastClick(1, "Plank Make", LOGS);
		tick(1, false);
		tick(2, false);
		tick(4, false);
		assertEquals(0, sr.casts());
		plank(5);
		plank(8);
		plank(11);
		assertEquals(3, sr.rhythm());
		assertTrue(sr.showing(12));
		assertEquals("Plank Make", sr.spell());
		assertEquals(22, sr.left());
		assertEquals(66, sr.etaTicks());
		plank(16);
		assertEquals(2, tl.peek(16).spellLost);
		assertFalse(sr.showing(16 + SpellRun.LINGER + 1));
	}

	@Test
	public void aSpellOnNothingUsesWhatLeavesTheInventoryNotTheRunes()
	{
		inv.put(FLAX, 25);
		inv.put(ASTRAL, 100);
		tick(0, false);
		sr.onCastClick(1, "Spin Flax", 0);
		inv.put(FLAX, 20);
		inv.put(BOW, 5);
		inv.put(ASTRAL, 99);
		tick(1, true);
		assertEquals(4, sr.left());
	}

	@Test
	public void anotherSpellStartsANewRun()
	{
		inv.put(LOGS, 25);
		inv.put(ASTRAL, 500);
		tick(0, false);
		plank(1);
		plank(4);
		sr.onCastClick(10, "Lvl-4 Enchant", 1615);
		tick(10, true);
		assertEquals("Lvl-4 Enchant", sr.spell());
		assertEquals(1, sr.casts());
		assertEquals(-1, sr.rhythm());
	}
}
