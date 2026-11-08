package com.superhistogram.model.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IgnoreListTest
{
	@Test
	public void namesMatchIgnoringCaseSpacesAndColourTags()
	{
		IgnoreList l = IgnoreList.parse(" Giant rat ,Imp");
		assertTrue(l.matches("giant rat"));
		assertTrue(l.matches("<col=ffff00>Giant rat</col>"));
		assertTrue(l.matches("IMP"));
		assertFalse(l.matches("Giant rat king"));
		assertFalse(l.matches("Scurrius"));
		assertFalse(l.matches(null));
	}

	@Test
	public void starMatchesAnything()
	{
		IgnoreList l = IgnoreList.parse("*rat*, dagannoth *");
		assertTrue(l.matches("Giant rat"));
		assertTrue(l.matches("Rat"));
		assertTrue(l.matches("Dagannoth spawn"));
		assertFalse(l.matches("Dagannoth"));
		assertFalse(l.matches("Imp"));
	}

	@Test
	public void emptyListMatchesNothing()
	{
		assertFalse(IgnoreList.parse("").matches("Imp"));
		assertFalse(IgnoreList.parse(null).matches("Imp"));
		assertFalse(IgnoreList.parse(" , ,").matches(""));
	}

	@Test
	public void toggleAddsThenRemovesTheName()
	{
		String once = IgnoreList.toggle("Imp", "<col=ffff00>Giant rat</col>");
		assertEquals("Imp, Giant rat", once);
		assertEquals("Imp", IgnoreList.toggle(once, "giant RAT"));
		assertEquals("Giant rat", IgnoreList.toggle("", "Giant rat"));
	}

	@Test
	public void toggleOffAWildcardMatchAddsNothingAndKeepsThePattern()
	{
		// Un-ignoring something only a wildcard covers can't be expressed; the list is left as written.
		assertEquals("*rat*", IgnoreList.toggle("*rat*", "Giant rat"));
	}
}
