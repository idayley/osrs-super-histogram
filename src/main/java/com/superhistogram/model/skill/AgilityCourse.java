package com.superhistogram.model.skill;

import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks agility obstacles and laps on a course. An obstacle is a click confirmed by an Agility
 * XP drop; a lap ends on XP at a lap-end tile. The first lap after a login or course switch is
 * provisional until the next lap starts with the same obstacle.
 * Read by the render thread, so the public methods are synchronized and lists are snapshots.
 */
public final class AgilityCourse
{
	public static final int LOST_MAX = 10;
	public static final int STALE = 30;
	public static final int LINGER = 50;
	public static final int MARK_WINDOW = 2;
	public static final int REVERT_WINDOW = 2;
	public static final int HISTORY = 64;
	public static final int LAPS_KEPT = 16;
	private static final int NUMBERS = 64;
	private static final String AGILITY = "Agility";

	/** An immutable snapshot of one obstacle. */
	public static final class Obstacle
	{
		public final int objectId;
		public final int startTick;
		/** Landing tick, -1 while pending. */
		public final int endTick;
		/** 1-based position in the course, 0 unknown. */
		public final int number;
		public final int lap;
		public final boolean failed;

		Obstacle(int objectId, int startTick, int endTick, int number, int lap, boolean failed)
		{
			this.objectId = objectId;
			this.startTick = startTick;
			this.endTick = endTick;
			this.number = number;
			this.lap = lap;
			this.failed = failed;
		}
	}

	public static final class LapStart
	{
		public final int tick;
		public final int number;

		LapStart(int tick, int number)
		{
			this.tick = tick;
			this.number = number;
		}
	}

	private static final class CourseStats
	{
		int best = -1;
		final List<Integer> order = new ArrayList<>();
		final int[] gapSum = new int[NUMBERS];
		final int[] gapCount = new int[NUMBERS];
		int fullLaps;
	}

	private static final class Provisional
	{
		final int ticks;
		final int firstId;
		final List<Integer> ids;
		final List<int[]> gaps;

		Provisional(int ticks, int firstId, List<Integer> ids, List<int[]> gaps)
		{
			this.ticks = ticks;
			this.firstId = firstId;
			this.ids = ids;
			this.gaps = gaps;
		}
	}

	private Timeline tl;
	private final Map<String, CourseStats> stats = new HashMap<>();
	private final List<Obstacle> done = new ArrayList<>();
	private final List<LapStart> lapStarts = new ArrayList<>();
	private String course;
	private Obstacle pending;
	private boolean pendingStartedLap;
	private boolean failureSince;
	private boolean lastFailedIsLast;
	private int lastLanding = -1;
	private int lastLandingNumber;
	private boolean lastLandingWasEnd;
	private int landings;
	private int lastTick = -1;
	private int courseSince = -1;
	private int pendingMark = -1;

	private int lapStart = -1;
	private int lapNumber;
	private boolean lapFull;
	private int lapFirstId;
	private int lapLost;
	private List<Integer> lapIds = new ArrayList<>();
	private List<int[]> lapGaps = new ArrayList<>();
	private boolean afterLapEnd;
	private Provisional provisional;
	private int lastLap = -1;
	private int previousLap = -1;
	private boolean lastWasBest;
	private boolean bestChanged;
	/** Laps finished and marks of grace picked up this session (kept across logouts and courses). */
	private int sessionLaps, sessionMarks;
	/** Agility XP gained since the last lap ended; the XP of the last full (or, before one, any) lap. */
	private int lapXpRunning, lapXp, lapXpFull = -1;

	public AgilityCourse(Timeline t)
	{
		this.tl = t;
	}

	private CourseStats stats(String id)
	{
		return stats.computeIfAbsent(id, k -> new CourseStats());
	}

	private void clearSession()
	{
		pending = null;
		pendingStartedLap = false;
		failureSince = false;
		lastFailedIsLast = false;
		lastLanding = -1;
		lastLandingNumber = 0;
		lastLandingWasEnd = false;
		landings = 0;
		pendingMark = -1;
		lapStart = -1;
		lapNumber = 0;
		lapFull = false;
		lapFirstId = 0;
		lapLost = 0;
		lapIds = new ArrayList<>();
		lapGaps = new ArrayList<>();
		afterLapEnd = false;
		provisional = null;
		lastLap = -1;
		previousLap = -1;
		lastWasBest = false;
	}

	public synchronized void onCourse(String courseId)
	{
		if (courseId == null || courseId.equals(course))
		{
			return;
		}
		course = courseId;
		courseSince = lastTick;
		clearSession();
		// Another course gives other XP per lap.
		lapXpRunning = 0;
		lapXp = 0;
		lapXpFull = -1;
	}

	public synchronized void rebind(Timeline t)
	{
		tl = t;
		clearSession();
		done.clear();
		lapStarts.clear();
		lastTick = -1;
		courseSince = -1;
	}

	public synchronized void reset()
	{
		clearSession();
		stats.clear();
		done.clear();
		lapStarts.clear();
		course = null;
		bestChanged = false;
		sessionLaps = 0;
		sessionMarks = 0;
		lapXpRunning = 0;
		lapXp = 0;
		lapXpFull = -1;
		lastTick = -1;
		courseSince = -1;
	}

	public synchronized void loadBest(String courseId, int ticks)
	{
		if (courseId == null || ticks <= 0)
		{
			return;
		}
		CourseStats s = stats(courseId);
		if (s.best < 0 || ticks < s.best)
		{
			s.best = ticks;
		}
	}

	public synchronized boolean takeBestChanged()
	{
		boolean b = bestChanged;
		bestChanged = false;
		return b;
	}

	private int numberFor(int objectId)
	{
		CourseStats s = stats(course);
		if (s.order.isEmpty())
		{
			return lapIds.size() + 1;
		}
		int idx = s.order.indexOf(objectId);
		return idx < 0 ? 0 : idx + 1;
	}

	private void trim()
	{
		while (done.size() > HISTORY)
		{
			done.remove(0);
		}
	}

	/** Removes the pending obstacle, undoing the lap it opened if nothing had landed in it. */
	private void dropPending()
	{
		if (pending == null)
		{
			return;
		}
		if (pendingStartedLap && lapIds.isEmpty() && lapStart >= 0)
		{
			lapStart = -1;
			lapNumber--;
			if (!lapStarts.isEmpty())
			{
				lapStarts.remove(lapStarts.size() - 1);
			}
			afterLapEnd = lapFull;
			lapGaps = new ArrayList<>();
		}
		pending = null;
		pendingStartedLap = false;
	}

	public synchronized void onObstacleClick(int tick, int objectId)
	{
		if (course == null)
		{
			return;
		}
		if (pending != null)
		{
			if (pending.objectId == objectId)
			{
				return;
			}
			dropPending();
		}
		boolean gapKnown = lastLanding >= 0 && !lastLandingWasEnd && !failureSince;
		failureSince = false;
		lastFailedIsLast = false;
		boolean startsLap = lapStart < 0;
		if (startsLap)
		{
			startLap(tick, objectId);
		}
		if (gapKnown && !startsLap)
		{
			int n = tick - lastLanding;
			if (n >= 1 && n <= LOST_MAX)
			{
				TickRecord r = tl.at(tick);
				if (r != null)
				{
					r.agilityLost = n;
				}
				lapLost += n;
				addGap(lastLandingNumber, n);
			}
			else if (n <= 0)
			{
				addGap(lastLandingNumber, 0);
			}
		}
		pending = new Obstacle(objectId, tick, -1, numberFor(objectId), lapNumber, false);
		pendingStartedLap = startsLap;
	}

	private void startLap(int tick, int objectId)
	{
		if (provisional != null)
		{
			if (provisional.firstId == objectId)
			{
				confirm(provisional);
			}
			provisional = null;
		}
		lapFull = afterLapEnd;
		afterLapEnd = false;
		lapNumber++;
		lapStart = tick;
		lapFirstId = objectId;
		lapLost = 0;
		lapIds = new ArrayList<>();
		lapGaps = new ArrayList<>();
		lapStarts.add(new LapStart(tick, lapNumber));
		while (lapStarts.size() > LAPS_KEPT)
		{
			lapStarts.remove(0);
		}
	}

	private void addGap(int number, int n)
	{
		if (number <= 0 || number >= NUMBERS)
		{
			return;
		}
		if (lapFull)
		{
			CourseStats s = stats(course);
			s.gapSum[number] += n;
			s.gapCount[number]++;
		}
		else
		{
			lapGaps.add(new int[] {number, n});
		}
	}

	private void setBest(int ticks)
	{
		CourseStats s = stats(course);
		if (s.best < 0 || ticks < s.best)
		{
			s.best = ticks;
			bestChanged = true;
		}
	}

	private void learn(List<Integer> ids)
	{
		CourseStats s = stats(course);
		if (s.order.isEmpty())
		{
			for (Integer id : ids)
			{
				if (!s.order.contains(id))
				{
					s.order.add(id);
				}
			}
		}
	}

	private void confirm(Provisional p)
	{
		CourseStats s = stats(course);
		setBest(p.ticks);
		learn(p.ids);
		for (int[] g : p.gaps)
		{
			s.gapSum[g[0]] += g[1];
			s.gapCount[g[0]]++;
		}
		s.fullLaps++;
	}

	private void endLap(int tick)
	{
		int ticks = tick - lapStart;
		previousLap = lastLap;
		lastLap = ticks;
		lapStart = -1;
		afterLapEnd = true;
		sessionLaps++;
		if (lapFull)
		{
			lapXpFull = lapXpRunning;
		}
		lapXp = lapXpRunning;
		lapXpRunning = 0;
		if (lapFull)
		{
			CourseStats s = stats(course);
			lastWasBest = s.best < 0 || ticks < s.best;
			setBest(ticks);
			learn(lapIds);
			s.fullLaps++;
		}
		else
		{
			lastWasBest = false;
			provisional = new Provisional(ticks, lapFirstId, lapIds, lapGaps);
			lapIds = new ArrayList<>();
			lapGaps = new ArrayList<>();
		}
	}

	private boolean land(Obstacle o, int tick, boolean atLapEnd)
	{
		lastLanding = tick;
		lastLandingNumber = o == null ? 0 : o.number;
		failureSince = false;
		lastFailedIsLast = false;
		landings++;
		if (o != null && !lapIds.contains(o.objectId))
		{
			lapIds.add(o.objectId);
		}
		boolean ended = atLapEnd && lapStart >= 0;
		lastLandingWasEnd = ended;
		if (ended)
		{
			endLap(tick);
		}
		return ended;
	}

	public synchronized boolean onTick(int tick, List<XpDrop> drops, boolean hitOnMe, boolean atLapEnd)
	{
		lastTick = tick;
		boolean ended = false;
		if (course != null)
		{
			boolean agi = false;
			for (XpDrop d : drops)
			{
				if (AGILITY.equals(d.skill))
				{
					agi = true;
					lapXpRunning += Math.max(0, d.amount);
				}
			}
			if (agi && pending != null)
			{
				Obstacle o = new Obstacle(pending.objectId, pending.startTick, tick, pending.number,
					pending.lap, false);
				pending = null;
				pendingStartedLap = false;
				done.add(o);
				trim();
				ended = land(o, tick, atLapEnd);
			}
			else if (agi && lastFailedIsLast && !done.isEmpty()
				&& tick - done.get(done.size() - 1).endTick <= REVERT_WINDOW)
			{
				Obstacle f = done.get(done.size() - 1);
				Obstacle o = new Obstacle(f.objectId, f.startTick, tick, f.number, f.lap, false);
				done.set(done.size() - 1, o);
				ended = land(o, tick, atLapEnd);
			}
			else if (agi && atLapEnd && lapStart >= 0)
			{
				ended = land(null, tick, true);
			}
			else if (hitOnMe && pending != null && !agi)
			{
				done.add(new Obstacle(pending.objectId, pending.startTick, tick, pending.number,
					pending.lap, true));
				trim();
				pending = null;
				pendingStartedLap = false;
				failureSince = true;
				lastFailedIsLast = true;
			}
			if (pending != null && tick - pending.startTick > STALE)
			{
				dropPending();
			}
			if (pendingMark >= 0 && pendingMark <= tick)
			{
				resolveMark(pendingMark);
				pendingMark = -1;
			}
		}
		return ended;
	}

	private void resolveMark(int m)
	{
		if (lastLanding >= 0 && lastLanding >= m - MARK_WINDOW && lastLanding <= m)
		{
			TickRecord r = tl.at(m);
			if (r != null)
			{
				r.mark = true;
			}
		}
	}

	public synchronized void onMark(int tick)
	{
		if (course == null)
		{
			return;
		}
		if (tick <= lastTick)
		{
			resolveMark(tick);
		}
		else
		{
			pendingMark = tick;
		}
	}

	/** Marks of grace picked up on a course. */
	public synchronized void onMarksPickedUp(int n)
	{
		if (n > 0)
		{
			sessionMarks += n;
		}
	}

	/** Agility XP of a lap on this course: the last full lap, else the last lap; 0 before one. */
	public synchronized int lapXp()
	{
		return lapXpFull > 0 ? lapXpFull : lapXp;
	}

	/** Laps finished this session. */
	public synchronized int laps()
	{
		return sessionLaps;
	}

	/** Marks of grace picked up this session. */
	public synchronized int marks()
	{
		return sessionMarks;
	}

	public synchronized String course()
	{
		return course;
	}

	/** The newest tick seen when the current course was entered; obstacles starting after it belong to it. */
	public synchronized int courseSinceTick()
	{
		return courseSince;
	}

	public synchronized boolean showing(int tick)
	{
		return course != null && landings >= 2
			&& (pending != null || (lastLanding >= 0 && tick - lastLanding <= LINGER));
	}

	public synchronized boolean pending()
	{
		return pending != null;
	}

	public synchronized int lapStartTick()
	{
		return lapStart;
	}

	public synchronized int lapNumber()
	{
		return lapNumber;
	}

	public synchronized int lastLapTicks()
	{
		return lastLap;
	}

	public synchronized int previousLapTicks()
	{
		return previousLap;
	}

	public synchronized boolean lastLapWasBest()
	{
		return lastWasBest;
	}

	public synchronized int bestTicks()
	{
		if (course == null)
		{
			return -1;
		}
		CourseStats s = stats.get(course);
		return s == null ? -1 : s.best;
	}

	public synchronized int lapLost()
	{
		return lapLost;
	}

	/** The obstacle number with the highest mean gap over completed laps; 0 when none. */
	public synchronized int slowestAfter()
	{
		CourseStats s = course == null ? null : stats.get(course);
		if (s == null || s.fullLaps < 1)
		{
			return 0;
		}
		int bestN = 0;
		long bestSum = 0;
		long bestCount = 1;
		for (int n = 1; n < NUMBERS; n++)
		{
			if (s.gapCount[n] == 0)
			{
				continue;
			}
			if ((long) s.gapSum[n] * bestCount > bestSum * s.gapCount[n])
			{
				bestN = n;
				bestSum = s.gapSum[n];
				bestCount = s.gapCount[n];
			}
		}
		return bestN;
	}

	public synchronized int readyTick()
	{
		return pending == null && lastLanding >= 0 ? lastLanding + 1 : -1;
	}

	public synchronized List<Obstacle> obstacles()
	{
		List<Obstacle> out = new ArrayList<>(done);
		if (pending != null)
		{
			out.add(pending);
		}
		return out;
	}

	public synchronized List<LapStart> lapStarts()
	{
		return new ArrayList<>(lapStarts);
	}
}
