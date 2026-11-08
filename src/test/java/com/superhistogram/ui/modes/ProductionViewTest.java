package com.superhistogram.ui.modes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.superhistogram.model.BarState;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.SampleStates;
import com.superhistogram.ui.SampleStates.ProdPhase;
import org.junit.Test;

public class ProductionViewTest
{
	@Test
	public void runningShowsTimeLeftAndCount()
	{
		BarState s = SampleStates.cooking(ProdPhase.RUNNING);
		assertArrayEquals(new String[]{"0:01", "27 of 28 cooked"}, ProductionView.rightLines(s));
		assertEquals(Palette.WHITE, ProductionView.rightColor(s));
	}

	@Test
	public void doneSaysBankNow()
	{
		BarState s = SampleStates.cooking(ProdPhase.DONE);
		assertArrayEquals(new String[]{"Bank now", "28 of 28 cooked"}, ProductionView.rightLines(s));
		assertEquals(Palette.WHITE, ProductionView.rightColor(s));
	}

	@Test
	public void bankedShowsWastedInMint()
	{
		BarState s = SampleStates.cooking(ProdPhase.BANKED);
		assertArrayEquals(new String[]{"Banking · +3", "28 of 28 cooked"}, ProductionView.rightLines(s));
		assertEquals(Palette.MINT, ProductionView.rightColor(s));
	}

	@Test
	public void bankedOnTimeShowsCheck()
	{
		BarState s = SampleStates.cookingBanked(0, true, true);
		assertEquals("Banking ✓", ProductionView.rightLines(s)[0]);
		assertEquals(Palette.MINT, ProductionView.rightColor(s));
	}

	@Test
	public void timeToFinishOffShowsCountOnly()
	{
		BarState s = SampleStates.cooking(ProdPhase.RUNNING, false, true);
		assertArrayEquals(new String[]{"27 of 28", "cooked"}, ProductionView.rightLines(s));
	}

	@Test
	public void otherSkillsSayDone()
	{
		assertArrayEquals(new String[]{"0:01", "27 of 28 done"},
			ProductionView.rightLines(SampleStates.fletching()));
	}

	@Test
	public void nullRunIsEmpty()
	{
		BarState s = BarState.builder().nowTick(5).build();
		assertArrayEquals(new String[]{"", ""}, ProductionView.rightLines(s));
	}
}
