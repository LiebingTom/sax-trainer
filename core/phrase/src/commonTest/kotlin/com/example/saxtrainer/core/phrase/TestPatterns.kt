package com.example.saxtrainer.core.phrase


/**
 * A scale pattern for tests, so they do not depend on what the bundled library happens to hold.
 *
 * The eight-bar walk that used to ship as "Woche 1 walk": in D major, octave 4, it reads
 * D E F♯ A | B A F♯ E | D E F♯ A | E E F♯ E | ...
 */
internal object TestPatterns {
    const val WALK_ID = "test_walk"

    val walk: PhraseRecipe = parsePatterns(listOf(WALK_JSON)).single()

    val catalog: PatternCatalog = InMemoryPatternCatalog(listOf(walk))
}

private val WALK_JSON = """
{
  "id": "test_walk",
  "name": "Test walk",
  "description": "An eight-bar walk on degrees 1 to 6, for tests only.",
  "trainingBars": 1,
  "bars": [
    {
      "steps": [
        { "degree": 1, "duration": "quarter" },
        { "degree": 2, "duration": "eighth" },
        { "degree": 3, "duration": "eighth" },
        { "degree": 5, "duration": "half" }
      ]
    },
    {
      "steps": [
        { "degree": 6, "duration": "quarter" },
        { "degree": 5, "duration": "eighth" },
        { "degree": 3, "duration": "eighth" },
        { "degree": 2, "duration": "half" }
      ]
    },
    {
      "steps": [
        { "degree": 1, "duration": "quarter" },
        { "degree": 2, "duration": "eighth" },
        { "degree": 3, "duration": "eighth" },
        { "degree": 5, "duration": "half" }
      ]
    },
    {
      "steps": [
        { "degree": 2, "duration": "quarter" },
        { "degree": 2, "duration": "eighth" },
        { "degree": 3, "duration": "eighth" },
        { "degree": 2, "duration": "half" }
      ]
    },
    {
      "steps": [
        { "degree": 1, "duration": "quarter" },
        { "degree": 5, "duration": "eighth" },
        { "degree": 6, "duration": "eighth" },
        { "degree": 1, "duration": "half" }
      ]
    },
    {
      "steps": [
        { "degree": 2, "duration": "quarter" },
        { "degree": 5, "duration": "eighth" },
        { "degree": 6, "duration": "eighth" },
        { "degree": 2, "duration": "half" }
      ]
    },
    {
      "steps": [
        { "degree": 3, "duration": "half" },
        { "degree": 1, "duration": "half" }
      ]
    },
    {
      "steps": [
        { "degree": 2, "duration": "quarter" },
        { "degree": 1, "duration": "eighth" },
        { "degree": 6, "duration": "eighth" },
        { "degree": 1, "duration": "half" }
      ]
    }
  ]
}
""".trimIndent()
