package com.superhistogram.ui;

import java.awt.Font;
import java.io.InputStream;

/** The bundled Outfit faces, with a logical-font fallback if loading fails. */
public final class Fonts
{
	private static final Font REGULAR = load("Outfit-Regular.ttf", Font.PLAIN);
	private static final Font SEMI_BOLD = load("Outfit-SemiBold.ttf", Font.BOLD);
	private static final Font BOLD = load("Outfit-Bold.ttf", Font.BOLD);

	private Fonts()
	{
	}

	public static Font regular(float px)
	{
		return REGULAR.deriveFont(px);
	}

	public static Font semiBold(float px)
	{
		return SEMI_BOLD.deriveFont(px);
	}

	public static Font bold(float px)
	{
		return BOLD.deriveFont(px);
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
