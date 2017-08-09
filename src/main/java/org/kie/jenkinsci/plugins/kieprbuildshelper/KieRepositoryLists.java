/*
 * Copyright 2015 JBoss by Red Hat
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
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

public class KieRepositoryLists {

    public static GitHubRepositoryList getListForBranch(final String repository,
                                                        final String branch) {
        String namespacedBranchId = resolveRepositoryNamespace(repository) + ":" + branch;

        List<KieGitHubRepository> repos = new ArrayList<KieGitHubRepository>();

        if ("master".equals(branch)) {
            repos.add(new KieGitHubRepository("errai", "errai"));
            repos.add(new KieGitHubRepository("uberfire", "uberfire"));
            repos.add(new KieGitHubRepository("dashbuilder", "dashbuilder"));
            repos.addAll(fetchRepositoryList("kiegroup", "master"));

            return new GitHubRepositoryList(repos);
        }

        switch (namespacedBranchId) {
            case "kiegroup:7.3.x": // kiegroup branch
            case "dashbuilder:0.9.x": // dashbuilder branch
            case "uberfire:1.3.x": // uberfire branch
                repos.add(new KieGitHubRepository("errai", "errai"));
                repos.add(new KieGitHubRepository("uberfire", "uberfire"));
                repos.add(new KieGitHubRepository("dashbuilder", "dashbuilder"));
                repos.addAll(fetchRepositoryList("kiegroup", "7.3.x"));
                break;

            case "kiegroup:7.2.x": // kiegroup branch
            case "dashbuilder:0.8.x": // dashbuilder branch
            case "uberfire:1.2.x": // uberfire branch
                repos.add(new KieGitHubRepository("errai", "errai"));
                repos.add(new KieGitHubRepository("uberfire", "uberfire"));
                repos.add(new KieGitHubRepository("dashbuilder", "dashbuilder"));
                repos.addAll(fetchRepositoryList("kiegroup", "7.2.x"));
                break;

            case "kiegroup:7.0.x": // kiegroup branch
            case "dashbuilder:0.6.x": // dashbuilder branch
            case "uberfire:1.0.x": // uberfire branch
                repos.add(new KieGitHubRepository("errai", "errai"));
                repos.add(new KieGitHubRepository("uberfire", "uberfire"));
                repos.add(new KieGitHubRepository("dashbuilder", "dashbuilder"));
                repos.addAll(fetchRepositoryList("kiegroup", "7.0.x"));
                break;

            case "kiegroup:6.5.x": // kiegroup branch
            case "dashbuilder:0.5.x": // dashbuilder branch
            case "uberfire:0.9.x": // uberfire branch
                repos.add(new KieGitHubRepository("uberfire", "uberfire"));
                repos.add(new KieGitHubRepository("uberfire", "uberfire-extensions"));
                repos.add(new KieGitHubRepository("dashbuilder", "dashbuilder"));
                repos.addAll(fetchRepositoryList("droolsjbpm", "6.5.x"));
                break;

            default:
                throw new IllegalArgumentException("Unknown branch '" + branch + "'! Make sure the plugin is aware of the specified branch.");
        }
        return new GitHubRepositoryList(repos);
    }

    private static String resolveRepositoryNamespace(final String repository) {
        if (isErraiRepo(repository) || isUberFireRepo(repository) || isDashbuilderRepo(repository)) {
            return repository;
        }
        return "kiegroup";
    }

    private static final List<BranchMapping> BRANCH_MAPPINGS = initMappings();

    private static List<BranchMapping> initMappings() {
        List<BranchMapping> mappings = new ArrayList<>();
        // branches for errai, uf, dashbuilder, kie
        mappings.add(new BranchMapping("master", "master", "master", "master"));
        mappings.add(new BranchMapping("4.0.x", "1.3.x", "0.9.x", "7.3.x"));
        mappings.add(new BranchMapping("4.0.x", "1.2.x", "0.8.x", "7.2.x"));
        mappings.add(new BranchMapping("4.0.x", "1.0.x", "0.6.x", "7.0.x"));
        mappings.add(new BranchMapping("3.2", "0.9.x", "0.5.x", "6.5.x"));
        mappings.add(new BranchMapping("3.2", "0.8.x", "0.4.x", "6.4.x"));
        mappings.add(new BranchMapping("0.7.x", "0.3.x", "6.3.x"));
        mappings.add(new BranchMapping("0.5.x", "0.2.x", "6.2.x"));
        return mappings;
    }



    public static List<KieGitHubRepository> fetchRepositoryList(String orgUnit, String branch) {
        List<KieGitHubRepository> repos = new ArrayList<>();
        String reposFileUrl = createUrlForRepositoryList(orgUnit, branch);
        try {
            URL reposFile = new URL(reposFileUrl);
            for (String repoName : IOUtils.readLines(reposFile.openStream())) {
                if ("kie-eap-modules".equals(repoName)) {
                    repos.add(new KieGitHubRepository("jboss-integration", repoName));
                } else {
                    repos.add(new KieGitHubRepository(orgUnit, repoName));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Can not fetch kiegroup repository list '" + reposFileUrl + "'!", e);
        }
        return repos;
    }

    private static String createUrlForRepositoryList(String orgUnit, String branch) {
        return "https://raw.githubusercontent.com/" + orgUnit  + "/droolsjbpm-build-bootstrap/" + branch + "/script/repository-list.txt";
    }

    public static String getBaseBranchFor(String repo, String otherRepo, String otherBranch) {
        BranchMapping mapping = getMapping(otherRepo, otherBranch);
        if (isErraiRepo(repo)) {
            return mapping.getErraiBranch();
        }else if (isUberFireRepo(repo)) {
            return mapping.getUfBranch();
        } else if (isDashbuilderRepo(repo)) {
            return mapping.getDashBranch();
        } else {
            return mapping.getKieBranch();
        }
    }

    private static BranchMapping getMapping(String repoName, String branch) {
        for (BranchMapping mapping : BRANCH_MAPPINGS) {
            if (isErraiRepo(repoName) && mapping.getErraiBranch() != null && mapping.getErraiBranch().equals(branch)) {
                return mapping;
            } else if (isUberFireRepo(repoName) && mapping.getUfBranch().equals(branch)) {
                return mapping;
            } else if (isDashbuilderRepo(repoName) && mapping.getDashBranch().equals(branch)) {
                return mapping;
            } else if (mapping.getKieBranch().equals(branch)) {
                return mapping;
            }
        }
        throw new RuntimeException("No branch mapping found for repo " + repoName + " with branch " + branch);
    }

    private static boolean isErraiRepo(String repoName) {
        return repoName.startsWith("errai");
    }
    private static boolean isUberFireRepo(String repoName) {
        return repoName.startsWith("uberfire");
    }

    private static boolean isDashbuilderRepo(String repoName) {
        return repoName.startsWith("dashbuilder");
    }

    public static class BranchMapping {
        /**
         * Can be null, because not all KIE branches depend on SNAPSHOTs
         * (and thus don't need to build the Errai for every PR)
         */
        private final String erraiBranch;
        private final String ufBranch;
        private final String dashBranch;
        private final String kieBranch;

        public BranchMapping(String erraiBranch, String ufBranch, String dashBranch, String kieBranch) {
            this.erraiBranch = erraiBranch;
            this.ufBranch = ufBranch;
            this.dashBranch = dashBranch;
            this.kieBranch = kieBranch;
        }

        public BranchMapping(String ufBranch, String dashBranch, String kieBranch) {
            this.erraiBranch = null;
            this.ufBranch = ufBranch;
            this.dashBranch = dashBranch;
            this.kieBranch = kieBranch;
        }

        public String getErraiBranch() {
            return erraiBranch;
        }

        public String getUfBranch() {
            return ufBranch;
        }

        public String getDashBranch() {
            return dashBranch;
        }

        public String getKieBranch() {
            return kieBranch;
        }
    }

}
