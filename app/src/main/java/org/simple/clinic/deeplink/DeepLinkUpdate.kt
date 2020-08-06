package org.simple.clinic.deeplink

import com.spotify.mobius.Next
import com.spotify.mobius.Update
import org.simple.clinic.mobius.dispatch
import org.simple.clinic.mobius.next
import org.simple.clinic.user.User

class DeepLinkUpdate : Update<DeepLinkModel, DeepLinkEvent, DeepLinkEffect> {

  override fun update(model: DeepLinkModel, event: DeepLinkEvent): Next<DeepLinkModel, DeepLinkEffect> {
    return when (event) {
      is UserFetched -> userFetched(model, event)
      is PatientFetched -> patientFetched(event, model)
    }
  }

  private fun userFetched(
      model: DeepLinkModel,
      event: UserFetched
  ): Next<DeepLinkModel, DeepLinkEffect> {
    val user = event.user
    return when {
      user?.loggedInStatus == User.LoggedInStatus.LOGGED_IN -> {
        val effect = if (model.patientUuid != null) {
          FetchPatient(model.patientUuid)
        } else {
          ShowNoPatientUuidError(user)
        }
        next(model.userFetched(user), effect)
      }
      else -> {
        dispatch(NavigateToSetupActivity)
      }
    }
  }

  private fun patientFetched(
      event: PatientFetched,
      model: DeepLinkModel
  ): Next<DeepLinkModel, DeepLinkEffect> {
    val patient = event.patient
    val effect = if (patient != null) {
      NavigateToPatientSummary(patient.uuid, model.user!!)
    } else {
      ShowPatientDoesNotExist(model.user!!)
    }
    return dispatch(effect)
  }
}
