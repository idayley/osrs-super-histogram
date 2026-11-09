package com.superhistogram.model.skill;

import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks your Hunter traps (box traps, bird snares, deadfalls, imp boxes, monkey traps). A trap is yours when it
 * appears on a tile within a few ticks of your Lay click there, or when you click Check, Reset or Dismantle on
 * it. Its state follows the object on that tile: a change of state is a despawn and a spawn on the same tick; a
 * despawn with nothing back by the next tick means the trap is gone (picked up, collected, collapsed). Each
 * check is a pill on the bar, ended by its Hunter XP. Read by the render thread: public methods are
 * synchronized.
 */
public final class TrapTracker
{
	public static final String SKILL = "Hunter";
	/** Timeline span kind: laying, checking or resetting a trap. */
	public static final String KIND = "hunt";
	/** A Lay click claims the trap that appears on that tile this many ticks later at most. */
	static final int LAY_WINDOW = 6;
	/** A check with no XP is closed after this long. */
	static final int CHECK_TICKS = 4;
	/** The view stays this long after the last trap action or catch with traps down. */
	public static final int LINGER = 100;

	public enum State
	{
		/** Set and waiting, or something is walking in. */
		SET,
		/** Caught something: check it. */
		CAUGHT,
		/** Collapsed or failed: reset it. */
		DOWN
	}

	/** Object id to its trap state; every Hunter trap object the item table knows. */
	static final Map<Integer, State> TRAPS = trapTable();

	private Timeline timeline;
	/** Tile key to the state of your trap there. */
	private final Map<Integer, State> mine = new HashMap<>();
	/** Tiles of your traps that despawned this tick, waiting for the respawn. */
	private final Set<Integer> gone = new HashSet<>();
	/** Tile key of a Lay click to its tick. */
	private final Map<Integer, Integer> lays = new HashMap<>();
	private int checkTick = -1;
	private int lastActivity = -1;
	private int caught;

	public TrapTracker(Timeline timeline)
	{
		this.timeline = timeline;
	}

	private static Map<Integer, State> trapTable()
	{
		Map<Integer, State> out = new HashMap<>();
		GameIds.TRAP_SET.forEach(id -> out.put(id, State.SET));
		GameIds.TRAP_CAUGHT.forEach(id -> out.put(id, State.CAUGHT));
		GameIds.TRAP_DOWN.forEach(id -> out.put(id, State.DOWN));
		return Collections.unmodifiableMap(out);
	}

	/** True for an object id that is a Hunter trap. */
	public static boolean isTrap(int objectId)
	{
		return TRAPS.containsKey(objectId);
	}

	/** You clicked Lay; the game acts at {@code tick}, on the tile you stand on ({@code tile}). */
	public synchronized void onLay(int tick, int tile)
	{
		lays.put(tile, tick);
		pill(tick);
	}

	/** You clicked Check, Reset or Dismantle on the trap at {@code tile}; it is yours. */
	public synchronized void onTrapClick(int tick, int tile, int objectId)
	{
		State s = TRAPS.get(objectId);
		if (s != null && !mine.containsKey(tile))
		{
			mine.put(tile, s);
		}
		pill(tick);
	}

	/** A trap action from {@code tick}: a pill until its XP, its trap appears, or a few ticks pass. */
	private void pill(int tick)
	{
		if (checkTick >= 0)
		{
			timeline.closeSpan(KIND, tick - 1);
		}
		checkTick = tick;
		timeline.openSpan(KIND, tick);
		lastActivity = tick;
	}

	/** An object appeared at {@code tile}: your trap changing state, or the trap you just laid. */
	public synchronized void onSpawn(int tick, int tile, int objectId)
	{
		State s = TRAPS.get(objectId);
		if (s == null)
		{
			return;
		}
		Integer laid = lays.get(tile);
		if (mine.containsKey(tile) || laid != null && tick - laid <= LAY_WINDOW && tick >= laid - 1)
		{
			mine.put(tile, s);
			gone.remove(tile);
			if (lays.remove(tile) != null && checkTick >= 0)
			{
				// The trap you laid is down: that ends its pill.
				timeline.closeSpan(KIND, tick);
				checkTick = -1;
			}
		}
	}

	/** The object at {@code tile} went; your trap is gone unless something spawns there this tick. */
	public synchronized void onDespawn(int tile)
	{
		if (mine.containsKey(tile))
		{
			gone.add(tile);
		}
	}

	/** The scene reloaded: tile keys mean other tiles now. */
	public synchronized void onSceneChange()
	{
		mine.clear();
		gone.clear();
		lays.clear();
	}

	/** After this tick's object events: despawns with no respawn are gone, Hunter XP ends the check. */
	public synchronized void onTick(int tick, List<XpDrop> drops)
	{
		for (int tile : gone)
		{
			mine.remove(tile);
		}
		gone.clear();
		lays.values().removeIf(t -> tick - t > LAY_WINDOW);
		boolean xp = false;
		for (XpDrop d : drops)
		{
			if (SKILL.equals(d.skill))
			{
				xp = true;
			}
		}
		if (xp)
		{
			caught++;
			lastActivity = tick;
			TickRecord r = timeline.at(tick);
			if (r != null)
			{
				r.caught = true;
			}
			if (checkTick >= 0)
			{
				timeline.closeSpan(KIND, tick);
				checkTick = -1;
			}
		}
		else if (checkTick >= 0 && tick - checkTick >= CHECK_TICKS)
		{
			timeline.closeSpan(KIND, checkTick + 1);
			checkTick = -1;
		}
	}

	/** True while hunting: a trap of yours is down and you did something with them recently. */
	public synchronized boolean showing(int tick)
	{
		return !mine.isEmpty() && lastActivity >= 0 && tick - lastActivity <= LINGER;
	}

	/** Your traps on the ground. */
	public synchronized int traps()
	{
		return mine.size();
	}

	/** Your traps in {@code s}. */
	public synchronized int count(State s)
	{
		int n = 0;
		for (State v : mine.values())
		{
			if (v == s)
			{
				n++;
			}
		}
		return n;
	}

	/** Catches (Hunter XP drops) since login. */
	public synchronized int caught()
	{
		return caught;
	}

	/** The traps you may set at {@code hunterLevel}: one, plus one per 20 levels, up to five. */
	public static int maxTraps(int hunterLevel)
	{
		return Math.max(1, Math.min(5, 1 + hunterLevel / 20));
	}

	/** A fresh timeline (after logout): traps there are no longer known. */
	public synchronized void rebind(Timeline t)
	{
		timeline = t;
		onSceneChange();
		checkTick = -1;
		lastActivity = -1;
	}

	/** Another account. */
	public synchronized void reset()
	{
		rebind(timeline);
		caught = 0;
	}
}
