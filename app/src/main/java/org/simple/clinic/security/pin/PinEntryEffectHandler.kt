package org.simple.clinic.security.pin

import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import org.simple.clinic.security.ComparisonResult.DIFFERENT
import org.simple.clinic.security.ComparisonResult.SAME
import org.simple.clinic.security.PasswordHasher
import org.simple.clinic.util.scheduler.SchedulersProvider

class PinEntryEffectHandler @AssistedInject constructor(
    private val passwordHasher: PasswordHasher,
    private val schedulersProvider: SchedulersProvider
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(): PinEntryEffectHandler
  }

  fun build(): ObservableTransformer<PinEntryEffect, PinEntryEvent> {
    return RxMobius
        .subtypeEffectHandler<PinEntryEffect, PinEntryEvent>()
        .addTransformer(ValidateEnteredPin::class.java, validateEnteredPin(schedulersProvider.computation()))
        .build()
  }

  private fun validateEnteredPin(
      scheduler: Scheduler
  ): ObservableTransformer<ValidateEnteredPin, PinEntryEvent> {
    return ObservableTransformer { effects ->
      effects
          .flatMapSingle {
            passwordHasher
                .compare(hashed = it.pinDigest, password = it.enteredPin)
                .subscribeOn(scheduler)
          }
          .map { result ->
            when (result) {
              SAME -> CorrectPinEntered
              DIFFERENT -> WrongPinEntered
            }
          }
    }
  }
}