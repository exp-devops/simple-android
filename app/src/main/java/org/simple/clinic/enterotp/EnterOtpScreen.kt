package org.simple.clinic.enterotp

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.RelativeLayout
import androidx.transition.TransitionManager
import com.jakewharton.rxbinding3.view.clicks
import com.jakewharton.rxbinding3.view.detaches
import com.jakewharton.rxbinding3.widget.editorActions
import io.reactivex.Observable
import io.reactivex.rxkotlin.ofType
import kotlinx.android.synthetic.main.screen_enterotp.view.*
import org.simple.clinic.LOGIN_OTP_LENGTH
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.appconfig.Country
import org.simple.clinic.bindUiToController
import org.simple.clinic.di.injector
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.ScreenCreated
import org.simple.clinic.widgets.ScreenDestroyed
import org.simple.clinic.widgets.UiEvent
import org.simple.clinic.widgets.hideKeyboard
import org.simple.clinic.widgets.showKeyboard
import javax.inject.Inject

class EnterOtpScreen(
    context: Context,
    attributeSet: AttributeSet
) : RelativeLayout(context, attributeSet), EnterOtpUi {

  @Inject
  lateinit var controller: EnterOtpScreenController

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var country: Country

  @Inject
  lateinit var effectHandlerFactory: EnterOtpEffectHandler.Factory

  private val events by unsafeLazy {
    Observable
        .mergeArray(
            screenCreates(),
            otpSubmits(),
            resendSmsClicks()
        )
        .compose(ReportAnalyticsEvents())
        .share()
  }

  private val delegate by unsafeLazy {
    val uiRenderer = EnterOtpUiRenderer(this)

    MobiusDelegate.forView(
        events = events.ofType(),
        defaultModel = EnterOtpModel.create(),
        update = EnterOtpUpdate(),
        effectHandler = effectHandlerFactory.create(this).build(),
        init = EnterOtpInit(),
        modelUpdateListener = uiRenderer::render
    )
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    context.injector<Injector>().inject(this)

    bindUiToController(
        ui = this,
        events = events,
        controller = controller,
        screenDestroys = detaches().map { ScreenDestroyed() }
    )

    otpEntryEditText.showKeyboard()
    backButton.setOnClickListener { goBack() }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    delegate.start()
  }

  override fun onDetachedFromWindow() {
    delegate.stop()
    super.onDetachedFromWindow()
  }

  override fun onSaveInstanceState(): Parcelable? {
    return delegate.onSaveInstanceState(super.onSaveInstanceState())
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    super.onRestoreInstanceState(delegate.onRestoreInstanceState(state))
  }

  private fun screenCreates() = Observable.just(ScreenCreated())

  private fun otpSubmits(): Observable<UiEvent> {
    val otpFromImeClicks: Observable<UiEvent> = otpEntryEditText
        .editorActions() { it == EditorInfo.IME_ACTION_DONE }
        .map { EnterOtpSubmitted(otpEntryEditText.text.toString()) }

    val otpFromTextChanges: Observable<UiEvent> = otpEntryEditText
        .textChanges()
        .filter { it.length == LOGIN_OTP_LENGTH }
        .map { EnterOtpSubmitted(it.toString()) }

    return otpFromImeClicks.mergeWith(otpFromTextChanges)
  }

  private fun resendSmsClicks() =
      resendSmsButton.clicks().map { EnterOtpResendSmsClicked() }

  override fun showUserPhoneNumber(phoneNumber: String) {
    val phoneNumberWithCountryCode = resources.getString(
        R.string.enterotp_phonenumber,
        country.isdCode,
        phoneNumber
    )

    userPhoneNumberTextView.text = phoneNumberWithCountryCode
  }

  override fun goBack() {
    hideKeyboard()
    screenRouter.pop()
  }

  override fun showUnexpectedError() {
    showError(resources.getString(R.string.api_unexpected_error))
  }

  override fun showNetworkError() {
    showError(resources.getString(R.string.api_network_error))
  }

  override fun showServerError(error: String) {
    showError(error)
    otpEntryEditText.showKeyboard()
  }

  override fun showIncorrectOtpError() {
    showError(resources.getString(R.string.enterotp_incorrect_code))
    otpEntryEditText.showKeyboard()
  }

  private fun showError(error: String) {
    smsSentTextView.visibility = View.GONE
    errorTextView.text = error
    errorTextView.visibility = View.VISIBLE
  }

  override fun hideError() {
    errorTextView.visibility = View.GONE
  }

  override fun showProgress() {
    TransitionManager.beginDelayedTransition(this)
    validateOtpProgressBar.visibility = View.VISIBLE
    otpEntryContainer.visibility = View.INVISIBLE
  }

  override fun hideProgress() {
    TransitionManager.beginDelayedTransition(this)
    validateOtpProgressBar.visibility = View.INVISIBLE
    otpEntryContainer.visibility = View.VISIBLE
  }

  override fun showSmsSentMessage() {
    smsSentTextView.visibility = View.VISIBLE
  }

  override fun clearPin() {
    otpEntryEditText.text = null
  }

  interface Injector {
    fun inject(target: EnterOtpScreen)
  }
}
