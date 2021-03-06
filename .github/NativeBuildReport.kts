#!/usr/bin/env kscript

@file:MavenRepository("jcenter","https://jcenter.bintray.com/")
@file:MavenRepository("maven-central","https://repo.maven.apache.org/maven2/")
@file:DependsOn("org.kohsuke:github-api:1.101")


import org.kohsuke.github.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

val token = args[0]; 
val status = args[1];

val ISSUE_NUMBER=6588
val REPO = "quarkusio/quarkus"

// Handle status. Possible values are success, failure, or cancelled.
val succeed = status == "success";
if (status == "cancelled") {
    println("Job status is `cancelled` - exiting")
    System.exit(0)
}

val github = GitHubBuilder().withOAuthToken(token).build()
val repository = github.getRepository(REPO)

val issue = repository.getIssue(ISSUE_NUMBER)
if (issue == null) {
    println("Unable to find the issue ${ISSUE_NUMBER} in project ${REPO}")
    System.exit(-1)
} else {
    println("Report issue found: ${issue.getTitle()} - ${issue.getHtmlUrl()}")
    println("The issue is currently ${issue.getState()}")
}

val quickstartsCommit = getRepositoryCommit(".")
val quarkusCommit = getRepositoryCommit("quarkus")
if (succeed) {
    if (issue != null  && isOpen(issue)) {
        // close issue with a comment
        val comment = issue.comment("""
            Build fixed with:

            * Quarkus commit: ${quarkusCommit}
            * Quickstarts commit: ${quickstartsCommit}  
            * Link to build: https://github.com/quarkusio/quarkus-quickstarts/actions

        """.trimIndent())        
        issue.close()
        println("Comment added on issue ${issue.getHtmlUrl()} - ${comment.getHtmlUrl()}, the issue has also been closed")
    } else {
        println("Nothing to do - the build passed and the issue is already closed")
    }
} else  {
    if (isOpen(issue)) {
        val comment = issue.comment("""
        The build is still failing with:

        * Quarkus commit: ${quarkusCommit}
        * Quickstarts commit: ${quickstartsCommit}   
        * Link to build: https://github.com/quarkusio/quarkus-quickstarts/actions

    """.trimIndent())
        println("Comment added on issue ${issue.getHtmlUrl()} - ${comment.getHtmlUrl()}")
    } else {
        issue.reopen()
        val comment = issue.comment("""
        Unfortunately, the build failed:

        * Quarkus commit: ${quarkusCommit}
        * Quickstarts commit: ${quickstartsCommit}   
        * Link to build: https://github.com/quarkusio/quarkus-quickstarts/actions

    """.trimIndent())
        println("Comment added on issue ${issue.getHtmlUrl()} - ${comment.getHtmlUrl()}, the issue has been re-opened.")

    }
}


fun getRepositoryCommit(workingDir: String) : String {
    return "git rev-parse HEAD".runCommand(workingDir)
}

fun String.runCommand(workingDir: String): String {
    try {
        val parts = this.split("\\s".toRegex())
        val proc = ProcessBuilder(*parts.toTypedArray())
                .directory(File(workingDir))
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()

        proc.waitFor(1, TimeUnit.MINUTES)
        return proc.inputStream.bufferedReader().readText()
    } catch(e: IOException) {
        e.printStackTrace()
        return ""
    }
}

fun isOpen(issue : GHIssue) : Boolean {
    return issue.getState() == GHIssueState.OPEN
}

