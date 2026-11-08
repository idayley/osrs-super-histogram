package com.superhistogram.ui.modes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import com.superhistogram.model.skill.ThieveRun;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ThieveViewTest
{
	private static final int POUCH = net.runelite.api.gameval.ItemID.PICKPOCKET_COIN_POUCH_KNIGHT;
	private static final List<XpDrop> XP = Collections.singletonList(new XpDrop("Thieving", 84));

	private static BarState knights(int pouches, int successes)
	{
		Timeline tl = new Timeline(512);
		ThieveRun th = new ThieveRun(tl);
		Map<Integer, Integer> inv = new HashMap<>();
		inv.put(POUCH, pouches - successes);
		int t = 0;
		th.onTick(t, Collections.emptyList(), false, new HashMap<>(inv), 27);
		for (int i = 0; i < successes; i++)
		{
			t += 2;
			th.onAttempt(t, "Knight of Ardougne", false);
			tl.at(t);
			th.onTick(t, Collections.emptyList(), false, null, -1);
			inv.merge(POUCH, 1, Integer::sum);
			tl.at(t + 1);
			th.onTick(t + 1, XP, false, new HashMap<>(inv), 27);
		}
		return BarState.builder().timeline(tl).nowTick(t + 1).mode(Mode.THIEVING).thieve(th).build();
	}

	@Test
	public void pouchesToTheCapAndTheSuccessRate()
	{
		String[] lines = ThieveView.rightLines(knights(17, 4));
		assertEquals("17/28 pouches", lines[0]);
		assertTrue(lines[1], lines[1].startsWith("100% · "));
		assertEquals("17/28", ThieveView.headlineFallbacks(knights(17, 4))[0]);
	}

	@Test
	public void atTheCapItSaysToOpenThem()
	{
		BarState s = knights(28, 3);
		assertEquals("Open pouches", ThieveView.rightLines(s)[0]);
		assertEquals("28/28 pouches", ThieveView.rightLines(s)[1]);
		assertEquals("Full", ThieveView.headlineFallbacks(s)[0]);
	}
}
