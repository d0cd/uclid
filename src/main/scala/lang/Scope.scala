/*
 * UCLID5 Verification and Synthesis Engine
 * 
 * Copyright (c) 2017. The Regents of the University of California (Regents). 
 * All Rights Reserved. 
 * 
 * Permission to use, copy, modify, and distribute this software
 * and its documentation for educational, research, and not-for-profit purposes,
 * without fee and without a signed licensing agreement, is hereby granted,
 * provided that the above copyright notice, this paragraph and the following two
 * paragraphs appear in all copies, modifications, and distributions. 
 * 
 * Contact The Office of Technology Licensing, UC Berkeley, 2150 Shattuck Avenue,
 * Suite 510, Berkeley, CA 94720-1620, (510) 643-7201, otl@berkeley.edu,
 * http://ipira.berkeley.edu/industry-info for commercial licensing opportunities.
 * 
 * IN NO EVENT SHALL REGENTS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT, SPECIAL,
 * INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS, ARISING OUT OF
 * THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF REGENTS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * REGENTS SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * THE SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF ANY, PROVIDED HEREUNDER IS
 * PROVIDED "AS IS". REGENTS HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT,
 * UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 * 
 * Author: Pramod Subramanyan

 * Class to track scope/context for UCLID ASTs.
 *
 */

package uclid
package lang

import scala.collection.mutable.{Set => MutableSet}

object Scope {
  sealed abstract class NamedExpression(val id : IdentifierBase, val typ: Type) {
    val isReadOnly = false
  }
  sealed abstract class ReadOnlyNamedExpression(id : IdentifierBase, typ: Type) extends NamedExpression(id, typ) {
    override val isReadOnly = true
  }
  case class Instance(instId : Identifier, moduleId : Identifier, instTyp : Type) extends ReadOnlyNamedExpression(instId, instTyp)  
  case class TypeSynonym(typId : Identifier, sTyp: Type) extends ReadOnlyNamedExpression(typId, sTyp)
  case class StateVar(varId : Identifier, varTyp: Type) extends NamedExpression(varId, varTyp)
  case class InputVar(inpId : Identifier, inpTyp: Type) extends ReadOnlyNamedExpression(inpId, inpTyp)
  case class OutputVar(outId : Identifier, outTyp: Type) extends NamedExpression(outId, outTyp)
  case class ConstantVar(cId : IdentifierBase, cTyp : Type) extends ReadOnlyNamedExpression(cId, cTyp)
  case class Function(fId : Identifier, fTyp: Type) extends ReadOnlyNamedExpression(fId, fTyp)
  case class Procedure(pId : Identifier, pTyp: Type) extends ReadOnlyNamedExpression(pId, pTyp)
  case class ProcedureInputArg(argId : Identifier, argTyp: Type) extends ReadOnlyNamedExpression(argId, argTyp)
  case class ProcedureOutputArg(argId : Identifier, argTyp: Type) extends NamedExpression(argId, argTyp)
  case class ProcedureLocalVar(vId : Identifier, vTyp : Type) extends NamedExpression(vId, vTyp)
  case class LambdaVar(vId : Identifier, vTyp : Type) extends ReadOnlyNamedExpression(vId, vTyp)
  case class ForIndexVar(iId : ConstIdentifier, iTyp : Type) extends ReadOnlyNamedExpression(iId, iTyp)
  case class SpecVar(varId : Identifier, expr: Expr) extends NamedExpression(varId, BoolType()) // FIXME: make readonly
  case class AxiomVar(varId : Identifier, expr : Expr) extends ReadOnlyNamedExpression(varId, BoolType())
  case class EnumIdentifier(enumId : Identifier, enumTyp : EnumType) extends NamedExpression(enumId, enumTyp)
  case class ForallVar(vId : Identifier, vTyp : Type) extends ReadOnlyNamedExpression(vId, vTyp)
  case class ExistsVar(vId : Identifier, vTyp : Type) extends ReadOnlyNamedExpression(vId, vTyp)

  type IdentifierMap = Map[IdentifierBase, NamedExpression]
  def addToMap(map : Scope.IdentifierMap, expr: Scope.NamedExpression) : Scope.IdentifierMap = {
    map + (expr.id -> expr)
  }
  def addTypeToMap(map : Scope.IdentifierMap, typ : Type, module : Option[Module]) : Scope.IdentifierMap = {
    typ match {
      case enumTyp : EnumType => 
        enumTyp.ids.foldLeft(map)((m, id) => {
          m.get(id) match {
            case Some(namedExpr) =>
              namedExpr match {
                case EnumIdentifier(eId, eTyp) =>
                  Utils.checkParsingError(eTyp == enumTyp, 
                      "Identifier " + eId.nam + " redeclared as a member of a different enum.", 
                      eTyp.pos, module.flatMap(_.filename))
                  m
                case _ =>
                  Utils.raiseParsingError("Redeclaration of identifier " + id.name, id.pos, module.flatMap(_.filename))
                  m
              }
            case None =>
              m + (id -> EnumIdentifier(id, enumTyp))
          }
        })
      case _ =>
        map
    }
  }
}

object ScopeMap {
  /** Create an empty context. */
  def empty : ScopeMap = ScopeMap(Map.empty[IdentifierBase, Scope.NamedExpression], None, None)
}

case class ScopeMap (map: Scope.IdentifierMap, module : Option[Module], procedure : Option[ProcedureDecl]) {
  /** Check if a variable name exists in this context. */
  def doesNameExist(name: IdentifierBase) = map.contains(name)
  /** Return the NamedExpression. */
  def get(id: IdentifierBase) : Option[Scope.NamedExpression] = map.get(id)
  /** Does procedure exist? */
  def doesProcedureExist(id : IdentifierBase) : Boolean = {
    map.get(id) match {
      case Some(namedExpr) =>
        namedExpr match {
          case Scope.Procedure(pId, typ) => true
          case _ => false
        }
      case None => false
    }
  }

  /** Return the filename. */
  def filename : Option[String] = {
    module.flatMap((m) => m.filename)
  }
  val inputs = map.filter(_._2.isInstanceOf[Scope.InputVar]).map(_._2.asInstanceOf[Scope.InputVar]).toSet
  val vars = map.filter(_._2.isInstanceOf[Scope.StateVar]).map(_._2.asInstanceOf[Scope.StateVar]).toSet
  val outputs = map.filter(_._2.isInstanceOf[Scope.OutputVar]).map(_._2.asInstanceOf[Scope.OutputVar]).toSet
  val specs = map.filter(_._2.isInstanceOf[Scope.SpecVar]).map(_._2.asInstanceOf[Scope.SpecVar]).toSet
  
  /** Return a new context with this identifier added to the current context. */
  def +(expr: Scope.NamedExpression) : ScopeMap = {
    ScopeMap(map + (expr.id -> expr), module, procedure)
  }
  def +(typ : Type) : ScopeMap = {
    ScopeMap(Scope.addTypeToMap(map, typ, module), module, procedure)
  }

  /** Return a new context with the declarations in this module added to it. */
  def +(m: Module) : ScopeMap = { 
    Utils.assert(module.isEmpty, "A module was already added to this Context.")
    val m1 = m.decls.foldLeft(map){ (mapAcc, decl) =>
      decl match {
        case instD : InstanceDecl => Scope.addToMap(mapAcc, Scope.Instance(instD.instanceId, instD.moduleId, instD.instanceType))
        case ProcedureDecl(id, sig, _, _) => Scope.addToMap(mapAcc, Scope.Procedure(id, sig.typ))
        case TypeDecl(id, typ) => Scope.addToMap(mapAcc, Scope.TypeSynonym(id, typ))
        case StateVarDecl(id, typ) => Scope.addToMap(mapAcc, Scope.StateVar(id, typ))
        case StateVarsDecl(ids, typ) => ids.foldLeft(mapAcc)((acc, id) => Scope.addToMap(acc, Scope.StateVar(id, typ)))
        case InputVarDecl(id, typ) => Scope.addToMap(mapAcc, Scope.InputVar(id, typ))
        case InputVarsDecl(ids, typ) => ids.foldLeft(mapAcc)((acc, id) => Scope.addToMap(acc, Scope.InputVar(id, typ)))
        case OutputVarDecl(id, typ) => Scope.addToMap(mapAcc, Scope.OutputVar(id, typ))
        case OutputVarsDecl(ids, typ) => ids.foldLeft(mapAcc)((acc, id) => Scope.addToMap(acc, Scope.OutputVar(id, typ)))
        case ConstantDecl(id, typ) => Scope.addToMap(mapAcc, Scope.ConstantVar(id, typ)) 
        case FunctionDecl(id, sig) => Scope.addToMap(mapAcc, Scope.Function(id, sig.typ))
        case SpecDecl(id, expr) => Scope.addToMap(mapAcc, Scope.SpecVar(id, expr))
        case AxiomDecl(sId, expr) => sId match {
          case Some(id) => Scope.addToMap(mapAcc, Scope.AxiomVar(id, expr))
          case None => mapAcc
        }
        case InitDecl(_) | NextDecl(_) => mapAcc
      }
    }
    val m2 = m.decls.foldLeft(m1){(mapAcc, decl) =>
      decl match {
        case ProcedureDecl(id, sig, _, _) => 
          val m1 = sig.inParams.foldLeft(mapAcc)((mapAcc2, operand) => Scope.addTypeToMap(mapAcc2, operand._2, Some(m)))
          val m2 = sig.outParams.foldLeft(m1)((mapAcc2, operand) => Scope.addTypeToMap(mapAcc2, operand._2, Some(m)))
          m2
        case FunctionDecl(id, sig) =>
          val m1 = sig.args.foldLeft(mapAcc)((mapAcc2, operand) => Scope.addTypeToMap(mapAcc2, operand._2, Some(m)))
          val m2 = Scope.addTypeToMap(m1, sig.retType, Some(m))
          m2
        case TypeDecl(id, typ) => Scope.addTypeToMap(mapAcc, typ, Some(m))
        case StateVarDecl(id, typ) => Scope.addTypeToMap(mapAcc, typ, Some(m))
        case StateVarsDecl(id, typ) => Scope.addTypeToMap(mapAcc, typ, Some(m))
        case InputVarDecl(id, typ) => Scope.addTypeToMap(mapAcc, typ, Some(m))
        case InputVarsDecl(id, typ) => Scope.addTypeToMap(mapAcc, typ, Some(m))
        case OutputVarDecl(id, typ) => Scope.addTypeToMap(mapAcc, typ, Some(m))
        case OutputVarsDecl(id, typ) => Scope.addTypeToMap(mapAcc, typ, Some(m))
        case ConstantDecl(id, typ) => Scope.addTypeToMap(mapAcc, typ, Some(m))
        case InstanceDecl(_, _, _, _, _) | SpecDecl(_, _) | AxiomDecl(_, _) | InitDecl(_) | NextDecl(_) => mapAcc
      }
    }
    ScopeMap(m2, Some(m), None)
  }
  /** Return a new context with the declarations in this procedure added to it. */
  def +(proc: ProcedureDecl) : ScopeMap = {
    Utils.assert(procedure.isEmpty, "A procedure was already added to this context.")
    val map1 = proc.sig.inParams.foldLeft(map){
      (mapAcc, arg) => Scope.addToMap(mapAcc, Scope.ProcedureInputArg(arg._1, arg._2))
    }
    val map2 = proc.sig.outParams.foldLeft(map1){
      (mapAcc, arg) => Scope.addToMap(mapAcc, Scope.ProcedureOutputArg(arg._1, arg._2))
    }
    val map3 = proc.decls.foldLeft(map2){
      (mapAcc, arg) => Scope.addToMap(mapAcc, Scope.ProcedureLocalVar(arg.id, arg.typ))
    }
    return ScopeMap(map3, module, Some(proc))
  }
  /** Return a new context with the declarations in this lambda expression added to it. */
  def +(lambda: Lambda) : ScopeMap = {
    val newMap = lambda.ids.foldLeft(map){ 
      (mapAcc, id) => Scope.addToMap(mapAcc, Scope.LambdaVar(id._1, id._2))
    }
    return ScopeMap(newMap, module, procedure)
  }
  /** Return a new context with quantifier variables added. */
  def +(opapp : OperatorApplication) : ScopeMap = {
    this + (opapp.op)
  }
  /** Return a new context with the quantifier variables adder. */
  def +(op : Operator) : ScopeMap = {
    op match {
      case ForallOp(vs) =>
        ScopeMap(
          vs.foldLeft(map)((mapAcc, arg) => Scope.addToMap(mapAcc, Scope.ForallVar(arg._1, arg._2))),
          module, procedure)
      case ExistsOp(vs) =>
        ScopeMap(
          vs.foldLeft(map)((mapAcc, arg) => Scope.addToMap(mapAcc, Scope.ForallVar(arg._1, arg._2))),
          module, procedure)
      case _ => this
    }
  }
  
  /** Return the type of an identifier in this context. */
  def typeOf(id : IdentifierBase) : Option[Type] = {
    map.get(id).flatMap((e) => Some(e.typ))
  }
  
  def isQuantifierVar(id : IdentifierBase) : Boolean = {
    (map.get(id).flatMap{
      (p) => p match {
        case Scope.ForallVar(_, _) | Scope.ExistsVar(_, _) => Some(true)
        case _ => Some(false)
      }
    }) match {
      case Some(t) => t
      case None => false
    }
  }
}

class ContextualNameProvider(ctx : ScopeMap, prefix : String) {
  var index = 1 
  def apply(name: Identifier, tag : String) : Identifier = {
    var newId = Identifier(prefix + "$" + tag + "$" + name + "_" + index.toString)
    index = index + 1
    while (ctx.doesNameExist(newId)) {
      newId = Identifier(prefix + "$" + tag + "$" + name + "_" + index.toString)
      index = index + 1
    }
    return newId
  }
}