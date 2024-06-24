//> using scala "2.13"
//> using dep "org.semanticweb.elk:elk-owlapi:0.4.3"
//> using file classify-speed.scala

import org.semanticweb.elk.owlapi.ElkReasonerFactory

val ontologyFile = args(0)
TimeClassification.run(ontologyFile, new ElkReasonerFactory())
