package org.simple.clinic.main

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.user.User
import org.simple.clinic.user.UserStatus

@Parcelize
data class CurrentUserStatus(
    val syncApprovalStatus: UserStatus,
    val loggedInStatus: User.LoggedInStatus
) : Parcelable {

  companion object {
    fun from(user: User?): CurrentUserStatus? {
      return user?.let { CurrentUserStatus(user.status, user.loggedInStatus) }
    }
  }
}
