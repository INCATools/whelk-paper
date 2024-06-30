//> using scala "2.13"
//> using dep "net.sourceforge.owlapi:owlapi-distribution:4.5.29"
//> using dep "com.outr::scribe-slf4j2:3.15.0"

import java.io.File
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.reasoner.InferenceType
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory
import scribe.Level
import scribe.writer.SystemErrWriter

object TimeClassification {

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

  def run(
      ontologyFile: String,
      reasonerFactory: OWLReasonerFactory
  ) = {
    val runs = 10
    val discard = 2
    val totalRuns = runs + discard
    val ontology = OWLManager
      .createOWLOntologyManager()
      .loadOntology(IRI.create(new File(ontologyFile)))
    println("runs:")
    val times = for {
      run <- 0 until totalRuns
    } yield {
      val (isCoherent, duration) = time {
        val reasoner = reasonerFactory.createReasoner(ontology)
        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)
        val coherent =
          reasoner.getBottomClassNode().getEntitiesMinusBottom().isEmpty()
        reasoner.dispose()
        coherent
      }
      if (run >= discard) println(s"  - $duration")
      duration
    }
    val timesDiscarded = times.drop(discard)
    val average = (timesDiscarded.sum) * 1.0 / timesDiscarded.size
    println(s"average: $average")
  }

}
