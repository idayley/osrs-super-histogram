package com.superhistogram;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SuperHistogramPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SuperHistogramPlugin.class);
		RuneLite.main(args);
	}
}
