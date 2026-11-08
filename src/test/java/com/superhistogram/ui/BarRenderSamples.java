package com.superhistogram.ui;

import static org.junit.Assume.assumeTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.Test;

/**
 * Writes sample renders for eyeballing against the mockups. Runs only when
 * {@code -PrenderDir=/some/dir} is passed to gradle.
 */
public class BarRenderSamples
{
	@Test
	public void writeSamples() throws IOException
	{
		String dir = System.getProperty("superhistogram.renderDir");
		assumeTrue(dir != null && !dir.isEmpty());
		BarState general = SampleStates.general();
		int[][] sizes = {{1026, 62}, {760, 46}, {620, 32}, {360, 28}};
		for (int[] wh : sizes)
		{
			write(dir, "task8-render-" + wh[0] + "x" + wh[1] + ".png", wh[0], wh[1], general);
		}
		write(dir, "task8-render-afk-1026x62.png", 1026, 62,
			SampleStates.afk(general));
		write(dir, "task8-render-minimal-1026x62.png", 1026, 62,
			SampleStates.withMode(general, Mode.MINIMAL, "XP", "minimal"));
	}

	private static void write(String dir, String name, int w, int h, BarState s) throws IOException
	{
		ImageIO.write(BarRenderSmokeTest.render(w, h, s, null), "png", new File(dir, name));
	}
}
