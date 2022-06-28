package org.virtuslab.typedframes

import scala.quoted.*
import org.apache.spark.sql.{ Column => UntypedColumn, DataFrame => UntypedDataFrame }
import types.{ DataType, StructType }

trait SelectCtx extends SparkOpCtx:
  type CtxOut <: SelectionView

object SelectOps:
  extension [DF <: DataFrame[?]](tdf: DF)
    transparent inline def select: Select[?] = ${ Select.selectImpl[DF]('{tdf}) }

class Select[View <: SelectionView](view: View, underlying: UntypedDataFrame):
  import Select.MergeColumns
  def apply[T](f: SelectCtx { type CtxOut = View } ?=> T)(using mc: MergeColumns[T]): DataFrame[mc.MergedSchema] =
    val ctx = new SelectCtx {
      type CtxOut = View
      def ctxOut: View = view
    }
    val typedCols = f(using ctx)
    val columns  = mc.columns(typedCols)
    DataFrame[mc.MergedSchema](underlying.select(columns*))

object Select:
  def selectImpl[DF <: DataFrame[?] : Type](tdf: Expr[DF])(using Quotes): Expr[Select[?]] =
    import quotes.reflect.asTerm
    val viewExpr = SelectionView.selectionViewExpr[DF]
    viewExpr.asTerm.tpe.asType match
      case '[SelectionView.Subtype[v]] =>
        '{ Select[v](${ viewExpr }.asInstanceOf[v], ${ tdf }.untyped) }

  trait MergeColumns[T]:
    type MergedSchema <: FrameSchema
    def columns(t: T): List[UntypedColumn]

  object MergeColumns:
    transparent inline given mergeTupleColumns[T <: Tuple]: MergeColumns[T] = ${ mergeTupleColumnsImpl[T] }

    def mergeTupleColumnsImpl[T <: Tuple : Type](using Quotes): Expr[MergeColumns[T]] = 
      '{
        new MergeColumns[T] {
          // TODO: drop frame prefixes
          type MergedSchema = T
          def columns(t: T): List[UntypedColumn] = t.toList.map(col => col.asInstanceOf[TypedColumn[DataType]].untyped)
        }
      }

    // TODO: assure N is not just Internals.Name or Nothing
    transparent inline given mergeSingleColumn[N <: Name, A <: DataType]: MergeColumns[LabeledColumn[N, A]] = ${ mergeSingleColumnImpl[N, A] }

    def mergeSingleColumnImpl[N <: Name : Type, A <: DataType : Type](using Quotes): Expr[MergeColumns[LabeledColumn[N, A]]] =
      '{
        new MergeColumns[LabeledColumn[N, A]] {
          type MergedSchema = Tuple1[LabeledColumn[N, A]]
          def columns(t: LabeledColumn[N, A]): List[UntypedColumn] = List(t.untyped)
        }
      }