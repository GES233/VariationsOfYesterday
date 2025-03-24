package moe.chestnut.awa.voy.core.calendar

/**
 * Provide interface.
*/
abstract class Calendar:
  def daysInMonth(year: Int, month: Int, day: Int): Int
  def daysInYear(year: Int, month: Int, day: Int): Int
  def monthsInYear(year: Int, month: Int, day: Int): Int

  def isLeapYear(year: Int, month: Int, day: Int): Boolean
  def isLeapMonth(year: Int, month: Int, day: Int): Boolean

  def isValid(year: Int, month: Int, day: Int): Boolean

  def toTuple(year: Int, month: Int, day: Int): Tuple3[Int, Int, Int]

  // def diff(date1)
