package com.sandevsystems.omarchyremote.input

import com.sandevsystems.omarchyremote.ui.tr

/**
 * herdr's shortcuts as buttons in the phone's terminal. The PC says which it can send (the keys
 * come from the person's herdr config, host herdr_keys.py); the phone only names them.
 */
object TermActions {
    data class Action(val id: String, val label: String)
    data class Group(val title: String, val actions: List<Action>)

    private fun panes() = listOf(
        Action("split_vertical", tr("Dividir ao lado", "Split right")),
        Action("split_horizontal", tr("Dividir abaixo", "Split down")),
        Action("cycle_pane_next", tr("Próximo painel", "Next pane")),
        Action("focus_pane_left", tr("Painel à esquerda", "Pane left")),
        Action("focus_pane_right", tr("Painel à direita", "Pane right")),
        Action("focus_pane_up", tr("Painel acima", "Pane up")),
        Action("focus_pane_down", tr("Painel abaixo", "Pane down")),
        Action("zoom", tr("Ampliar / voltar", "Zoom / restore")),
        Action("close_pane", tr("Fechar painel", "Close pane")),
    )

    private fun tabs() = listOf(
        Action("new_tab", tr("Nova aba", "New tab")),
        Action("previous_tab", tr("Aba anterior", "Previous tab")),
        Action("next_tab", tr("Próxima aba", "Next tab")),
        Action("close_tab", tr("Fechar aba", "Close tab")),
    )

    val all: List<Action> get() = panes() + tabs()

    /** The groups with what the PC offers, in a fixed order; empty groups left out. */
    fun groups(offered: Set<String>): List<Group> = listOf(
        Group(tr("Painéis", "Panes"), panes().filter { it.id in offered }),
        Group(tr("Abas", "Tabs"), tabs().filter { it.id in offered }),
    ).filter { it.actions.isNotEmpty() }
}
