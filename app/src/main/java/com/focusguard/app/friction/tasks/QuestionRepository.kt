package com.focusguard.app.friction.tasks

import android.content.Context
import android.util.Log
import com.focusguard.app.domain.pyq.PyqQuestion
import org.json.JSONObject
import kotlin.random.Random

/**
 * Loads exam questions from assets/exam_questions.json and serves them
 * randomly without repetition until exhausted.
 */
object QuestionRepository {

    private const val TAG = "QuestionRepo"
    private const val JSON_FILE = "exam_questions.json"

    data class ExamQuestion(
        val id: Int,
        val year: Int,
        val subject: String,
        val question: String,
        val options: Map<String, String>,  // A→text, B→text, etc.
        val answer: String,                // "A", "B", "C", or "D"
        val explanation: String
    )

    private var allQuestions: List<ExamQuestion> = emptyList()
    private var cachedPyqQuestions: List<PyqQuestion> = emptyList()
    private val shownIds = java.util.Collections.synchronizedSet(mutableSetOf<Int>())
    private var currentExamId: String = "neet"
    private var isInitialized = false

    /**
     * Initialize the repository by loading questions for the given exam.
     * Call this once at app startup.
     */
    fun initialize(context: Context, examId: String) {
        currentExamId = examId
        loadQuestions(context, examId)
        isInitialized = true
    }

    /**
     * Switch to a different exam and reload questions.
     */
    fun switchExam(context: Context, examId: String) {
        if (examId == currentExamId && isInitialized) return
        currentExamId = examId
        shownIds.clear()
        loadQuestions(context, examId)
    }

    /**
     * Get a random question that hasn't been shown yet.
     * If all questions have been shown, resets and starts fresh.
     */
    fun getRandomQuestion(subject: String? = null): ExamQuestion? {
        if (allQuestions.isEmpty()) {
            Log.e(TAG, "No questions loaded!")
            return null
        }

        // Filter by subject if specified
        val pool = if (subject != null) {
            allQuestions.filter { it.subject.equals(subject, ignoreCase = true) }
        } else {
            allQuestions
        }

        if (pool.isEmpty()) {
            Log.w(TAG, "No questions for subject: $subject")
            return allQuestions.random() // Fallback to any question
        }

        // Find unshown questions
        val unshown = pool.filter { it.id !in shownIds }

        if (unshown.isEmpty()) {
            // All questions shown — reset and start fresh
            Log.d(TAG, "All ${pool.size} questions exhausted. Resetting.")
            shownIds.clear()
            return pool.random().also { shownIds.add(it.id) }
        }

        val question = unshown.random()
        shownIds.add(question.id)
        Log.d(TAG, "Serving question #${question.id} (${question.subject}, ${question.year}). " +
                "Shown: ${shownIds.size}/${allQuestions.size}")
        return question
    }

    /**
     * Get total loaded question count.
     */
    fun getQuestionCount(): Int = allQuestions.size

    fun getAllPyqQuestions(): List<PyqQuestion> {
        cachedPyqQuestions.takeIf { it.isNotEmpty() }?.let { return it }

        return synchronized(this) {
            cachedPyqQuestions.takeIf { it.isNotEmpty() } ?: allQuestions.map { question ->
                PyqQuestion(
                    id = question.id,
                    exam = currentExamId,
                    subject = question.subject,
                    topic = null,
                    difficulty = inferDifficulty(question.subject),
                    year = question.year,
                    question = question.question,
                    options = question.options,
                    correctAnswer = question.answer,
                    explanation = question.explanation
                )
            }.also { cachedPyqQuestions = it }
        }
    }

    /**
     * Get current exam name.
     */
    fun getExamName(): String = currentExamId.uppercase()

    private fun inferDifficulty(subject: String): Int {
        return when (subject.lowercase()) {
            "biology" -> 1
            "chemistry" -> 2
            "physics" -> 3
            else -> 2
        }
    }

    private fun loadQuestions(context: Context, examId: String) {
        try {
            val jsonString = context.assets.open(JSON_FILE).bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            if (!root.has(examId)) {
                Log.e(TAG, "Exam '$examId' not found in JSON. Available: ${root.keys().asSequence().toList()}")
                return
            }

            val examObj = root.getJSONObject(examId)
            val questionsArray = examObj.getJSONArray("questions")
            val questions = mutableListOf<ExamQuestion>()

            for (i in 0 until questionsArray.length()) {
                val q = questionsArray.getJSONObject(i)
                val optionsObj = q.getJSONObject("options")
                val options = mutableMapOf<String, String>()
                optionsObj.keys().forEach { key ->
                    options[key] = optionsObj.getString(key)
                }

                questions.add(
                    ExamQuestion(
                        id = q.getInt("id"),
                        year = q.getInt("year"),
                        subject = q.getString("subject"),
                        question = q.getString("question"),
                        options = options,
                        answer = q.getString("answer"),
                        explanation = q.getString("explanation")
                    )
                )
            }

            allQuestions = questions
            cachedPyqQuestions = emptyList()
            Log.d(TAG, "Loaded ${questions.size} questions for exam: $examId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load questions: ${e.message}", e)
            cachedPyqQuestions = emptyList()
        }
    }
}
