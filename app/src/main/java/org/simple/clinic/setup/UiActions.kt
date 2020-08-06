package org.simple.clinic.setup

import org.simple.clinic.user.User

interface UiActions {
  fun goToMainActivity(user: User)

  fun showSplashScreen()

  fun showCountrySelectionScreen()
}
