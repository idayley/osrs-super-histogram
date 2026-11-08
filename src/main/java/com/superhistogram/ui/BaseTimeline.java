package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Hit;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.FoodKind;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one timeline every mode shares (one-timeline.html): every fact in its fixed {@link Lane}, each lane under
 * its own alpha so a mode can dim what it doesn't care about. Paints in timeline coordinates: the caller clips
 * to the timeline and applies the smooth-scrolling translation, as {@link BarPainter} does for the lenses.
 */
public final class BaseTimeline
{
	/** Labels need this much lane height (px) to be drawn. */
	/** Lane height from which pills carry text: an 8 px label fits a 7.5 px pill (the font's caps are ~5 px). */
	static final float LABEL_MIN_H = 7.5f;

	private BaseTimeline()
	{
	}

	/** Paints every lane; {@code alpha} is indexed by {@link Lane#ordinal()} (null = all at full strength). */
	public static void paint(Graphics2D g, BarLayout l, BarState s, float[] alpha)
	{
		ruler(g, l, s, a(alpha, Lane.RULER));
		if (!hasTicks(s))
		{
			return;
		}
		if (a(alpha, Lane.RULER) > 0f)
		{
			beat(g, l, s);
		}
		doing(g, l, s, a(alpha, Lane.DOING));
		prayer(g, l, s, a(alpha, Lane.PRAYER));
		inputs(g, l, s, a(alpha, Lane.INPUTS));
		top(g, l, s, a(alpha, Lane.TOP));
	}

	private static float a(float[] alpha, Lane lane)
	{
		return alpha == null || alpha.length <= lane.ordinal() ? 1f : alpha[lane.ordinal()];
	}

	/** A copy of g that paints at {@code alpha} on top of any alpha it already has; null when alpha is 0. */
	private static Graphics2D under(Graphics2D g, float alpha)
	{
		if (alpha <= 0f)
		{
			return null;
		}
		Graphics2D c = (Graphics2D) g.create();
		if (alpha < 1f)
		{
			Composite old = c.getComposite();
			float base = old instanceof AlphaComposite && ((AlphaComposite) old).getRule() == AlphaComposite.SRC_OVER
				? ((AlphaComposite) old).getAlpha() : 1f;
			c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, base * Math.min(1f, alpha)));
		}
		return c;
	}

	// ---- ruler ----

	/** Tick marks in the ruler lane, and a quiet note on each lag tick. */
	public static void ruler(Graphics2D g0, BarLayout l, BarState s, float alpha)
	{
		Graphics2D g = under(g0, alpha);
		if (g == null)
		{
			return;
		}
		try
		{
			g.setColor(Palette.RULER);
			float y = l.laneY(Lane.RULER), h = l.laneH(Lane.RULER);
			float w = Math.max(1f, Math.round(1.5f * l.u));
			if (!hasTicks(s))
			{
				for (int i = -1; i <= l.visibleTicks + 1; i++)
				{
					g.fill(new Rectangle2D.Float(l.timelineX + i * l.tickWidth - w / 2f, y, w, h));
				}
				return;
			}
			int oldest = TimelinePainter.oldestTick(l, s) - 1;
			for (int t = oldest; t <= s.nowTick + l.visibleTicks; t++)
			{
				float x = TimelinePainter.x(l, s, t);
				if (x > l.timelineX + l.timelineW + l.tickWidth)
				{
					break;
				}
				g.fill(new Rectangle2D.Float(x - w / 2f, y, w, h));
			}
			lagNotes(g, l, s, oldest, y, h);
		}
		finally
		{
			g.dispose();
		}
	}

	/**
	 * Each lag tick, marked like a measurement on the ruler: a thin grey line either side of how long it took
	 * ("930"), so it never collides with the XP and hits above.
	 */
	private static void lagNotes(Graphics2D g, BarLayout l, BarState s, int oldest, float y, float h)
	{
		g.setFont(Fonts.bold(7f * l.u));
		java.awt.FontMetrics fm = g.getFontMetrics();
		float mid = y + h / 2f, lh = Math.max(1f, l.u), pad = 1.5f * l.u;
		for (int t = oldest; t < s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null || r.lengthMs < TimelinePainter.LAG_MS)
			{
				continue;
			}
			float x0 = TimelinePainter.x(l, s, t) + 2f * l.u, x1 = TimelinePainter.x(l, s, t + 1) - 2f * l.u;
			String txt = Integer.toString(r.lengthMs);
			float tw = fm.stringWidth(txt), tx = (x0 + x1 - tw) / 2f;
			g.setColor(Palette.LAG_LINE);
			g.fill(new Rectangle2D.Float(x0, mid - lh / 2f, Math.max(0f, tx - pad - x0), lh));
			g.fill(new Rectangle2D.Float(tx + tw + pad, mid - lh / 2f, Math.max(0f, x1 - tx - tw - pad), lh));
			g.setColor(Palette.DIM);
			g.drawString(txt, tx, mid + fm.getAscent() * 0.42f);
		}
		g.setColor(Palette.RULER);
	}

	/**
	 * Your beat (Beat hotkey, every "Beat every" ticks), e.g. to track the enemy: a caret on the ruler at each beat
	 * in view, and the next beat ahead of now as a bright caret with a guide line and the ticks left (held at the right edge
	 * when it is further out).
	 */
	private static void beat(Graphics2D g, BarLayout l, BarState s)
	{
		Beat b = s.beat;
		if (b == null || l.rows < 3)
		{
			return;
		}
		float u = l.u, y = l.laneY(Lane.RULER) + 1f * u;
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			if (b.isBeat(t))
			{
				CombatPainter.caret(g, TimelinePainter.x(l, s, t), y, u, Palette.WHITE);
			}
		}
		int next = b.nextBeat();
		if (next <= s.nowTick)
		{
			return;
		}
		float right = l.timelineX + l.timelineW - 4f * u;
		float x = Math.min(TimelinePainter.x(l, s, next), right);
		g.setColor(Palette.NEXT_BEAT_GUIDE);
		g.fill(new Rectangle2D.Float(x - 0.75f * u, l.laneY(Lane.TOP), 1.5f * u, y - l.laneY(Lane.TOP)));
		CombatPainter.caret(g, x, y, u, Palette.WHITE);
		g.setFont(Fonts.bold(Math.max(8f, 9f * u)));
		g.setColor(Palette.WHITE);
		String left = Integer.toString(next - s.nowTick);
		float tw = g.getFontMetrics().stringWidth(left);
		g.drawString(left, x - tw / 2f, l.laneY(Lane.TOP) + 8f * u);
	}

	// ---- top: what came to you ----

	/**
	 * Hits on you (a line from the top lane down to the bottom of the prayer lane, the number in the top lane;
	 * a miss is a light-blue line and 0) and XP drops in the top lane, moved right past a hit number on the same tick.
	 * One-row bar: hits are notches and XP is hidden.
	 */
	public static void top(Graphics2D g0, BarLayout l, BarState s, float alpha)
	{
		Graphics2D g = hasTicks(s) ? under(g0, alpha) : null;
		if (g == null)
		{
			return;
		}
		try
		{
			List<float[]> busy = new ArrayList<>();
			Map<Integer, Float> shift = hits(g, l, s, busy);
			XpRow.paintShifted(g, l, s, shift);
			if (l.rows > 1)
			{
				busy.addAll(XpRow.numberRanges(g, l, s));
				topIcons(g, l, s, busy);
			}
		}
		finally
		{
			g.dispose();
		}
	}

	/** Paints the hits; returns how far (px) each tick's XP must move right to clear its hit number. */
	private static Map<Integer, Float> hits(Graphics2D g, BarLayout l, BarState s, List<float[]> busy)
	{
		Map<Integer, Float> shift = new HashMap<>();
		float u = l.u;
		float y0 = l.laneY(Lane.TOP), y1 = l.laneY(Lane.PRAYER) + l.laneH(Lane.PRAYER);
		float baseline = TimelinePainter.xpY(l);
		// Where earlier ticks' XP numbers run (they start 4u before their tick and can run past the next ones).
		List<float[]> xp = l.rows == 1 ? new ArrayList<>() : XpRow.numberRanges(g, l, s);
		g.setFont(Fonts.bold(10f * u));
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null || r.hits.isEmpty())
			{
				continue;
			}
			boolean landed = false;
			for (Hit h : r.hits)
			{
				landed |= !h.blocked;
			}
			float x = TimelinePainter.x(l, s, t);
			g.setColor(landed ? Palette.damage() : Palette.MISS_FLASH);
			if (l.rows == 1)
			{
				g.fill(new Rectangle2D.Float(x + l.tickWidth / 2f - 1.5f, y0, 3f, l.laneY(Lane.DOING) - y0 + 1f));
				continue;
			}
			float w = Math.max(1f, 1.5f * u);
			g.fill(new RoundRectangle2D.Float(x - w / 2f, y0, w, y1 - y0, w, w));
			float tx = x + 4f * u, end = x;
			for (float[] range : xp)
			{
				// An earlier tick's XP still running over this hit: the number starts after it.
				if (range[0] < x - 4f * u && range[1] > x - 3f * u)
				{
					tx = Math.max(tx, range[1] + 4f * u);
				}
			}
			for (Hit h : r.hits)
			{
				if (h.blocked && landed)
				{
					continue;
				}
				g.setColor(h.blocked ? Palette.MISS_FLASH : Palette.damage());
				String text = String.valueOf(h.amount);
				g.drawString(text, tx, baseline);
				end = tx + g.getFontMetrics().stringWidth(text);
				busy.add(new float[]{tx, end});
				tx = end + 4f * u;
			}
			// This tick's XP starts 4u before the tick, across the line; put it 5u after the line or hit number.
			shift.put(t, end + 5f * u - (x - 4f * u));
		}
		return shift;
	}

	/**
	 * Whatever the activity brought in, whenever its tracker has it: the gathered item of each success and each
	 * mark of grace just left of that tick's XP, and "burnt" on each failed production action. Each is left out
	 * where it would touch an XP or hit number (the {@code busy} x ranges).
	 */
	private static void topIcons(Graphics2D g, BarLayout l, BarState s, List<float[]> busy)
	{
		float u = l.u, baseline = TimelinePainter.xpY(l), iy = baseline - 3.5f * u;
		String gather = s.gather == null ? null : s.gather.skill();
		boolean items = gather != null && !"Firemaking".equals(gather);
		int since = s.agility == null ? Integer.MAX_VALUE : s.agility.courseSinceTick();
		g.setFont(Fonts.semiBold(9.5f * u));
		float burntW = g.getFontMetrics().stringWidth("burnt");
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null)
			{
				continue;
			}
			float x = TimelinePainter.x(l, s, t);
			if (items && r.gathered > 0 && clear(busy, x - 14f * u, x - 8f * u))
			{
				if ("Woodcutting".equals(gather))
				{
					SkillPainter.log(g, x - 11f * u, iy, u);
				}
				else
				{
					SkillPainter.item(g, x - 11f * u, iy, u, Palette.skill(gather));
				}
			}
			if (r.mark && t > since && clear(busy, x - 13.5f * u, x - 4.5f * u))
			{
				SkillPainter.mark(g, x - 9f * u, iy, u);
			}
			if (r.failedAction && clear(busy, x - burntW / 2f, x + burntW / 2f))
			{
				g.setColor(Palette.BURNT);
				g.drawString("burnt", x - burntW / 2f, baseline);
			}
		}
	}

	private static boolean clear(List<float[]> busy, float x0, float x1)
	{
		for (float[] r : busy)
		{
			if (x0 < r[1] && x1 > r[0])
			{
				return false;
			}
		}
		return true;
	}

	// ---- doing ----

	/** What you are doing (see {@link DoingLane}); prayers have their own lane and are left out. */
	public static void doing(Graphics2D g0, BarLayout l, BarState s, float alpha)
	{
		Graphics2D g = hasTicks(s) ? under(g0, alpha) : null;
		if (g == null)
		{
			return;
		}
		try
		{
			DoingLane.paint(g, l, s);
		}
		finally
		{
			g.dispose();
		}
	}

	// ---- prayer ----

	/** Prayer pills at prayer-lane height, click-timed, with the points used when the lane is tall enough. */
	public static void prayer(Graphics2D g0, BarLayout l, BarState s, float alpha)
	{
		Graphics2D g = hasTicks(s) ? under(g0, alpha) : null;
		if (g == null)
		{
			return;
		}
		try
		{
			float h = l.laneH(Lane.PRAYER);
			CombatPainter.prayerPills(g, l, s, l.laneY(Lane.PRAYER), h, l.rows > 1 && h >= LABEL_MIN_H);
		}
		finally
		{
			g.dispose();
		}
	}

	/** {left, right} of the prayer-points labels the prayer lane draws; empty when the lane is too small for them. */
	public static List<float[]> prayerLabels(Graphics2D g, BarLayout l, BarState s)
	{
		return hasTicks(s) && l.rows > 1 && l.laneH(Lane.PRAYER) >= LABEL_MIN_H
			? CombatPainter.prayerLabelRanges(g, l, s) : new ArrayList<>();
	}

	// ---- inputs ----

	/**
	 * Your inputs (3-row bar only): clicks as 2u marks, attacks (sword) and eats (fish, with the attack-timer
	 * cost when on) as the actions row setting allows, bank clicks and depletion marks.
	 */
	public static void inputs(Graphics2D g0, BarLayout l, BarState s, float alpha)
	{
		Graphics2D g = hasTicks(s) && l.hasLane(Lane.INPUTS) ? under(g0, alpha) : null;
		if (g == null)
		{
			return;
		}
		try
		{
			float u = l.u, y = l.laneY(Lane.INPUTS), h = l.laneH(Lane.INPUTS), bottom = y + h;
			boolean swords = s.actionsRow != ActionsRow.OFF;
			boolean eats = s.actionsRow == ActionsRow.ATTACKS_EATS;
			boolean wood = s.gather != null && "Woodcutting".equals(s.gather.skill());
			// Icons are fitted to the lane: a 12u icon box becomes the lane's height.
			float k = h / Icons.BOX;
			int oldest = TimelinePainter.oldestTick(l, s);
			List<Float> marks = markXs(l, s, oldest, swords);
			g.setFont(Fonts.bold(9f * u));
			for (int t = oldest; t <= s.nowTick; t++)
			{
				TickRecord r = s.timeline.peek(t);
				if (r == null)
				{
					continue;
				}
				float x = TimelinePainter.x(l, s, t);
				float sw = TimelinePainter.slotW(l, s, t);
				clickMarks(g, l, r, x, sw);
				inputMarks(g, l, r, x, sw, bottom, k);
				if (swords && r.attackLate >= 0)
				{
					CombatPainter.sword(g, swordX(l, x), bottom - 4.7f * k, k);
					swingResult(g, l, r, x, bottom);
				}
				if (eats && r.eat != null)
				{
					eat(g, l, s, r, x + l.tickWidth / 2f, y, marks);
				}
				if (r.bankClick || r.gatherBank)
				{
					SkillPainter.bank(g, x, bottom - 6.1f * k, k, Palette.TEXT);
				}
				if (r.depleted)
				{
					float dk = h / 7.2f;
					if (wood)
					{
						SkillPainter.stump(g, x + 6f * u, bottom - 4f * dk, dk);
					}
					else
					{
						SkillPainter.depleted(g, x + 6f * u, bottom - 3.6f * dk, dk);
					}
				}
			}
			if (swords)
			{
				nextAttack(g, l, s, bottom - 4.7f * k, k);
			}
		}
		finally
		{
			g.dispose();
		}
	}

	static final float NEXT_ATTACK_ALPHA = 0.4f;

	/** Your next attack, from your weapon's speed: a faint sword ahead of now on the tick your weapon is ready. */
	private static void nextAttack(Graphics2D g, BarLayout l, BarState s, float y, float k)
	{
		int t = s.nextAttackTick;
		if (t <= s.nowTick)
		{
			return;
		}
		float x = swordX(l, TimelinePainter.x(l, s, t));
		if (x > l.timelineX + l.timelineW)
		{
			return;
		}
		Graphics2D c = (Graphics2D) g.create();
		try
		{
			c.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, NEXT_ATTACK_ALPHA));
			CombatPainter.sword(c, x, y, k);
			if (s.nextAttackLearned)
			{
				// Learned from your swings, not the weapon: say so above the sword.
				c.setColor(Palette.DIM);
				c.setFont(Fonts.bold(8f * l.u));
				String txt = "learned";
				float w = c.getFontMetrics().stringWidth(txt);
				float tx = Math.min(x - w / 2f, l.timelineX + l.timelineW - w);
				c.setComposite(AlphaComposite.SrcOver);
				c.drawString(txt, tx, l.laneY(Lane.PRAYER) + l.laneH(Lane.PRAYER) * 0.75f);
			}
		}
		finally
		{
			c.dispose();
		}
	}

	/** Just the clicks of the inputs lane (2u white marks placed by in-tick time), at full strength. */
	public static void clicks(Graphics2D g, BarLayout l, BarState s)
	{
		if (!hasTicks(s) || !l.hasLane(Lane.INPUTS))
		{
			return;
		}
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r != null)
			{
				clickMarks(g, l, r, TimelinePainter.x(l, s, t), TimelinePainter.slotW(l, s, t));
			}
		}
	}

	private static void clickMarks(Graphics2D g, BarLayout l, TickRecord r, float x, float slotW)
	{
		float u = l.u, y = l.laneY(Lane.INPUTS), h = l.laneH(Lane.INPUTS);
		// Where you clicked, for a click the server got a tick later (drawn there in white).
		g.setColor(Palette.CLICK_GHOST);
		for (Float f : r.clickGhosts)
		{
			float cx = x + (f == null ? 0f : f) * slotW;
			g.fill(new RoundRectangle2D.Float(cx - u, y, 2f * u, h, 2f * u, 2f * u));
		}
		g.setColor(Palette.WHITE);
		for (Float f : r.clicks)
		{
			float cx = x + (f == null ? 0f : f) * slotW;
			g.fill(new RoundRectangle2D.Float(cx - u, y, 2f * u, h, 2f * u, 2f * u));
		}
	}

	/** Gear switch, spec, walk and drink marks, just right of their click line, the size of the sword. */
	private static void inputMarks(Graphics2D g, BarLayout l, TickRecord r, float x, float slotW, float bottom, float k)
	{
		for (com.superhistogram.model.InputMark m : r.inputs)
		{
			float cx = x + m.at * slotW + 4.5f * l.u;
			Icons.Icon icon;
			switch (m.kind)
			{
				case GEAR:
					icon = Icons.Icon.SHIRT;
					g.setColor(Palette.TEXT);
					break;
				case SPEC:
					icon = Icons.Icon.BOLT;
					g.setColor(Palette.RIM_TICK);
					break;
				case WALK:
					icon = Icons.Icon.STEPS;
					g.setColor(Palette.DIM);
					break;
				default:
					icon = Icons.Icon.POT;
					g.setColor(Palette.RIM_TOCK);
					break;
			}
			g.fill(Icons.icon(icon, cx, bottom, k * 0.8f));
		}
		g.setColor(Palette.WHITE);
	}

	/**
	 * What the swing did, beside its sword once the hitsplat landed: the damage in the hit colour, or a blue 0
	 * (the game's miss splat) so a miss streak is plain to see.
	 */
	private static void swingResult(Graphics2D g, BarLayout l, TickRecord r, float x, float bottom)
	{
		if (r.swingHit < 0)
		{
			return;
		}
		g.setColor(r.swingHit == 0 ? Palette.MISS_FLASH : Palette.damage());
		g.drawString(Integer.toString(r.swingHit), swordX(l, x) + 5f * l.u, bottom - 0.5f * l.u);
		g.setColor(Palette.WHITE);
	}

	/** Centre of a tick's sword: 6u into the slot. */
	public static float swordX(BarLayout l, float tickX)
	{
		return tickX + 6f * l.u;
	}

	/** Fish (two for a combo eat) centred on ex, then its cost ("+3", "free") when it fits before the next mark. */
	private static void eat(Graphics2D g, BarLayout l, BarState s, TickRecord r, float ex, float y, List<Float> marks)
	{
		float u = l.u, h = l.laneH(Lane.INPUTS), bottom = y + h;
		// The fish is 6.4 of its 12u box tall: about 5.4u here, inside the 7u lane.
		float fk = 0.85f * u;
		float right;
		if (r.eat == FoodKind.COMBO)
		{
			CombatPainter.fish(g, ex - 3f * u, bottom - 3.2f * fk, fk);
			CombatPainter.fish(g, ex + 3f * u, bottom - 3.2f * fk, fk);
			right = ex + 3f * u + 6.5f * fk;
		}
		else
		{
			CombatPainter.fish(g, ex, bottom - 3.2f * fk, fk);
			right = ex + 6.5f * fk;
		}
		if (!s.showEatCosts)
		{
			return;
		}
		boolean free = r.eatCost <= 0;
		String text = free ? "free" : "+" + r.eatCost;
		float tx = right + 2f * u;
		float tw = g.getFontMetrics().stringWidth(text);
		for (Float m : marks)
		{
			if (m > ex && tx + tw > m - 4f * u)
			{
				return;
			}
		}
		g.setColor(free ? Palette.mint() : Palette.LOST);
		g.drawString(text, tx, bottom - 0.5f * u);
		g.setColor(Palette.WHITE);
	}

	/** Left edges (px) of the swords drawn in view, so eat costs can stop short of them. */
	private static List<Float> markXs(BarLayout l, BarState s, int oldest, boolean swords)
	{
		List<Float> out = new ArrayList<>();
		if (!swords)
		{
			return out;
		}
		for (int t = oldest; t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r != null && r.attackLate >= 0)
			{
				out.add(swordX(l, TimelinePainter.x(l, s, t)) - 3.5f * l.u);
			}
		}
		return out;
	}

	private static boolean hasTicks(BarState s)
	{
		return s != null && s.timeline != null && s.nowTick >= 0;
	}
}
