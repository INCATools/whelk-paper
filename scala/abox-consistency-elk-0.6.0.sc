//> using scala "2.13"
//> using dep "net.sourceforge.owlapi:owlapi-distribution:4.5.29"
//> using dep "com.outr::scribe-slf4j2:3.15.0"
//> using dep "io.github.liveontologies:elk-owlapi:0.6.0"
//> using dep "dev.zio::zio-streams:2.1.4"

import scala.jdk.CollectionConverters._
import org.semanticweb.owlapi.model.OWLClassExpression
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import java.io.File
import org.semanticweb.owlapi.reasoner.InferenceType
import scribe.Level
import scribe.writer.SystemErrWriter
import org.semanticweb.owlapi.reasoner.OWLReasoner
import zio._
import zio.stream._
import java.lang.System
import java.util.Arrays
import org.semanticweb.elk.owlapi.ElkReasonerFactory

scribe.Logger.root
  .clearHandlers()
  .clearModifiers()
  .withHandler(minimumLevel = Some(Level.Error), writer = SystemErrWriter)
  .replace()
def time[T](operation: => T): (T, Double) = {
  val start = System.nanoTime()
  val output = operation
  val stop = System.nanoTime()
  val seconds = (stop - start) / 1_000_000_000.0;
  (output, seconds)
}
val ontologyFile = args(0)
val modelsFolder = args(1)
val parallelism = args(2).toInt
val manager = OWLManager.createConcurrentOWLOntologyManager()
val ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyFile))
val reasoner = new ElkReasonerFactory().createReasoner(ontology)
val models = new File(modelsFolder)
  .listFiles()
  .map(manager.loadOntologyFromOntologyDocument(_))
  .toList
def isConsistent(model: OWLOntology) = {
  ontology.synchronized {
    manager.addAxioms(ontology, model.getAxioms())
    reasoner.flush()
    val isConsistent = reasoner.isConsistent()
    manager.removeAxioms(ontology, model.getAxioms())
    isConsistent
  }
}
def runParallel(models: List[OWLOntology]) = {
  val runtime = Runtime.default
  Unsafe.unsafe { implicit unsafe =>
    runtime.unsafe
      .run {
        ZStream
          .fromIterable(models)
          .mapZIOParUnordered(parallelism) { model =>
            ZIO.succeed(isConsistent(model))
          }
          .runCollect
          .map(_.toList)
      }
      .getOrThrowFiberFailure()
  }
}
val runs = 3
val warmup =
  if (parallelism > 1) runParallel(models)
  else models.map(isConsistent)
println("runs:")
val times = for {
  run <- 0 until runs
} yield {
  val (results, duration) = time {
    models.map { model =>
      manager.addAxioms(ontology, model.getAxioms())
      reasoner.flush()
      val isConsistent = reasoner.isConsistent()
      manager.removeAxioms(ontology, model.getAxioms())
      isConsistent
    }
  }
  val mps = (models.size).toDouble / duration
  println(s"  - total_time: $duration")
  println(s"    models_per_second: $mps")
  println(s"    inconsistent: ${results.filter(_ == false).size}")
  mps
}
val average = (times.sum) * 1.0 / runs.toDouble
println(s"average: $average")
