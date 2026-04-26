package com.focusguard.app.data.pyq

import com.focusguard.app.domain.pyq.PyqQuestion
import com.focusguard.app.domain.pyq.PyqQuestionSource
import com.focusguard.app.friction.tasks.QuestionRepository

class JsonPyqQuestionSource : PyqQuestionSource {
    override fun getAllQuestions(): List<PyqQuestion> {
        return QuestionRepository.getAllPyqQuestions()
    }
}
