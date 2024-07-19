package com.github.merlijn.llm.api.schema

import scala.annotation.StaticAnnotation

class Meta(val name: String, val description: String) extends StaticAnnotation

object Meta {

  import scala.quoted.*

  inline def fieldMetaForType[T]: Vector[(String, Meta)] = ${
    readFieldMeta[T]
  }

  private def readFieldMeta[T: Type](using q: Quotes): Expr[Vector[(String, Meta)]] =
    import quotes.reflect.*
    val annot = TypeRepr.of[Meta].typeSymbol
    val tuples: Seq[Expr[(String, Meta)]] = TypeRepr
      .of[T]
      .typeSymbol
      .primaryConstructor
      .paramSymss
      .flatten
      .collect:
        case sym if sym.hasAnnotation(annot) =>
          val fieldNameExpr = Expr(sym.name.asInstanceOf[String])
          val annotExpr = sym.getAnnotation(annot).get.asExprOf[Meta]
          '{ ($fieldNameExpr, $annotExpr) }
    val seq: Expr[Seq[(String, Meta)]] = Expr.ofSeq(tuples)
    '{ $seq.toVector }
}
