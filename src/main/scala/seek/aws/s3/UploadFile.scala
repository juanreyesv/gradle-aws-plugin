package seek.aws
package s3

import java.io.File

import cats.data.Kleisli
import cats.effect.IO
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

class UploadFile extends Upload {
  import S3._

  setDescription("Uploads a single file to S3")

  private val file = lazyProperty[File]("file")
  def file(v: Any): Unit = file.set(v)

  private val bucket = lazyProperty[String]("bucket")
  def bucket(v: Any): Unit = bucket.set(v)

  private val key = lazyProperty[String]("key")
  def key(v: Any): Unit = key.set(v)

  private val failIfObjectExists = lazyProperty[Boolean]("failIfObjectExists", true)
  def failIfObjectExists(v: Any): Unit = failIfObjectExists.set(v)

  override def run: IO[Unit] =
    for {
      f <- file.run
      b <- bucket.run
      k <- key.run
      c <- buildClient(AmazonS3ClientBuilder.standard())
      _ <- maybeFailIfObjectExists(b, k).run(c)
      g <- maybeInterpolate(f)
      _ <- upload(b, k, g).run(c)
    } yield ()

  private def maybeFailIfObjectExists(bucket: String, key: String): Kleisli[IO, AmazonS3, Unit] =
    maybeRun(failIfObjectExists, exists(bucket, key),
      raiseError(s"Object ${key} already exists in bucket ${bucket}"))
}
