package com.superhistogram;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@PluginDescriptor(
	name = "Super Histogram"
)
public class SuperHistogramPlugin extends Plugin
{
	@Override
	protected void startUp() throws Exception
	{
	}

	@Override
	protected void shutDown() throws Exception
	{
	}

	@Provides
	SuperHistogramConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SuperHistogramConfig.class);
	}
}
