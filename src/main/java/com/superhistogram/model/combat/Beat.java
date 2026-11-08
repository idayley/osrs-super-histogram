package com.superhistogram.model.combat;

import java.util.ArrayList;
import java.util.List;

/**
 * A metronome the player starts with the hotkey and nothing else. It never predicts anything:
 * beats are spaced by the player's own weapon speed from the press tick, and each is judged
 * only against the player's own overhead-prayer state.
 */
public final class Beat
{
	public enum Verdict
	{
		ON_TIME,
		LATE,
		EARLY
	}

	public static final int END_AFTER_NO_ATTACK = 10;
	public static final int TRAIL = 10;
	private static final int MAX_BEATS = 64;

	private static final class Rec
	{
		final int tick;
		final boolean pressed;
		Verdict verdict;

		Rec(int tick, boolean pressed)
		{
			this.tick = tick;
			this.pressed = pressed;
		}
	}

	private final List<Rec> beats = new ArrayList<>();
	private boolean active;
	private int speed = AttackTimer.DEFAULT_SPEED;
	private int nextBeat = -1;
	private int pressTick = AttackTimer.NEVER;
	private int lastAttack = AttackTimer.NEVER;
	private boolean prevOn;

	/** Starts (or re-phases) the beat. Ignored unless in combat. */
	public synchronized boolean press(int tick, int weaponSpeed, boolean inCombat)
	{
		if (!inCombat)
		{
			return false;
		}
		active = true;
		speed = Math.max(1, weaponSpeed);
		pressTick = tick;
		add(new Rec(tick, true));
		nextBeat = tick + speed;
		return true;
	}

	public synchronized void onWeaponSpeed(int tick, int weaponSpeed)
	{
		speed = Math.max(1, weaponSpeed);
		if (active)
		{
			nextBeat = Math.max(lastBeat() + speed, tick + 1);
		}
	}

	public synchronized void onAttack(int tick)
	{
		lastAttack = tick;
	}

	/** Called once per game tick with whether an overhead prayer is on this tick. */
	public synchronized void onTick(int tick, boolean overheadOn)
	{
		if (active && tick - Math.max(pressTick, lastAttack) >= END_AFTER_NO_ATTACK)
		{
			active = false;
			nextBeat = -1;
		}
		Rec prev = find(tick - 1);
		if (prev != null && !prev.pressed && prev.verdict == null)
		{
			prev.verdict = overheadOn ? (prevOn ? Verdict.EARLY : Verdict.ON_TIME) : Verdict.LATE;
		}
		if (active && tick == nextBeat)
		{
			add(new Rec(tick, false));
			nextBeat = tick + speed;
		}
		prevOn = overheadOn;
	}

	public synchronized void reset()
	{
		beats.clear();
		active = false;
		nextBeat = -1;
		pressTick = AttackTimer.NEVER;
		lastAttack = AttackTimer.NEVER;
		prevOn = false;
	}

	public synchronized boolean active()
	{
		return active;
	}

	public synchronized boolean isBeat(int tick)
	{
		return find(tick) != null;
	}

	public synchronized int nextBeat()
	{
		return active ? nextBeat : -1;
	}

	public synchronized int speed()
	{
		return speed;
	}

	public synchronized Verdict verdict(int beatTick)
	{
		Rec r = find(beatTick);
		return r == null ? null : r.verdict;
	}

	/** The last TRAIL verdicts, oldest first. */
	public synchronized List<Verdict> trail()
	{
		List<Verdict> all = new ArrayList<>();
		for (Rec r : beats)
		{
			if (r.verdict != null)
			{
				all.add(r.verdict);
			}
		}
		int from = Math.max(0, all.size() - TRAIL);
		return new ArrayList<>(all.subList(from, all.size()));
	}

	public synchronized int streak()
	{
		int n = 0;
		for (int i = beats.size() - 1; i >= 0; i--)
		{
			Verdict v = beats.get(i).verdict;
			if (v == null)
			{
				continue;
			}
			if (v != Verdict.ON_TIME)
			{
				break;
			}
			n++;
		}
		return n;
	}

	private int lastBeat()
	{
		return beats.isEmpty() ? pressTick : beats.get(beats.size() - 1).tick;
	}

	private Rec find(int tick)
	{
		for (int i = beats.size() - 1; i >= 0; i--)
		{
			if (beats.get(i).tick == tick)
			{
				return beats.get(i);
			}
		}
		return null;
	}

	private void add(Rec r)
	{
		beats.add(r);
		if (beats.size() > MAX_BEATS)
		{
			beats.remove(0);
		}
	}
}
