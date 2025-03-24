package moe.chestnut.awa.voy.core.calendar

/**
 * Date datatype and functions.
 * 
 * Copied from elixir's stdlib
 * <https://github.com/elixir-lang/elixir/blob/v1.18.3/lib/elixir/lib/calendar/date.ex#L3>.
 * 
 * Only containing year, month and day(exclude week).
**/
final case class VoYDate(year: Int, month: Int, day: Int, calendar: Calendar):
  // VoYDate => Int
  def daysInMonth: Unit = { }

  // VoYDate => Int
  def monthsInYear: Unit = { }

  // (VoYDate, Any) => String
  // override def toString(schema: Any): String = { }

  // (VoYDate, VoYDate) => lt | eq | gt
  def compare(targetDate: VoYDate): Unit = { }

  // before and after

  // (VoYDate, Tuple2[day, Int]) => VoYDate
  def offset(offset: Integer): Unit = { }

  // (VoYDate, VoYDate) => Tuple2[]
  def diff(date1: VoYDate): Unit = { }

  // (VoYDate, Tuple2[Unit, Int]) => VoYDate
  def shift(shiftPair: Tuple2[VoYDateFormat, Integer]): Unit = { }
