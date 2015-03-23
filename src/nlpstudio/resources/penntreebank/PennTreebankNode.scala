package nlpstudio.resources.penntreebank


import foundation.math.graph.Node
import nlpstudio.resources.HeadFinders.{GerberSemanticHeadFinder, CollinsHeadFinder}
import nlpstudio.resources.core.Rule


import scala.collection.mutable
import scala.collection.mutable.{ArrayBuffer}


/**
 * Created by yuhuan on 3/17/15.
 */
class PennTreebankNode private(var depth: Int,
                               var content: String,
                               var labels: Seq[String],
                               var parentNode: PennTreebankNode,
                               var childrenNodes: mutable.ArrayBuffer[PennTreebankNode]) extends Node[String] {

  override def data: String = content
  override def parent: Node[String] = parentNode
  override def children: Seq[Node[String]] = childrenNodes

  override def isLeaf: Boolean = childrenNodes.isEmpty

  def addChildren(newNode: PennTreebankNode) = {
    newNode.parentNode = this
    this.childrenNodes += newNode
  }

  def apply(idx: Int) = childrenNodes(idx)

  override def toString = data.toString

  var posTag: String = null

  def index = this.parentNode.children.indexOf(this)

  def syntacticCategory = if (posTag != null) posTag else this.content

  /**
   * Head constituent.
   */
  def syntaxHead = CollinsHeadFinder(this)

  /**
   * Head word.
   */
  def syntaxHeadWord = {
    if (isWord) this
    else {
      var reachedTheBottom = false
      var cur = this
      while (!reachedTheBottom) {
        cur = cur.syntaxHead
        if (cur.isWord) reachedTheBottom = true
      }
      cur
    }
  }

  def semanticHead = GerberSemanticHeadFinder(this)

  def wordNodes = this.leaves.map(n ⇒ n.asInstanceOf[PennTreebankNode])
  def words = this.leaves.map(n ⇒ n.data)

  def firstWordNode = traverse(n ⇒ n.children, n ⇒ n.isLeaf, n ⇒ n.isLeaf, n ⇒ Unit).head.asInstanceOf[PennTreebankNode]
  def firstWord = firstWordNode.content
  def firstPos = firstWordNode.posTag

  def lastWordNode = traverse(n ⇒ n.children.reverse, n ⇒ n.isLeaf, n ⇒ n.isLeaf, n ⇒ Unit).head.asInstanceOf[PennTreebankNode]
  def lastWord = lastWordNode.content
  def lastPos = lastWordNode.posTag

  def rule = Rule(this.syntacticCategory, this.childrenNodes.map(_.syntacticCategory))

  def isWord = this.isLeaf



  def isNullElement: Boolean = {
    if (isLeaf) syntacticCategory == SpecialCategories.nullElement
    else childrenNodes.forall(n ⇒ n.isNullElement)
  }

  def leftSiblings = {
    val indexUnderParent = this.index
    val allSiblings = this.parentNode.childrenNodes
    allSiblings.slice(0, indexUnderParent)
  }

  def rightSiblings = {
    val indexUnderParent = this.index
    val allSiblings = this.parentNode.childrenNodes
    allSiblings.slice(indexUnderParent + 1, allSiblings.length)
  }
}

object PennTreebankNode {
  def apply(depth: Int,
            data: String,
            labels: Seq[String],
            parent: PennTreebankNode,
            children: mutable.ArrayBuffer[PennTreebankNode]) = {

    if (children == null) {
      new PennTreebankNode(depth, data, labels, parent, new ArrayBuffer[PennTreebankNode])
    }
    else {
      new PennTreebankNode(depth, data, labels, parent, children)
    }

  }
}