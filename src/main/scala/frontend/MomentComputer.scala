package frontend

object MomentComputer {

  def avg(count: Array[String], x1: Array[String] ): Array[String] = {
    if (count.length != x1.length) {
      Array.empty
    } else {
      val temp = new Array[String](count.length)
      for (i <- count.indices) {
        temp(i) = (x1(i).toLong/count(i).toLong).toString
      }
      temp
    }
  }

  def variance(count: Array[String], x1: Array[String], x2: Array[String] ): Array[String] = {
    if (count.length != x1.length || count.length != x2.length) {
      Array.empty
    } else {
      val temp = new Array[String](count.length)
      for (i <- count.indices) {
        temp(i) = (x2(i).toLong/count(i).toLong - math.pow(x1(i).toLong/count(i).toLong, 2)).toString
      }
      temp
    }
  }

  def normalize(count: Array[String], x1: Array[String], x2: Array[String] ): Array[String] = {
    if (count.length != x1.length || count.length != x2.length) {
      Array.empty
    } else {
      val mean = avg(count, x1)
      val varianceArray = variance(count, x1, x2)
      val temp = new Array[String](count.length)
      for (i <- count.indices) {
        temp(i) = ((x1(i).toDouble - mean(i).toDouble)/math.sqrt(varianceArray(i).toDouble)).toString
      }
      temp
    }
  }
}