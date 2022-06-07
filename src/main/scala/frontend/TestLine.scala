package frontend


case object TestLine {
  def testLineOp(op: OPERATOR, splitString: Array[String], q_unsorted: List[(String, List[String])]): Boolean = {
    val newString = splitString.filter(x => q_unsorted.map(y => y._1).contains(x.split("=")(0)))
    if (q_unsorted.isEmpty) {
      return false
    }
    op match {
      case AND => testLineAnd(newString.sortBy(_.toLowerCase), q_unsorted.sortBy(_._1.toLowerCase), 0)
      case OR => testLineOr(newString.sortBy(_.toLowerCase), q_unsorted.sortBy(_._1.toLowerCase), 0)
      case _ => false
    }
  }

  /**
   * recursively checks if the provided string checks all the criteria of qV_sorted
   *
   * @param splitString string to test
   * @param q_sorted    criteria, in form (field, list of acceptable values)
   * @param n           index of field considered
   * @return true <=> one of the condition is not fulfilled
   */
  def testLineAnd(splitString: Array[String], q_sorted: List[(String, List[String])], n: Int): Boolean = {
    if (n != splitString.length) {
      if (q_sorted(n)._2.isEmpty) {
        return testLineAnd(splitString, q_sorted, n + 1)
      }
      for (i <- q_sorted(n)._2.indices) {
        q_sorted(n)._2(i) match {
          case s if s.startsWith("!") =>
            //when specified that the string must NOT equal a value
            if (!splitString(n).contains(q_sorted(n)._2(i))) {
              return testLineAnd(splitString, q_sorted, n + 1)
            }
          case s if s.startsWith(">=") =>
            //retrieve all the numbers in this string, and test if one of them matches the criterion
            if (("""\d+""".r findAllIn splitString(n)).toList.exists(string => string.toFloat >= s.replaceFirst(">=", "").toFloat)) {
              return testLineAnd(splitString, q_sorted, n + 1)
            }
          case s if s.startsWith("<=") =>
            //retrieve all the numbers in this string, and test if one of them matches the criterion
            if (("""\d+""".r findAllIn splitString(n)).toList.exists(string => string.toFloat <= s.replaceFirst("<=", "").toFloat)) {
              return testLineAnd(splitString, q_sorted, n + 1)
            }
          case s if s.startsWith(">") =>
            //retrieve all the numbers in this string, and test if one of them matches the criterion

            if (("""\d+""".r findAllIn splitString(n)).toList.exists(string => string.toFloat > s.replaceFirst(">", "").toFloat)) {
              return testLineAnd(splitString, q_sorted, n + 1)
            }

          case s if s.startsWith("<") =>
            //retrieve all the numbers in this string, and test if one of them matches the criterion

            if (("""\d+""".r findAllIn splitString(n)).toList.exists(string => string.toFloat < s.replaceFirst("<", "").toFloat)) {
              return testLineAnd(splitString, q_sorted, n + 1)
            }
          case _ =>
            if (splitString(n).contains(q_sorted(n)._2(i))) {
              return testLineAnd(splitString, q_sorted, n + 1)
            }
        }
      }
      true

    } else {
      false
    }
  }

  /**
   * recursively checks if the provided string checks one of the criteria of qV_sorted
   *
   * @param splitString string to test
   * @param q_sorted    criteria, in form (field, list of acceptable values)
   * @param n           index of field considered
   * @return true <=> all the conditions are not fulfilled
   */
  def testLineOr(splitString: Array[String], q_sorted: List[(String, List[String])], n: Int): Boolean = {
    if (n != splitString.length) {
      if (q_sorted(n)._2.isEmpty) {
        return false
      }
      for (i <- q_sorted(n)._2.indices) {
        q_sorted(n)._2(i) match {
          case s if s.startsWith("!") =>
            //when specified that the string must NOT equal a value
            if (!splitString(n).contains(q_sorted(n)._2(i))) {
              return false
            }
          case s if s.startsWith(">=") =>
            //retrieve all the numbers in this string, and test if one of them matches the criterion
            if (("""\d+""".r findAllIn splitString(n)).toList.exists(string => string.toFloat >= s.replaceFirst(">=", "").toFloat)) {
              return false
            }
          case s if s.startsWith("<=") =>
            //retrieve all the numbers in this string, and test if one of them matches the criterion
            if (("""\d+""".r findAllIn splitString(n)).toList.exists(string => string.toFloat <= s.replaceFirst("<=", "").toFloat)) {
              return false
            }
          case s if s.startsWith(">") =>
            //retrieve all the numbers in this string, and test if one of them matches the criterion
            if (("""\d+""".r findAllIn splitString(n)).toList.exists(string => string.toFloat > s.replaceFirst(">", "").toFloat)) {
              return false
            }
          case s if s.startsWith("<") =>
            //retrieve all the numbers in this string, and test if one of them matches the criterion
            if (("""\d+""".r findAllIn splitString(n)).toList.exists(string => string.toFloat < s.replaceFirst("<", "").toFloat)) {
              return false
            }
          case _ =>
            if (splitString(n).contains(q_sorted(n)._2(i))) {
              return false
            }
        }
      }
      testLineOr(splitString, q_sorted, n + 1)

    } else {
      true
    }
  }
}
