package com.superhistogram.ui;

import java.awt.Font;
import java.io.InputStream;

/**
 * Bar text faces. All text uses the Tiny5 pixel font, snapped to whole multiples of its 8 px design size so
 * it stays sharp when RuneLite's Stretched Mode enlarges the game image. Falls back to a logical font if
 * loading fails.
 */
public final class Fonts
{
	private static final Font PIXEL = load("Tiny5.ttf", Font.PLAIN);
	/** Tiny5's pixel grid: it is only sharp at multiples of this size. */
	static final float PIXEL_PX = 8f;
	/** Requested sizes from here up get the 2x pixel size. */
	private static final float DOUBLE_FROM = 13f;

	private Fonts()
	{
	}

	public static Font regular(float px)
	{
		return pixel(px);
	}

	public static Font semiBold(float px)
	{
		return pixel(px);
	}

	public static Font bold(float px)
	{
		return pixel(px);
	}

	/** Tiny5 at 8 px, or 16 px for requests of {@value #DOUBLE_FROM} px and up. */
	static Font pixel(float px)
	{
		return PIXEL.deriveFont(px >= DOUBLE_FROM ? PIXEL_PX * 2f : PIXEL_PX);
	}

	private static Font load(String file, int fallbackStyle)
	{
		try (InputStream in = Fonts.class.getResourceAsStream("/com/superhistogram/" + file))
		{
			if (in != null)
			{
				return Font.createFont(Font.TRUETYPE_FONT, in);
			}
		}
		catch (Exception e)
		{
			// Fall through to the logical font.
		}
		return new Font(Font.SANS_SERIF, fallbackStyle, 12);
	}
}
