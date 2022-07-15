//package ch.epfl.data.sudokube
package core

import backend._
import core.ds.settrie.SetTrieForMoments
import core.materialization.MaterializationScheme
import core.materialization.builder._
import core.prepare.Preparer
import core.cube.CuboidIndex
import core.solver.lpp.{SliceSparseSolver, SparseSolver}
import core.solver.moment.Strategy._
import core.solver.moment._
import planning.ProjectionMetaData
import util._

import java.io._
import solver._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, future}

/** To create a DataCube, must either
  * (1) call DataCube.build(full_cube) or
  * (2) call (companion object) DataCube.load(..)
  * before use.
  *
  * An instance of the DataCube class has an associated MaterializationScheme
  * that holds the metadata -- the decisions on which cuboids to materialize.
  *
  * The DataCube instance itself stores the Cuboids -- the data or proxies
  * to the data held in the backend.
  */
@SerialVersionUID(2L)
class DataCube(val m: MaterializationScheme) extends Serializable {

  /* protected */
  var cuboids = Array[Cuboid]()
  var primaryMoments: (Long, Array[Long]) = null
  var index: CuboidIndex = null
  def showProgress = m.n_bits > 25

  /** this is too slow. */
  def simple_build(full_cube: Cuboid) {
    cuboids = m.projections.map(bits => full_cube.rehash_to_sparse(
      Bits.mk_list_mask((0 to m.n_bits - 1), bits.toSet).toArray)).toArray
  }

  /**
    * Builds this cube using the base_cuboid of another DataCube
    */
  def buildFrom(that: DataCube): Unit = {
    val full_cube = that.cuboids.last
    build(full_cube)
  }

  /** build each cuboid from a smallest that subsumes it -- using
    * MaterializationScheme.create_build_plan().
    * has to be called explicitly, otherwise no data is created.
    * In the end, the full_cube is at position cuboids.last (this is consistent
    * with the order in m.projections).
    *
    * @param full_cube is integrated into the data cube as is and not copied.
    */
  def build(full_cube: Cuboid, cb: CubeBuilder = SimpleCubeBuilderMT) {
    assert(full_cube.n_bits == m.n_bits)
    cuboids = cb.build(full_cube, m, showProgress).toArray

    val real_size = cuboids.map(c => c.size).sum
    // statistics output
    if (showProgress) {
      println("\nReal size: " + real_size + " data points ("
        + real_size.toDouble / full_cube.size.toLong + "x base cube size).")
    }
  }

  /** load the cuboids from files. Do not call this directly, but call
    * the load method of the companion object instead.
    *
    * @param l a list of (isSparseCuboid, n_bits, size) triples, one list
    *          entry for each cuboid to load.
    * @deprecated Use load2 for multiple cuboids from a single file
    */
  protected def load(backend: Backend[Payload], l: List[(Boolean, Int, BigInt)], name_prefix: String) {
    cuboids = (l.zipWithIndex.map {
      case ((sparse, n_bits, size), id) =>
        backend.readCuboid(id, sparse, n_bits, size, name_prefix)
    }).toArray
  }

  /**
    * Loads cuboids from files. Do not call this directly, but invoke the load2 method of the companion object.
    *
    * @param be                Backend
    * @param multicuboidLayout List of layout information, one per file. For each  file, we have
    *                          List[CuboidId], List[isSparse], List[NBits], List[NRows]
    */
  def load2(be: Backend[Payload], multicuboidLayout: List[(List[Int], List[Boolean], List[Int], List[BigInt])], parentDir: String): Unit = {
    cuboids = new Array[Cuboid](m.projections.length)
    implicit val ec = ExecutionContext.global
    //println(s"Reading cuboids from disk")

    val futures = multicuboidLayout.zipWithIndex.map { case ((idList, sparseList, nbitsList, sizeList), mcid) =>
      Future {
        val filename = parentDir + s"/multicube_$mcid.csuk"
        //WARNING Converting size of cuboid from BigInt to Int
        val cmap = be.readMultiCuboid(filename, idList.toArray, sparseList.toArray, nbitsList.toArray, sizeList.map(_.toInt).toArray)
        cmap.foreach { case (cid, cub) => cuboids(cid) = cub }
      }
    }
    Await.result(Future.sequence(futures), Duration.Inf)
  }

  /** write the DataCube's metadata and cuboid data to a file.
    *
    * @param filename is the filename used for the metadata.
    */
  def save(filename: String) {
    val be = cuboids(0).backend
    val file = new File("cubedata/" + filename + "/" + filename + ".dc")
    if (!file.exists())
      file.getParentFile.mkdirs()
    val l: List[(Boolean, Int, BigInt)] = (cuboids.zipWithIndex.map {
      case (c, id) => {
        val sparse = c.isInstanceOf[be.SparseCuboid];
        be.writeCuboid(id, c, file.getParent); // this actually goes to the backend.
        (sparse, c.n_bits, c.size)
      }
    }).toList

    val oos = new ObjectOutputStream(new FileOutputStream(file))
    oos.writeObject(m)
    oos.writeObject(l)
    oos.close
  }

  final val threshold = 1000 * 1000 * 200

  def save2(filename: String) {
    val be = cuboids(0).backend
    val file = new File("cubedata/" + filename + "/" + filename + ".dc2")
    if (!file.exists())
      file.getParentFile.mkdirs()

    //pack cuboids upto 10 MB into a single file
    val multiCuboids = cuboids.zipWithIndex.foldLeft((Map[Int, Cuboid]() :: Nil) -> BigInt(0)) {
      case ((list, total), (cub, id)) => if ((total + cub.numBytes) < threshold) {
        ((list.head + (id -> cub)) :: list.tail) -> (total + cub.numBytes)
      } else {
        (Map(id -> cub) :: list) -> cub.numBytes
      }
    }._1

    type MultiCuboid = Map[Int, Cuboid]

    def extractCuboidData(mc: MultiCuboid) = {
      mc.foldLeft((List[Int](), List[Boolean](), List[Int](), List[BigInt]())) {
        case ((idList, sparseList, nbitsList, sizeList), (id, cub)) =>
          (id :: idList, cub.isInstanceOf[be.SparseCuboid] :: sparseList, cub.n_bits :: nbitsList, cub.size :: sizeList)
      }
    }

    val oos = new ObjectOutputStream(new FileOutputStream(file))
    oos.writeObject(m)
    val multiCuboidLayoutData = multiCuboids.map(extractCuboidData)
    oos.writeObject(multiCuboidLayoutData)
    oos.close

    implicit val ec = ExecutionContext.global
    println(s"Writing cuboids to disk")
    val threadTasks = multiCuboids.zipWithIndex.map { case (mc, mcid) =>
      Future {
        val filename = file.getParent + s"/multicube_$mcid.csuk"
        //layout data is written in reverse order, so we reverse it here too
        be.writeMultiCuboid(filename, mc.values.toArray.reverse)
      }
    }
    Await.result(Future.sequence(threadTasks), Duration.Inf)
  }


  /** This is the only place where we transfer data from C into Scala.
    */
  def fetch(pms: Seq[ProjectionMetaData]): Array[Payload] = {
    if (cuboids.length == 0) throw new Exception("Need to build first!")

    val backend = cuboids(0).backend

    (for (pm <- pms) yield {
      val c = cuboids(pm.id).rehash_to_dense(pm.mask.toArray)
      //val maskString = pm.mask.mkString("")
      //println(s"Fetch mask=$maskString  maskLen = ${pm.mask.length}  origSize=${cuboids(pm.id).size}  newSize=${c.size}")
      c.asInstanceOf[backend.DenseCuboid].fetch.asInstanceOf[Array[Payload]]
    }).flatten.toArray
  }

  /** Gets rid of the Payload box. */
  def fetch2[T](pms: Seq[ProjectionMetaData]
               )(implicit num: Fractional[T]): Seq[T] = {
    fetch(pms).map(p => Util.fromLong(p.smLong))
  }

  /** returns a solver for a given query. One needs to explicitly compute
    * and extract bounds from it. (See the code of DataCube.online_agg() for
    * an example.)
    */
  def solver[T](query: List[Int], max_fetch_dim: Int
               )(implicit num: Fractional[T]): SparseSolver[T] = {

    val l = Profiler("SolverPrepare") {
      Preparer.default.prepareBatch(m, query, max_fetch_dim)
    }
    val bounds = SolverTools.mk_all_non_neg[T](1 << query.length)

    val data = Profiler("SolverFetch") {
      fetch2(l)
    }
    Profiler("SolverConstructor") {
      new SliceSparseSolver[T](query.length, bounds, l.map(_.accessible_bits), data)
    }
  }

  /** lets us provide a callback function that is called for increasing
    bit depths of relevant projections (as provided by
    MaterializationScheme.prepare_online_agg()).

    @param cheap_size is the number of dimensions up to which cuboids
    can be prepared for the initial iteration. cheap_size refers
    to the actual dimensionality of the cuboid, not to their
    dimensionality after projection to dimensions occurring in the
    query.

    @example {{{
    import frontend.experiments.Tools._
    import core._
    import RationalTools._

    class CB {
    var bounds : Option[collection.mutable.ArrayBuffer[Interval[Rational]]] = None

    def callback(s: SparseSolver[Rational]) = {
    println(s.bounds)
    bounds = Some(s.bounds)
    true
    }
    }
    }}}
    */
  def online_agg[T](
    query: List[Int],
    cheap_size: Int,
    callback: SparseSolver[T] => Boolean, // returns whether to continue
    sliceFunc: Int => Boolean = _ => true // determines what variables to filter
  )(implicit num: Fractional[T]) {

    var l = Preparer.default.prepareOnline(m, query, cheap_size, m.n_bits)
    val bounds = SolverTools.mk_all_non_neg[T](1 << query.length)
    val s = new SliceSparseSolver[T](query.length, bounds, List(), List(), sliceFunc)
    var df = s.df
    var cont = true
    println("START ONLINE AGGREGATION")
    while ((!l.isEmpty) && (df > 0) && cont) {
      if (s.shouldFetch(l.head.accessible_bits)) { // something can be added
        println(l.head.accessible_bits)

        s.add2(List(l.head.accessible_bits), fetch2(List(l.head)))
        //TODO: Probably gauss not required if newly added varibales are first rewritten in terms of non-basic
        s.gauss(s.det_vars)
        s.compute_bounds
        cont = callback(s)
        df = s.df
      }
      l = l.tail
    }
  }

  def online_agg_moment(query: List[Int], cheap_size: Int, callback: MomentSolverAll[Double] => Boolean) = {
    val s = new MomentSolverAll[Double](query.size, CoMoment3)
    var l = Preparer.default.prepareOnline(m, query, cheap_size, m.n_bits)
    var cont = true
    while (!(l.isEmpty) && cont) {
      val fetched = fetch2[Double](List(l.head))
      val bits = l.head.accessible_bits
      s.add(bits, fetched.toArray)
      s.fillMissing()
      s.fastSolve()
      cont = callback(s)
      l = l.tail
    }
  }

  /** fetches the smallest subsuming cuboid and projects away columns not in
    * the query. Does not involve a solver.
    */
  def naive_eval(query: Seq[Int]): Array[Double] = {
    val l = Profiler("NaivePrepare") {
      Preparer.default.prepareBatch(m, query, m.n_bits)
    }
    //println("Naive query "+l.head.mask.sum + "  fetch = " + l.head.mask.length)
    Profiler("NaiveFetch") {
      fetch(l).map(p => p.sm.toDouble)
    }
  }

  def loadTrie(cubename: String) = {
    val filename = s"cubedata/${cubename}_trie/${cubename}.trie"
    val file = new File(filename)
    val in = new ObjectInputStream(new FileInputStream(file))
    in.readObject().asInstanceOf[SetTrieForMoments]
  }

  def saveAsTrie(cubename: String) = {
    val filename = s"cubedata/${cubename}_trie/${cubename}.ctrie"
    val file = new File(filename)
    if (!file.exists())
      file.getParentFile.mkdirs()

    val maxsize = 1L << 30
    val dim = 25
    val be = cuboids.head.backend
    val cubs = m.projections.zipWithIndex.filter(_._1.length <= dim).map { case (cols, cid) =>
      val n = cols.length
      val cuboid = cuboids(cid)
      assert(cuboid.n_bits == n)

      val be_cid = cuboid match {
        case d: be.DenseCuboid => be.denseToHybrid(d.data)
        case s: be.SparseCuboid => be.sparseToHybrid(s.data)
      }
      cols.sorted.toArray -> be_cid
    }.toArray
    be.saveAsTrie(cubs, filename, maxsize)
  }

  def savePrimaryMoments(cubename: String) = {
    val filename = s"cubedata/$cubename/${cubename}.m1s"
    val out = new ObjectOutputStream(new FileOutputStream(filename))
    out.writeObject(primaryMoments)
  }

  def loadPrimaryMoments(cubename: String) = {
    val filename = s"cubedata/$cubename/${cubename}.m1s"
    val in = new ObjectInputStream(new FileInputStream(filename))
    primaryMoments = in.readObject().asInstanceOf[(Long, Array[Long])]
  }
} // end DataCube

/**
  * DataCube that builds projections from another data cube that contains only the full cuboid.
  * Does not materialize the full cuboid again.
  * Several PartialDataCubes can share the same full cuboid.
  *
  * @param m        Materialization Scheme
  * @param basename The name of the data cube storing the full cuboid. This will be fetched at runtime.
  */
class PartialDataCube(m: MaterializationScheme, basename: String) extends DataCube(m) {
  val base = DataCube.load2(basename)

  def build() = buildFrom(base)

  override def load2(be: Backend[Payload], multicuboidLayout: List[(List[Int], List[Boolean], List[Int], List[BigInt])], parentDir: String): Unit = {
    cuboids = new Array[Cuboid](m.projections.length)
    implicit val ec = ExecutionContext.global
    //println(s"Reading cuboids from disk")
    val tasks = multicuboidLayout.zipWithIndex.map { case ((idList, sparseList, nbitsList, sizeList), mcid) =>
      Future {
        val filename = parentDir + s"/multicube_$mcid.csuk"
        //WARNING Converting size of cuboid from BigInt to Int
        val cmap = be.readMultiCuboid(filename, idList.toArray, sparseList.toArray, nbitsList.toArray, sizeList.map(_.toInt).toArray)
        cmap.foreach { case (cid, cub) => cuboids(cid) = cub }
      }
    }
    val last = cuboids.indices.last
    assert(cuboids(last) == null)
    cuboids(last) = DataCube.load2(basename).cuboids.last
    Await.result(Future.sequence(tasks), Duration.Inf)
  }

  override def save2(filename: String): Unit = {
    val be = cuboids(0).backend
    val file = new File("cubedata/" + filename + "_partial/" + filename + ".pdc")
    if (!file.exists())
      file.getParentFile.mkdirs()

    //do not materialize base cuboid
    val multiCuboids = cuboids.dropRight(1).zipWithIndex.foldLeft((Map[Int, Cuboid]() :: Nil) -> BigInt(0)) {
      case ((list, total), (cub, id)) => if ((total + cub.numBytes) < threshold) {
        ((list.head + (id -> cub)) :: list.tail) -> (total + cub.numBytes)
      } else {
        (Map(id -> cub) :: list) -> cub.numBytes
      }
    }._1

    type MultiCuboid = Map[Int, Cuboid]

    def extractCuboidData(mc: MultiCuboid) = {
      mc.foldLeft((List[Int](), List[Boolean](), List[Int](), List[BigInt]())) {
        case ((idList, sparseList, nbitsList, sizeList), (id, cub)) =>
          (id :: idList, cub.isInstanceOf[be.SparseCuboid] :: sparseList, cub.n_bits :: nbitsList, cub.size :: sizeList)
      }
    }

    val oos = new ObjectOutputStream(new FileOutputStream(file))
    oos.writeObject(m)
    val multiCuboidLayoutData = multiCuboids.map(extractCuboidData)
    oos.writeObject(multiCuboidLayoutData)
    oos.close

    implicit val ec = ExecutionContext.global
    println(s"Writing cuboids to disk")
    val threadTasks = multiCuboids.zipWithIndex.map { case (mc, mcid) =>
      Future {
        val filename = file.getParent + s"/multicube_$mcid.csuk"
        //layout data is written in reverse order, so we reverse it here too
        be.writeMultiCuboid(filename, mc.values.toArray.reverse)
      }
    }
    Await.result(Future.sequence(threadTasks), Duration.Inf)
  }
}

object DataCube {
  /** creates and loads a DataCube.
     @param filename is the name of the metadata file.

     @example{{{
        val dc = frontend.experiments.Tools.mkDC(10, 0.5, 1.8, 100,
        backend.ScalaBackend)
        dc.save("hello")
        core.DataCube.load("hello", backend.ScalaBackend)
     }}}
    */
  def load(filename: String, be: Backend[Payload] = CBackend.b): DataCube = {
    val file = new File("cubedata/" + filename + "/" + filename + ".dc")
    val ois = new ObjectInputStream(new FileInputStream(file))
    val m = ois.readObject.asInstanceOf[MaterializationScheme]
    val l = ois.readObject.asInstanceOf[List[(Boolean, Int, BigInt)]]
    ois.close

    val dc = new DataCube(m)
    dc.load(be, l, file.getParent)

    dc
  }

  def load2(filename: String, be: Backend[Payload] = CBackend.b): DataCube = {
    val file = new File("cubedata/" + filename + "/" + filename + ".dc2")
    val ois = new ObjectInputStream(new FileInputStream(file))
    val m = ois.readObject.asInstanceOf[MaterializationScheme]
    //println("Loading MultiCuboidLayout...")
    val multiCuboidLayoutData = ois.readObject.asInstanceOf[List[(List[Int], List[Boolean], List[Int], List[BigInt])]]
    ois.close
    //println("MultiCuboidLayout loaded")
    val dc = new DataCube(m)
    dc.load2(be, multiCuboidLayoutData, file.getParent)

    dc
  }
}

object PartialDataCube {
  def load2(filename: String, basename: String, be: Backend[Payload] = CBackend.b) = {
    val file = new File("cubedata/" + filename + "_partial/" + filename + ".pdc")
    val ois = new ObjectInputStream(new FileInputStream(file))
    val m = ois.readObject.asInstanceOf[MaterializationScheme]
    //println("Loading MultiCuboidLayout...")
    val multiCuboidLayoutData = ois.readObject.asInstanceOf[List[(List[Int], List[Boolean], List[Int], List[BigInt])]]
    ois.close
    //println("MultiCuboidLayout loaded")
    val dc = new PartialDataCube(m, basename)
    dc.load2(be, multiCuboidLayoutData, file.getParent)
    dc
  }
}

