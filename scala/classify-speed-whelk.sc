//> using scala "2.13"
//> using dep "org.geneontology::whelk-owlapi:1.1.2"
//> using file classify-speed.scala

import org.geneontology.whelk.owlapi.WhelkOWLReasonerFactory

val ontologyFile = args(0)
TimeClassification.run(ontologyFile, new WhelkOWLReasonerFactory())
