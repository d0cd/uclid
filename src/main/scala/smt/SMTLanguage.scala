/*
 * UCLID5 Verification and Synthesis Engine
 *
 * Copyright (c) 2017.
 * Sanjit A. Seshia, Rohit Sinha and Pramod Subramanyan.
 *
 * All Rights Reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 1. Redistributions of source code must retain the above copyright notice,
 *
 * this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 *
 * documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Authors: Rohit Sinha, Pramod Subramanyan
 *
 * SMT AST definition.
 *
 */
package uclid
package smt
import scala.collection.mutable.Map
import scala.util.hashing.{MurmurHash3 => MH}

sealed trait Hashable {
  /** These are unique ids for each abstract base class that are used
   *  to start off the hashing.
   */
  val hashBaseId : Int
  val hashId : Int

  def mix(a : Int, b : Int) = MH.mix(a, b)
  def mix(a : Int, bs : List[Any]) : Int = bs.foldLeft(a)((acc, h) => mix(acc, h.hashCode))
  def finalize(h : Int, l : Int) : Int = MH.finalizeHash(h, l)
  def hashBaseValue = mix(hashBaseId, hashId)
  def computeHash = finalize(hashBaseValue, 1)
  def computeHash(a : Any) = finalize(mix(hashBaseValue, a.hashCode()), 1)
  def computeHash(a : Any, b : Any) = finalize(mix(mix(hashBaseValue, a.hashCode()), b.hashCode()), 2)
  def computeHash(bs : List[Any]) : Int =
    finalize(bs.foldLeft(hashBaseId)((acc, h) => mix(acc, h.hashCode)), bs.size)
  def computeHash(bs : List[Any], b : Any) : Int =
    finalize(bs.foldLeft(mix(hashBaseValue, b.hashCode))((acc, b) => mix(acc, b.hashCode)), bs.size+1)
  def computeHash(bs : List[Any], b : Any, c : Any) : Int =
    finalize(bs.foldLeft(mix(hashBaseValue, mix(c.hashCode, b.hashCode)))((acc, b) => mix(acc, b.hashCode)), bs.size+1)
  def computeHash(as : List[Any], bs : List[Any]) : Int = {
    val start = hashBaseValue
    val hashA = as.foldLeft(start)((acc, a) => mix(acc, a.hashCode))
    val hashB = bs.foldLeft(hashA)((acc, b) => mix(acc, b.hashCode))
    hashB
  }
}
sealed trait Type extends Hashable {
  override val hashBaseId = 22575 // Random number. Not super important, must just be unique for each abstract base class.
  def isBool = false
  def isInt = false
  def isBitVector = false
  def isTuple = false
  def isRecord = false
  def isMap = false
  def isArray = false
  def isEnum = false
  def isUninterpreted = false
  def isSynonym = false
  val typeNamePrefix : String
}
// Uninterpreted types.
case class UninterpretedType(name: String) extends Type {
  override val hashId = 100
  override val hashCode = computeHash
  override def toString = name.toString()
  override def isUninterpreted = true
  override val typeNamePrefix = "uninterpreted"
}
// The Boolean type.
case object BoolType extends Type {
  override val hashId = 101
  override val hashCode = computeHash
  override def toString = "Bool"
  override def isBool = true
  override val typeNamePrefix = "bool"
}
// The integer type.
case object IntType extends Type {
  override val hashId = 102
  override val hashCode = computeHash
  override def toString = "Int"
  override def isInt = true
  override val typeNamePrefix = "int"
}
// The bit-vector type.
case class BitVectorType(width: Int) extends Type
{
  override val hashId = mix(103, width)
  override val hashCode = computeHash
  override def toString = "BitVec %s" + (width.toString)
  override def isBitVector = true
  override val typeNamePrefix = "bv" + width.toString()
}
object BitVectorType {
  val t = new Memo[Int, BitVectorType]((w : Int) => new BitVectorType(w))
}

sealed abstract class ProductType(fields : List[(String, Type)]) extends Type {
  lazy val fieldNames = fields.map(_._1)
  lazy val fieldTypes = fields.map(_._2)
  lazy val fieldIndices = (0 to fields.length - 1)
  def fieldType(name: String) : Option[Type] = fields.find((p) => p._1 == name).flatMap((f) => Some(f._2))
  def hasField(name: String) : Boolean = fields.find((p) => p._1 == name).isDefined
  def fieldIndex(name: String) : Int = fields.indexWhere((p) => p._1 == name)
}
case class TupleType(types: List[Type]) extends ProductType(((1 to types.length).map("_" + _.toString)).zip(types).toList) {
  override val hashId = 104
  override val hashCode = computeHash(types)
  override def toString = "tuple [" + Utils.join(types.map(_.toString), ", ") + "]"
  override def isTuple = true
  override val typeNamePrefix = "tuple"
}
case class RecordType(fields_ : List[(String, Type)]) extends ProductType(fields_) {
  override val hashId = 105
  override val hashCode = computeHash(fields_)
  override def toString = "record [" + Utils.join(fields_.map((f) => f._1.toString + " : " + f._2.toString), ", ") + "]"
  override def isRecord = true
  override val typeNamePrefix = "record"
}
case class MapType(inTypes: List[Type], outType: Type) extends Type {
  override val hashId = 106
  override val hashCode = computeHash(inTypes, outType)
  override def toString = {
    "map [" +
    inTypes.tail.fold(inTypes.head.toString){ (acc,i) => acc + "," + i.toString } +
    "] " + outType
  }
  override def isMap = true
  override val typeNamePrefix = "map"
}
case class ArrayType(inTypes: List[Type], outType: Type) extends Type {
  override val hashId = 107
  override val hashCode = computeHash(inTypes, outType)
  override def toString = {
    "array [" +
    inTypes.tail.fold(inTypes.head.toString){ (acc,i) => acc + "," + i.toString } +
    "] " + outType
  }
  override def isArray = true
  override val typeNamePrefix = "array"
}
case class EnumType(members : List[String]) extends Type {
  override val hashId = 108
  override val hashCode = computeHash(members)
  override def toString  = "enum {" + Utils.join(members, ", ") + "}"
  override def isEnum = true
  def fieldIndex(name : String) : Int = members.indexWhere(_ == name)
  override val typeNamePrefix = "enum"
}
case class SynonymType(name: String, typ: Type) extends Type {
  override val hashId = 109
  override val hashCode = computeHash(name, typ)
  override def toString = "type %s = %s".format(name, typ.toString)
  override def isSynonym = true
  override val typeNamePrefix = "synonym"
}

object OperatorFixity extends scala.Enumeration {
  type OperatorFixity = Value
  val INFIX, PREFIX = Value
}
import OperatorFixity._

trait Operator extends Hashable {
  override val hashBaseId : Int = 22446 // Random number.
  def resultType(args: List[Expr]) : Type
  def typeCheck (args: List[Expr]) : Unit = { }
  def fixity : OperatorFixity

  def checkNumArgs(args: List[Expr], expectedNumOperands : Int) : Unit = {
    Utils.assert(args.size == expectedNumOperands, "Operator '" + toString + "' requires " + expectedNumOperands + " operand(s).")
  }
  def checkAllArgTypes(args: List[Expr], expectedType : Type) : Unit = {
    Utils.assert(args.forall(op => op.typ == expectedType), "Operator '" + toString + "' requires operand(s) of type: " + expectedType)
  }
  def checkAllArgsSameType(args: List[Expr]) : Unit = {
    args match {
      case Nil => Utils.assert(false, "Expected at least one operand for '" + toString + "' operator.")
      case head :: tail =>
        Utils.assert(args.forall(op => op.typ == head.typ),
            "Operands to '" + toString + "' must of the same type. Got: " +
            Utils.join(args.map(a => a.typ.toString()), " "))
    }
  }
}

// Operators that return integers.
abstract class IntResultOp extends Operator {
  override def resultType(args: List[Expr]) : Type = { IntType }
  override def fixity = { PREFIX }
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, IntType) }
}
object IntAddOp extends IntResultOp {
  override val hashId = 200
  override val hashCode = computeHash
  override def toString = "+"
}
object IntSubOp extends IntResultOp {
  override val hashId = 201
  override val hashCode = computeHash
  override def toString = "-"
}
object IntMulOp extends IntResultOp {
  override val hashId = 202
  override val hashCode = computeHash
  override def toString = "*"
}
object IntMinusOp extends IntResultOp {
  override val hashId = 203
  override val hashCode = computeHash
  override def toString = "-"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 1); checkAllArgTypes(args, IntType) }
}

// Operators that return bitvectors.
abstract class BVResultOp(width : Int) extends Operator {
  override def resultType(args: List[Expr]) : Type = { BitVectorType.t(width) }
  override def fixity = PREFIX
  override def typeCheck(args: List[Expr]) : Unit  = { checkNumArgs(args, 2); checkAllArgTypes(args, BitVectorType.t(width)) }
}
case class BVAddOp(w : Int) extends BVResultOp(w) {
  override val hashId = mix(w, 204)
  override val hashCode = computeHash
  override def toString = "bvadd"
}
case class BVSubOp(w : Int) extends BVResultOp(w) {
  override val hashId = mix(w, 205)
  override val hashCode = computeHash
  override def toString = "bvsub"
}
case class BVMulOp(w : Int) extends BVResultOp(w) {
  override val hashId = mix(w, 206)
  override val hashCode = computeHash
  override def toString = "bvmul"
}
case class BVMinusOp(w : Int) extends BVResultOp(w) {
  override val hashId = mix(w, 207)
  override val hashCode = computeHash
  override def toString = "bvneg"
  override def typeCheck(args: List[Expr]) : Unit  = { checkNumArgs(args, 1); checkAllArgTypes(args, BitVectorType.t(w)) }
}
case class BVAndOp(w : Int) extends BVResultOp(w) {
  override val hashId = mix(w, 208)
  override val hashCode = computeHash
  override def toString = "bvand"
}
case class BVOrOp(w : Int) extends BVResultOp(w) {
  override val hashId = mix(w, 209)
  override val hashCode = computeHash
  override def toString = "bvor"
}
case class BVXorOp(w : Int) extends BVResultOp(w) {
  override val hashId = mix(w, 210)
  override val hashCode = computeHash
  override def toString = "bvxor"
}
case class BVNotOp(w : Int) extends BVResultOp(w) {
  override val hashId = mix(w, 211)
  override val hashCode = computeHash
  override def toString = "bvneg"
  override def typeCheck(args: List[Expr]) : Unit  = { checkNumArgs(args, 1); checkAllArgTypes(args, BitVectorType.t(w)) }
}
case class BVExtractOp(hi : Int, lo : Int) extends BVResultOp(hi - lo + 1) {
  override val hashId = mix(lo, mix(hi, 212))
  override val hashCode = computeHash
  override def toString = "bvextract " + hi + " " + lo
  override def typeCheck(args: List[Expr]) : Unit = {
    checkNumArgs(args, 1);
    Utils.assert(args(0).typ.isBitVector, "Argument to bitvector extract must be a bitvector.")
    val argBvType = args(0).typ.asInstanceOf[BitVectorType]
    Utils.assert(hi < argBvType.width && lo < argBvType.width && hi >= 0 && lo >= 0, "Invalid indices to bitvector extract.")
  }
}
case class BVConcatOp(w : Int) extends BVResultOp(w) {
  override val hashId = mix(w, 213)
  override val hashCode = computeHash
  override def toString = "bvconcat"
  override def typeCheck(args: List[Expr]) : Unit = {
    checkNumArgs(args, 2);
    Utils.assert(args.forall(_.typ.isBitVector), "Argument to bitvector concat must be a bitvector.")
    val width = args.foldLeft(0)((acc, ai) => ai.typ.asInstanceOf[BitVectorType].width + acc)
    Utils.assert(width == w, "Incorrect width argument to BVConcatOp.")
  }
}
case class BVReplaceOp(w : Int, hi : Int, lo : Int) extends BVResultOp(w) {
  override val hashId = mix(lo, mix(hi, mix(w, 214)))
  override val hashCode = computeHash
  override def toString = "bvreplace " + hi + " " + lo
  override def typeCheck(args: List[Expr]) : Unit = {
    checkNumArgs(args, 2);
    Utils.assert(args.forall(_.typ.isBitVector), "Argument to bitvector concat must be a bitvector.")
    Utils.assert(args(0).typ.asInstanceOf[BitVectorType].width == w, "Incorrect width of first operand to BVReplaceOp.")
    Utils.assert(args(1).typ.asInstanceOf[BitVectorType].width == (hi-lo+1), "Incorrect width of second operand to BVReplaceOp.")
  }
}
// Operators that return Booleans.
abstract class BoolResultOp extends Operator {
  override def resultType(args: List[Expr]) : Type = { BoolType }
  override def fixity = { INFIX }
}

abstract class QuantifierOp extends BoolResultOp {
  def variables : List[Symbol]

  override def fixity = PREFIX
  override def typeCheck (args: List[Expr]) = {
    Utils.assert(args.size == 1, this.toString + " must have exactly one operand.")
    Utils.assert(args.size == 1, this.toString + " must have exactly one operand.")
  }
}

case class ForallOp(vs : List[Symbol]) extends QuantifierOp {
  override val hashId = 215
  override val hashCode = computeHash(vs)
  override def variables = vs
  override def toString = "forall (" + Utils.join(vs.map(i => i.toString + ": " + i.typ.toString), ", ") + "): "
}
case class ExistsOp(vs : List[Symbol]) extends QuantifierOp {
  override val hashId = 216
  override val hashCode = computeHash(vs)
  override def variables = vs
  override def toString = "exists (" + Utils.join(vs.map(i => i.toString + ": " + i.typ.toString), ", ") + "): "
}

case object IffOp extends BoolResultOp {
  override val hashId = 217
  override val hashCode = computeHash
  override def toString = "<==>"
  override def typeCheck (args: List[Expr]) = {
    Utils.assert(args.size == 2, "Iff must have two operands.")
    Utils.assert(args.forall(op => op.typ.isBool), "Iff operands must be boolean.")
  }
}
case object ImplicationOp extends BoolResultOp {
  override val hashId = 218
  override val hashCode = computeHash
  override def toString  = "=>"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, BoolType) }
}
case object ConjunctionOp extends BoolResultOp {
  override val hashId = 219
  override val hashCode = computeHash
  override def toString = "and"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, BoolType) }
}
case object DisjunctionOp extends BoolResultOp {
  override val hashId = 220
  override val hashCode = computeHash
  override def toString = "or"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, BoolType) }
}
case object NegationOp extends BoolResultOp {
  override val hashId = 221
  override val hashCode = computeHash
  override def toString = "not"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 1); checkAllArgTypes(args, BoolType) }
  override def fixity = PREFIX
}
case object EqualityOp extends BoolResultOp {
  override val hashId = 222
  override val hashCode = computeHash
  override def toString = "="
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgsSameType(args) }
}
case object InequalityOp extends BoolResultOp {
  override val hashId = 223
  override val hashCode = computeHash
  override def toString = "distinct"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgsSameType(args) }
}
// Integer comparison.
case object IntLTOp extends BoolResultOp {
  override val hashId = 224
  override val hashCode = computeHash
  override def toString = "<"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, IntType) }
  override def fixity = PREFIX
}
case object IntLEOp extends BoolResultOp {
  override val hashId = 225
  override val hashCode = computeHash
  override def toString = "<="
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, IntType) }
  override def fixity = PREFIX
}
case object IntGTOp extends BoolResultOp {
  override val hashId = 226
  override val hashCode = computeHash
  override def toString = ">"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, IntType) }
  override def fixity = PREFIX
}
case object IntGEOp extends BoolResultOp {
  override val hashId = 227
  override val hashCode = computeHash
  override def toString = ">="
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, IntType) }
  override def fixity = PREFIX
}
// Bitvector comparison.
case class BVLTOp(w : Int) extends BoolResultOp {
  override val hashId = mix(w, 228)
  override val hashCode = computeHash
  override def toString = "bvslt"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, BitVectorType.t(w)) }
  override def fixity = PREFIX
}
case class BVLEOp(w : Int) extends BoolResultOp {
  override val hashId = mix(w, 229)
  override val hashCode = computeHash
  override def toString = "bvsle"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, BitVectorType.t(w)) }
  override def fixity = PREFIX
}
case class BVGTOp(w : Int) extends BoolResultOp {
  override val hashId = mix(w, 230)
  override val hashCode = computeHash
  override def toString = "bvugt"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, BitVectorType.t(w)) }
  override def fixity = PREFIX
}
case class BVGEOp(w : Int) extends BoolResultOp {
  override val hashId = mix(w, 231)
  override val hashCode = computeHash
  override def toString = "bvuge"
  override def typeCheck(args: List[Expr]) : Unit = { checkNumArgs(args, 2); checkAllArgTypes(args, BitVectorType.t(w)) }
  override def fixity = PREFIX
}
case class RecordSelectOp(name : String) extends Operator {
  override val hashId = mix(name.hashCode(), 232)
  override val hashCode = computeHash
  override def toString = Context.getFieldName(name)
  override def typeCheck(args: List[Expr]) : Unit = {
    checkNumArgs(args, 1);
    Utils.assert(args(0).typ.isInstanceOf[ProductType], "Argument to record select must be a product type.")
    Utils.assert(args(0).typ.asInstanceOf[ProductType].hasField(name), "Field '" + name + "' does not exist in product type.")
  }
  def resultType(args: List[Expr]) : Type = {
    args(0).typ.asInstanceOf[ProductType].fieldType(name).get
  }
  override def fixity = PREFIX
}
case class RecordUpdateOp(name: String) extends Operator {
  override val hashId = mix(name.hashCode(), 232)
  override val hashCode = computeHash
  override def toString = "update-field " + name
  override def typeCheck(args: List[Expr]) : Unit = {
    checkNumArgs(args, 2)
    Utils.assert(args(0).typ.isInstanceOf[ProductType], "Argument to record update must be a product type.")
    val tupleType = args(0).typ.asInstanceOf[ProductType]
    Utils.assert(tupleType.hasField(name), "Field '" + name + "' does not exist in product type.")
  }
  def resultType(args: List[Expr]) : Type = args(0).typ
  override def fixity = PREFIX
}
case object ITEOp extends Operator {
  override val hashId = 234
  override val hashCode = computeHash
  override def toString = "ite"
  override def typeCheck(args: List[Expr]) : Unit = {
    checkNumArgs(args, 3)
    Utils.assert(args(0).typ.isBool, "Condition in ITE must be a boolean")
    Utils.assert(args(1).typ == args(1).typ, "Types in then- and else- expressions must be the same")
  }
  def resultType(args: List[Expr]) : Type = args(1).typ
  override def fixity = PREFIX
}
// Expressions
abstract class Expr(val typ: Type) extends Hashable {
  val hashBaseId : Int = 662
  val isConstant = false
}
// Literals.
abstract class Literal(exprType : Type) extends Expr (exprType) {
  override val isConstant = true
}

case class IntLit(value: BigInt) extends Literal (IntType) {
  override val hashId = mix(value.hashCode(), 300)
  override val hashCode = computeHash
  override def toString = value.toString
}

case class BitVectorLit(value: BigInt, width: Int) extends Literal (BitVectorType.t(width)) {
  Utils.assert(value.bitCount <= width, "Value (" + value.toString + ") too big for BitVector of width " + width + " bits.")
  override val hashId = mix(value.hashCode(), mix(width, 301))
  override val hashCode = computeHash
  override def toString = "(_ bv" + value.toString + " " + width.toString +")"
}

case class BooleanLit(value: Boolean) extends Literal (BoolType) {
  override val hashId = mix(value.hashCode(), 302)
  override val hashCode = computeHash
  override def toString = value.toString
}

case class EnumLit(id : String, eTyp : EnumType) extends Literal (eTyp) {
  override val hashId = mix(id.hashCode(), mix(eTyp.hashCode, 303))
  override val hashCode = computeHash
  override def toString  = id.toString
}

case class Symbol(id: String, symbolTyp: Type) extends Expr (symbolTyp) {
  override val hashId = mix(id.hashCode(), mix(symbolTyp.hashCode(), 304))
  override val hashCode = computeHash
  override def toString = id.toString
}
// Tuple creation.
case class MakeTuple(args: List[Expr]) extends Expr (TupleType(args.map(_.typ))) {
  override val hashId = 305
  override val hashCode = computeHash(args)
  override def toString = "(mk-tuple " + Utils.join(args.map(_.toString), " ") + ")"
  override val isConstant = args.forall(p => p.isConstant)
}


case class OperatorApplication(op: Operator, operands: List[Expr]) extends Expr (op.resultType(operands)) {
  override val hashId = 306
  override val hashCode = computeHash(operands, op)
  val fix = op.fixity
  Utils.assert(fix == INFIX || fix == PREFIX, "Unknown fixity.")
  Utils.assert(fix != INFIX || operands.size == 2, "Infix operators must have two operands.")
  op.typeCheck(operands)
  override def toString = {
    "(" + op.toString + " " + Utils.join(operands.map(_.toString), " ") + ")"
  }
  override val isConstant = operands.forall(p => p.isConstant)
}

case class ArraySelectOperation(e: Expr, index: List[Expr])
  extends Expr (e.typ.asInstanceOf[ArrayType].outType)
{
  override val hashId = 307
  override val hashCode = computeHash(index, e)
  override def toString = "(" + e.toString + ")" + "[" + index.tail.fold(index.head.toString)
    { (acc,i) => acc + "," + i.toString } + "]"
  override val isConstant = e.isConstant && index.forall(i => i.isConstant)
}
case class ArrayStoreOperation(e: Expr, index: List[Expr], value: Expr) extends Expr(e.typ)
{
  override val hashId = 308
  override val hashCode = computeHash(index, e, value)
  override def toString = e.toString + "[" + index.tail.fold(index.head.toString)
    { (acc,i) => acc + "," + i.toString } + " := " + value.toString + "]"
  override val isConstant = e.isConstant && index.forall(i => i.isConstant) && value.isConstant
}

//For uninterpreted function symbols or anonymous functions defined by Lambda expressions
case class FunctionApplication(e: Expr, args: List[Expr])
  extends Expr (e.typ.asInstanceOf[MapType].outType)
{
  override val hashId = 309
  override val hashCode = computeHash(args, e)
  override def toString = e.toString + "(" + args.tail.fold(args.head.toString)
    { (acc,i) => acc + "," + i.toString } + ")"
  override val isConstant = e.isConstant && args.forall(a => a.isConstant)
}

case class Lambda(ids: List[Symbol], e: Expr) extends Expr(MapType(ids.map(id => id.typ), e.typ)) {
  override val hashId = 310
  override val hashCode = computeHash(ids, e)
  override def toString = "Lambda(" + ids + "). " + e.toString
  override val isConstant = e.isConstant
}

abstract class SolverInterface {
  // Assert 'e' in the solver. (Modifies solver context to contain 'e'.)
  def addConstraint(e : Expr)
  // Check whether 'e' is satisfiable in the current solver context.
  def check(e : Expr) : SolverResult
  // Produce SMT2 output for this expression.
  def toSMT2(e : Expr, assumptions : List[Expr], name : String) : String
  // Add a list of assumptions
  def addAssumptions(es : List[Expr])
  // Pop the the last added list of assumptions
  def popAssumptions()
}
