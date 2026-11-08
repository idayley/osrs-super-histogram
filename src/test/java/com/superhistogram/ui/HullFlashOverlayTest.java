package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HullFlashOverlayTest
{
	@Test
	public void alphaFadesOver500ms()
	{
		assertEquals(1f, HullFlashOverlay.alpha(0), 1e-6);
		assertEquals(0.5f, HullFlashOverlay.alpha(250), 1e-3);
		assertEquals(0f, HullFlashOverlay.alpha(500), 1e-6);
		assertEquals(0f, HullFlashOverlay.alpha(900), 1e-6);
		assertEquals(0f, HullFlashOverlay.alpha(-1), 1e-6);
	}

	@Test
	public void missIsFainterThanHit()
	{
		assertEquals(1f, HullFlashOverlay.opacity(false, 0), 1e-6);
		assertEquals(HullFlashOverlay.MISS_PEAK, HullFlashOverlay.opacity(true, 0), 1e-6);
		assertEquals(HullFlashOverlay.MISS_PEAK / 2f, HullFlashOverlay.opacity(true, 250), 1e-3);
		assertEquals(0f, HullFlashOverlay.opacity(true, 500), 1e-6);
	}
}
