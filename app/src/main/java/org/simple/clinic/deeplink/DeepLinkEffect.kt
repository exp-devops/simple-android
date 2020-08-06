package org.simple.clinic.deeplink

import org.simple.clinic.user.User
import java.util.UUID

sealed class DeepLinkEffect

object FetchUser : DeepLinkEffect()

object NavigateToSetupActivity : DeepLinkEffect()

data class FetchPatient(val patientUuid: UUID) : DeepLinkEffect()

data class NavigateToPatientSummary(val patientUuid: UUID, val user: User) : DeepLinkEffect()

data class ShowPatientDoesNotExist(val user: User) : DeepLinkEffect()

// We will show this error when patient uuid is null
data class ShowNoPatientUuidError(val user: User) : DeepLinkEffect()
