//> using scala "2.13"
//> using dep "org.geneontology::whelk-owlapi:1.1.2"
//> using dep "io.github.liveontologies:elk-owlapi:0.6.0"
//> using dep "com.outr::scribe-slf4j2:3.15.0"

import java.io.File
import scala.jdk.CollectionConverters._
import scribe.Level
import scribe.writer.SystemErrWriter
import org.geneontology.whelk.owlapi.WhelkOWLReasonerFactory
import org.semanticweb.elk.owlapi.ElkReasonerFactory
//import au.csiro.snorocket.owlapi.SnorocketReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.reasoner.InferenceType

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
val ontology = OWLManager
  .createOWLOntologyManager()
  .loadOntologyFromOntologyDocument(new File(ontologyFile))
val elk = new ElkReasonerFactory().createReasoner(ontology)
val classes = ontology.getClassesInSignature().asScala.toSet
val elkSubsumptions = classes.flatMap { c =>
  elk.getEquivalentClasses(c).getEntities().asScala.to(Set).map(c -> _) ++
    elk.getSuperClasses(c, false).getFlattened().asScala.to(Set).map(c -> _)
}
elk.dispose()
val whelk = new WhelkOWLReasonerFactory().createReasoner(ontology)
val whelkSubsumptions = classes.flatMap { c =>
  elk.getEquivalentClasses(c).getEntities().asScala.to(Set).map(c -> _) ++
    elk.getSuperClasses(c, false).getFlattened().asScala.to(Set).map(c -> _)
}
whelk.dispose()
// val snorocket = new SnorocketReasonerFactory().createReasoner(ontology)
// val snorocketSubsumptions = classes.flatMap { c =>
//   snorocket.getEquivalentClasses(c).getEntities().asScala.to(Set).map(c -> _) ++
//     snorocket
//       .getSuperClasses(c, false)
//       .getFlattened()
//       .asScala
//       .to(Set)
//       .map(c -> _)
// }
println(s"elk_subsumptions: ${elkSubsumptions.size}")
println(s"whelk_subsumptions: ${whelkSubsumptions.size}")
//println(s"snorocket_subsumptions: ${snorocketSubsumptions.size}")
println(s"elk_whelk_same: ${elkSubsumptions == whelkSubsumptions}")
//println(s"snorocket_whelk_same: ${snorocketSubsumptions == whelkSubsumptions}")
//println(s"elk_snorocket_same: ${snorocketSubsumptions == elkSubsumptions}")
