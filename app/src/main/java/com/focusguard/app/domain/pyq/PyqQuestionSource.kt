package com.focusguard.app.domain.pyq

interface PyqQuestionSource {
    fun getAllQuestions(): List<PyqQuestion>
}
