package com.example.financehome.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class BankHolidayUtilTest {

    @Test
    fun testFederalHolidaysDetected() {
        // New Year's Day
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 1, 1)))

        // MLK Day (3rd Monday in Jan): Jan 19, 2026
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 1, 19)))

        // Washington's Birthday (3rd Monday in Feb): Feb 16, 2026
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 2, 16)))

        // Memorial Day (Last Monday in May): May 25, 2026
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 5, 25)))

        // Juneteenth (June 19)
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 6, 19)))

        // Independence Day (July 4, 2026 is Saturday -> observed Friday July 3, 2026)
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 7, 3)))

        // Labor Day (1st Monday in Sep): Sep 7, 2026
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 9, 7)))

        // Columbus Day (2nd Monday in Oct): Oct 12, 2026
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 10, 12)))

        // Veterans Day (Nov 11)
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 11, 11)))

        // Thanksgiving (4th Thursday in Nov): Nov 26, 2026
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 11, 26)))

        // Christmas Day (Dec 25)
        assertTrue(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 12, 25)))
    }

    @Test
    fun testObservedHolidaysShifting() {
        // July 4, 2026 is Saturday -> Observed on Friday July 3, 2026
        assertEquals(DayOfWeek.SATURDAY, LocalDate.of(2026, 7, 4).dayOfWeek)
        assertTrue("July 3 should be observed holiday", BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 7, 3)))

        // July 4, 2027 is Sunday -> Observed on Monday July 5, 2027
        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2027, 7, 4).dayOfWeek)
        assertTrue("July 5 should be observed holiday", BankHolidayUtil.isBankHoliday(LocalDate.of(2027, 7, 5)))
    }

    @Test
    fun testNonHolidaysReturnFalse() {
        assertFalse(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 5, 1)))
        assertFalse(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 11, 27))) // Black Friday is not a Fed holiday
        assertFalse(BankHolidayUtil.isBankHoliday(LocalDate.of(2026, 7, 6)))
    }

    @Test
    fun testAdjustPayDateForWeekend() {
        // Saturday May 2, 2026 -> should shift to Friday May 1, 2026
        val saturday = LocalDate.of(2026, 5, 2)
        val adjusted = BankHolidayUtil.adjustPayDateForBankHoliday(saturday)
        assertEquals(LocalDate.of(2026, 5, 1), adjusted)

        // Sunday May 3, 2026 -> should shift to Friday May 1, 2026
        val sunday = LocalDate.of(2026, 5, 3)
        val adjustedSun = BankHolidayUtil.adjustPayDateForBankHoliday(sunday)
        assertEquals(LocalDate.of(2026, 5, 1), adjustedSun)
    }

    @Test
    fun testAdjustPayDateForHoliday() {
        // Friday July 3, 2026 is observed Independence Day holiday -> should shift to Thursday July 2, 2026
        val scheduledFriday = LocalDate.of(2026, 7, 3)
        val adjusted = BankHolidayUtil.adjustPayDateForBankHoliday(scheduledFriday)
        assertEquals(LocalDate.of(2026, 7, 2), adjusted)
        assertEquals(DayOfWeek.THURSDAY, adjusted.dayOfWeek)
    }

    @Test
    fun testAdjustPayDateAcrossYearBoundary() {
        // Jan 1, 2021 was a Friday (New Year's Day)
        // A payday scheduled on Friday Jan 1, 2021 shifts to Thursday Dec 31, 2020!
        val jan1 = LocalDate.of(2021, 1, 1)
        val adjusted = BankHolidayUtil.adjustPayDateForBankHoliday(jan1)
        assertEquals(LocalDate.of(2020, 12, 31), adjusted)
        assertEquals(DayOfWeek.THURSDAY, adjusted.dayOfWeek)
    }
}
