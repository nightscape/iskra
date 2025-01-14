package org.virtuslab.iskra.test

class WithColumnsTest extends SparkUnitTest:
  import org.virtuslab.iskra.api.*

  case class Foo(a: Int, b: Int)
  case class Bar(a: Int, b: Int, c: Int)
  case class Baz(a: Int, b: Int, c: Int, d: Int)

  val foos = Seq(
    Foo(1, 2),
  ).toTypedDF

  test("withColumn") {
    val result = foos
      .withColumn("c", $.a + $.b)
      .collectAs[Bar]

    result shouldEqual Seq(Bar(1, 2, 3))
  }

  test("withColumns-single") {
    val result = foos
      .withColumns(
        ($.a + $.b).as("c")
      )
      .collectAs[Bar]

    result shouldEqual Seq(Bar(1, 2, 3))
  }

  test("withColumns-many") {
    val result = foos
      .withColumns(
        ($.a + $.b).as("c"),
        ($.a - $.b).as("d"),
      )
      .collectAs[Baz]

    result shouldEqual Seq(Baz(1, 2, 3, -1))
  }