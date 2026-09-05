package com.example.saxtrainer.core.fingering

/**
 * The keys of a saxophone this app can talk about.
 *
 * Deliberately free of any visual information: where a key sits and how it is drawn belongs to the
 * key layout in the design system, not here. [label] is the name the key carries in fingering
 * charts, which is what makes a stored fingering readable next to a printed chart.
 */
enum class SaxKey(val label: String) {
    /** Octave key, operated by the left thumb. */
    OCTAVE("Octave"),

    /** Left hand, first finger. */
    LH1("L1"),

    /** Left hand, second finger. */
    LH2("L2"),

    /** Left hand, third finger. */
    LH3("L3"),

    /** Right hand, first finger. */
    RH1("R1"),

    /** Right hand, second finger. */
    RH2("R2"),

    /** Right hand, third finger. */
    RH3("R3"),

    /** Left little finger, low B flat. */
    LH_LOW_BB("LH-Bb"),

    /** Left little finger, low B. */
    LH_LOW_B("LH-B"),

    /** Left little finger, low C sharp. */
    LH_LOW_CS("LH-C#"),

    /** Left little finger, G sharp. */
    LH_GS("LH-G#"),

    /** Right little finger, low C. */
    RH_C("RH-C"),

    /** Right little finger, E flat. */
    RH_EB("RH-Eb"),

    /** Bis key, between the first and second left-hand keys. */
    BIS("Bis"),

    /** Side key for B flat. */
    SIDE_BB("Side-Bb"),

    /**
     * Side key for C.
     *
     * Part of the instrument and drawn on the diagram, but no fingering in the chart presses it.
     */
    SIDE_C("Side-C"),

    /** Side key for F sharp. */
    SIDE_F_SHARP("Side-F#"),

    /** Side key for E. */
    SIDE_E("Side-E"),

    /** High F sharp key. */
    HIGH_F_SHARP("High-F#"),

    /** Left-hand palm key, D. */
    PALM_D("Palm-D"),

    /** Left-hand palm key, E flat. */
    PALM_EB("Palm-Eb"),

    /** Left-hand palm key, F. */
    PALM_F("Palm-F"),

    /** Front F key, above the first left-hand key. */
    FRONT_F("Front-F"),
    ;

    companion object {
        /** Looks a key up by the name charts use, e.g. "LH-G#". */
        fun byLabel(label: String): SaxKey? = entries.firstOrNull { it.label == label }
    }
}
