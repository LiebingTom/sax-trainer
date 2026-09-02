package com.example.saxtrainer.core.fingering

/**
 * The keys of a saxophone this app can talk about.
 *
 * Deliberately free of any visual information: where a key sits and how it is drawn belongs to the
 * key layout in the design system, not here. Only keys the fingering charts actually use are listed;
 * the palm and low-pinky clusters join when a chart needs them.
 */
enum class SaxKey {
    /** Octave key, operated by the left thumb. */
    OCTAVE,

    /** Left hand, first finger (B). */
    LH1,

    /** Left hand, second finger (A). */
    LH2,

    /** Left hand, third finger (G). */
    LH3,

    /** Right hand, first finger (F). */
    RH1,

    /** Right hand, second finger (E). */
    RH2,

    /** Right hand, third finger (D). */
    RH3,

    /** Left little finger, low C. */
    LOW_C,

    /** Side key next to the right hand, giving B flat. */
    SIDE_BB,
}
