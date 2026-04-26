package com.focusguard.app.data.profile

import com.focusguard.app.domain.profile.UserProfile
import com.focusguard.app.friction.tasks.QuestionRepository
import com.focusguard.app.persistence.FocusGuardPrefs
import kotlinx.coroutines.flow.Flow

class ProfileRepository(
    private val profileDataStore: ProfileDataStore,
    private val legacyPrefs: FocusGuardPrefs,
    private val reloadQuestions: (String) -> Unit
) {
    val profile: Flow<UserProfile> = profileDataStore.profile

    fun getLegacySnapshot(): UserProfile {
        return UserProfile(
            deviceId = legacyPrefs.getOrCreateDeviceId(),
            exam = legacyPrefs.targetExam,
            targetDate = legacyPrefs.examDate,
            preferredSubjects = legacyPrefs.preferredSubjects.toList().sorted(),
            isOnboardingComplete = legacyPrefs.isOnboardingComplete,
            createdAt = legacyPrefs.getOrCreateUserProfileCreatedAt()
        )
    }

    suspend fun saveProfile(
        exam: String,
        targetDate: Long,
        preferredSubjects: List<String>,
        isOnboardingComplete: Boolean = true
    ) {
        val normalizedExam = exam.lowercase()
        val subjects = preferredSubjects.ifEmpty {
            UserProfile.defaultSubjectsForExam(normalizedExam)
        }

        legacyPrefs.targetExam = normalizedExam
        legacyPrefs.examDate = targetDate
        legacyPrefs.preferredSubjects = subjects.toSet()
        legacyPrefs.isOnboardingComplete = isOnboardingComplete
        val deviceId = legacyPrefs.getOrCreateDeviceId()
        legacyPrefs.getOrCreateUserProfileCreatedAt()

        profileDataStore.saveProfile(
            deviceId = deviceId,
            exam = normalizedExam,
            targetDate = targetDate,
            preferredSubjects = subjects,
            isOnboardingComplete = isOnboardingComplete
        )

        reloadQuestions(normalizedExam)
    }

    suspend fun skipOnboarding() {
        val snapshot = getLegacySnapshot()
        saveProfile(
            exam = snapshot.exam,
            targetDate = snapshot.targetDate,
            preferredSubjects = snapshot.preferredSubjects,
            isOnboardingComplete = true
        )
    }

    companion object {
        fun default(
            profileDataStore: ProfileDataStore,
            legacyPrefs: FocusGuardPrefs,
            questionRepositoryContext: android.content.Context
        ): ProfileRepository {
            return ProfileRepository(
                profileDataStore = profileDataStore,
                legacyPrefs = legacyPrefs,
                reloadQuestions = { exam ->
                    QuestionRepository.switchExam(questionRepositoryContext, exam)
                }
            )
        }
    }
}
