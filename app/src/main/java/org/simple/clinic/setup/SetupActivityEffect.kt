package org.simple.clinic.setup

import org.simple.clinic.user.User

sealed class SetupActivityEffect

object FetchUserDetails : SetupActivityEffect()

data class GoToMainActivity(val user: User) : SetupActivityEffect()

object ShowOnboardingScreen : SetupActivityEffect()

object InitializeDatabase : SetupActivityEffect()

object ShowCountrySelectionScreen : SetupActivityEffect()

object SetFallbackCountryAsCurrentCountry: SetupActivityEffect()
