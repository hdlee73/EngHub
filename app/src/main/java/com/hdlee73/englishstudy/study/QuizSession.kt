package com.hdlee73.englishstudy.study

/**
 * One run through a list of questions: pick an answer, see whether it was right, move on. Choosing is
 * allowed once per question, so a double tap cannot change or double-count an answer.
 */
class QuizSession(val questions: List<QuizQuestion>) {
    var index: Int = 0
        private set
    /** The choice picked for the current question; null until the learner answers. */
    var selected: Int? = null
        private set
    var correctCount: Int = 0
        private set
    private val wrongQuestions = ArrayList<QuizQuestion>()

    val total: Int get() = questions.size
    val current: QuizQuestion? get() = questions.getOrNull(index)
    val answered: Boolean get() = selected != null
    val finished: Boolean get() = index >= questions.size
    /** Questions answered wrongly so far, in the order they were asked. */
    val wrong: List<QuizQuestion> get() = wrongQuestions.toList()

    /** Records [choice] for the current question. Returns whether it was right, or null when it was ignored. */
    fun choose(choice: Int): Boolean? {
        val question = current ?: return null
        if (selected != null || choice !in question.choices.indices) return null
        selected = choice
        val right = question.isCorrect(choice)
        if (right) correctCount++ else wrongQuestions += question
        return right
    }

    /** Moves to the next question once the current one has been answered. */
    fun next() {
        if (selected == null || finished) return
        index++
        selected = null
    }
}
