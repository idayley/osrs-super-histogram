package com.superhistogram.model.combat;

import static org.junit.Assert.assertEquals;

import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.combat.FlickClassifier.Kind;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class FlickClassifierTest
{
	private final FlickClassifier f = new FlickClassifier();

	private static List<Float> c(float... v)
	{
		Float[] a = new Float[v.length];
		for (int i = 0; i < v.length; i++)
		{
			a[i] = v[i];
		}
		return Arrays.asList(a);
	}

	@Test
	public void twoClicksSameTickIsOk()
	{
		assertEquals(Kind.OK, f.classify(c(.3f, .35f), true));
		assertEquals(.3f, f.phase(), 0.0001f);
	}

	@Test
	public void oddClickIsSplitAndCarries()
	{
		assertEquals(Kind.SPLIT, f.classify(c(.92f), false));
		assertEquals(.92f, f.phase(), 0.0001f);
		assertEquals(Kind.OK, f.classify(c(.05f, .3f, .35f), true));
		assertEquals(.3f, f.phase(), 0.0001f);
	}

	@Test
	public void carryOnlyTickIsNone()
	{
		f.classify(c(.92f), false);
		assertEquals(Kind.NONE, f.classify(c(.05f), true));
	}

	@Test
	public void noClicksPrayerOnIsDrain()
	{
		assertEquals(Kind.DRAIN, f.classify(Collections.<Float>emptyList(), true));
	}

	@Test
	public void noClicksAfterFlickIsSkip()
	{
		f.classify(c(.3f, .35f), true);
		assertEquals(Kind.SKIP, f.classify(Collections.<Float>emptyList(), false));
		assertEquals(Kind.NONE, f.classify(Collections.<Float>emptyList(), false));
	}

	@Test
	public void idleIsNone()
	{
		assertEquals(Kind.NONE, f.classify(Collections.<Float>emptyList(), false));
	}

	@Test
	public void streakCountsCleanFlicks()
	{
		f.classify(c(.3f, .35f), true);
		f.classify(c(.3f, .35f), true);
		f.classify(c(.9f), false);
		f.classify(c(.05f, .3f, .35f), true);
		f.classify(c(.3f, .35f), true);
		assertEquals(2, f.streak());
		assertEquals(Kind.OK, f.last());
		f.reset();
		assertEquals(0, f.streak());
		assertEquals(Kind.NONE, f.last());
	}

	@Test
	public void histogramBinsPhases()
	{
		Timeline t = new Timeline(512);
		put(t, 10, Kind.OK, .30f);
		put(t, 11, Kind.OK, .32f);
		put(t, 12, Kind.SPLIT, .95f);
		put(t, 5, Kind.OK, .5f);
		int[] h = FlickClassifier.histogram(t, 10, 12);
		assertEquals(FlickClassifier.HIST_BINS, h.length);
		assertEquals(2, h[3]);
		assertEquals(1, h[11]);
		int total = 0;
		for (int n : h)
		{
			total += n;
		}
		assertEquals(3, total);
	}

	private static void put(Timeline t, int tick, Kind k, float phase)
	{
		TickRecord r = t.at(tick);
		r.flick = k;
		r.flickPhase = phase;
	}
}
