package com.superhistogram.model.combat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * In-game NPC names of OSRS bosses (OSRS Wiki "Boss" page, main, minigame and raid sections),
 * matched case-insensitively. Static set, no network. Quest-only bosses are not included.
 */
public final class Bosses
{
	private static final Set<String> NAMES = new HashSet<>(Arrays.asList(
			"ahrim the blighted",
			"karil the tainted",
			"dharok the wretched",
			"guthan the infested",
			"torag the corrupted",
			"verac the defiled",
			"gemstone crab",
			"scurrius",
			"giant mole",
			"deranged archaeologist",
			"dagannoth supreme",
			"dagannoth rex",
			"dagannoth prime",
			"sarachnis",
			"blood moon",
			"blue moon",
			"eclipse moon",
			"kalphite queen",
			"kree'arra",
			"commander zilyana",
			"general graardor",
			"k'ril tsutsaroth",
			"the hueycoatl",
			"corporeal beast",
			"nex",
			"chaos fanatic",
			"crazy archaeologist",
			"scorpia",
			"king black dragon",
			"chaos elemental",
			"revenant maledictus",
			"calvar'ion",
			"vet'ion",
			"spindel",
			"venenatis",
			"artio",
			"callisto",
			"brutus",
			"demonic brutus",
			"obor",
			"bryophyta",
			"amoxliatl",
			"royal titans",
			"branda the fire queen",
			"eldric the ice king",
			"doom of mokhaiotl",
			"zulrah",
			"vorkath",
			"phantom muspah",
			"maggot king",
			"the nightmare",
			"phosani's nightmare",
			"yama",
			"duke sucellus",
			"the leviathan",
			"the whisperer",
			"vardorvis",
			"the mimic",
			"hespori",
			"skotizo",
			"shellbane gryphon",
			"grotesque guardians",
			"dusk",
			"dawn",
			"abyssal sire",
			"kraken",
			"cerberus",
			"araxxor",
			"thermonuclear smoke devil",
			"alchemical hydra",
			"crystalline hunllef",
			"corrupted hunllef",
			"tztok-jad",
			"jaltok-jad",
			"tzkal-zuk",
			"sol heredit",
			"tempoross",
			"wintertodt",
			"zalcano",
			"tekton",
			"vanguard",
			"vespula",
			"vasa nistirio",
			"muttadile",
			"great olm",
			"the maiden of sugadinti",
			"pestilent bloat",
			"nylocas vasilias",
			"sotetseg",
			"xarpus",
			"verzik vitur",
			"akkha",
			"ba-ba",
			"kephri",
			"zebak",
			"tumeken's warden",
			"elidinis' warden",
			"barrelchest"));

	private Bosses()
	{
	}

	public static boolean isBoss(String npcName)
	{
		return npcName != null && NAMES.contains(npcName.trim().toLowerCase(Locale.ROOT));
	}
}
