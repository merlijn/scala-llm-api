package com.github.merlijn.llm.api.schema

import scala.annotation.StaticAnnotation

class Description(val description: String) extends StaticAnnotation

class Title(val title: String) extends StaticAnnotation

object Annotations:

  import scala.quoted.*

  inline def readFieldDescriptions[T]: Vector[(String, Description)] = ${ readFieldAnnotationsImpl[T, Description] }

  inline def readFieldTitles[T]: Vector[(String, Title)] = ${ readFieldAnnotationsImpl[T, Title] }

  private def readFieldAnnotationsImpl[T: Type, A <: StaticAnnotation: Type](using q: Quotes): Expr[Vector[(String, A)]] =
    import quotes.reflect.*
    val annot = TypeRepr.of[A].typeSymbol
    val tuples: Seq[Expr[(String, A)]] = TypeRepr
      .of[T]
      .typeSymbol
      .primaryConstructor
      .paramSymss
      .flatten
      .collect:
        case sym if sym.hasAnnotation(annot) =>
          val fieldNameExpr = Expr(sym.name.asInstanceOf[String])
          val annotExpr     = sym.getAnnotation(annot).get.asExprOf[A]
          '{ ($fieldNameExpr, $annotExpr) }
    val seq: Expr[Seq[(String, A)]] = Expr.ofSeq(tuples)
    '{ $seq.toVector }

  inline def readDescriptionForType[T]: Option[Description] = ${ readAnnotationForTypeImpl[T, Description] }

  inline def readTitleForType[T]: Option[Title] = ${ readAnnotationForTypeImpl[T, Title] }

  private def readAnnotationForTypeImpl[T: Type, A <: StaticAnnotation: Type](using Quotes): Expr[Option[A]] =
    import quotes.reflect.*
    val annot = TypeRepr.of[A]
    TypeRepr
      .of[T]
      .typeSymbol
      .annotations
      .collectFirst:
        case term if term.tpe =:= annot => term.asExprOf[A]
    match
      case Some(expr) => '{ Some($expr) }
      case None       => '{ None }
