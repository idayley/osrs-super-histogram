package com.superhistogram.model;

public final class Hit
{
	public final int amount;
	/** True for a 0 or a miss. */
	public final boolean blocked;

	public Hit(int amount, boolean blocked)
	{
		this.amount = amount;
		this.blocked = blocked;
	}
}
