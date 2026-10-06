package com.example.saxtrainer.core.reed

/** A reed maker, under an id that never changes whatever the name on the box becomes. */
data class ReedManufacturer(
    val id: String,
    val name: String,
    val products: List<ReedProduct>,
)

/**
 * One kind of reed a maker sells, and the strengths it comes in.
 *
 * The scale belongs to the product because makers do not share one: half steps, quarter steps,
 * "3M", "Medium Hard". Its order is the product's own, softest first, so strengths can be sorted
 * and compared within a product without anybody parsing them.
 */
data class ReedProduct(
    val id: String,
    val name: String,
    val strengths: List<String>,
)

/**
 * The reeds the logbook offers to choose from.
 *
 * A fixed list for now, the common saxophone reeds. Everything stored refers to it by id, so a
 * player's own makes and models can later be added beside these under ids of their own without a
 * stored reed changing; an id this list does not know is shown as it is rather than lost.
 */
object ReedCatalog {

    val manufacturers: List<ReedManufacturer> = listOf(
        ReedManufacturer(
            id = "daddario",
            name = "D'Addario",
            products = listOf(
                ReedProduct("daddario-royal", "Royal", halfSteps(1.5, 5.0)),
                ReedProduct("daddario-rico", "Rico", halfSteps(1.5, 5.0)),
                ReedProduct("daddario-reserve", "Reserve", halfSteps(2.0, 4.5)),
                ReedProduct("daddario-reserve-classic", "Reserve Classic", halfSteps(2.0, 4.5)),
                ReedProduct("daddario-hemke", "Frederick L. Hemke", halfSteps(2.0, 4.0)),
                ReedProduct("daddario-select-jazz-filed", "Select Jazz Filed", softMediumHard(2..4)),
                ReedProduct("daddario-select-jazz-unfiled", "Select Jazz Unfiled", softMediumHard(2..4)),
                ReedProduct("daddario-la-voz", "La Voz", listOf("S", "MS", "M", "MH", "H")),
            ),
        ),
        ReedManufacturer(
            id = "vandoren",
            name = "Vandoren",
            products = listOf(
                ReedProduct("vandoren-traditional", "Traditional", halfSteps(1.5, 5.0)),
                ReedProduct("vandoren-v16", "V16", halfSteps(1.5, 5.0)),
                ReedProduct("vandoren-java", "Java", halfSteps(1.5, 4.0)),
                ReedProduct("vandoren-java-red", "Java Red", halfSteps(1.5, 4.0)),
                ReedProduct("vandoren-zz", "ZZ", halfSteps(1.5, 4.0)),
                ReedProduct("vandoren-v21", "V21", halfSteps(2.0, 4.0)),
            ),
        ),
        ReedManufacturer(
            id = "legere",
            name = "Légère",
            products = listOf(
                ReedProduct("legere-classic", "Classic", quarterSteps(1.5, 4.0)),
                ReedProduct("legere-signature", "Signature", quarterSteps(1.5, 4.0)),
                ReedProduct("legere-american-cut", "American Cut", quarterSteps(1.5, 4.0)),
                ReedProduct("legere-european-cut", "European Cut", quarterSteps(1.5, 4.0)),
            ),
        ),
        ReedManufacturer(
            id = "rigotti",
            name = "Rigotti",
            products = listOf(
                ReedProduct("rigotti-gold-jazz", "Gold Jazz", lightMediumStrong(halfSteps(2.0, 4.0))),
            ),
        ),
        ReedManufacturer(
            id = "marca",
            name = "Marca",
            products = listOf(
                ReedProduct("marca-jazz", "Jazz", halfSteps(1.5, 4.0)),
                ReedProduct("marca-superieure", "Superieure", halfSteps(1.5, 4.0)),
                ReedProduct("marca-american-vintage", "American Vintage", halfSteps(1.5, 4.0)),
            ),
        ),
    )

    /** What a new reed starts as: the reed the app was first made for. */
    val defaultManufacturer: ReedManufacturer = manufacturers.first()
    val defaultProduct: ReedProduct = defaultManufacturer.products.first()
    const val DEFAULT_STRENGTH = "2.5"

    fun manufacturerOf(id: String): ReedManufacturer? = manufacturers.firstOrNull { it.id == id }

    fun productOf(id: String): ReedProduct? =
        manufacturers.firstNotNullOfOrNull { maker -> maker.products.firstOrNull { it.id == id } }

    /** The maker's name, or the stored id itself when this catalog does not know it. */
    fun manufacturerNameOf(id: String): String = manufacturerOf(id)?.name ?: id

    /** The product's name, or the stored id itself when this catalog does not know it. */
    fun productNameOf(id: String): String = productOf(id)?.name ?: id

    /** How a reed is called in one line: "D'Addario Royal 2.5". */
    fun describe(reed: Reed): String =
        "${manufacturerNameOf(reed.manufacturerId)} ${productNameOf(reed.productId)} ${reed.strength}"

    private fun halfSteps(from: Double, to: Double): List<String> = steps(from, to, 0.5)

    private fun quarterSteps(from: Double, to: Double): List<String> = steps(from, to, 0.25)

    private fun steps(from: Double, to: Double, by: Double): List<String> {
        val count = ((to - from) / by).toInt()
        return (0..count).map { label(from + it * by) }
    }

    /** "2", "2.5", "2.25" - as the box prints it, without a trailing ".0". */
    private fun label(value: Double): String {
        val hundredths = kotlin.math.round(value * 100).toInt()
        val whole = hundredths / 100
        val fraction = hundredths % 100
        return when {
            fraction == 0 -> "$whole"
            fraction % 10 == 0 -> "$whole.${fraction / 10}"
            else -> "$whole.$fraction"
        }
    }

    private fun softMediumHard(numbers: IntRange): List<String> =
        numbers.flatMap { n -> listOf("${n}S", "${n}M", "${n}H") }

    private fun lightMediumStrong(steps: List<String>): List<String> =
        steps.flatMap { s -> listOf("$s Light", "$s Medium", "$s Strong") }
}
