package org.simple.clinic.bp.entry

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import org.junit.After
import org.junit.Test
import org.simple.clinic.mobius.EffectHandlerTestCase
import org.simple.clinic.util.TestUserClock
import org.simple.clinic.util.scheduler.TrampolineSchedulersProvider
import java.time.LocalDate
import java.time.ZoneOffset.UTC

class BloodPressureEntryEffectHandlerTest {
  private val ui = mock<BloodPressureEntryUi>()
  private val userClock = TestUserClock()
  private val effectHandler = BloodPressureEntryEffectHandler(
      ui = ui,
      userSession = mock(),
      facilityRepository = mock(),
      patientRepository = mock(),
      bloodPressureRepository = mock(),
      appointmentsRepository = mock(),
      userClock = userClock,
      schedulersProvider = TrampolineSchedulersProvider(),
      uuidGenerator = mock()
  ).build()
  private val testCase = EffectHandlerTestCase(effectHandler)

  @After
  fun teardown() {
    testCase.dispose()
  }

  @Test
  fun `when prefill date is dispatched, then populate date button and date input fields`() {
    // when
    val entryDate = LocalDate.of(1992, 6, 7)
    userClock.setDate(LocalDate.of(1992, 6, 7), UTC)
    testCase.dispatch(PrefillDate.forNewEntry())

    // then
    verify(ui).setDateOnInputFields("7", "6", "1992")
    verify(ui).showDateOnDateButton(entryDate)
    testCase.assertOutgoingEvents(DatePrefilled(entryDate))
  }
}
