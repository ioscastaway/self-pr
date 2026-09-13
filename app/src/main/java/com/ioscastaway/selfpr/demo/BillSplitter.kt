package com.ioscastaway.selfpr.demo

/**
 * The host feature. Deliberately written the way a first draft gets written: it works for the
 * inputs the author tried and crashes on the ones they did not. These are the bugs the app is
 * supposed to find and fix on its own; see README → Experiment for the list.
 */
object BillSplitter {
    data class Result(val amount: Int, val people: Int, val tipPercent: Int, val tipTotal: Int, val perPerson: Int)

    fun split(amountText: String, peopleText: String, tipPercent: Int): Result {
        val amount = amountText.trim().toInt()
        val people = peopleText.trim().toInt()
        val tip = amount * tipPercent / 100
        val perPerson = (amount + tip) / people
        return Result(amount, people, tipPercent, tip, perPerson)
    }

    /** Null when nothing has been split yet; an empty history is not an error. */
    fun lastSplit(history: List<Result>): Result? = history.lastOrNull()

    fun format(r: Result): String =
        "${r.people} people, ${r.tipPercent}% tip (${r.tipTotal}): ${r.perPerson} each"
}
