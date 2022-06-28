package org.virtuslab.typedframes

import scala.quoted.*
import scala.deriving.Mirror
import types.DataType
import types.{IntegerType, BooleanType, StringType}
import Internals.Name

type FrameSchema = Tuple

object FrameSchema:
  type Subtype[T <: FrameSchema] = T

  type Merge[S1 <: FrameSchema, S2 <: FrameSchema] = Tuple.Concat[S1, S2]
  
  type Reowned[S <: FrameSchema, N <: Name] <: FrameSchema = S match
    case EmptyTuple => EmptyTuple
    case LabeledColumn[Name.Subtype[name], dataType] *: tail =>
      LabeledColumn[(N, name), dataType] *: Reowned[tail, N]
    case LabeledColumn[(Name.Subtype[framePrefix], Name.Subtype[name]), dataType] *: tail =>
      LabeledColumn[(N, name), dataType] *: Reowned[tail, N]

  trait Encoder[A]:
    type Encoded <: FrameSchema

  object Encoder:
    type TupleSubtype[T <: Tuple] = T // TODO: Needed or not?

    def getEncodedType(using quotes: Quotes)(mirroredElemLabels: Type[?], mirroredElemTypes: Type[?]): quotes.reflect.TypeRepr =
      import quotes.reflect.*

      mirroredElemLabels match
        case '[EmptyTuple] => TypeRepr.of[EmptyTuple]
        case '[Name.Subtype[label] *: labels] => mirroredElemTypes match
          case '[tpe *: tpes] =>
            // TODO: Handle missing encoder
            Expr.summon[DataType.Encoder[tpe]].get /* getOrElse(DataType.Encoder.fromMirrorImpl[tpe]) */ match // TODO: tpe might also be an unhandled primitive
              case '{ $encoder: DataType.Encoder.Aux[tpe, DataType.Subtype[e]] } => 
                getEncodedType(Type.of[labels], Type.of[tpes]).asType match
                  case '[TupleSubtype[tail]] =>
                    TypeRepr.of[(LabeledColumn[label, e]) *: tail]

    transparent inline given fromMirror[A](using m: Mirror.ProductOf[A]): Encoder[A] = ${ fromMirrorImpl[A] }

    def fromMirrorImpl[A : Type](using Quotes): Expr[Encoder[A]] =
      val encodedType = Expr.summon[Mirror.Of[A]].getOrElse(throw new Exception(s"aaaaa ${Type.show[A]}")) match
        case '{ $m: Mirror.ProductOf[A] { type MirroredElemLabels = elementLabels; type MirroredElemTypes = elementTypes } } =>
          getEncodedType(Type.of[elementLabels], Type.of[elementTypes])
      encodedType.asType match
        case '[t] =>
          '{
            (new Encoder[A] {
              override type Encoded = t
            }): Encoder[A] { type Encoded = t } // TODO: Type ascription shouldn't be needed
          }

// /////////////////////

// TODO: Conformance should be recursive
// trait Conformance[S1 <: FrameSchema, S2 <: FrameSchema]

// object Conformance:
//   inline given instance[S1 <: FrameSchema, S2 <: FrameSchema]: Conformance[S1, S2]

// transparent inline def schemaInstance[Schema <: FrameSchema]: FrameSchema = erasedValue[Schema] match
//   case _: SNil.type => SNil
//   case _: SCons[headLabel, headType, tail] =>
//     inline val tailSchema = schemaInstance[tail].asInstanceOf[tail]
//     (new SCons[headLabel, headType, tail](valueOf[headLabel], "FakeType", tailSchema))//.asInstanceOf[Schema]

// inline def sconsSchemaInstance[headLabel <: Name, headType, tail <: FrameSchema]: Schema =
  
//   erasedValue[Schema] match