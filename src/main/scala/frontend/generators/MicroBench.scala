package frontend.generators

import core.{DataCube, MaterializationScheme}
import frontend.generators.MB_Sampler.{Exponential, LogNormal, Normal, Sampler, Uniform}
import frontend.schema.encoders.StaticNatCol.defaultToInt
import frontend.schema.encoders.{NatCol, StaticNatCol}
import frontend.schema.{LD2, Schema2, StaticSchema2, StructuredDynamicSchema}
import util.BigBinary

import scala.util.Random

object MB_Sampler extends Enumeration {
  type Sampler = Value
  val Uniform, Normal, LogNormal, Exponential = Value
}
case class MicroBench(n_bits: Int, sampler: Sampler) extends CubeGenerator(s"mb_${n_bits}_${sampler}") {
  println(s"Microbench $n_bits $sampler")
  override def schema(): Schema2 = {
    val enc = (0 until n_bits).map{i => new LD2(s"D$i", new StaticNatCol(0, 1, defaultToInt))}.toVector
    val sch = new StaticSchema2(enc)
    assert(sch.n_bits == n_bits)
    sch
  }
  def dc = DataCube.load2(inputname+"_all")
  def sampleValue = sampler match {
    case Uniform => Random.nextInt(1000)
    case Normal => Random.nextGaussian() * 150 + 500
    case Exponential => -200 * math.log(Random.nextDouble())
    case LogNormal => math.exp(Random.nextGaussian() * 2.31)
  }
  override def generate(): (StructuredDynamicSchema, Seq[(BigBinary, Long)]) = ???

  override def generate2(): (Schema2, IndexedSeq[(Int, Iterator[(BigBinary, Long)])]) =  {
    val sch = schema()
    val keys = (0 until 1 << n_bits)
    val kv = keys.map(k => BigBinary(k) -> sampleValue.toLong)
    sch -> Vector((kv.size, kv.iterator))
  }

}

object MicroBenchTest {
  def main(args: Array[String]) = {

    List(MicroBench(20, Uniform),  MicroBench(20, Normal)).
      //List(MicroBench(20, LogNormal), MicroBench(20, Exponential)).
      map{cg =>
      //cg.saveBase()
      val (sch, dc) = cg.loadBase()
      val dc2 = new DataCube(MaterializationScheme.all_cuboids(cg.n_bits))
      dc2.buildFrom(dc)
      dc2.save2(cg.inputname + "_all")
    }

    ()
  }
}
