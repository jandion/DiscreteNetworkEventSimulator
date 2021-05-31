case class Event(eventIndex: Int, time: Long) extends Ordered[Event]{
  override def compare(that: Event): Int = that.time compareTo this.time
}