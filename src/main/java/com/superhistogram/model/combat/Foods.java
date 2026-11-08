package com.superhistogram.model.combat;

/** Classifies food by item ID. IDs are bundled so the model needs no RuneLite import. */
public final class Foods
{
	private static final int KARAMBWAN = 3144;
	private static final int[] FAST = {
		32336,
		2205, 2209, 2213, 2217,
		2253, 2255, 2259, 2277, 2281,
		2185, 2187, 2191, 2195,
	};

	private Foods()
	{
	}

	public static FoodKind kindOf(int itemId)
	{
		if (itemId == KARAMBWAN)
		{
			return FoodKind.KARAMBWAN;
		}
		for (int id : FAST)
		{
			if (id == itemId)
			{
				return FoodKind.FAST;
			}
		}
		return FoodKind.NORMAL;
	}
}
