package org.sonarlint.intellij.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import org.sonarlint.intellij.analysis.SonarLintStatus
import org.sonarlint.intellij.config.Settings
import org.sonarlint.intellij.core.ProjectBindingManager
import org.sonarlint.intellij.util.SonarLintUtils
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue
import org.sonarsource.sonarlint.core.util.StringUtils

class OpenIssueInBrowserAction : AbstractSonarAction(
    "Open In Browser",
    "Open issue in browser interface of SonarQube or SonarCloud",
    AllIcons.Ide.External_link_arrow
) {


    companion object {
        val SERVER_ISSUE_DATA_KEY = DataKey.create<ServerIssue>("sonarlint_server_issue")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val issue = e.getData(SERVER_ISSUE_DATA_KEY)
        val key = issue?.key() ?: return
        BrowserUtil.browse(buildLink(project, key))
    }

    override fun isEnabled(e: AnActionEvent, project: Project, status: SonarLintStatus): Boolean {
        val issue = e.getData(SERVER_ISSUE_DATA_KEY)
        return issue != null
    }

    private fun buildLink(project: Project, issueKey: String): String {
        val projectBindingManager = SonarLintUtils.getService(project, ProjectBindingManager::class.java)
        val hostUrl = projectBindingManager.serverConnection.hostUrl
        val projectSettings = Settings.getSettingsFor(project)
        val urlEncodedProjectKey = StringUtils.urlEncode(projectSettings.projectKey!!)
        val urlEncodedIssueKey = StringUtils.urlEncode(issueKey)
        return "$hostUrl/project/issues?id=$urlEncodedProjectKey&open=$urlEncodedIssueKey"
    }
}