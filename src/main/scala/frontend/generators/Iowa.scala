package frontend.generators

import backend.CBackend
import core.{DataCube, RandomizedMaterializationScheme}
import experiments.UniformSolverExpt
import frontend.schema.{BD2, BitPosRegistry, LD2, StructuredDynamicSchema}
import frontend.schema.encoders.{DateCol, MemCol, NestedMemCol, PositionCol}
import util.Profiler
import core.RationalTools._

import java.text.SimpleDateFormat
import java.util.Date
import scala.io.Source

object Iowa {
  def read(name: String) = {
    val filename = s"tabledata/Iowa/$name.tsv"
    val data =  Source.fromFile(filename, "utf-8").getLines().map(_.split("\t").toVector).toVector

    val header = data.head
    val keyCols = Vector("Date", "County Number", "City", "Zip Code", "Store Location", "Store Number", "Item Number", "Category", "Vendor Number")

    val keyIdx = keyCols.map(c => header.indexOf(c))
    val valueIdx = header.indexOf("Sale (Dollars)")
    val join = data.tail.map{ r =>
      val f1 = new SimpleDateFormat("MM/dd/yyyy")
      val key = keyIdx.map{
        case 1 => try { f1.parse(r(1))} catch { case e: Exception => new Date(2012, 0, 1) }
        case i => r(i)
      }
      val valueStr = r(valueIdx)
      val value = if(valueStr.isEmpty) 0L else (valueStr.toDouble * 100).toLong
     (key, value)
    }

    def getDistinct[T](id: Int) = join.map(r => r._1(id).asInstanceOf[T]).distinct
    implicit val bpr = new BitPosRegistry
    val date = LD2[Date]("Date", new DateCol(2012, 2021, true, true))

    val countyvals = getDistinct(1)

    val county = LD2[String]("County Number", new MemCol(countyvals))
    val cityvals = getDistinct(2)
    val city = LD2[String]("City", new MemCol(cityvals))
    val zipvals = getDistinct(3)
    val zip = LD2[String]("Zip Code", new MemCol(zipvals))
    val longlatvals = join.map(r => r._1(4).asInstanceOf[String]).filter(!_.isEmpty).map(_.drop(7).dropRight(1).split(" ").map(_.toDouble))
    val longmin = longlatvals.map(_(0)).min
    val longmax = longlatvals.map(_(0)).max
    val latmin = longlatvals.map(_(1)).min
    val latmax = longlatvals.map(_(1)).max

    //val storeloc = LD2[(Double, Double)]("Store Location", new PositionCol((-96.63, 40.38), 2, (-90.2, 43.5)))
    val storeloc = LD2[(Double, Double)]("Store Location", new PositionCol((longmin, latmin), 2, (longmax, latmax)))
    val storevals = getDistinct(5)
    val store = LD2[String]("Store Number", new MemCol(storevals))
    val locDims = BD2("Location", Vector(county, city, zip, storeloc, store), false)

    val itemvals = getDistinct(6)
    val item = LD2[String]("Item Number", new MemCol(itemvals))
    val categoryvals = getDistinct(7)
    val category = LD2[String]("Category", new NestedMemCol(s => (s.take(3), s.drop(3)), categoryvals))
    val vendorvals = getDistinct(8)
    val vendor = LD2[String]("Vendor Number", new MemCol(vendorvals))

    val itemDims = BD2("Item", Vector(item, category, vendor), false)

    val sch = new StructuredDynamicSchema(Vector(date, locDims, itemDims))

    val R = join.map { case (key, value) => sch.encode_tuple(key) -> value}
    println(s"Bits = ${sch.n_bits}")
    (sch, R)
  }

  def save(inputname: String, lrf: Double, lbase: Double) = {
    val (sch, r) = read(inputname)
    val rf = math.pow(10, lrf)
    val base = math.pow(10, lbase)
    val dc = new DataCube(RandomizedMaterializationScheme(sch.n_bits, rf, base))
    sch.save(inputname)
    dc.build(CBackend.b.mk(sch.n_bits, r.toIterator))
    dc.save2(s"${inputname}_${lrf}_${lbase}")
    (sch, dc)
  }

  def loadAndSave(inputname: String, lrf1: Double, lbase1: Double, lrf2: Double, lbase2: Double) = {

    val rf2 = math.pow(10, lrf2)
    val base2 = math.pow(10, lbase2)
    val dc1 = DataCube.load2(s"${inputname}_${lrf1}_${lbase1}")
    val dc2 = new DataCube(RandomizedMaterializationScheme(dc1.m.n_bits, rf2, base2))

    dc2.buildFrom(dc1)
    dc2.save2(s"${inputname}_${lrf2}_${lbase2}")
  }
  def load(inputname: String, lrf: Double, lbase: Double) = {
    val sch = StructuredDynamicSchema.load(inputname)
    sch.columnVector.map(c => c.name -> c.encoder.bits).foreach(println)
    val dc = DataCube.load2(s"${inputname}_${lrf}_${lbase}")
    (sch, dc)
  }

  def main(args: Array[String]) = {

    val name = "Iowa200k"
    val lrf = -9
    val lbase = 0.13

    //val name = "IowaAll"
    //val lrf = -16
    //val lbase = 0.19

    //val (sch, dc) = save(name, lrf, lbase)
    val (sch, dc) = load(name, lrf, lbase)
    //loadAndSave("IowaAll", 0, -1, -16, 0.19)
    val qs = sch.queries
    val expt = new UniformSolverExpt(dc, s"${name}-${lrf}-${lbase}")
    expt.compare(List(12, 77, 76, 75, 74))
    //SBJ: TODO  Bug in Encoder means that all bits are shifed by one. The queries with max bit cannot be evaluated
    //qs.filter(x => x.length >= 4  && x.length <= 10 && !x.contains(sch.n_bits)).foreach(q => expt.compare(q))
    ()
  }
}
