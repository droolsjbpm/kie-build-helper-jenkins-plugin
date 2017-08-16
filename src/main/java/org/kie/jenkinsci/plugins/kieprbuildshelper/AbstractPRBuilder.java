/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.jenkinsci.plugins.kieprbuildshelper;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.Builder;
import org.eclipse.jgit.transport.RefSpec;
import org.kohsuke.github.GitHub;

public abstract class AbstractPRBuilder extends Builder {

    private transient PrintStream buildLogger;

    private transient GitHubPRSummary pr;
    private final MavenBuildConfig mvnBuildConfig;

    public AbstractPRBuilder(String mvnHome, String mvnOpts, String mvnArgs) {
        this.mvnBuildConfig = new MavenBuildConfig(mvnHome, mvnOpts, mvnArgs);
    }

    public String getMvnHome() {
        return mvnBuildConfig.getMvnHome();
    }

    public String getMvnOpts() {
        return mvnBuildConfig.getMvnOpts();
    }

    public String getMvnArgs() {
        return mvnBuildConfig.getMvnArgs();
    }

    protected abstract String getDescription();

    protected abstract FilePath getBuildDir(FilePath workspace);

    protected abstract List<Tuple<GitHubRepository, GitBranch>> getReposToBuild(GitHubRepository prRepo,
                                                                                List<Tuple<GitHubRepository, GitBranch>> allRepos);

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        try {
            buildLogger = listener.getLogger();
            buildLogger.println(getDescription() + " started.");
            EnvVars envVars = build.getEnvironment(launcher.getListener());
            GitHub github = connectToGitHubWithOAuthToken();
            initFromEnvVars(envVars, github);

            // clean-up the destination directory to avoid stale content
            FilePath reposDir = getBuildDir(build.getWorkspace());
            buildLogger.println("Cleaning-up directory " + reposDir.getRemote());
            reposDir.deleteRecursive();

            // figure out the location of the repository-list.txt and branch-mapping.yaml files
            // if there is an open PR for bootstrap repo, tied together with this PR, use the files from there, otherwise
            // use the default ones
            GitBranch prSourceBranch = pr.getSourceBranch();
            GitBranch prTargetBranch = pr.getTargetBranch();
            List<Tuple<GitHubRepository, GitBranch>> allRepos;
            Optional<GitHubPRSummary> bootstrapRepoPR =
                    GitHubUtils.findOpenPullRequest(RepositoryLists.KIE_BOOTSTRAP_REPO, prSourceBranch, pr.getSourceRepo().getOwner(), github);
            if (prTargetBranch.equals(GitBranch.MASTER) && bootstrapRepoPR.isPresent()) {
                GitHubRepository bootstrapRepo = new GitHubRepository(bootstrapRepoPR.get().getSourceRepo().getOwner(), bootstrapRepoPR.get().getTargetRepoName());
                allRepos = RepositoryLists.createFor(pr.getTargetRepo(), prTargetBranch, bootstrapRepo, prSourceBranch);
            } else {
                allRepos = RepositoryLists.createFor(pr.getTargetRepo(), prTargetBranch);
            }

            List<Tuple<GitHubRepository, GitBranch>> filteredRepos = getReposToBuild(pr.getTargetRepo(), allRepos);
            List<Tuple<GitHubRepository, RefSpec>> reposToBuild = transformToRefSpecs(filteredRepos, github);
            GitHubUtils.logRepositories(reposToBuild, buildLogger);
            GitHubUtils.cloneRepositories(reposDir, reposToBuild, GitHubUtils.GIT_REFERENCE_BASEDIR, listener);
            // build the repositories using Maven
            for (Tuple<GitHubRepository, RefSpec> repoWithRefSpec : reposToBuild) {
                GitHubRepository repo = repoWithRefSpec._1();
                MavenProject mavenProject = new MavenProject(new FilePath(reposDir, repo.getName()), mvnBuildConfig.getMvnHome(),
                                                             mvnBuildConfig.getMvnOpts(), launcher, listener);
                mavenProject.build(mvnBuildConfig.getMvnArgs(), envVars, buildLogger);
            }
        } catch (Exception ex) {
            buildLogger.println("Unexpected error while executing the " + getDescription() + "! " + ex.getMessage());
            ex.printStackTrace(buildLogger);
            return false;
        }
        buildLogger.println(getDescription() + " finished successfully.");
        return true;
    }

    private List<Tuple<GitHubRepository, RefSpec>> transformToRefSpecs(List<Tuple<GitHubRepository, GitBranch>> repos, GitHub github) {
        List<Tuple<GitHubRepository, RefSpec>> result = new ArrayList<>();
        for (Tuple<GitHubRepository, GitBranch> repoWithBranch: repos) {
            GitHubRepository otherRepo = repoWithBranch._1();
            Optional<GitHubPRSummary> otherRepoPR = GitHubUtils.findOpenPullRequest(otherRepo,
                                                                               pr.getSourceBranch(),
                                                                               pr.getSourceRepo().getOwner(),
                                                                               github);
            // in case the other repo has a PR we are looking for, it also needs to be mergeable, if not fail fast
            otherRepoPR.ifPresent(pr -> {
                if (!pr.isMergeable()) {
                    throw new RuntimeException("PR " + pr.getNumber() + " for repo " + pr.getTargetRepo() + " is " +
                                                       "not automatically mergeable. Please fix the conflicts first!");
                }
            });
            GitBranch baseBranch = repoWithBranch._2();
            RefSpec refspec = new RefSpec(otherRepoPR
                                             .map(pr -> "pull/" + pr.getNumber() + "/merge:pr" + pr.getNumber() + "-" + pr.getSourceBranch().getName() + "-merge")
                                             .orElse(baseBranch.getName() + ":" + baseBranch.getName() + "-pr-build"));
            result.add(Tuple.of(otherRepo, refspec));
        }
        return result;
    }

    private GitHub connectToGitHubWithOAuthToken() {
        KiePRBuildsHelper.KiePRBuildsHelperDescriptor globalSettings = KiePRBuildsHelper.getKiePRBuildsHelperDescriptor();
        String ghOAuthToken = globalSettings.getGhOAuthToken();
        if (ghOAuthToken == null) {
            throw new IllegalStateException("No GitHub OAuth token found. Please set one on global Jenkins configuration page.");
        }
        try {
            return GitHub.connectUsingOAuth(ghOAuthToken);
        } catch (IOException e) {
            throw new RuntimeException("Can not connect to GitHub using the configured OAuth token!");
        }
    }

    /**
     * Initializes the fields from passed Environmental Variables
     *
     * @param envVars set of environment variables
     */
    private void initFromEnvVars(EnvVars envVars, GitHub github) {
        String prLink = envVars.get("ghprbPullLink");
        buildLogger.println("Working with PR: " + prLink);
        if (prLink == null || "".equals(prLink)) {
            throw new IllegalStateException("PR link not set! Make sure variable 'ghprbPullLink' contains valid link to GitHub Pull Request!");
        }
        this.pr = GitHubPRSummary.fromPRLink(prLink, github);
    }
}
