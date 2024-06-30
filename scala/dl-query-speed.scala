//> using scala "2.13"
//> using dep "net.sourceforge.owlapi:owlapi-distribution:4.5.29"
//> using dep "com.outr::scribe-slf4j2:3.15.0"
//> using dep "dev.zio::zio-streams:2.1.4"
import scala.jdk.CollectionConverters._
import org.semanticweb.owlapi.model.OWLClassExpression
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.model.IRI
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

object OntologyExpressions {

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

  def getQueriesFromOntologyExpressions(
      ontology: OWLOntology
  ): List[OWLClassExpression] = (for {
    axiom <- ontology.getLogicalAxioms(Imports.INCLUDED).asScala
    expression <- axiom.getNestedClassExpressions.asScala
    if expression.isAnonymous
    if isOnlyNamedOrIntersectionOrSomeValuesFrom(expression)
  } yield expression).toSet.toList

  def isOnlyNamedOrIntersectionOrSomeValuesFrom(
      expression: OWLClassExpression
  ): Boolean = expression match {
    case _: OWLClass => true
    case s: OWLObjectSomeValuesFrom =>
      (s.getNestedClassExpressions.asScala - s)
        .forall(isOnlyNamedOrIntersectionOrSomeValuesFrom)
    case i: OWLObjectIntersectionOf =>
      (i.getNestedClassExpressions.asScala - i)
        .forall(isOnlyNamedOrIntersectionOrSomeValuesFrom)
    case _ => false
  }

  def timeQueries(
      reasonerFactory: OWLReasonerFactory,
      ontologyFile: String,
      parallelism: Int
  ) = {
    val ontology = OWLManager
      .createOWLOntologyManager()
      .loadOntology(IRI.create(new File(ontologyFile)))
    val reasoner = reasonerFactory.createReasoner(ontology)
    reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)
    val expressions = getQueriesFromOntologyExpressions(ontology)
    val total = expressions.size
    println(s"query_count: $total")
    val runs = 3
    val warmup =
      if (parallelism > 1) queryParallel(reasoner, expressions, parallelism)
      else querySequentially(reasoner, expressions)
    val times = for {
      run <- 0 until runs
    } yield {
      val (subclassesFound, duration) =
        if (parallelism > 1)
          time(queryParallel(reasoner, expressions, parallelism))
        else time(querySequentially(reasoner, expressions))
      val qps = total.toDouble / duration
      println(s"  - total_time: $duration")
      println(s"    queries_per_second: $qps")
      println(s"    total_subclasses_result: $subclassesFound")
      qps
    }
    val average = (times.sum) * 1.0 / runs.toDouble
    println(s"average: $average")
    println(s"parallelism: $parallelism")
    reasoner.dispose()
  }

  def querySequentially(
      reasoner: OWLReasoner,
      expressions: List[OWLClassExpression]
  ): Int = {
    expressions.map { expression =>
      reasoner
        .getSubClasses(expression, false)
        .getFlattened()
        .asScala
        .filterNot(_.isOWLNothing())
        .size
    }.sum
  }

  def queryParallel(
      reasoner: OWLReasoner,
      expressions: List[OWLClassExpression],
      parallelism: Int
  ): Int = {
    val runtime = Runtime.default
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe
        .run {
          ZStream
            .fromIterable(expressions)
            .mapZIOParUnordered(parallelism) { expression =>
              ZIO
                .succeed {
                  reasoner
                    .getSubClasses(expression, false)
                    .getFlattened()
                    .asScala
                    .filterNot(_.isOWLNothing())
                    .size
                }
            }
            .runSum
        }
        .getOrThrowFiberFailure()
    }
  }

}
