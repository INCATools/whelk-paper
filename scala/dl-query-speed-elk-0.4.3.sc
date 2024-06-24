//> using scala "2.13"
//> using dep "org.semanticweb.elk:elk-owlapi:0.4.3"
//> using file dl-query-speed.scala

import org.semanticweb.elk.owlapi.ElkReasonerFactory

val ontologyFile = args(0)
val parallelism = args(1).toInt
OntologyExpressions.timeQueries(
  new ElkReasonerFactory(),
  ontologyFile,
  parallelism
)
