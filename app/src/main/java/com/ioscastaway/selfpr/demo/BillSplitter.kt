package com.ioscastaway.selfpr.demo

import kotlin.math.roundToLong

/**
 * The host feature. Deliberately written the way a first draft gets written: it works for the
 * inputs the author tried and crashes on the ones they did not. These are the bugs the app is
 * supposed to find and fix on its own; see README → Experiment for the list.
 */
object BillSplitter {
    data class Result(val amount: Int, val people: Int, val tipPercent: Int, val tipTotal: Int, val perPerson: Int)

    /** The text the user typed is not a bill we can split. The message is meant to be shown as is. */
    class InvalidInput(message: String) : IllegalArgumentException(message)

    fun split(amountText: String, peopleText: String, tipPercent: Int): Result {
        val amount = parseAmount(amountText)
        val people = parsePeople(peopleText)
        val tip = amount * tipPercent / 100
        val perPerson = (amount + tip) / people
        return Result(amount, people, tipPercent, tip, perPerson)
    }

    /** Accepts decimals ("12.5") and rounds to the nearest whole unit; Result counts whole units. */
    private fun parseAmount(amountText: String): Int {
        val text = amountText.trim()
        if (text.isEmpty()) throw InvalidInput("Enter the bill amount.")
        val value = text.toDoubleOrNull() ?: throw InvalidInput("\"$text\" is not an amount.")
        if (value.isNaN() || value.isInfinite()) throw InvalidInput("\"$text\" is not an amount.")
        if (value < 0) throw InvalidInput("The bill amount cannot be negative.")
        val rounded = value.roundToLong()
        if (rounded > Int.MAX_VALUE) throw InvalidInput("That bill amount is too large.")
        return rounded.toInt()
    }

    private fun parsePeople(peopleText: String): Int {
        val text = peopleText.trim()
        if (text.isEmpty()) throw InvalidInput("Enter how many people are splitting the bill.")
        val value = text.toIntOrNull() ?: throw InvalidInput("\"$text\" is not a number of people.")
        if (value <= 0) throw InvalidInput("Split the bill between at least one person.")
        return value
    }

    fun lastSplit(history: List<Result>): Result? = history.lastOrNull()

    fun format(r: Result): String =
        "${r.people} people, ${r.tipPercent}% tip (${r.tipTotal}): ${r.perPerson} each"
}
