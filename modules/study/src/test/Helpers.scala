package lila.study

import monocle.syntax.all.*
import cats.syntax.all.*
import chess.{ Centis, ErrorStr }
import chess.format.pgn.{
  Dumper,
  Glyphs,
  ParsedPgn,
  San,
  Tags,
  PgnStr,
  PgnNodeData,
  Comment as ChessComment,
  Node as PgnNode
}
import chess.format.{ Fen, Uci, UciCharPair, UciPath }
import chess.MoveOrDrop.*

import lila.importer.{ ImportData, Preprocessed }
import lila.tree.Node.{ Comment, Comments, Shapes }

import cats.data.Validated
import scala.language.implicitConversions

import lila.tree.{ Branch, Branches, Root, Metas, NewTree, NewBranch, NewRoot, Node }

object Helpers:
  import lila.tree.NewTree.*

  object MetasC:
    def fromNode(node: Node) =
      Metas(
        node.ply,
        node.fen,
        node.check,
        node.dests,
        node.drops,
        node.eval,
        node.shapes,
        node.comments,
        node.gamebook,
        node.glyphs,
        node.opening,
        node.clock,
        node.crazyData
      )

  // Convertor
  object NewBranchC:
    def fromBranch(branch: Branch) =
      NewBranch(
        branch.id,
        UciPath.root,
        branch.move,
        branch.comp,
        branch.forceVariation,
        MetasC.fromNode(branch)
      )

  extension (newBranch: NewBranch)
    def toBranch(children: Option[NewTree]): Branch = Branch(
      newBranch.id,
      newBranch.metas.ply,
      newBranch.move,
      newBranch.metas.fen,
      newBranch.metas.check,
      newBranch.metas.dests,
      newBranch.metas.drops,
      newBranch.metas.eval,
      newBranch.metas.shapes,
      newBranch.metas.comments,
      newBranch.metas.gamebook,
      newBranch.metas.glyphs,
      children.fold(Branches.empty)(_.toBranches),
      newBranch.metas.opening,
      newBranch.comp,
      newBranch.metas.clock,
      newBranch.metas.crazyData,
      newBranch.forceVariation
    )
  // extension (newBranch: NewBranch)

  // Convertor
  object NewTreeC:
    def fromRoot(root: Root): Option[NewTree] =
      root.children.first.map(first =>
        NewTree(
          value = NewBranchC.fromBranch(first),
          child = first.children.first.map(NewTreeC.fromBranch(_, first.children.variations)),
          variation = root.children.variations.toVariations(toVariation)
        )
      )

    def fromBranch(branch: Branch, variations: List[Branch]): NewTree =
      NewTree(
        value = NewBranchC.fromBranch(branch),
        child = branch.children.first.map(NewTreeC.fromBranch(_, branch.children.variations)),
        variation = variations.toVariations(toVariation)
      )

    def toVariation(branch: Branch): NewTree =
      NewTree(
        value = NewBranchC.fromBranch(branch),
        child = branch.children.first.map(NewTreeC.fromBranch(_, branch.children.variations)),
        variation = None
      )

  extension (newTree: NewTree)
    def toBranch: Branch = newTree.value.toBranch(newTree.child)
    def toBranches: Branches =
      val variations = newTree.variations.map(_.toBranch)
      Branches(newTree.value.toBranch(newTree.child) :: variations)

  // Convertor
  object NewRootC:
    def fromRoot(root: Root) =
      NewRoot(MetasC.fromNode(root), NewTreeC.fromRoot(root))

  extension (newRoot: NewRoot)
    def toRoot =
      Root(
        newRoot.metas.ply,
        newRoot.metas.fen,
        newRoot.metas.check,
        newRoot.metas.dests,
        newRoot.metas.drops,
        newRoot.metas.eval,
        newRoot.metas.shapes,
        newRoot.metas.comments,
        newRoot.metas.gamebook,
        newRoot.metas.glyphs,
        newRoot.tree.fold(Branches.empty)(_.toBranches),
        newRoot.metas.opening,
        newRoot.metas.clock,
        newRoot.metas.crazyData
      )

  extension (comments: Comments)
    def cleanup: Comments =
      Comments(comments.value.map(_.copy(id = Comment.Id("i"))))

  extension (node: NewBranch)
    def cleanup: NewBranch =
      node
        .focus(_.metas.comments)
        .modify(_.cleanup)
        .focus(_.path)
        .replace(UciPath.root)

  extension (root: NewRoot)
    def cleanup: NewRoot =
      root
        .focus(_.tree.some)
        .modify(_.map(_.cleanup))
        .focus(_.metas.comments)
        .modify(_.cleanup)
