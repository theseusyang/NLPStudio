package nlpstudio.resources.nombank

import nlpstudio.io.dataset.DatasetManager
import nlpstudio.io.files.TextFile
import nlpstudio.resources.nomlexplus.NomLexPlus
import nlpstudio.resources.penntreebank.{SpecialCategories, PennTreebank, PennTreebankEntry, PennTreebankNode}
import nlpstudio.tools.stemmers.PorterStemmer

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable

/**
 * Created by Yuhuan Jiang (jyuhuan@gmail.com) on 3/11/15.
 */

/**
 * A reader for NomBank 1.0.
 * Important notice: Since the section 00 does not have wsj_0000.mrg (it starts with
 * wsj_0001.mrg, which is very very very very annoying!!!), you should create a fake
 * wsj_0000.mrg under wsj/00/. And since this file is to be parsed by this library,
 * you should put at least one bracket-style parse tree in it. A simple way to do this,
 * is to make a copy of any wsj_????.mrg file that's already in wsj/00/, and rename it
 * to wsj_0000.mrg.
 */
object NomBank {


  /**
   * Parses "4:0*9:1-ARG1-PRD" to a NomBankAnnotation object.
   * @param parseTree The parse tree this annotation annotates on.
   * @param annotation The annotation in the form of "4:0*9:1-ARG1-PRD".
   */
  private def parseAnnotations(parseTree: PennTreebankEntry, annotation: String): NomBankAnnotation = {
    val pointersAndTags = annotation.split("-") // = ["4:0*9:1", "ARG1", "PRD"]
    val pointerStrings = pointersAndTags(0)
    val tagStrings = pointersAndTags(1)

    // Determine whether the pointers are connected with "*", meaning that they are coreferences; or
    // they are connected with "-", meaning they are simply concatenation due to not being under
    // one constituent.
    val pointerType = if (pointerStrings contains '*') {
      PointerType.Coreference
    }
    else if (pointerStrings contains ',') {
      PointerType.NotAConstituent
    }
    else PointerType.Single

    val tokens = parseTree.wordNodes

    val pointers = if (pointerType == PointerType.Coreference) {
      pointerStrings.split('*')
    }
    else if (pointerType == PointerType.NotAConstituent) {
      pointerStrings.split(',')
    }
    else Array[String](pointerStrings)

    val affectedNodes = pointers.map(s ⇒ {
      val tokenAndSteps = s.split(':')
      val tokenId = tokenAndSteps(0).toInt
      val steps = tokenAndSteps(1).toInt
      var curNode = tokens(tokenId)
      for (i ← 0 until steps) {
        curNode = curNode.parentNode
      }
      curNode
    })

    val labelAndFunctionTags = tagStrings.split('-')
    val label = labelAndFunctionTags(0)
    val functionTags = labelAndFunctionTags.slice(1, labelAndFunctionTags.length)
    if (affectedNodes(0) == null) {
      println("found")
      val bp = 0
    }
    NomBankAnnotation(affectedNodes, pointerType, label, functionTags)
  }




  /**
   * Loads a single entry of NomBank.
   * @param entry A sequence of fields. Has the form:
   *               "4 account 03 1:0*12:1-ARG0 2:0,3:0-Support 4:0-rel 5:2-ARG1"
   * @param parseTree The parse tree that the annotation contained in this entry annotate over.
   * @return
   */
  private def loadSingleEntry(entry: String, parseTree: PennTreebankEntry): NomBankEntry = {

    val fields = entry.split(' ')
    val treeRelativePath = fields(0)
    val pathParts = treeRelativePath.split('/')
    val sectionId = pathParts(1).toInt
    val mrgFileId = pathParts(2).substring(6, 8).toInt
    val treeId = fields(1).toInt
    val tokenId = fields(2).toInt
    val predicateBaseForm = fields(3)
    val senseId = fields(4).toInt
    val annotationStrings = fields.slice(5, fields.length)

//    try {
      val annotations = annotationStrings.map(a ⇒ parseAnnotations(parseTree, a))
//    }
//    catch {
//      case e: Exception ⇒ {
//        val breakpoint = 0
//      }
//    }

    NomBankEntry(sectionId, mrgFileId, treeId, parseTree.wordNodes(tokenId), predicateBaseForm, senseId, annotations, parseTree)
  }


  /**
   * Loads NomBank 1.0. Users can iterate over the return value by NomBankEntry's.
   * @param pathToNomBank The path to the file "nombank.1.0". Remember to remove line 63292 before
   *                      calling this method. Line 63292 contains an error: The 6th word node in
   *                      the tree has only 8 ancestors, but there is 6:9 in it.
   * @param pathToPennTreebank Path to the directory "parsed/mrg/wsj"
   * @return A sequence of NomBankEntry's.
   */
  def load(pathToNomBank: String, pathToPennTreebank: String): Array[NomBankEntry] = {
    val ptb = PennTreebank.load(pathToPennTreebank)


    val entries = for (line ← TextFile.readLines(pathToNomBank)) yield {
      val fields = line.split(' ')
      val treeRelativePath = fields(0)
      val pathParts = treeRelativePath.split('/')
      val sectionId = pathParts(1).toInt
      val mrgFileId = pathParts(2).substring(6, 8).toInt
      val treeId = fields(1).toInt
      val parseTree = ptb(sectionId)(mrgFileId)(treeId)

      loadSingleEntry(line, parseTree)
    }

    entries.toArray
  }

  /**
   * Loads fine-grained entries. In the original NomBank annotation, one entry contains:
   * <ol>
   *   <li> The predicate </li>
   *   <li> Arg0 of the predicate </li>
   *   <li> Arg1 of the predicate </li>
   *   <li> ... </li>
   *   <li> Argm-TMP of the predicate </li>
   *   <li> Support verb of the predicate </li>
   *   <li> ... </li>
   * </ol>
   *
   * This method breaks all the annotations for the predicate p. Thus, the result will be:
   *
   * <ul>
   *   <li> Predicate, Arg0 </li>
   *   <li> Predicate, Arg1 </li>
   *   <li> ... </li>
   *   <li> Predicate, Argm-TMP </li>
   *   <li> Predicate, Support verb </li>
   *   <li> ... </li>
   * </ul>
   *
   * Notice that this is not suitable for training, because there are no negative samples (i.e.,
   * those with label "null")
   *
   * @param pathToNomBank The path to the file "nombank.1.0". Remember to remove line 63292 before
   *                      calling this method. Line 63292 contains an error: The 6th word node in
   *                      the tree has only 8 ancestors, but there is 6:9 in it.
   * @param pathToPennTreebank Path to the directory "parsed/mrg/wsj".
   * @return
   */
  def loadAsFineGrainedEntries(pathToNomBank: String, pathToPennTreebank: String): Array[NomBankFineGrainedEntry] = {
    val coarseEntries = load(pathToNomBank, pathToPennTreebank)
    coarseEntries.flatMap(e ⇒ {
      val supportVerbNodes = ArrayBuffer[PennTreebankNode]()
      supportVerbNodes ++= e.annotations.filter(a ⇒ a.label == "Support").map(a ⇒ a.nodes.head)
      e.annotations.flatMap(a ⇒ a.nodes.map(n ⇒ {
        NomBankFineGrainedEntry(e.sectionId, e.mrgFileId, e.treeId, e.predicateNode, e.stemmedPredicate, e.senseId, n, supportVerbNodes, a.label, a.functionTags, e.parseTree)
      }))})
  }


  def selectCandidates(parseTree: PennTreebankNode, predicateNode: PennTreebankNode, supportVerbNodes: Seq[PennTreebankNode]): mutable.Set[PennTreebankNode] = {
    val result = mutable.Set[PennTreebankNode]()
    val eligibleNodes = parseTree.allNodes.filterNot(n ⇒ SpecialCategories.posWithNoRealMeaning.contains(n.syntacticCategoryOrPosTag) || predicateNode.ancestors.contains(n) || supportVerbNodes.contains(n))
    for (n ← eligibleNodes) result.add(n)
    result
  }

  def loadDeverbalEntriesForTraining(pathToNomBank: String, pathToPennTreebank: String): ArrayBuffer[NomBankFineGrainedEntry] = {
    val trainingEntries = ArrayBuffer[NomBankFineGrainedEntry]()

    val stemmer = new PorterStemmer()
    val deverbalNouns = NomLexPlus.load(DatasetManager.NomLexPlusPath).map(x ⇒ stemmer.stem(x)).toSet
    val coarseEntries = load(pathToNomBank, pathToPennTreebank).filter(e ⇒ {
      val predicateWord = e.predicateNode.surface
      val stemmed = stemmer.stem(predicateWord)
      deverbalNouns contains stemmed
    })

    for (e ← coarseEntries) {
      val predicateNode = e.predicateNode
      val parseTree = e.parseTree

      // One NomBank entry may mark multiple support verbs (in version 1.0, at most 2)
      val supportVerbNodes = ArrayBuffer[PennTreebankNode]()
      supportVerbNodes ++= e.annotations.filter(a ⇒ a.label == "Support").map(a ⇒ a.nodes.head)

      val allCandidateNodes = selectCandidates(parseTree.tree, predicateNode, supportVerbNodes)

      // All positive training entries
      trainingEntries ++= e.annotations.flatMap(a ⇒ a.nodes.map(n ⇒ {
        allCandidateNodes.remove(n)
        NomBankFineGrainedEntry(e.sectionId, e.mrgFileId, e.treeId, predicateNode, e.stemmedPredicate, e.senseId, n, supportVerbNodes, a.label, a.functionTags, e.parseTree)
      }))

      // Negative training entries
      trainingEntries ++= allCandidateNodes.map(n ⇒
        NomBankFineGrainedEntry(e.sectionId, e.mrgFileId, e.treeId, predicateNode, e.stemmedPredicate, e.senseId, n, supportVerbNodes, "NULL", Seq[String](), parseTree)
      )

    }

    trainingEntries
  }



}
