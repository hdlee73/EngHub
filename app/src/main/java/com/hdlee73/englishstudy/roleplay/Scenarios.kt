package com.hdlee73.englishstudy.roleplay

/**
 * Offline scripted scenarios: the bot follows a queue of natural follow-up prompts and
 * loosely reacts to user keywords. English only model isn't needed; templates keep the
 * conversation going forever, offline and free.
 */
data class Scenario(
    val title: String,
    val emoji: String,
    val opener: String,
    val prompts: List<String>,
    val goal: String = "",
)

object Scenarios {

    val ALL = listOf(
        Scenario(
            "카페 주문", "☕",
            "Hi! Welcome to Daily Grind Coffee. What can I get for you today?",
            listOf(
                "Nice choice! Would you like that hot or iced?",
                "Great. What size would you like? We have small, medium, and large.",
                "Anything to eat with that? Our banana bread is quite popular!",
                "For here or to go?",
                "That'll be five dollars. Would you like to pay with card?",
                "Awesome, thank you! Anything else I can help with?",
                "See you next time, have a lovely day!"
            ),
            goal = "주문하고 사이즈·결제까지 마치기",
        ),
        Scenario(
            "공항 체크인", "✈️",
            "Good morning! May I see your passport and booking number, please?",
            listOf(
                "Thank you. How many bags will you check in today?",
                "I see you're flying to Paris. Window or aisle seat?",
                "Would you prefer an aisle seat with extra legroom for a small fee?",
                "What are the main purposes of your visit — tourism or work?",
                "How long will you be staying?",
                "Here's your boarding pass. Do you have any liquids over 100 ml in your carry-on?",
                "Boarding starts at 9:35. Have a nice flight!"
            ),
            goal = "짐 부치고 좌석 고르고 탑승권 받기",
        ),
        Scenario(
            "면접 연습", "💼",
            "Welcome, thanks for coming in. Could you briefly introduce yourself?",
            listOf(
                "Interesting. What strengths would you bring to our team?",
                "Can you tell me about a challenge you faced and how you solved it?",
                "What made you interested in this position at our company?",
                "Where do you see yourself growing in the next three years?",
                "How do you usually handle tight deadlines and stress?",
                "Do you have any questions about the role or the team?",
                "Great answers, thank you! We'll get back to you within a week."
            ),
            goal = "자기소개부터 마지막 질문까지 답하기",
        ),
        Scenario(
            "소개팅", "💬",
            "Hi! Is this our seat number six? I'm excited to practice small talk with you!",
            listOf(
                "What do you usually do on weekends?",
                "That sounds fun! What kind of music do you listen to these days?",
                "Have you seen any good shows or movies recently?",
                "Do you enjoy traveling? What's the last place you visited?",
                "What are your favorite cafes around here?",
                "This has been a lovely chat. Would you like to meet again sometime?",
                "Terrific! Talk to you soon, take care!"
            ),
            goal = "취미·여행 이야기로 스몰토크 하기",
        ),
        Scenario(
            "호텔 프론트", "🏨",
            "Good evening, welcome to The Bayview. How may I help you?",
            listOf(
                "Certainly. Is this reservation under a company name or private?",
                "We have a deluxe suite upgrade available tonight. Interested?",
                "Breakfast is served from 6:30 to 10:00. Would you like to reserve that now?",
                "Perfect. Is there anything particular you'd like to know about the neighborhood?",
                "Are you happy with your room temperature and the general comfort?",
                "Would you like a wake-up call in the morning?",
                "Understood, have a wonderful stay."
            ),
            goal = "체크인하고 조식·모닝콜 요청하기",
        )
    )

    private val reaction = mapOf(
        listOf("no", "nope", "not") to "No problem at all!",
        listOf("yes", "sure", "of course", "ok", "okay") to "Great, wonderful!",
        listOf("thanks", "thank you") to "You're very welcome!",
        listOf("hello", "hi", "hey") to "Hey there! Nice to meet you.",
        listOf("water") to "Sure, water coming right up!",
        listOf("expensive", "price", "cost") to "I totally understand, let me check our best price.",
    )

    /** True once the partner has said every scripted line. */
    fun isFinished(scenario: Scenario, turn: Int): Boolean = turn >= scenario.prompts.size

    /** Finds the scripted next prompt with a small keyword-conditioned reaction. */
    fun reply(scenario: Scenario, turn: Int, userText: String): String {
        val lower = userText.lowercase().trim()
        val words = " " + lower.replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ") + " "
        // whole words only, so "now" or "nothing" does not count as "no"
        val react = reaction.entries.firstOrNull { entry -> entry.key.any { words.contains(" $it ") } }?.value
        val step = scenario.prompts[turn.coerceIn(0, scenario.prompts.size - 1)]
        return if (react != null) "$react $step" else step
    }
}
