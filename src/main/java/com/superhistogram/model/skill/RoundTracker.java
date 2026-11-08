package com.superhistogram.model.skill;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks rounds of a minigame played in its own area (Wintertodt, Tempoross, Guardians of the Rift, Zalcano,
 * Volcanic Mine). A round starts with the first XP in the area after a pause, and ends at the game's end-of-round
 * message ("Your subdued Wintertodt count is", "Amount of rifts you have closed"), when you leave the area, or after
 * {@link #ROUND_GAP} ticks with no XP. Each round's time is kept against your best for that game. Read by the
 * render thread: public methods are synchronized.
 */
public final class RoundTracker
{
	/** One minigame: its name and the map regions it is played in. */
	public static final class Game
	{
		public final String id;
		public final String name;
		final int[] regions;

		Game(String id, String name, int... regions)
		{
			this.id = id;
			this.name = name;
			this.regions = regions;
		}
	}

	public static final List<Game> GAMES = new ArrayList<>();

	static
	{
		GAMES.add(new Game("wintertodt", "Wintertodt", 6462));
		GAMES.add(new Game("tempoross", "Tempoross", 12078));
		GAMES.add(new Game("gotr", "Guardians of the Rift", 14484));
		GAMES.add(new Game("zalcano", "Zalcano", 12126));
		GAMES.add(new Game("volcanic", "Volcanic Mine", 15262, 15263));
	}

	/** End-of-round messages (kill counts, rifts closed). */
	static final String[] END_MESSAGES = {"count is", "rifts you have closed"};
	/** No XP for this long ends the round. */
	public static final int ROUND_GAP = 50;
	/** The view stays this long after a round ends, while you are still in the area. */
	public static final int LINGER = 300;
	static final int MARKS_KEPT = 16;

	/** A round on the bar: where it started and, once over, where it ended. */
	public static final class Round
	{
		public final int start;
		public final int end;
		public final int number;

		Round(int start, int end, int number)
		{
			this.start = start;
			this.end = end;
			this.number = number;
		}
	}

	private Timeline timeline;
	private Game game;
	private int roundStart = -1;
	private int lastXp = -1;
	private int lastEnd = -1;
	private int lastRoundTicks = -1;
	private boolean lastWasBest;
	private int rounds;
	private int roundXp;
	/** The game's own score this round (Tempoross, Volcanic Mine); -1 where the game has none. */
	private int points = -1, lastRoundPoints = -1;
	/** Volcanic Mine's stability, 0-100; -1 elsewhere. */
	private int stability = -1;
	private final Map<String, Integer> best = new HashMap<>();
	private final List<Round> history = new ArrayList<>();

	public RoundTracker(Timeline timeline)
	{
		this.timeline = timeline;
	}

	/** The minigame played in map region {@code regionId}, or null. */
	public static Game byRegion(int regionId)
	{
		for (Game g : GAMES)
		{
			for (int r : g.regions)
			{
				if (r == regionId)
				{
					return g;
				}
			}
		}
		return null;
	}

	/**
	 * @param regionId the map region you stand in (the template region inside an instance)
	 */
	public synchronized void onTick(int tick, int regionId, List<XpDrop> drops)
	{
		Game here = byRegion(regionId);
		if (here != game)
		{
			if (roundStart >= 0)
			{
				end(lastXp);
			}
			if (here != null)
			{
				game = here;
			}
			else if (game != null && tick - lastEnd > LINGER)
			{
				game = null;
			}
		}
		if (here == null)
		{
			return;
		}
		int xp = 0;
		for (XpDrop d : drops)
		{
			xp += Math.max(0, d.amount);
		}
		if (roundStart >= 0 && tick - lastXp > ROUND_GAP)
		{
			end(lastXp);
		}
		if (xp > 0)
		{
			if (roundStart < 0 && tick > lastEnd)
			{
				roundStart = tick;
				roundXp = 0;
				lastRoundPoints = -1;
				history.add(new Round(tick, -1, rounds + 1));
				while (history.size() > MARKS_KEPT)
				{
					history.remove(0);
				}
			}
			roundXp += xp;
			lastXp = tick;
		}
	}

	/**
	 * The game's own numbers this tick (after {@link #onTick}): its points this round and, for Volcanic Mine, the
	 * mine's stability; -1 for none. The best points of the round are kept for after it (the game zeroes them).
	 */
	public synchronized void onScore(int points, int stability)
	{
		this.points = points;
		this.stability = stability;
		if (roundStart >= 0 && points >= 0)
		{
			lastRoundPoints = Math.max(lastRoundPoints, points);
		}
	}

	/** Points this round; -1 when the game has none. */
	public synchronized int points()
	{
		return points;
	}

	/** Points the round under way has reached, or the last round's; -1 when none. */
	public synchronized int roundPoints()
	{
		return roundStart >= 0 ? points : lastRoundPoints;
	}

	/** Volcanic Mine's stability, 0-100; -1 elsewhere. */
	public synchronized int stability()
	{
		return stability;
	}

	/** A game message: an end-of-round one ends the round now. */
	public synchronized void onMessage(int tick, String message)
	{
		if (message == null || roundStart < 0)
		{
			return;
		}
		for (String m : END_MESSAGES)
		{
			if (message.contains(m))
			{
				end(tick);
				return;
			}
		}
	}

	private void end(int tick)
	{
		int ticks = Math.max(1, tick - roundStart);
		rounds++;
		lastRoundTicks = ticks;
		Integer b = best.get(game.id);
		lastWasBest = b == null || ticks < b;
		if (lastWasBest)
		{
			best.put(game.id, ticks);
		}
		if (!history.isEmpty())
		{
			Round r = history.get(history.size() - 1);
			history.set(history.size() - 1, new Round(r.start, tick, r.number));
		}
		roundStart = -1;
		lastEnd = tick;
	}

	/** True in a minigame's area once a round has started, until a while after the last one ends. */
	public synchronized boolean showing(int tick)
	{
		return game != null && (roundStart >= 0 || lastEnd >= 0 && tick - lastEnd <= LINGER);
	}

	public synchronized Game game()
	{
		return game;
	}

	public synchronized boolean inRound()
	{
		return roundStart >= 0;
	}

	public synchronized int roundStartTick()
	{
		return roundStart;
	}

	/** Rounds finished since login (all games). */
	public synchronized int rounds()
	{
		return rounds;
	}

	public synchronized int lastRoundTicks()
	{
		return lastRoundTicks;
	}

	public synchronized boolean lastWasBest()
	{
		return lastWasBest;
	}

	/** Your fastest round of this game since login; -1 before one. */
	public synchronized int bestTicks()
	{
		Integer b = game == null ? null : best.get(game.id);
		return b == null ? -1 : b;
	}

	/** XP of the round under way (or the last one). */
	public synchronized int roundXp()
	{
		return roundXp;
	}

	public synchronized List<Round> history()
	{
		return new ArrayList<>(history);
	}

	/** A fresh timeline (after logout): the round in progress is dropped. */
	public synchronized void rebind(Timeline t)
	{
		timeline = t;
		game = null;
		roundStart = -1;
		lastXp = -1;
		lastEnd = -1;
		history.clear();
	}

	/** Another account. */
	public synchronized void reset()
	{
		rebind(timeline);
		rounds = 0;
		lastRoundTicks = -1;
		lastWasBest = false;
		roundXp = 0;
		points = -1;
		lastRoundPoints = -1;
		stability = -1;
		best.clear();
	}
}
