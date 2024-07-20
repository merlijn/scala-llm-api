package com.github.merlijn.llm.api.schema

import scala.annotation.StaticAnnotation

class Description(val description: String) extends StaticAnnotation

object Description:

  import scala.quoted.*

  inline def fieldMetaForType[T]: Vector[(String, Description)] = ${
    readFieldMeta[T]
  }

  private def readFieldMeta[T: Type](using q: Quotes): Expr[Vector[(String, Description)]] =
    import quotes.reflect.*
    val annot = TypeRepr.of[Description].typeSymbol
    val tuples: Seq[Expr[(String, Description)]] = TypeRepr
      .of[T]
      .typeSymbol
      .primaryConstructor
      .paramSymss
      .flatten
      .collect:
        case sym if sym.hasAnnotation(annot) =>
          val fieldNameExpr = Expr(sym.name.asInstanceOf[String])
          val annotExpr = sym.getAnnotation(annot).get.asExprOf[Description]
          '{ ($fieldNameExpr, $annotExpr) }
    val seq: Expr[Seq[(String, Description)]] = Expr.ofSeq(tuples)
    '{ $seq.toVector }

  inline def readMetaForType[T]: Option[Description] = ${ readMetaForTypeImpl[T] }

  private def readMetaForTypeImpl[T: Type](using Quotes): Expr[Option[Description]] =
    import quotes.reflect.*
    val annot = TypeRepr.of[Description]
    TypeRepr
      .of[T]
      .typeSymbol
      .annotations
      .collectFirst:
        case term if term.tpe =:= annot => term.asExprOf[Description]
    match
      case Some(expr) => '{ Some($expr) }
      case None => '{ None }
