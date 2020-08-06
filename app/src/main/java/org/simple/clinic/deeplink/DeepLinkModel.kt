package org.simple.clinic.deeplink

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.user.User
import java.util.UUID

@Parcelize
data class DeepLinkModel(
    val patientUuid: UUID?,
    val user: User?
) : Parcelable {

  companion object {
    fun default(patientUuid: UUID?) = DeepLinkModel(patientUuid = patientUuid, user = null)
  }

  fun userFetched(user: User): DeepLinkModel {
    return copy(user = user)
  }
}
