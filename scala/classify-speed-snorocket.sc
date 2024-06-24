//> using scala "2.13"
//> using dep "au.csiro:snorocket-owlapi:4.0.1"
//> using file classify-speed.scala
import au.csiro.snorocket.owlapi.SnorocketReasonerFactory

val ontologyFile = args(0)
TimeClassification.run(ontologyFile, new SnorocketReasonerFactory())
