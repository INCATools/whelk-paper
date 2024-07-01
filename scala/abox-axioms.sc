//> using scala "2.13"
//> using dep "net.sourceforge.owlapi:owlapi-distribution:4.5.29"

import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.apibinding.OWLManager
import java.io.File

val modelsFolder = args(0)
val manager = OWLManager.createOWLOntologyManager()
val models = new File(modelsFolder)
  .listFiles()
  .foreach { file =>
    val model = manager.loadOntologyFromOntologyDocument(file)
    println(model.getLogicalAxiomCount(Imports.EXCLUDED))
  }
