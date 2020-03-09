package org.simple.clinic.summary

import com.spotify.mobius.rx2.RxMobius
import com.squareup.inject.assisted.Assisted
import com.squareup.inject.assisted.AssistedInject
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.cast
import io.reactivex.rxkotlin.zipWith
import org.simple.clinic.analytics.Analytics
import org.simple.clinic.bloodsugar.BloodSugarRepository
import org.simple.clinic.bp.BloodPressureRepository
import org.simple.clinic.facility.FacilityRepository
import org.simple.clinic.overdue.Appointment
import org.simple.clinic.overdue.AppointmentCancelReason
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.patient.PatientRepository
import org.simple.clinic.summary.addphone.MissingPhoneReminderRepository
import org.simple.clinic.user.UserSession
import org.simple.clinic.util.Just
import org.simple.clinic.util.None
import org.simple.clinic.util.filterAndUnwrapJust
import org.simple.clinic.util.scheduler.SchedulersProvider
import java.util.UUID

class PatientSummaryEffectHandler @AssistedInject constructor(
    private val schedulersProvider: SchedulersProvider,
    private val patientRepository: PatientRepository,
    private val bloodPressureRepository: BloodPressureRepository,
    private val appointmentRepository: AppointmentRepository,
    private val missingPhoneReminderRepository: MissingPhoneReminderRepository,
    private val userSession: UserSession,
    private val facilityRepository: FacilityRepository,
    val bloodSugarRepository: BloodSugarRepository,
    @Assisted private val uiActions: PatientSummaryUiActions
) {

  @AssistedInject.Factory
  interface Factory {
    fun create(uiActions: PatientSummaryUiActions): PatientSummaryEffectHandler
  }

  fun build(): ObservableTransformer<PatientSummaryEffect, PatientSummaryEvent> {
    return RxMobius
        .subtypeEffectHandler<PatientSummaryEffect, PatientSummaryEvent>()
        .addTransformer(LoadPatientSummaryProfile::class.java, loadPatientSummaryProfile(schedulersProvider.io()))
        .addTransformer(LoadCurrentFacility::class.java, loadCurrentFacility(schedulersProvider.io()))
        .addConsumer(HandleEditClick::class.java, { uiActions.showEditPatientScreen(it.patientSummaryProfile) }, schedulersProvider.ui())
        .addAction(HandleLinkIdCancelled::class.java, { uiActions.goToPreviousScreen() }, schedulersProvider.ui())
        .addAction(GoBackToPreviousScreen::class.java, { uiActions.goToPreviousScreen() }, schedulersProvider.ui())
        .addAction(GoToHomeScreen::class.java, { uiActions.goToHomeScreen() }, schedulersProvider.ui())
        .addTransformer(CheckForInvalidPhone::class.java, checkForInvalidPhone(schedulersProvider.io(), schedulersProvider.ui()))
        .addTransformer(FetchHasShownMissingPhoneReminder::class.java, fetchHasShownMissingPhoneReminder(schedulersProvider.io()))
        .addTransformer(MarkReminderAsShown::class.java, markReminderAsShown(schedulersProvider.io()))
        .addConsumer(ShowAddPhonePopup::class.java, { uiActions.showAddPhoneDialog(it.patientUuid) }, schedulersProvider.ui())
        .addTransformer(ShowLinkIdWithPatientView::class.java, showLinkIdWithPatientView(schedulersProvider.ui()))
        .addAction(HideLinkIdWithPatientView::class.java, { uiActions.hideLinkIdWithPatientView() }, schedulersProvider.ui())
        .addTransformer(ReportViewedPatientToAnalytics::class.java, reportViewedPatientToAnalytics())
        .addConsumer(
            ShowScheduleAppointmentSheet::class.java,
            { uiActions.showScheduleAppointmentSheet(it.patientUuid, it.sheetOpenedFrom) },
            schedulersProvider.ui()
        )
        .addTransformer(LoadDataForBackClick::class.java, loadDataForBackClick(schedulersProvider.io()))
        .addTransformer(LoadDataForDoneClick::class.java, loadDataForDoneClick(schedulersProvider.io()))
        .build()
  }

  // TODO(vs): 2020-01-15 Revisit after Mobius migration
  private fun loadPatientSummaryProfile(scheduler: Scheduler): ObservableTransformer<LoadPatientSummaryProfile, PatientSummaryEvent> {
    return ObservableTransformer { effects ->
      effects.flatMap { fetchPatientSummaryProfile ->
        val patientUuid = fetchPatientSummaryProfile.patientUuid

        val sharedPatients = patientRepository.patient(patientUuid)
            .subscribeOn(scheduler)
            .map {
              // We do not expect the patient to get deleted while this screen is already open.
              (it as Just).value
            }
            .replay(1)
            .refCount()

        val addresses = sharedPatients
            .flatMap { patient -> patientRepository.address(patient.addressUuid).subscribeOn(scheduler) }
            .map { (it as Just).value }

        val latestPhoneNumberStream = patientRepository.phoneNumber(patientUuid).subscribeOn(scheduler)
        val latestBpPassportStream = patientRepository.bpPassportForPatient(patientUuid).subscribeOn(scheduler)
        val bangladeshNationalIdStream = patientRepository.bangladeshNationalIdForPatient(patientUuid).subscribeOn(scheduler)

        Observables
            .combineLatest(sharedPatients, addresses, latestPhoneNumberStream, latestBpPassportStream, bangladeshNationalIdStream) { patient, address, phoneNumber, bpPassport, bangladeshNationalId ->
              PatientSummaryProfile(patient, address, phoneNumber.toNullable(), bpPassport.toNullable(), bangladeshNationalId.toNullable())
            }
            .take(1)
            .map(::PatientSummaryProfileLoaded)
            .cast<PatientSummaryEvent>()
      }
    }
  }

  // TODO(vs): 2020-02-19 Revisit after Mobius migration
  private fun checkForInvalidPhone(
      backgroundWorkScheduler: Scheduler,
      uiWorkScheduler: Scheduler
  ): ObservableTransformer<CheckForInvalidPhone, PatientSummaryEvent> {
    return ObservableTransformer { effects ->
      effects
          .flatMap { checkForInvalidPhone ->
            hasInvalidPhone(checkForInvalidPhone.patientUuid)
                .subscribeOn(backgroundWorkScheduler)
                .take(1)
                .observeOn(uiWorkScheduler)
                .doOnNext { isPhoneInvalid ->
                  if (isPhoneInvalid) {
                    uiActions.showUpdatePhoneDialog(checkForInvalidPhone.patientUuid)
                  }
                }
                .flatMapSingle { Single.just(CompletedCheckForInvalidPhone) }
          }
    }
  }

  // TODO(vs): 2020-02-19 Revisit after Mobius migration
  private fun fetchHasShownMissingPhoneReminder(
      scheduler: Scheduler
  ): ObservableTransformer<FetchHasShownMissingPhoneReminder, PatientSummaryEvent> {
    return ObservableTransformer { effects ->
      effects
          .flatMap { effect ->
            isMissingPhoneAndHasShownReminder(effect.patientUuid)
                .subscribeOn(scheduler)
                .take(1)
                .map(::FetchedHasShownMissingReminder)
          }
    }
  }

  // TODO(vs): 2020-02-19 Revisit after Mobius migration
  private fun markReminderAsShown(
      scheduler: Scheduler
  ): ObservableTransformer<MarkReminderAsShown, PatientSummaryEvent> {
    return ObservableTransformer { effects ->
      effects
          .flatMap { effect ->
            missingPhoneReminderRepository
                .markReminderAsShownFor(effect.patientUuid)
                .subscribeOn(scheduler)
                .andThen(Observable.empty<PatientSummaryEvent>())
          }
    }
  }

  private fun loadDataForBackClick(
      scheduler: Scheduler
  ): ObservableTransformer<LoadDataForBackClick, PatientSummaryEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(scheduler)
          .map { loadDataForBackClick ->
            val patientUuid = loadDataForBackClick.patientUuid
            val timestamp = loadDataForBackClick.screenCreatedTimestamp
            DataForBackClickLoaded(
                hasPatientDataChangedSinceScreenCreated = patientRepository.hasPatientDataChangedSince(patientUuid, timestamp),
                noBloodPressuresRecordedForPatient = doesNotHaveBloodPressures(patientUuid),
                noBloodSugarsRecordedForPatient = doesNotHaveBloodSugars(patientUuid)
            )
          }
    }
  }

  private fun loadDataForDoneClick(
      scheduler: Scheduler
  ): ObservableTransformer<LoadDataForDoneClick, PatientSummaryEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(scheduler)
          .map { loadDataForBackClick ->
            val patientUuid = loadDataForBackClick.patientUuid
            DataForDoneClickLoaded(
                noBloodPressuresRecordedForPatient = doesNotHaveBloodPressures(patientUuid),
                noBloodSugarsRecordedForPatient = doesNotHaveBloodSugars(patientUuid)
            )
          }
    }
  }

  private fun showLinkIdWithPatientView(
      scheduler: Scheduler
  ): ObservableTransformer<ShowLinkIdWithPatientView, PatientSummaryEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(scheduler)
          .doOnNext { uiActions.showLinkIdWithPatientView(it.patientUuid, it.identifier) }
          .map { LinkIdWithPatientSheetShown }
    }
  }

  private fun reportViewedPatientToAnalytics(): ObservableTransformer<ReportViewedPatientToAnalytics, PatientSummaryEvent> {
    return ObservableTransformer { effects ->
      effects
          .doOnNext { Analytics.reportViewedPatient(it.patientUuid, it.openIntention.analyticsName()) }
          .map { ReportedViewedPatientToAnalytics }
    }
  }

  private fun loadCurrentFacility(scheduler: Scheduler): ObservableTransformer<LoadCurrentFacility, PatientSummaryEvent> {
    return ObservableTransformer { effects ->
      effects
          .observeOn(scheduler)
          .flatMap {
            val user = userSession.loggedInUserImmediate()
            requireNotNull(user)

            facilityRepository
                .currentFacility(user)
                .take(1)
          }
          .map(::CurrentFacilityLoaded)
    }
  }

  private fun doesNotHaveBloodPressures(patientUuid: UUID): Boolean {
    return bloodPressureRepository.bloodPressureCountImmediate(patientUuid) == 0
  }

  private fun doesNotHaveBloodSugars(patientUuid: UUID): Boolean {
    return bloodSugarRepository.bloodSugarCountImmediate(patientUuid) == 0
  }

  // TODO(vs): 2020-02-18 Revisit after Mobius migration
  private fun hasInvalidPhone(patientUuid: UUID): Observable<Boolean> {
    return patientRepository.phoneNumber(patientUuid)
        .filterAndUnwrapJust()
        .zipWith(lastCancelledAppointmentWithInvalidPhone(patientUuid))
        .map { (number, appointment) -> appointment.updatedAt > number.updatedAt }
  }

  // TODO(vs): 2020-02-18 Revisit after Mobius migration
  private fun lastCancelledAppointmentWithInvalidPhone(patientUuid: UUID): Observable<Appointment> {
    return appointmentRepository
        .lastCreatedAppointmentForPatient(patientUuid)
        .filterAndUnwrapJust()
        .filter { it.status == Appointment.Status.Cancelled && it.cancelReason == AppointmentCancelReason.InvalidPhoneNumber }
  }

  private fun isMissingPhoneAndHasShownReminder(patientUuid: UUID): Observable<Boolean> {
    return patientRepository
        .phoneNumber(patientUuid)
        .zipWith(hasShownReminderForMissingPhone(patientUuid))
        .map { (number, reminderShown) -> number is None && reminderShown }
  }

  private fun hasShownReminderForMissingPhone(patientUuid: UUID): Observable<Boolean> {
    return missingPhoneReminderRepository
        .hasShownReminderFor(patientUuid)
        .toObservable()
  }
}
