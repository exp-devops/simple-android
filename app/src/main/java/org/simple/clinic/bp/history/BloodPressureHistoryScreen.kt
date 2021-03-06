package org.simple.clinic.bp.history

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.paging.PagedList
import androidx.recyclerview.widget.LinearLayoutManager
import com.jakewharton.rxbinding3.view.detaches
import io.reactivex.Observable
import io.reactivex.rxkotlin.cast
import io.reactivex.rxkotlin.ofType
import kotlinx.android.synthetic.main.screen_bp_history.view.*
import org.simple.clinic.R
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.bp.BloodPressureHistoryListItemDataSourceFactory
import org.simple.clinic.bp.entry.BloodPressureEntrySheet
import org.simple.clinic.bp.history.adapter.BloodPressureHistoryListItemDiffCallback
import org.simple.clinic.bp.history.adapter.Event.AddNewBpClicked
import org.simple.clinic.bp.history.adapter.Event.BloodPressureHistoryItemClicked
import org.simple.clinic.di.injector
import org.simple.clinic.mobius.MobiusDelegate
import org.simple.clinic.patient.DateOfBirth
import org.simple.clinic.patient.Gender
import org.simple.clinic.patient.Patient
import org.simple.clinic.patient.displayLetterRes
import org.simple.clinic.router.screen.ScreenRouter
import org.simple.clinic.summary.PatientSummaryConfig
import org.simple.clinic.util.UserClock
import org.simple.clinic.util.UtcClock
import org.simple.clinic.util.unsafeLazy
import org.simple.clinic.widgets.DividerItemDecorator
import org.simple.clinic.widgets.PagingItemAdapter
import org.simple.clinic.widgets.dp
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

class BloodPressureHistoryScreen(
    context: Context,
    attrs: AttributeSet
) : ConstraintLayout(context, attrs), BloodPressureHistoryScreenUi, BloodPressureHistoryScreenUiActions {

  @Inject
  lateinit var utcClock: UtcClock

  @Inject
  lateinit var userClock: UserClock

  @Inject
  lateinit var config: PatientSummaryConfig

  @Inject
  lateinit var screenRouter: ScreenRouter

  @Inject
  lateinit var effectHandler: BloodPressureHistoryScreenEffectHandler.Factory

  @field:[Inject Named("full_date")]
  lateinit var dateFormatter: DateTimeFormatter

  @field:[Inject Named("time_for_measurement_history")]
  lateinit var timeFormatter: DateTimeFormatter

  @field:[Inject Named("for_measurement_history")]
  lateinit var measurementHistoryPaginationConfig: PagedList.Config

  private val bloodPressureHistoryAdapter = PagingItemAdapter(BloodPressureHistoryListItemDiffCallback())

  private val events: Observable<BloodPressureHistoryScreenEvent> by unsafeLazy {
    Observable
        .merge(
            addNewBpClicked(),
            bloodPressureClicked()
        )
        .compose(ReportAnalyticsEvents())
        .cast<BloodPressureHistoryScreenEvent>()
  }

  private val uiRenderer = BloodPressureHistoryScreenUiRenderer(this)

  private val delegate: MobiusDelegate<BloodPressureHistoryScreenModel, BloodPressureHistoryScreenEvent, BloodPressureHistoryScreenEffect> by unsafeLazy {
    val screenKey = screenRouter.key<BloodPressureHistoryScreenKey>(this)
    MobiusDelegate.forView(
        events = events,
        defaultModel = BloodPressureHistoryScreenModel.create(screenKey.patientUuid),
        init = BloodPressureHistoryScreenInit(),
        update = BloodPressureHistoryScreenUpdate(),
        effectHandler = effectHandler.create(this).build(),
        modelUpdateListener = uiRenderer::render
    )
  }

  override fun onFinishInflate() {
    super.onFinishInflate()
    if (isInEditMode) {
      return
    }
    context.injector<BloodPressureHistoryScreenInjector>().inject(this)

    setupBloodPressureHistoryList()
    handleToolbarBackClick()
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

  private fun setupBloodPressureHistoryList() {
    val dividerMargin = 8.dp
    val divider = DividerItemDecorator(context = context, marginStart = dividerMargin, marginEnd = dividerMargin)

    bpHistoryList.apply {
      setHasFixedSize(true)
      layoutManager = LinearLayoutManager(context)
      addItemDecoration(divider)
      adapter = bloodPressureHistoryAdapter
    }
  }

  private fun handleToolbarBackClick() {
    toolbar.setNavigationOnClickListener {
      screenRouter.pop()
    }
  }

  override fun showPatientInformation(patient: Patient) {
    val ageValue = DateOfBirth.fromPatient(patient, userClock).estimateAge(userClock)
    displayNameGenderAge(patient.fullName, patient.gender, ageValue)
  }

  override fun openBloodPressureEntrySheet(patientUuid: UUID) {
    val intent = BloodPressureEntrySheet.intentForNewBp(context, patientUuid)
    context.startActivity(intent)
  }

  override fun openBloodPressureUpdateSheet(bpUuid: UUID) {
    val intent = BloodPressureEntrySheet.intentForUpdateBp(context, bpUuid)
    context.startActivity(intent)
  }

  @SuppressLint("CheckResult")
  override fun showBloodPressures(dataSourceFactory: BloodPressureHistoryListItemDataSourceFactory) {
    val detaches = detaches()
    // Initial load size hint should be a multiple of page size
    dataSourceFactory.toObservable(config = measurementHistoryPaginationConfig, detaches = detaches)
        .takeUntil(detaches)
        .subscribe(bloodPressureHistoryAdapter::submitList)
  }

  private fun displayNameGenderAge(name: String, gender: Gender, age: Int) {
    val genderLetter = resources.getString(gender.displayLetterRes)
    toolbar.title = resources.getString(R.string.bloodpressurehistory_toolbar_title, name, genderLetter, age.toString())
  }

  private fun addNewBpClicked(): Observable<BloodPressureHistoryScreenEvent> {
    return bloodPressureHistoryAdapter
        .itemEvents
        .ofType<AddNewBpClicked>()
        .map { NewBloodPressureClicked }
  }

  private fun bloodPressureClicked(): Observable<BloodPressureHistoryScreenEvent> {
    return bloodPressureHistoryAdapter
        .itemEvents
        .ofType<BloodPressureHistoryItemClicked>()
        .map { it.measurement }
        .map(::BloodPressureClicked)
  }
}
