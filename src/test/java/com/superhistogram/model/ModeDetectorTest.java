package com.superhistogram.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ModeDetectorTest
{
	private final ModeDetector detector = new ModeDetector();

	@Test
	public void afkBeatsBothBaselines()
	{
		assertEquals(Mode.AFK, detector.detect(true, BaselineSetting.GENERAL));
		assertEquals(Mode.AFK, detector.detect(true, BaselineSetting.MINIMAL));
	}

	@Test
	public void minimalBaselineGivesMinimal()
	{
		assertEquals(Mode.MINIMAL, detector.detect(false, BaselineSetting.MINIMAL));
	}

	@Test
	public void generalBaselineGivesGeneral()
	{
		assertEquals(Mode.GENERAL, detector.detect(false, BaselineSetting.GENERAL));
	}
}
