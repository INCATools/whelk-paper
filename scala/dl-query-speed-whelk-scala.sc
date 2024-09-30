//> using scala "2.13"
//> using dep "net.sourceforge.owlapi:owlapi-distribution:4.5.29"
//> using dep "com.outr::scribe-slf4j2:3.15.0"
//> using dep "dev.zio::zio-streams:2.1.4"
//> using dep "org.geneontology::whelk-owlapi:1.2.1"

import org.geneontology.whelk.owlapi.WhelkOWLReasonerFactory
import scala.jdk.CollectionConverters._
import org.semanticweb.owlapi.model.OWLClassExpression
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import java.io.File
import org.semanticweb.owlapi.reasoner.InferenceType
import scribe.Level
import scribe.writer.SystemErrWriter
import org.geneontology.whelk.owlapi.WhelkOWLReasonerFactory
import org.semanticweb.owlapi.reasoner.OWLReasoner
import zio._
import zio.stream._
import java.lang.System
import java.util.UUID
import org.geneontology.whelk.{
  AtomicConcept,
  Bridge,
  BuiltIn,
  ConceptAssertion,
  ConceptInclusion,
  ExistentialRestriction,
  Nominal,
  Reasoner,
  ReasonerState,
  Role,
  RoleAssertion,
  Individual => WhelkIndividual
}

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
    ontologyFile: String,
    parallelism: Int
) = {
  val ontology = OWLManager
    .createOWLOntologyManager()
    .loadOntologyFromOntologyDocument(new File(ontologyFile))
  val whelk = Reasoner.assert(Bridge.ontologyToAxioms(ontology))
  val expressions = getQueriesFromOntologyExpressions(ontology)
  val total = expressions.size
  println(s"query_count: $total")
  val warmup =
    if (parallelism > 1) queryParallel(whelk, expressions, parallelism)
    else querySequentially(whelk, expressions)
  val runs = 3
  println("runs:")
  val times = for {
    run <- 0 until runs
  } yield {
    val (subclassesFound, duration) =
      if (parallelism > 1)
        time(queryParallel(whelk, expressions, parallelism))
      else time(querySequentially(whelk, expressions))
    val qps = total.toDouble / duration
    println(s"  - total_time: $duration")
    println(s"    queries_per_second: $qps")
    println(s"    total_subclasses_result: $subclassesFound")
    qps
  }
  val average = (times.sum) * 1.0 / runs.toDouble
  println(s"average: $average")
  println(s"parallelism: $parallelism")
}

private def freshConcept(): AtomicConcept = AtomicConcept(
  s"urn:uuid:${UUID.randomUUID.toString}"
)

def queryExpression(
    whelk: ReasonerState,
    expression: OWLClassExpression
): Int = {
  val (concept, updatedWhelk) = Bridge.convertExpression(expression) match {
    case Some(named @ AtomicConcept(_)) => (named, whelk)
    case Some(expression) =>
      val fresh = freshConcept()
      (
        fresh,
        Reasoner.assert(
          Set(
            ConceptInclusion(fresh, expression),
            ConceptInclusion(expression, fresh)
          ),
          whelk
        )
      )
    case None =>
      throw new UnsupportedOperationException(s"getSubClasses: $expression")
  }
  val subclasses =
    updatedWhelk.closureSubsBySuperclass.getOrElse(
      concept,
      Set.empty
    ) - BuiltIn.Bottom - concept
  val minusEquivs = subclasses.diff(
    updatedWhelk.closureSubsBySubclass.getOrElse(concept, Set.empty)
  )
  val subsumed = minusEquivs.collect { case ac @ AtomicConcept(_) => ac }
  subsumed.size
}

def querySequentially(
    whelk: ReasonerState,
    expressions: List[OWLClassExpression]
): Int = {
  expressions.map { expression =>
    queryExpression(whelk, expression)
  }.sum
}

def queryParallel(
    whelk: ReasonerState,
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
            ZIO.succeed(queryExpression(whelk, expression))
          }
          .runSum
      }
      .getOrThrowFiberFailure()
  }
}

val ontologyFile = args(0)
val parallelism = args(1).toInt
timeQueries(
  ontologyFile,
  parallelism
)
