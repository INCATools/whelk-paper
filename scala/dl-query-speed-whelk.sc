//> using scala "2.13"
//> using dep "org.geneontology::whelk-owlapi:1.2.1"
//> using file dl-query-speed.scala

import org.geneontology.whelk.owlapi.WhelkOWLReasonerFactory

val ontologyFile = args(0)
val parallelism = args(1).toInt
OntologyExpressions.timeQueries(
  new WhelkOWLReasonerFactory(),
  ontologyFile,
  parallelism
)
