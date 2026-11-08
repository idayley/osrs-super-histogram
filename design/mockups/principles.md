# Design rules for the tick-histogram bar

Target: one 48 x ~1000 px RuneLite overlay, read in peripheral vision in about half a second, for hours. Current mockup: `bar.html#A` (`bar-A.png`, `zzbar-A.png`).

The governing constraint comes first. The player looks at the monster, not the bar. At a few degrees of eccentricity the eye still detects **luminance contrast, motion/onsets, and coarse position**. It loses **fine detail, small hue differences and anything crowded** (Strasburger et al. 2011; Carter & Carter 1988). A rule that fights this constraint loses, however "Tufte-correct" it is.

---

## The rules

**R1. One question per glance: "was my last beat on time?" Make it the brightest thing on the bar, and the only thing that changes suddenly.**
*Source:* Matthews et al., glanceable peripheral displays (abstract, minimal, one notification level per item); NHTSA 2/12 rule (a single eyes-off glance must stay under 2 s, so a 0.5 s glance carries about one fact).
*Here:* the verdict is the one item read peripherally. Everything else (click positions inside ticks, prayer spans, hits) is for a deliberate look, between kills or after a death. Two reading levels (Tufte's micro/macro): macro = the verdict plus the pip trail; micro = the histogram.

**R2. Encode on-time vs late with luminance and position first. Hue is only a third, redundant channel.**
*Source:* the periphery keeps luminance contrast and motion but loses chromatic discrimination quickly, especially on small marks (Strasburger 2011; Carter & Carter 1988: colours of symbols under 30 arcmin are "less distinct"). About 8% of men are red/green colour-deficient.
*Here:* a late beat must look *different in shape or brightness*, not only redder. For example: an on-time pip is a small dim tick sitting on the baseline; a late pip is a full-height bright block offset to the right by its tick count. Green/red stays, but the bar must still read in greyscale. Test it with a desaturated screenshot.

**R3. Frame ≤ 5% of the ink. Delete the outer border, the inset rim and the verdict box border. The bar is a translucent dark band with no edge.**
*Source:* Tufte, data-ink ratio / erase non-data-ink; FAA AC 25-11B §5.10.3 and AC 25.1329-1C: minimise HUD clutter, because clutter slows interpretation and change detection.
*Here:* the game world is the bar's frame. A soft 50–60% black ground is enough to separate the bar from the grass. Borders around the bar, around the verdict, and around the "F" chip are all frame.

**R4. Tick boundaries use the smallest effective difference: a 1px hairline at about 15% luminance step, or alternating ±4% tint. Not filled cells with gaps.**
*Source:* Tufte, *Visual Explanations*, "The Smallest Effective Difference": make every distinction "as subtle as possible, but still clear and effective"; layering and separation: background < structure < data.
*Here:* the tick grid is structure. It must sit one layer below the click marks. Right now every tick is a dark filled box with a gap, which makes 24 equally loud rectangles. Future ticks get no dashed outlines at all: empty space to the right of "now" already reads as the future.

**R5. Use no more than 4 hues on the whole bar, each with one meaning everywhere: ok (green), miss (red), beat/now (one accent), and prayer (blue). Everything else is a neutral grey at different brightness.**
*Source:* Healey/Treisman: a colour singleton pops out only when few hues compete; adding more colours lowers search performance (Gong et al. 2016). Ware: roughly 5–8 nominal colours are the practical ceiling, and far fewer in peripheral, small-mark conditions.
*Here:* A uses about 11 hues: yellow title/F/now, orange beat, green and red tick fills, green and red pips, cyan prayer, white on, beige off, blue block, red damage, grey attack, pink eat, plus a red-bordered verdict. Collapse attack, eat and equip clicks to neutral grey marks that differ by **shape or height**. Merge yellow and orange into one accent.

**R6. Never make an important mark depend on a conjunction of two features.**
*Source:* Treisman's feature integration theory; Healey, Booth & Enns 1996: a target defined by a conjunction (e.g. red AND diamond) needs serial search and doesn't pop out.
*Here:* "red diamond = took damage" vs "blue diamond = blocked" vs "red tick fill = late beat" vs "red pip = late beat" mixes red across two meanings and diamonds across two meanings. Give each important event a **unique single feature**. For example, damage is the only thing that breaks the top edge of the bar; a late beat is the only bright red block.

**R7. Quantities go on position along the common axis: click time inside the tick, and slip in ticks. Never on colour, area or a text number alone.**
*Source:* Cleveland & McGill 1984; Mackinlay 1986; Munzner 2014: position on a common scale is the most accurate channel, then length; colour saturation is the least accurate.
*Here:* the bar's x-axis already *is* time, which is its strength. The "283ms before" text duplicates what the click position shows, so it is micro-level detail, not the headline. A late beat should be drawn as a **length**: a bar spanning from the beat line to the actual click, 1 tick long or 2 ticks long. That way "how late" is read as length, peripherally, without reading digits.

**R8. Reading text is foveal-only. The verdict word must be one short token readable at a glance (≤ 6 characters, ≥ 14px, high-contrast). Explanatory subtitles go.**
*Source:* peripheral crowding (Bouma: flankers within about half the eccentricity interfere, so letters in a word crowd each other away from the fovea); NHTSA (2012 proposal) discourages more than 30 characters and auto-scrolling text for glance tasks.
*Here:* "LATE · 1 TICK" + "on came after your beat" is 37 characters in a decorative pixel font. Use `LATE +1` / `OK −283` (or just `+1` / `✓` whose *shape* differs), in a clean bold face with tabular digits. Drop the subtitle; the user learns the meaning once.

**R9. Allow exactly one onset per event that matters. Everything else changes gradually or scrolls.**
*Source:* change blindness (Rensink, O'Regan & Clark 1997; Simons & Rensink 2005): changes are missed unless they carry a local transient; abrupt onsets capture attention (Yantis 1993; Scholl 2000). The corollary is that every transient you add competes with the one you need.
*Here:* the scroll itself is constant motion, and the visual system adapts to steady motion. A missed beat should produce one brief luminance flash (≈150–250 ms) at the verdict position. On-time beats should **not** flash; silence is the success signal. Hits on the player already have the game's hitsplat; on the bar they can be quiet marks.

**R10. Give marks room: spacing between distinct marks inside a tick ≥ the mark's own size; at most 3 mark types per tick lane.**
*Source:* crowding (Strasburger 2020; Bouma 1970): critical spacing grows with eccentricity, and crowded marks merge into texture; Tufte's "1 + 1 = 3": adjacent marks create unintended visual effects.
*Here:* in a 30px track, A stacks an orange top cap, an orange left line, a cyan band, a white tee, a beige stub, a blue diamond and grey dots in the same 40px cell. Split the 48px into lanes: top 8px = beat pips/hits, middle 24px = clicks, bottom 4px = prayer-up line. Then no lane holds more than one mark family.

**R11. Prayer state is a thin baseline line, not a band through the middle of the clicks.**
*Source:* layering and separation (Tufte); Munzner, separable channels: put a different attribute in its own spatial region rather than overlaying it.
*Here:* "prayer was up from t₁ to t₂" is an interval, so it reads best as a line segment on its own lane (2–3px, just under the clicks), like SF6's frame meter puts each player's state on its own strip. The cyan block currently covers the click marks it's meant to explain.

**R12. Use colour for a few categories at this size, and icons only where they are ≥ 16px and ever-present. Enemy identity is shown by position or the game, not a 9px icon.**
*Source:* Lindberg & Näsänen 2003: search time rises sharply for icons under ~0.7°; Satcharoen 2017: degrades below 48px; Carter & Carter 1988: hue identity also fails under 30′, so neither works for tiny marks in the periphery; shape is the robust channel at tiny sizes.
*Here:* a ~12px enemy icon inside a 48px bar is unreadable outside the fovea and adds crowding. Show enemy identity only on hover, or as a 1-letter or shape code on the hit marker in the micro layer. If the player fights one monster type at a time, drop it.

**R13. The pip trail is a sparkline: word-sized, no boxes, no outlines, and it lives next to the verdict.**
*Source:* Tufte, sparklines ("small, intense, simple, word-sized graphic with typographic resolution"); small multiples (same encoding repeated so the eye reads change, not form).
*Here:* the last 8–12 beats as a row of identical ticks (ok = short dim stroke, miss = tall bright stroke, shape + luminance per R2), right next to the verdict. It answers "am I getting better?" in one glance. The pips in A float above each beat column with black outlines and scroll away with the ticks, so the trail is never in one place.

**R14. The bar must look calm in steady state: nothing in it should be as bright as the game's own HUD orbs unless something just went wrong.**
*Source:* ambient display heuristics (Mankoff et al. 2003: "peripherality of display", "aesthetic and pleasing"); hours-long use: Matthews' "distraction" evaluation criterion.
*Here:* the dominant state when the player is doing well is a dim neutral bar with a quiet green pip being added every beat. Brightness and red are reserved for misses, so the display carries information by *exception*.

---

## Critique of the current bar A against the rules

| Rule | What A does | Verdict |
|---|---|---|
| R1 one question | Title, setting, F chip, histogram, "your beat in 2", pips and verdict all compete at similar weight | Fails: no primary item |
| R2 luminance not hue | On-time vs late is green vs red tint on tick cells, green vs red pips, same shape and size | Fails in greyscale and for colour-deficient players |
| R3 frame | Outer border + inner rim; verdict has its own red border; F chip boxed; future cells dashed | About 25–30% of ink is frame |
| R4 smallest difference | Each tick is a filled dark box with a gap, plus orange caps and lines on beat ticks | Grid shouts at the same level as data |
| R5 ≤ 4 hues | About 11 hues; yellow and orange both mean "beat/now"; red means damage, late tick and late pip | Fails |
| R6 no conjunctions | Diamonds = hits in two colours; red = 3 meanings | Fails |
| R7 position for quantity | Click x-position is good. Lateness is a colour fill on a whole tick, not a length | Half |
| R8 short text | 37 chars, 2 lines, pixel font (the code even notes "2 reads as 8") | Fails |
| R9 one onset | Not specified; verdict box is always loud red/white | Unclear: the always-loud box gives no onset contrast |
| R10 spacing | 6–7 mark types overlap in a 30px track | Fails |
| R11 prayer lane | Cyan band through the middle of the click marks | Fails |
| R12 icons | Not yet drawn; the plan to add enemy icons would worsen R10 | Risk |
| R13 sparkline trail | Pips scroll with ticks and leave; outlined boxes | Half |
| R14 calm | Bright yellow title, orange accents, red-bordered verdict at all times | Fails |

Why it looks "clunky": every layer (frame, tick grid, beat markers, prayer band, clicks, hits, labels) is drawn at the same contrast. That produces a stripe of equally loud boxes with nothing for the eye to land on. The fix is mostly *subtraction and layering*, not new features.

---

## How rhythm-game and fighting-game timing displays solve the same problem

**osu! hit-error bar.** A thin horizontal strip centred under the playfield. The centre is "perfect"; left is early, right is late; the hit-window zones are faint coloured bands (300/100/50). Each hit draws a short vertical tick at its offset, and **ticks fade out over a few seconds**. A small arrow shows the running average. What makes it work: (1) quantity on position along one axis (R7); (2) the structure (zones) is faint and the data (ticks) is bright (R4); (3) history is shown by **fading in place**, not scrolling away, so the distribution builds up as a smear you can read peripherally ("my ticks cluster right = I'm late") (R13); (4) no text during play. UR and mean error are on the results screen, which is the micro layer (R1). Sources: osu! wiki "Accuracy"; osu! forum bug 347919.

**Etterna / StepMania.** A big single judgement word at a fixed spot ("Perfect", "Great"), each word with a distinct *length and shape* as well as colour, plus an optional error bar. The "EWMA" error bar mode shows only a moving average as one bar, which is the purest glanceable form. Two-wide judgement graphics add a dot on the left or right for early/late: one bit added by **position**, not by more text. Sources: Etterna wiki Options Guide; Etterna fallback theme `Player judgment/default.lua`.

**DDR-style judgements.** The judgement word appears with a brief scale "pop" (a single onset, R9), then holds and fades. Good judgements are calmer; misses are distinct. The combo counter is the sparkline equivalent: it encodes your streak as a number that resets on a miss.

**Street Fighter 6 frame meter.** Two thin horizontal strips (one per player), one cell per frame, each state a flat colour (startup, active, recovery, stun). A single number above (+3/−5) is the summary. What works: each actor on its own lane (R11), the cell grid is drawn by tiny gaps rather than borders (R4), and a single short signed number is the verdict (R8). It resets from the left after a pause rather than scrolling forever. Sources: Infil's SF6 beta notes; TheGamer frame-meter guide.

**WoW cast bars and swing timers.** One filling bar per timer; the latency/spell-queue window is a **differently shaded segment at the end of the bar** (Castbars addon "Queue Zone"; WeakAuras latency overlay). The "when should I press" information is a region on the same axis as time, not text. This matches the "your beat" marker: show the *window* as a faint region where the next press should land, and remove the words "your beat in 2".

**Common pattern:** time on x; faint structure; bright data marks; one short verdict token at a fixed position; history either fading in place (osu!) or summarised as a tiny trail/number; all explanatory text moved to a results screen.

---

## Sources

- Tufte, *The Visual Display of Quantitative Information* (1983): data-ink, chartjunk. Summary: [Georgia Tech CS7450 notes](https://faculty.cc.gatech.edu/~stasko/7450/16/Notes/tufte.pdf), [UMich "Gospel according to Tufte"](https://websites.umich.edu/~jpboyd/eng403_chap2_tuftegospel.pdf)
- Tufte, *Visual Explanations* (1997), "The Smallest Effective Difference": [Casselman review](https://personal.math.ubc.ca/~cass/graphics/tufte/review.pdf)
- Tufte, *Envisioning Information* (1990): layering and separation, small multiples, micro/macro.
- Tufte, sparklines: [Sparkline theory and practice](https://www.edwardtufte.com/notebook/sparkline-theory-and-practice-edward-tufte/)
- Healey, [Perception in Visualization](https://www.csc2.ncsu.edu/faculty/healey/PP/); Healey, Booth & Enns 1996, [High-Speed Visual Estimation Using Preattentive Processing](https://dl.acm.org/doi/pdf/10.1145/230562.230563)
- Treisman & Gelade 1980, feature integration theory (via Healey above). Ware, *Information Visualization: Perception for Design* (colour-category ceiling; from the book, not fetched).
- Cleveland & McGill 1984, [Graphical Perception](http://snoid.sv.vt.edu/~npolys/projects/safas/science.pdf); Mackinlay 1986; Munzner, *Visualization Analysis & Design* (2014), channel effectiveness (book, not fetched).
- Strasburger, Rentschler & Jüttner 2011, [Peripheral vision and pattern recognition: a review](https://jov.arvojournals.org/article.aspx?articleid=2191825); Strasburger 2020, [Seven myths on crowding](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7238452/)
- Rensink, O'Regan & Clark 1997/2000, [change blindness](https://www2.psych.ubc.ca/~rensink/publications/download/VisCog_00.08.pdf); Scholl 2000, [onsets reduce change blindness](https://perception.yale.edu/papers/00-Scholl-VisCog.pdf)
- Matthews et al., [Evaluating Peripheral Displays](https://faculty.washington.edu/garyhs/docs/matthews-BOOK-evaluation.pdf); [Peripheral Displays Toolkit](http://www.madpickle.net/scott/pubs/p321-matthews.pdf); Pousman & Stasko 2006, [ambient taxonomy](https://faculty.cc.gatech.edu/~stasko/papers/avi06.pdf)
- NHTSA visual-manual distraction guidelines, [78 FR 24818 (2013)](https://www.federalregister.gov/documents/2013/04/26/2013-09883/visual-manual-nhtsa-driver-distraction-guidelines-for-in-vehicle-electronic-devices)
- FAA [AC 25-11B](https://www.faa.gov/documentlibrary/media/advisory_circular/ac_25-11b.pdf) §5.10.3; [AC 25.1329-1C](https://www.faa.gov/documentLibrary/media/Advisory_Circular/AC_25_1329-1C.pdf) (HUD clutter)
- Carter & Carter 1988, [Colour coding for rapid location of small symbols](https://doi.org/10.1002/col.5080130407); Lindberg & Näsänen 2003 icon size (via [Yan et al. 2023](https://onlinelibrary.wiley.com/doi/full/10.1002/eng2.12577)); [Gong et al. colour count](https://www.researchgate.net/publication/309922096_Eye_movement_study_on_color_effects_to_icon_visual_search_efficiency)
- osu!: [Accuracy wiki](https://osu.ppy.sh/wiki/en/Gameplay/Accuracy), [hit-error bar colours](https://osu.ppy.sh/community/forums/topics/347919). Etterna: [Options Guide](https://wiki.etternaonline.com/doku.php?id=optionsguide), [judgment theme code](https://github.com/etternagame/etterna/blob/master/Themes/_fallback/Graphics/Player%20judgment/default.lua). SF6: [Infil beta notes](https://words.infil.net/w03-sf6beta-p6.html), [TheGamer frame meter](https://www.thegamer.com/street-fighter-6-sfvi-frame-meter-explained-tips-tricks-guide/). WoW: [Castbars addon](https://www.curseforge.com/wow/addons/castbars).
