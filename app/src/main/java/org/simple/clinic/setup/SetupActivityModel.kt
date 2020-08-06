package org.simple.clinic.setup

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.simple.clinic.appconfig.Country
import org.simple.clinic.user.User
import org.simple.clinic.util.Optional
import org.simple.clinic.util.isNotEmpty
import org.simple.clinic.util.toNullable

@Parcelize
data class SetupActivityModel(
    val user: User?,
    val hasUserSelectedACountry: Boolean?
) : Parcelable {

  companion object {
    val SETTING_UP = SetupActivityModel(user = null, hasUserSelectedACountry = null)
  }

  fun withLoggedInUser(user: Optional<User>): SetupActivityModel {
    return copy(user = user.toNullable())
  }

  fun withSelectedCountry(country: Optional<Country>): SetupActivityModel {
    return copy(hasUserSelectedACountry = country.isNotEmpty())
  }
}
