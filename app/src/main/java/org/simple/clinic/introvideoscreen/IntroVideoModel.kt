package org.simple.clinic.introvideoscreen

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
class IntroVideoModel : Parcelable {

  companion object {
    fun default() = IntroVideoModel()
  }
}
