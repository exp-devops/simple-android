package org.simple.clinic.deeplink

import org.simple.clinic.user.User
import java.util.UUID

interface DeepLinkUiActions {
  fun navigateToSetupActivity()
  fun navigateToPatientSummary(patientUuid: UUID, user: User)
  fun showPatientDoesNotExist(user: User)
  fun showNoPatientUuidError(user: User)
}
