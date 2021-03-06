package org.simple.clinic.summary.teleconsultation.api

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

sealed class TeleconsultInfo : Parcelable {

  @Parcelize
  object Fetching : TeleconsultInfo()

  @Parcelize
  data class Fetched(
      val doctorsPhoneNumbers: List<TeleconsultPhoneNumber>
  ) : TeleconsultInfo() {

    val areMultipleDoctorsAvailable: Boolean
      get() = doctorsPhoneNumbers.size > 1
  }

  @Parcelize
  object NetworkError : TeleconsultInfo()

  @Parcelize
  object MissingPhoneNumber : TeleconsultInfo()
}
