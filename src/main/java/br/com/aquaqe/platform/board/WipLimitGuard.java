package br.com.aquaqe.platform.board;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.aquaqe.platform.board.BoardService.Board;
import br.com.aquaqe.platform.board.BoardService.Column;
import br.com.aquaqe.platform.configuration.ArtifactConfiguration.StatusTransition;
import br.com.aquaqe.platform.workflow.TransitionGuard;
import br.com.aquaqe.platform.workitem.Artifacts;
import br.com.aquaqe.platform.workitem.Artifacts.ArtifactRef;

/**
 * Limite de WIP com política {@code BLOCK} (Proposta 04, decisões de 2026-11-20): recusa a transição quando a coluna de
 * destino, em qualquer quadro que contenha o item, já está no limite. Mover dentro da mesma coluna não conta.
 */
@Component
class WipLimitGuard implements TransitionGuard {

	private final BoardService boards;
	private final Artifacts artifacts;

	WipLimitGuard(BoardService boards, Artifacts artifacts) {
		this.boards = boards;
		this.artifacts = artifacts;
	}

	@Override
	public List<FailedRule> evaluate(ArtifactRef artifact, StatusTransition transition) {
		List<FailedRule> failed = new ArrayList<>();
		String to = transition.to().key();
		String from = transition.from().key();
		for (Board board : boards.find(null, artifact.level())) {
			Column target = board.columns().stream()
				.filter(c -> "BLOCK".equals(c.wipPolicy()) && c.wipLimit() != null && c.statusKeys().contains(to))
				.findFirst()
				.orElse(null);
			if (target == null || target.statusKeys().contains(from) || !artifacts.inBoardScope(artifact.id(), board.orgUnitId())) {
				continue;
			}
			long count = boards.wipCount(board, target);
			if (count >= target.wipLimit()) {
				failed.add(new FailedRule("wip:" + target.id(), "WIP_LIMIT", "A coluna \"" + target.name() + "\" do quadro \""
						+ board.name() + "\" está no limite de WIP (" + count + " de " + target.wipLimit() + ")."));
			}
		}
		return failed;
	}
}
