package com.superhistogram.model.combat;

import static org.junit.Assert.assertEquals;

import net.runelite.api.ItemID;
import org.junit.Test;

public class FoodsTest
{
	@Test
	public void idsMatchRuneLite()
	{
		assertEquals(ItemID.COOKED_KARAMBWAN, 3144);
		assertEquals(ItemID.HALIBUT, 32336);
		int[] fast = {
			ItemID.HALIBUT,
			ItemID.WORM_CRUNCHIES, ItemID.CHOCCHIP_CRUNCHIES, ItemID.SPICY_CRUNCHIES, ItemID.TOAD_CRUNCHIES,
			ItemID.WORM_BATTA, ItemID.TOAD_BATTA, ItemID.CHEESETOM_BATTA, ItemID.FRUIT_BATTA, ItemID.VEGETABLE_BATTA,
			ItemID.CHOCOLATE_BOMB, ItemID.TANGLED_TOADS_LEGS, ItemID.WORM_HOLE, ItemID.VEG_BALL,
		};
		for (int id : fast)
		{
			assertEquals("id " + id, FoodKind.FAST, Foods.kindOf(id));
		}
		assertEquals(FoodKind.KARAMBWAN, Foods.kindOf(ItemID.COOKED_KARAMBWAN));
	}

	@Test
	public void sharkIsNormal()
	{
		assertEquals(FoodKind.NORMAL, Foods.kindOf(ItemID.SHARK));
	}

	@Test
	public void karambwanAndHalibut()
	{
		assertEquals(FoodKind.KARAMBWAN, Foods.kindOf(3144));
		assertEquals(FoodKind.FAST, Foods.kindOf(32336));
	}

	@Test
	public void mergeMakesCombo()
	{
		assertEquals(FoodKind.COMBO, FoodKind.merge(FoodKind.NORMAL, FoodKind.KARAMBWAN));
		assertEquals(FoodKind.COMBO, FoodKind.merge(FoodKind.KARAMBWAN, FoodKind.FAST));
		assertEquals(FoodKind.NORMAL, FoodKind.merge(FoodKind.NORMAL, FoodKind.NORMAL));
	}
}
