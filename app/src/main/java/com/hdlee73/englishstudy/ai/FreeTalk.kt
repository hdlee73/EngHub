package com.hdlee73.englishstudy.ai

import java.util.Locale
import kotlin.random.Random

/**
 * 프리토킹: open conversation without a fixed topic.
 * Gemini Nano (Prompt API) writes the partner's replies when the device supports it; otherwise
 * [SmallTalk] keeps the conversation going fully offline.
 */
object FreeTalk {

    data class Line(val fromUser: Boolean, val text: String)

    data class Reply(val text: String, val usedModel: Boolean)

    suspend fun reply(history: List<Line>): Reply {
        if (GenAi.canChat()) {
            GenAi.generate(buildPrompt(history))?.let { cleanModelReply(it) }?.takeIf { it.isNotBlank() }?.let {
                return Reply(it, true)
            }
        }
        return Reply(SmallTalk.reply(history), false)
    }

    fun opener(random: Random = Random.Default): String = SmallTalk.OPENERS.random(random)

    /**
     * Gentle feedback on what the learner said: a more natural version when the sentence needed
     * more than capitalisation or punctuation changes, otherwise null.
     */
    suspend fun suggestion(userText: String): String? {
        val fixed = GenAi.proofread(userText) ?: RuleEngine.fix(userText)
        return fixed.takeIf { meaningfullyDifferent(userText, it) }
    }

    fun meaningfullyDifferent(a: String, b: String): Boolean {
        fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        return norm(a) != norm(b)
    }

    internal fun buildPrompt(history: List<Line>): String = buildString {
        append("You are Lingo, a warm and curious English conversation partner for a Korean adult learner. ")
        append("Chat naturally about anything the learner brings up, like a friend would. ")
        append("Reply in 1 to 3 short sentences of simple, natural spoken English. ")
        append("Share a small thought of your own, then ask one follow-up question. ")
        append("Never correct grammar, never use Korean, never use lists or emojis.\n\n")
        for (line in history.takeLast(12)) {
            append(if (line.fromUser) "Learner: " else "Lingo: ").append(line.text.trim()).append('\n')
        }
        append("Lingo:")
    }

    internal fun cleanModelReply(raw: String): String =
        raw.trim().removePrefix("Lingo:").trim()
            .substringBefore("\nLearner:").substringBefore("Learner:")
            .lines().joinToString(" ") { it.trim() }.trim().trim('"')
}

/** Offline conversation partner: topic-aware reactions plus follow-up questions that never repeat. */
object SmallTalk {

    val OPENERS = listOf(
        "Hey! How's your day going so far?",
        "Hi there! What have you been up to today?",
        "Hello! Anything interesting happen today?",
        "Hey, good to see you! How are you feeling right now?",
        "Hi! What's on your mind today?",
    )

    private class Topic(val keywords: List<String>, val reactions: List<String>, val questions: List<String>)

    private val topics = listOf(
        Topic(
            listOf("work", "worked", "working", "job", "office", "boss", "meeting", "company", "coworker", "colleague", "project", "deadline"),
            listOf("Work can be a lot sometimes.", "That sounds like a busy day at work.", "I see, work stuff."),
            listOf("What do you do for work?", "What's the best part of your job?", "How do you relax after work?", "Do you get along with your coworkers?"),
        ),
        Topic(
            listOf("study", "studied", "studying", "school", "exam", "test", "class", "homework", "university", "college", "english", "learn"),
            listOf("Good for you, studying takes effort!", "Learning something new is always exciting.", "That sounds challenging."),
            listOf("What are you studying these days?", "How do you usually study English?", "What's the hardest part for you?", "Why did you decide to learn that?"),
        ),
        Topic(
            listOf("eat", "ate", "food", "lunch", "dinner", "breakfast", "cook", "restaurant", "delicious", "hungry", "pizza", "chicken", "kimchi", "noodle", "ramen"),
            listOf("Yum, that sounds delicious!", "Now I'm getting hungry!", "Food always makes the day better."),
            listOf("What's your favorite food?", "Do you like cooking at home?", "What did you have for lunch today?", "Is there a restaurant you'd recommend?"),
        ),
        Topic(
            listOf("travel", "trip", "vacation", "holiday", "fly", "flight", "visit", "visited", "abroad", "beach", "busan", "jeju", "japan", "europe"),
            listOf("Oh, I love talking about travel!", "That sounds like a great trip.", "Traveling is the best way to recharge."),
            listOf("Where would you like to go next?", "What was the best place you've ever visited?", "Do you prefer the beach or the mountains?", "Who do you usually travel with?"),
        ),
        Topic(
            listOf("movie", "movies", "film", "drama", "show", "netflix", "series", "watch", "watched", "watching", "actor"),
            listOf("Oh, nice! I love a good story.", "That sounds fun to watch."),
            listOf("What was it about?", "Would you recommend it?", "What kind of movies do you like?", "Who is your favorite actor?"),
        ),
        Topic(
            listOf("music", "song", "singer", "concert", "kpop", "k-pop", "listen", "band", "guitar", "piano"),
            listOf("Music makes everything better!", "Nice, I love music too."),
            listOf("What kind of music do you listen to?", "Who's your favorite singer?", "Do you play any instruments?", "Have you been to a concert recently?"),
        ),
        Topic(
            listOf("exercise", "gym", "run", "running", "soccer", "football", "baseball", "tennis", "golf", "yoga", "hike", "hiking", "swim", "workout", "walk"),
            listOf("That's a healthy habit!", "Wow, you're active!", "Exercise feels great afterwards, doesn't it?"),
            listOf("How often do you work out?", "What sport do you enjoy the most?", "Do you exercise alone or with friends?"),
        ),
        Topic(
            listOf("weather", "rain", "raining", "snow", "cold", "hot", "sunny", "cloudy", "windy", "warm"),
            listOf("Weather really changes my mood.", "Oh, I see. Weather can be so unpredictable."),
            listOf("What's your favorite season?", "What do you like to do on rainy days?", "Is it usually like this where you live?"),
        ),
        Topic(
            listOf("weekend", "saturday", "sunday", "plan", "plans", "tomorrow", "tonight", "free time"),
            listOf("Sounds like a good plan.", "Weekends are the best."),
            listOf("What are your plans for the weekend?", "What's your ideal way to spend a day off?", "Did you do anything fun last weekend?"),
        ),
        Topic(
            listOf("family", "mom", "dad", "mother", "father", "wife", "husband", "son", "daughter", "kids", "children", "brother", "sister", "parents"),
            listOf("Family is so important.", "That's sweet."),
            listOf("Tell me about your family.", "What do you like to do together?", "Do you live close to your family?"),
        ),
        Topic(
            listOf("dog", "cat", "pet", "puppy", "kitten"),
            listOf("Aww, I love animals!", "Pets make life so much happier."),
            listOf("What's your pet's name?", "Do you have any pets?", "Are you more of a dog person or a cat person?"),
        ),
        Topic(
            listOf("book", "read", "reading", "novel", "game", "games", "hobby", "draw", "drawing", "photo", "camera"),
            listOf("That sounds like a fun hobby.", "Nice, it's great to have something you enjoy."),
            listOf("How did you get into that?", "How much time do you spend on it?", "What do you like most about it?"),
        ),
        Topic(
            listOf("coffee", "cafe", "tea", "latte", "americano"),
            listOf("A good coffee can save the day!", "Cafes are such cozy places."),
            listOf("How do you take your coffee?", "Do you have a favorite cafe?", "How many cups do you drink a day?"),
        ),
        Topic(
            listOf("shopping", "bought", "buy", "clothes", "shoes", "mall"),
            listOf("Ooh, shopping!", "Nice, a little shopping is fun."),
            listOf("What did you get?", "Do you prefer shopping online or in stores?"),
        ),
    )

    private val feelings = listOf(
        listOf("tired", "exhausted", "sleepy") to "Oh no, it sounds like you need some rest.",
        listOf("stressed", "stress", "busy", "worried") to "That sounds stressful. I hope things calm down soon.",
        listOf("sad", "upset", "lonely", "bad", "terrible", "awful") to "I'm sorry to hear that.",
        listOf("happy", "great", "good", "fine", "excited", "awesome", "fantastic", "nice", "wonderful") to "That's great to hear!",
        listOf("bored", "boring") to "Being bored is the worst!",
    )

    private val selfAnswers = listOf(
        listOf("your name", "who are you") to "I'm Lingo, your English chat buddy who lives in your phone.",
        listOf("how are you", "how's it going", "how are things", "how about you", "what about you", "and you") to
            "I'm doing great, thanks for asking! Talking with you is the best part of my day.",
        listOf("do you like", "your favorite", "favourite") to "Hmm, I really like learning about people's daily lives, and I'm a big fan of travel stories.",
        listOf("where do you live", "where are you from") to "I live right here in your phone, so I go wherever you go!",
    )

    private val genericQuestions = listOf(
        "Tell me more about that.",
        "How did that make you feel?",
        "What happened next?",
        "Why do you think so?",
        "Was that the first time?",
        "What do you usually do in that situation?",
    )

    private val shortFollowUps = listOf(
        "Could you tell me a little more?",
        "Oh really? Why is that?",
        "Hmm, can you say a bit more about it?",
    )

    private val topicSwitches = listOf(
        "By the way, what do you usually do on weekends?",
        "Can I ask you something? What's your favorite food?",
        "Let's switch it up. If you could travel anywhere, where would you go?",
        "Random question: what was the best part of your week?",
        "Here's one: what's a hobby you'd like to try someday?",
    )

    fun reply(history: List<FreeTalk.Line>, random: Random = Random.Default): String {
        val user = history.lastOrNull { it.fromUser }?.text?.trim().orEmpty()
        val said = history.filter { !it.fromUser }.map { it.text }.toSet()
        fun fresh(options: List<String>) = options.filter { o -> said.none { it.contains(o) } }
        fun pick(options: List<String>) = fresh(options).ifEmpty { options }.random(random)

        val lower = " " + user.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim() + " "
        fun has(word: String) = lower.contains(" $word ")

        if (user.isBlank()) return pick(OPENERS)
        if (listOf("bye", "goodbye", "see you", "good night", "gotta go").any { has(it) })
            return "It was really nice talking with you! See you next time."
        if (listOf("thanks", "thank you").any { has(it) }) return "You're welcome! " + pick(topicSwitches)

        val parts = mutableListOf<String>()
        val self = selfAnswers.firstOrNull { (keys, _) -> keys.any { has(it) } }?.second
        val feeling = feelings.firstOrNull { (keys, _) -> keys.any { has(it) } }?.second
        val topic = topics.maxByOrNull { t -> t.keywords.count { has(it) } }?.takeIf { t -> t.keywords.any { has(it) } }

        val greeting = listOf("hi", "hello", "hey").any { lower.startsWith(" $it ") } && user.split(" ").size <= 3
        val filler = lower.trim() in setOf("yes", "yeah", "yep", "no", "nope", "ok", "okay", "sure", "maybe", "hmm", "um", "uh", "i don't know", "not really")
        when {
            self != null -> parts += self
            greeting -> parts += "Hi! Nice to chat with you."
            filler -> parts += pick(listOf("I see!", "Got it.", "Okay!", "No worries."))
            feeling != null -> parts += feeling
            topic != null -> parts += pick(topic.reactions)
            else -> echo(user)?.let { parts += it }
        }

        val shortAnswer = user.split(Regex("\\s+")).size <= 2
        val question = when {
            topic != null && fresh(topic.questions).isNotEmpty() -> pick(topic.questions)
            self != null || greeting || filler -> pick(topicSwitches)
            shortAnswer -> pick(shortFollowUps)
            botTurnsSinceTopic(history) >= 3 -> pick(topicSwitches)
            else -> pick(genericQuestions + topicSwitches)
        }
        parts += question
        return parts.joinToString(" ")
    }

    /** "I went to Busan yesterday" -> "Oh, you went to Busan yesterday?" for short first-person statements. */
    internal fun echo(user: String): String? {
        val s = user.trim().trimEnd('.', '!', '?')
        val words = s.split(Regex("\\s+"))
        if (words.size < 3 || words.size > 9 || !words[0].equals("I", ignoreCase = true)) return null
        val swapped = words.mapIndexed { i, w ->
            when (w.lowercase(Locale.ROOT)) {
                "i" -> "you"
                "i'm" -> "you're"
                "am" -> if (i == 1) "are" else w
                "my" -> "your"
                "me" -> "you"
                "myself" -> "yourself"
                "i've" -> "you've"
                "i'll" -> "you'll"
                else -> w
            }
        }
        return "Oh, ${swapped.joinToString(" ")}? That's interesting!"
    }

    private fun botTurnsSinceTopic(history: List<FreeTalk.Line>): Int =
        history.takeLastWhile { line -> line.fromUser || topicSwitches.none { line.text.contains(it) } }.count { !it.fromUser }
}
