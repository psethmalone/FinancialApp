package com.example.financehome.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object BankHolidayUtil {

    /**
     * Checks if a given date is an official US Federal Reserve holiday (including observed dates).
     */
    fun isBankHoliday(date: LocalDate): Boolean {
        val year = date.year
        val holidays = getFederalHolidaysForYear(year) +
                // Check edge cases around year boundary (e.g. Dec 31 observed New Year's for next year, or Jan 2 observed New Year's for prior year)
                getFederalHolidaysForYear(year - 1).filter { it.year == year } +
                getFederalHolidaysForYear(year + 1).filter { it.year == year }

        return holidays.contains(date)
    }

    /**
     * Checks if a given date is a valid banking business day (Monday-Friday, not a federal holiday).
     */
    fun isBankingBusinessDay(date: LocalDate): Boolean {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            return false
        }
        return !isBankHoliday(date)
    }

    /**
     * If a scheduled payday falls on a weekend or bank holiday, shift effective payday
     * backward to the immediately preceding banking business day.
     */
    fun adjustPayDateForBankHoliday(scheduledDate: LocalDate): LocalDate {
        var date = scheduledDate
        while (!isBankingBusinessDay(date)) {
            date = date.minusDays(1)
        }
        return date
    }

    /**
     * Returns all observed US Federal Reserve holidays for a given calendar year.
     */
    fun getFederalHolidaysForYear(year: Int): Set<LocalDate> {
        val holidays = mutableSetOf<LocalDate>()

        // 1. New Year's Day (Jan 1)
        addFixedHolidayWithObservation(holidays, LocalDate.of(year, 1, 1))

        // 2. Martin Luther King Jr. Day (Third Monday in January)
        holidays.add(
            LocalDate.of(year, 1, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY))
        )

        // 3. Washington's Birthday / Presidents' Day (Third Monday in February)
        holidays.add(
            LocalDate.of(year, 2, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY))
        )

        // 4. Memorial Day (Last Monday in May)
        holidays.add(
            LocalDate.of(year, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY))
        )

        // 5. Juneteenth National Independence Day (June 19)
        addFixedHolidayWithObservation(holidays, LocalDate.of(year, 6, 19))

        // 6. Independence Day (July 4)
        addFixedHolidayWithObservation(holidays, LocalDate.of(year, 7, 4))

        // 7. Labor Day (First Monday in September)
        holidays.add(
            LocalDate.of(year, 9, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY))
        )

        // 8. Columbus Day (Second Monday in October)
        holidays.add(
            LocalDate.of(year, 10, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY))
        )

        // 9. Veterans Day (November 11)
        addFixedHolidayWithObservation(holidays, LocalDate.of(year, 11, 11))

        // 10. Thanksgiving Day (Fourth Thursday in November)
        holidays.add(
            LocalDate.of(year, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY))
        )

        // 11. Christmas Day (December 25)
        addFixedHolidayWithObservation(holidays, LocalDate.of(year, 12, 25))

        return holidays
    }

    private fun addFixedHolidayWithObservation(set: MutableSet<LocalDate>, fixedDate: LocalDate) {
        when (fixedDate.dayOfWeek) {
            DayOfWeek.SATURDAY -> set.add(fixedDate.minusDays(1)) // Observed Friday
            DayOfWeek.SUNDAY -> set.add(fixedDate.plusDays(1))   // Observed Monday
            else -> set.add(fixedDate)
        }
    }
}
