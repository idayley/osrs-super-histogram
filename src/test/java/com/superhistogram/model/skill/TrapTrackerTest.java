package com.superhistogram.model.skill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import com.superhistogram.model.skill.TrapTracker.State;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

public class TrapTrackerTest
{
	private static final List<XpDrop> NONE = Collections.emptyList();
	private final Timeline tl = new Timeline(512);
	private final TrapTracker tr = new TrapTracker(tl);

	private void tick(int t, boolean xp)
	{
		tl.at(t);
		tr.onTick(t, xp ? Collections.singletonList(new XpDrop("Hunter", 315)) : NONE);
	}

	@Test
	public void trapNamesAreClassified()
	{
		assertEquals(State.SET, TrapTracker.TRAPS.get(ObjectID.HUNTING_BOXTRAP_EMPTY));
		assertEquals(State.SET, TrapTracker.TRAPS.get(ObjectID.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_N));
		assertEquals(State.CAUGHT, TrapTracker.TRAPS.get(ObjectID.HUNTING_BOXTRAP_FULL_CHINCHOMPA_BIG));
		assertEquals(State.DOWN, TrapTracker.TRAPS.get(ObjectID.HUNTING_BOXTRAP_FAILED));
		assertEquals(State.DOWN, TrapTracker.TRAPS.get(ObjectID.HUNTING_OJIBWAY_TRAP_BROKEN));
		assertEquals(State.CAUGHT, TrapTracker.TRAPS.get(ObjectID.HUNTING_OJIBWAY_TRAP_FULL_JUNGLE));
		assertFalse(TrapTracker.isTrap(ObjectID.CHINCHOMPA_JUNGLE_CAVE_EXIT));
	}

	@Test
	public void onlyTrapsLaidOnYourTileAreYours()
	{
		tr.onLay(1, 100);
		tick(1, false);
		tr.onSpawn(2, 100, ObjectID.HUNTING_BOXTRAP_EMPTY);
		// Someone else's trap two tiles over.
		tr.onSpawn(2, 102, ObjectID.HUNTING_BOXTRAP_EMPTY);
		tick(2, false);
		assertEquals(1, tr.traps());
		assertTrue(tr.showing(2));
	}

	@Test
	public void stateFollowsTheObjectAndAResetEndsInACatch()
	{
		tr.onLay(1, 100);
		tick(1, false);
		tr.onSpawn(2, 100, ObjectID.HUNTING_BOXTRAP_EMPTY);
		tick(2, false);
		tr.onDespawn(100);
		tr.onSpawn(10, 100, ObjectID.HUNTING_BOXTRAP_FULL_CHINCHOMPA);
		tick(10, false);
		assertEquals(1, tr.count(State.CAUGHT));
		tr.onTrapClick(12, 100, ObjectID.HUNTING_BOXTRAP_FULL_CHINCHOMPA);
		tick(12, false);
		tr.onDespawn(100);
		tr.onSpawn(14, 100, ObjectID.HUNTING_BOXTRAP_EMPTY);
		tick(14, true);
		assertEquals(1, tr.caught());
		assertEquals(1, tr.count(State.SET));
		assertTrue(tl.peek(14).caught);
	}

	@Test
	public void aDespawnWithNoRespawnIsGone()
	{
		tr.onTrapClick(1, 100, ObjectID.HUNTING_BOXTRAP_FAILED);
		tick(1, false);
		assertEquals(1, tr.count(State.DOWN));
		tr.onDespawn(100);
		tick(3, false);
		assertEquals(0, tr.traps());
		assertFalse(tr.showing(3));
	}

	@Test
	public void trapsGrowWithLevel()
	{
		assertEquals(1, TrapTracker.maxTraps(1));
		assertEquals(2, TrapTracker.maxTraps(20));
		assertEquals(4, TrapTracker.maxTraps(79));
		assertEquals(5, TrapTracker.maxTraps(80));
		assertEquals(5, TrapTracker.maxTraps(99));
	}
}
