package com.example.saxtrainer.core.fingering

/**
 * What tells one fingering of a note apart from the others, in words a player uses.
 *
 * An alternate is named by what it adds to the standard grip - "Side B♭", "Bis", "Front F" - and
 * where it adds one of the special keys, by those alone: the finger that moves along with a side
 * key is part of reaching it, not what the grip is called. An alternate that only takes keys away
 * is named by what it leaves open.
 */

/** The six main finger keys and the octave key: what every grip is built on. */
private val PLAIN_KEYS = setOf(
    SaxKey.OCTAVE,
    SaxKey.LH1,
    SaxKey.LH2,
    SaxKey.LH3,
    SaxKey.RH1,
    SaxKey.RH2,
    SaxKey.RH3,
)

/**
 * The keys [fingering] is known by: the special keys it presses that the standard grip does not,
 * or, where it adds none, every key it adds. Empty for the standard grip itself, and for an
 * alternate that only lifts keys.
 */
fun NoteFingerings.distinguishingKeys(fingering: Fingering): Set<SaxKey> {
    if (fingering == standard) return emptySet()
    val added = fingering.pressedKeys - standard.pressedKeys
    val special = added - PLAIN_KEYS
    return special.ifEmpty { added }
}

/**
 * How [fingering] is captioned: "Standard" for the usual grip, otherwise the keys that set it
 * apart - "Side B♭", "R1" - or, for one that only lifts keys, "without" the ones it leaves open.
 */
fun NoteFingerings.captionOf(fingering: Fingering): String {
    if (fingering == standard) return STANDARD_CAPTION
    val keys = distinguishingKeys(fingering)
    if (keys.isNotEmpty()) return keys.sorted().joinToString(" + ") { it.displayName }
    val lifted = (standard.pressedKeys - fingering.pressedKeys).sorted()
    if (lifted.isEmpty()) return ALTERNATE_CAPTION
    return "without " + lifted.joinToString(" + ") { it.displayName }
}

/** A key's label as it reads on screen: "Side-Bb" becomes "Side B♭", "Side-F#" "Side F♯". */
val SaxKey.displayName: String
    get() = label
        .replace('-', ' ')
        .replace(FLAT_AFTER_LETTER) { "${it.value.first()}♭" }
        .replace('#', '♯')

private val FLAT_AFTER_LETTER = Regex("[A-G]b\\b")

private const val STANDARD_CAPTION = "Standard"
private const val ALTERNATE_CAPTION = "Alternate"
