//> using scala "2.13"
//> using dep "io.github.liveontologies:elk-owlapi:0.6.0"
//> using file classify-speed.scala

import org.semanticweb.elk.owlapi.ElkReasonerFactory

val ontologyFile = args(0)
TimeClassification.run(ontologyFile, new ElkReasonerFactory())
