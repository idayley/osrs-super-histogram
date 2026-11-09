package com.superhistogram.ui;

import com.superhistogram.collect.FightCollector;
import com.superhistogram.model.BarState;
import com.superhistogram.model.Span;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.skill.AgilityCourse;
import com.superhistogram.model.skill.ThieveRun;
import com.superhistogram.model.skill.TrapTracker;
import com.superhistogram.model.skill.SpellRun;
import com.superhistogram.model.skill.AlchTracker;
import com.superhistogram.model.skill.GatherRun;
import com.superhistogram.model.skill.InventoryRun;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The doing lane of the one timeline: whatever you were doing, from every tracker with history in view (not
 * only in its mode). Skilling pills (production run, gathering, alch casts, agility obstacles) win, then the
 * fight span, then the generic walk / run / action spans in the ticks nothing else covers, so each tick range
 * has exactly one pill. The pill helpers take their own y, h and icon scale so the old mode views draw
 * through them too.
 */
public final class DoingLane
{
	/** Labels, numbers and icons need this much lane height (px). */
	static final float LABEL_MIN_H = BaseTimeline.LABEL_MIN_H;
	/** Pill height the icon and notch offsets were drawn for (the old 14u middle-row pill). */
	private static final float ICON_PILL_H = 14f;
	private static final String WOODCUTTING = "Woodcutting";

	private DoingLane()
	{
	}

	static void paint(Graphics2D g, BarLayout l, BarState s)
	{
		if (s == null || s.timeline == null || s.nowTick < 0 || !l.hasLane(Lane.DOING))
		{
			return;
		}
		float y = l.laneY(Lane.DOING), h = l.laneH(Lane.DOING);
		boolean details = l.rows > 1 && h >= LABEL_MIN_H;
		float k = h / ICON_PILL_H;
		int oldest = TimelinePainter.oldestTick(l, s);
		List<int[]> covered = new ArrayList<>();

		if (s.run != null)
		{
			for (Span sp : s.run.runs())
			{
				if (productionVisible(s, sp, oldest))
				{
					productionRun(g, l, s, sp, y, h, k, details);
					covered.add(productionRange(s, sp));
				}
			}
		}
		if (s.gather != null)
		{
			for (GatherRun.Pill p : s.gather.pills())
			{
				if (gatherVisible(s, p, oldest))
				{
					gatherPill(g, l, s, p, y, h, k, details, oldest);
					covered.add(new int[]{p.startTick, liveEnd(s, p.endTick)});
				}
			}
		}
		if (s.alch != null)
		{
			for (int t = oldest - AlchTracker.CAST_TICKS; t <= s.nowTick; t++)
			{
				TickRecord r = s.timeline.peek(t);
				if (r != null && r.cast)
				{
					alchCast(g, l, s, t, y, h);
					covered.add(new int[]{t, Math.min(t + AlchTracker.CAST_TICKS, s.nowTick + 1)});
				}
			}
		}
		if (s.agility != null)
		{
			int since = s.agility.courseSinceTick();
			for (AgilityCourse.Obstacle o : s.agility.obstacles())
			{
				if (obstacleVisible(s, o, since, oldest))
				{
					obstacle(g, l, s, o, y, h, details);
					covered.add(new int[]{o.startTick, liveEnd(s, o.endTick)});
				}
			}
		}

		List<Span> spans = s.timeline.spansBetween(oldest, s.nowTick);
		List<Span> runs = s.run == null ? new ArrayList<>() : s.run.runs();
		// Fights fill what the skilling pills leave; walking and animations fill what both leave.
		List<int[]> fights = new ArrayList<>();
		for (Span sp : spans)
		{
			if (FightCollector.KIND.equals(sp.kind))
			{
				clipped(g, l, s, sp, covered, y, h, Palette.fight(), false, details ? sp.label : null);
				fights.add(new int[]{sp.startTick, spanEnd(s, sp)});
			}
		}
		covered.addAll(fights);
		// Skill attempts (a pickpocket, a trap check) and the stun after a failed pickpocket; animations go under.
		for (Span sp : spans)
		{
			Color c = attemptColor(sp.kind);
			if (c != null)
			{
				boolean stun = ThieveRun.STUN.equals(sp.kind);
				clipped(g, l, s, sp, covered, y, h, c, stun, stun && details ? sp.label : null);
			}
		}
		for (Span sp : spans)
		{
			if (attemptColor(sp.kind) != null)
			{
				covered.add(new int[]{sp.startTick, spanEnd(s, sp)});
			}
		}
		for (Span sp : spans)
		{
			if (sp.kind.startsWith("pray-") || FightCollector.KIND.equals(sp.kind) || containsSame(runs, sp)
				|| attemptColor(sp.kind) != null)
			{
				continue;
			}
			switch (sp.kind)
			{
				case "walk":
					clipped(g, l, s, sp, covered, y, h, Palette.TEXT, true,
						details ? (sp.label != null ? sp.label : "walking") : null);
					break;
				case "run":
					clipped(g, l, s, sp, covered, y, h, Palette.runEnergy(), true,
						details ? (sp.label != null ? sp.label : "running") : null);
					break;
				default:
					clipped(g, l, s, sp, covered, y, h, Palette.action(), false, details ? sp.label : null);
			}
		}
	}

	/** The pill colour of a skill attempt span; null for any other span. */
	private static Color attemptColor(String kind)
	{
		switch (kind)
		{
			case ThieveRun.KIND:
				return Palette.skill(ThieveRun.SKILL);
			case ThieveRun.STUN:
				return Palette.BURNT;
			case TrapTracker.KIND:
				return Palette.skill(TrapTracker.SKILL);
			case SpellRun.KIND:
				return Palette.prayerMagic();
			default:
				return null;
		}
	}

	private static boolean containsSame(List<Span> list, Span sp)
	{
		for (Span o : list)
		{
			if (o == sp)
			{
				return true;
			}
		}
		return false;
	}

	/** Exclusive end tick of something ending at {@code endTick} (exclusive, -1 = still going), cut at now. */
	private static int liveEnd(BarState s, int endTick)
	{
		return endTick == -1 ? s.nowTick + 1 : Math.min(endTick, s.nowTick + 1);
	}

	/** Exclusive end tick of a timeline span (whose end is inclusive), cut at now. */
	private static int spanEnd(BarState s, Span sp)
	{
		return sp.endTick == -1 ? s.nowTick + 1 : Math.min(sp.endTick, s.nowTick) + 1;
	}

	/** Draws the parts of a timeline span in the ticks no range in {@code covered} has. */
	private static void clipped(Graphics2D g, BarLayout l, BarState s, Span sp, List<int[]> covered, float y,
		float h, Color c, boolean faint, String label)
	{
		int start = sp.startTick, end = spanEnd(s, sp);
		List<int[]> parts = gaps(start, end, covered);
		for (int i = 0; i < parts.size(); i++)
		{
			int[] seg = parts.get(i);
			float x0 = seg[0] == start ? TimelinePainter.spanStartX(l, s, sp) : TimelinePainter.x(l, s, seg[0]);
			float x1 = seg[1] == end ? TimelinePainter.spanEndX(l, s, sp) : TimelinePainter.x(l, s, seg[1]);
			// Label once, on the newest piece.
			TimelinePainter.pill(g, l, x0, x1, y, h, c, faint, i == parts.size() - 1 ? label : null);
		}
	}

	/** The parts of [from, to) that no range in {@code covered} ([a, b)) overlaps, in order. */
	static List<int[]> gaps(int from, int to, List<int[]> covered)
	{
		List<int[]> out = new ArrayList<>();
		out.add(new int[]{from, to});
		for (int[] c : covered)
		{
			List<int[]> next = new ArrayList<>();
			for (int[] seg : out)
			{
				if (c[1] <= seg[0] || c[0] >= seg[1])
				{
					next.add(seg);
					continue;
				}
				if (c[0] > seg[0])
				{
					next.add(new int[]{seg[0], c[0]});
				}
				if (c[1] < seg[1])
				{
					next.add(new int[]{c[1], seg[1]});
				}
			}
			out = next;
		}
		return out;
	}

	// ---- production ----

	/** Whether an inventory run (a span of {@link InventoryRun#runs()}) has any part in view. */
	public static boolean productionVisible(BarState s, Span sp, int oldest)
	{
		boolean current = sp.endTick == -1;
		return sp.startTick <= s.nowTick && (current || sp.endTick >= oldest);
	}

	private static int[] productionRange(BarState s, Span sp)
	{
		int endTick = sp.endTick == -1 ? s.run.finishTick() : sp.endTick;
		return new int[]{sp.startTick, sp.endTick == -1 && endTick > s.nowTick + 1 ? s.nowTick + 1
			: Math.min(s.nowTick + 1, endTick)};
	}

	/**
	 * One inventory run as a solid pill (the current run reaches the "now" line), with the cooking pot when
	 * {@code icons}; k scales the pot (u for a 14u pill).
	 */
	public static void productionRun(Graphics2D g, BarLayout l, BarState s, Span sp, float y, float h, float k,
		boolean icons)
	{
		InventoryRun run = s.run;
		float u = l.u;
		boolean current = sp.endTick == -1;
		Color c = "Cooking".equals(sp.kind) ? Palette.COOK_PILL : Palette.skill(sp.kind);
		float x0 = TimelinePainter.x(l, s, sp.startTick);
		int endTick = current ? run.finishTick() : sp.endTick;
		float solidX = current && endTick > s.nowTick + 1 ? TimelinePainter.liveX(l)
			: TimelinePainter.x(l, s, Math.min(s.nowTick + 1, endTick));
		float solidEnd = Math.max(x0 + h, solidX);
		g.setColor(c);
		g.fill(Px.round(x0, y, solidEnd - x0, h, h, h));
		if ("Cooking".equals(sp.kind) && icons)
		{
			float ix = Math.max(x0 + 11f * u, l.timelineX + 11f * u);
			if (ix < solidEnd - 6f * u)
			{
				SkillPainter.pot(g, ix, y + h / 2f + 1f * k, k, Palette.PILL_LABEL_SOLID);
			}
		}
	}

	// ---- gathering ----

	public static boolean gatherVisible(BarState s, GatherRun.Pill p, int oldest)
	{
		return p.rolls != 0 && p.startTick <= s.nowTick && (p.endTick == -1 || p.endTick >= oldest);
	}

	/**
	 * One resource's pill with the axe (woodcutting) and a notch per roll when {@code details}; k scales the
	 * axe and the notch insets (u for a 14u pill).
	 */
	public static void gatherPill(Graphics2D g, BarLayout l, BarState s, GatherRun.Pill p, float y, float h,
		float k, boolean details, int oldest)
	{
		float u = l.u;
		boolean wood = WOODCUTTING.equals(p.skill);
		float x0 = TimelinePainter.x(l, s, p.startTick);
		float x1 = p.endTick == -1 ? TimelinePainter.liveX(l) : TimelinePainter.x(l, s, Math.min(p.endTick, s.nowTick + 1));
		g.setColor(wood ? Palette.WC_PILL : Palette.skill(p.skill));
		// No clamp to the timeline's left edge: the clip trims it, and smooth scrolling shifts the timeline.
		g.fill(Px.round(x0, y, Math.max(h, x1 - x0), h, h, h));
		if (!details)
		{
			return;
		}
		float ix = Math.max(x0 + 10f * u, l.timelineX + 10f * u);
		if (wood && ix < x1 - 8f * u)
		{
			SkillPainter.axe(g, ix, y + h / 2f, k, Palette.PILL_LABEL_SOLID);
		}
		g.setColor(Palette.NOTCH);
		int from = Math.max(p.startTick, oldest), to = Math.min(p.endTick == -1 ? s.nowTick : p.endTick, s.nowTick);
		for (int t = from; t <= to; t++)
		{
			TickRecord r = s.timeline.peek(t);
			float x = TimelinePainter.x(l, s, t);
			if (r != null && r.roll && x > ix + 6f * u && x < x1)
			{
				g.fill(Px.round(x - 0.6f * u, y + 3f * k, 1.2f * u, h - 6f * k, 1.2f * u, 1.2f * u));
			}
		}
	}

	// ---- alch ----

	/** One cast's 5-tick pill from tick t (reaching the "now" line while it is still casting). */
	public static void alchCast(Graphics2D g, BarLayout l, BarState s, int t, float y, float h)
	{
		boolean casting = t + AlchTracker.CAST_TICKS > s.nowTick + 1;
		TimelinePainter.pill(g, l, TimelinePainter.x(l, s, t),
			casting ? TimelinePainter.liveX(l) : TimelinePainter.x(l, s, t + AlchTracker.CAST_TICKS),
			y, h, Palette.prayerMagic(), false, null);
	}

	// ---- agility ----

	public static boolean obstacleVisible(BarState s, AgilityCourse.Obstacle o, int since, int oldest)
	{
		return o.startTick > since && o.startTick <= s.nowTick && (o.endTick == -1 || o.endTick >= oldest);
	}

	/** One obstacle from the click to the landing (faint when failed), numbered when {@code numbers}. */
	public static void obstacle(Graphics2D g, BarLayout l, BarState s, AgilityCourse.Obstacle o, float y, float h,
		boolean numbers)
	{
		float u = l.u;
		Color solid = Palette.skill("Agility");
		float x1 = TimelinePainter.x(l, s, o.startTick);
		float x2 = o.endTick == -1 ? TimelinePainter.liveX(l) : TimelinePainter.x(l, s, Math.min(o.endTick, s.nowTick + 1));
		float px0 = x1 + 1f;
		g.setColor(o.failed ? Palette.withAlpha(solid, Palette.FAINT_ALPHA) : solid);
		g.fill(Px.round(px0, y, Math.max(h, x2 - 1f - px0), h, h, h));
		float nx = x1 + 10f * u;
		if (numbers && o.number > 0 && nx < x2 - 6f * u && nx > l.timelineX)
		{
			g.setFont(Fonts.bold(10f * u));
			g.setColor(o.failed ? Palette.PILL_LABEL_FAINT : Palette.PILL_LABEL_SOLID);
			String text = String.valueOf(o.number);
			g.drawString(text, nx - g.getFontMetrics().stringWidth(text) / 2f, y + h / 2f + 4f * u);
		}
	}
}
