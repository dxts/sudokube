package experiments

import core.SolverTools.fastMoments
import core.{DataCube, RandomizedMaterializationScheme}
import core.solver.Strategy.{CoMomentFrechet, CoMoment3}
import core.solver.{Strategy, UniformSolver}
import experiments.CubeData.dc
import util.{AutoStatsGatherer, ManualStatsGatherer, Profiler}

import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.reflect.ClassTag

class UniformSolverOnlineExpt[T:Fractional:ClassTag](dc: DataCube, val name: String = "")(implicit  shouldRecord: Boolean) {
  val timestamp = if(shouldRecord) {
    val datetime = LocalDateTime.now
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(datetime)
  } else "dummy"
  //val lrf = math.log(dc.m.asInstanceOf[RandomizedMaterializationScheme].rf)/math.log(10)
  //val lbase = math.log(dc.m.asInstanceOf[RandomizedMaterializationScheme].base)/math.log(10)
  val fileout = new PrintStream(s"expdata/online_${name}_${timestamp}.csv")
  fileout.println("Name,Query,QSize,Counter,TimeElapsed(s),DOF,Error")
  println("Uniform Solver of type " + implicitly[ClassTag[T]])


  def error(naive: Array[Double], solver: Array[Double]) = {
    val length = naive.length
    val deviation = (0 until length).map(i => Math.abs(naive(i) - solver(i))).sum
    val sum = naive.sum
    deviation / sum
  }

  def compare(qu: Seq[Int], output: Boolean = true) = {
    val q = qu.sorted
    println(s"\nQuery size = ${q.size} \nQuery = " + qu)
    val qstr = qu.mkString(":")
    val s = new UniformSolver(q.size, CoMoment3)
    val stg = new ManualStatsGatherer(s.getStats)
    stg.start()
    val cheap_size = 30
    var l = dc.m.prepare_online_agg(q, cheap_size)
    //l.map(p => (p.accessible_bits, p.mask.length)).foreach(println)
    while (!(l.isEmpty) ) {
      val fetched = dc.fetch2(List(l.head))
      val bits = l.head.accessible_bits
      s.add(bits, fetched.toArray)
      s.fillMissing()
      s.fastSolve()
      stg.record()
      l = l.tail
    }
    stg.finish()

    val naiveRes = dc.naive_eval(q)
    //val naivecum = fastMoments(naiveRes)

    //println("Naive moments")
    //println(naivecum.map(_.toLong).mkString("", " ", "\n"))

    if(output) {
      stg.stats.foreach { case (time, count, (dof, sol)) =>
        val err = error(naiveRes, sol)
        println(s"$count @ $time : dof=$dof err=$err")
        fileout.println(s"$name,$qstr,${q.size},$count,${time},$dof,$err")
      }
    }
  }

}