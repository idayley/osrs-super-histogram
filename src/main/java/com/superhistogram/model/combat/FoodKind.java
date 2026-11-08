package com.superhistogram.model.combat;

public enum FoodKind
{
	NORMAL(3), FAST(2), KARAMBWAN(2), COMBO(5);

	public final int add;

	FoodKind(int add)
	{
		this.add = add;
	}

	/** Karambwan with a normal or fast food in one tick is a combo; otherwise the first food wins. */
	public static FoodKind merge(FoodKind first, FoodKind second)
	{
		if ((first == KARAMBWAN && (second == NORMAL || second == FAST))
			|| (second == KARAMBWAN && (first == NORMAL || first == FAST)))
		{
			return COMBO;
		}
		return first;
	}
}
