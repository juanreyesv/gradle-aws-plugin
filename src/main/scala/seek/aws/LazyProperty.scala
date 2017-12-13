package seek.aws

import cats.effect.IO
import groovy.lang.{Closure, GString}
import org.gradle.api._
import seek.aws.HasLazyProperties.lazyProperty
import seek.aws.config.Lookup

import scala.reflect.ClassTag

class LazyProperty[A](name: String, default: Option[A] = None)(project: Project) {

  private var thing: Option[Any] = None

  def run(implicit tag: ClassTag[A]): IO[A] =
    thing match {
      case Some(v) => render(v)
      case None    =>
        default match {
          case Some(v) => IO.pure(v)
          case None    => IO.raiseError(new GradleException(s"Property ${name} has not been set"))
        }
    }

  def runOptional(implicit tag: ClassTag[A]): IO[Option[A]] =
    if (isSet) run.map(Some(_))
    else IO.pure(None)

  def set(x: Any) =
    thing = Some(x)

  def isSet: Boolean =
    thing.isDefined

  private def render(v: Any)(implicit tag: ClassTag[A]): IO[A] =
    v match {
      case a: A          => IO(a)
      case l: Lookup     => l.run(project).flatMap(render)
      case c: Closure[_] => IO(c.call()).flatMap(render)
      case g: GString    => render(g.toString)
    }
}

object LazyProperty {

  def render(a: Any)(implicit p: Project): IO[String] = {
    val lp = lazyProperty[String]("")
    lp.set(a)
    lp.run
  }

  def renderAll(s: List[Any])(implicit p: Project): IO[List[String]] =
    s.foldRight(IO.pure(List.empty[String])) { (a, z) =>
      for {
        h <- render(a)
        t <- z
      } yield h :: t
    }

  def renderValues[A](m: Map[A, Any])(implicit p: Project): IO[Map[A, String]] =
    renderAll(m.values.toList).map(rs => m.keys.zip(rs).toMap)
}

trait HasLazyProperties {

  def lazyProperty[A](name: String)(implicit p: Project): LazyProperty[A] =
    new LazyProperty[A](name)(p)

  def lazyProperty[A](name: String, default: A)(implicit p: Project): LazyProperty[A] =
    new LazyProperty[A](name, Some(default))(p)
}

object HasLazyProperties extends HasLazyProperties