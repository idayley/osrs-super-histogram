package com.superhistogram.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import org.junit.Test;

public class XpRowTest
{
	private static Timeline fletch(int... ticks)
	{
		Timeline t = new Timeline(512);
		for (int k : ticks)
		{
			t.at(k).xp.add(new XpDrop("Fletching", 15));
		}
		t.at(200);
		return t;
	}

	@Test
	public void frequentNeedsFiveTicksInTen()
	{
		assertTrue(XpRow.frequentSkills(fletch(191, 193, 195, 197, 199), 200).contains("Fletching"));
		assertFalse(XpRow.frequentSkills(fletch(193, 195, 197, 199), 200).contains("Fletching"));
	}

	@Test
	public void frequencyCountsTicksNotDrops()
	{
		Timeline t = new Timeline(512);
		for (int i = 0; i < 6; i++)
		{
			t.at(199).xp.add(new XpDrop("Fletching", 15));
		}
		t.at(200);
		assertFalse(XpRow.frequentSkills(t, 200).contains("Fletching"));
	}

	@Test
	public void oldDropsDoNotCount()
	{
		assertFalse(XpRow.frequentSkills(fletch(180, 182, 184, 186, 188), 200).contains("Fletching"));
	}
}
